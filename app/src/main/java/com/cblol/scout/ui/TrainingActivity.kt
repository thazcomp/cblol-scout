package com.cblol.scout.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.data.TrainingOutcome
import com.cblol.scout.data.TrainingSession
import com.cblol.scout.data.TrainingType
import com.cblol.scout.databinding.ActivityTrainingBinding
import com.cblol.scout.domain.usecase.TrainingService
import com.cblol.scout.domain.usecase.TrainingUiState
import com.cblol.scout.game.GameRepository
import com.cblol.scout.ui.viewmodel.TrainingEvent
import com.cblol.scout.ui.viewmodel.TrainingViewModel
import com.cblol.scout.util.TeamColors
import com.google.android.material.tabs.TabLayout
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Tela de **Treinos**.
 *
 * **MVVM**: observa [TrainingViewModel.state] (orçamento + disponibilidade
 * por tipo + histórico) e [TrainingViewModel.running] para o "lock" da UI
 * durante a operação. O VM cuida da coreografia "avançar dias + aplicar
 * treino + salvar" dentro do [com.cblol.scout.domain.usecase.RunTrainingUseCase].
 */
class TrainingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrainingBinding
    private val vm: TrainingViewModel by viewModel()
    private var currentTab = TAB_OPTIONS
    private var lastState: TrainingUiState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrainingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupTabs()
        binding.recycler.layoutManager = LinearLayoutManager(this)

        vm.state.observe(this) { state ->
            lastState = state
            render(state)
        }
        vm.running.observe(this) { isRunning ->
            // Bloqueia interação durante o treino (era feito direto com alpha
            // e isClickable na Activity antes — agora via LiveData).
            binding.recycler.alpha = if (isRunning) 0.4f else 1f
            binding.recycler.isClickable = !isRunning
        }
        vm.events.observe(this) { ev -> ev.consume()?.let(::handleEvent) }
        vm.refresh()
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    // ── Setup ────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        binding.toolbar.setBackgroundColor(TeamColors.forTeam(GameRepository.current().managerTeamId))
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupTabs() {
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.training_tab_options))
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.training_tab_history))
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                lastState?.let(::render)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    // ── Render ───────────────────────────────────────────────────────────

    private fun render(state: TrainingUiState) {
        binding.tvBudget.text = getString(R.string.training_cost_format, "%,d".format(state.budget))
        when (currentTab) {
            TAB_OPTIONS -> renderOptions(state)
            TAB_HISTORY -> renderHistory(state.history)
        }
    }

    private fun renderOptions(state: TrainingUiState) {
        binding.recycler.visibility = View.VISIBLE
        binding.tvEmpty.visibility  = View.GONE
        binding.recycler.adapter = TrainingOptionsAdapter(
            types = TrainingType.values().toList(),
            availabilityFor = { state.availability[it] ?: TrainingService.Availability.Available },
            onTrainClick = ::confirmTraining
        )
    }

    private fun renderHistory(sessions: List<TrainingSession>) {
        if (sessions.isEmpty()) {
            binding.recycler.visibility = View.GONE
            binding.tvEmpty.visibility  = View.VISIBLE
            binding.tvEmpty.setText(R.string.training_empty_history)
        } else {
            binding.recycler.visibility = View.VISIBLE
            binding.tvEmpty.visibility  = View.GONE
            binding.recycler.adapter = TrainingHistoryAdapter(sessions)
        }
    }

    // ── Ação: iniciar treino ────────────────────────────────────────────

    private fun confirmTraining(type: TrainingType) {
        // Revalidação defensiva: se uma partida do gerente cai na janela do
        // treino, NUNCA treina (senão o avanço de dias auto-simularia o jogo).
        // Mostra um dialog explicativo em vez do de confirmação.
        val gs = GameRepository.current()
        val avail = TrainingService.checkAvailability(gs, type)
        if (avail is TrainingService.Availability.MatchInWindow) {
            val msg = if (avail.daysUntilMatch <= 0) {
                getString(R.string.training_blocked_match_today_msg)
            } else {
                getString(R.string.training_blocked_match_msg, type.durationDays, avail.daysUntilMatch)
            }
            stylizedDialog(this)
                .setTitle(R.string.training_blocked_match_title)
                .setMessage(msg)
                .setPositiveButton(R.string.btn_ok, null)
                .show()
            return
        }

        stylizedDialog(this)
            .setTitle(R.string.training_confirm_title)
            .setMessage(getString(R.string.training_confirm_msg,
                "${type.emoji} ${type.label}",
                type.durationDays,
                "%,d".format(type.cost)))
            .setPositiveButton(R.string.btn_yes) { _, _ -> vm.startTraining(type) }
            .setNegativeButton(R.string.btn_no, null)
            .show()
    }

    private fun handleEvent(event: TrainingEvent) {
        when (event) {
            is TrainingEvent.TrainingDone -> showResultDialog(event.session)
        }
    }

    private fun showResultDialog(session: TrainingSession) {
        val title = getString(R.string.training_result_title,
            session.outcome.emoji, session.outcome.label)
        stylizedDialog(this)
            .setTitle(title)
            .setMessage(session.summary)
            .setPositiveButton(R.string.btn_ok, null)
            .show()
    }

    companion object {
        private const val TAB_OPTIONS = 0
        private const val TAB_HISTORY = 1
    }

    // ── Adapters ─────────────────────────────────────────────────────────

    private class TrainingOptionsAdapter(
        private val types: List<TrainingType>,
        private val availabilityFor: (TrainingType) -> TrainingService.Availability,
        private val onTrainClick: (TrainingType) -> Unit
    ) : RecyclerView.Adapter<TrainingOptionsAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvIcon: TextView   = v.findViewById(R.id.tv_training_icon)
            val tvName: TextView   = v.findViewById(R.id.tv_training_name)
            val tvCost: TextView   = v.findViewById(R.id.tv_training_cost)
            val tvDesc: TextView   = v.findViewById(R.id.tv_training_desc)
            val tvMeta: TextView   = v.findViewById(R.id.tv_training_meta)
            val tvStatus: TextView = v.findViewById(R.id.tv_training_status)
            val btnTrain: com.google.android.material.button.MaterialButton =
                v.findViewById(R.id.btn_train)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_training, parent, false)
        )

        override fun getItemCount() = types.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val type = types[position]
            val ctx  = h.itemView.context
            h.tvIcon.text = type.emoji
            h.tvName.text = type.label
            h.tvCost.text = ctx.getString(R.string.training_cost_format, "%,d".format(type.cost))
            h.tvDesc.text = type.description
            h.tvMeta.text = ctx.getString(R.string.training_meta_format,
                type.durationDays, type.cooldownDays)

            when (val avail = availabilityFor(type)) {
                TrainingService.Availability.Available -> {
                    h.tvStatus.visibility = View.GONE
                    h.btnTrain.isEnabled = true
                    h.btnTrain.alpha = 1f
                }
                is TrainingService.Availability.OnCooldown -> {
                    h.tvStatus.visibility = View.VISIBLE
                    h.tvStatus.text = ctx.getString(R.string.training_status_cooldown,
                        avail.daysRemaining)
                    h.btnTrain.isEnabled = false
                    h.btnTrain.alpha = 0.4f
                }
                is TrainingService.Availability.InsufficientFunds -> {
                    h.tvStatus.visibility = View.VISIBLE
                    h.tvStatus.text = ctx.getString(R.string.training_status_no_funds)
                    h.btnTrain.isEnabled = false
                    h.btnTrain.alpha = 0.4f
                }
                is TrainingService.Availability.MatchInWindow -> {
                    // Há partida do gerente na janela do treino — bloqueia para
                    // não auto-simular o jogo (o jogador perderia o pick & ban).
                    h.tvStatus.visibility = View.VISIBLE
                    h.tvStatus.text = if (avail.daysUntilMatch <= 0) {
                        ctx.getString(R.string.training_status_match_today)
                    } else {
                        ctx.getString(R.string.training_status_match, avail.daysUntilMatch)
                    }
                    h.btnTrain.isEnabled = false
                    h.btnTrain.alpha = 0.4f
                }
            }

            h.btnTrain.setOnClickListener { onTrainClick(type) }
        }
    }

    private class TrainingHistoryAdapter(
        private val sessions: List<TrainingSession>
    ) : RecyclerView.Adapter<TrainingHistoryAdapter.VH>() {

        private val displayFormat = DateTimeFormatter.ofPattern("dd/MM")

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val viewBar: View       = v.findViewById(R.id.view_outcome_bar)
            val tvIcon: TextView    = v.findViewById(R.id.tv_hist_icon)
            val tvType: TextView    = v.findViewById(R.id.tv_hist_type)
            val tvOutcome: TextView = v.findViewById(R.id.tv_hist_outcome)
            val tvDate: TextView    = v.findViewById(R.id.tv_hist_date)
            val tvSummary: TextView = v.findViewById(R.id.tv_hist_summary)
            val tvCost: TextView    = v.findViewById(R.id.tv_hist_cost)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_training_history, parent, false)
        )

        override fun getItemCount() = sessions.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val s   = sessions[position]
            val ctx = h.itemView.context

            h.tvIcon.text = s.type.emoji
            h.tvType.text = s.type.label
            h.tvOutcome.text = "${s.outcome.emoji} ${s.outcome.label}"
            h.tvSummary.text = s.summary

            val color = outcomeColor(ctx, s.outcome)
            h.viewBar.setBackgroundColor(color)
            h.tvOutcome.setTextColor(color)

            h.tvDate.text = runCatching {
                LocalDate.parse(s.date).format(displayFormat)
            }.getOrDefault(s.date)

            h.tvCost.text = ctx.getString(R.string.training_hist_cost_format,
                "%,d".format(s.cost))
        }

        private fun outcomeColor(ctx: android.content.Context, o: TrainingOutcome): Int = when (o) {
            TrainingOutcome.GREAT    -> ContextCompat.getColor(ctx, R.color.state_success)
            TrainingOutcome.GOOD     -> ContextCompat.getColor(ctx, R.color.state_success)
            TrainingOutcome.NEUTRAL  -> ContextCompat.getColor(ctx, R.color.champion_gold)
            TrainingOutcome.BAD      -> ContextCompat.getColor(ctx, R.color.state_warning)
            TrainingOutcome.DISASTER -> ContextCompat.getColor(ctx, R.color.state_danger)
        }
    }
}
