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
    val atributos_derivados: AtributosDeriv,
    /**
     * Champion pool do jogador — campeões nos quais ele tem mais experiência
     * e maior taxa de vitória. Quando ele pickar um campeão desta lista no
     * pick & ban, o motor de simulação aplica um bônus de força (ver
     * [com.cblol.scout.domain.GameConstants.Player.CHAMP_POOL_MAIN_BONUS]).
     *
     * Tipicamente 3-5 campeões por jogador, refletindo:
     *   - signature picks competitivos (ex: Robo → Aatrox, Tinowns → Azir)
     *   - meta-picks da role atual
     *   - mains históricos do jogador
     */
    val championPool: List<String> = emptyList()
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


/**
 * Representa um campeão de LoL na tela de pick & ban.
 * imageUrl e splashUrl usam a Riot Data Dragon (versão 14.10.1).
 * Substitua a versão conforme o patch atual.
 */
data class Champion(
    val id: String,
    val name: String,
    val shortName: String,
    val roles: List<String>,
    val primaryRole: String,
    val tags: List<ChampionTag> = emptyList(),
    val imageUrl: String = "https://ddragon.leagueoflegends.com/cdn/14.10.1/img/champion/$id.png",
    val splashUrl: String = "https://ddragon.leagueoflegends.com/cdn/img/champion/splash/${id}_0.jpg"
) {
    /** Tags mais importantes para exibir no card (máx 3) */
    val primaryTags: List<ChampionTag> get() = tags.take(3)

    /** true se o campeão tem a tag especificada */
    fun hasTag(tag: ChampionTag) = tag in tags
}

enum class PickBanPhase { BAN, PICK }

/**
 * Estado mutável da fase de pick & ban.
 */
data class PickBanState(
    var currentTurnIndex: Int,
    val blueBans: MutableList<Champion>,
    val redBans: MutableList<Champion>,
    val bluePicks: MutableList<Champion>,
    val redPicks: MutableList<Champion>,
    val playerIsBlue: Boolean,
    val usedChampions: MutableSet<String>
)