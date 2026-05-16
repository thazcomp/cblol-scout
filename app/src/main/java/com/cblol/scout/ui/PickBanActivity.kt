package com.cblol.scout.ui

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
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
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
import com.cblol.scout.data.Player
import com.cblol.scout.domain.GameConstants
import com.cblol.scout.game.GameRepository
import com.cblol.scout.util.ChampionRepository
import com.cblol.scout.util.CompositionRepository
import com.cblol.scout.util.PickSuggestionEngine
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tela imersiva de pick & ban manual.
 *
 * SOLID:
 * - **SRP**: cada método tem responsabilidade única ([setupRoleFilters],
 *   [setupTagFilters], [updateSynergyBars], [completeMissingWithAI]).
 * - **OCP**: a lista de tags filtráveis em [DRAFT_TAGS_RESOURCE] é declarativa;
 *   adicionar uma nova tag não muda a lógica.
 * - **DIP**: depende apenas de [ChampionRepository] e [CompositionRepository]
 *   (camada de utilitário), e o adapter [ChampionGridAdapter] é injetado por
 *   callback. Não há referência a Activities concretas.
 *
 * Strings em PT-BR vêm de `R.string.*`; cores hex de `R.color.*`; números
 * mágicos (delays, escalas, contagens) de [GameConstants.Draft].
 */
class PickBanActivity : AppCompatActivity() {

    // ── Views principais ────────────────────────────────────────────────
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

    // Sugestões contextuais de pick
    private lateinit var llSuggestionsContainer: View
    private lateinit var rvSuggestions: RecyclerView
    private lateinit var suggestionsAdapter: PickSuggestionAdapter
    /** Titulares do time do jogador ordenados por role (TOP→SUP). Usado para descobrir
     *  qual atleta vai pickar em cada slot e oferecer seus mains como sugestão. */
    private var playerStarters: List<Player> = emptyList()

    // ── Loading overlay ─────────────────────────────────────────────────
    private lateinit var viewLoadingOverlay: View
    private lateinit var progressBarLoading: ProgressBar
    private lateinit var tvLoadingStatus: TextView

    // ── Estado ──────────────────────────────────────────────────────────
    private lateinit var state: PickBanState
    private var selectedChampion: Champion? = null

    // Handler usado pela IA para agir após delay
    private val handler = Handler(Looper.getMainLooper())
    private var aiRunnable: Runnable? = null

    private var playerTeamId: String = ""
    private var opponentTeamId: String = ""
    private var matchId: String = ""
    private var mapNumber: Int = 1

    // Ordem de pick/ban padrão LoL (formato CBLOL)
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

    // ── Lifecycle ───────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pick_ban)

        readIntentExtras()
        val playerIsBlue = (mapNumber % 2 == 1)

        bindViews()
        initState(playerIsBlue)
        renderTeamNames(playerIsBlue)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmExit()
        })

        viewLoadingOverlay.visibility = View.VISIBLE
        preloadAllChampionImages {
            viewLoadingOverlay.visibility = View.GONE
            setupChampionGrid()
            setupSuggestions()
            setupRoleFilters()
            setupTagFilters()
            setupSearch()
            setupButtons()
            showBanSuggestions()
            advanceTurn()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelAiRunnable()
    }

    // ── Inicialização ───────────────────────────────────────────────────

    private fun readIntentExtras() {
        playerTeamId   = intent.getStringExtra(EXTRA_PLAYER_TEAM_ID) ?: ""
        opponentTeamId = intent.getStringExtra(EXTRA_OPPONENT_TEAM_ID) ?: ""
        matchId        = intent.getStringExtra(EXTRA_MATCH_ID) ?: ""
        mapNumber      = intent.getIntExtra(EXTRA_MAP_NUMBER, 1)
    }

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

        pbBlueSynergy   = findViewById(R.id.pb_blue_synergy)
        pbRedSynergy    = findViewById(R.id.pb_red_synergy)
        tvBlueCompLabel = findViewById(R.id.tv_blue_comp_label)
        tvRedCompLabel  = findViewById(R.id.tv_red_comp_label)
        tvBlueCompValue = findViewById(R.id.tv_blue_comp_value)
        tvRedCompValue  = findViewById(R.id.tv_red_comp_value)

        llSuggestionsContainer = findViewById(R.id.ll_suggestions_container)
        rvSuggestions          = findViewById(R.id.rv_suggestions)

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

        tvMapNumber.text = getString(R.string.pb_map_label, mapNumber)
    }

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

    // ── Pré-carregamento de imagens ─────────────────────────────────────

    private fun preloadAllChampionImages(onComplete: () -> Unit) {
        val champions = ChampionRepository.getAll()
        val total     = champions.size
        val done      = AtomicInteger(0)

        progressBarLoading.max      = total
        progressBarLoading.progress = 0
        tvLoadingStatus.text        = getString(R.string.loading_champions_progress, 0, total)

        fun tick() {
            val current = done.incrementAndGet()
            runOnUiThread {
                progressBarLoading.progress = current
                tvLoadingStatus.text        = getString(R.string.loading_champions_progress, current, total)
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

    // ── Setup de componentes ────────────────────────────────────────────

    private fun setupChampionGrid() {
        championAdapter = ChampionGridAdapter(ChampionRepository.getAll()) { onChampionSelected(it) }
        rvChampions.layoutManager = GridLayoutManager(this, GameConstants.Draft.GRID_COLUMNS)
        rvChampions.adapter = championAdapter

        // Carrega o roster do time do jogador (titulares ordenados por role) para
        // alimentar o motor de sugestões com o champion pool do atleta da role atual.
        playerStarters = GameRepository.rosterOf(applicationContext, playerTeamId)
            .filter { it.titular }
            .sortedBy { roleOrder(it.role) }
    }

    /**
     * Configura a faixa horizontal de sugestões contextuais.
     * Tocar num card de sugestão seleciona e confirma o pick imediatamente.
     */
    private fun setupSuggestions() {
        suggestionsAdapter = PickSuggestionAdapter { champ ->
            onChampionSelected(champ)
            confirmAction(champ)
        }
        rvSuggestions.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvSuggestions.adapter = suggestionsAdapter
    }

    /** Ordem canônica de roles (TOP→SUP). */
    private fun roleOrder(role: String): Int = when (role) {
        "TOP" -> 0; "JNG" -> 1; "MID" -> 2; "ADC" -> 3; "SUP" -> 4; else -> 5
    }

    private fun setupRoleFilters() {
        ROLE_FILTERS.forEach { (roleKey, labelRes) ->
            val chip = Chip(this).apply {
                text        = getString(labelRes)
                isCheckable = true
                isChecked   = (roleKey == ROLE_ALL)
                textSize    = resources.getDimension(R.dimen.pickban_role_chip_text_size) /
                              resources.displayMetrics.scaledDensity
            }
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) championAdapter.filterByRole(if (roleKey == ROLE_ALL) null else roleKey)
            }
            cgRoleFilter.addView(chip)
        }
    }

    private fun setupTagFilters() {
        DRAFT_TAGS_RESOURCE.forEach { (tag, labelRes) ->
            val chip = Chip(this).apply {
                text        = getString(labelRes)
                isCheckable = true
                isChecked   = false
                textSize    = resources.getDimension(R.dimen.pickban_tag_chip_text_size) /
                              resources.displayMetrics.scaledDensity
            }
            chip.setOnCheckedChangeListener { _, checked ->
                championAdapter.filterByTag(if (checked) tag else null)
                if (checked) uncheckOtherTagChips(chip)
            }
            cgTagFilter.addView(chip)
        }
    }

    /** Garante seleção exclusiva entre chips de tag (singleSelection emulada). */
    private fun uncheckOtherTagChips(selected: Chip) {
        for (i in 0 until cgTagFilter.childCount) {
            val other = cgTagFilter.getChildAt(i) as? Chip
            if (other != selected && other?.isChecked == true) other.isChecked = false
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
            pickRandomAvailableChampion()?.let { confirmAction(it) }
        }
    }

    private fun pickRandomAvailableChampion(): Champion? =
        ChampionRepository.getAll().filter { it.id !in state.usedChampions }.randomOrNull()

    // ── Fluxo de turnos ─────────────────────────────────────────────────

    private fun advanceTurn() {
        if (state.currentTurnIndex >= turnOrder.size) { finishPickBan(); return }

        val (isBlue, phase) = turnOrder[state.currentTurnIndex]
        val isPlayerTurn    = (isBlue == state.playerIsBlue)

        tvPhaseLabel.text = phaseLabelFor(isBlue, phase)
        viewBlueTurn.visibility = if (isBlue)  View.VISIBLE else View.INVISIBLE
        viewRedTurn.visibility  = if (!isBlue) View.VISIBLE else View.INVISIBLE

        rvChampions.alpha     = if (isPlayerTurn) ALPHA_ACTIVE else ALPHA_DIMMED
        rvChampions.isEnabled = isPlayerTurn
        btnConfirm.isEnabled  = false
        btnSkip.visibility    = if (!isPlayerTurn) View.VISIBLE else View.GONE

        highlightActivePickSlot(isBlue, phase)
        selectedChampion = null
        championAdapter.clearSelection()
        refreshSuggestions(isPlayerTurn, phase)

        cancelAiRunnable()
        if (!isPlayerTurn) {
            aiRunnable = Runnable {
                pickRandomAvailableChampion()?.let { confirmAction(it) }
            }
            handler.postDelayed(aiRunnable!!, GameConstants.Draft.AI_ACTION_DELAY_MS)
        }
    }

    private fun phaseLabelFor(isBlue: Boolean, phase: PickBanPhase): String = getString(
        when (phase) {
            PickBanPhase.BAN  -> if (isBlue) R.string.pb_phase_blue_banning else R.string.pb_phase_red_banning
            PickBanPhase.PICK -> if (isBlue) R.string.pb_phase_blue_picking else R.string.pb_phase_red_picking
        }
    )

    /**
     * Atualiza a faixa de sugestões contextuais.
     *
     * Mostrada apenas no turno do jogador em fase de PICK — bans não recebem
     * sugestões (a UI já tem o dialog "Composições Perigosas" no início).
     *
     * As sugestões são computadas pelo [PickSuggestionEngine] combinando:
     *  - Champion pool do atleta da role atual (MAIN)
     *  - Comps que estamos montando (COMPOSITION)
     *  - Counters à seleção do oponente (COUNTER)
     *  - Lacunas de tags do nosso time (SYNERGY)
     *  - Picks fortes do meta como fallback (META)
     */
    private fun refreshSuggestions(isPlayerTurn: Boolean, phase: PickBanPhase) {
        if (!isPlayerTurn || phase != PickBanPhase.PICK) {
            llSuggestionsContainer.visibility = View.GONE
            return
        }

        val myPicks       = (if (state.playerIsBlue) state.bluePicks else state.redPicks).map { it.id }
        val opponentPicks = (if (state.playerIsBlue) state.redPicks  else state.bluePicks).map { it.id }
        val allBans       = (state.blueBans + state.redBans).map { it.id }
        val currentPlayer = playerStarters.getOrNull(myPicks.size)

        val suggestions = PickSuggestionEngine.suggest(
            myPicks       = myPicks,
            opponentPicks = opponentPicks,
            bans          = allBans,
            currentPlayer = currentPlayer
        )

        if (suggestions.isEmpty()) {
            llSuggestionsContainer.visibility = View.GONE
        } else {
            llSuggestionsContainer.visibility = View.VISIBLE
            suggestionsAdapter.submit(suggestions)
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
                ObjectAnimator.ofFloat(btnConfirm, "scaleX", 1f, BTN_PULSE_SCALE, 1f),
                ObjectAnimator.ofFloat(btnConfirm, "scaleY", 1f, BTN_PULSE_SCALE, 1f)
            )
            duration     = BTN_PULSE_DURATION_MS
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun confirmAction(champ: Champion) {
        cancelAiRunnable()
        val (isBlue, phase) = turnOrder[state.currentTurnIndex]
        state.usedChampions.add(champ.id)

        when (phase) {
            PickBanPhase.BAN  -> registerBan(champ, isBlue)
            PickBanPhase.PICK -> registerPick(champ, isBlue)
        }

        updateSynergyBars()
        championAdapter.markUsed(champ.id)
        state.currentTurnIndex++
        selectedChampion = null
        advanceTurn()
    }

    private fun registerBan(champ: Champion, isBlue: Boolean) {
        val list  = if (isBlue) state.blueBans else state.redBans
        val slots = if (isBlue) blueBanSlots else redBanSlots
        list.add(champ)
        slots.getOrNull(list.size - 1)?.let {
            loadChampionImage(it, champ, grayscale = true)
            animateBanSlot(it)
        }
    }

    private fun registerPick(champ: Champion, isBlue: Boolean) {
        val list  = if (isBlue) state.bluePicks else state.redPicks
        val slots = if (isBlue) bluePickSlots else redPickSlots
        list.add(champ)
        slots.getOrNull(list.size - 1)?.apply {
            setChampion(champ)
            setActive(false)
        }
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
        view.animate().alpha(1f).setDuration(BAN_FADE_DURATION_MS).start()
    }

    private fun loadChampionImage(view: ImageView, champ: Champion, grayscale: Boolean = false) {
        Glide.with(this)
            .load(champ.imageUrl)
            .transition(DrawableTransitionOptions.withCrossFade(GameConstants.Draft.IMAGE_TRANSITION_MS))
            .apply(RequestOptions().centerCrop()
                .placeholder(R.color.champion_slot_bg)
                .error(R.color.champion_slot_bg))
            .into(view)

        view.colorFilter = if (grayscale) {
            ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        } else null
    }

    // ── Sugestão de bans ────────────────────────────────────────────────

    private fun showBanSuggestions() {
        val suggestions = CompositionRepository.suggestBans()
        if (suggestions.isEmpty()) return

        val top = suggestions.take(GameConstants.Draft.BAN_SUGGESTIONS_TOP)
        val msg = top.joinToString("\n") { (champ, reason) ->
            getString(R.string.ban_suggestion_item, champ, reason)
        }

        stylizedDialog(this)
            .setTitle(R.string.dialog_ban_suggestions_title)
            .setMessage(getString(R.string.dialog_ban_suggestions_message, msg))
            .setPositiveButton(R.string.btn_understood, null)
            .show()
    }

    // ── Sinergia em tempo real ──────────────────────────────────────────

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

        applySynergyToBar(pbBlueSynergy, tvBlueCompLabel, tvBlueCompValue,
            blueResult.totalBonus, blueResult.detectedComps, state.bluePicks.size)
        applySynergyToBar(pbRedSynergy, tvRedCompLabel, tvRedCompValue,
            redResult.totalBonus, redResult.detectedComps, state.redPicks.size)
    }

    private fun applySynergyToBar(
        bar: ProgressBar, tvLabel: TextView, tvValue: TextView,
        totalBonus: Int, comps: List<CompositionRepository.DetectedComp>, picksCount: Int
    ) {
        val target = (totalBonus * GameConstants.Synergy.SYNERGY_BAR_SCALE).toInt().coerceIn(0, 100)
        ValueAnimator.ofInt(bar.progress, target).apply {
            duration = SYNERGY_BAR_ANIM_MS
            addUpdateListener { bar.progress = it.animatedValue as Int }
            start()
        }
        tvValue.text = if (totalBonus >= 0) getString(R.string.synergy_value_positive, totalBonus)
                       else totalBonus.toString()

        tvLabel.text = synergyLabelFor(picksCount, comps, totalBonus)
    }

    private fun synergyLabelFor(
        picksCount: Int,
        comps: List<CompositionRepository.DetectedComp>,
        totalBonus: Int
    ): String = when {
        picksCount == 0 -> getString(R.string.synergy_waiting)
        comps.isEmpty() -> getString(when {
            totalBonus < 0 -> R.string.synergy_unbalanced
            totalBonus < SYNERGY_THRESHOLD_WEAK    -> R.string.synergy_weak
            totalBonus < SYNERGY_THRESHOLD_PARTIAL -> R.string.synergy_partial
            else -> R.string.synergy_strong
        })
        comps.size == 1 -> getString(R.string.synergy_single_comp,
            comps[0].composition.name, comps[0].percent)
        else -> getString(R.string.synergy_hybrid_comps,
            comps[0].composition.name, comps[0].percent,
            comps[1].composition.name, comps[1].percent)
    }

    // ── Saída ───────────────────────────────────────────────────────────

    private fun completeMissingWithAI() {
        cancelAiRunnable()
        val pool = ChampionRepository.getAll()
            .filter { it.id !in state.usedChampions }
            .toMutableList()

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
            .setTitle(R.string.dialog_exit_pickban_title)
            .setMessage(R.string.dialog_exit_pickban_message)
            .setPositiveButton(R.string.btn_simulate_until_end) { _, _ -> completeMissingWithAI() }
            .setNeutralButton(R.string.btn_cancel_match) { _, _ ->
                setResult(RESULT_CANCELED)
                cancelAiRunnable()
                finish()
            }
            .setNegativeButton(R.string.btn_continue_pickban, null)
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
            putStringArrayListExtra(RESULT_BLUE_PICKS, ArrayList(state.bluePicks.map { it.id }))
            putStringArrayListExtra(RESULT_RED_PICKS,  ArrayList(state.redPicks.map  { it.id }))
            putStringArrayListExtra(RESULT_BLUE_BANS,  ArrayList(state.blueBans.map  { it.id }))
            putStringArrayListExtra(RESULT_RED_BANS,   ArrayList(state.redBans.map   { it.id }))
            putExtra(EXTRA_MATCH_ID,   matchId)
            putExtra(EXTRA_MAP_NUMBER, mapNumber)
        })
        finish()
    }

    companion object {
        const val REQUEST_PICK_BAN = 1001

        // Intent extras
        const val EXTRA_PLAYER_TEAM_ID   = "player_team_id"
        const val EXTRA_OPPONENT_TEAM_ID = "opponent_team_id"
        const val EXTRA_MATCH_ID         = "match_id"
        const val EXTRA_MAP_NUMBER       = "map_number"

        // Result extras
        const val RESULT_BLUE_PICKS = "blue_picks"
        const val RESULT_RED_PICKS  = "red_picks"
        const val RESULT_BLUE_BANS  = "blue_bans"
        const val RESULT_RED_BANS   = "red_bans"

        // UI constants
        private const val ALPHA_ACTIVE = 1f
        private const val ALPHA_DIMMED = 0.4f
        private const val BTN_PULSE_SCALE = 1.05f
        private const val BTN_PULSE_DURATION_MS = 200L
        private const val BAN_FADE_DURATION_MS = 300L
        private const val SYNERGY_BAR_ANIM_MS = 400L
        private const val SYNERGY_THRESHOLD_WEAK = 4
        private const val SYNERGY_THRESHOLD_PARTIAL = 8

        private const val ROLE_ALL = "ALL"

        /** Roles para os chips de filtro: par (chave interna usada pelo adapter, label R.string). */
        private val ROLE_FILTERS: List<Pair<String, Int>> = listOf(
            ROLE_ALL to R.string.filter_all,
            "TOP"    to R.string.filter_top,
            "JNG"    to R.string.filter_jng,
            "MID"    to R.string.filter_mid,
            "ADC"    to R.string.filter_adc,
            "SUP"    to R.string.filter_sup
        )

        /** Tags filtráveis no draft, mapeadas para R.string. Adicionar uma nova tag é OCP-friendly. */
        private val DRAFT_TAGS_RESOURCE: List<Pair<ChampionTag, Int>> = listOf(
            ChampionTag.TANK            to R.string.tag_tank,
            ChampionTag.ENGAGE          to R.string.tag_engage,
            ChampionTag.CROWD_CONTROL   to R.string.tag_cc,
            ChampionTag.MAGIC_DAMAGE    to R.string.tag_ap,
            ChampionTag.PHYSICAL_DAMAGE to R.string.tag_ad,
            ChampionTag.ANTI_TANK       to R.string.tag_anti_tank,
            ChampionTag.HEAL            to R.string.tag_heal,
            ChampionTag.SHIELD          to R.string.tag_shield,
            ChampionTag.ASSASSIN        to R.string.tag_assassin,
            ChampionTag.POKE            to R.string.tag_poke,
            ChampionTag.SPLIT_PUSH      to R.string.tag_split,
            ChampionTag.HYPERCARRY      to R.string.tag_hypercarry,
            ChampionTag.GLOBAL_ULT      to R.string.tag_global_ult,
            ChampionTag.KNOCK_UP        to R.string.tag_knockup,
            ChampionTag.SUSTAIN         to R.string.tag_sustain,
            ChampionTag.INVISIBLE       to R.string.tag_invisible,
            ChampionTag.TRUE_DAMAGE     to R.string.tag_true_damage
        )
    }
}
