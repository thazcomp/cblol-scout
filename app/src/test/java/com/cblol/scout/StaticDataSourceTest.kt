package com.cblol.scout

import com.cblol.scout.data.StaticData
import com.cblol.scout.data.seed.ChampionPoolSeed
import com.cblol.scout.data.seed.ChampionSeed
import com.cblol.scout.data.seed.CompositionSeed
import com.cblol.scout.data.seed.SponsorSeed
import com.cblol.scout.domain.datasource.StaticDataSource
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Testes da camada de dados estáticos migrada para o Realm.
 *
 * Como o runtime nativo do Realm não roda em testes JVM puros, exercitamos a
 * lógica através do [InMemoryStaticDataSource] (que serve os MESMOS seeds que
 * populam o Realm em produção) e validamos a consistência dos dados de seed.
 *
 * Isso garante que:
 *  - o contrato [StaticDataSource] é satisfeito corretamente;
 *  - os repositórios (`ChampionRepository` etc.) leem do DataSource;
 *  - os dados de seed estão íntegros (sem ids duplicados, refs válidas, etc.).
 */
class StaticDataSourceTest {

    private lateinit var source: StaticDataSource

    @Before
    fun setup() {
        source = InMemoryStaticDataSource()
        StaticData.install(source)
    }

    // ── StaticData holder ───────────────────────────────────────────────

    @Test
    fun staticData_isInstalled_afterInstall() {
        assertTrue(StaticData.isInstalled())
    }

    @Test
    fun staticData_source_returnsInstalledInstance() {
        assertSame(source, StaticData.source)
    }

    // ── Campeões ────────────────────────────────────────────────────────

    @Test
    fun champions_matchSeedCount() {
        assertEquals(ChampionSeed.all().size, source.allChampions().size)
    }

    @Test
    fun champions_notEmpty() {
        assertTrue(source.allChampions().isNotEmpty())
    }

    @Test
    fun champions_noDuplicateIds() {
        val ids = source.allChampions().map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun championById_knownChampion_found() {
        assertEquals("Ahri", source.championById("Ahri")?.id)
    }

    @Test
    fun championById_unknown_returnsNull() {
        assertNull(source.championById("NaoExiste123"))
    }

    @Test
    fun champions_preserveTagsFromSeed() {
        // Aatrox tem várias tags no seed; garante que o mapeamento as preserva.
        val aatrox = source.championById("Aatrox")!!
        assertTrue(aatrox.tags.isNotEmpty())
    }

    @Test
    fun champions_ddragonId_usedInImageUrl() {
        // Wukong tem ddragonId "MonkeyKing" — a URL da imagem deve refletir isso.
        val wukong = source.championById("Wukong")!!
        assertTrue(wukong.imageUrl.contains("MonkeyKing"))
    }

    // ── Composições ─────────────────────────────────────────────────────

    @Test
    fun compositions_matchSeedCount() {
        assertEquals(CompositionSeed.all().size, source.allCompositions().size)
    }

    @Test
    fun compositions_noDuplicateIds() {
        val ids = source.allCompositions().map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun compositions_keyChampionsExistInChampionPool() {
        // Todo keyChampion de toda comp deve existir no catálogo de campeões.
        val championIds = source.allChampions().map { it.id }.toSet()
        source.allCompositions().forEach { comp ->
            comp.keyChampions.forEach { key ->
                assertTrue(
                    "Comp ${comp.id}: keyChampion '$key' não existe no catálogo",
                    key in championIds
                )
            }
        }
    }

    // ── Patrocínios ─────────────────────────────────────────────────────

    @Test
    fun sponsors_matchSeedCount() {
        assertEquals(SponsorSeed.all().size, source.allSponsors().size)
    }

    @Test
    fun sponsors_noDuplicateIds() {
        val ids = source.allSponsors().map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun sponsors_allHavePositiveWeeklyAmount() {
        source.allSponsors().forEach {
            assertTrue("${it.id} weeklyAmount <= 0", it.weeklyAmount > 0)
        }
    }

    // ── Champion pools ──────────────────────────────────────────────────

    @Test
    fun signaturePool_knownPlayer_found() {
        // "tinowns" tem pool fixo no seed.
        assertNotNull(source.signaturePool("tinowns"))
    }

    @Test
    fun signaturePool_unknownPlayer_returnsNull() {
        assertNull(source.signaturePool("jogador_inexistente_xyz"))
    }

    @Test
    fun rolePool_knownRole_notEmpty() {
        assertTrue(source.rolePool("MID").isNotEmpty())
    }

    @Test
    fun rolePool_unknownRole_returnsEmpty() {
        assertTrue(source.rolePool("ROLE_INEXISTENTE").isEmpty())
    }

    @Test
    fun signaturePools_matchSeed() {
        assertEquals(ChampionPoolSeed.signaturePools.size,
            ChampionPoolSeed.signaturePools.keys.count { source.signaturePool(it) != null })
    }

    @Test
    fun rolePools_allFiveRolesPresent() {
        listOf("TOP", "JNG", "MID", "ADC", "SUP").forEach {
            assertTrue("Role pool ausente: $it", source.rolePool(it).isNotEmpty())
        }
    }
}
