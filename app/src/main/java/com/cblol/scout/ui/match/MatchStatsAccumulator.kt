package com.cblol.scout.ui.match

import com.cblol.scout.R
import com.cblol.scout.data.MatchEvent
import com.cblol.scout.data.Side
import com.cblol.scout.databinding.ActivityMatchSimulationBinding

/**
 * Acumula os contadores de stats DENTRO de um mapa (kills, torres, dragões,
 * baron, herald de cada lado) e atualiza o scoreboard da UI.
 *
 * Extraído da [com.cblol.scout.ui.MatchSimulationActivity] para isolar o
 * estado mutável da simulação (eram 10 campos `var` esparramados pela
 * Activity) num único objeto coeso. Reset por mapa fica num único `reset()`.
 *
 * **SOLID:**
 *  - **SRP**: só guarda contadores e mantém o scoreboard atualizado.
 *  - **OCP**: novos tipos de stat (ex: void grubs) entram aqui com novo
 *    campo + entrada no [accumulate].
 *
 * Recebe o [ActivityMatchSimulationBinding] no construtor para acessar as
 * TextViews do scoreboard — assim a Activity não precisa intermediar cada
 * atualização.
 */
internal class MatchStatsAccumulator(
    private val binding: ActivityMatchSimulationBinding,
    private val ctx: android.content.Context
) {
    // Contadores acumulados (resetados por mapa)
    var homeKills = 0;   var awayKills = 0
    var homeTowers = 0;  var awayTowers = 0
    var homeDragons = 0; var awayDragons = 0
    var homeBarons = 0;  var awayBarons = 0
    var homeHeralds = 0; var awayHeralds = 0

    /**
     * Atualiza os contadores conforme o tipo do evento. Devolve true se o
     * scoreboard mudou (evento contável); útil para a UI chamar
     * [updateScoreboard] só quando necessário.
     */
    fun accumulate(e: MatchEvent): Boolean = when (e) {
        is MatchEvent.GameStart -> { reset(); true }
        is MatchEvent.Kill      -> { if (e.killerSide == Side.HOME) homeKills++ else awayKills++; true }
        is MatchEvent.TowerDown -> { if (e.side == Side.HOME) homeTowers++ else awayTowers++; true }
        is MatchEvent.Dragon    -> { if (e.side == Side.HOME) homeDragons++ else awayDragons++; true }
        is MatchEvent.Baron     -> { if (e.side == Side.HOME) homeBarons++ else awayBarons++; true }
        is MatchEvent.Herald    -> { if (e.side == Side.HOME) homeHeralds++ else awayHeralds++; true }
        else                    -> false
    }

    /**
     * Versão silenciosa do [accumulate], usada pelo "pular para o resultado"
     * que processa eventos sem renderizar. Não atualiza UI.
     */
    fun accumulateSilent(e: MatchEvent) { accumulate(e) }

    /** Atualiza todos os TextViews do scoreboard com os valores atuais. */
    fun updateScoreboard() {
        binding.tvHomeKills.text = homeKills.toString()
        binding.tvAwayKills.text = awayKills.toString()
        binding.tvObjTowers.text  = ctx.getString(R.string.obj_towers_format,  homeTowers,  awayTowers)
        binding.tvObjDragons.text = ctx.getString(R.string.obj_dragons_format, homeDragons, awayDragons)
        binding.tvObjBarons.text  = ctx.getString(R.string.obj_barons_format,  homeBarons,  awayBarons)
        binding.tvObjHeralds.text = ctx.getString(R.string.obj_heralds_format, homeHeralds, awayHeralds)
    }

    /** Reseta todos os contadores para 0 e atualiza o scoreboard. */
    fun reset() {
        homeKills = 0; awayKills = 0
        homeTowers = 0; awayTowers = 0
        homeDragons = 0; awayDragons = 0
        homeBarons = 0; awayBarons = 0
        homeHeralds = 0; awayHeralds = 0
        updateScoreboard()
    }
}
