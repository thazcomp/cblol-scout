package com.cblol.scout.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cblol.scout.data.PickBanPlan
import com.cblol.scout.game.GameRepository

/**
 * Activity transparente e sem UI própria.
 *
 * Responsabilidade única: receber o resultado do PickBanActivity via
 * onActivityResult, salvar o PickBanPlan no match e abrir o
 * MatchSimulationActivity com o plano pronto.
 *
 * SOLID:
 * - **SRP**: faz apenas o roteamento entre PickBanActivity → MatchSimulationActivity.
 * - **DIP**: depende somente de [PickBanActivity] (chaves Intent via companion).
 *
 * Fluxo:
 *   MatchResultActivity (série não acabou)
 *       → PickBanRouterActivity (transparente)
 *           → PickBanActivity (pick & ban do próximo mapa)
 *           ← onActivityResult (plano salvo)
 *       → MatchSimulationActivity (simula com os picks escolhidos)
 *           → MatchResultActivity (resultado do próximo mapa)
 */
class PickBanRouterActivity : AppCompatActivity() {

    private var matchId       = ""
    private var mapNumber     = 1
    private var playerTeamId  = ""
    private var opponentId    = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sem layout — Activity completamente transparente
        readIntentExtras()
        if (matchId.isEmpty() || playerTeamId.isEmpty()) { finish(); return }

        launchPickBan()
    }

    private fun readIntentExtras() {
        matchId      = intent.getStringExtra(EXTRA_MATCH_ID)       ?: ""
        mapNumber    = intent.getIntExtra(EXTRA_MAP_NUMBER, 1)
        playerTeamId = intent.getStringExtra(EXTRA_PLAYER_TEAM_ID) ?: ""
        opponentId   = intent.getStringExtra(EXTRA_OPPONENT_ID)    ?: ""
    }

    private fun launchPickBan() {
        @Suppress("DEPRECATION")
        startActivityForResult(
            Intent(this, PickBanActivity::class.java).apply {
                putExtra(PickBanActivity.EXTRA_PLAYER_TEAM_ID,   playerTeamId)
                putExtra(PickBanActivity.EXTRA_OPPONENT_TEAM_ID, opponentId)
                putExtra(PickBanActivity.EXTRA_MATCH_ID,         matchId)
                putExtra(PickBanActivity.EXTRA_MAP_NUMBER,       mapNumber)
            },
            PickBanActivity.REQUEST_PICK_BAN
        )
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != PickBanActivity.REQUEST_PICK_BAN || resultCode != RESULT_OK || data == null) {
            finish()
            return
        }

        savePickBanPlan(data)
        startSimulation()
        finish()
    }

    private fun savePickBanPlan(data: Intent) {
        val bluePicks = data.getStringArrayListExtra(PickBanActivity.RESULT_BLUE_PICKS)?.toList() ?: emptyList()
        val redPicks  = data.getStringArrayListExtra(PickBanActivity.RESULT_RED_PICKS)?.toList()  ?: emptyList()
        val blueBans  = data.getStringArrayListExtra(PickBanActivity.RESULT_BLUE_BANS)?.toList()  ?: emptyList()
        val redBans   = data.getStringArrayListExtra(PickBanActivity.RESULT_RED_BANS)?.toList()   ?: emptyList()
        val mapNum    = data.getIntExtra(PickBanActivity.EXTRA_MAP_NUMBER, mapNumber)

        val gs = GameRepository.current()
        gs.matches.find { it.id == matchId }?.pickBanPlan =
            PickBanPlan(mapNum, bluePicks, redPicks, blueBans, redBans)
        GameRepository.save(applicationContext)
    }

    private fun startSimulation() {
        // O MatchSimulationActivity vai exibir o dialog de sinergia após
        // reanimar a fase de pick & ban e antes dos eventos ao vivo.
        startActivity(
            Intent(this, MatchSimulationActivity::class.java)
                .putExtra(MatchSimulationActivity.EXTRA_MATCH_ID, matchId)
        )
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // Se o usuário cancelar o pick & ban, volta ao hub
        startActivity(
            Intent(this, ManagerHubActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    companion object {
        const val EXTRA_MATCH_ID       = "router_match_id"
        const val EXTRA_MAP_NUMBER     = "router_map_number"
        const val EXTRA_PLAYER_TEAM_ID = "router_player_team_id"
        const val EXTRA_OPPONENT_ID    = "router_opponent_id"
    }
}
