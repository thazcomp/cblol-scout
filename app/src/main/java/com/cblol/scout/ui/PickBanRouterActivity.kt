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
 * Fluxo:
 *   MatchResultActivity (série não acabou)
 *       → PickBanRouterActivity (transparente)
 *           → PickBanActivity (pick & ban do próximo mapa)
 *           ← onActivityResult (plano salvo)
 *       → MatchSimulationActivity (simula com os picks escolhidos)
 *           → MatchResultActivity (resultado do próximo mapa)
 *
 * Extras esperados:
 *   EXTRA_MATCH_ID       String
 *   EXTRA_MAP_NUMBER     Int
 *   EXTRA_PLAYER_TEAM_ID String
 *   EXTRA_OPPONENT_ID    String
 */
class PickBanRouterActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MATCH_ID       = "router_match_id"
        const val EXTRA_MAP_NUMBER     = "router_map_number"
        const val EXTRA_PLAYER_TEAM_ID = "router_player_team_id"
        const val EXTRA_OPPONENT_ID    = "router_opponent_id"
    }

    private var matchId       = ""
    private var mapNumber     = 1
    private var playerTeamId  = ""
    private var opponentId    = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sem layout — Activity completamente transparente
        matchId      = intent.getStringExtra(EXTRA_MATCH_ID)       ?: ""
        mapNumber    = intent.getIntExtra(EXTRA_MAP_NUMBER, 1)
        playerTeamId = intent.getStringExtra(EXTRA_PLAYER_TEAM_ID) ?: ""
        opponentId   = intent.getStringExtra(EXTRA_OPPONENT_ID)    ?: ""

        if (matchId.isEmpty() || playerTeamId.isEmpty()) { finish(); return }

        @Suppress("DEPRECATION")
        startActivityForResult(
            Intent(this, PickBanActivity::class.java).apply {
                putExtra("player_team_id",   playerTeamId)
                putExtra("opponent_team_id", opponentId)
                putExtra("match_id",         matchId)
                putExtra("map_number",       mapNumber)
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

        val bluePicks = data.getStringArrayListExtra("blue_picks")?.toList() ?: emptyList()
        val redPicks  = data.getStringArrayListExtra("red_picks")?.toList()  ?: emptyList()
        val blueBans  = data.getStringArrayListExtra("blue_bans")?.toList()  ?: emptyList()
        val redBans   = data.getStringArrayListExtra("red_bans")?.toList()   ?: emptyList()
        val mapNum    = data.getIntExtra("map_number", mapNumber)

        // Salva o plano no match
        val gs = GameRepository.current()
        gs.matches.find { it.id == matchId }?.pickBanPlan =
            PickBanPlan(mapNum, bluePicks, redPicks, blueBans, redBans)
        GameRepository.save(applicationContext)

        // Abre a simulação com os campeões escolhidos e fecha esta Activity
        startActivity(
            Intent(this, MatchSimulationActivity::class.java)
                .putExtra(MatchSimulationActivity.EXTRA_MATCH_ID, matchId)
        )
        finish()
    }

    override fun onBackPressed() {
        // Se o usuário cancelar o pick & ban, volta ao hub
        startActivity(
            Intent(this, ManagerHubActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }
}
