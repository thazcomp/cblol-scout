package com.cblol.scout.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.cblol.scout.R
import com.cblol.scout.data.BondTier
import com.cblol.scout.data.GameState
import com.cblol.scout.data.Player
import com.cblol.scout.data.PlayerBond
import com.cblol.scout.domain.usecase.PlayerBondService

/**
 * Dialog que mostra a **química (laços) do elenco**: o laço médio do time + o
 * bônus de força resultante, seguido da lista de todos os pares de jogadores
 * com seu nível, faixa (emoji/label) e dias de convivência.
 *
 * Construído programaticamente (sem XML dedicado) para manter o sistema de
 * laços autocontido — a tela é simples (cabeçalho + lista de linhas) e não
 * justifica novos arquivos de layout.
 *
 * **SOLID:**
 *  - **SRP**: só apresenta os laços; toda a regra vem do [PlayerBondService].
 *  - **OCP**: novas faixas de laço ([BondTier]) se renderizam automaticamente
 *    pela cor/emoji derivados do nível.
 *  - **DIP**: depende do [GameState] + [PlayerBondService], não do repositório.
 */
object PlayerBondsDialog {

    /**
     * Exibe os laços de um elenco inteiro.
     *
     * @param activity Activity hospedeira (tema do dialog)
     * @param state    GameState ativo
     * @param roster   jogadores do elenco (para resolver nomes e calcular médias)
     */
    fun show(activity: Activity, state: GameState, roster: List<Player>) {
        val nameById = roster.associate { it.id to it.nome_jogo }
        val content = buildContent(activity, state, roster, nameById)

        stylizedDialog(activity)
            .setTitle(R.string.bonds_title)
            .setView(wrapInScroll(activity, content))
            .setPositiveButton(R.string.btn_ok, null)
            .show()
    }

    // ── Construção da view ──────────────────────────────────────────────

    private fun buildContent(
        activity: Activity,
        state: GameState,
        roster: List<Player>,
        nameById: Map<String, String>
    ): LinearLayout {
        val ctx = activity
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 20), dp(ctx, 12), dp(ctx, 20), dp(ctx, 8))
        }

        // Cabeçalho: química média + bônus de força.
        val avg = PlayerBondService.averageTeamBond(state, roster)
        val bonus = PlayerBondService.teamStrengthBonus(state, roster)
        val tier = BondTier.from(avg)

        root.addView(TextView(ctx).apply {
            text = ctx.getString(R.string.bonds_team_average, avg, "${tier.emoji} ${tier.label}")
            setTextColor(onSurface(ctx))
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        })
        root.addView(TextView(ctx).apply {
            text = ctx.getString(R.string.bonds_team_bonus, bonus)
            setTextColor(if (bonus >= 0) success(ctx) else danger(ctx))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(ctx, 2), 0, dp(ctx, 12))
        })

        // Pares ordenados por nível (melhores laços primeiro).
        val pairs = collectPairs(state, roster)
        if (pairs.isEmpty()) {
            root.addView(TextView(ctx).apply {
                text = ctx.getString(R.string.bonds_empty)
                setTextColor(onSurfaceVariant(ctx))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            })
            return root
        }

        pairs.forEach { bond ->
            root.addView(buildPairRow(ctx, bond, nameById))
        }
        return root
    }

    /** Coleta os laços entre jogadores PRESENTES no roster, ordenados por nível desc. */
    private fun collectPairs(state: GameState, roster: List<Player>): List<PlayerBond> {
        val ids = roster.map { it.id }.toSet()
        val seen = mutableSetOf<String>()
        val result = mutableListOf<PlayerBond>()
        for (i in roster.indices) {
            for (j in i + 1 until roster.size) {
                val bond = PlayerBondService.bondBetween(state, roster[i].id, roster[j].id)
                    ?: PlayerBond(
                        playerAId = roster[i].id,
                        playerBId = roster[j].id
                    )
                val key = PlayerBond.keyFor(bond.playerAId, bond.playerBId)
                if (key in seen) continue
                // Só pares cujos dois jogadores estão no roster atual.
                if (bond.playerAId in ids && bond.playerBId in ids) {
                    seen += key
                    result += bond
                }
            }
        }
        return result.sortedByDescending { it.level }
    }

    private fun buildPairRow(
        ctx: Activity,
        bond: PlayerBond,
        nameById: Map<String, String>
    ): View {
        val tier = BondTier.from(bond.level)
        val nameA = nameById[bond.playerAId] ?: bond.playerAId
        val nameB = nameById[bond.playerBId] ?: bond.playerBId

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(ctx, 6), 0, dp(ctx, 6))
        }

        // Emoji da faixa.
        row.addView(TextView(ctx).apply {
            text = tier.emoji
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(0, 0, dp(ctx, 10), 0)
        })

        // Nomes + dias juntos (coluna que expande).
        val textCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(TextView(ctx).apply {
            text = ctx.getString(R.string.bonds_pair_format, nameA, nameB)
            setTextColor(onSurface(ctx))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
        })
        textCol.addView(TextView(ctx).apply {
            text = "${tier.label} · ${ctx.getString(R.string.bonds_days_together, bond.daysTogether)}"
            setTextColor(onSurfaceVariant(ctx))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        })
        row.addView(textCol)

        // Nível numérico colorido pela faixa.
        row.addView(TextView(ctx).apply {
            text = (if (bond.level > 0) "+" else "") + bond.level
            setTextColor(colorForTier(ctx, tier))
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        })

        return row
    }

    private fun wrapInScroll(ctx: Activity, content: View): ScrollView =
        ScrollView(ctx).apply { addView(content) }

    // ── Cores ───────────────────────────────────────────────────────────

    private fun colorForTier(ctx: Activity, tier: BondTier): Int = when (tier) {
        BondTier.BONDED   -> success(ctx)
        BondTier.FRIENDLY -> ContextCompat.getColor(ctx, R.color.champion_gold)
        BondTier.NEUTRAL  -> onSurfaceVariant(ctx)
        BondTier.TENSE    -> ContextCompat.getColor(ctx, R.color.state_warning)
        BondTier.TOXIC    -> danger(ctx)
    }

    private fun onSurface(ctx: Activity) = ContextCompat.getColor(ctx, R.color.color_on_surface)
    private fun onSurfaceVariant(ctx: Activity) = ContextCompat.getColor(ctx, R.color.color_on_surface_variant)
    private fun success(ctx: Activity) = ContextCompat.getColor(ctx, R.color.state_success)
    private fun danger(ctx: Activity) = ContextCompat.getColor(ctx, R.color.state_danger)

    private fun dp(ctx: Activity, value: Int): Int =
        (value * ctx.resources.displayMetrics.density).toInt()
}
