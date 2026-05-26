package com.cblol.scout.util

import com.cblol.scout.data.ChampionTag
import com.cblol.scout.data.CompAnalysisResult
import com.cblol.scout.data.StaticData
import com.cblol.scout.data.TeamComposition
import com.cblol.scout.domain.GameConstants

/**
 * Repositório de composições e motor de análise de sinergia.
 *
 * **Origem dos dados**: as composições NÃO ficam mais hardcoded aqui — vivem no
 * banco **Realm criptografado** e são lidas via [StaticData] pela propriedade
 * [all]. Os dados de seed estão em [com.cblol.scout.data.seed.CompositionSeed]
 * (usados só para popular o Realm na primeira execução). Esta classe mantém
 * apenas a LÓGICA de análise/sugestão (regra de jogo, não dado).
 *
 * COMPOSIÇÕES CORINGA (Tier S — bônus máximo, difíceis de banir completamente):
 *   As composições Tier S representam as "god comps" do meta competitivo 2026.
 *   Banir apenas 1 campeão-chave as desmonta.
 *
 * LÓGICA DE BÔNUS NO SIMULADOR:
 *   - Composição detectada nos picks → bonusStrength adicionado ao teamStrength
 *   - Composição do oponente detectada nos picks adversários → mesmo bônus para eles
 *   - Campeões-chave banidos antes do oponente pickear → bônus é bloqueado
 */
object CompositionRepository {

    /** Todas as composições, lidas do banco Realm via [StaticData]. */
    val all: List<TeamComposition> get() = StaticData.source.allCompositions()

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

            // Uma comp só está "online" se ao menos UM de seus campeões-chave
            // foi pickado. Senão, os picks até batem no mínimo numérico, mas o
            // núcleo que define a composição não está presente (ex: "Yone+Yasuo"
            // sem Yone nem Yasuo no time não é a comp Yone+Yasuo).
            val keyPresent = comp.keyChampions.any { key -> key.lowercase() in picksLower }
            if (!keyPresent) continue

            // "Time cheio" comprometido com a comp = 5 picks (ou o total de
            // required, se a comp listar menos). Usar este alvo (em vez do
            // tamanho bruto de requiredPicks, que pode ter 13-15 opções) evita
            // punir comps com lista ampla de campeões elegíveis.
            val fullTarget = minOf(5, required.size)
            val bonus = when {
                matched.size >= fullTarget       -> comp.bonusStrength
                matched.size <= comp.minRequired -> comp.bonusStrength / 2
                else -> {
                    // Interpolação linear entre metade (no minRequired) e cheio
                    // (no fullTarget).
                    val half = comp.bonusStrength / 2
                    val span = (fullTarget - comp.minRequired).coerceAtLeast(1)
                    val progress = matched.size - comp.minRequired
                    half + (comp.bonusStrength - half) * progress / span
                }
            }
            val originalNames = picks.filter { it.lowercase() in matched }
            val keyMatched = comp.keyChampions.count { it.lowercase() in picksLower }
            results += DetectedComp(comp, originalNames, bonus, keyMatched)
        }
        // Ordena primeiro pela FRAÇÃO de campeões-chave presentes (uma comp com
        // todos os seus key champions é mais "a comp" do que outra que só
        // tangencia o mínimo) e, em empate, pelo bônus efetivo. Isso faz, por
        // ex., "Yone+Yasuo" (2/2 keys) ganhar de "Wombo" (1/3 keys) quando os
        // picks são Yasuo+Yone+Malphite.
        return results.sortedWith(
            compareByDescending<DetectedComp> { it.keyMatchRatio }
                .thenByDescending { it.bonus }
        )
    }

    /** Uma composição detectada nos picks atuais, com seu bônus efetivo. */
    data class DetectedComp(
        val composition: TeamComposition,
        val matched: List<String>,
        val bonus: Int,
        /** Quantos dos [TeamComposition.keyChampions] estão entre os picks. */
        val keyMatched: Int = 0
    ) {
        val percent: Int get() =
            (matched.size.toFloat() / composition.requiredPicks.size * 100).toInt()

        /** Fração (0-1) de campeões-chave presentes — usada para ranquear comps. */
        val keyMatchRatio: Float get() =
            if (composition.keyChampions.isEmpty()) 0f
            else keyMatched.toFloat() / composition.keyChampions.size
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
}
