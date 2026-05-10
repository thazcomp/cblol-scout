package com.cblol.scout.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.cblol.scout.databinding.ActivityLoginBinding

/**
 * Tela de login simples (sem backend real).
 * Aceita qualquer combinação não-vazia de usuário e senha.
 * Poderia ser substituída por autenticação Firebase/OAuth futuramente.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener { tryLogin() }
        binding.btnGuest.setOnClickListener {
            // Guest: bypass auth e vai direto pra seleção de time
            goToTeamSelect("Convidado")
        }
    }

    private fun tryLogin() {
        val user = binding.etUser.text.toString().trim()
        val pass = binding.etPass.text.toString().trim()

        when {
            user.isEmpty() -> {
                binding.tilUser.error = "Informe um usuário"
            }
            pass.isEmpty() -> {
                binding.tilUser.error = null
                binding.tilPass.error = "Informe uma senha"
            }
            pass.length < 4 -> {
                binding.tilPass.error = "Senha precisa ter ao menos 4 caracteres"
            }
            else -> {
                binding.tilUser.error = null
                binding.tilPass.error = null
                binding.progress.visibility = View.VISIBLE
                binding.btnLogin.isEnabled = false
                // Simula um delay de auth
                binding.root.postDelayed({
                    Toast.makeText(this, "Bem-vindo, $user!", Toast.LENGTH_SHORT).show()
                    goToTeamSelect(user)
                }, 600)
            }
        }
    }

    private fun goToTeamSelect(username: String) {
        startActivity(
            Intent(this, TeamSelectActivity::class.java)
                .putExtra(TeamSelectActivity.EXTRA_USERNAME, username)
        )
        finish()
    }
}
