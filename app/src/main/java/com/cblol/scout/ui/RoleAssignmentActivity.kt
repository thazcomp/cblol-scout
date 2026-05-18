package com.cblol.scout.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.cblol.scout.R
import com.cblol.scout.data.Player
import com.cblol.scout.data.RoleAssignment
import com.cblol.scout.game.GameRepository
import com.cblol.scout.util.ChampionRepository

/**
 * Tela de **Estratégia de Rotas** — aparece após o pick & ban e antes da simulação.
 *
 * Permite ao treinador reatribuir cada um dos 5 picks do time aliado a um jogador
 * específico do roster. Por padrão cada campeão é atribuído ao jogador da role
 * "natural" do pick (ex: campeão TOP → jogador TOP titular), mas o técnico pode
 * remanejar: um jogador TOP pode ir para mid, um support para jungle, etc.
 *
 * Quando há mismatch entre a role natural do jogador e a role do pick atribuído,
 * a UI mostra "⚠ ROTA ERRADA" e a simulação aplicará penalidade.
 *
 * **SOLID:**
 * - **SRP**: a Activity orquestra a UI. Os cálculos de mismatch e a validação
 *   de duplicatas ficam em helpers privados; o resultado é serializado em
 *   [RoleAssignment]s passados via Intent.
 * - **OCP**: adicionar nova restrição (ex: "jogador lesionado não pode jogar")
 *   é estender [validateAssignments]; o resto da UI não precisa mudar.
 * - **DIP**: depende apenas do roster do [GameRepository] e do
 *   [ChampionRepository]. Não conhece a próxima Activity (quem inicia esta
 *   recebe o resultado via setResult).
 */
class RoleAssignmentActivity : AppCompatActivity() {

    /** Cada slot da lista (UI) — referência às views + o estado mutável. */
    private data class AssignmentSlot(
        val championId: String,
        val opponentChampionId: String?,
        val pickRole: String,                 // role natural do pick (TOP/JNG/MID/ADC/SUP)
        val container: View,
        val spinner: Spinner,
        val tvWarning: TextView,
        var selectedPlayer: Player
    )

    private val slots = mutableListOf<AssignmentSlot>()
    private lateinit var llAssignments: LinearLayout
    private lateinit var playerStarters: List<Player>
    private lateinit var myPickIds: List<String>
    private lateinit var opponentPickIds: List<String>

    // ── Lifecycle ───────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_assignment)

        readIntentExtras()
        if (myPickIds.size != 5 || playerStarters.size < 5) {
            // Sem dados suficientes — devolve assignments vazios e segue o fluxo
            setResult(RESULT_OK, Intent().putExtra(RESULT_ASSIGNMENTS_JSON, ""))
            finish()
            return
        }

        llAssignments = findViewById(R.id.ll_assignments)
        renderSlots()
        findViewById<View>(R.id.btn_confirm_assignments).setOnClickListener { onConfirm() }
    }

    // ── Leitura do Intent ───────────────────────────────────────────────

    private fun readIntentExtras() {
        val playerTeamId = intent.getStringExtra(EXTRA_PLAYER_TEAM_ID) ?: ""
        myPickIds       = intent.getStringArrayListExtra(EXTRA_MY_PICKS)?.toList()        ?: emptyList()
        opponentPickIds = intent.getStringArrayListExtra(EXTRA_OPPONENT_PICKS)?.toList()  ?: emptyList()

        // Roster do time do jogador, titulares ordenados por role canônica
        playerStarters = GameRepository.rosterOf(applicationContext, playerTeamId)
            .filter { it.titular }
            .sortedBy { ROLE_ORDER.indexOf(it.role).let { i -> if (i < 0) 99 else i } }
    }

    // ── Renderização dos slots ──────────────────────────────────────────

    private fun renderSlots() {
        // Para cada pick aliado, inferimos a role natural pela ORDEM do draft:
        // a ordem snake CBLOL coloca os picks na sequência TOP/JNG/MID/ADC/SUP do
        // primeiro pick para os subsequentes na maioria dos casos. Como simplificação
        // estável, usamos a role primária do CHAMPION para inferir a role pretendida
        // do pick. Fallback: usar a posição na lista.
        myPickIds.forEachIndexed { index, championId ->
            val champion = ChampionRepository.getById(championId)
            val pickRole = champion?.primaryRole ?: roleAtIndex(index)
            val opponentChampion = opponentPickIds.getOrNull(index)
            // Jogador inicial: titular dessa role
            val defaultPlayer = playerStarters.firstOrNull { it.role == pickRole }
                                ?: playerStarters[index.coerceIn(0, playerStarters.lastIndex)]
            addSlot(championId, opponentChampion, pickRole, defaultPlayer)
        }
        refreshWarnings()
    }

    /** Cria a view de um slot e adiciona ao LinearLayout. */
    private fun addSlot(
        championId: String,
        opponentChampionId: String?,
        pickRole: String,
        defaultPlayer: Player
    ) {
        val view = layoutInflater.inflate(R.layout.item_role_assignment, llAssignments, false)
        val ivAvatar    = view.findViewById<ImageView>(R.id.iv_champ_avatar)
        val ivOpponent  = view.findViewById<ImageView>(R.id.iv_opponent_avatar)
        val tvName      = view.findViewById<TextView>(R.id.tv_champ_name)
        val tvWarning   = view.findViewById<TextView>(R.id.tv_wrong_role_warning)
        val spinner     = view.findViewById<Spinner>(R.id.sp_role)

        ChampionRepository.getById(championId)?.let { champ ->
            tvName.text = champ.name
            Glide.with(this).load(champ.imageUrl).into(ivAvatar)
        }
        if (opponentChampionId != null) {
            ChampionRepository.getById(opponentChampionId)?.let { opp ->
                Glide.with(this).load(opp.imageUrl).into(ivOpponent)
            }
        } else {
            ivOpponent.visibility = View.GONE
        }

        // Adapter com os nomes dos 5 titulares (mesmo conjunto em todos os spinners
        // para permitir reatribuição livre). O ROLE_ORDER define a ordem visual.
        val playerLabels = playerStarters.map { "${it.role} · ${it.nome_jogo}" }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, playerLabels)
            .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinner.setSelection(playerStarters.indexOf(defaultPlayer).coerceAtLeast(0))

        val slot = AssignmentSlot(
            championId         = championId,
            opponentChampionId = opponentChampionId,
            pickRole           = pickRole,
            container          = view,
            spinner            = spinner,
            tvWarning          = tvWarning,
            selectedPlayer     = defaultPlayer
        )
        slots += slot

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, position: Int, id: Long) {
                slot.selectedPlayer = playerStarters[position]
                refreshWarnings()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        llAssignments.addView(view)
    }

    /**
     * Atualiza o aviso "ROTA ERRADA" para cada slot.
     * Visível quando a role do pick (ex: ADC) difere da role natural do jogador
     * selecionado (ex: jogador é SUP).
     */
    private fun refreshWarnings() {
        for (slot in slots) {
            val mismatch = slot.selectedPlayer.role != slot.pickRole
            slot.tvWarning.visibility = if (mismatch) View.VISIBLE else View.GONE
        }
    }

    // ── Confirmação ─────────────────────────────────────────────────────

    private fun onConfirm() {
        val duplicate = validateAssignments()
        if (duplicate != null) {
            stylizedDialog(this)
                .setTitle(R.string.app_name)
                .setMessage(getString(R.string.role_assign_duplicate))
                .setPositiveButton(R.string.btn_ok, null)
                .show()
            return
        }

        // Serializa como pares "championId|playerName|assignedRole|naturalRole"
        // separados por "\n". Simples e suficiente — evita Gson na ponte de Intent.
        val payload = slots.joinToString(separator = "\n") { s ->
            listOf(
                s.championId,
                s.selectedPlayer.nome_jogo,
                s.pickRole,                  // assigned: o pick decide a rota
                s.selectedPlayer.role        // natural: role nativa do jogador
            ).joinToString("|")
        }
        setResult(RESULT_OK, Intent().putExtra(RESULT_ASSIGNMENTS_JSON, payload))
        finish()
    }

    /** Retorna o nome do jogador duplicado ou null se não houver. */
    private fun validateAssignments(): String? {
        val grouped = slots.groupBy { it.selectedPlayer.nome_jogo }
        return grouped.entries.firstOrNull { it.value.size > 1 }?.key
    }

    private fun roleAtIndex(index: Int): String =
        ROLE_ORDER.getOrElse(index) { "MID" }

    companion object {
        const val REQUEST_ROLE_ASSIGNMENT = 2002

        const val EXTRA_PLAYER_TEAM_ID  = "ra_player_team_id"
        const val EXTRA_MY_PICKS        = "ra_my_picks"
        const val EXTRA_OPPONENT_PICKS  = "ra_opponent_picks"

        const val RESULT_ASSIGNMENTS_JSON = "ra_assignments"

        private val ROLE_ORDER = listOf("TOP", "JNG", "MID", "ADC", "SUP")

        /**
         * Parser do payload serializado retornado em [RESULT_ASSIGNMENTS_JSON].
         * Cada linha tem o formato `championId|playerName|assignedRole|naturalRole`.
         */
        fun parseAssignments(payload: String): List<RoleAssignment> {
            if (payload.isBlank()) return emptyList()
            return payload.split("\n").mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size != 4) return@mapNotNull null
                RoleAssignment(
                    championId   = parts[0],
                    playerName   = parts[1],
                    assignedRole = parts[2],
                    naturalRole  = parts[3]
                )
            }
        }
    }
}
