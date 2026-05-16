package com.cblol.scout.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.cblol.scout.R
import com.cblol.scout.databinding.ActivityLoginBinding
import com.cblol.scout.game.GameRepository

/**
 * Tela de login simples (sem backend real).
 * Aceita qualquer combinação não-vazia de usuário e senha (>= 4 chars).
 *
 * SOLID:
 * - **SRP**: validação isolada em [validateAndLogin]; navegação em [goToNext].
 * - **DIP**: depende apenas de [GameRepository.hasSave] e do binding; sem lógica
 *   de negócio nem strings hardcoded.
 *
 * Strings em `R.string.login_*` e `R.string.btn_*`.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener { validateAndLogin() }
        binding.btnGuest.setOnClickListener {
            goToNext(getString(R.string.login_guest))
        }
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

    private fun validateAndLogin() {
        val user = binding.etUser.text.toString().trim()
        val pass = binding.etPass.text.toString().trim()

        when {
            user.isEmpty()             -> showUserError(R.string.login_error_user)
            pass.isEmpty()             -> showPassError(R.string.login_error_pass)
            pass.length < MIN_PASS_LEN -> showPassError(R.string.login_error_pass_short)
            else                       -> performLogin(user)
        }
    }

    private fun showUserError(resId: Int) {
        binding.tilUser.error = getString(resId)
    }

    private fun showPassError(resId: Int) {
        binding.tilUser.error = null
        binding.tilPass.error = getString(resId)
    }

    private fun performLogin(user: String) {
        binding.tilUser.error = null
        binding.tilPass.error = null
        binding.progress.visibility = View.VISIBLE
        binding.btnLogin.isEnabled  = false
        // Simula um delay de auth
        binding.root.postDelayed({
            Toast.makeText(this, getString(R.string.login_welcome, user), Toast.LENGTH_SHORT).show()
            goToNext(user)
        }, AUTH_FAKE_DELAY_MS)
    }

    companion object {
        private const val MIN_PASS_LEN = 4
        private const val AUTH_FAKE_DELAY_MS = 600L
    }
}
