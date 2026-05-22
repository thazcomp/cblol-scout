package com.cblol.scout

import com.cblol.scout.data.Champion
import com.cblol.scout.data.Sponsor
import com.cblol.scout.data.TeamComposition
import com.cblol.scout.data.seed.ChampionPoolSeed
import com.cblol.scout.data.seed.ChampionSeed
import com.cblol.scout.data.seed.CompositionSeed
import com.cblol.scout.data.seed.SponsorSeed
import com.cblol.scout.domain.datasource.StaticDataSource

/**
 * Implementação **em memória** de [StaticDataSource] para testes JVM puros.
 *
 * O runtime nativo do Realm não roda fora do Android, então os testes não podem
 * usar o `RealmStaticDataSource`. Este fake serve os MESMOS dados de seed
 * (`data.seed.*`) que populam o Realm em produção — garantindo que os testes
 * exercitem exatamente os dados reais do jogo, sem depender do banco.
 *
 * Uso (no `@Before` de cada teste que toca dados estáticos):
 * ```
 * StaticData.install(InMemoryStaticDataSource())
 * ```
 *
 * Como `RealmStaticDataSource` e este fake derivam da mesma fonte de seed, são
 * substituíveis sem divergência de comportamento (LSP).
 */
class InMemoryStaticDataSource : StaticDataSource {

    private val champions: List<Champion> = ChampionSeed.all()
    private val championsById: Map<String, Champion> = champions.associateBy { it.id }
    private val compositions: List<TeamComposition> = CompositionSeed.all()
    private val sponsors: List<Sponsor> = SponsorSeed.all()
    private val signaturePools: Map<String, List<String>> = ChampionPoolSeed.signaturePools
    private val rolePools: Map<String, List<String>> = ChampionPoolSeed.rolePools

    override fun allChampions(): List<Champion> = champions
    override fun championById(id: String): Champion? = championsById[id]
    override fun allCompositions(): List<TeamComposition> = compositions
    override fun allSponsors(): List<Sponsor> = sponsors
    override fun signaturePool(key: String): List<String>? = signaturePools[key]
    override fun rolePool(role: String): List<String> = rolePools[role] ?: emptyList()
}
