package com.cblol.scout.domain.usecase

import com.cblol.scout.data.CoachProfile
import com.cblol.scout.domain.GameConstants
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Serviço puro de domínio para derivar level, atributos e título do técnico
 * a partir de um [CoachProfile].
 *
 * **Não tem dependências de Android nem do GameRepository** — só lê o profile
 * e retorna um snapshot calculado. Isso permite testar tudo em JVM e mantém
 * Clean Architecture (camada de domínio limpa).
 *
 * SOLID:
 * - **SRP**: cada função tem uma responsabilidade (level / xp / atributo / título).
 * - **OCP**: novos atributos podem ser adicionados ao [CoachStats] sem mexer no
 *   resto do código.
 * - **DIP**: depende só de [CoachProfile] e [GameConstants], não de framework.
 */
object CoachProgressionService {

    /**
     * Snapshot calculado pronto para a UI exibir.
     * Tudo é derivado do [CoachProfile] no momento da chamada.
     */
    data class CoachStats(
        val name: String,
        val level: Int,
        val title: String,
        val currentXp: Int,
        val xpForCurrentLevel: Int,
        val xpForNextLevel: Int,
        val progressToNextLevel: Float,   // 0..1, fração até o próximo level

        // Atributos derivados (1-99)
        val leadership: Int,        // Liderança — vitórias e séries ganhas
        val drafting: Int,          // Drafting — pick & bans manuais conduzidos
        val financialMgmt: Int,     // Gestão Financeira — equilíbrio de gastos
        val talentEye: Int,         // Olho para Talentos — contratações + vendas lucrativas
        val reputation: Int,        // Reputação — média ponderada

        // Estatísticas brutas (para a UI mostrar)
        val mapsWon: Int,
        val mapsLost: Int,
        val seriesWon: Int,
        val seriesLost: Int,
        val playersHired: Int,
        val playersSold: Int,
        val manualPickBansDone: Int,
        val winRate: Int            // % de mapas vencidos (0-100)
    )

    /** Computa o snapshot completo.
     *
     *  Aceita `profile` nullable como blindagem defensiva: saves antigos
     *  podem chegar com `coachProfile == null` antes do `migrateLoaded` rodar.
     *  Quando null, retorna um snapshot do perfil padrão (level 1, 0 XP).
     */
    fun compute(profile: CoachProfile?, name: String): CoachStats {
        val safe          = profile ?: CoachProfile()
        val level         = levelFor(safe.xp)
        val xpCurr        = xpRequiredFor(level)
        val xpNext        = xpRequiredFor(level + 1)
        val progress      = if (xpNext == xpCurr) 1f
                            else ((safe.xp - xpCurr).toFloat() / (xpNext - xpCurr)).coerceIn(0f, 1f)
        val totalGames    = safe.mapsWon + safe.mapsLost
        val winRate       = if (totalGames == 0) 0
                            else (safe.mapsWon * 100 / totalGames)

        return CoachStats(
            name                = name,
            level               = level,
            title               = titleFor(level),
            currentXp           = safe.xp,
            xpForCurrentLevel   = xpCurr,
            xpForNextLevel      = xpNext,
            progressToNextLevel = progress,
            leadership          = leadershipFor(safe),
            drafting            = draftingFor(safe),
            financialMgmt       = financialFor(safe),
            talentEye           = talentFor(safe),
            reputation          = safe.reputation.coerceIn(GameConstants.Coach.ATTR_MIN, GameConstants.Coach.ATTR_MAX),
            mapsWon             = safe.mapsWon,
            mapsLost            = safe.mapsLost,
            seriesWon           = safe.seriesWon,
            seriesLost          = safe.seriesLost,
            playersHired        = safe.playersHired,
            playersSold         = safe.playersSold,
            manualPickBansDone  = safe.manualPickBansDone,
            winRate             = winRate
        )
    }

    // ── Cálculos individuais ─────────────────────────────────────────────

    /** Level a partir do XP usando curva quadrática inversa. */
    fun levelFor(xp: Int): Int {
        // xp = level^2 * factor  →  level = sqrt(xp/factor)
        val level = sqrt((xp.toDouble() / GameConstants.Coach.LEVEL_XP_FACTOR)).toInt() + 1
        return level.coerceIn(1, GameConstants.Coach.MAX_LEVEL)
    }

    /** XP total necessário para alcançar o `level` informado. */
    fun xpRequiredFor(level: Int): Int {
        if (level <= 1) return 0
        val capped = level.coerceAtMost(GameConstants.Coach.MAX_LEVEL + 1)
        val l = capped - 1
        return l * l * GameConstants.Coach.LEVEL_XP_FACTOR
    }

    /** Título cosmético baseado no level. Adicionar faixas é OCP-friendly. */
    fun titleFor(level: Int): String = when {
        level >= 25 -> "Lendário"
        level >= 20 -> "Hall da Fama"
        level >= 15 -> "Veterano"
        level >= 10 -> "Experiente"
        level >= 7  -> "Profissional"
        level >= 4  -> "Promissor"
        level >= 2  -> "Iniciante"
        else        -> "Estreante"
    }

    // ── Atributos derivados (1-99) ──────────────────────────────────────

    /** Liderança = base + bônus por séries vencidas + bônus por mapas vencidos. */
    private fun leadershipFor(p: CoachProfile): Int = clampAttr(
        GameConstants.Coach.ATTR_BASE + p.seriesWon * 4 + p.mapsWon * 2 - p.seriesLost * 1
    )

    /** Drafting = base + bônus pesado por pick & bans manuais conduzidos. */
    private fun draftingFor(p: CoachProfile): Int = clampAttr(
        GameConstants.Coach.ATTR_BASE + p.manualPickBansDone * 3
    )

    /**
     * Gestão Financeira = base + bônus pela diferença líquida de transferências.
     * Vender por mais do que comprou aumenta; o contrário diminui.
     */
    private fun financialFor(p: CoachProfile): Int {
        val net = p.totalEarned - p.totalSpent
        // Cada 100k de saldo positivo = +1; saldo negativo de 100k = -1
        val bonus = (net / 100_000L).toInt()
        return clampAttr(GameConstants.Coach.ATTR_BASE + bonus + p.contractsRenewed * 2)
    }

    /** Olho para Talentos = base + bônus por contratações + bônus menor por vendas. */
    private fun talentFor(p: CoachProfile): Int = clampAttr(
        GameConstants.Coach.ATTR_BASE + p.playersHired * 5 + p.playersSold * 2
    )

    private fun clampAttr(value: Int): Int = value.coerceIn(
        GameConstants.Coach.ATTR_MIN, GameConstants.Coach.ATTR_MAX
    )

    // ── Mutação do profile (chamada pelo CareerUseCases) ────────────────

    /** Registra resultado de um mapa do split, retornando XP ganho. */
    fun recordMapResult(profile: CoachProfile?, playerWon: Boolean): Int {
        if (profile == null) return 0
        val xpGain: Int
        if (playerWon) {
            profile.mapsWon += 1
            xpGain = GameConstants.CoachXp.WIN_MAP
            profile.reputation = min(GameConstants.Coach.ATTR_MAX, profile.reputation + 1)
        } else {
            profile.mapsLost += 1
            xpGain = GameConstants.CoachXp.LOSE_MAP
            profile.reputation = (profile.reputation - 1).coerceAtLeast(GameConstants.Coach.ATTR_MIN)
        }
        profile.xp += xpGain
        return xpGain
    }

    /** Registra resultado de uma série BO3, retornando XP ganho. */
    fun recordSeriesResult(profile: CoachProfile?, playerWon: Boolean): Int {
        if (profile == null) return 0
        val xpGain: Int
        if (playerWon) {
            profile.seriesWon += 1
            xpGain = GameConstants.CoachXp.WIN_SERIES
            profile.reputation = min(GameConstants.Coach.ATTR_MAX, profile.reputation + 3)
        } else {
            profile.seriesLost += 1
            xpGain = GameConstants.CoachXp.LOSE_SERIES
            profile.reputation = (profile.reputation - 2).coerceAtLeast(GameConstants.Coach.ATTR_MIN)
        }
        profile.xp += xpGain
        return xpGain
    }

    /** Pick & ban manual conduzido. */
    fun recordManualPickBan(profile: CoachProfile?, allOnMains: Boolean): Int {
        if (profile == null) return 0
        profile.manualPickBansDone += 1
        var xpGain = GameConstants.CoachXp.MANUAL_PICK_BAN
        if (allOnMains) xpGain += GameConstants.CoachXp.PICK_PERFECT_DRAFT
        profile.xp += xpGain
        return xpGain
    }

    fun recordHire(profile: CoachProfile?, price: Long): Int {
        if (profile == null) return 0
        profile.playersHired += 1
        profile.totalSpent += price
        profile.xp += GameConstants.CoachXp.HIRE_PLAYER
        return GameConstants.CoachXp.HIRE_PLAYER
    }

    fun recordSell(profile: CoachProfile?, price: Long): Int {
        if (profile == null) return 0
        profile.playersSold += 1
        profile.totalEarned += price
        profile.xp += GameConstants.CoachXp.SELL_PLAYER
        return GameConstants.CoachXp.SELL_PLAYER
    }

    fun recordRenew(profile: CoachProfile?): Int {
        if (profile == null) return 0
        profile.contractsRenewed += 1
        profile.xp += GameConstants.CoachXp.RENEW_CONTRACT
        return GameConstants.CoachXp.RENEW_CONTRACT
    }
}
