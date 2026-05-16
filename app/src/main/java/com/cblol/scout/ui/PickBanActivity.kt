package com.cblol.scout.ui

import androidx.activity.OnBackPressedCallback
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.cblol.scout.R
import com.cblol.scout.data.Champion
import com.cblol.scout.data.ChampionTag
import com.cblol.scout.data.PickBanPhase
import com.cblol.scout.data.PickBanState
import com.cblol.scout.game.GameRepository
import com.cblol.scout.util.ChampionRepository
import com.cblol.scout.util.CompositionRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.util.concurrent.atomic.AtomicInteger

class PickBanActivity : AppCompatActivity() {

    // ── Views principais ─────────────────────────────────────────────────
    private lateinit var rvChampions: RecyclerView
    private lateinit var championAdapter: ChampionGridAdapter
    private lateinit var cgRoleFilter: ChipGroup
    private lateinit var cgTagFilter: ChipGroup
    private lateinit var etSearch: EditText

    private lateinit var blueBanSlots: List<ImageView>
    private lateinit var redBanSlots: List<ImageView>
    private lateinit var bluePickSlots: List<PickSlotView>
    private lateinit var redPickSlots: List<PickSlotView>

    private lateinit var tvPhaseLabel: TextView
    private lateinit var tvMapNumber: TextView
    private lateinit var tvBlueName: TextView
    private lateinit var tvRedName: TextView
    private lateinit var btnConfirm: MaterialButton
    private lateinit var btnSkip: MaterialButton
    private lateinit var viewBlueTurn: View
    private lateinit var viewRedTurn: View

    // ── Barras de sinergia ──────────────────────────────────────────────
    private lateinit var pbBlueSynergy: ProgressBar
    private lateinit var pbRedSynergy: ProgressBar
    private lateinit var tvBlueCompLabel: TextView
    private lateinit var tvRedCompLabel: TextView
    private lateinit var tvBlueCompValue: TextView
    private lateinit var tvRedCompValue: TextView

    // ── Loading overlay ──────────────────────────────────────────────────
    private lateinit var viewLoadingOverlay: View
    private lateinit var progressBarLoading: ProgressBar
    private lateinit var tvLoadingStatus: TextView

    // ── Estado ───────────────────────────────────────────────────────────
    private lateinit var state: PickBanState
    private var selectedChampion: Champion? = null

    // Handler para IA agir com pequeno delay (sem mais countdown timer para o jogador)
    private val handler = Handler(Looper.getMainLooper())
    private var aiRunnable: Runnable? = null

    private var playerTeamId: String = ""
    private var opponentTeamId: String = ""
    private var matchId: String = ""
    private var mapNumber: Int = 1

    // ── Ordem de pick/ban (formato LoL padrão) ────────────────────────────
    private val turnOrder: List<Pair<Boolean, PickBanPhase>> = listOf(
        true  to PickBanPhase.BAN,  false to PickBanPhase.BAN,
        true  to PickBanPhase.BAN,  false to PickBanPhase.BAN,
        true  to PickBanPhase.BAN,  false to PickBanPhase.BAN,
        true  to PickBanPhase.PICK, false to PickBanPhase.PICK,
        false to PickBanPhase.PICK, true  to PickBanPhase.PICK,
        true  to PickBanPhase.PICK, false to PickBanPhase.PICK,
        false to PickBanPhase.BAN,  true  to PickBanPhase.BAN,
        false to PickBanPhase.BAN,  true  to PickBanPhase.BAN,
        false to PickBanPhase.PICK, true  to PickBanPhase.PICK,
        false to PickBanPhase.PICK, true  to PickBanPhase.PICK
    )

    // ─────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pick_ban)

        playerTeamId   = intent.getStringExtra("player_team_id") ?: ""
        opponentTeamId = intent.getStringExtra("opponent_team_id") ?: ""
        matchId        = intent.getStringExtra("match_id") ?: ""
        mapNumber      = intent.getIntExtra("map_number", 1)

        val playerIsBlue = (mapNumber % 2 == 1)

        bindViews()
        initState(playerIsBlue)
        renderTeamNames(playerIsBlue)

        // Intercepta o botão voltar durante o pick & ban
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { confirmExit() }
        })

        // Exibe overlay e pré-carrega todas as imagens antes de liberar a UI
        viewLoadingOverlay.visibility = View.VISIBLE
        preloadAllChampionImages {
            viewLoadingOverlay.visibility = View.GONE
            setupChampionGrid()
            setupRoleFilters()
            setupTagFilters()
            setupSearch()
            setupButtons()
            showBanSuggestions()
            advanceTurn()
        }
    }

    // ── Bind ──────────────────────────────────────────────────────────────
    private fun bindViews() {
        rvChampions        = findViewById(R.id.rv_champions)
        cgRoleFilter       = findViewById(R.id.cg_role_filter)
        cgTagFilter        = findViewById(R.id.cg_tag_filter)
        etSearch           = findViewById(R.id.et_search_champion)
        tvPhaseLabel       = findViewById(R.id.tv_phase_label)
        tvMapNumber        = findViewById(R.id.tv_map_number)
        tvBlueName         = findViewById(R.id.tv_blue_team_name)
        tvRedName          = findViewById(R.id.tv_red_team_name)
        btnConfirm         = findViewById(R.id.btn_confirm_pick)
        btnSkip            = findViewById(R.id.btn_skip)
        viewBlueTurn       = findViewById(R.id.view_blue_turn_indicator)
        viewRedTurn        = findViewById(R.id.view_red_turn_indicator)
        viewLoadingOverlay = findViewById(R.id.view_loading_overlay)
        progressBarLoading = findViewById(R.id.progress_bar_loading)
        tvLoadingStatus    = findViewById(R.id.tv_loading_status)

        pbBlueSynergy     = findViewById(R.id.pb_blue_synergy)
        pbRedSynergy      = findViewById(R.id.pb_red_synergy)
        tvBlueCompLabel   = findViewById(R.id.tv_blue_comp_label)
        tvRedCompLabel    = findViewById(R.id.tv_red_comp_label)
        tvBlueCompValue   = findViewById(R.id.tv_blue_comp_value)
        tvRedCompValue    = findViewById(R.id.tv_red_comp_value)

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

        tvMapNumber.text = "MAPA $mapNumber"
    }

    // ── Pré-carregamento de imagens com barra de progresso ───────────────
    private fun preloadAllChampionImages(onComplete: () -> Unit) {
        val champions = ChampionRepository.getAll()
        val total     = champions.size
        val done      = AtomicInteger(0)

        progressBarLoading.max      = total
        progressBarLoading.progress = 0
        tvLoadingStatus.text        = "Carregando campeões… 0/$total"

        fun tick() {
            val current = done.incrementAndGet()
            runOnUiThread {
                progressBarLoading.progress = current
                tvLoadingStatus.text = "Carregando campeões… $current/$total"
                if (current >= total) onComplete()
            }
        }

        champions.forEach { champ ->
            Glide.with(this)
                .load(champ.imageUrl)
                .apply(RequestOptions().centerCrop())
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?, model: Any?,
                        target: Target<Drawable>, isFirstResource: Boolean
                    ): Boolean { tick(); return false }

                    override fun onResourceReady(
                        resource: Drawable, model: Any,
                        target: Target<Drawable>, dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean { tick(); return false }
                })
                .preload()
        }
    }

    // ── Setup ─────────────────────────────────────────────────────────────
    private fun initState(playerIsBlue: Boolean) {
        state = PickBanState(
            currentTurnIndex = 0,
            blueBans         = mutableListOf(),
            redBans          = mutableListOf(),
            bluePicks        = mutableListOf(),
            redPicks         = mutableListOf(),
            playerIsBlue     = playerIsBlue,
            usedChampions    = mutableSetOf()
        )
    }

    private fun setupChampionGrid() {
        championAdapter = ChampionGridAdapter(ChampionRepository.getAll()) { onChampionSelected(it) }
        rvChampions.layoutManager = GridLayoutManager(this, 7)
        rvChampions.adapter = championAdapter
    }

    private fun setupRoleFilters() {
        listOf("ALL", "TOP", "JNG", "MID", "ADC", "SUP").forEach { role ->
            val chip = Chip(this).apply {
                text        = role
                isCheckable = true
                isChecked   = role == "ALL"
                textSize    = 11f
            }
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) championAdapter.filterByRole(if (role == "ALL") null else role)
            }
            cgRoleFilter.addView(chip)
        }
    }

    private fun setupTagFilters() {
        val draftTags = listOf(
            ChampionTag.TANK            to "🛡️ Tank",
            ChampionTag.ENGAGE          to "🚀 Engage",
            ChampionTag.CROWD_CONTROL   to "🔒 CC",
            ChampionTag.MAGIC_DAMAGE    to "✨ AP",
            ChampionTag.PHYSICAL_DAMAGE to "💪 AD",
            ChampionTag.ANTI_TANK       to "🔱 Anti-Tank",
            ChampionTag.HEAL            to "💉 Cura",
            ChampionTag.SHIELD          to "🔰 Escudo",
            ChampionTag.ASSASSIN        to "🗡️ Assassino",
            ChampionTag.POKE            to "🎯 Poke",
            ChampionTag.SPLIT_PUSH      to "🔀 Split",
            ChampionTag.HYPERCARRY      to "🌟 HyperCarry",
            ChampionTag.GLOBAL_ULT      to "🌐 Ult Global",
            ChampionTag.KNOCK_UP        to "☝️ Knock-up",
            ChampionTag.SUSTAIN         to "♻️ Sustain",
            ChampionTag.INVISIBLE       to "🫥 Invisível",
            ChampionTag.TRUE_DAMAGE     to "⚡ Dano Real"
        )

        draftTags.forEach { (tag, label) ->
            val chip = Chip(this).apply {
                text        = label
                isCheckable = true
                isChecked   = false
                textSize    = 10f
            }
            chip.setOnCheckedChangeListener { _, checked ->
                championAdapter.filterByTag(if (checked) tag else null)
                if (checked) {
                    for (i in 0 until cgTagFilter.childCount) {
                        val other = cgTagFilter.getChildAt(i) as? Chip
                        if (other != chip && other?.isChecked == true) other.isChecked = false
                    }
                }
            }
            cgTagFilter.addView(chip)
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

    private fun setupButtons() {
        btnConfirm.setOnClickListener {
            selectedChampion?.let { confirmAction(it) }
        }
        btnSkip.setOnClickListener {
            ChampionRepository.getAll()
                .filter { it.id !in state.usedChampions }
                .randomOrNull()
                ?.let { confirmAction(it) }
        }
    }

    // ── Fluxo de turnos ───────────────────────────────────────────────────
    private fun advanceTurn() {
        if (state.currentTurnIndex >= turnOrder.size) { finishPickBan(); return }

        val (isBlue, phase) = turnOrder[state.currentTurnIndex]
        val isPlayerTurn    = (isBlue == state.playerIsBlue)

        tvPhaseLabel.text = when (phase) {
            PickBanPhase.BAN  -> if (isBlue) "🔵 BANINDO"    else "🔴 BANINDO"
            PickBanPhase.PICK -> if (isBlue) "🔵 ESCOLHENDO" else "🔴 ESCOLHENDO"
        }

        viewBlueTurn.visibility = if (isBlue)  View.VISIBLE else View.INVISIBLE
        viewRedTurn.visibility  = if (!isBlue) View.VISIBLE else View.INVISIBLE

        rvChampions.alpha     = if (isPlayerTurn) 1f else 0.4f
        rvChampions.isEnabled = isPlayerTurn
        btnConfirm.isEnabled  = false
        btnSkip.visibility    = if (!isPlayerTurn) View.VISIBLE else View.GONE

        highlightActivePickSlot(isBlue, phase)
        selectedChampion = null
        championAdapter.clearSelection()

        // IA age com pequeno delay para visualizar a transição; jogador age livremente
        cancelAiRunnable()
        if (!isPlayerTurn) {
            aiRunnable = Runnable {
                ChampionRepository.getAll()
                    .filter { it.id !in state.usedChampions }
                    .randomOrNull()
                    ?.let { confirmAction(it) }
            }
            handler.postDelayed(aiRunnable!!, 1200L)
        }
    }

    private fun cancelAiRunnable() {
        aiRunnable?.let { handler.removeCallbacks(it) }
        aiRunnable = null
    }

    private fun onChampionSelected(champ: Champion) {
        if (champ.id in state.usedChampions) return
        val (isBlue, _) = turnOrder[state.currentTurnIndex]
        if (isBlue != state.playerIsBlue) return

        selectedChampion = champ
        championAdapter.setSelected(champ.id)
        btnConfirm.isEnabled = true

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(btnConfirm, "scaleX", 1f, 1.05f, 1f),
                ObjectAnimator.ofFloat(btnConfirm, "scaleY", 1f, 1.05f, 1f)
            )
            duration     = 200
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun confirmAction(champ: Champion) {
        cancelAiRunnable()
        val (isBlue, phase) = turnOrder[state.currentTurnIndex]
        state.usedChampions.add(champ.id)

        when (phase) {
            PickBanPhase.BAN -> {
                if (isBlue) {
                    state.blueBans.add(champ)
                    blueBanSlots.getOrNull(state.blueBans.size - 1)?.let {
                        loadChampionImage(it, champ, grayscale = true); animateBanSlot(it)
                    }
                } else {
                    state.redBans.add(champ)
                    redBanSlots.getOrNull(state.redBans.size - 1)?.let {
                        loadChampionImage(it, champ, grayscale = true); animateBanSlot(it)
                    }
                }
            }
            PickBanPhase.PICK -> {
                if (isBlue) {
                    state.bluePicks.add(champ)
                    bluePickSlots.getOrNull(state.bluePicks.size - 1)?.let {
                        it.setChampion(champ); it.setActive(false)
                    }
                } else {
                    state.redPicks.add(champ)
                    redPickSlots.getOrNull(state.redPicks.size - 1)?.let {
                        it.setChampion(champ); it.setActive(false)
                    }
                }
            }
        }

        // Atualiza barras de sinergia após CADA ação (bans podem destruir comps já formadas)
        updateSynergyBars()

        championAdapter.markUsed(champ.id)
        state.currentTurnIndex++
        selectedChampion = null
        advanceTurn()
    }

    private fun highlightActivePickSlot(isBlue: Boolean, phase: PickBanPhase) {
        if (phase != PickBanPhase.PICK) return
        if (isBlue) {
            bluePickSlots.forEachIndexed { i, s -> s.setActive(i == state.bluePicks.size) }
            redPickSlots.forEach { it.setActive(false) }
        } else {
            redPickSlots.forEachIndexed { i, s -> s.setActive(i == state.redPicks.size) }
            bluePickSlots.forEach { it.setActive(false) }
        }
    }

    private fun animateBanSlot(view: View) {
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(300).start()
    }

    private fun loadChampionImage(view: ImageView, champ: Champion, grayscale: Boolean = false) {
        Glide.with(this)
            .load(champ.imageUrl)
            .transition(DrawableTransitionOptions.withCrossFade(150))
            .apply(RequestOptions().centerCrop()
                .placeholder(R.color.champion_slot_bg)
                .error(R.color.champion_slot_bg))
            .into(view)

        view.colorFilter = if (grayscale) {
            ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        } else null
    }

    // ── Sugestão de bans baseada em composições perigosas ──────────────
    private fun showBanSuggestions() {
        val suggestions = CompositionRepository.suggestBans()
        if (suggestions.isEmpty()) return

        val top5 = suggestions.take(5)
        val msg = top5.joinToString("\n") { (champ, reason) -> "⚡ Banir $champ — $reason" }

        stylizedDialog(this)
            .setTitle("🚨 Composições Perigosas")
            .setMessage("Banir esses campeões neutraliza as composições mais fortes do meta:\n\n$msg")
            .setPositiveButton("Entendido", null)
            .show()
    }

    /**
     * Atualiza as barras de sinergia de AMBOS os lados.
     *
     * O score considera:
     *  - bônus da composição detectada (analyzeWithTags.base.bonusStrength)
     *  - bônus extra por equilíbrio de tags (extraBonus: AD+AP misto, CC abundante,
     *    knock-ups, anti-tank, etc.)
     *  - penalidades por falta de frontline ou anti-tank contra tanques inimigos
     *
     * Os bans do OPONENTE podem quebrar uma comp do nosso lado (se o
     * campeão-chave já banido fosse exigido). Por isso a atualização ocorre
     * tanto após picks quanto após bans.
     *
     * Escala visual: bonusStrength máx ≈ 18 (comp completa Tier S + tags). Mapeamos
     * para 0–100 multiplicando por 5.5 e capando em 100.
     */
    private fun updateSynergyBars() {
        val blueResult = CompositionRepository.analyzeWithTags(
            picks         = state.bluePicks.map { it.id },
            opponentPicks = state.redPicks.map  { it.id },
            bans          = state.redBans.map   { it.id }
        )
        val redResult = CompositionRepository.analyzeWithTags(
            picks         = state.redPicks.map { it.id },
            opponentPicks = state.bluePicks.map { it.id },
            bans          = state.blueBans.map  { it.id }
        )

        applySynergyToBar(
            bar         = pbBlueSynergy,
            tvLabel     = tvBlueCompLabel,
            tvValue     = tvBlueCompValue,
            totalBonus  = blueResult.totalBonus,
            comps       = blueResult.detectedComps,
            picksCount  = state.bluePicks.size
        )
        applySynergyToBar(
            bar         = pbRedSynergy,
            tvLabel     = tvRedCompLabel,
            tvValue     = tvRedCompValue,
            totalBonus  = redResult.totalBonus,
            comps       = redResult.detectedComps,
            picksCount  = state.redPicks.size
        )
    }

    /**
     * Aplica score à barra de sinergia.
     *
     * Quando o time monta MAIS DE UMA composição ao mesmo tempo, o label
     * exibe ambas: "⚡ Wombo Combo (60%) + Hard Engage (40%)". As barras
     * ainda crescem mais rápido nesses casos porque [totalBonus] já soma
     * os bônus parciais de cada comp detectada.
     */
    private fun applySynergyToBar(
        bar: ProgressBar,
        tvLabel: TextView,
        tvValue: TextView,
        totalBonus: Int,
        comps: List<CompositionRepository.DetectedComp>,
        picksCount: Int
    ) {
        val target = (totalBonus * 5.5).toInt().coerceIn(0, 100)
        ValueAnimator.ofInt(bar.progress, target).apply {
            duration = 400
            addUpdateListener { bar.progress = it.animatedValue as Int }
            start()
        }
        tvValue.text = if (totalBonus >= 0) "+$totalBonus" else totalBonus.toString()
        tvLabel.text = when {
            picksCount == 0    -> "Aguardando picks…"
            comps.isEmpty()    -> when {
                totalBonus < 0 -> "⚠️ Comp desbalanceada"
                totalBonus < 4 -> "Sinergia fraca"
                totalBonus < 8 -> "Sinergia parcial"
                else           -> "Sinergia forte"
            }
            comps.size == 1    -> "⚡ ${comps[0].composition.name} (${comps[0].percent}%)"
            else               -> {
                // Múltiplas comps simultâneas — mostra as 2 principais
                val first  = comps[0]
                val second = comps[1]
                "✨ ${first.composition.name} (${first.percent}%) + ${second.composition.name} (${second.percent}%)"
            }
        }
    }

    /**
     * Completa os turnos restantes com escolhas da IA e chama finishPickBan().
     */
    private fun completeMissingWithAI() {
        cancelAiRunnable()
        val available = ChampionRepository.getAll().filter { it.id !in state.usedChampions }
        val pool = available.toMutableList()

        while (state.currentTurnIndex < turnOrder.size) {
            val (isBlue, phase) = turnOrder[state.currentTurnIndex]
            val champ = pool.randomOrNull() ?: break
            pool.remove(champ)
            state.usedChampions.add(champ.id)
            when (phase) {
                PickBanPhase.BAN  -> if (isBlue) state.blueBans.add(champ) else state.redBans.add(champ)
                PickBanPhase.PICK -> if (isBlue) state.bluePicks.add(champ) else state.redPicks.add(champ)
            }
            state.currentTurnIndex++
        }
        finishPickBan()
    }

    private fun confirmExit() {
        stylizedDialog(this)
            .setTitle("Sair do Pick & Ban?")
            .setMessage("Deseja simular o restante automaticamente e ir direto para a partida?")
            .setPositiveButton("Simular até o fim") { _, _ ->
                completeMissingWithAI()
            }
            .setNeutralButton("Cancelar partida") { _, _ ->
                setResult(RESULT_CANCELED)
                cancelAiRunnable()
                finish()
            }
            .setNegativeButton("Continuar pick & ban", null)
            .show()
    }

    private fun renderTeamNames(playerIsBlue: Boolean) {
        val snap         = GameRepository.snapshot(applicationContext)
        val playerName   = snap.times.find { it.id == playerTeamId }?.nome   ?: playerTeamId
        val opponentName = snap.times.find { it.id == opponentTeamId }?.nome ?: opponentTeamId
        tvBlueName.text = if (playerIsBlue) playerName   else opponentName
        tvRedName.text  = if (playerIsBlue) opponentName else playerName
    }

    private fun finishPickBan() {
        cancelAiRunnable()
        setResult(RESULT_OK, Intent().apply {
            putStringArrayListExtra("blue_picks", ArrayList(state.bluePicks.map { it.id }))
            putStringArrayListExtra("red_picks",  ArrayList(state.redPicks.map  { it.id }))
            putStringArrayListExtra("blue_bans",  ArrayList(state.blueBans.map  { it.id }))
            putStringArrayListExtra("red_bans",   ArrayList(state.redBans.map   { it.id }))
            putExtra("match_id",   matchId)
            putExtra("map_number", mapNumber)
        })
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelAiRunnable()
    }

    companion object {
        const val REQUEST_PICK_BAN = 1001
    }
}
