package com.cblol.scout.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.data.Player
import com.cblol.scout.databinding.ActivitySquadBinding
import com.cblol.scout.game.GameRepository
import com.cblol.scout.game.PromoteResult
import com.cblol.scout.game.SquadManager
import com.cblol.scout.ui.viewmodel.SquadViewModel
import com.cblol.scout.util.TeamColors
import com.google.android.material.tabs.TabLayout
import org.koin.androidx.viewmodel.ext.android.viewModel

class SquadActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySquadBinding
    private val vm: SquadViewModel by viewModel()
    private var showStarters = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySquadBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        GameRepository.load(applicationContext)

        val gs   = GameRepository.current()
        val snap = GameRepository.snapshot(applicationContext)
        val team = snap.times.find { it.id == gs.managerTeamId }!!
        binding.toolbar.title = "Elenco · ${team.nome}"
        binding.toolbar.setBackgroundColor(TeamColors.forTeam(team.id))
        binding.toolbar.setTitleTextColor(Color.WHITE)

        binding.recycler.layoutManager = LinearLayoutManager(this)

        binding.tabs.addTab(binding.tabs.newTab().setText("Titulares"))
        binding.tabs.addTab(binding.tabs.newTab().setText("Banco"))
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showStarters = (tab.position == 0)
                renderCurrentTab()
            }
            override fun onTabUnselected(t: TabLayout.Tab) {}
            override fun onTabReselected(t: TabLayout.Tab) {}
        })

        vm.starters.observe(this) { starters ->
            binding.tabs.getTabAt(0)?.text = "Titulares (${starters.size})"
            if (showStarters) renderList(starters, true)
        }

        vm.reserves.observe(this) { reserves ->
            binding.tabs.getTabAt(1)?.text = "Banco (${reserves.size})"
            if (!showStarters) renderList(reserves, false)
        }

        vm.swapResult.observe(this) { ok ->
            if (ok) Toast.makeText(this, "Troca realizada", Toast.LENGTH_SHORT).show()
        }

        vm.promoteResult.observe(this) { result ->
            val msg = when (result) {
                is PromoteResult.Swapped  -> "${result.replaced.nome_jogo} substituído"
                is PromoteResult.Promoted -> "Jogador promovido"
                else -> return@observe
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        vm.load()
    }

    override fun onResume() {
        super.onResume()

        // Validação automática do elenco
        val logs = SquadManager.validateAndFixRoster(applicationContext)
        if (logs.isNotEmpty()) {
            val message = logs.joinToString("\n")
            AlertDialog.Builder(this)
                .setTitle("⚠ Elenco ajustado automaticamente")
                .setMessage(message)
                .setPositiveButton("OK", null).show()
        }

        vm.load()
    }

    private fun renderCurrentTab() {
        if (showStarters)
            vm.starters.value?.let { renderList(it, true) }
        else
            vm.reserves.value?.let { renderList(it, false) }
    }

    private fun renderList(list: List<Player>, isStarter: Boolean) {
        binding.recycler.adapter = SquadAdapter(
            players = list,
            isStarter = isStarter,
            onActionClick = { p ->
                if (isStarter) openSwapDialog(p)
                else vm.promote(p.id)
            },
            onItemClick = { p -> PlayerDetailDialog.show(this, p) { vm.load() } }
        )
        binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.tvEmpty.text =
            if (isStarter) "Nenhum titular cadastrado" else "Nenhum reserva — contrate no Mercado"
    }

    private fun openSwapDialog(starter: Player) {
        val candidates = SquadManager.reservesForRoleOf(applicationContext, starter)
        if (candidates.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Sem substitutos")
                .setMessage("Não há reservas de ${starter.role} no elenco.")
                .setPositiveButton("OK", null).show()
            return
        }
        val items = candidates.map { "${it.nome_jogo} · OVR ${it.overallRating()}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Substituir ${starter.nome_jogo} (${starter.role})")
            .setItems(items) { _, which -> vm.swap(starter.id, candidates[which].id) }
            .setNegativeButton("Cancelar", null).show()
    }

    private class SquadAdapter(
        private val players: List<Player>,
        private val isStarter: Boolean,
        private val onActionClick: (Player) -> Unit,
        private val onItemClick: (Player) -> Unit
    ) : RecyclerView.Adapter<SquadAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val viewBar: View       = v.findViewById(R.id.view_sq_bar)
            val tvRole: TextView    = v.findViewById(R.id.tv_sq_role)
            val tvName: TextView    = v.findViewById(R.id.tv_sq_name)
            val tvInfo: TextView    = v.findViewById(R.id.tv_sq_info)
            val tvOvr: TextView     = v.findViewById(R.id.tv_sq_overall)
            val btnAction: TextView = v.findViewById(R.id.btn_sq_action)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_squad_player, parent, false)
        )

        override fun getItemCount() = players.size

        override fun onBindViewHolder(h: VH, i: Int) {
            val p = players[i]
            h.viewBar.setBackgroundColor(TeamColors.forTeam(p.time_id))
            h.tvRole.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = 4f
                setColor(TeamColors.roleColor(p.role))
            }
            h.tvRole.text = p.role
            h.tvName.text = p.nome_jogo
            val salary = p.contrato.salario_mensal_estimado_brl ?: 0L
            h.tvInfo.text = "KDA ${p.stats_brutas.kda} · CS ${p.stats_brutas.cs_min} · R$ ${"%,d".format(salary)}/mês"
            val ovr = p.overallRating()
            h.tvOvr.text = ovr.toString()
            h.tvOvr.setTextColor(when {
                ovr >= 85 -> Color.parseColor("#C89B3C")
                ovr >= 75 -> Color.parseColor("#0AC8B9")
                ovr >= 65 -> Color.parseColor("#B19CD9")
                else      -> Color.parseColor("#788CA0")
            })
            h.btnAction.text = if (isStarter) "↔ TROCAR" else "↑ TITULAR"
            h.btnAction.setOnClickListener { onActionClick(p) }
            h.itemView.setOnClickListener { onItemClick(p) }
        }
    }
}
