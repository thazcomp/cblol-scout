package com.cblol.scout.data

/**
 * Estado completo de uma carreira (modo jogo). Persistido em SharedPreferences via Gson.
 */
data class GameState(
    val managerName: String,
    val managerTeamId: String,
    val splitStartDate: String,
    val splitEndDate: String,
    var currentDate: String,
    var budget: Long,
    val sponsorshipPerWeek: Long,
    val playerOverrides: MutableMap<String, PlayerOverride> = mutableMapOf(),
    val matches: MutableList<Match> = mutableListOf(),
    val gameLog: MutableList<LogEntry> = mutableListOf(),

    /**
     * Placar parcial de séries BO3 em andamento.
     * Chave = Match.id — criado quando o jogador inicia o pick & ban do mapa 1,
     * removido ao finalizar a série (2 vitórias de qualquer lado).
     * Não precisa ser persistido entre sessões (série sempre recomeça do mapa 1
     * se o app for fechado), mas fica aqui para consistência com o Gson.
     */
    val seriesState: MutableMap<String, SeriesState> = mutableMapOf()
)

/**
 * Placar parcial de uma série BO3 entre dois times.
 */
data class SeriesState(
    val playerWins: Int = 0,
    val opponentWins: Int = 0
) {
    fun recordMap(playerWon: Boolean) = copy(
        playerWins   = if (playerWon)  playerWins + 1  else playerWins,
        opponentWins = if (!playerWon) opponentWins + 1 else opponentWins
    )
    val isFinished: Boolean get() = playerWins == 2 || opponentWins == 2
}

/**
 * Mudanças aplicadas a um jogador durante a carreira.
 */
data class PlayerOverride(
    val playerId: String,
    val newTeamId: String? = null,
    val newSalary: Long? = null,
    val newContractEnd: String? = null,
    val titular: Boolean? = null,
    val transferredOn: String? = null
)

/**
 * Partida do split (BO3). homeScore/awayScore = mapas vencidos.
 */
data class Match(
    val id: String,
    val date: String,
    val round: Int,
    val homeTeamId: String,
    val awayTeamId: String,
    var played: Boolean = false,
    var homeScore: Int = 0,
    var awayScore: Int = 0,
    var pickBanPlan: PickBanPlan? = null
) {
    fun winnerTeamId(): String? = when {
        !played -> null
        homeScore > awayScore -> homeTeamId
        else -> awayTeamId
    }
}

/**
 * Resultado do pick & ban de um mapa, armazenado junto à partida.
 */
data class PickBanPlan(
    val mapNumber: Int,
    val bluePicks: List<String>,
    val redPicks: List<String>,
    val blueBans: List<String>,
    val redBans: List<String>
)

/**
 * Linha da classificação.
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
    val games: Int  get() = wins + losses
}

/**
 * Eventos do log do jogo.
 */
data class LogEntry(
    val date: String,
    val type: String,
    val message: String
)