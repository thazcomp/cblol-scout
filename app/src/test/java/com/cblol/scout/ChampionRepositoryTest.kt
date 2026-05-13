package com.cblol.scout

import com.cblol.scout.util.ChampionRepository
import org.junit.Assert.*
import org.junit.Test

class ChampionRepositoryTest {

    @Test
    fun getAll_notEmpty() {
        assertTrue(ChampionRepository.getAll().isNotEmpty())
    }

    @Test
    fun getAll_allHaveNonBlankId() {
        ChampionRepository.getAll().forEach { assertTrue(it.id.isNotBlank()) }
    }

    @Test
    fun getAll_allHaveAtLeastOneRole() {
        ChampionRepository.getAll().forEach { assertTrue(it.roles.isNotEmpty()) }
    }

    @Test
    fun getAll_primaryRoleEqualsFirstRole() {
        ChampionRepository.getAll().forEach {
            assertEquals(it.roles.first(), it.primaryRole)
        }
    }

    @Test
    fun getAll_noDuplicateIds() {
        val ids = ChampionRepository.getAll().map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun getById_knownChampion_returnsCorrect() {
        val c = ChampionRepository.getById("Ahri")
        assertNotNull(c)
        assertEquals("Ahri", c!!.id)
    }

    @Test
    fun getById_unknownChampion_returnsNull() {
        assertNull(ChampionRepository.getById("CampeaoFicticio123"))
    }

    @Test
    fun getByRole_MID_allHaveMidInRoles() {
        val mids = ChampionRepository.getByRole("MID")
        assertTrue(mids.isNotEmpty())
        mids.forEach { assertTrue("MID" in it.roles) }
    }

    @Test
    fun getByRole_unknownRole_returnsEmpty() {
        assertTrue(ChampionRepository.getByRole("UNKNOWN_ROLE").isEmpty())
    }

    @Test
    fun shortName_maxLength9Chars() {
        ChampionRepository.getAll().forEach {
            assertTrue("${it.id} shortName muito longo: '${it.shortName}'",
                it.shortName.length <= 9)
        }
    }

    @Test
    fun imageUrl_containsChampionId() {
        val c = ChampionRepository.getById("Ahri")!!
        assertTrue(c.imageUrl.contains("Ahri"))
    }

    @Test
    fun splashUrl_containsChampionId() {
        val c = ChampionRepository.getById("Ahri")!!
        assertTrue(c.splashUrl.contains("Ahri"))
    }

    @Test
    fun imageUrl_isHttps() {
        ChampionRepository.getAll().forEach {
            assertTrue("${it.id} imageUrl não é https", it.imageUrl.startsWith("https://"))
        }
    }

    @Test
    fun splashUrl_isHttps() {
        ChampionRepository.getAll().forEach {
            assertTrue("${it.id} splashUrl não é https", it.splashUrl.startsWith("https://"))
        }
    }

    @Test
    fun multiRoleChampion_appearsInBothRoles() {
        // Ahri é MID; Gragas é TOP e JNG
        val gragas = ChampionRepository.getById("Gragas")!!
        assertTrue("TOP" in gragas.roles)
        assertTrue("JNG" in gragas.roles)
    }
}
