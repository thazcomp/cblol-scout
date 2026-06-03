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

    // Picks capturados do PickBanActivity — guardados aqui para passar para a
    // tela de atribuição de rotas e em seguida montar o PickBanPlan final.
    private var bluePicks: List<String> = emptyList()
    private var redPicks:  List<String> = emptyList()
    private var blueBans:  List<String> = emptyList()
    private var redBans:   List<String> = emptyList()
    /**
     * Lanes escolhidas pelo treinador na ordem dos picks dele. Quando vier
     * preenchida (5 itens, um por pick), usamos para montar os RoleAssignments
     * automaticamente e pular a [RoleAssignmentActivity] — o coach já disse
     * a lane de cada pick durante o draft.
     */
    private var pickedLanes: List<String> = emptyList()
    private var mapNumFromResult = 1

    // Flag que evita relançar o PickBanActivity se o Router for recriado pelo
    // sistema (ex: depois de uma rotação no PickBan, mudança de configuração,
    // baixa memória). Sem isso, o `onCreate` recria o Router em loop e abre o
    // PickBan do zero — que era exatamente o bug "voltou ao início do pick & ban".
    private var pickBanLaunched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readIntentExtras()
        if (matchId.isEmpty() || playerTeamId.isEmpty()) { finish(); return }

        // Restaura estado se a Activity foi recriada
        if (savedInstanceState != null) {
            pickBanLaunched  = savedInstanceState.getBoolean(STATE_PICKBAN_LAUNCHED, false)
            mapNumFromResult = savedInstanceState.getInt(STATE_MAP_NUM_FROM_RESULT, mapNumber)
            bluePicks = savedInstanceState.getStringArrayList(STATE_BLUE_PICKS)?.toList() ?: emptyList()
            redPicks  = savedInstanceState.getStringArrayList(STATE_RED_PICKS)?.toList()  ?: emptyList()
            blueBans  = savedInstanceState.getStringArrayList(STATE_BLUE_BANS)?.toList()  ?: emptyList()
            redBans   = savedInstanceState.getStringArrayList(STATE_RED_BANS)?.toList()   ?: emptyList()
            pickedLanes = savedInstanceState.getStringArrayList(STATE_PICKED_LANES)?.toList() ?: emptyList()
        }

        // Só lança o pick & ban na PRIMEIRA criação. Se o Router for recriado pelo
        // sistema (rotação, low memory) enquanto outra Activity está em primeiro
        // plano, o resultado virá via `onActivityResult` normalmente — não queremos
        // abrir um novo PickBan por cima.
        if (!pickBanLaunched) {
            pickBanLaunched = true
            launchPickBan()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_PICKBAN_LAUNCHED, pickBanLaunched)
        outState.putInt(STATE_MAP_NUM_FROM_RESULT, mapNumFromResult)
        outState.putStringArrayList(STATE_BLUE_PICKS, ArrayList(bluePicks))
        outState.putStringArrayList(STATE_RED_PICKS,  ArrayList(redPicks))
        outState.putStringArrayList(STATE_BLUE_BANS,  ArrayList(blueBans))
        outState.putStringArrayList(STATE_RED_BANS,   ArrayList(redBans))
        outState.putStringArrayList(STATE_PICKED_LANES, ArrayList(pickedLanes))
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

        when (requestCode) {
            PickBanActivity.REQUEST_PICK_BAN -> handlePickBanResult(resultCode, data)
            RoleAssignmentActivity.REQUEST_ROLE_ASSIGNMENT ->
                handleRoleAssignmentResult(resultCode, data)
            else -> finish()
        }
    }

    private fun handlePickBanResult(resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK || data == null) { finish(); return }

        bluePicks = data.getStringArrayListExtra(PickBanActivity.RESULT_BLUE_PICKS)?.toList() ?: emptyList()
        redPicks  = data.getStringArrayListExtra(PickBanActivity.RESULT_RED_PICKS)?.toList()  ?: emptyList()
        blueBans  = data.getStringArrayListExtra(PickBanActivity.RESULT_BLUE_BANS)?.toList()  ?: emptyList()
        redBans   = data.getStringArrayListExtra(PickBanActivity.RESULT_RED_BANS)?.toList()   ?: emptyList()
        pickedLanes = data.getStringArrayListExtra(PickBanActivity.RESULT_PICKED_LANES)?.toList() ?: emptyList()
        mapNumFromResult = data.getIntExtra(PickBanActivity.EXTRA_MAP_NUMBER, mapNumber)

        // Identifica quais picks são do JOGADOR (depende do lado) e os do oponente
        val playerIsBlue = (mapNumFromResult % 2 == 1)
        val myPicks       = if (playerIsBlue) bluePicks else redPicks
        val opponentPicks = if (playerIsBlue) redPicks  else bluePicks

        // Pula a tela de rotas se houver menos de 5 picks (defesa contra fluxo "IA escolhe")
        if (myPicks.size < 5) {
            savePickBanPlan(emptyList())
            startSimulation()
            finish()
            return
        }

        // Atalho: se o coach já escolheu a lane de CADA pick durante o draft,
        // não precisamos abrir a [RoleAssignmentActivity] — montamos os
        // RoleAssignments aqui mesmo e seguimos direto pra simulação.
        if (pickedLanes.size == myPicks.size && pickedLanes.all { it.isNotBlank() }) {
            val assignments = buildAssignmentsFromLanes(myPicks, pickedLanes)
            savePickBanPlan(assignments)
            startSimulation()
            finish()
            return
        }

        launchRoleAssignment(myPicks, opponentPicks)
    }

    /**
     * Monta a lista de [RoleAssignment]s a partir das lanes que o treinador
     * escolheu durante o pick & ban. Cada pick é pareado com o JOGADOR TITULAR
     * da lane correspondente — ou seja, se o coach disse "esse pick é da MID",
     * o titular de MID do time é quem joga com ele.
     *
     * Defensivo: se o time não tem titular para alguma role (caso raro mas
     * possível em saves transitórios), cai numa string vazia que o motor
     * tratará como "role natural do campeão".
     */
    private fun buildAssignmentsFromLanes(
        picks: List<String>,
        lanes: List<String>
    ): List<com.cblol.scout.data.RoleAssignment> {
        val starters = GameRepository.rosterOf(applicationContext, playerTeamId)
            .filter { it.titular }
        return picks.mapIndexedNotNull { index, championId ->
            val role = lanes.getOrNull(index) ?: return@mapIndexedNotNull null
            val player = starters.firstOrNull { it.role == role } ?: return@mapIndexedNotNull null
            com.cblol.scout.data.RoleAssignment(
                championId   = championId,
                playerName   = player.nome_jogo,
                assignedRole = role,
                // Role nativa do jogador no roster. Quando a lane escolhida
                // bate com a role natural (caso comum), [isWrongRole] retorna
                // false; quando o coach colocou um jogador fora da posção
                // (ex: TOP nato jogando MID por algum motivo), o motor aplica
                // penalidade de rota errada normalmente.
                naturalRole  = player.role
            )
        }
    }

    private fun launchRoleAssignment(myPicks: List<String>, opponentPicks: List<String>) {
        @Suppress("DEPRECATION")
        startActivityForResult(
            Intent(this, RoleAssignmentActivity::class.java).apply {
                putExtra(RoleAssignmentActivity.EXTRA_PLAYER_TEAM_ID, playerTeamId)
                putStringArrayListExtra(RoleAssignmentActivity.EXTRA_MY_PICKS, ArrayList(myPicks))
                putStringArrayListExtra(RoleAssignmentActivity.EXTRA_OPPONENT_PICKS, ArrayList(opponentPicks))
            },
            RoleAssignmentActivity.REQUEST_ROLE_ASSIGNMENT
        )
    }

    private fun handleRoleAssignmentResult(resultCode: Int, data: Intent?) {
        val payload = data?.getStringExtra(RoleAssignmentActivity.RESULT_ASSIGNMENTS_JSON) ?: ""
        val assignments = if (resultCode == RESULT_OK) {
            RoleAssignmentActivity.parseAssignments(payload)
        } else emptyList()

        savePickBanPlan(assignments)
        startSimulation()
        finish()
    }

    private fun savePickBanPlan(assignments: List<com.cblol.scout.data.RoleAssignment>) {
        val gs = GameRepository.current()
        gs.matches.find { it.id == matchId }?.pickBanPlan = PickBanPlan(
            mapNumber       = mapNumFromResult,
            bluePicks       = bluePicks,
            redPicks        = redPicks,
            blueBans        = blueBans,
            redBans         = redBans,
            roleAssignments = assignments
        )
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

        // Chaves de SavedInstanceState para sobreviver a recriações do Router
        private const val STATE_PICKBAN_LAUNCHED     = "router_pickban_launched"
        private const val STATE_MAP_NUM_FROM_RESULT  = "router_map_num_from_result"
        private const val STATE_BLUE_PICKS           = "router_blue_picks"
        private const val STATE_RED_PICKS            = "router_red_picks"
        private const val STATE_BLUE_BANS            = "router_blue_bans"
        private const val STATE_RED_BANS             = "router_red_bans"
        private const val STATE_PICKED_LANES         = "router_picked_lanes"
    }
}
