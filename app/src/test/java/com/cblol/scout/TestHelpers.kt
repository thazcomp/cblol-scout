package com.cblol.scout

import com.cblol.scout.data.*
import org.junit.Assert.*
import org.junit.Test

// ── Helpers compartilhados entre todos os arquivos de teste ──────────────────

fun makeAtributos(value: Int = 70) =
    AtributosDeriv(value, value, value, value, value)

fun makeContrato(salary: Long = 10_000L) = Contrato(
    termino = "2026-12-31",
    valor_estimado_brl = salary * 12,
    salario_mensal_estimado_brl = salary,
    fonte_salario = "estimado"
)

fun makeStats() = StatsBrutas(10, 3.0, 60.0, 8.0, 100, 100, 25.0, 1.0)

fun makePlayer(
    id: String,
    role: String,
    overall: Int = 70,
    titular: Boolean = true,
    teamId: String = "T1",
    salary: Long = 10_000L
) = Player(
    id = id, nome_jogo = id, nome_real = null,
    time_id = teamId, time_nome = teamId,
    role = role, titular = titular, idade = 22,
    nacionalidade = "BR",
    contrato = makeContrato(salary),
    stats_brutas = makeStats(),
    atributos_derivados = makeAtributos(overall)
)

fun makeRoster5(teamId: String = "T1", overall: Int = 70) = listOf(
    makePlayer("${teamId}_top", "TOP", overall, teamId = teamId),
    makePlayer("${teamId}_jng", "JNG", overall, teamId = teamId),
    makePlayer("${teamId}_mid", "MID", overall, teamId = teamId),
    makePlayer("${teamId}_adc", "ADC", overall, teamId = teamId),
    makePlayer("${teamId}_sup", "SUP", overall, teamId = teamId)
)

fun makeMatch(
    id: String = "m1",
    homeId: String = "T1",
    awayId: String = "T2",
    played: Boolean = false,
    homeScore: Int = 0,
    awayScore: Int = 0
) = Match(
    id = id, date = "2026-03-28", round = 1,
    homeTeamId = homeId, awayTeamId = awayId,
    played = played, homeScore = homeScore, awayScore = awayScore
)

val EIGHT_TEAMS = listOf("T1", "T2", "T3", "T4", "T5", "T6", "T7", "T8")
