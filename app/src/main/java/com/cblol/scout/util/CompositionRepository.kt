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
     * Analisa uma lista de picks e retorna a composição detectada com maior sinergia.
     * Também verifica tags funcionais: times com 3+ TANK recebem aviso de ANTI_TANK,
     * times com 3+ HEAL recebem aviso de ignite pressure, etc.
     */
    fun analyze(picks: List<String>, bans: List<String> = emptyList()): CompAnalysisResult {
        val picksLower = picks.map { it.lowercase() }

        var best: TeamComposition? = null
        var bestMatched = emptyList<String>()
        var bestBonus = 0

        for (comp in all) {
            val required = comp.requiredPicks.map { it.lowercase() }
            val matched = required.filter { it in picksLower }

            if (matched.size < comp.minRequired) continue

            // Verifica se algum campeão-chave foi banado (pelo oponente) — quebra o bônus
            val keyBanned = comp.keyChampions.any { key ->
                bans.any { ban -> ban.lowercase() == key.lowercase() }
            }
            if (keyBanned) continue

            val bonus = when {
                matched.size >= required.size       -> comp.bonusStrength          // comp completa
                matched.size == comp.minRequired    -> comp.bonusStrength / 2      // comp parcial
                else                                -> comp.bonusStrength * matched.size / required.size
            }

            if (bonus > bestBonus) {
                bestBonus   = bonus
                best        = comp
                bestMatched = matched
            }
        }

        val desc = if (best != null) {
            val pct = (bestMatched.size.toFloat() / best.requiredPicks.size * 100).toInt()
            "${best.name} ($pct% montada) · +$bestBonus força"
        } else {
            "Sem sinergia detectada"
        }

        return CompAnalysisResult(best, bestMatched, bestBonus, desc)
    }

    /**
     * Análise enriquecida com tags funcionais dos campeões picados.
     * Considera equilíbrio de dano, CC, frontline, anti-tank, etc.
     */
    fun analyzeWithTags(
        picks: List<String>,
        opponentPicks: List<String> = emptyList(),
        bans: List<String> = emptyList()
    ): TaggedAnalysisResult {
        val base     = analyze(picks, bans)
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

        return TaggedAnalysisResult(
            base       = base,
            extraBonus = extra,
            totalBonus = (base.bonusStrength + extra).coerceAtLeast(0),
            insights   = insights
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
        val insights: List<String>
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
