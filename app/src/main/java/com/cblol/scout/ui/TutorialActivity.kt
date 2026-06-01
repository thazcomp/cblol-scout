package com.cblol.scout.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.domain.TutorialContent

/**
 * Tela de **tutorial sob demanda** — lista todos os tópicos do
 * [TutorialContent] como cards clicáveis. Tocar abre o [TutorialDetailDialog]
 * com o conteúdo completo do tópico.
 *
 * **Acessível a qualquer momento:**
 *  - Pelo card "📚 Tutorial" no Hub (chip de Outras Opções)
 *  - Pelo botão "📚 Ver Tutorial" no LoginActivity (antes mesmo de criar carreira)
 *
 * Diferente do [OnboardingActivity] (15 slides one-shot na 1ª execução), este
 * tutorial é um catálogo navegável sem ordem obrigatória — o jogador pula
 * direto pro tópico em que tem dúvida.
 *
 * **SOLID:**
 *  - **SRP**: Activity só renderiza lista. Conteúdo vem do [TutorialContent].
 *  - **OCP**: novos tópicos = nova entrada em [TutorialContent.topics];
 *    UI itera sem mudar.
 *  - **DIP**: nenhuma dependência de jogo/persistência. Pode ser aberto antes
 *    do save existir (caminho via Login).
 */
class TutorialActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutorial)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val recycler = findViewById<RecyclerView>(R.id.recycler_topics)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = TopicAdapter(TutorialContent.topics) { topic ->
            TutorialDetailDialog.show(this, topic)
        }
    }

    companion object {
        fun intent(context: Context) = Intent(context, TutorialActivity::class.java)
    }

    // ── Adapter ──────────────────────────────────────────────────────────

    private class TopicAdapter(
        private val items: List<TutorialContent.Topic>,
        private val onClick: (TutorialContent.Topic) -> Unit
    ) : RecyclerView.Adapter<TopicAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvEmoji: TextView   = v.findViewById(R.id.tv_tutorial_emoji)
            val tvTitle: TextView   = v.findViewById(R.id.tv_tutorial_title)
            val tvSummary: TextView = v.findViewById(R.id.tv_tutorial_summary)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_tutorial_topic, parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val topic = items[position]
            h.tvEmoji.text = topic.emoji
            h.tvTitle.text = topic.title
            h.tvSummary.text = topic.summary
            h.itemView.setOnClickListener { onClick(topic) }
        }
    }
}
