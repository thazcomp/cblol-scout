package com.cblol.scout.data

data class SnapshotData(
    val meta: Meta,
    val times: List<Team>,
    val jogadores: List<Player>
)

data class Meta(
    val liga: String,
    val split: String,
    val atualizado_em: String,
    val fontes: List<String>
)

data class Team(
    val id: String,
    val nome: String,
    val tier_orcamento: String
)

data class Player(
    val id: String,
    val nome_jogo: String,
    val nome_real: String?,
    val time_id: String,
    val time_nome: String,
    val role: String,
    val titular: Boolean,
    val idade: Int?,
    val nacionalidade: String,
    val contrato: Contrato,
    val stats_brutas: StatsBrutas,
    val atributos_derivados: AtributosDeriv
) {
    fun overallRating(): Int {
        return with(atributos_derivados) {
            (lane_phase + team_fight + criatividade + consistencia + clutch) / 5
        }
    }
}

data class Contrato(
    val termino: String?,
    val valor_estimado_brl: Long?,
    val salario_mensal_estimado_brl: Long?,
    val fonte_salario: String
)

data class StatsBrutas(
    val jogos: Int,
    val kda: Double,
    val kp_pct: Double,
    val cs_min: Double,
    val gd15: Int?,
    val xpd15: Int?,
    val damage_share_pct: Double,
    val vision_score_min: Double?
)

data class AtributosDeriv(
    val lane_phase: Int,
    val team_fight: Int,
    val criatividade: Int,
    val consistencia: Int,
    val clutch: Int
)
