package com.cblol.scout.util

import com.cblol.scout.data.ChampionTag
import com.cblol.scout.data.CompAnalysisResult
import com.cblol.scout.data.CompArchetype
import com.cblol.scout.data.TeamComposition

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
     * Campeões que aparecem em várias comps ("coringas") naturalmente impulsionam
     * mais de uma sinergia ao mesmo tempo.
     */
    fun analyzeAll(picks: List<String>, bans: List<String> = emptyList()): List<DetectedComp> {
        val picksLower = picks.map { it.lowercase() }
        val results = mutableListOf<DetectedComp>()

        for (comp in all) {
            val required = comp.requiredPicks.map { it.lowercase() }
            val matched  = required.filter { it in picksLower }
            if (matched.size < comp.minRequired) continue

            // Algum campeão-chave foi banido → comp neutralizada (sem bônus)
            val keyBanned = comp.keyChampions.any { key ->
                bans.any { ban -> ban.lowercase() == key.lowercase() }
            }
            if (keyBanned) continue

            val bonus = when {
                matched.size >= required.size    -> comp.bonusStrength          // comp completa
                matched.size == comp.minRequired -> comp.bonusStrength / 2      // mínimo atingido
                else                              -> comp.bonusStrength * matched.size / required.size
            }
            // Preserva o case original dos picks que casaram
            val originalNames = picks.filter { it.lowercase() in matched }
            results += DetectedComp(comp, originalNames, bonus)
        }
        return results.sortedByDescending { it.bonus }
    }

    /** Uma composição detectada nos picks atuais, com seu bônus efetivo. */
    data class DetectedComp(
        val composition: TeamComposition,
        val matched: List<String>,   // campeões picados que casam com requiredPicks
        val bonus: Int               // bônus efetivo após considerar parcialidade
    ) {
        val percent: Int get() =
            (matched.size.toFloat() / composition.requiredPicks.size * 100).toInt()
    }

    /**
     * Análise enriquecida com tags funcionais + múltiplas composições simultâneas.
     *
     * Soma bônus de TODAS as composições detectadas com diminishing returns:
     * a primeira (maior) entra cheia, a segunda com 60%, a terceira com 30%.
     * Isso impede que um único pick coringa exploite stacking infinito,
     * mas premia composições genuinamente híbridas (ex: engage + wombo).
     */
    fun analyzeWithTags(
        picks: List<String>,
        opponentPicks: List<String> = emptyList(),
        bans: List<String> = emptyList()
    ): TaggedAnalysisResult {
        val detectedComps = analyzeAll(picks, bans)
        // Bônus combinado com diminishing returns
        val combinedBonus = detectedComps.foldIndexed(0) { idx, acc, comp ->
            val factor = when (idx) {
                0    -> 1.0      // comp principal: 100%
                1    -> 0.6      // segunda comp: 60%
                2    -> 0.3      // terceira comp: 30%
                else -> 0.15     // demais: 15% cada
            }
            acc + (comp.bonus * factor).toInt()
        }

        val primary = detectedComps.firstOrNull()
        val baseDesc = when {
            detectedComps.isEmpty()  -> "Sem sinergia detectada"
            detectedComps.size == 1  -> primary!!.let { "${it.composition.name} (${it.percent}%) · +${it.bonus} força" }
            else                     -> {
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
        if (hasAD && hasAP) { extra += 2; insights += "✅ Dano misto (AD+AP)" }

        val ccCount = myChamps.count { it.hasTag(ChampionTag.CROWD_CONTROL) || it.hasTag(ChampionTag.ENGAGE) }
        if (ccCount >= 3) { extra += 3; insights += "✅ $ccCount fontes de CC" }

        val knockUps = myChamps.count { it.hasTag(ChampionTag.KNOCK_UP) }
        if (knockUps >= 2) { extra += 2; insights += "✅ $knockUps knock-ups (sinergia Yasuo/Yone)" }

        val healers = myChamps.count { it.hasTag(ChampionTag.HEAL) || it.hasTag(ChampionTag.SHIELD) }
        if (healers >= 3) { extra += 2; insights += "✅ $healers fontes de cura/escudo" }

        val tanks = myChamps.count { it.hasTag(ChampionTag.TANK) }
        val squishies = myChamps.count { it.hasTag(ChampionTag.MARKSMAN) || it.hasTag(ChampionTag.MAGE) }
        if (tanks == 0 && squishies >= 3) { extra -= 3; insights += "⚠️ Sem frontline, vulnerável a engage" }

        val opTanks = opChamps.count { it.hasTag(ChampionTag.TANK) || it.hasTag(ChampionTag.FIGHTER) }
        val antiTank = myChamps.count { it.hasTag(ChampionTag.ANTI_TANK) || it.hasTag(ChampionTag.TRUE_DAMAGE) }
        if (opTanks >= 3 && antiTank == 0) { extra -= 2; insights += "⚠️ Sem anti-tank contra frontline pesada" }
        if (opTanks >= 2 && antiTank >= 2)  { extra += 2; insights += "✅ Bom anti-tank" }

        val opHyper = opChamps.count { it.hasTag(ChampionTag.HYPERCARRY) }
        val myEarly = myChamps.count { it.hasTag(ChampionTag.EARLY_GAME) }
        if (opHyper >= 2 && myEarly == 0) { extra -= 1; insights += "⚠️ Oponente escala muito, force lutas cedo" }

        // Insight extra quando múltiplas composições se sobrepõem
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
        /** Todas as composições detectadas, ordenadas por bônus decrescente. */
        val detectedComps: List<DetectedComp> = emptyList()
    )

    data class SuggestedPick(
        val championId: String,
        val score: Int,
        val reasons: List<String>
    )

    /**
     * Retorna a lista de composições que um conjunto de bans consegue neutralizar.
     * Útil para sugerir bans ao jogador.
     */
    fun compsNeutralizedBy(bans: List<String>): List<TeamComposition> {
        val bansLower = bans.map { it.lowercase() }
        return all.filter { comp ->
            comp.keyChampions.any { key -> key.lowercase() in bansLower }
        }
    }

    /**
     * Retorna sugestões de bans para neutralizar composições perigosas do oponente,
     * ordenadas por prioridade (tier S primeiro, depois A, depois B).
     */
    fun suggestBans(opponentPicks: List<String> = emptyList()): List<Pair<String, String>> {
        // Prioriza bans por tier e bonusStrength
        val priority = all.sortedWith(
            compareByDescending<TeamComposition> { it.tier }
                .thenByDescending { it.bonusStrength }
        )
        val suggestions = mutableListOf<Pair<String, String>>()
        val added = mutableSetOf<String>()

        for (comp in priority) {
            for (key in comp.keyChampions) {
                if (key !in added && suggestions.size < 10) {
                    suggestions += key to "Quebra ${comp.name} (Tier ${comp.tier})"
                    added += key
                }
            }
        }
        return suggestions
    }

    // ─────────────────────────────────────────────────────────────────────
    private fun buildComps(): List<TeamComposition> = listOf(

        // ══════════════════════════════════════════════════════════════════
        // TIER S — COMPOSIÇÕES CORINGA (bônus máximo, meta 2026)
        // ══════════════════════════════════════════════════════════════════

        TeamComposition(
            id              = "wombo_combo",
            name            = "Wombo Combo",
            description     = "AoE devastador: ulti encadeado que apaga o time inimigo em segundos. " +
                              "Requer posicionamento e ativação simultânea.",
            archetype       = CompArchetype.WOMBO,
            requiredPicks   = listOf("Malphite", "MissFortune", "Orianna", "Amumu", "Jarvaniv"),
            minRequired     = 3,
            keyChampions    = listOf("Malphite", "Orianna", "Amumu"),
            bonusStrength   = 14,
            tier            = "S"
        ),

        TeamComposition(
            id              = "protect_the_carry",
            name            = "Protect the Carry",
            description     = "Hipercarry protegido por múltiplas camadas de escudos e cura. " +
                              "Scales infinitamente no late game.",
            archetype       = CompArchetype.PROTECT,
            requiredPicks   = listOf("Jinx", "Lulu", "Karma", "Zilean", "Kaisa"),
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
            requiredPicks   = listOf("Jayce", "Ezreal", "Xerath", "Varus", "Karma"),
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
            requiredPicks   = listOf("Leona", "Nautilus", "Malphite", "Wukong", "Sejuani"),
            minRequired     = 3,
            keyChampions    = listOf("Leona", "Nautilus", "Malphite"),
            bonusStrength   = 12,
            tier            = "S"
        ),

        // ══════════════════════════════════════════════════════════════════
        // TIER A — COMPOSIÇÕES SÓLIDAS
        // ══════════════════════════════════════════════════════════════════

        TeamComposition(
            id              = "split_push",
            name            = "Split Push + Peel",
            description     = "Um splitpusher divide o mapa enquanto o resto faz objetivos. " +
                              "Força o oponente a tomar decisão difícil.",
            archetype       = CompArchetype.SPLIT,
            requiredPicks   = listOf("Fiora", "Camille", "Tryndamere", "Nasus", "Jax"),
            minRequired     = 2,
            keyChampions    = listOf("Fiora", "Camille", "Tryndamere"),
            bonusStrength   = 10,
            tier            = "A"
        ),

        TeamComposition(
            id              = "pick_comp",
            name            = "Pick Composition",
            description     = "Assassinato isolado com alto burst. Uma abertura do oponente " +
                              "vira morte certa e vantagem numérica.",
            archetype       = CompArchetype.PICK,
            requiredPicks   = listOf("Blitzcrank", "Zed", "Pyke", "Lissandra", "Ahri"),
            minRequired     = 3,
            keyChampions    = listOf("Blitzcrank", "Zed", "Pyke"),
            bonusStrength   = 10,
            tier            = "A"
        ),

        TeamComposition(
            id              = "azir_control",
            name            = "Azir Control / Teamfight",
            description     = "Controle absoluto de visão e objetivos. " +
                              "Azir no centro com soldados ditando o posicionamento inimigo.",
            archetype       = CompArchetype.CONTROL,
            requiredPicks   = listOf("Azir", "Orianna", "Taliyah", "Viktor", "Corki"),
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
            requiredPicks   = listOf("Kassadin", "Vayne", "Kayle", "Nasus", "Veigar"),
            minRequired     = 2,
            keyChampions    = listOf("Kassadin", "Vayne", "Kayle"),
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
            requiredPicks   = listOf("Zeri", "Lulu", "Karma", "Nami", "Seraphine"),
            minRequired     = 2,
            keyChampions    = listOf("Zeri", "Lulu"),
            bonusStrength   = 9,
            tier            = "A"
        ),

        // ══════════════════════════════════════════════════════════════════
        // TIER B — COMPOSIÇÕES SITUACIONAIS
        // ══════════════════════════════════════════════════════════════════

        TeamComposition(
            id              = "double_adc",
            name            = "Double ADC",
            description     = "Dois carries físicos para devastar tanques. " +
                              "Forte contra stacks de armadura.",
            archetype       = CompArchetype.CONTROL,
            requiredPicks   = listOf("Lucian", "Senna", "Kalista", "Jinx", "MissFortune"),
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
            requiredPicks   = listOf("Thresh", "Janna", "Nautilus", "Lulu", "Alistar"),
            minRequired     = 3,
            keyChampions    = listOf("Thresh", "Janna"),
            bonusStrength   = 7,
            tier            = "B"
        ),

        TeamComposition(
            id              = "yone_yasuo",
            name            = "Yone + Yasuo Knockup",
            description     = "Knockout combo: qualquer knockup ativa o ult de Yasuo/Yone. " +
                              "Terrível para receber, divertido de executar.",
            archetype       = CompArchetype.WOMBO,
            requiredPicks   = listOf("Yasuo", "Yone", "Malphite", "Wukong", "Jarvaniv"),
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
            requiredPicks   = listOf("Zoe", "LeBlanc", "Fizz", "Ekko", "Katarina"),
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
            requiredPicks   = listOf("Caitlyn", "Jayce", "Jhin", "Karma", "Lux"),
            minRequired     = 3,
            keyChampions    = listOf("Caitlyn", "Jhin", "Karma"),
            bonusStrength   = 7,
            tier            = "B"
        )
    )
}
