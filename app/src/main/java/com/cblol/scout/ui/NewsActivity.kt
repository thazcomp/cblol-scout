package com.cblol.scout.ui

import android.content.Context
import android.content.Intent
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
import com.cblol.scout.data.NewsCategory
import com.cblol.scout.data.NewsItem
import com.cblol.scout.databinding.ActivityNewsBinding
import com.cblol.scout.domain.usecase.NewsService
import com.cblol.scout.game.GameRepository
import com.cblol.scout.util.TeamColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Tela do **feed de notícias** — mostra as manchetes editorializadas geradas
 * pelo [NewsService] ao longo da carreira (resultados, marcos, transferências,
 * finanças, bastidores).
 *
 * É uma tela só de leitura: lista [NewsService.feed] em ordem cronológica
 * inversa (mais recentes/relevantes primeiro). Cada card mostra a fonte
 * fictícia, a data, a categoria (com cor de destaque) e a manchete + lead.
 *
 * **SOLID:**
 *  - **SRP**: Activity só renderiza o feed; a geração vive no [NewsService].
 *  - **OCP**: novas categorias entram no enum e ganham cor em [accentColorFor]
 *    sem mexer no resto.
 *  - **DIP**: depende de [NewsService] + [GameRepository].
 */
class NewsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewsBinding
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        binding.recycler.layoutManager = LinearLayoutManager(this)
        renderFeed()
    }

    private fun setupToolbar() {
        val gs = GameRepository.current()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setBackgroundColor(TeamColors.forTeam(gs.managerTeamId))
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun renderFeed() {
        val gs = GameRepository.current()
        val items = NewsService.feed(gs)
        if (items.isEmpty()) {
            binding.recycler.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
            return
        }
        binding.recycler.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
        binding.recycler.adapter = NewsAdapter(items, dateFormatter)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    companion object {
        fun intent(context: Context) = Intent(context, NewsActivity::class.java)

        /**
         * Cor de destaque (barra lateral + categoria) por editoria. Reaproveita
         * as cores de estado já existentes na paleta para manter coerência
         * visual com o resto do app.
         */
        fun accentColorFor(category: NewsCategory): Int = when (category) {
            NewsCategory.MATCH       -> R.color.champion_gold
            NewsCategory.STANDINGS   -> R.color.champion_gold
            NewsCategory.PLAYER      -> R.color.state_success
            NewsCategory.ACADEMY     -> R.color.state_success
            NewsCategory.TRANSFER    -> R.color.color_primary
            NewsCategory.FINANCE     -> R.color.state_warning
            NewsCategory.LOCKER_ROOM -> R.color.state_danger
        }
    }

    // ── Adapter ──────────────────────────────────────────────────────────

    private class NewsAdapter(
        private val items: List<NewsItem>,
        private val dateFormatter: DateTimeFormatter
    ) : RecyclerView.Adapter<NewsAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val accent: View          = v.findViewById(R.id.view_news_accent)
            val tvSource: TextView    = v.findViewById(R.id.tv_news_source)
            val tvCategory: TextView  = v.findViewById(R.id.tv_news_category)
            val tvHeadline: TextView  = v.findViewById(R.id.tv_news_headline)
            val tvBody: TextView      = v.findViewById(R.id.tv_news_body)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_news, parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val item = items[position]
            val ctx  = h.itemView.context

            val dateLabel = runCatching {
                LocalDate.parse(item.date).format(dateFormatter)
            }.getOrDefault(item.date)

            h.tvSource.text = "${item.sourceEmoji} ${item.source} · $dateLabel"
            h.tvCategory.text = "${item.category.emoji} ${item.category.label}"
            h.tvHeadline.text = item.headline
            h.tvBody.text = item.body

            val accentColor = ContextCompat.getColor(ctx, accentColorFor(item.category))
            h.accent.setBackgroundColor(accentColor)
            h.tvCategory.setTextColor(accentColor)
        }
    }
}
