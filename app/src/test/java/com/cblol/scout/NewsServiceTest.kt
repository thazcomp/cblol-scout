package com.cblol.scout

import com.cblol.scout.data.NewsCategory
import com.cblol.scout.domain.usecase.NewsService
import org.junit.Assert.*
import org.junit.Test

/**
 * Testes do [NewsService] — geração e gestão do feed de notícias.
 *
 * O serviço é JVM-puro: cada `report*` publica um [com.cblol.scout.data.NewsItem]
 * em `state.news` (criado sob demanda). As manchetes têm variações sorteadas,
 * então os testes verificam invariantes estruturais (categoria, prioridade,
 * campos preenchidos, ordenação, limite) em vez de texto exato.
 */
class NewsServiceTest {

    @Test
    fun feed_emptyWhenNoNews() {
        val gs = makeGameState()
        assertTrue(NewsService.feed(gs).isEmpty())
        assertNull(NewsService.latestHeadline(gs))
    }

    @Test
    fun reportMatchResult_publishesOneMatchNews() {
        val gs = makeGameState()
        NewsService.reportMatchResult(
            gs, "Meu Time", "Rival", managerWon = true,
            managerMaps = 2, opponentMaps = 0
        )
        val feed = NewsService.feed(gs)
        assertEquals(1, feed.size)
        assertEquals(NewsCategory.MATCH, feed.first().category)
        assertTrue(feed.first().headline.isNotBlank())
        assertTrue(feed.first().body.isNotBlank())
        assertTrue(feed.first().source.isNotBlank())
    }

    @Test
    fun reportMatchResult_upsetWinHasHeadlinePriority() {
        val gs = makeGameState()
        // Zebra (vitória surpresa) deve ter prioridade de manchete de capa,
        // maior que uma vitória comum.
        NewsService.reportMatchResult(
            gs, "Zé", "Favorito", managerWon = true,
            managerMaps = 2, opponentMaps = 1, wasUpset = true
        )
        val upsetPriority = NewsService.feed(gs).first().priority

        val gs2 = makeGameState()
        NewsService.reportMatchResult(
            gs2, "Zé", "Rival", managerWon = true,
            managerMaps = 2, opponentMaps = 1, wasUpset = false
        )
        val normalPriority = NewsService.feed(gs2).first().priority

        assertTrue("Zebra deve ter prioridade maior que vitória comum",
            upsetPriority > normalPriority)
    }

    @Test
    fun reportPlayerMilestone_publishesPlayerNews() {
        val gs = makeGameState()
        NewsService.reportPlayerMilestone(gs, "Faker", "KDA de 15.0")
        val item = NewsService.feed(gs).first()
        assertEquals(NewsCategory.PLAYER, item.category)
        assertTrue(item.headline.contains("Faker"))
    }

    @Test
    fun reportSigning_expensiveHasHigherPriority() {
        val gsCheap = makeGameState()
        NewsService.reportSigning(gsCheap, "Jogador", "Time", 50_000L)
        val cheap = NewsService.feed(gsCheap).first()

        val gsExpensive = makeGameState()
        NewsService.reportSigning(gsExpensive, "Craque", "Time", 500_000L)
        val expensive = NewsService.feed(gsExpensive).first()

        assertEquals(NewsCategory.TRANSFER, cheap.category)
        assertEquals(NewsCategory.TRANSFER, expensive.category)
        assertTrue("Contratação cara deve ter prioridade maior",
            expensive.priority > cheap.priority)
    }

    @Test
    fun reportDeparture_publishesTransferNews() {
        val gs = makeGameState()
        NewsService.reportDeparture(gs, "Jogador", "Meu Time", "Outro Time", 200_000L)
        val item = NewsService.feed(gs).first()
        assertEquals(NewsCategory.TRANSFER, item.category)
    }

    @Test
    fun reportAcademyPromotion_highOverallIsHeadline() {
        val gs = makeGameState()
        NewsService.reportAcademyPromotion(gs, "Joia", "Meu Time", overall = 75)
        val item = NewsService.feed(gs).first()
        assertEquals(NewsCategory.ACADEMY, item.category)
        // Joia de alto overall (>=70) é manchete de capa.
        assertTrue(item.priority >= 100)
    }

    @Test
    fun reportSponsorship_publishesFinanceNews() {
        val gs = makeGameState()
        NewsService.reportSponsorship(gs, "VoltKick", "Meu Time", 100_000L)
        assertEquals(NewsCategory.FINANCE, NewsService.feed(gs).first().category)
    }

    @Test
    fun reportFinancialCrisis_publishesFinanceNews() {
        val gs = makeGameState()
        NewsService.reportFinancialCrisis(gs, "Meu Time")
        assertEquals(NewsCategory.FINANCE, NewsService.feed(gs).first().category)
    }

    @Test
    fun reportStrongBond_andCrisis_publishLockerRoomNews() {
        val gs = makeGameState()
        NewsService.reportStrongBond(gs, "A", "B")
        NewsService.reportLockerRoomCrisis(gs, "C", "D")
        val feed = NewsService.feed(gs)
        assertEquals(2, feed.size)
        assertTrue(feed.all { it.category == NewsCategory.LOCKER_ROOM })
    }

    @Test
    fun reportTransferRequest_publishesLockerRoomNews() {
        val gs = makeGameState()
        NewsService.reportTransferRequest(gs, "Insatisfeito", "Meu Time")
        assertEquals(NewsCategory.LOCKER_ROOM, NewsService.feed(gs).first().category)
    }

    @Test
    fun reportStandings_leaderHasHighPriority() {
        val gs = makeGameState()
        NewsService.reportStandings(gs, "Meu Time", position = 1, totalTeams = 8)
        val leader = NewsService.feed(gs).first()
        assertEquals(NewsCategory.STANDINGS, leader.category)

        val gsMid = makeGameState()
        NewsService.reportStandings(gsMid, "Meu Time", position = 5, totalTeams = 8)
        val mid = NewsService.feed(gsMid).first()

        assertTrue("Liderança deve ter prioridade maior que meio de tabela",
            leader.priority > mid.priority)
    }

    @Test
    fun feed_sortedByDateThenPriorityDescending() {
        val gs = makeGameState()
        // Publica em datas diferentes para checar ordenação por data desc.
        gs.currentDate = "2026-04-01"
        NewsService.reportMatchResult(gs, "T", "R", managerWon = true, managerMaps = 2, opponentMaps = 0)
        gs.currentDate = "2026-04-10"
        NewsService.reportSponsorship(gs, "Sponsor", "T", 100_000L)

        val feed = NewsService.feed(gs)
        // Mais recente primeiro.
        assertEquals("2026-04-10", feed.first().date)
        assertEquals("2026-04-01", feed.last().date)
    }

    @Test
    fun feed_sameDateSortedByPriority() {
        val gs = makeGameState()
        gs.currentDate = "2026-04-05"
        // Patrocínio (prioridade baixa) + zebra (prioridade de capa) no mesmo dia.
        NewsService.reportSponsorship(gs, "Sponsor", "T", 100_000L)
        NewsService.reportMatchResult(
            gs, "T", "Favorito", managerWon = true,
            managerMaps = 2, opponentMaps = 1, wasUpset = true
        )
        val feed = NewsService.feed(gs)
        // No mesmo dia, a de maior prioridade vem primeiro.
        assertEquals(NewsCategory.MATCH, feed.first().category)
        assertEquals(NewsCategory.FINANCE, feed.last().category)
    }

    @Test
    fun latestHeadline_returnsTopOfFeed() {
        val gs = makeGameState()
        gs.currentDate = "2026-04-05"
        NewsService.reportSponsorship(gs, "Sponsor", "T", 100_000L)
        NewsService.reportMatchResult(
            gs, "T", "Favorito", managerWon = true,
            managerMaps = 2, opponentMaps = 0, wasUpset = true
        )
        val headline = NewsService.latestHeadline(gs)
        assertNotNull(headline)
        assertEquals(NewsCategory.MATCH, headline!!.category)
    }

    @Test
    fun feed_respectsMaxSize() {
        val gs = makeGameState()
        // Publica mais que o limite e confirma que o feed não cresce além dele.
        repeat(NewsService.MAX_FEED_SIZE + 5) {
            NewsService.reportSponsorship(gs, "Sponsor$it", "T", 100_000L)
        }
        assertEquals(NewsService.MAX_FEED_SIZE, NewsService.feed(gs).size)
    }

    @Test
    fun publish_createsNewsListOnDemand() {
        val gs = makeGameState()
        assertNull(gs.news)
        NewsService.reportFinancialCrisis(gs, "T")
        assertNotNull(gs.news)
        assertEquals(1, gs.news!!.size)
    }

    @Test
    fun allItems_haveNonBlankSourceAndEmoji() {
        val gs = makeGameState()
        NewsService.reportMatchResult(gs, "T", "R", managerWon = false, managerMaps = 0, opponentMaps = 2)
        NewsService.reportSigning(gs, "P", "T", 100_000L)
        NewsService.reportAcademyPromotion(gs, "J", "T", 60)
        NewsService.feed(gs).forEach {
            assertTrue("source vazio", it.source.isNotBlank())
            assertTrue("emoji vazio", it.sourceEmoji.isNotBlank())
            assertTrue("id vazio", it.id.isNotBlank())
        }
    }
}
