package com.cblol.scout.util

import com.cblol.scout.data.Sponsor
import com.cblol.scout.data.StaticData

/**
 * Catálogo de patrocínios — fachada estável sobre o
 * [com.cblol.scout.domain.datasource.StaticDataSource].
 *
 * Os patrocínios NÃO ficam mais hardcoded aqui: vivem no banco **Realm
 * criptografado** e são lidos via [StaticData]. Os dados de seed estão em
 * [com.cblol.scout.data.seed.SponsorSeed] (usados só para popular o Realm na
 * primeira execução).
 *
 * A API pública (`ALL`, `byId`) é idêntica à versão anterior.
 */
object SponsorCatalog {

    val ALL: List<Sponsor> get() = StaticData.source.allSponsors()

    /** Patrocínio pelo id. Útil para reconstruir contratos vindos do save. */
    fun byId(id: String): Sponsor? = ALL.find { it.id == id }
}
