package com.cblol.scout.game.repo

import com.cblol.scout.data.Academy
import com.cblol.scout.data.BankState
import com.cblol.scout.data.CoachProfile
import com.cblol.scout.data.Division
import com.cblol.scout.data.GameState
import com.cblol.scout.data.NewsItem
import com.cblol.scout.data.PlayerOverride
import com.cblol.scout.data.ScoutingDepartment
import com.cblol.scout.domain.usecase.TransferWindowService

/**
 * Aplica migrações defensivas em carreiras salvas antes de campos novos
 * existirem.
 *
 * O Gson não respeita default values do Kotlin quando o JSON tem o campo como
 * `null` (ou não tem o campo, dependendo da versão), então precisamos blindar
 * campos opcionais aqui. Cada bloco trata uma feature adicionada após a v1
 * do save format.
 *
 * Extraído do [com.cblol.scout.game.GameRepository] para isolar a
 * complexidade de compatibilidade retroativa: um único lugar onde olhar
 * quando saves antigos quebrarem.
 */
internal object GameStateMigrator {

    /**
     * Aplica todas as migrações em ordem. Idempotente — rodar duas vezes não
     * causa efeito colateral.
     */
    fun migrate(gs: GameState): GameState {
        migrateCoachProfile(gs)
        migrateMoodHistory(gs)
        migrateScoutingDepartment(gs)
        migrateTransferWindows(gs)
        migrateIncomingOffers(gs)
        migratePlayerBonds(gs)
        migrateAcademy(gs)
        migrateBank(gs)
        migrateSecondDivision(gs)
        migrateNews(gs)
        return gs
    }

    private fun migrateCoachProfile(gs: GameState) {
        // coachProfile foi adicionado em uma versão posterior — saves antigos
        // não têm esse campo no JSON; Gson o deixa null mesmo com default value.
        @Suppress("SENSELESS_COMPARISON")
        if (gs.coachProfile == null) {
            gs.coachProfile = CoachProfile()
        }
    }

    private fun migrateMoodHistory(gs: GameState) {
        // Defesa contra `moodHistory == null` em saves antigos. O Gson via
        // reflection põe null em fields List quando o JSON salvo não contém
        // a key, mesmo que o campo Kotlin tenha default = emptyList(). Como
        // o tipo declarado é List<MoodEvent> não-nullable, deixar null
        // crasharia em qualquer leitura. Reconstruímos os overrides afetados.
        val overridesToFix = gs.playerOverrides.entries.filter { entry ->
            @Suppress("SENSELESS_COMPARISON")
            entry.value.moodHistory == null
        }
        overridesToFix.forEach { entry ->
            gs.playerOverrides[entry.key] = entry.value.copy(moodHistory = emptyList<com.cblol.scout.data.MoodEvent>())
        }
    }

    private fun migrateScoutingDepartment(gs: GameState) {
        // Saves anteriores ao sistema de scouting não têm o departamento.
        // O ScoutingService cria default BASIC na primeira leitura, mas
        // inicializamos aqui para o estado ser persistido no próximo save.
        @Suppress("SENSELESS_COMPARISON")
        if (gs.scoutingDepartment == null) {
            gs.scoutingDepartment = ScoutingDepartment()
        }
    }

    private fun migrateTransferWindows(gs: GameState) {
        // Saves anteriores ao sistema de janelas não têm o campo. Reconstruímos
        // a partir das datas do split salvas no próprio estado, de modo que
        // carreiras em andamento passem a ter mercado com janelas sem precisar
        // reiniciar.
        @Suppress("SENSELESS_COMPARISON")
        if (gs.transferWindows == null || gs.transferWindows.isEmpty()) {
            val gameStart = runCatching {
                TransferWindowService.gameStartFor(gs.splitStartDate)
            }.getOrDefault(gs.currentDate)
            gs.transferWindows = TransferWindowService
                .buildWindowsForSplit(gameStart, gs.splitStartDate)
                .toMutableList()
        }
    }

    private fun migrateIncomingOffers(gs: GameState) {
        // Saves anteriores ao sistema de ofertas recebidas não têm o campo.
        if (gs.incomingOffers == null) {
            gs.incomingOffers = mutableListOf()
        }
    }

    private fun migratePlayerBonds(gs: GameState) {
        // Saves anteriores ao sistema de laços não têm o campo. A química
        // começa do zero para carreiras já em andamento (não há histórico a
        // recuperar) — o PlayerBondService popula os pares neutros na primeira
        // leitura/tick.
        if (gs.playerBonds == null) {
            gs.playerBonds = mutableMapOf()
        }
    }

    private fun migrateAcademy(gs: GameState) {
        // Saves anteriores ao sistema da categoria de base não têm a academia
        // nem a lista de promovidos. Carreiras em andamento ganham uma base
        // vazia — o gerente pode recrutar quando quiser.
        if (gs.academy == null) {
            gs.academy = Academy()
        }
        if (gs.promotedPlayers == null) {
            gs.promotedPlayers = mutableListOf()
        }
    }

    private fun migrateBank(gs: GameState) {
        // Saves anteriores ao sistema bancário não têm o estado. Carreiras em
        // andamento começam sem dívida.
        if (gs.bank == null) {
            gs.bank = BankState()
        }
    }

    private fun migrateSecondDivision(gs: GameState) {
        // Saves anteriores ao modo "começar de baixo" não têm divisão definida.
        // Default = primeira divisão (preserva comportamento antigo).
        @Suppress("SENSELESS_COMPARISON")
        if (gs.division == null) {
            gs.division = Division.FIRST
        }
        @Suppress("SENSELESS_COMPARISON")
        if (gs.secondDivisionTeams == null) {
            gs.secondDivisionTeams = mutableListOf()
        }
        @Suppress("SENSELESS_COMPARISON")
        if (gs.secondDivisionPlayers == null) {
            gs.secondDivisionPlayers = mutableListOf()
        }
    }

    private fun migrateNews(gs: GameState) {
        // Saves anteriores ao feed de notícias não têm o campo. Carreiras em
        // andamento começam com o feed vazio.
        if (gs.news == null) {
            gs.news = mutableListOf<NewsItem>()
        }
    }
}
