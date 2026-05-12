package com.cblol.scout.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.data.Champion
import com.cblol.scout.data.PickBanState
import com.cblol.scout.data.PickBanPhase
import com.cblol.scout.game.GameRepository
import com.cblol.scout.util.ChampionRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * PickBanActivity — Tela de pick & ban estilo LoL esports (BO3/BO5)
 *
 * Fluxo:
 *  1. Fase de BANS alternados: azul ban → vermelho ban → (×3 each)
 *  2. Fase de PICKS alternados: azul pick → vermelho pick × 2 → azul pick × 2 → vermelho pick → azul pick × 2 → vermelho pick
 *  3. Ao finalizar → lança MatchResultActivity (ou ScheduleActivity com resultado)
 *
 * Integração: chamada por ScheduleActivity via:
 *   val intent = Intent(this, PickBanActivity::class.java)
 *   intent.putExtra("player_team_id", playerTeamId)
 *   intent.putExtra("opponent_team_id", opponentTeamId)
 *   intent.putExtra("match_id", matchId)
 *   intent.putExtra("map_number", mapNumber) // 1, 2 ou 3
 *   startActivityForResult(intent, REQUEST_PICK_BAN)
 *
 * Retorno via setResult:
 *   Intent com "blue_picks" e "red_picks" como ArrayList<String> (champion ids)
 */
class PickBanActivity : AppCompatActivity() {

    // ── Views principais ────────────────────────────────────────────────
    private lateinit var rvChampions: RecyclerView
    private lateinit var championAdapter: ChampionGridAdapter

    private lateinit var cgRoleFilter: ChipGroup
    private lateinit var etSearch: EditText

    // Slots de ban (6 por lado)
    private lateinit var blueBanSlots: List<ImageView>
    private lateinit var redBanSlots: List<ImageView>

    // Slots de pick (5 por lado)
    private lateinit var bluePickSlots: List<PickSlotView>
    private lateinit var redPickSlots: List<PickSlotView>

    private lateinit var tvPhaseLabel: TextView
    private lateinit var tvTimerLabel: TextView
    private lateinit var tvMapNumber: TextView
    private lateinit var tvBlueName: TextView
    private lateinit var tvRedName: TextView
    private lateinit var btnConfirm: MaterialButton
    private lateinit var btnSkip: MaterialButton
    private lateinit var viewBlueTurn: View
    private lateinit var viewRedTurn: View

    // ── Estado ──────────────────────────────────────────────────────────
    private lateinit var state: PickBanState
    private var selectedChampion: Champion? = null
    private var countDownTimer: CountDownTimer? = null

    private var playerTeamId: String = ""
    private var opponentTeamId: String = ""
    private var matchId: String = ""
    private var mapNumber: Int = 1

    // ── Ordem de pick/ban (formato LoL padrão) ───────────────────────────
    // true = lado azul age; false = lado vermelho age
    // B=ban P=pick; posições 0..17
    private val turnOrder: List<Pair<Boolean, PickBanPhase>> = listOf(
        // Bans fase 1 (3 ban cada)
        true  to PickBanPhase.BAN,
        false to PickBanPhase.BAN,
        true  to PickBanPhase.BAN,
        false to PickBanPhase.BAN,
        true  to PickBanPhase.BAN,
        false to PickBanPhase.BAN,
        // Picks fase 1
        true  to PickBanPhase.PICK,
        false to PickBanPhase.PICK,
        false to PickBanPhase.PICK,
        true  to PickBanPhase.PICK,
        true  to PickBanPhase.PICK,
        false to PickBanPhase.PICK,
        // Bans fase 2 (2 ban cada)
        false to PickBanPhase.BAN,
        true  to PickBanPhase.BAN,
        false to PickBanPhase.BAN,
        true  to PickBanPhase.BAN,
        // Picks fase 2
        false to PickBanPhase.PICK,
        true  to PickBanPhase.PICK,
        false to PickBanPhase.PICK,
        true  to PickBanPhase.PICK
    )

    // ────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pick_ban)

        playerTeamId   = intent.getStringExtra("player_team_id") ?: ""
        opponentTeamId = intent.getStringExtra("opponent_team_id") ?: ""
        matchId        = intent.getStringExtra("match_id") ?: ""
        mapNumber      = intent.getIntExtra("map_number", 1)

        // Decide lado do jogador (azul = casa, vermelho = visitante; alterna por mapa)
        val playerIsBlue = (mapNumber % 2 == 1)

        bindViews()
        initState(playerIsBlue)
        setupChampionGrid()
        setupRoleFilters()
        setupSearch()
        setupButtons()
        renderTeamNames(playerIsBlue)
        advanceTurn()
    }

    // ────────────────────────────────────────────────────────────────────
    private fun bindViews() {
        rvChampions   = findViewById(R.id.rv_champions)
        cgRoleFilter  = findViewById(R.id.cg_role_filter)
        etSearch      = findViewById(R.id.et_search_champion)
        tvPhaseLabel  = findViewById(R.id.tv_phase_label)
        tvTimerLabel  = findViewById(R.id.tv_timer)
        tvMapNumber   = findViewById(R.id.tv_map_number)
        tvBlueName    = findViewById(R.id.tv_blue_team_name)
        tvRedName     = findViewById(R.id.tv_red_team_name)
        btnConfirm    = findViewById(R.id.btn_confirm_pick)
        btnSkip       = findViewById(R.id.btn_skip)
        viewBlueTurn  = findViewById(R.id.view_blue_turn_indicator)
        viewRedTurn   = findViewById(R.id.view_red_turn_indicator)

        blueBanSlots = listOf(
            findViewById(R.id.iv_blue_ban_1), findViewById(R.id.iv_blue_ban_2),
            findViewById(R.id.iv_blue_ban_3), findViewById(R.id.iv_blue_ban_4),
            findViewById(R.id.iv_blue_ban_5)
        )
        redBanSlots = listOf(
            findViewById(R.id.iv_red_ban_1), findViewById(R.id.iv_red_ban_2),
            findViewById(R.id.iv_red_ban_3), findViewById(R.id.iv_red_ban_4),
            findViewById(R.id.iv_red_ban_5)
        )
        bluePickSlots = listOf(
            findViewById(R.id.psv_blue_1), findViewById(R.id.psv_blue_2),
            findViewById(R.id.psv_blue_3), findViewById(R.id.psv_blue_4),
            findViewById(R.id.psv_blue_5)
        )
        redPickSlots = listOf(
            findViewById(R.id.psv_red_1), findViewById(R.id.psv_red_2),
            findViewById(R.id.psv_red_3), findViewById(R.id.psv_red_4),
            findViewById(R.id.psv_red_5)
        )

        tvMapNumber.text = "Mapa $mapNumber"
    }

    // ────────────────────────────────────────────────────────────────────
    private fun initState(playerIsBlue: Boolean) {
        state = PickBanState(
            currentTurnIndex = 0,
            blueBans   = mutableListOf(),
            redBans    = mutableListOf(),
            bluePicks  = mutableListOf(),
            redPicks   = mutableListOf(),
            playerIsBlue = playerIsBlue,
            usedChampions = mutableSetOf()
        )
    }

    // ────────────────────────────────────────────────────────────────────
    private fun setupChampionGrid() {
        val allChampions = ChampionRepository.getAll()
        championAdapter = ChampionGridAdapter(allChampions) { champ ->
            onChampionSelected(champ)
        }
        rvChampions.layoutManager = GridLayoutManager(this, 7)
        rvChampions.adapter = championAdapter
    }

    private fun setupRoleFilters() {
        val roles = listOf("ALL", "TOP", "JNG", "MID", "ADC", "SUP")
        roles.forEach { role ->
            val chip = Chip(this).apply {
                text = role
                isCheckable = true
                isChecked = role == "ALL"
            }
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) filterByRole(if (role == "ALL") null else role)
            }
            cgRoleFilter.addView(chip)
        }
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                championAdapter.filter(s?.toString() ?: "")
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun filterByRole(role: String?) {
        championAdapter.filterByRole(role)
    }

    // ────────────────────────────────────────────────────────────────────
    private fun setupButtons() {
        btnConfirm.setOnClickListener {
            val champ = selectedChampion ?: return@setOnClickListener
            confirmAction(champ)
        }
        btnSkip.setOnClickListener {
            // IA age automaticamente (random)
            val available = ChampionRepository.getAll()
                .filter { it.id !in state.usedChampions }
            if (available.isNotEmpty()) {
                confirmAction(available.random())
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────
    private fun advanceTurn() {
        if (state.currentTurnIndex >= turnOrder.size) {
            finishPickBan()
            return
        }

        val (isBlue, phase) = turnOrder[state.currentTurnIndex]
        val isPlayerTurn = (isBlue == state.playerIsBlue)

        // Atualiza label de fase
        val phaseText = when (phase) {
            PickBanPhase.BAN  -> if (isBlue) "🔵 BANINDO" else "🔴 BANINDO"
            PickBanPhase.PICK -> if (isBlue) "🔵 ESCOLHENDO" else "🔴 ESCOLHENDO"
        }
        tvPhaseLabel.text = phaseText

        // Indicadores de turno
        viewBlueTurn.visibility = if (isBlue)  View.VISIBLE else View.INVISIBLE
        viewRedTurn.visibility  = if (!isBlue) View.VISIBLE else View.INVISIBLE

        // Habilita grid apenas no turno do jogador
        val gridEnabled = isPlayerTurn
        rvChampions.alpha = if (gridEnabled) 1f else 0.4f
        rvChampions.isEnabled = gridEnabled
        btnConfirm.isEnabled = false
        btnSkip.visibility = if (!isPlayerTurn) View.VISIBLE else View.GONE

        highlightActivePickSlot(isBlue, phase)
        selectedChampion = null
        championAdapter.clearSelection()

        if (!isPlayerTurn) {
            // IA age após 1.5s
            startTimer(1500L, autoAct = true)
        } else {
            startTimer(30_000L, autoAct = false)
        }
    }

    private fun startTimer(durationMs: Long, autoAct: Boolean) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(ms: Long) {
                tvTimerLabel.text = (ms / 1000).toString()
                if (ms / 1000 <= 5L) {
                    tvTimerLabel.setTextColor(ContextCompat.getColor(this@PickBanActivity, android.R.color.holo_red_light))
                } else {
                    tvTimerLabel.setTextColor(ContextCompat.getColor(this@PickBanActivity, android.R.color.white))
                }
            }
            override fun onFinish() {
                if (autoAct) {
                    // IA faz escolha automática
                    val available = ChampionRepository.getAll().filter { it.id !in state.usedChampions }
                    if (available.isNotEmpty()) confirmAction(available.random())
                } else {
                    // Jogador esgotou tempo → random
                    val available = ChampionRepository.getAll().filter { it.id !in state.usedChampions }
                    if (available.isNotEmpty()) confirmAction(available.random())
                }
            }
        }.start()
    }

    // ────────────────────────────────────────────────────────────────────
    private fun onChampionSelected(champ: Champion) {
        if (champ.id in state.usedChampions) return
        val (isBlue, _) = turnOrder[state.currentTurnIndex]
        val isPlayerTurn = (isBlue == state.playerIsBlue)
        if (!isPlayerTurn) return

        selectedChampion = champ
        championAdapter.setSelected(champ.id)
        btnConfirm.isEnabled = true

        // Animação de pulso no botão confirmar
        val scaleX = ObjectAnimator.ofFloat(btnConfirm, "scaleX", 1f, 1.05f, 1f)
        val scaleY = ObjectAnimator.ofFloat(btnConfirm, "scaleY", 1f, 1.05f, 1f)
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 200
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun confirmAction(champ: Champion) {
        countDownTimer?.cancel()
        val (isBlue, phase) = turnOrder[state.currentTurnIndex]
        state.usedChampions.add(champ.id)

        when (phase) {
            PickBanPhase.BAN -> {
                if (isBlue) {
                    state.blueBans.add(champ)
                    val idx = state.blueBans.size - 1
                    if (idx < blueBanSlots.size) {
                        loadChampionImage(blueBanSlots[idx], champ, grayscale = true)
                        animateBanSlot(blueBanSlots[idx])
                    }
                } else {
                    state.redBans.add(champ)
                    val idx = state.redBans.size - 1
                    if (idx < redBanSlots.size) {
                        loadChampionImage(redBanSlots[idx], champ, grayscale = true)
                        animateBanSlot(redBanSlots[idx])
                    }
                }
            }
            PickBanPhase.PICK -> {
                if (isBlue) {
                    state.bluePicks.add(champ)
                    val idx = state.bluePicks.size - 1
                    if (idx < bluePickSlots.size) {
                        bluePickSlots[idx].setChampion(champ)
                        bluePickSlots[idx].setActive(false)
                    }
                } else {
                    state.redPicks.add(champ)
                    val idx = state.redPicks.size - 1
                    if (idx < redPickSlots.size) {
                        redPickSlots[idx].setChampion(champ)
                        redPickSlots[idx].setActive(false)
                    }
                }
            }
        }

        championAdapter.markUsed(champ.id)
        state.currentTurnIndex++
        selectedChampion = null
        advanceTurn()
    }

    // ────────────────────────────────────────────────────────────────────
    private fun highlightActivePickSlot(isBlue: Boolean, phase: PickBanPhase) {
        if (phase != PickBanPhase.PICK) return
        if (isBlue) {
            val idx = state.bluePicks.size
            bluePickSlots.forEachIndexed { i, slot -> slot.setActive(i == idx) }
            redPickSlots.forEach { it.setActive(false) }
        } else {
            val idx = state.redPicks.size
            redPickSlots.forEachIndexed { i, slot -> slot.setActive(i == idx) }
            bluePickSlots.forEach { it.setActive(false) }
        }
    }

    private fun animateBanSlot(view: View) {
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(300).start()
    }

    /**
     * Carrega imagem do campeão via URL da Data Dragon da Riot.
     * Ex: https://ddragon.leagueoflegends.com/cdn/14.10.1/img/champion/Ahri.png
     * Se quiser usar Glide/Coil, substitua aqui.
     */
    private fun loadChampionImage(view: ImageView, champ: Champion, grayscale: Boolean = false) {
        // Placeholder com cor de fundo e inicial — substitua por Glide/Coil
        view.setBackgroundColor(ContextCompat.getColor(this, R.color.champion_slot_bg))
        // TODO: Glide.with(this).load(champ.imageUrl).into(view)
        // Com Glide e filtro de escala de cinza para bans:
        // val options = RequestOptions().let { if (grayscale) it.colorSpace(ColorSpace.Named.LINEAR_SRGB) else it }
        // Glide.with(this).load(champ.imageUrl).apply(options).into(view)
    }

    // ────────────────────────────────────────────────────────────────────
    private fun renderTeamNames(playerIsBlue: Boolean) {
        val snap         = GameRepository.snapshot(applicationContext)
        val playerName   = snap.times.find { it.id == playerTeamId }?.nome   ?: playerTeamId
        val opponentName = snap.times.find { it.id == opponentTeamId }?.nome ?: opponentTeamId
        if (playerIsBlue) {
            tvBlueName.text = playerName
            tvRedName.text  = opponentName
        } else {
            tvBlueName.text = opponentName
            tvRedName.text  = playerName
        }
    }

    // ────────────────────────────────────────────────────────────────────
    private fun finishPickBan() {
        countDownTimer?.cancel()
        val result = Intent().apply {
            putStringArrayListExtra("blue_picks", ArrayList(state.bluePicks.map { it.id }))
            putStringArrayListExtra("red_picks",  ArrayList(state.redPicks.map  { it.id }))
            putStringArrayListExtra("blue_bans",  ArrayList(state.blueBans.map  { it.id }))
            putStringArrayListExtra("red_bans",   ArrayList(state.redBans.map   { it.id }))
            putExtra("match_id", matchId)
            putExtra("map_number", mapNumber)
        }
        setResult(RESULT_OK, result)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }

    companion object {
        const val REQUEST_PICK_BAN = 1001
    }
}
