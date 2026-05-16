package com.cblol.scout.domain

/**
 * Constantes de domínio — valores numéricos que governam regras de negócio
 * do jogo (economia, ratings, formatos de série).
 *
 * Separadas em objetos aninhados por bounded context para evitar acoplamento
 * acidental entre módulos:
 *
 *   - Economy   : prêmios e modificadores financeiros (regra do MoneyManager)
 *   - Series    : formato de BO3 (regra do MatchSimulator)
 *   - Synergy   : pesos do CompositionRepository
 *   - Draft     : ordem de pick & ban, número de slots
 *   - Player    : limites de overall, idade, ratings
 *   - Schedule  : split, rodadas, dias por rodada
 *
 * **Por que não está em `res/values/integers.xml`?**
 *   - Valores de res/values pertencem à camada de apresentação (Android).
 *   - Estes governam regras de negócio puras (Clean Architecture: camada de domínio
 *     não depende de framework). Mantê-los em Kotlin permite que módulos não-Android
 *     (`:domain` puro) continuem testáveis sem o SDK.
 */
object GameConstants {

    /** Prêmios financeiros pagos ao jogador. */
    object Economy {
        const val PRIZE_PER_SERIES_WIN = 100_000L
        const val PRIZE_PER_MAP_WIN    = 50_000L

        /** Orçamento inicial por tier do time. */
        const val STARTING_BUDGET_TIER_S = 5_000_000L
        const val STARTING_BUDGET_TIER_A = 3_000_000L
        const val STARTING_BUDGET_TIER_B = 1_500_000L

        /** Patrocínio semanal por tier. */
        const val WEEKLY_SPONSOR_TIER_S = 600_000L
        const val WEEKLY_SPONSOR_TIER_A = 350_000L
        const val WEEKLY_SPONSOR_TIER_B = 200_000L

        /** Multiplicadores de preço de mercado por bracket de overall. */
        const val MARKET_MULTIPLIER_MYTHIC      = 2.4   // overall >= 85
        const val MARKET_MULTIPLIER_LEGENDARY   = 1.8   // 75-84
        const val MARKET_MULTIPLIER_EPIC        = 1.2   // 65-74
        const val MARKET_MULTIPLIER_RARE        = 0.85  // 55-64
        const val MARKET_MULTIPLIER_COMMON      = 0.6   // < 55

        const val OVERALL_BRACKET_MYTHIC      = 85
        const val OVERALL_BRACKET_LEGENDARY   = 75
        const val OVERALL_BRACKET_EPIC        = 65
        const val OVERALL_BRACKET_RARE        = 55
    }

    /** Formato de série BO3. */
    object Series {
        const val MAPS_TO_WIN     = 2
        const val MAX_MAPS        = 3
        const val HOME_SIDE_BONUS = 4   // pontos de força extra para o lado azul de mando
    }

    /** Pesos do motor de análise de composições. */
    object Synergy {
        /** Multiplicadores de bônus quando múltiplas comps são detectadas. */
        const val PRIMARY_COMP_FACTOR   = 1.0
        const val SECONDARY_COMP_FACTOR = 0.6
        const val TERTIARY_COMP_FACTOR  = 0.3
        const val ADDITIONAL_COMP_FACTOR = 0.15

        /** Escala visual: bonusStrength × este fator = % da barra (0–100). */
        const val SYNERGY_BAR_SCALE = 5.5

        /** Bônus de força extras avaliados por tags (analyzeWithTags). */
        const val BONUS_MIXED_DAMAGE      =  2
        const val BONUS_HIGH_CC           =  3
        const val BONUS_KNOCKUP_SYNERGY   =  2
        const val BONUS_HEAL_SHIELD       =  2
        const val BONUS_ANTI_TANK_VS_TANK =  2

        const val PENALTY_NO_FRONTLINE        = -3
        const val PENALTY_NO_ANTI_TANK        = -2
        const val PENALTY_NO_EARLY_VS_SCALING = -1

        const val THRESHOLD_HIGH_CC      = 3
        const val THRESHOLD_KNOCKUPS     = 2
        const val THRESHOLD_HEAL_SHIELD  = 3
        const val THRESHOLD_TANKS_ON_OP  = 3
        const val THRESHOLD_BRUISERS_OP  = 2
        const val THRESHOLD_HYPER_ON_OP  = 2
        const val THRESHOLD_SQUISHIES    = 3
    }

    /** Configuração do draft (pick & ban). */
    object Draft {
        const val BANS_PER_SIDE         = 5
        const val PICKS_PER_SIDE        = 5
        const val TOTAL_TURNS           = (BANS_PER_SIDE + PICKS_PER_SIDE) * 2  // 20
        const val AI_ACTION_DELAY_MS    = 1200L
        const val GRID_COLUMNS          = 7
        const val IMAGE_TRANSITION_MS   = 150
        const val SELECTED_SCALE        = 1.08f
        const val DISABLED_ALPHA        = 0.22f
        const val BAN_SUGGESTIONS_COUNT = 10
        const val BAN_SUGGESTIONS_TOP   = 5  // exibidos no dialog inicial
    }

    /** Faixas de avaliação de jogadores. */
    object Player {
        const val DEFAULT_OVERALL   = 75   // fallback quando não há roster
        const val OVERALL_DIFF_HUGE = 5    // ▶▶ ou ◀◀
        const val OVERALL_DIFF_MILD = 2    // ▶ ou ◀
    }

    /** Configuração do calendário. */
    object Schedule {
        const val TEAMS_COUNT     = 8
        const val ROUNDS_TOTAL    = 14    // round-robin duplo
        const val MATCHES_TOTAL   = 56
        const val PLAYERS_PER_TEAM = 5
    }

    /** Animações da tela de resultado. */
    object Result {
        const val ANIM_BG_DURATION_MS         = 500L
        const val ANIM_BG_TARGET_ALPHA        = 0.6f
        const val ANIM_ICON_DELAY_MS          = 200L
        const val ANIM_ICON_DURATION_MS       = 600L
        const val ANIM_LABEL_DELAY_MS         = 600L
        const val ANIM_LABEL_DURATION_MS      = 500L
        const val ANIM_SCOREBOARD_DELAY_MS    = 1000L
        const val ANIM_SCOREBOARD_DURATION_MS = 500L
        const val ANIM_COUNTERS_DELAY_MS      = 1400L
        const val ANIM_COUNTERS_DURATION_MS   = 800L
        const val ANIM_CARD_STATS_DELAY_MS    = 1500L
        const val ANIM_CARD_PRIZE_DELAY_MS    = 1800L
        const val ANIM_BUTTON_DELAY_MS        = 2100L
        const val ANIM_PULSE_DELAY_MS         = 1200L
        const val ANIM_PULSE_DURATION_MS      = 400L
        const val ANIM_PULSE_REPEAT_COUNT     = 3
        const val ANIM_VICTORY_PULSE_SCALE    = 1.1f
        const val ANIM_SCOREBOARD_START_SCALE = 0.7f
        const val ANIM_OVERSHOOT_TENSION      = 1.5f
    }

    /** Delays de eventos durante a simulação ao vivo. */
    object Simulation {
        const val DELAY_PHASE_MS    = 1200L
        const val DELAY_BAN_MS      = 450L
        const val DELAY_PICK_MS     = 600L
        const val DELAY_TICK_MS     = 700L
        const val DELAY_OBJECTIVE_MS = 350L
        const val DELAY_GAME_END_MS = 2200L
        const val FEED_MAX_ITEMS    = 60
    }
}
