package com.cblol.scout.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.data.TrainingOutcome
import com.cblol.scout.data.TrainingSession
import com.cblol.scout.data.TrainingType
import com.cblol.scout.databinding.ActivityTrainingBinding
import com.cblol.scout.domain.usecase.TrainingService
import com.cblol.scout.game.GameEngine
import com.cblol.scout.game.GameRepository
import com.cblol.scout.util.TeamColors
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Tela de Treinos.
 *
 * Duas abas:
 *  - **TREINAR**: lista os 6 tipos de treino. Cada card mostra custo, duração,
 *    cooldown e botão TREINAR (desabilitado se em cooldown ou sem orçamento).
 *  - **HISTÓRICO**: lista as últimas sessões realizadas, com outcome colorido
 *    e narrativa do resultado.
 *
 * **Fluxo de execução de treino:**
 *  1. Usuário toca TREINAR num card
 *  2. Dialog de confirmação mostra duração + custo
 *  3. Avança o calendário (`GameEngine.advanceDays`) para que partidas pendentes
 *     e pagamentos rolem normalmente nos dias do treino
 *  4. Roda `TrainingService.runTraining` que aplica efeitos no roster
 *  5. Mostra dialog com o resultado sorteado (outcome + narrativa)
 *  6. Salva o save e re-renderiza a lista
 *
 * **SOLID:**
 *  - **SRP**: Activity orquestra; o adapter cuida do bind; o Service aplica regras.
 *  - **OCP**: novos tipos = adicionar no enum, sem mexer aqui.
 *  - **DIP**: depende de `TrainingService`, `GameEngine` e `GameRepository` —
 *    três fontes da verdade bem separadas.
 */
class TrainingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrainingBinding
    private var currentTab = TAB_OPTIONS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrainingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupTabs()
        binding.recycler.layoutManager = LinearLayoutManager(this)
        renderState()
    }

    override fun onResume() {
        super.onResume()
        renderState()
    }

    // ── Setup ────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        val gs = GameRepository.current()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setBackgroundColor(TeamColors.forTeam(gs.managerTeamId))
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupTabs() {
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.training_tab_options))
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.training_tab_history))
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                renderState()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    // ── Render ───────────────────────────────────────────────────────────

    private fun renderState() {
        val gs = GameRepository.current()
        binding.tvBudget.text = getString(R.string.training_cost_format, "%,d".format(gs.budget))

        when (currentTab) {
            TAB_OPTIONS -> renderOptions()
            TAB_HISTORY -> renderHistory(gs.trainingHistory ?: emptyList())
        }
    }

    private fun renderOptions() {
        val gs = GameRepository.current()
        binding.recycler.visibility = View.VISIBLE
        binding.tvEmpty.visibility  = View.GONE
        binding.recycler.adapter = TrainingOptionsAdapter(
            types = TrainingType.values().toList(),
            availabilityFor = { TrainingService.checkAvailability(gs, it) },
            cooldownFor = { TrainingService.daysUntilAvailable(gs, it) },
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
        stylizedDialog(this)
            .setTitle(R.string.training_confirm_title)
            .setMessage(getString(R.string.training_confirm_msg,
                "${type.emoji} ${type.label}",
                type.durationDays,
                "%,d".format(type.cost)))
            .setPositiveButton(R.string.btn_yes) { _, _ -> startTraining(type) }
            .setNegativeButton(R.string.btn_no, null)
            .show()
    }

    /**
     * Executa o treino: avança o calendário pelos dias necessários (rodando
     * partidas/eventos do GameEngine no caminho) e em seguida aplica os
     * efeitos do treino, salvando o save.
     */
    private fun startTraining(type: TrainingType) {
        // Bloqueia UI rapidamente para evitar duplo-clique
        binding.recycler.alpha = 0.4f
        binding.recycler.isClickable = false

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // 1. Avança os dias do treino (GameEngine cuida de partidas + pagamentos)
                if (type.durationDays > 0) {
                    GameEngine.advanceDays(applicationContext, type.durationDays)
                }
                // 2. Aplica efeitos do treino no ROSTER TITULAR (treino só conta para quem joga)
                val gs = GameRepository.current()
                val roster = GameRepository.rosterOf(applicationContext, gs.managerTeamId)
                    .filter { it.titular }
                TrainingService.runTraining(gs, type, roster)
                GameRepository.save(applicationContext)
            }

            binding.recycler.alpha = 1f
            binding.recycler.isClickable = true

            // Pega a sessão mais recente (acabou de ser inserida no topo) e exibe
            val lastSession = GameRepository.current().trainingHistory?.firstOrNull()
            if (lastSession != null) showResultDialog(lastSession)
            renderState()
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

    /** Lista de tipos de treino disponíveis. */
    private class TrainingOptionsAdapter(
        private val types: List<TrainingType>,
        private val availabilityFor: (TrainingType) -> TrainingService.Availability,
        private val cooldownFor: (TrainingType) -> Int,
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
            }

            h.btnTrain.setOnClickListener { onTrainClick(type) }
        }
    }

    /** Lista do histórico de treinos. */
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
