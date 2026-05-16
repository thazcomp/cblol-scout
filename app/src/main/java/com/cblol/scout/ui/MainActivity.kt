package com.cblol.scout.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cblol.scout.R
import com.cblol.scout.data.Player
import com.cblol.scout.data.SnapshotData
import com.cblol.scout.databinding.ActivityMainBinding
import com.cblol.scout.game.GameRepository
import com.cblol.scout.util.JsonLoader
import com.cblol.scout.util.TeamColors
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tela principal de browsing de jogadores. Lista titulares (ou elenco completo
 * se vinculada a um time específico via `EXTRA_TEAM_ID`), com filtros por role
 * e time, busca textual e ordenação.
 *
 * SOLID:
 * - **SRP**: cada setup/filter/sort isolado em método próprio.
 * - **OCP**: roles e modos de ordenação são declarativos ([ROLE_FILTERS]).
 * - **DIP**: depende de [GameRepository] e [JsonLoader]. Strings via R.string,
 *   cores via R.color.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: PlayerAdapter
    private var snapshot: SnapshotData? = null
    private var selectedRole = ROLE_ALL
    private var selectedTeam = ROLE_ALL
    private var sortMode = SortMode.OVERALL
    private var searchQuery = ""

    /** Se != null, a tela está travada num único time (vindo de TeamSelectActivity). */
    private var lockedTeamId: String? = null

    private enum class SortMode { OVERALL, NAME, KDA, CS, SALARY }

    // ── Lifecycle ────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        readIntentExtras()
        setupRecycler()
        setupSearchBar()
        loadData()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    // ── Setup ────────────────────────────────────────────────────────────

    private fun readIntentExtras() {
        lockedTeamId = intent.getStringExtra(TeamSelectActivity.EXTRA_TEAM_ID)
        if (lockedTeamId != null) {
            selectedTeam = lockedTeamId!!
            binding.chipGroupTeam.visibility = View.GONE
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupRecycler() {
        adapter = PlayerAdapter { player -> showPlayerDetail(player) }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupSearchBar() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim() ?: ""
                applyFilters()
            }
        })
    }

    // ── Carregamento de dados ───────────────────────────────────────────

    private fun loadData() {
        lifecycleScope.launch {
            GameRepository.load(applicationContext)
            val data = withContext(Dispatchers.IO) { JsonLoader.loadSnapshot(applicationContext) }
            snapshot = data

            applyLockedTeamHeader(data)
            buildRoleChips()
            if (lockedTeamId == null) buildTeamChips(data)
            applyFilters()
        }
    }

    private fun applyLockedTeamHeader(data: SnapshotData) {
        val id   = lockedTeamId ?: return
        val team = data.times.find { it.id == id } ?: return
        binding.toolbar.title    = team.nome
        binding.toolbar.subtitle = getString(R.string.team_select_tier_label, team.tier_orcamento)
        binding.toolbar.setBackgroundColor(TeamColors.forTeam(team.id))
        binding.toolbar.setTitleTextColor(Color.WHITE)
        binding.toolbar.setSubtitleTextColor(Color.WHITE)
    }

    /** Recarrega aplicando overrides do GameRepository (após uma compra/venda/renegociação). */
    private fun reloadFromGameState() {
        val gs   = GameRepository.load(applicationContext) ?: return
        val snap = GameRepository.snapshot(applicationContext)
        val overridden = snap.jogadores.map { p ->
            val ov = gs.playerOverrides[p.id] ?: return@map p
            p.copy(
                time_id   = ov.newTeamId ?: p.time_id,
                time_nome = ov.newTeamId?.let { id -> snap.times.find { it.id == id }?.nome } ?: p.time_nome,
                titular   = ov.titular ?: p.titular,
                contrato  = p.contrato.copy(
                    salario_mensal_estimado_brl = ov.newSalary ?: p.contrato.salario_mensal_estimado_brl,
                    termino       = ov.newContractEnd ?: p.contrato.termino,
                    fonte_salario = if (ov.newSalary != null) SALARY_SOURCE_RENEGOTIATED
                                    else p.contrato.fonte_salario
                )
            )
        }
        snapshot = snap.copy(jogadores = overridden)
        applyFilters()
    }

    // ── Chips ────────────────────────────────────────────────────────────

    private fun buildRoleChips() {
        binding.chipGroupRole.removeAllViews()
        ROLE_FILTERS.forEach { (roleKey, labelRes) ->
            val chip = Chip(this).apply {
                text        = getString(labelRes)
                isCheckable = true
                isChecked   = roleKey == ROLE_ALL
                chipBackgroundColor = if (roleKey == ROLE_ALL) null else
                    ColorStateList.valueOf(TeamColors.roleColor(roleKey)).withAlpha(CHIP_BG_ALPHA)
                setOnClickListener {
                    selectedRole = roleKey
                    uncheckOthersIn(binding.chipGroupRole, this)
                    isChecked = true
                    applyFilters()
                }
            }
            binding.chipGroupRole.addView(chip)
        }
    }

    private fun buildTeamChips(data: SnapshotData) {
        binding.chipGroupTeam.removeAllViews()
        val allChip = Chip(this).apply {
            text        = getString(R.string.team_chip_all)
            isCheckable = true
            isChecked   = true
            setOnClickListener {
                selectedTeam = ROLE_ALL
                uncheckOthersIn(binding.chipGroupTeam, this)
                isChecked = true
                applyFilters()
            }
        }
        binding.chipGroupTeam.addView(allChip)

        data.times.forEach { team ->
            val chip = Chip(this).apply {
                text        = team.nome
                isCheckable = true
                isChecked   = false
                chipBackgroundColor = ColorStateList.valueOf(TeamColors.forTeam(team.id))
                    .withAlpha(CHIP_BG_TEAM_ALPHA)
                setOnClickListener {
                    selectedTeam = team.id
                    uncheckOthersIn(binding.chipGroupTeam, this)
                    isChecked = true
                    applyFilters()
                }
            }
            binding.chipGroupTeam.addView(chip)
        }
    }

    private fun uncheckOthersIn(group: ChipGroup, selected: Chip) {
        for (i in 0 until group.childCount) {
            (group.getChildAt(i) as? Chip)?.isChecked = false
        }
    }

    // ── Filtros e ordenação ─────────────────────────────────────────────

    private fun applyFilters() {
        val data = snapshot ?: return
        // Time travado mostra titulares E reservas; navegação livre só titulares.
        var list = if (lockedTeamId != null) data.jogadores else data.jogadores.filter { it.titular }

        if (selectedRole != ROLE_ALL) list = list.filter { it.role == selectedRole }
        if (selectedTeam != ROLE_ALL) list = list.filter { it.time_id == selectedTeam }
        if (searchQuery.isNotEmpty()) list = filterByQuery(list, searchQuery)

        list = sortBy(list, sortMode)
        adapter.submitList(list)
        binding.tvPlayerCount.text = getString(R.string.team_select_players, list.size)
    }

    private fun filterByQuery(list: List<Player>, q: String): List<Player> = list.filter {
        it.nome_jogo.contains(q, ignoreCase = true) ||
        it.time_nome.contains(q, ignoreCase = true) ||
        (it.nome_real?.contains(q, ignoreCase = true) == true)
    }

    private fun sortBy(list: List<Player>, mode: SortMode): List<Player> = when (mode) {
        SortMode.OVERALL -> list.sortedByDescending { it.overallRating() }
        SortMode.NAME    -> list.sortedBy { it.nome_jogo }
        SortMode.KDA     -> list.sortedByDescending { it.stats_brutas.kda }
        SortMode.CS      -> list.sortedByDescending { it.stats_brutas.cs_min }
        SortMode.SALARY  -> list.sortedByDescending { it.contrato.salario_mensal_estimado_brl ?: 0 }
    }

    // ── Menu ─────────────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_change_team -> { goToTeamSelect(); return true }
            R.id.action_logout      -> { goToLogin(); return true }
        }
        val newSort = when (item.itemId) {
            R.id.sort_overall -> SortMode.OVERALL
            R.id.sort_name    -> SortMode.NAME
            R.id.sort_kda     -> SortMode.KDA
            R.id.sort_cs      -> SortMode.CS
            R.id.sort_salary  -> SortMode.SALARY
            else              -> return super.onOptionsItemSelected(item)
        }
        sortMode = newSort
        applyFilters()
        return true
    }

    private fun goToTeamSelect() {
        startActivity(Intent(this, TeamSelectActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun showPlayerDetail(player: Player) {
        PlayerDetailDialog.show(this, player) { reloadFromGameState() }
    }

    companion object {
        private const val ROLE_ALL = "ALL"
        private const val SALARY_SOURCE_RENEGOTIATED = "renegociado"

        private const val CHIP_BG_ALPHA       = 40
        private const val CHIP_BG_TEAM_ALPHA  = 50

        /** Filtros de role: chave interna → R.string. Adicionar role é OCP-friendly. */
        private val ROLE_FILTERS: List<Pair<String, Int>> = listOf(
            ROLE_ALL to R.string.filter_all,
            "TOP"    to R.string.filter_top,
            "JNG"    to R.string.filter_jng,
            "MID"    to R.string.filter_mid,
            "ADC"    to R.string.filter_adc,
            "SUP"    to R.string.filter_sup
        )
    }
}
