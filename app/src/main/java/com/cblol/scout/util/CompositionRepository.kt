package com.cblol.scout.util

import com.cblol.scout.data.ChampionTag
import com.cblol.scout.data.CompAnalysisResult
import com.cblol.scout.data.CompArchetype
import com.cblol.scout.data.TeamComposition
import com.cblol.scout.domain.GameConstants

/**
 * Repositório de composições e motor de análise de sinergia.
 *
 * COMPOSIÇÕES CORINGA (Tier S — bônus máximo, difíceis de banir completamente):
 *   As composições Tier S representam as "god comps" do meta competitivo 2026.
 *   Banir apenas 1 campeão-chave as desmonta.
 *
 * LÓGICA DE BÔNUS NO SIMULADOR:
 *   - Composição detectada nos picks → bonusStrength adicionado ao teamStrength
 *   - Composição do oponente detectada nos picks adversários → mesmo bônus para eles
 *   - Campeões-chave banidos antes do oponente pickear → bônus é bloqueado
 *
 * Isso cria tensão genuína no pick & ban:
 *   "Devo banir o Orianna (que quebra a Wombo Combo deles) ou o Zed (que ameaça meu mid)?"
 */
object CompositionRepository {

    val all: List<TeamComposition> by lazy { buildComps() }

    /**
     * Analisa todos os picks e retorna a composição com maior sinergia (legado).
     * Para múltiplas composições simultâneas, use [analyzeAll].
     */
    fun analyze(picks: List<String>, bans: List<String> = emptyList()): CompAnalysisResult {
        val detected = analyzeAll(picks, bans)
        val best = detected.firstOrNull()
        val desc = if (best != null) {
            val pct = (best.matched.size.toFloat() / best.composition.requiredPicks.size * 100).toInt()
            "${best.composition.name} ($pct% montada) · +${best.bonus} força"
        } else {
            "Sem sinergia detectada"
        }
        return CompAnalysisResult(best?.composition, best?.matched ?: emptyList(), best?.bonus ?: 0, desc)
    }

    /**
     * Retorna TODAS as composições que atingem o mínimo de picks exigidos,
     * ordenadas pelo bônus efetivo (maior primeiro).
     *
     * Um mesmo time pode encaixar em múltiplas composições ao mesmo tempo —
     * por exemplo Jarvan+Malphite+Wukong pode contar tanto para "Wombo Combo"
     * (knock-ups encadeados) quanto para "Hard Engage" (CC inicial em cadeia).
     */
    fun analyzeAll(picks: List<String>, bans: List<String> = emptyList()): List<DetectedComp> {
        val picksLower = picks.map { it.lowercase() }
        val results = mutableListOf<DetectedComp>()

        for (comp in all) {
            val required = comp.requiredPicks.map { it.lowercase() }
            val matched  = required.filter { it in picksLower }
            if (matched.size < comp.minRequired) continue

            val keyBanned = comp.keyChampions.any { key ->
                bans.any { ban -> ban.lowercase() == key.lowercase() }
            }
            if (keyBanned) continue

            val bonus = when {
                matched.size >= required.size    -> comp.bonusStrength
                matched.size == comp.minRequired -> comp.bonusStrength / 2
                else                              -> comp.bonusStrength * matched.size / required.size
            }
            val originalNames = picks.filter { it.lowercase() in matched }
            results += DetectedComp(comp, originalNames, bonus)
        }
        return results.sortedByDescending { it.bonus }
    }

    /** Uma composição detectada nos picks atuais, com seu bônus efetivo. */
    data class DetectedComp(
        val composition: TeamComposition,
        val matched: List<String>,
        val bonus: Int
    ) {
        val percent: Int get() =
            (matched.size.toFloat() / composition.requiredPicks.size * 100).toInt()
    }

    /**
     * Análise enriquecida com tags funcionais + múltiplas composições simultâneas.
     */
    fun analyzeWithTags(
        picks: List<String>,
        opponentPicks: List<String> = emptyList(),
        bans: List<String> = emptyList()
    ): TaggedAnalysisResult {
        val detectedComps = analyzeAll(picks, bans)

        val combinedBonus = detectedComps.foldIndexed(0) { idx, acc, comp ->
            val factor = when (idx) {
                0    -> GameConstants.Synergy.PRIMARY_COMP_FACTOR
                1    -> GameConstants.Synergy.SECONDARY_COMP_FACTOR
                2    -> GameConstants.Synergy.TERTIARY_COMP_FACTOR
                else -> GameConstants.Synergy.ADDITIONAL_COMP_FACTOR
            }
            acc + (comp.bonus * factor).toInt()
        }

        val primary = detectedComps.firstOrNull()
        val baseDesc = when {
            detectedComps.isEmpty() -> "Sem sinergia detectada"
            detectedComps.size == 1 -> primary!!.let { "${it.composition.name} (${it.percent}%) · +${it.bonus} força" }
            else -> {
                val names = detectedComps.take(3).joinToString(" + ") { it.composition.name }
                "$names · +$combinedBonus força (combinado)"
            }
        }
        val base = CompAnalysisResult(
            detected         = primary?.composition,
            matchedChampions = primary?.matched ?: emptyList(),
            bonusStrength    = combinedBonus,
            description      = baseDesc
        )

        val myChamps = picks.mapNotNull { ChampionRepository.getById(it) }
        val opChamps = opponentPicks.mapNotNull { ChampionRepository.getById(it) }
        var extra    = 0
        val insights = mutableListOf<String>()

        val hasAD = myChamps.any { it.hasTag(ChampionTag.PHYSICAL_DAMAGE) }
        val hasAP = myChamps.any { it.hasTag(ChampionTag.MAGIC_DAMAGE) }
        if (hasAD && hasAP) {
            extra += GameConstants.Synergy.BONUS_MIXED_DAMAGE
            insights += "✅ Dano misto (AD+AP)"
        }

        val ccCount = myChamps.count { it.hasTag(ChampionTag.CROWD_CONTROL) || it.hasTag(ChampionTag.ENGAGE) }
        if (ccCount >= GameConstants.Synergy.THRESHOLD_HIGH_CC) {
            extra += GameConstants.Synergy.BONUS_HIGH_CC
            insights += "✅ $ccCount fontes de CC"
        }

        val knockUps = myChamps.count { it.hasTag(ChampionTag.KNOCK_UP) }
        if (knockUps >= GameConstants.Synergy.THRESHOLD_KNOCKUPS) {
            extra += GameConstants.Synergy.BONUS_KNOCKUP_SYNERGY
            insights += "✅ $knockUps knock-ups (sinergia Yasuo/Yone)"
        }

        val healers = myChamps.count { it.hasTag(ChampionTag.HEAL) || it.hasTag(ChampionTag.SHIELD) }
        if (healers >= GameConstants.Synergy.THRESHOLD_HEAL_SHIELD) {
            extra += GameConstants.Synergy.BONUS_HEAL_SHIELD
            insights += "✅ $healers fontes de cura/escudo"
        }

        val tanks = myChamps.count { it.hasTag(ChampionTag.TANK) }
        val squishies = myChamps.count { it.hasTag(ChampionTag.MARKSMAN) || it.hasTag(ChampionTag.MAGE) }
        if (tanks == 0 && squishies >= GameConstants.Synergy.THRESHOLD_SQUISHIES) {
            extra += GameConstants.Synergy.PENALTY_NO_FRONTLINE
            insights += "⚠️ Sem frontline, vulnerável a engage"
        }

        val opTanks = opChamps.count { it.hasTag(ChampionTag.TANK) || it.hasTag(ChampionTag.FIGHTER) }
        val antiTank = myChamps.count { it.hasTag(ChampionTag.ANTI_TANK) || it.hasTag(ChampionTag.TRUE_DAMAGE) }
        if (opTanks >= GameConstants.Synergy.THRESHOLD_TANKS_ON_OP && antiTank == 0) {
            extra += GameConstants.Synergy.PENALTY_NO_ANTI_TANK
            insights += "⚠️ Sem anti-tank contra frontline pesada"
        }
        if (opTanks >= GameConstants.Synergy.THRESHOLD_BRUISERS_OP && antiTank >= 2) {
            extra += GameConstants.Synergy.BONUS_ANTI_TANK_VS_TANK
            insights += "✅ Bom anti-tank"
        }

        val opHyper = opChamps.count { it.hasTag(ChampionTag.HYPERCARRY) }
        val myEarly = myChamps.count { it.hasTag(ChampionTag.EARLY_GAME) }
        if (opHyper >= GameConstants.Synergy.THRESHOLD_HYPER_ON_OP && myEarly == 0) {
            extra += GameConstants.Synergy.PENALTY_NO_EARLY_VS_SCALING
            insights += "⚠️ Oponente escala muito, force lutas cedo"
        }

        if (detectedComps.size >= 2) {
            val names = detectedComps.take(2).joinToString(" + ") { it.composition.name }
            insights.add(0, "✨ Comp híbrida: $names")
        }

        return TaggedAnalysisResult(
            base          = base,
            extraBonus    = extra,
            totalBonus    = (combinedBonus + extra).coerceAtLeast(0),
            insights      = insights,
            detectedComps = detectedComps
        )
    }

    /** Sugere picks que complementam os já escolhidos com base em tags. */
    fun suggestPicks(currentPicks: List<String>, role: String? = null): List<SuggestedPick> {
        val current = currentPicks.mapNotNull { ChampionRepository.getById(it) }
        val hasEngage   = current.any { it.hasTag(ChampionTag.ENGAGE) }
        val hasHeal     = current.any { it.hasTag(ChampionTag.HEAL) }
        val hasAntiTank = current.any { it.hasTag(ChampionTag.ANTI_TANK) }
        val hasAP       = current.any { it.hasTag(ChampionTag.MAGIC_DAMAGE) }
        val hasAD       = current.any { it.hasTag(ChampionTag.PHYSICAL_DAMAGE) }

        val pool = if (role != null) ChampionRepository.getByRole(role) else ChampionRepository.getAll()

        return pool.filter { it.id !in currentPicks }.mapNotNull { champ ->
            var score = 0
            val reasons = mutableListOf<String>()
            if (!hasEngage   && champ.hasTag(ChampionTag.ENGAGE))          { score += 3; reasons += "engage" }
            if (!hasHeal     && champ.hasTag(ChampionTag.HEAL))            { score += 2; reasons += "cura" }
            if (!hasAntiTank && champ.hasTag(ChampionTag.ANTI_TANK))       { score += 2; reasons += "anti-tank" }
            if (!hasAP       && champ.hasTag(ChampionTag.MAGIC_DAMAGE))    { score += 2; reasons += "dano mágico" }
            if (!hasAD       && champ.hasTag(ChampionTag.PHYSICAL_DAMAGE)) { score += 2; reasons += "dano físico" }
            if (champ.hasTag(ChampionTag.CROWD_CONTROL))                   { score += 1; reasons += "CC" }
            if (score >= 3) SuggestedPick(champ.id, score, reasons) else null
        }.sortedByDescending { it.score }.take(5)
    }

    data class TaggedAnalysisResult(
        val base: CompAnalysisResult,
        val extraBonus: Int,
        val totalBonus: Int,
        val insights: List<String>,
        val detectedComps: List<DetectedComp> = emptyList()
    )

    data class SuggestedPick(
        val championId: String,
        val score: Int,
        val reasons: List<String>
    )

    fun compsNeutralizedBy(bans: List<String>): List<TeamComposition> {
        val bansLower = bans.map { it.lowercase() }
        return all.filter { comp ->
            comp.keyChampions.any { key -> key.lowercase() in bansLower }
        }
    }

    fun suggestBans(opponentPicks: List<String> = emptyList()): List<Pair<String, String>> {
        val priority = all.sortedWith(
            compareByDescending<TeamComposition> { it.tier }
                .thenByDescending { it.bonusStrength }
        )
        val suggestions = mutableListOf<Pair<String, String>>()
        val added = mutableSetOf<String>()

        for (comp in priority) {
            for (key in comp.keyChampions) {
                if (key !in added && suggestions.size < GameConstants.Draft.BAN_SUGGESTIONS_COUNT) {
                    suggestions += key to "Quebra ${comp.name} (Tier ${comp.tier})"
                    added += key
                }
            }
        }
        return suggestions
    }

    // ─────────────────────────────────────────────────────────────────────
    // CATÁLOGO DE COMPOSIÇÕES
    //
    // Cada comp tem uma lista AMPLA de campeões em requiredPicks com minRequired
    // baixo (2-3). Isso permite que múltiplas combinações diferentes formem a mesma
    // comp — ex: "Wombo Combo" pode ser Malphite+Orianna+MF, ou Amumu+Yasuo+Jarvan,
    // ou Wukong+Sett+Kennen+MF, etc.
    //
    // keyChampions são apenas os 2-3 mais críticos (que se banidos quebram a comp).
    // Banir Malphite não impede um Wombo com Amumu+Wukong+Yasuo, por exemplo.
    // ─────────────────────────────────────────────────────────────────────
    private fun buildComps(): List<TeamComposition> = listOf(

        // ═════════════════════════════════════════════════════════════════
        // TIER S — COMPOSIÇÕES DOMINANTES DO META 2026
        // ═════════════════════════════════════════════════════════════════

        TeamComposition(
            id              = "wombo_combo",
            name            = "Wombo Combo",
            description     = "AoE devastador: ulti encadeado que apaga o time inimigo em segundos. " +
                              "Requer posicionamento e ativação simultânea.",
            archetype       = CompArchetype.WOMBO,
            requiredPicks   = listOf(
                // Iniciadores AoE
                "Malphite", "Amumu", "Kennen", "Sett", "Wukong", "Sejuani", "Galio", "Zac",
                // Multiplicadores AoE
                "Orianna", "MissFortune", "Yasuo", "Yone", "Seraphine",
                // Engagers complementares
                "Jarvaniv", "Diana"
            ),
            minRequired     = 3,
            keyChampions    = listOf("Orianna", "MissFortune", "Yasuo"),
            bonusStrength   = 14,
            tier            = "S"
        ),

        TeamComposition(
            id              = "protect_the_carry",
            name            = "Protect the Carry",
            description     = "Hipercarry protegido por múltiplas camadas de escudos e cura. " +
                              "Scales infinitamente no late game.",
            archetype       = CompArchetype.PROTECT,
            requiredPicks   = listOf(
                // Hipercarries
                "Jinx", "Kaisa", "Aphelios", "Zeri", "Vayne",
                // Enchanters/peelers
                "Lulu", "Karma", "Soraka", "Yuumi", "Milio", "Nami", "Seraphine"
            ),
            minRequired     = 3,
            keyChampions    = listOf("Lulu", "Jinx", "Kaisa"),
            bonusStrength   = 13,
            tier            = "S"
        ),

        TeamComposition(
            id              = "poke_siege",
            name            = "Poke & Siege",
            description     = "Desgaste contínuo antes de lutas. Difícil de engajar sem tomar dano. " +
                              "Vence batendo em objetivos.",
            archetype       = CompArchetype.POKE,
            requiredPicks   = listOf(
                // Pokers principais
                "Jayce", "Xerath", "Varus", "Ezreal", "Caitlyn", "Nidalee", "Zoe", "Lux",
                // Suporte de poke
                "Karma", "Senna",
                // Wave clear / siege
                "Corki", "Gangplank"
            ),
            minRequired     = 3,
            keyChampions    = listOf("Jayce", "Xerath", "Varus"),
            bonusStrength   = 12,
            tier            = "S"
        ),

        TeamComposition(
            id              = "engage_snowball",
            name            = "Hard Engage Snowball",
            description     = "Engage agressivo e repetitivo. Ganha com CC em cadeia e " +
                              "pressão constante sobre o mapa.",
            archetype       = CompArchetype.ENGAGE,
            requiredPicks   = listOf(
                // Iniciadores hard CC
                "Leona", "Nautilus", "Malphite", "Amumu", "Sejuani", "Rakan", "Alistar",
                "Blitzcrank", "Thresh", "Wukong", "Jarvaniv", "Zac",
                // Follow-up de dano
                "Diana", "Kennen", "Orianna", "Yasuo", "Yone", "Sett"
            ),
            minRequired     = 3,
            keyChampions    = listOf("Leona", "Nautilus", "Malphite"),
            bonusStrength   = 12,
            tier            = "S"
        ),

        TeamComposition(
            id              = "dive_comp",
            name            = "Dive / Tower Dive",
            description     = "Pula no back-line inimigo ignorando frontline. " +
                              "Vence one-shotando carries antes da troca de dano.",
            archetype       = CompArchetype.DIVE,
            requiredPicks   = listOf(
                // Divers tradicionais
                "Diana", "Hecarim", "Wukong", "Camille", "Irelia", "Vi", "Xinzhao",
                "KhaZix", "Nocturne", "Kayn",
                // Carries que se beneficiam de dive
                "Kaisa", "Akali", "Fizz", "Ekko", "Yone"
            ),
            minRequired     = 3,
            keyChampions    = listOf("Hecarim", "Diana", "Wukong"),
            bonusStrength   = 13,
            tier            = "S"
        ),

        // ═════════════════════════════════════════════════════════════════
        // TIER A — COMPOSIÇÕES SÓLIDAS
        // ═════════════════════════════════════════════════════════════════

        TeamComposition(
            id              = "split_push",
            name            = "Split Push 1-3-1",
            description     = "Um splitpusher divide o mapa enquanto o resto faz objetivos. " +
                              "Força o oponente a tomar decisão difícil.",
            archetype       = CompArchetype.SPLIT,
            requiredPicks   = listOf(
                // Splitpushers
                "Fiora", "Camille", "Jax", "Riven", "Gangplank", "Akali", "Renekton",
                "Urgot", "Jayce", "Mordekaiser",
                // ADCs com TP/auto-suficientes
                "Sivir", "Vayne",
                // Suportes de map control
                "Senna", "Ashe"
            ),
            minRequired     = 2,
            keyChampions    = listOf("Fiora", "Camille", "Jax"),
            bonusStrength   = 10,
            tier            = "A"
        ),

        TeamComposition(
            id              = "pick_comp",
            name            = "Pick Composition",
            description     = "Assassinato isolado com alto burst. Uma abertura do oponente " +
                              "vira morte certa e vantagem numérica.",
            archetype       = CompArchetype.PICK,
            requiredPicks   = listOf(
                // Hooks/CC isolado
                "Blitzcrank", "Thresh", "Pyke", "Lissandra", "Ashe",
                // Assassinos de follow-up
                "Zed", "Talon", "KhaZix", "Evelynn", "LeBlanc", "Ahri",
                // Mages com pick power
                "TwistedFate", "Bard", "Nidalee"
            ),
            minRequired     = 3,
            keyChampions    = listOf("Blitzcrank", "Zed", "Pyke"),
            bonusStrength   = 10,
            tier            = "A"
        ),

        TeamComposition(
            id              = "azir_control",
            name            = "Mage Control / Teamfight",
            description     = "Controle absoluto de espaço e objetivos com zoning de mages. " +
                              "Azir/Orianna ditam o posicionamento inimigo.",
            archetype       = CompArchetype.CONTROL,
            requiredPicks   = listOf(
                // Mages de zoning
                "Azir", "Orianna", "Taliyah", "Viktor", "Anivia", "Cassiopeia", "Ryze",
                "Vladimir", "Lissandra", "Malzahar",
                // Damage / follow-up
                "Corki", "Syndra"
            ),
            minRequired     = 2,
            keyChampions    = listOf("Azir", "Orianna"),
            bonusStrength   = 10,
            tier            = "A"
        ),

        TeamComposition(
            id              = "hyperscaling",
            name            = "HyperScaling Late Game",
            description     = "Aguenta o early game e explode no late. " +
                              "Praticamente imbatível após 35 minutos.",
            archetype       = CompArchetype.SCALING,
            requiredPicks   = listOf(
                // Hyperscalers clássicos
                "Vayne", "Vladimir", "Jinx", "Senna", "Veigar",
                // Enablers de escala
                "Lulu", "Soraka", "Yuumi", "Karma"
            ),
            minRequired     = 2,
            keyChampions    = listOf("Vayne", "Veigar", "Vladimir"),
            bonusStrength   = 9,
            tier            = "A"
        ),

        TeamComposition(
            id              = "xayah_rakan",
            name            = "Xayah & Rakan Synergy",
            description     = "Dupla bot com sinergia nativa única. Rakan dança enquanto " +
                              "Xayah garante execução no teamfight.",
            archetype       = CompArchetype.ENGAGE,
            requiredPicks   = listOf("Xayah", "Rakan"),
            minRequired     = 2,
            keyChampions    = listOf("Xayah", "Rakan"),
            bonusStrength   = 9,
            tier            = "A"
        ),

        TeamComposition(
            id              = "zeri_enchanters",
            name            = "Zeri + Enchanters",
            description     = "Zeri escala com escudos e speed stacks. " +
                              "Com dois enchanters fica praticamente intocável.",
            archetype       = CompArchetype.PROTECT,
            requiredPicks   = listOf(
                "Zeri",
                "Lulu", "Karma", "Nami", "Seraphine", "Milio", "Yuumi", "Soraka"
            ),
            minRequired     = 2,
            keyChampions    = listOf("Zeri", "Lulu"),
            bonusStrength   = 9,
            tier            = "A"
        ),

        TeamComposition(
            id              = "skirmish_brawl",
            name            = "Skirmish / Brawl Comp",
            description     = "Brigas curtas 2v2 e 3v3 no mapa. " +
                              "Vence com micro mecânico e janelas de poder espalhadas.",
            archetype       = CompArchetype.SKIRMISH,
            requiredPicks   = listOf(
                // Skirmishers em todas as roles
                "Graves", "Ekko", "LeeSin", "Viego", "KhaZix", "Kayn", "Belveth",
                "Camille", "Riven", "Fiora", "Sett", "Irelia", "Akali",
                "Lucian", "Samira", "Nilah", "Pyke", "Senna"
            ),
            minRequired     = 3,
            keyChampions    = listOf("Graves", "LeeSin", "Ekko"),
            bonusStrength   = 10,
            tier            = "A"
        ),

        TeamComposition(
            id              = "early_invade",
            name            = "Early Invade Pressure",
            description     = "Pressiona o jungle inimigo desde o minuto 1. " +
                              "Snowball pesado se acertar a level 2/3 dive.",
            archetype       = CompArchetype.INVADE,
            requiredPicks   = listOf(
                // Early jungle dominators
                "LeeSin", "Xinzhao", "Elise", "Graves", "Nidalee", "RekSai",
                // Laners early game
                "Renekton", "Draven", "Lucian", "Caitlyn", "Zoe", "Syndra",
                // Suportes invader
                "Pyke", "Blitzcrank", "Leona"
            ),
            minRequired     = 3,
            keyChampions    = listOf("LeeSin", "Elise", "Draven"),
            bonusStrength   = 10,
            tier            = "A"
        ),

        TeamComposition(
            id              = "front_to_back",
            name            = "Front-to-Back Teamfight",
            description     = "Tank na frente, ADC atrás, mages no meio. " +
                              "Vitória por engenharia de teamfight, não por jogadas individuais.",
            archetype       = CompArchetype.PROTECT,
            requiredPicks   = listOf(
                // Frontline grande
                "Ornn", "Malphite", "Sejuani",
                // ADCs front-to-back
                "Aphelios", "Jinx", "Caitlyn", "Sivir",
                // Mid de range/teamfight
                "Orianna", "Azir", "Viktor", "Karma"
            ),
            minRequired     = 3,
            keyChampions    = listOf("Ornn", "Aphelios", "Jinx"),
            bonusStrength   = 11,
            tier            = "A"
        ),

        TeamComposition(
            id              = "global_ults",
            name            = "Global Pressure",
            description     = "Múltiplos ults globais permitem responder a qualquer luta no mapa. " +
                              "Força o inimigo a se manter sempre agrupado.",
            archetype       = CompArchetype.CONTROL,
            requiredPicks   = listOf(
                "TwistedFate", "Ashe", "Nocturne",
                "Senna", "Soraka", "Bard", "Galio", "Taliyah"
            ),
            minRequired     = 2,
            keyChampions    = listOf("TwistedFate", "Senna", "Galio"),
            bonusStrength   = 9,
            tier            = "A"
        ),

        TeamComposition(
            id              = "ardent_enchanters",
            name            = "Ardent Enchanters",
            description     = "ADC com speed-up e attack speed massivo via enchanters. " +
                              "Domina lutas longas.",
            archetype       = CompArchetype.PROTECT,
            requiredPicks   = listOf(
                // ADCs que se beneficiam de Ardent
                "Tristana", "Jinx", "Vayne", "Kaisa", "Aphelios", "Zeri",
                // Enchanters com Ardent
                "Nami", "Soraka", "Lulu", "Karma", "Milio", "Seraphine", "Yuumi"
            ),
            minRequired     = 2,
            keyChampions    = listOf("Nami", "Lulu", "Milio"),
            bonusStrength   = 9,
            tier            = "A"
        ),

        TeamComposition(
            id              = "objective_control",
            name            = "Objective Control",
            description     = "Tudo girando em torno de drakes e Baron. " +
                              "Vence pelo controle de monstros do mapa, não lutando.",
            archetype       = CompArchetype.CONTROL,
            requiredPicks   = listOf(
                // Smites poderosos
                "Shyvana", "Belveth", "Hecarim",
                // Setup de visão/objetivo
                "Ashe", "Caitlyn", "Senna", "Bard",
                // Mages com DPS sustained
                "Cassiopeia", "Azir"
            ),
            minRequired     = 3,
            keyChampions    = listOf("Shyvana", "Belveth", "Ashe"),
            bonusStrength   = 9,
            tier            = "A"
        ),

        // ═════════════════════════════════════════════════════════════════
        // TIER B — COMPOSIÇÕES SITUACIONAIS
        // ═════════════════════════════════════════════════════════════════

        TeamComposition(
            id              = "double_adc",
            name            = "Double ADC",
            description     = "Dois carries físicos para devastar tanques. " +
                              "Forte contra stacks de armadura.",
            archetype       = CompArchetype.CONTROL,
            requiredPicks   = listOf(
                // Bot lane double-AD
                "Lucian", "Senna", "Kalista", "Jinx", "MissFortune", "Draven",
                // Mid "ADC" picks
                "Tristana", "Corki", "Vayne",
                // Top lane carry AD
                "Camille", "Jax", "Fiora"
            ),
            minRequired     = 3,
            keyChampions    = listOf("Lucian", "Senna", "Kalista"),
            bonusStrength   = 7,
            tier            = "B"
        ),

        TeamComposition(
            id              = "peel_comp",
            name            = "Full Peel / Protect ADC",
            description     = "Máximo de peeling para um ADC farmar e dominar o late. " +
                              "Thresh + Janna + tank front.",
            archetype       = CompArchetype.PEEL,
            requiredPicks   = listOf(
                // Peelers
                "Thresh", "Nautilus", "Lulu", "Alistar",
                // Carries que precisam de peel
                "Jinx", "Vayne"
            ),
            minRequired     = 3,
            keyChampions    = listOf("Thresh", "Lulu", "Nautilus"),
            bonusStrength   = 7,
            tier            = "B"
        ),

        TeamComposition(
            id              = "yone_yasuo",
            name            = "Yone + Yasuo Knockup",
            description     = "Knockout combo: qualquer knockup ativa o ult de Yasuo/Yone. " +
                              "Terrível para receber, divertido de executar.",
            archetype       = CompArchetype.WOMBO,
            requiredPicks   = listOf(
                "Yasuo", "Yone",
                // Fontes de knockup
                "Malphite", "Wukong", "Jarvaniv", "Alistar", "Sett", "Gnar",
                "Nautilus", "Azir", "Zac", "Diana", "Tristana"
            ),
            minRequired     = 2,
            keyChampions    = listOf("Yasuo", "Yone"),
            bonusStrength   = 8,
            tier            = "B"
        ),

        TeamComposition(
            id              = "ap_burst",
            name            = "AP Burst / Assassin Mid",
            description     = "Dois assassinos AP que one-shotam carries. " +
                              "Alta pressão de pick e roam.",
            archetype       = CompArchetype.PICK,
            requiredPicks   = listOf(
                "Zoe", "LeBlanc", "Fizz", "Ekko", "Katarina", "Akali", "Ahri", "Diana",
                "Annie", "Veigar", "Syndra", "Talon", "Evelynn"
            ),
            minRequired     = 2,
            keyChampions    = listOf("Zoe", "LeBlanc", "Katarina"),
            bonusStrength   = 7,
            tier            = "B"
        ),

        TeamComposition(
            id              = "vision_control",
            name            = "Vision Control / Siege",
            description     = "Controle de visão superior e siege de torres. " +
                              "Vence apertando o mapa sem precisar lutar.",
            archetype       = CompArchetype.CONTROL,
            requiredPicks   = listOf(
                "Caitlyn", "Jayce", "Jhin", "Karma", "Lux", "Senna", "Ashe",
                "Bard", "Zilean"
            ),
            minRequired     = 3,
            keyChampions    = listOf("Caitlyn", "Jhin", "Karma"),
            bonusStrength   = 7,
            tier            = "B"
        ),

        TeamComposition(
            id              = "tank_stack",
            name            = "Tank Stack",
            description     = "4-5 tanks que não morrem. " +
                              "Vence em chip damage e teamfight prolongado.",
            archetype       = CompArchetype.ENGAGE,
            requiredPicks   = listOf(
                // Tanks de todas as roles
                "Ornn", "Malphite", "Sejuani", "Zac", "Amumu",
                "Galio", "Nautilus", "Leona", "Alistar",
                // Carries de tank-comp
                "Senna", "Veigar"
            ),
            minRequired     = 4,
            keyChampions    = listOf("Ornn", "Sejuani", "Malphite"),
            bonusStrength   = 8,
            tier            = "B"
        ),

        TeamComposition(
            id              = "true_damage_shred",
            name            = "True Damage / Anti-Tank",
            description     = "Stack de dano real e %max HP. " +
                              "Especificamente desenhado contra tank-comp adversária.",
            archetype       = CompArchetype.CONTROL,
            requiredPicks   = listOf(
                "Vayne", "Fiora", "Camille", "Darius", "Garen",
                "Senna", "Kaisa"
            ),
            minRequired     = 3,
            keyChampions    = listOf("Vayne", "Fiora", "Darius"),
            bonusStrength   = 8,
            tier            = "B"
        ),

        TeamComposition(
            id              = "speed_comp",
            name            = "Speed / Kite Comp",
            description     = "Movement speed massivo. Pega lutas onde quer e " +
                              "foge das que não quer.",
            archetype       = CompArchetype.SKIRMISH,
            requiredPicks   = listOf(
                "Hecarim", "Rakan", "Sivir", "Zeri", "Talon", "Ahri", "Ezreal"
            ),
            minRequired     = 3,
            keyChampions    = listOf("Hecarim", "Sivir", "Rakan"),
            bonusStrength   = 8,
            tier            = "B"
        ),

        TeamComposition(
            id              = "all_in_dive_bot",
            name            = "Bot Lane All-In 2v2",
            description     = "Burst infinito na bot lane na level 2-3. " +
                              "Snowball total se converter primeira luta.",
            archetype       = CompArchetype.DIVE,
            requiredPicks   = listOf(
                // ADCs all-in
                "Draven", "Lucian", "Samira", "Kalista", "Tristana", "Nilah",
                // Suportes all-in
                "Leona", "Nautilus", "Pyke", "Rakan", "Blitzcrank", "Thresh"
            ),
            minRequired     = 2,
            keyChampions    = listOf("Draven", "Lucian", "Leona"),
            bonusStrength   = 8,
            tier            = "B"
        ),

        TeamComposition(
            id              = "flex_swap",
            name            = "Flex Pick / Position Swap",
            description     = "Picks que jogam em múltiplas roles, confundindo o ban-phase. " +
                              "Karma top, Galio sup, Yasuo bot, etc.",
            archetype       = CompArchetype.CONTROL,
            requiredPicks   = listOf(
                "Karma", "Galio", "Sett", "Yasuo", "Lucian", "Senna",
                "Veigar", "Pyke", "Vladimir", "Gragas"
            ),
            minRequired     = 3,
            keyChampions    = listOf("Karma", "Sett", "Yasuo"),
            bonusStrength   = 7,
            tier            = "B"
        ),

        TeamComposition(
            id              = "disengage_kite",
            name            = "Disengage / Kite",
            description     = "Stack de ferramentas de disengage. " +
                              "Inimigo não consegue engajar; quem manda o ritmo é você.",
            archetype       = CompArchetype.PEEL,
            requiredPicks   = listOf(
                "Karma", "Gragas", "Anivia", "Lissandra", "Poppy",
                "Sivir", "Ezreal", "Lulu", "Bard"
            ),
            minRequired     = 3,
            keyChampions    = listOf("Karma", "Gragas", "Lissandra"),
            bonusStrength   = 7,
            tier            = "B"
        ),

        TeamComposition(
            id              = "executioner",
            name            = "Execute / Finisher",
            description     = "Múltiplas habilidades de execução garantem que ninguém sai vivo. " +
                              "Cleanup absoluto em teamfight.",
            archetype       = CompArchetype.PICK,
            requiredPicks   = listOf(
                "Darius", "Garen", "Pyke", "Urgot", "KhaZix", "Veigar", "Naafiri",
                "Talon", "Tristana", "Riven", "Samira"
            ),
            minRequired     = 3,
            keyChampions    = listOf("Darius", "Pyke", "Veigar"),
            bonusStrength   = 7,
            tier            = "B"
        ),

        TeamComposition(
            id              = "sustain_battle",
            name            = "Sustain Battle / Drain Tank",
            description     = "Sustenta luta longa com lifesteal/heals massivos. " +
                              "Vence pelo desgaste e não pelo burst.",
            archetype       = CompArchetype.SCALING,
            requiredPicks   = listOf(
                "Aatrox", "Mordekaiser", "Vladimir", "Sett", "Soraka", "Yuumi", "Senna"
            ),
            minRequired     = 3,
            keyChampions    = listOf("Aatrox", "Soraka", "Vladimir"),
            bonusStrength   = 7,
            tier            = "B"
        ),

        TeamComposition(
            id              = "counter_engage",
            name            = "Counter-Engage",
            description     = "Espera o inimigo engajar e vira a luta com hard CC reativo. " +
                              "Punisher de comps de engage.",
            archetype       = CompArchetype.PEEL,
            requiredPicks   = listOf(
                "Orianna", "Anivia", "Lissandra", "Gragas", "Lulu",
                "Sivir", "Sejuani", "Veigar"
            ),
            minRequired     = 3,
            keyChampions    = listOf("Orianna", "Lissandra", "Lulu"),
            bonusStrength   = 8,
            tier            = "B"
        ),

        TeamComposition(
            id              = "invisible_pick",
            name            = "Invisibility / Vision Denial",
            description     = "Campeões invisíveis caçam sozinhos no mapa. " +
                              "Inimigo precisa de control wards para não ser pickado.",
            archetype       = CompArchetype.PICK,
            requiredPicks   = listOf(
                "Evelynn", "Akali", "Talon", "KhaZix",
                "Pyke", "Senna", "Teemo", "Vayne", "Wukong"
            ),
            minRequired     = 3,
            keyChampions    = listOf("Evelynn", "Akali", "Talon"),
            bonusStrength   = 7,
            tier            = "B"
        )
    )
}
