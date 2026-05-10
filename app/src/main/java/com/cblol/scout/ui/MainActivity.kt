package com.cblol.scout.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cblol.scout.R
import com.cblol.scout.data.Player
import com.cblol.scout.data.SnapshotData
import com.cblol.scout.databinding.ActivityMainBinding
import com.cblol.scout.game.GameRepository
import com.cblol.scout.game.SellResult
import com.cblol.scout.game.TransferMarket
import com.cblol.scout.util.JsonLoader
import com.cblol.scout.util.TeamColors
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: PlayerAdapter
    private var snapshot: SnapshotData? = null
    private var selectedRole = "ALL"
    private var selectedTeam = "ALL"
    private var sortMode = SortMode.OVERALL
    private var searchQuery = ""

    /** Se != null, a tela está travada num único time (vindo de TeamSelectActivity). */
    private var lockedTeamId: String? = null

    enum class SortMode { OVERALL, NAME, KDA, CS, SALARY }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        lockedTeamId = intent.getStringExtra(TeamSelectActivity.EXTRA_TEAM_ID)
        if (lockedTeamId != null) {
            selectedTeam = lockedTeamId!!
            // Esconde a fileira de chips de time pois já está travado
            binding.chipGroupTeam.visibility = View.GONE
            // Habilita botão de voltar
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }

        adapter = PlayerAdapter { player -> showPlayerDetail(player) }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        setupSearchBar()
        loadData()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish() // volta pra TeamSelectActivity
        return true
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

    private fun loadData() {
        lifecycleScope.launch {
            // Carrega game state pra refletir transferências/overrides
            GameRepository.load(applicationContext)
            val data = withContext(Dispatchers.IO) { JsonLoader.loadSnapshot(applicationContext) }
            snapshot = data

            lockedTeamId?.let { id ->
                val team = data.times.find { it.id == id }
                if (team != null) {
                    binding.toolbar.title = team.nome
                    binding.toolbar.subtitle = "Tier ${team.tier_orcamento} · Elenco"
                    binding.toolbar.setBackgroundColor(TeamColors.forTeam(team.id))
                    binding.toolbar.setTitleTextColor(Color.WHITE)
                    binding.toolbar.setSubtitleTextColor(Color.WHITE)
                }
            }

            buildRoleChips(data)
            if (lockedTeamId == null) buildTeamChips(data)
            applyFilters()
        }
    }

    /** Recarrega aplicando overrides do GameRepository (após uma compra/venda/renegociação). */
    private fun reloadFromGameState() {
        val gs = GameRepository.load(applicationContext) ?: return
        val snap = GameRepository.snapshot(applicationContext)
        // Copia o snapshot mas usando overrides
        val overridden = snap.jogadores.map { p ->
            val ov = gs.playerOverrides[p.id] ?: return@map p
            p.copy(
                time_id = ov.newTeamId ?: p.time_id,
                time_nome = ov.newTeamId?.let { id -> snap.times.find { it.id == id }?.nome } ?: p.time_nome,
                titular = ov.titular ?: p.titular,
                contrato = p.contrato.copy(
                    salario_mensal_estimado_brl = ov.newSalary ?: p.contrato.salario_mensal_estimado_brl,
                    termino = ov.newContractEnd ?: p.contrato.termino,
                    fonte_salario = if (ov.newSalary != null) "renegociado" else p.contrato.fonte_salario
                )
            )
        }
        snapshot = snap.copy(jogadores = overridden)
        applyFilters()
    }

    private fun buildRoleChips(data: SnapshotData) {
        val roles = listOf("ALL", "TOP", "JNG", "MID", "ADC", "SUP")
        binding.chipGroupRole.removeAllViews()
        roles.forEach { role ->
            val chip = Chip(this).apply {
                text = role
                isCheckable = true
                isChecked = role == "ALL"
                chipBackgroundColor = if (role == "ALL") null else {
                    val color = if (role != "ALL") TeamColors.roleColor(role) else Color.GRAY
                    android.content.res.ColorStateList.valueOf(color).withAlpha(40)
                }
                setOnClickListener {
                    selectedRole = role
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
            text = "TODOS"
            isCheckable = true
            isChecked = true
            setOnClickListener {
                selectedTeam = "ALL"
                uncheckOthersIn(binding.chipGroupTeam, this)
                isChecked = true
                applyFilters()
            }
        }
        binding.chipGroupTeam.addView(allChip)

        data.times.forEach { team ->
            val chip = Chip(this).apply {
                text = team.nome
                isCheckable = true
                isChecked = false
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    TeamColors.forTeam(team.id)
                ).withAlpha(50)
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

    private fun applyFilters() {
        val data = snapshot ?: return
        // Quando há um time travado (modo gerência), mostra titulares E reservas dele.
        // Quando navegação livre (sem lock), mostra só titulares.
        var list = if (lockedTeamId != null) data.jogadores else data.jogadores.filter { it.titular }

        if (selectedRole != "ALL") list = list.filter { it.role == selectedRole }
        if (selectedTeam != "ALL") list = list.filter { it.time_id == selectedTeam }
        if (searchQuery.isNotEmpty()) {
            list = list.filter {
                it.nome_jogo.contains(searchQuery, ignoreCase = true) ||
                it.time_nome.contains(searchQuery, ignoreCase = true) ||
                (it.nome_real?.contains(searchQuery, ignoreCase = true) == true)
            }
        }

        list = when (sortMode) {
            SortMode.OVERALL -> list.sortedByDescending { it.overallRating() }
            SortMode.NAME    -> list.sortedBy { it.nome_jogo }
            SortMode.KDA     -> list.sortedByDescending { it.stats_brutas.kda }
            SortMode.CS      -> list.sortedByDescending { it.stats_brutas.cs_min }
            SortMode.SALARY  -> list.sortedByDescending { it.contrato.salario_mensal_estimado_brl ?: 0 }
        }

        adapter.submitList(list)
        binding.tvPlayerCount.text = "${list.size} jogadores"
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_change_team) {
            startActivity(
                Intent(this, TeamSelectActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            finish()
            return true
        }
        if (item.itemId == R.id.action_logout) {
            startActivity(
                Intent(this, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            finish()
            return true
        }
        sortMode = when (item.itemId) {
            R.id.sort_overall -> SortMode.OVERALL
            R.id.sort_name    -> SortMode.NAME
            R.id.sort_kda     -> SortMode.KDA
            R.id.sort_cs      -> SortMode.CS
            R.id.sort_salary  -> SortMode.SALARY
            else              -> return super.onOptionsItemSelected(item)
        }
        applyFilters()
        return true
    }

    private fun showPlayerDetail(player: Player) {
        val dialog = BottomSheetDialog(this, R.style.ThemeOverlay_CBLOLScout_BottomSheet)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_player, null)
        dialog.setContentView(view)

        val s = player.stats_brutas
        val a = player.atributos_derivados
        val teamColor = TeamColors.forTeam(player.time_id)

        view.findViewById<View>(R.id.view_bs_team_bar).setBackgroundColor(teamColor)
        view.findViewById<TextView>(R.id.tv_bs_name).text = player.nome_jogo
        view.findViewById<TextView>(R.id.tv_bs_realname).text =
            player.nome_real ?: "Nome real não disponível"
        view.findViewById<TextView>(R.id.tv_bs_team).text = player.time_nome
        view.findViewById<TextView>(R.id.tv_bs_role).text =
            "${player.role}  ${TeamColors.flagEmoji(player.nacionalidade)}  ${player.idade?.let { "$it anos" } ?: ""}"
        view.findViewById<TextView>(R.id.tv_bs_overall).text = player.overallRating().toString()

        view.findViewById<TextView>(R.id.tv_bs_jogos).text  = s.jogos.toString()
        view.findViewById<TextView>(R.id.tv_bs_kda).text    = s.kda.toString()
        view.findViewById<TextView>(R.id.tv_bs_kp).text     = "${s.kp_pct.toInt()}%"
        view.findViewById<TextView>(R.id.tv_bs_cs).text     = s.cs_min.toString()
        view.findViewById<TextView>(R.id.tv_bs_dmg).text    = "${s.damage_share_pct}%"
        view.findViewById<TextView>(R.id.tv_bs_gd15).text   = s.gd15?.let { if (it >= 0) "+$it" else "$it" } ?: "N/A"
        view.findViewById<TextView>(R.id.tv_bs_xpd15).text  = s.xpd15?.let { if (it >= 0) "+$it" else "$it" } ?: "N/A"
        view.findViewById<TextView>(R.id.tv_bs_vis).text    = s.vision_score_min?.toString() ?: "N/A"

        view.findViewById<ProgressBar>(R.id.pb_bs_lane).progress    = a.lane_phase
        view.findViewById<ProgressBar>(R.id.pb_bs_tf).progress      = a.team_fight
        view.findViewById<ProgressBar>(R.id.pb_bs_cria).progress    = a.criatividade
        view.findViewById<ProgressBar>(R.id.pb_bs_cons).progress    = a.consistencia
        view.findViewById<ProgressBar>(R.id.pb_bs_clutch).progress  = a.clutch
        view.findViewById<TextView>(R.id.tv_bs_lane_val).text    = a.lane_phase.toString()
        view.findViewById<TextView>(R.id.tv_bs_tf_val).text      = a.team_fight.toString()
        view.findViewById<TextView>(R.id.tv_bs_cria_val).text    = a.criatividade.toString()
        view.findViewById<TextView>(R.id.tv_bs_cons_val).text    = a.consistencia.toString()
        view.findViewById<TextView>(R.id.tv_bs_clutch_val).text  = a.clutch.toString()

        val salary = player.contrato.salario_mensal_estimado_brl
        view.findViewById<TextView>(R.id.tv_bs_salary).text =
            if (salary != null) "R$ ${"%,d".format(salary)}/mês (${player.contrato.fonte_salario})"
            else "Salário não disponível"

        // Ações de gerência (só aparecem se houver carreira ativa e for jogador do meu time)
        val gs = GameRepository.load(applicationContext)
        val actions = view.findViewById<LinearLayout>(R.id.layout_bs_actions)
        if (gs != null && player.time_id == gs.managerTeamId) {
            actions.visibility = View.VISIBLE
            view.findViewById<Button>(R.id.btn_bs_toggle_starter).apply {
                text = if (player.titular) "Mover para reserva" else "Promover a titular"
                setOnClickListener {
                    TransferMarket.toggleStarter(applicationContext, player.id)
                    Toast.makeText(this@MainActivity, "Status atualizado", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    reloadFromGameState()
                }
            }
            view.findViewById<Button>(R.id.btn_bs_renegotiate).setOnClickListener {
                showRenegotiateDialog(player); dialog.dismiss()
            }
            view.findViewById<Button>(R.id.btn_bs_sell).setOnClickListener {
                confirmSell(player); dialog.dismiss()
            }
        } else {
            actions.visibility = View.GONE
        }

        dialog.show()
    }

    private fun confirmSell(player: Player) {
        val price = TransferMarket.marketPriceOf(player)
        AlertDialog.Builder(this)
            .setTitle("Vender ${player.nome_jogo}?")
            .setMessage("Você receberá R$ ${"%,d".format(price)}.\nO jogador será transferido para outra organização do CBLOL.")
            .setPositiveButton("Vender") { _, _ ->
                when (val r = TransferMarket.sellPlayer(applicationContext, player.id)) {
                    is SellResult.Ok -> {
                        Toast.makeText(this,
                            "${player.nome_jogo} → ${r.toTeam} (R$ ${"%,d".format(r.price)})",
                            Toast.LENGTH_LONG).show()
                        reloadFromGameState()
                    }
                    is SellResult.Error -> Toast.makeText(this, r.msg, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showRenegotiateDialog(player: Player) {
        val current = player.contrato.salario_mensal_estimado_brl ?: 0L
        val view = layoutInflater.inflate(R.layout.dialog_renegotiate, null)
        val etSalary = view.findViewById<android.widget.EditText>(R.id.et_new_salary)
        val etDate   = view.findViewById<android.widget.EditText>(R.id.et_new_date)
        etSalary.setText(current.toString())
        etDate.setText("2027-11-30")

        AlertDialog.Builder(this)
            .setTitle("Renegociar ${player.nome_jogo}")
            .setView(view)
            .setPositiveButton("Propor") { _, _ ->
                val newSal = etSalary.text.toString().toLongOrNull() ?: current
                val newEnd = etDate.text.toString().ifBlank { "2027-11-30" }
                val ok = TransferMarket.renegotiateContract(
                    applicationContext, player.id, newSal, newEnd
                )
                Toast.makeText(this,
                    if (ok) "Contrato renovado!" else "${player.nome_jogo} recusou a oferta.",
                    Toast.LENGTH_LONG).show()
                reloadFromGameState()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
