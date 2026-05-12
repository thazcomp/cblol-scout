package com.cblol.scout.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.cblol.scout.R
import com.cblol.scout.data.Champion

/**
 * PickSlotView — Slot individual de pick na tela de pick & ban.
 *
 * Estados:
 *  - Vazio + inativo  : fundo escuro sutil, sem contorno
 *  - Vazio + ativo    : pulsa com contorno gold (borda grossa animada)
 *  - Preenchido       : mostra imagem do campeão + nome da role
 */
class PickSlotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    private val ivChampion: ImageView
    private val tvRole: TextView
    private val viewActiveBorder: android.view.View
    private val tvEmpty: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.view_pick_slot, this, true)
        ivChampion   = findViewById(R.id.iv_pick_champion)
        tvRole       = findViewById(R.id.tv_pick_role)
        viewActiveBorder = findViewById(R.id.view_pick_active_border)
        tvEmpty      = findViewById(R.id.tv_pick_empty)

        setEmpty()
    }

    fun setChampion(champ: Champion) {
        tvEmpty.visibility = android.view.View.GONE
        ivChampion.visibility = android.view.View.VISIBLE
        tvRole.text = champ.name
        viewActiveBorder.visibility = android.view.View.INVISIBLE
        stopPulse()

        // Splash art landscape da Data Dragon — enquadrado no slot vertical com centerCrop
        Glide.with(context)
            .load(champ.splashUrl)
            .transition(DrawableTransitionOptions.withCrossFade(200))
            .apply(RequestOptions().centerCrop()
                .placeholder(R.color.champion_slot_bg)
                .error(R.color.champion_slot_filled))
            .into(ivChampion)
    }

    fun setActive(active: Boolean) {
        if (ivChampion.visibility == android.view.View.VISIBLE) return // já preenchido

        if (active) {
            viewActiveBorder.visibility = android.view.View.VISIBLE
            viewActiveBorder.setBackgroundResource(R.drawable.border_pick_slot_active)
            startPulse()
        } else {
            viewActiveBorder.visibility = android.view.View.INVISIBLE
            stopPulse()
        }
    }

    private fun setEmpty() {
        tvEmpty.visibility = android.view.View.VISIBLE
        ivChampion.visibility = android.view.View.INVISIBLE
        viewActiveBorder.visibility = android.view.View.INVISIBLE
    }

    private fun startPulse() {
        viewActiveBorder.animate().cancel()
        viewActiveBorder.alpha = 1f
        val pulse = android.animation.ObjectAnimator.ofFloat(viewActiveBorder, "alpha", 1f, 0.3f, 1f)
        pulse.duration = 900
        pulse.repeatCount = android.animation.ObjectAnimator.INFINITE
        pulse.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        pulse.start()
        viewActiveBorder.tag = pulse
    }

    private fun stopPulse() {
        (viewActiveBorder.tag as? android.animation.ObjectAnimator)?.cancel()
        viewActiveBorder.tag = null
        viewActiveBorder.alpha = 1f
    }
}
