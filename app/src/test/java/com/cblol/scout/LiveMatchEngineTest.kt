package com.cblol.scout

import com.cblol.scout.game.LiveMatchEngine
import com.cblol.scout.game.Champions
import com.cblol.scout.data.PickBanMap
import com.cblol.scout.data.PickBanPlan
import com.cblol.scout.data.Player
import com.cblol.scout.data.MatchEvent
import org.junit.Assert.*
import org.junit.Test

class LiveMatchEngineTest {

    private fun makePlayer(id: String, name: String, role: String): Player = Player(
        id = id,
        nome_jogo = name,
        nome_real = null,
        time_id = "T",
        time_nome = "Team",
        role = role,
        titular = true,
        idade = 20,
        nacionalidade = "BR",
        contrato = com.cblol.scout.data.Contrato(termino = null, valor_estimado_brl = 0, salario_mensal_estimado_brl = 0, fonte_salario = ""),
        stats_brutas = com.cblol.scout.data.StatsBrutas(0,0.0,0.0,0.0,null,null,0.0,null),
        atributos_derivados = com.cblol.scout.data.AtributosDeriv(50,50,50,50,50)
    )

    @Test
    fun generatePickBan_respectsOverrides() {
        val home = listOf(
            makePlayer("p1","Top1","TOP"),
            makePlayer("p2","Jng1","JNG"),
            makePlayer("p3","Mid1","MID"),
            makePlayer("p4","Adc1","ADC"),
            makePlayer("p5","Sup1","SUP")
        )
        val away = listOf(
            makePlayer("a1","Top2","TOP"),
            makePlayer("a2","Jng2","JNG"),
            makePlayer("a3","Mid2","MID"),
            makePlayer("a4","Adc2","ADC"),
            makePlayer("a5","Sup2","SUP")
        )

        // choose a banned champ and a pick
        val banned = Champions.TOP.first()
        val pickChamp = Champions.MID.first()

        val map = PickBanMap(gameNumber = 1, homeBans = listOf(banned), homePicks = listOf(com.cblol.scout.data.PickRecord(null, null, pickChamp)))
        val events = LiveMatchEngine.generatePickBan(1, home, away, map)

        // Ensure the ban event is present
        val bans = events.filterIsInstance<MatchEvent.Ban>().map { it.champion }
        assertTrue(bans.contains(banned))

        // Ensure the pick event is present
        val picks = events.filterIsInstance<MatchEvent.Pick>().map { it.champion }
        assertTrue(picks.contains(pickChamp))

        // Ensure no pick uses a banned champion
        val picksSet = picks.toSet()
        assertFalse(picksSet.contains(banned))
    }
}
