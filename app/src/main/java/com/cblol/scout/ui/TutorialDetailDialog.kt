package com.cblol.scout.ui

import android.app.Activity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.cblol.scout.R
import com.cblol.scout.domain.TutorialContent

/**
 * Dialog de detalhe de um tópico do tutorial.
 *
 * Renderiza o tópico ([TutorialContent.Topic]) com header (emoji + título +
 * resumo) e as N seções abaixo, cada uma com título dourado + body em prosa.
 *
 * As seções variam por tópico, então criamos as views dinamicamente em vez
 * de um layout fixo — o XML traz só o shell (LinearLayout vazio `ll_sections`
 * dentro do ScrollView).
 *
 * **SOLID:**
 *  - **SRP**: renderiza um tópico. Conteúdo vem do [TutorialContent].
 *  - **OCP**: nova seção em qualquer tópico = mais uma `Section`; iteração
 *    aqui não muda.
 */
object TutorialDetailDialog {

    fun show(activity: Activity, topic: TutorialContent.Topic) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_tutorial_detail, null)

        view.findViewById<TextView>(R.id.tv_detail_emoji).text = topic.emoji
        view.findViewById<TextView>(R.id.tv_detail_title).text = topic.title
        view.findViewById<TextView>(R.id.tv_detail_summary).text = topic.summary

        val container = view.findViewById<LinearLayout>(R.id.ll_sections)
        container.removeAllViews()
        topic.sections.forEachIndexed { index, section ->
            container.addView(buildSectionTitle(activity, section.title, isFirst = index == 0))
            container.addView(buildSectionBody(activity, section.body))
        }

        stylizedDialog(activity)
            .setView(view)
            .setPositiveButton(R.string.btn_ok, null)
            .show()
    }

    // ── Builders de view ─────────────────────────────────────────────────

    /**
     * Título de seção em dourado, bold, letterSpacing — segue o padrão visual
     * dos labels de bloco do app (ex: "GERENCIAR", "OUTRAS OPÇÕES" no Hub).
     *
     * O `isFirst` evita marginTop na primeira seção, já que o ScrollView pai
     * já tem padding de 20dp.
     */
    private fun buildSectionTitle(activity: Activity, title: String, isFirst: Boolean): TextView {
        val tv = TextView(activity)
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = if (isFirst) 0 else dp(activity, 18)
        params.bottomMargin = dp(activity, 6)
        tv.layoutParams = params
        tv.text = title.uppercase()
        tv.textSize = 12f
        tv.setTextColor(ContextCompat.getColor(activity, R.color.color_primary))
        tv.letterSpacing = 0.1f
        tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
        return tv
    }

    /** Corpo da seção em prosa, com lineSpacing 1.4 para leitura confortável. */
    private fun buildSectionBody(activity: Activity, body: String): TextView {
        val tv = TextView(activity)
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        tv.layoutParams = params
        tv.text = body
        tv.textSize = 14f
        tv.setTextColor(ContextCompat.getColor(activity, R.color.color_on_surface))
        tv.setLineSpacing(0f, 1.4f)
        return tv
    }

    private fun dp(ctx: android.content.Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density).toInt()
}
