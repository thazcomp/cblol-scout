package com.cblol.scout.util

import com.cblol.scout.data.Champion
import com.cblol.scout.data.ChampionTag
import com.cblol.scout.data.StaticData

/**
 * Repositório de campeões — fachada estável sobre o
 * [com.cblol.scout.domain.datasource.StaticDataSource].
 *
 * Os dados de campeões NÃO ficam mais hardcoded aqui: agora vivem no banco
 * **Realm criptografado** e são lidos via [StaticData]. Os dados de seed que
 * populam o Realm na primeira execução estão em
 * [com.cblol.scout.data.seed.ChampionSeed] (única fonte hardcoded restante,
 * usada só para seeding).
 *
 * A API pública (getAll/getById/getByRole/getByTag) é idêntica à versão
 * anterior, então nenhum call-site precisou mudar.
 */
object ChampionRepository {

    fun getAll(): List<Champion> = StaticData.source.allChampions()

    fun getById(id: String): Champion? = StaticData.source.championById(id)

    fun getByRole(role: String): List<Champion> =
        StaticData.source.allChampions().filter { role in it.roles }

    fun getByTag(tag: ChampionTag): List<Champion> =
        StaticData.source.allChampions().filter { it.hasTag(tag) }
}
