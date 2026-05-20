package com.cblol.scout.domain.usecase

import com.cblol.scout.data.ContractClauses
import com.cblol.scout.data.GameState
import com.cblol.scout.data.Player
import com.cblol.scout.data.PlayerOverride
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Serviço de contratos.
 *
 * Centraliza:
 *  - **Avaliação de propostas** de renovação: o jogador aceita/rejeita com
 *    base em salário, duração, bônus, moral atual, idade e overall.
 *  - **Cálculo de multa rescisória** padrão quando o contrato é criado.
 *  - **Multas por encerramento antecipado** quando o time quer dispensar um
 *    jogador antes do fim do contrato (descontado do orçamento).
 *  - **Status do contrato** (dias restantes, expirando, expirado).
 *
 * **Filosofia**: usar um único ponto de verdade para todas as decisões
 * relacionadas a contrato evita inconsistências entre dialogs, mercado e
 * sistema de moral.
 *
 * **SOLID**:
 *  - **SRP**: este service apenas cuida de contratos; venda/compra continuam
 *    no [com.cblol.scout.game.TransferMarket].
 *  - **OCP**: nova cláusula = adicionar campo em [ContractClauses] +
 *    considerar em [evaluateOffer]/[releaseClauseFor]. Resto inalterado.
 *  - **DIP**: depende só de [GameState], [Player] e [MoraleService]. JVM-puro.
 */
object ContractService {

    // ── Constantes ──────────────────────────────────────────────────────

    /**
     * Janela em dias antes do fim do contrato em que o jogador começa a
     * cobrar renovação. Abaixo disso, ele aceita mais facilmente uma proposta.
     */
    const val EXPIRING_WINDOW_DAYS = 60

    /**
     * Fator usado para calcular a multa rescisória default na criação do
     * contrato: salario_anual * fator. Multiplica pelo overall depois.
     */
    private const val DEFAULT_RELEASE_FACTOR = 2.0

    // ── Status do contrato ──────────────────────────────────────────────

    /** Status simbólico do contrato em relação à data corrente. */
    enum class ContractStatus(val label: String) {
        ACTIVE("Ativo"),
        EXPIRING("Expirando"),    // <= EXPIRING_WINDOW_DAYS
        EXPIRED("Expirado")        // termino < hoje
    }

    /**
     * Dias restantes do contrato de um jogador. Negativo = já expirou.
     * Retorna null se a data de término não for parseável.
     */
    fun daysRemaining(state: GameState, player: Player): Int? {
        val end = player.contrato.termino ?: return null
        val today = runCatching { LocalDate.parse(state.currentDate) }.getOrNull() ?: return null
        val endDate = runCatching { LocalDate.parse(end) }.getOrNull() ?: return null
        return ChronoUnit.DAYS.between(today, endDate).toInt()
    }

    fun statusOf(state: GameState, player: Player): ContractStatus {
        val days = daysRemaining(state, player) ?: return ContractStatus.ACTIVE
        return when {
            days < 0                      -> ContractStatus.EXPIRED
            days <= EXPIRING_WINDOW_DAYS  -> ContractStatus.EXPIRING
            else                          -> ContractStatus.ACTIVE
        }
    }

    // ── Cláusulas ───────────────────────────────────────────────────────

    /**
     * Retorna as cláusulas atuais do contrato OU o default calculado quando
     * o jogador ainda não tem cláusulas configuradas. Garante que toda
     * leitura tenha um valor válido para mostrar na UI.
     */
    fun clausesFor(state: GameState, player: Player): ContractClauses {
        val existing = state.playerOverrides[player.id]?.contractClauses
        if (existing != null) return existing
        return defaultClausesFor(player)
    }

    /**
     * Multa rescisória default baseada em salário + overall. Quanto melhor o
     * jogador, maior a multa para impedir saída fácil.
     */
    private fun defaultClausesFor(player: Player): ContractClauses {
        val annual = (player.contrato.salario_mensal_estimado_brl ?: 0L) * 12
        val overallMult = overallMultiplierFor(player)
        val release = (annual * DEFAULT_RELEASE_FACTOR * overallMult / 2.0).toLong()
        return ContractClauses(
            releaseClauseBrl     = release,
            signingBonusBrl      = 0L,
            performanceClauseBrl = 0L
        )
    }

    private fun overallMultiplierFor(player: Player): Double = when {
        player.overallRating() >= 85 -> 4.0
        player.overallRating() >= 75 -> 3.0
        player.overallRating() >= 65 -> 2.0
        else                         -> 1.5
    }

    /** Atalho: pega só a multa rescisória. */
    fun releaseClauseFor(state: GameState, player: Player): Long =
        clausesFor(state, player).releaseClauseBrl

    // ── Encerramento antecipado ─────────────────────────────────────────

    /**
     * Quanto o clube paga para dispensar o jogador AGORA (antes do fim do contrato).
     *
     * - Contrato EXPIRADO: 0 (livre)
     * - Contrato EXPIRING (<= 60 dias): paga 1 mês de salário
     * - Contrato ATIVO: paga a multa rescisória proporcional à duração restante
     */
    fun earlyTerminationCost(state: GameState, player: Player): Long {
        val status = statusOf(state, player)
        val monthlySalary = player.contrato.salario_mensal_estimado_brl ?: 0L
        return when (status) {
            ContractStatus.EXPIRED  -> 0L
            ContractStatus.EXPIRING -> monthlySalary
            ContractStatus.ACTIVE   -> {
                val days = daysRemaining(state, player) ?: 0
                val totalDays = 365  // assume contrato anual padrão para normalizar
                val ratio = (days.toDouble() / totalDays).coerceIn(0.0, 1.0)
                (releaseClauseFor(state, player) * ratio).toLong()
            }
        }
    }

    // ── Avaliação de propostas de renovação ─────────────────────────────

    /** Resultado de uma proposta. */
    sealed class OfferResult {
        data class Accepted(val message: String) : OfferResult()
        data class Rejected(val reason: String) : OfferResult()
    }

    /**
     * Avalia uma proposta de renovação/alteração de contrato.
     *
     * Critérios para o jogador aceitar:
     *  1. Salário dentro de uma faixa razoável (não muito abaixo do atual)
     *  2. Jovem (<24) com overall alto (>80) → exige salário maior
     *  3. Veterano (>28) com overall baixo (<75) → aceita menos
     *  4. Moral alta (HAPPY) → aceita mais facilmente
     *  5. Moral baixa (SAD) → exige salário maior
     *  6. Bônus de assinatura compensa salário menor (R$50k = -1% no exigido)
     *  7. Contrato EXPIRING (<60 dias) → aceita com -8% no exigido
     *  8. Contrato EXPIRED → aceita com -20% (está desempregado)
     */
    fun evaluateOffer(
        state: GameState,
        player: Player,
        offeredMonthlySalary: Long,
        offeredDurationMonths: Int,
        signingBonus: Long = 0L
    ): OfferResult {
        val currentSalary = player.contrato.salario_mensal_estimado_brl ?: 0L
        if (currentSalary == 0L) {
            return if (offeredMonthlySalary > 0)
                OfferResult.Accepted("Aceita com entusiasmo — não tinha salário definido antes.")
            else
                OfferResult.Rejected("Sem salário definido, não vai aceitar zero.")
        }

        var minMultiplier = 0.85  // base: aceita -15% do atual
        val mood   = MoraleService.moodOf(state, player.id)
        val status = statusOf(state, player)
        val age    = player.idade ?: 25
        val ovr    = player.overallRating()

        minMultiplier += when {
            mood >= 80 -> -0.10  // HAPPY: mais flexível
            mood <= 30 -> +0.15  // SAD: mais exigente
            else        -> 0.0
        }
        if (age < 24 && ovr > 80)  minMultiplier += 0.10
        if (age > 28 && ovr < 75)  minMultiplier -= 0.10
        if (status == ContractStatus.EXPIRING) minMultiplier -= 0.08
        if (status == ContractStatus.EXPIRED)  minMultiplier -= 0.20

        val bonusOffset = (signingBonus / 50_000.0) * 0.01
        minMultiplier -= bonusOffset.coerceAtMost(0.15)

        val minimumAcceptable = (currentSalary * minMultiplier.coerceAtLeast(0.4)).toLong()

        return if (offeredMonthlySalary >= minimumAcceptable) {
            val pct = ((offeredMonthlySalary - currentSalary) * 100 / currentSalary).toInt()
            val tone = when {
                pct >= 20 -> "empolgado"
                pct >= 5  -> "satisfeito"
                pct >= -5 -> "contente"
                else      -> "com algumas hesitações"
            }
            OfferResult.Accepted(
                "${player.nome_jogo} aceitou a proposta $tone. " +
                "Salário: R$ ${"%,d".format(offeredMonthlySalary)}/mês por $offeredDurationMonths meses."
            )
        } else {
            OfferResult.Rejected(
                "${player.nome_jogo} recusou a proposta. " +
                "Pedido mínimo: R$ ${"%,d".format(minimumAcceptable)}/mês."
            )
        }
    }

    // ── Persistência ────────────────────────────────────────────────────

    /**
     * Aplica uma proposta APROVADA: atualiza salário, data de término e
     * cláusulas (multa rescisória recalculada).
     */
    fun applyAcceptedOffer(
        state: GameState,
        player: Player,
        newMonthlySalary: Long,
        newEndDate: String,
        signingBonus: Long = 0L
    ) {
        val existing = state.playerOverrides[player.id] ?: PlayerOverride(player.id)
        val newAnnual = newMonthlySalary * 12
        val newRelease = (newAnnual * DEFAULT_RELEASE_FACTOR * overallMultiplierFor(player) / 2.0).toLong()
        val updatedClauses = (existing.contractClauses ?: ContractClauses()).copy(
            releaseClauseBrl = newRelease,
            signingBonusBrl  = signingBonus
        )
        state.playerOverrides[player.id] = existing.copy(
            newSalary       = newMonthlySalary,
            newContractEnd  = newEndDate,
            contractClauses = updatedClauses
        )
        if (signingBonus > 0) state.budget -= signingBonus
    }
}
