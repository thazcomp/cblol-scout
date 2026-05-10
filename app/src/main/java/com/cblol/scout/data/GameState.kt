package com.cblol.scout.data

/**
 * Estado completo de uma carreira (modo jogo). Persistido em SharedPreferences via Gson.
 *
 * - `playerOverrides` registra mudanças em jogadores (transferências, salário renegociado,
 *   status de titular/reserva, etc.). O snapshot original (assets/cblol_jogadores.json) é
 *   imutável; sempre que precisar dos dados "atuais" de um jogador, consulte os overrides.
 * - `matches` é o calendário inteiro do split, gerado uma única vez ao iniciar a carreira.
 * - `currentDate` é a data simulada do jogo (ISO yyyy-MM-dd). "Avançar dia" incrementa
 *   essa data e simula partidas/aplica eventos econômicos.
 */
data class GameState(
    val managerName: String,
    val managerTeamId: String,
    val splitStartDate: String,        // "2026-03-28"
    val splitEndDate: String,          // "2026-06-06"
    var currentDate: String,           // avança com o tempo
    var budget: Long,                  // R$ disponível pra contratações
    val sponsorshipPerWeek: Long,      // receita semanal fixa
    val playerOverrides: MutableMap<String, PlayerOverride> = mutableMapOf(),
    val matches: MutableList<Match> = mutableListOf(),
    val gameLog: MutableList<LogEntry> = mutableListOf()
)

/**
 * Mudanças aplicadas a um jogador durante a carreira. Se um campo é null, usa o valor original.
 */
data class PlayerOverride(
    val playerId: String,
    val newTeamId: String? = null,           // se transferido
    val newSalary: Long? = null,             // se renegociado
    val newContractEnd: String? = null,      // ISO
    val titular: Boolean? = null,            // override de titular/reserva (apenas para meu time)
    val transferredOn: String? = null        // data (ISO) da última transferência
)

/**
 * Partida do split (BO3 — primeiro a 2 mapas). homeScore/awayScore = mapas vencidos.
 */
data class Match(
    val id: String,
    val date: String,
    val round: Int,
    val homeTeamId: String,
    val awayTeamId: String,
    var played: Boolean = false,
    var homeScore: Int = 0,
    var awayScore: Int = 0
) {
    fun winnerTeamId(): String? = when {
        !played -> null
        homeScore > awayScore -> homeTeamId
        else -> awayTeamId
    }
}

/**
 * Linha computada da classificação. Ordenada por: vitórias desc, saldo de mapas desc, mapas vencidos desc.
 */
data class Standing(
    val teamId: String,
    val teamName: String,
    val wins: Int,
    val losses: Int,
    val mapsWon: Int,
    val mapsLost: Int
) {
    val mapDiff: Int get() = mapsWon - mapsLost
    val games: Int get() = wins + losses
}

/**
 * Eventos do log do jogo (mostrado no dashboard).
 */
data class LogEntry(
    val date: String,        // ISO
    val type: String,        // MATCH, TRANSFER, ECONOMY, CONTRACT
    val message: String
)
