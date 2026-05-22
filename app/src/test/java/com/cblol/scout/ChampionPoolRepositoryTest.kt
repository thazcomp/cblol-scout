package com.cblol.scout

import com.cblol.scout.util.ChampionPoolRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Testes da LÓGICA de atribuição de champion pools, agora que os dados (pools
 * signature e por role) vêm do [StaticData] (Realm em prod, fake nos testes).
 *
 * Cobre a integração da migração: a lógica de sorteio/atribuição continua igual,
 * mas os catálogos são lidos da fonte de dados em vez de mapas hardcoded.
 */
class ChampionPoolRepositoryTest {

    @Before
    fun setup() = installTestStaticData()

    @Test
    fun attach_signaturePlayer_usesFixedPool() {
        // "tinowns" tem pool fixo: Azir/Orianna/Syndra/Ahri/Viktor.
        val player = makePlayer("tinowns", "MID")
        val withPool = ChampionPoolRepository.attach(player)
        assertTrue(withPool.championPool.contains("Azir"))
        assertEquals(5, withPool.championPool.size)
    }

    @Test
    fun attach_unknownPlayer_generatesPoolFromRoleCatalog() {
        val player = makePlayer("jogador_qualquer", "ADC")
        val withPool = ChampionPoolRepository.attach(player)
        assertTrue("pool deveria ter 3-5 mains",
            withPool.championPool.size in 3..5)
        // Todos os mains gerados pertencem ao catálogo da role ADC.
        val adcCatalog = StaticData.source.rolePool("ADC").toSet()
        withPool.championPool.forEach {
            assertTrue("$it não pertence ao catálogo ADC", it in adcCatalog)
        }
    }

    @Test
    fun attach_isDeterministic_sameIdSamePool() {
        val a = ChampionPoolRepository.attach(makePlayer("mesmo_id", "TOP"))
        val b = ChampionPoolRepository.attach(makePlayer("mesmo_id", "TOP"))
        assertEquals(a.championPool, b.championPool)
    }

    @Test
    fun attach_existingPool_isPreserved() {
        val player = makePlayer("p", "MID").copy(championPool = listOf("Zed", "Ahri"))
        val result = ChampionPoolRepository.attach(player)
        assertEquals(listOf("Zed", "Ahri"), result.championPool)
    }

    @Test
    fun countMainsPicked_countsCorrectly() {
        val roster = listOf(
            makePlayer("tinowns", "MID"),   // Azir é main
            makePlayer("outro", "TOP")
        ).map { ChampionPoolRepository.attach(it) }

        val picked = ChampionPoolRepository.countMainsPicked(roster, listOf("Azir"))
        assertTrue("Tinowns joga Azir de main", picked >= 1)
    }
}
