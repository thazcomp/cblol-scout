package com.cblol.scout.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.cblol.scout.R
import com.cblol.scout.databinding.ActivityOnboardingBinding

/**
 * Tela de boas-vindas pré-login.
 *
 * Apresenta em ViewPager2 a origem dos dados, o uso de IA para gerar atributos,
 * a lógica do pick & ban e da simulação, e como funciona a carreira do técnico.
 * O treinador pode navegar com swipe, clicar PRÓXIMO ou PULAR para ir direto
 * ao login.
 *
 * **Quando aparece**: apenas na primeira execução do app. Após terminar
 * (ou pular), grava uma flag em SharedPreferences e nunca mais aparece, indo
 * direto para [LoginActivity]. Para forçar a tela a aparecer de novo, o app
 * precisa ter os dados limpos (Settings → Apps → CBLOL Scout → Limpar dados).
 *
 * SOLID:
 * - **SRP**: a Activity orquestra navegação entre páginas. O conteúdo está
 *   isolado em string-arrays no XML; o adapter [OnboardingAdapter] só renderiza.
 * - **OCP**: novas páginas se adicionam em três `string-array` (titles, messages,
 *   icons) — nada de código muda.
 * - **DIP**: depende apenas de [OnboardingAdapter] e do contrato POJO [OnboardingPage].
 *
 * Strings em `R.string.onboarding_*` e `R.array.onboarding_*`.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var pages: List<OnboardingPage>
    private val indicatorDots = mutableListOf<ImageView>()

    /** POJO com o conteúdo de uma página. Lida do `string-array` no `onCreate`. */
    data class OnboardingPage(val icon: String, val title: String, val message: String)

    // ── Lifecycle ────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Se o usuário já viu o onboarding em execução anterior, vai direto pro login.
        // A flag fica em SharedPreferences — "Limpar dados" no Android restaura.
        if (isOnboardingComplete(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pages = loadPagesFromResources()
        setupViewPager()
        setupIndicator()
        setupNavigationButtons()
    }

    /**
     * Carrega as páginas dos três `string-array` (titles, messages, icons).
     * As três listas precisam ter o mesmo tamanho (validado em build/teste).
     */
    private fun loadPagesFromResources(): List<OnboardingPage> {
        val titles   = resources.getStringArray(R.array.onboarding_titles)
        val messages = resources.getStringArray(R.array.onboarding_messages)
        val icons    = resources.getStringArray(R.array.onboarding_icons)
        val count    = minOf(titles.size, messages.size, icons.size)
        return (0 until count).map { OnboardingPage(icons[it], titles[it], messages[it]) }
    }

    // ── ViewPager2 ──────────────────────────────────────────────────────

    private fun setupViewPager() {
        binding.vpOnboarding.adapter = OnboardingAdapter(pages)
        binding.vpOnboarding.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicator(position)
                updateNextButtonLabel(position)
            }
        })
    }

    // ── Indicador de páginas (dots) ──────────────────────────────────────

    private fun setupIndicator() {
        binding.llPageIndicator.removeAllViews()
        indicatorDots.clear()
        for (i in pages.indices) {
            val dot = ImageView(this).apply {
                setImageResource(
                    if (i == 0) R.drawable.onboarding_dot_active
                    else        R.drawable.onboarding_dot_inactive
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(DOT_MARGIN_PX, 0, DOT_MARGIN_PX, 0) }
            }
            binding.llPageIndicator.addView(dot)
            indicatorDots += dot
        }
    }

    private fun updateIndicator(activeIndex: Int) {
        indicatorDots.forEachIndexed { i, dot ->
            dot.setImageResource(
                if (i == activeIndex) R.drawable.onboarding_dot_active
                else                  R.drawable.onboarding_dot_inactive
            )
        }
    }

    // ── Botões de navegação ──────────────────────────────────────────────

    private fun setupNavigationButtons() {
        binding.btnOnboardingSkip.setOnClickListener { finishOnboarding() }
        binding.btnOnboardingNext.setOnClickListener {
            val current = binding.vpOnboarding.currentItem
            if (current < pages.lastIndex) {
                binding.vpOnboarding.currentItem = current + 1
            } else {
                finishOnboarding()
            }
        }
    }

    /** Na última página o botão muda de "PRÓXIMO" para "COMEÇAR". */
    private fun updateNextButtonLabel(position: Int) {
        binding.btnOnboardingNext.setText(
            if (position == pages.lastIndex) R.string.onboarding_start
            else                             R.string.onboarding_next
        )
    }

    /**
     * Marca o onboarding como concluído e abre o login.
     * Chamado tanto pelo PULAR quanto pelo COMEÇAR (última página).
     */
    private fun finishOnboarding() {
        markOnboardingComplete(this)
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    companion object {
        private const val PREFS_NAME       = "cblol_onboarding_prefs"
        private const val KEY_ONBOARDED    = "onboarding_complete"
        private const val DOT_MARGIN_PX    = 6   // espaçamento entre dots em pixels

        /** Indica se a tela de onboarding já foi exibida pelo menos uma vez. */
        fun isOnboardingComplete(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ONBOARDED, false)

        /** Persiste que o usuário completou (ou pulou) o onboarding. */
        fun markOnboardingComplete(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ONBOARDED, true)
                .apply()
        }
    }
}
