package com.cblol.scout.data

/**
 * Eventos emitidos durante a simulação ao vivo de uma partida.
 * Tem três fases: pick&ban, game (1..3 mapas em BO3) e final.
 *
 * Cada evento carrega contexto suficiente pro UI atualizar a tela sem precisar
 * de outras consultas (nome do jogador + campeão, lado da equipe, tempo de jogo).
 */
sealed class MatchEvent {

    /** Início do mapa N (1, 2 ou 3). Usado pra resetar o painel de game. */
    data class GameStart(val gameNumber: Int) : MatchEvent()

    /** Anúncio "fase de seleção" (1x antes dos picks/bans de cada mapa). */
    data class PhaseAnnouncement(val text: String) : MatchEvent()

    /** Banimento de um campeão por uma equipe. */
    data class Ban(val gameNumber: Int, val side: Side, val champion: String) : MatchEvent()

    /** Pick (escolha) de um campeão por um jogador. */
    data class Pick(
        val gameNumber: Int,
        val side: Side,
        val playerName: String,
        val role: String,
        val champion: String
    ) : MatchEvent()

    /** Tick do relógio do jogo. UI atualiza display de tempo. */
    data class GameTick(val minute: Int, val second: Int) : MatchEvent()

    /** Kill: jogador X (com campeão) abateu jogador Y (com campeão). */
    data class Kill(
        val time: String,        // "12:34"
        val killerSide: Side,
        val killerName: String,
        val killerChamp: String,
        val victimName: String,
        val victimChamp: String
    ) : MatchEvent()

    /** Torre derrubada. location ∈ {top, mid, bot, base}. */
    data class TowerDown(val time: String, val side: Side, val location: String) : MatchEvent()

    /** Inibidor destruído. */
    data class Inhibitor(val time: String, val side: Side, val location: String) : MatchEvent()

    /** Drake conquistado. type ∈ {Infernal, Mountain, Ocean, Cloud, Hextech, Chemtech, Elder}. */
    data class Dragon(val time: String, val side: Side, val type: String) : MatchEvent()

    /** Baron Nashor. */
    data class Baron(val time: String, val side: Side) : MatchEvent()

    /** Arauto do Vale. */
    data class Herald(val time: String, val side: Side) : MatchEvent()

    /** Buff de selva (Red ou Blue) pego — geralmente discreto, mas dá flavor. */
    data class Buff(val time: String, val side: Side, val playerName: String, val type: String) : MatchEvent()

    /** Fim de mapa. winner indica qual lado venceu. */
    data class GameEnd(
        val gameNumber: Int,
        val winnerSide: Side,
        val durationMinutes: Int,
        val finalKills: Pair<Int, Int>   // home, away
    ) : MatchEvent()

    /** Fim da série BO3. */
    data class SeriesEnd(val winnerSide: Side, val mapScore: Pair<Int, Int>) : MatchEvent()
}

enum class Side { HOME, AWAY }
