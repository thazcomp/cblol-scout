package com.cblol.scout.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R

/**
 * Adapter do [androidx.viewpager2.widget.ViewPager2] usado em [OnboardingActivity].
 *
 * Cada página exibe um trio (ícone-emoji, título, mensagem) lido de
 * [com.cblol.scout.ui.OnboardingActivity.OnboardingPage]. A lista é fornecida
 * pela Activity (que carrega de string-arrays), mantendo este adapter dumb
 * e reutilizável.
 *
 * SOLID:
 * - **SRP**: só renderiza páginas; não decide quantas existem nem o conteúdo.
 * - **OCP**: adicionar uma página é só estender a lista de `pages` na Activity.
 * - **DIP**: depende apenas do contrato `OnboardingPage` (POJO).
 */
class OnboardingAdapter(
    private val pages: List<OnboardingActivity.OnboardingPage>
) : RecyclerView.Adapter<OnboardingAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvIcon: TextView    = view.findViewById(R.id.tv_onboarding_icon)
        val tvTitle: TextView   = view.findViewById(R.id.tv_onboarding_title)
        val tvMessage: TextView = view.findViewById(R.id.tv_onboarding_message)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_onboarding_page, parent, false)
    )

    override fun getItemCount(): Int = pages.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val page = pages[position]
        holder.tvIcon.text    = page.icon
        holder.tvTitle.text   = page.title
        holder.tvMessage.text = page.message
    }
}
