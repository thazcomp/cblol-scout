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
import com.cblol.scout.util.TeamColors
import com.google.android.material.tabs.TabLayout

/**
 * Tela de gerenciamento de elenco. Duas abas:
 *  - **Titulares (5)**: cada linha tem um botão "↔ Trocar" que abre um diálogo
 *    listando todas as reservas da mesma role; tap → swap atômico.
 *  - **Banco**: cada reserva tem um botão "↑ Titular" que automaticamente
 *    rebaixa o titular atual da role e promove o reserva.
 *
 * Tap no card → abre o detalhe completo (PlayerDetailDialog) com as ações
 * de renegociação e venda.
 */
class SquadActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySquadBinding
    private var roster: List<Player> = emptyList()
    private var showStarters = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySquadBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        if (GameRepository.load(applicationContext) == null) {
            finish(); return
        }

        binding.recycler.layoutManager = LinearLayoutManager(this)

        binding.tabs.addTab(binding.tabs.newTab().setText("Titulares"))
        binding.tabs.addTab(binding.tabs.newTab().setText("Banco"))
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showStarters = (tab.position == 0)
                renderList()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        loadRoster()
    }

    override fun onResume() {
        super.onResume()
        loadRoster()
    }

    private fun loadRoster() {
        val gs = GameRepository.current()
        val snap = GameRepository.snapshot(applicationContext)
        val team = snap.times.find { it.id == gs.managerTeamId }!!

        binding.toolbar.title = "Elenco · ${team.nome}"
        binding.toolbar.setBackgroundColor(TeamColors.forTeam(team.id))
        binding.toolbar.setTitleTextColor(Color.WHITE)

        roster = GameRepository.rosterOf(applicationContext, gs.managerTeamId)
        val starters = roster.count { it.titular }
        val reserves = roster.size - starters

        binding.tabs.getTabAt(0)?.text = "Titulares ($starters)"
        binding.tabs.getTabAt(1)?.text = "Banco ($reserves)"

        renderList()
    }

    private fun renderList() {
        val list = if (showStarters)
            roster.filter { it.titular }.sortedBy { roleOrder(it.role) }
        else
            roster.filter { !it.titular }.sortedByDescending { it.overallRating() }

        binding.recycler.adapter = SquadAdapter(
            players = list,
            isStarter = showStarters,
            onActionClick = { p -> if (showStarters) openSwapDialog(p) else promoteFromBench(p) },
            onItemClick = { p -> PlayerDetailDialog.show(this, p) { loadRoster() } }
        )

        binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.tvEmpty.text =
            if (showStarters) "Nenhum titular cadastrado" else "Nenhum reserva — contrate no Mercado"
    }

    /** Diálogo de troca: lista as reservas da mesma role do titular selecionado. */
    private fun openSwapDialog(starter: Player) {
        val candidates = SquadManager.reservesForRoleOf(applicationContext, starter)
        if (candidates.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Sem substitutos")
                .setMessage("Não há reservas de ${starter.role} no elenco.\n\nVá ao Mercado para contratar.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val items = candidates.map { p ->
            "${p.nome_jogo} · OVR ${p.overallRating()} · KDA ${p.stats_brutas.kda}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Substituir ${starter.nome_jogo} (${starter.role})")
            .setItems(items) { _, which ->
                val replacement = candidates[which]
                if (SquadManager.swapStarters(applicationContext, starter.id, replacement.id)) {
                    Toast.makeText(this,
                        "${replacement.nome_jogo} entra no lugar de ${starter.nome_jogo}",
                        Toast.LENGTH_SHORT).show()
                    loadRoster()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Promove direto: rebaixa o titular atual da role automaticamente. */
    private fun promoteFromBench(reserve: Player) {
        when (val r = SquadManager.promoteFromBench(applicationContext, reserve.id)) {
            is PromoteResult.Swapped -> {
                Toast.makeText(this,
                    "${reserve.nome_jogo} entra no lugar de ${r.replaced.nome_jogo}",
                    Toast.LENGTH_LONG).show()
                loadRoster()
            }
            is PromoteResult.Promoted -> {
                Toast.makeText(this, "${reserve.nome_jogo} promovido", Toast.LENGTH_SHORT).show()
                loadRoster()
            }
            else -> Unit
        }
    }

    private fun roleOrder(role: String): Int = when (role) {
        "TOP" -> 1; "JNG" -> 2; "MID" -> 3; "ADC" -> 4; "SUP" -> 5; else -> 6
    }

    // ───── Adapter ─────

    private class SquadAdapter(
        private val players: List<Player>,
        private val isStarter: Boolean,
        private val onActionClick: (Player) -> Unit,
        private val onItemClick: (Player) -> Unit
    ) : RecyclerView.Adapter<SquadAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val viewBar: View      = v.findViewById(R.id.view_sq_bar)
            val tvRole: TextView   = v.findViewById(R.id.tv_sq_role)
            val tvName: TextView   = v.findViewById(R.id.tv_sq_name)
            val tvInfo: TextView   = v.findViewById(R.id.tv_sq_info)
            val tvOvr: TextView    = v.findViewById(R.id.tv_sq_overall)
            val btnAction: TextView = v.findViewById(R.id.btn_sq_action)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_squad_player, parent, false)
        )

        override fun getItemCount() = players.size

        override fun onBindViewHolder(h: VH, i: Int) {
            val p = players[i]
            h.viewBar.setBackgroundColor(TeamColors.forTeam(p.time_id))

            val roleBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 4f
                setColor(TeamColors.roleColor(p.role))
            }
            h.tvRole.background = roleBg
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
