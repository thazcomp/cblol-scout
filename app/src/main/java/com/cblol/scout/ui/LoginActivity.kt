package com.cblol.scout.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cblol.scout.R
import com.cblol.scout.databinding.ActivityLoginBinding
import com.cblol.scout.game.GameRepository

/**
 * Tela de entrada (sem backend nem login/senha).
 *
 * Um único botão leva o jogador adiante: se já há uma carreira salva, vai
 * direto ao Hub; caso contrário, abre a seleção de time. Não há autenticação
 * — o jogo é single-player local.
 *
 * SOLID:
 * - **SRP**: a Activity só navega ([goToNext]); sem validação nem regra de negócio.
 * - **DIP**: depende apenas de [GameRepository.hasSave] e do binding; strings
 *   em `R.string.*`.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGuest.setOnClickListener {
            goToNext(getString(R.string.login_guest))
        }
        binding.tvLegalDisclaimer.setOnClickListener { showLegalDisclaimer() }
    }

    /**
     * Abre o diálogo com o aviso legal completo da Riot Games. Acessível
     * permanentemente do Login (em todas as execuções, não só no
     * onboarding one-shot) por conformidade com as diretrizes da Riot Games
     * para projetos de fã que usam IP do LoL.
     */
    private fun showLegalDisclaimer() {
        stylizedDialog(this)
            .setTitle(R.string.legal_disclaimer_dialog_title)
            .setMessage(R.string.legal_disclaimer_dialog_body)
            .setPositiveButton(R.string.btn_ok, null)
            .show()
    }

    /** Se já existe save vai direto pro Hub; caso contrário, abre seleção de time. */
    private fun goToNext(username: String) {
        val intent = if (GameRepository.hasSave(applicationContext)) {
            Intent(this, ManagerHubActivity::class.java)
        } else {
            Intent(this, TeamSelectActivity::class.java)
                .putExtra(TeamSelectActivity.EXTRA_USERNAME, username)
        }
        startActivity(intent)
        finish()
    }
}
