package com.cblol.scout.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.data.Division
import com.cblol.scout.data.Player
import com.cblol.scout.data.SnapshotData
import com.cblol.scout.data.Team
import com.cblol.scout.databinding.ActivityTeamSelectBinding
import com.cblol.scout.domain.GameConstants
import com.cblol.scout.game.GameRepository
import com.cblol.scout.ui.viewmodel.TeamSelectViewModel
import com.cblol.scout.util.JsonLoader
import com.cblol.scout.util.SecondDivisionTeamsGenerator
import com.cblol.scout.util.TeamColors
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Seleção inicial de time (entry point após login).
 *
 * Duas abas:
 *  - **1ª DIVISÃO (CBLOL)**: 8 times do snapshot oficial, com cores e marcas
 *    reais. Orçamento e patrocínio do tier (S/A/B).
 *  - **2ª DIVISÃO (CD)**: 8 times procedurais gerados na hora pelo
 *    [SecondDivisionTeamsGenerator]. Orçamento e patrocínio reduzidos
 *    ([GameConstants.Economy.STARTING_BUDGET_SECOND_DIV]). O **seed** da
 *    geração é fixado no `onCreate` para que a tela mostre os MESMOS times
 *    durante toda a sessão de seleção, e é repassado ao
 *    [TeamSelectViewModel.startCareer] para que a carreira inicie exatamente
 *    com os times exibidos.
 *
 * SOLID:
 * - **SRP**: cada aba tem seu próprio fluxo de carregamento e adapter; o cálculo
 *   de orçamento/patrocínio é centralizado em métodos privados.
 * - **OCP**: novas divisões (ex: amadora, internacional) entram como nova aba
 *   sem mexer nas existentes.
 * - **DIP**: depende de [TeamSelectViewModel] e dos geradores; sem lógica de
 *   persistência aqui.
 */
class TeamSelectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeamSelectBinding
    private val vm: TeamSelectViewModel by viewModel()

    /**
     * Cache do snapshot da 1ª divisão, populado em background no `onCreate` e
     * reutilizado quando o usuário troca de aba. Nullable enquanto carrega.
     */
    private var snapshot: SnapshotData? = null

    /**
     * Cache dos times procedurais da 2ª divisão + roster, usado para popular
     * a aba e para passar o mesmo seed à criação da carreira. Lazy: só gera
     * quando o usuário entra na aba 2ª divisão pela primeira vez. Lazy demais
     * (no clique do time) seria pior — geraríamos uma lista para mostrar e
     * outra para criar.
     */
    private var secondDivisionCache: SecondDivisionTeamsGenerator.Generated? = null

    /**
     * Seed sorteado UMA VEZ no `onCreate` e reutilizado em toda a tela: tanto
     * para gerar os times mostrados na aba quanto para iniciar a carreira. Sem
     * isso, o time clicado pelo usuário (gerado com seed A) poderia não existir
     * na carreira criada (com seed B).
     */
    private val secondDivisionSeed: Long = System.currentTimeMillis()

    private var currentTab: Int = TAB_FIRST

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeamSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val username = intent.getStringExtra(EXTRA_USERNAME) ?: DEFAULT_USERNAME
        binding.tvGreeting.text = getString(R.string.team_select_greeting, username)

        setupTabs()
        observeViewModel()
        binding.recyclerTeams.layoutManager = GridLayoutManager(this, GRID_COLUMNS)

        // Pré-carrega o snapshot da 1ª divisão em background.
        CoroutineScope(Dispatchers.Main).launch {
            val data = withContext(Dispatchers.IO) { JsonLoader.loadSnapshot(applicationContext) }
            snapshot = data
            if (currentTab == TAB_FIRST) renderCurrentTab(username)
        }

        vm.checkSave()
    }

    // ── Tabs ─────────────────────────────────────────────────────────────

    private fun setupTabs() {
        binding.tabsDivision.addTab(
            binding.tabsDivision.newTab().setText(R.string.team_select_tab_first)
        )
        binding.tabsDivision.addTab(
            binding.tabsDivision.newTab().setText(R.string.team_select_tab_second)
        )
        binding.tabsDivision.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                val username = intent.getStringExtra(EXTRA_USERNAME) ?: DEFAULT_USERNAME
                renderCurrentTab(username)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    /**
     * Carrega/renderiza a aba ativa. Mostra mensagem temporária se o snapshot
     * da 1ª divisão ainda estiver carregando; a 2ª divisão é gerada
     * sincronicamente (in-memory, baratíssimo).
     */
    private fun renderCurrentTab(username: String) {
        when (currentTab) {
            TAB_FIRST -> {
                val data = snapshot ?: return // ainda carregando, render virá quando carregar
                binding.tvSubtitle.text = getString(
                    R.string.team_select_subtitle,
                    data.meta.split.removePrefix("2026 ")
                )
                binding.recyclerTeams.adapter = FirstDivisionAdapter(data) {
                    showStartDialog(username, it, Division.FIRST)
                }
            }
            TAB_SECOND -> {
                val gen = secondDivisionCache
                    ?: SecondDivisionTeamsGenerator.generate(secondDivisionSeed)
                        .also { secondDivisionCache = it }
                binding.tvSubtitle.text = getString(R.string.team_select_subtitle_second)
                binding.recyclerTeams.adapter = SecondDivisionAdapter(gen) {
                    showStartDialog(username, it, Division.SECOND)
                }
            }
        }
    }

    // ── Observers ────────────────────────────────────────────────────────

    private fun observeViewModel() {
        vm.hasSave.observe(this) { has ->
            if (has) showContinueDialog()
        }
        vm.careerStarted.observe(this) { started ->
            if (started) {
                startActivity(Intent(this, ManagerHubActivity::class.java))
                finish()
            }
        }
    }

    private fun showContinueDialog() {
        val gs = GameRepository.load(applicationContext) ?: return
        stylizedDialog(this)
            .setTitle(R.string.dialog_continue_career)
            .setMessage(getString(R.string.team_select_continue_message, gs.managerTeamId.uppercase()))
            .setPositiveButton(R.string.btn_continue_career) { _, _ ->
                startActivity(Intent(this, ManagerHubActivity::class.java))
                finish()
            }
            .setNegativeButton(R.string.btn_new_career) { _, _ -> vm.clearAndRestart() }
            .setCancelable(false).show()
    }

    /**
     * Diálogo de confirmação ao escolher um time. Mostra economia da divisão
     * relevante (tier S/A/B na 1ª, valores reduzidos na 2ª).
     */
    private fun showStartDialog(defaultName: String, team: Team, division: Division) {
        val (budget, sponsor) = economyFor(team, division)
        val input = EditText(this).apply {
            setText(defaultName); setSelection(text.length)
            hint = getString(R.string.team_select_hint_coach_name)
            setPadding(INPUT_PAD_H, INPUT_PAD_V, INPUT_PAD_H, INPUT_PAD_V)
        }
        val message = when (division) {
            Division.FIRST -> getString(
                R.string.team_select_start_message,
                team.tier_orcamento, "%,d".format(budget), "%,d".format(sponsor)
            )
            Division.SECOND -> getString(
                R.string.team_select_start_message_second,
                "%,d".format(budget), "%,d".format(sponsor)
            )
        }
        stylizedDialog(this)
            .setTitle(getString(R.string.dialog_start_career, team.nome))
            .setMessage(message)
            .setView(input)
            .setPositiveButton(R.string.btn_start) { _, _ ->
                vm.startCareer(
                    input.text.toString().ifBlank { defaultName },
                    team.id,
                    division,
                    secondDivisionSeed
                )
            }
            .setNegativeButton(R.string.btn_cancel, null).show()
    }

    // ── Tier → economia (regra de domínio) ──────────────────────────────

    private fun economyFor(team: Team, division: Division): Pair<Long, Long> = when (division) {
        Division.FIRST -> tierBudget(team.tier_orcamento) to tierSponsor(team.tier_orcamento)
        Division.SECOND ->
            GameConstants.Economy.STARTING_BUDGET_SECOND_DIV to GameConstants.Economy.WEEKLY_SPONSOR_SECOND_DIV
    }

    private fun tierBudget(tier: String): Long = when (tier) {
        "S" -> GameConstants.Economy.STARTING_BUDGET_TIER_S
        "A" -> GameConstants.Economy.STARTING_BUDGET_TIER_A
        else -> GameConstants.Economy.STARTING_BUDGET_TIER_B
    }

    private fun tierSponsor(tier: String): Long = when (tier) {
        "S" -> GameConstants.Economy.WEEKLY_SPONSOR_TIER_S
        "A" -> GameConstants.Economy.WEEKLY_SPONSOR_TIER_A
        else -> GameConstants.Economy.WEEKLY_SPONSOR_TIER_B
    }

    // ── Adapter: 1ª divisão (snapshot) ───────────────────────────────────

    private class FirstDivisionAdapter(
        private val data: SnapshotData,
        private val onClick: (Team) -> Unit
    ) : RecyclerView.Adapter<FirstDivisionAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val card: CardView      = view.findViewById(R.id.card_team)
            val bar: View           = view.findViewById(R.id.view_team_color)
            val tvName: TextView    = view.findViewById(R.id.tv_team_name)
            val tvTier: TextView    = view.findViewById(R.id.tv_team_tier)
            val tvPlayers: TextView = view.findViewById(R.id.tv_team_players)
            val tvAvg: TextView     = view.findViewById(R.id.tv_team_avg)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_team_card, parent, false)
        )

        override fun getItemCount() = data.times.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val team    = data.times[position]
            val ctx     = holder.itemView.context
            val players = data.jogadores.filter { it.time_id == team.id && it.titular }
            val avg     = if (players.isNotEmpty()) players.sumOf { it.overallRating() } / players.size else 0
            holder.bar.setBackgroundColor(TeamColors.forTeam(team.id))
            holder.tvName.text    = team.nome
            holder.tvTier.text    = ctx.getString(R.string.team_select_tier_label, team.tier_orcamento)
            holder.tvPlayers.text = ctx.getString(R.string.team_select_players, players.size)
            holder.tvAvg.text     = ctx.getString(R.string.team_select_overall, avg)
            holder.card.setOnClickListener { onClick(team) }
        }
    }

    // ── Adapter: 2ª divisão (procedural) ─────────────────────────────────

    private class SecondDivisionAdapter(
        private val data: SecondDivisionTeamsGenerator.Generated,
        private val onClick: (Team) -> Unit
    ) : RecyclerView.Adapter<SecondDivisionAdapter.VH>() {

        /** Roster cacheado por time para o cálculo do overall médio. */
        private val rosterByTeam: Map<String, List<Player>> = data.players.groupBy { it.time_id }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val card: CardView      = view.findViewById(R.id.card_team)
            val bar: View           = view.findViewById(R.id.view_team_color)
            val tvName: TextView    = view.findViewById(R.id.tv_team_name)
            val tvTier: TextView    = view.findViewById(R.id.tv_team_tier)
            val tvPlayers: TextView = view.findViewById(R.id.tv_team_players)
            val tvAvg: TextView     = view.findViewById(R.id.tv_team_avg)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_team_card, parent, false)
        )

        override fun getItemCount() = data.teams.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val team    = data.teams[position]
            val ctx     = holder.itemView.context
            val players = rosterByTeam[team.id].orEmpty().filter { it.titular }
            val avg     = if (players.isNotEmpty()) players.sumOf { it.overallRating() } / players.size else 0
            holder.bar.setBackgroundColor(TeamColors.forTeam(team.id))
            holder.tvName.text    = team.nome
            // Substitui o tier por badge "CD" para deixar claro que é 2ª divisão.
            holder.tvTier.text    = ctx.getString(R.string.team_select_tier_label_cd)
            holder.tvPlayers.text = ctx.getString(R.string.team_select_players, players.size)
            holder.tvAvg.text     = ctx.getString(R.string.team_select_overall, avg)
            holder.card.setOnClickListener { onClick(team) }
        }
    }

    companion object {
        const val EXTRA_USERNAME = "extra_username"
        const val EXTRA_TEAM_ID  = "extra_team_id"

        private const val DEFAULT_USERNAME = "Técnico"
        private const val GRID_COLUMNS     = 2
        private const val INPUT_PAD_H      = 48
        private const val INPUT_PAD_V      = 32

        private const val TAB_FIRST  = 0
        private const val TAB_SECOND = 1
    }
}
