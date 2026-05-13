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
import com.cblol.scout.databinding.ActivityTransferMarketBinding
import com.cblol.scout.game.BuyResult
import com.cblol.scout.game.GameRepository
import com.cblol.scout.ui.viewmodel.TransferMarketViewModel
import com.cblol.scout.util.TeamColors
import org.koin.androidx.viewmodel.ext.android.viewModel

class TransferMarketActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTransferMarketBinding
    private val vm: TransferMarketViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransferMarketBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        GameRepository.load(applicationContext)

        vm.players.observe(this) { players ->
            binding.tvBudget.text = "Orçamento: R$ ${"%,d".format(GameRepository.current().budget)}"
            binding.recycler.layoutManager = LinearLayoutManager(this)
            binding.recycler.adapter = MarketAdapter(players) { confirmBuy(it) }
        }

        vm.buyResult.observe(this) { result ->
            when (result) {
                is BuyResult.Ok    -> Toast.makeText(this, "Jogador contratado!", Toast.LENGTH_SHORT).show()
                is BuyResult.Error -> AlertDialog.Builder(this).setMessage(result.msg)
                    .setPositiveButton("OK", null).show()
            }
        }

        val chips = listOf(
            binding.chipAll to "ALL", binding.chipTop to "TOP", binding.chipJng to "JNG",
            binding.chipMid to "MID", binding.chipAdc to "ADC", binding.chipSup to "SUP"
        )
        chips.forEach { (chip, role) ->
            chip.setOnClickListener {
                chips.forEach { (c, r) -> c.isChecked = (r == role) }
                vm.load(role)
            }
        }
        binding.chipAll.isChecked = true
        vm.load()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun confirmBuy(player: Player) {
        val price  = vm.priceOf(player)
        val budget = GameRepository.current().budget
        AlertDialog.Builder(this)
            .setTitle("Contratar ${player.nome_jogo}?")
            .setMessage(
                "${player.role} · ${player.time_nome}\n" +
                "Overall: ${player.overallRating()}\n" +
                "Salário: R$ ${"%,d".format(player.contrato.salario_mensal_estimado_brl ?: 0L)}/mês\n\n" +
                "💰 Preço: R$ ${"%,d".format(price)}\n" +
                "Orçamento atual: R$ ${"%,d".format(budget)}\n" +
                "Após compra: R$ ${"%,d".format(budget - price)}"
            )
            .setPositiveButton("Contratar") { _, _ -> vm.buy(player.id) }
            .setNegativeButton("Cancelar", null).show()
    }

    private class MarketAdapter(
        private val players: List<Player>,
        private val onClick: (Player) -> Unit
    ) : RecyclerView.Adapter<MarketAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val viewBar: View       = v.findViewById(R.id.view_mk_bar)
            val tvRole: TextView    = v.findViewById(R.id.tv_mk_role)
            val tvName: TextView    = v.findViewById(R.id.tv_mk_name)
            val tvTeam: TextView    = v.findViewById(R.id.tv_mk_team)
            val tvOverall: TextView = v.findViewById(R.id.tv_mk_overall)
            val tvPrice: TextView   = v.findViewById(R.id.tv_mk_price)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_market, parent, false)
        )

        override fun getItemCount() = players.size

        override fun onBindViewHolder(h: VH, i: Int) {
            val p = players[i]
            h.viewBar.setBackgroundColor(TeamColors.forTeam(p.time_id))
            h.tvRole.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = 18f
                setColor(TeamColors.roleColor(p.role))
            }
            h.tvRole.text    = p.role
            h.tvName.text    = p.nome_jogo
            h.tvTeam.text    = p.time_nome
            h.tvOverall.text = p.overallRating().toString()
            h.tvPrice.text   = "R$ ${"%,d".format(com.cblol.scout.game.TransferMarket.marketPriceOf(p))}"
            h.itemView.setOnClickListener { onClick(p) }
        }
    }
}
