package com.cblol.scout.game.live

import com.cblol.scout.data.MatchEvent
import com.cblol.scout.data.Player
import com.cblol.scout.data.Side
import com.cblol.scout.game.Champions
import kotlin.random.Random

/**
 * Distribui os eventos do mapa (kills, torres, dragões, baron, herald, buffs,
 * inibidor) ao longo dos minutos do jogo. Recebe os totais já decididos pelo
 * [GameOutcomeCalculator] e converte em uma timeline.
 *
 * Extraído do [com.cblol.scout.game.LiveMatchEngine] para isolar a "narrativa
 * temporal" do jogo — toda a distribuição por minuto, regras de tempo (drake
 * a cada ~5 min, baron depois dos 22, inibidor perto do fim) vive aqui.
 *
 * **SOLID:**
 *  - **SRP**: cria timed events; não decide quem vence nem quantos kills.
 *  - **OCP**: novos tipos de objetivo (ex: Atakhan futuro) entram como helper
 *    privado + bloco de geração, sem mexer no resto.
 */
internal object TimedEventGenerator {

    /**
     * Constrói a timeline completa do jogo intercalando `GameTick`s a cada
     * minuto com os eventos do minuto.
     *
     * @param duration duração total do jogo em minutos
     * @param outcome resultado calculado (kills, vencedor, probabilidade)
     * @param homeRoster jogadores do home (para sortear killers/vítimas)
     * @param awayRoster jogadores do away
     * @param playerChampions mapa `playerName → championId` vindo do
     *   [PickBanGenerator] — usado para que kills mostrem os campeões reais
     *   do draft
     */
    fun build(
        duration: Int,
        outcome: GameOutcomeCalculator.GameOutcome,
        homeRoster: List<Player>,
        awayRoster: List<Player>,
        playerChampions: Map<String, String>
    ): List<MatchEvent> {
        val timed = mutableListOf<TimedEvent>()

        // Kills
        repeat(outcome.homeKills) {
            timed += kill(
                t = (3..(duration - 1)).random(),
                killerSide = Side.HOME,
                roster = homeRoster, victimRoster = awayRoster,
                playerChampions = playerChampions
            )
        }
        repeat(outcome.awayKills) {
            timed += kill(
                t = (3..(duration - 1)).random(),
                killerSide = Side.AWAY,
                roster = awayRoster, victimRoster = homeRoster,
                playerChampions = playerChampions
            )
        }

        // Heralds (7–10 e às vezes 13–15)
        timed += herald(time = (7..10).random(), side = GameOutcomeCalculator.sideByOdds(outcome.homeWinProb))
        if (Random.nextDouble() < HERALD_SECOND_PROB && duration > HERALD_SECOND_MIN_DURATION) {
            timed += herald(time = (13..15).random(), side = GameOutcomeCalculator.sideByOdds(outcome.homeWinProb))
        }

        // Drakes a cada ~5 min começando em 5–7
        addDragons(timed, duration, outcome.homeWinProb)

        // Torres (vencedor mínimo 5 — caminho ao Nexus; perdedor 0–4)
        addTowers(timed, duration, outcome.homeWon)

        // Baron depois dos 22 min
        if (duration > BARON_FIRST_MIN_DURATION) {
            timed += baron(time = (22..(duration - 2)).random(),
                side = GameOutcomeCalculator.sideByOdds(outcome.homeWinProb))
            if (Random.nextDouble() < BARON_SECOND_PROB && duration > BARON_SECOND_MIN_DURATION) {
                timed += baron(time = (28..(duration - 1)).random(),
                    side = GameOutcomeCalculator.sideByOdds(outcome.homeWinProb))
            }
        }

        // Buffs (3 ao longo do jogo, bem espaçados)
        addBuffs(timed, duration, homeRoster, awayRoster)

        // Inibidor perto do fim, do lado vencedor
        if (duration > INHIBITOR_MIN_DURATION) {
            timed += inhibitor(
                time = duration - (1..3).random(),
                side = if (outcome.homeWon) Side.HOME else Side.AWAY,
                location = listOf("top", "mid", "bot").random()
            )
        }

        // Ordena por tempo
        timed.sortBy { it.minute * 60 + it.second }

        // Intercala GameTicks com os eventos do minuto
        val events = mutableListOf<MatchEvent>()
        for (m in 0..duration) {
            events.add(MatchEvent.GameTick(minute = m, second = 0))
            events.addAll(timed.filter { it.minute == m }.map { it.event })
        }
        return events
    }

    // ── Objetivos ────────────────────────────────────────────────────────

    private fun addDragons(timed: MutableList<TimedEvent>, duration: Int, homeWinProb: Double) {
        var dragonTime = (5..7).random()
        val dragonTypes = listOf("Infernal", "Cloud", "Mountain", "Ocean", "Hextech", "Chemtech").shuffled()
        var dragonIndex = 0
        while (dragonTime < duration - 1 && dragonIndex < MAX_DRAGONS) {
            timed += dragon(
                time = dragonTime,
                side = GameOutcomeCalculator.sideByOdds(homeWinProb),
                type = dragonTypes[dragonIndex % dragonTypes.size]
            )
            dragonIndex++
            dragonTime += (4..6).random()
        }
    }

    /**
     * Distribui torres respeitando as regras do LoL:
     *  - Vencedor: mínimo 5 (3 da lane + inibidora + Nexus), até 9.
     *  - Perdedor: 0–3 em jogos curtos, 1–4 em jogos longos.
     */
    private fun addTowers(timed: MutableList<TimedEvent>, duration: Int, homeWon: Boolean) {
        val winnerTowers = (TOWER_WIN_MIN..TOWER_WIN_MAX).random()
        val loserTowers  = if (duration > 30) (1..4).random() else (0..3).random()
        val homeTowers   = if (homeWon) winnerTowers else loserTowers
        val awayTowers   = if (homeWon) loserTowers  else winnerTowers
        val lanes        = listOf("top", "mid", "bot")
        repeat(homeTowers) {
            timed += tower(time = (8..(duration - 2)).random(), side = Side.HOME, location = lanes.random())
        }
        repeat(awayTowers) {
            timed += tower(time = (8..(duration - 2)).random(), side = Side.AWAY, location = lanes.random())
        }
    }

    private fun addBuffs(
        timed: MutableList<TimedEvent>,
        duration: Int,
        homeRoster: List<Player>,
        awayRoster: List<Player>
    ) {
        repeat(BUFF_EVENTS_COUNT) {
            val team = if (Random.nextBoolean()) Side.HOME else Side.AWAY
            val rosterSide = if (team == Side.HOME) homeRoster else awayRoster
            val jng = rosterSide.find { it.role == "JNG" } ?: rosterSide.first()
            timed += buff(
                time = (4..(duration - 4)).random(),
                side = team,
                player = jng.nome_jogo,
                type = listOf("Red", "Blue").random()
            )
        }
    }

    // ── Construtores de TimedEvent ──────────────────────────────────────

    private fun kill(
        t: Int, killerSide: Side,
        roster: List<Player>, victimRoster: List<Player>,
        playerChampions: Map<String, String>
    ): TimedEvent {
        val killer = roster.random()
        val victim = victimRoster.random()
        // Usa o campeão REAL pickado por cada jogador no draft; fallback para
        // sorteio por role apenas se o mapa não tem entrada (não deveria
        // acontecer, mas blinda contra inconsistências).
        val killerChamp = playerChampions[killer.nome_jogo] ?: Champions.forRole(killer.role).random()
        val victimChamp = playerChampions[victim.nome_jogo] ?: Champions.forRole(victim.role).random()
        return TimedEvent(t, 0, MatchEvent.Kill(
            time = fmt(t),
            killerSide = killerSide,
            killerName = killer.nome_jogo,
            killerChamp = killerChamp,
            victimName = victim.nome_jogo,
            victimChamp = victimChamp
        ))
    }

    private fun tower(time: Int, side: Side, location: String) =
        TimedEvent(time, 0, MatchEvent.TowerDown(fmt(time), side, location))

    private fun inhibitor(time: Int, side: Side, location: String) =
        TimedEvent(time, 0, MatchEvent.Inhibitor(fmt(time), side, location))

    private fun dragon(time: Int, side: Side, type: String) =
        TimedEvent(time, 0, MatchEvent.Dragon(fmt(time), side, type))

    private fun baron(time: Int, side: Side) =
        TimedEvent(time, 0, MatchEvent.Baron(fmt(time), side))

    private fun herald(time: Int, side: Side) =
        TimedEvent(time, 0, MatchEvent.Herald(fmt(time), side))

    private fun buff(time: Int, side: Side, player: String, type: String) =
        TimedEvent(time, 0, MatchEvent.Buff(fmt(time), side, player, type))

    private fun fmt(t: Int): String = "%02d:%02d".format(t, Random.nextInt(0, 60))

    private data class TimedEvent(val minute: Int, val second: Int, val event: MatchEvent)

    // ── Constantes ───────────────────────────────────────────────────────

    /**
     * Faixa de torres do vencedor. Mínimo 5 porque o caminho ao Nexus exige
     * lane completa (3 torres + inibidora + Nexus). Máximo 9 para o caso
     * extremo onde o vencedor quase zera o mapa.
     */
    private const val TOWER_WIN_MIN = 5
    private const val TOWER_WIN_MAX = 9

    private const val MAX_DRAGONS = 5
    private const val BUFF_EVENTS_COUNT = 3

    private const val HERALD_SECOND_PROB = 0.55
    private const val HERALD_SECOND_MIN_DURATION = 15
    private const val BARON_FIRST_MIN_DURATION = 24
    private const val BARON_SECOND_PROB = 0.35
    private const val BARON_SECOND_MIN_DURATION = 30
    private const val INHIBITOR_MIN_DURATION = 25
}
