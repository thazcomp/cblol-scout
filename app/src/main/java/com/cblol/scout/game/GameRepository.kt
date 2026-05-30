package com.cblol.scout.game

import android.content.Context
import com.cblol.scout.data.AcademyProspect
import com.cblol.scout.data.GameState
import com.cblol.scout.data.LogEntry
import com.cblol.scout.data.Player
import com.cblol.scout.data.PlayerOverride
import com.cblol.scout.data.SnapshotData
import com.cblol.scout.data.Team
import com.cblol.scout.data.realm.GameStatePersistence
import com.cblol.scout.game.repo.PromotedPlayerFactory
import com.cblol.scout.game.repo.RosterResolver
import com.cblol.scout.util.JsonLoader

/**
 * Fachada de persistência e acesso ao estado da carreira.
 *
 * Esta classe coordena:
 *  - **Persistência** do [GameState] em banco **Realm criptografado** via
 *    [GameStatePersistence] (substitui o antigo SharedPreferences + Gson).
 *  - **Snapshot** imutável do JSON dos times (carregado uma vez do assets).
 *  - **Cache em memória** do GameState durante a sessão — o disco é tocado
 *    apenas em load/save explícitos.
 *  - **Logs** do jogo (rolling buffer de 80 entradas).
 *
 * **Tratar Realm como API:** [GameStatePersistence] expõe load/save/clear
 * mas também queries pontuais (orçamento, data, divisão) que leem campos
 * diretos da entidade sem desserializar o payload. Quando uma camada
 * superior só precisa de um número, prefira essas queries em vez de
 * `current()` (que assume estado em memória).
 *
 * A lógica que une fontes de roster, aplica overrides e cria players
 * promovidos vive em helpers no pacote `game/repo/`:
 *
 *  - **[com.cblol.scout.game.repo.GameStateMigrator]** — blindagem
 *    retroativa de campos adicionados depois da v1 (coachProfile, bank, news…).
 *    Aplicado pelo próprio [GameStatePersistence.load].
 *  - **[RosterResolver]** — `rosterOf`, `marketRoster`, `teamsForCurrentDivision`
 *    e a aplicação de overrides.
 *  - **[PromotedPlayerFactory]** — materialização de prospects da academia.
 */
object GameRepository {
    private const val LOG_MAX_SIZE = 80

    private var state: GameState? = null
    private var snapshot: SnapshotData? = null
    private var persistence: GameStatePersistence? = null

    // ── Snapshot ─────────────────────────────────────────────────────────

    /** Snapshot original (imutável) carregado do assets. */
    fun snapshot(context: Context): SnapshotData {
        if (snapshot == null) snapshot = JsonLoader.loadSnapshot(context)
        return snapshot!!
    }

    // ── Persistência (Realm) ─────────────────────────────────────────────

    /**
     * Lazy factory da camada Realm — só constrói no primeiro acesso para
     * evitar tocar o disco no startup quando ninguém precisa ainda.
     *
     * Na primeira aquisição também roda a migração de saves legados das
     * SharedPreferences (idempotente; veja [GameStatePersistence.migrateLegacySaveIfNeeded]).
     *
     * **Ordem importa**: atribuímos `persistence = created` ANTES de rodar a
     * migração; caso a migração (ou qualquer código que ela chame) chegue a
     * usar [GameRepository.hasSave] reentrantemente, já encontra a instância
     * cachada em vez de criar outra.
     */
    private fun persistence(context: Context): GameStatePersistence {
        val existing = persistence
        if (existing != null) return existing
        val created = GameStatePersistence(context.applicationContext)
        persistence = created
        created.migrateLegacySaveIfNeeded()
        return created
    }

    /** Existe um save (após migração eventual do legado)? */
    fun hasSave(context: Context): Boolean = persistence(context).hasSave()

    /**
     * Carrega o estado do Realm e o coloca em cache em memória. Devolve null
     * se não há save. Chamadas subsequentes na mesma sessão usam o cache.
     */
    fun load(context: Context): GameState? {
        state?.let { return it }
        val loaded = persistence(context).load() ?: return null
        state = loaded
        return loaded
    }

    /**
     * Grava o estado no Realm. Se [gs] for null, persiste o estado em cache.
     * No-op se não há nada a salvar.
     */
    fun save(context: Context, gs: GameState? = null) {
        val target = gs ?: state ?: return
        state = target
        persistence(context).save(target)
    }

    /** Apaga o save do Realm e zera o cache em memória. */
    fun clear(context: Context) {
        state = null
        persistence(context).clear()
    }

    /**
     * Estado em memória da sessão atual. Requer um [load] prévio bem-sucedido
     * ou o estado vindo de [com.cblol.scout.game.GameEngine.startNewCareer]
     * (que chama [save] ao final).
     */
    fun current(): GameState = state ?: error("GameState não carregado")

    /**
     * Consulta pontual de orçamento direto no Realm, sem reidratar o estado
     * completo. Útil quando UI exterior à carreira (ex: tela de login com
     * "Continuar com R$ X em caixa") só precisa de um valor.
     */
    fun persistedBudget(context: Context): Long? =
        persistence(context).currentBudget()

    /** Consulta pontual da data corrente persistida. */
    fun persistedCurrentDate(context: Context): String? =
        persistence(context).currentDate()

    // ── Rosters e times (delegado a RosterResolver) ─────────────────────

    /**
     * Retorna jogadores efetivamente lotados num time, considerando
     * transferências. Une snapshot + free agents do CD + academia +
     * jogadores da 2ª divisão. Ver [RosterResolver.rosterOf].
     */
    fun rosterOf(context: Context, teamId: String): List<Player> =
        RosterResolver.rosterOf(snapshot(context), state, teamId)

    /** Lista os 8 times da divisão ATIVA da carreira. */
    fun teamsForCurrentDivision(context: Context): List<Team> =
        RosterResolver.teamsForCurrentDivision(snapshot(context), state)

    /** Jogadores que NÃO pertencem ao time do gerente (mercado), filtrado por divisão. */
    fun marketRoster(context: Context): List<Player> =
        RosterResolver.marketRoster(snapshot(context), current())

    // ── Overrides ────────────────────────────────────────────────────────

    /** Atualiza override (cria se não existir). */
    fun updateOverride(playerId: String, transform: (PlayerOverride) -> PlayerOverride) {
        val gs = current()
        val current = gs.playerOverrides[playerId] ?: PlayerOverride(playerId)
        gs.playerOverrides[playerId] = transform(current)
    }

    // ── Promoção da base ────────────────────────────────────────────────

    /**
     * Materializa um prospect promovido da base como um [Player] real no
     * elenco do gerente e o persiste em [GameState.promotedPlayers].
     */
    fun addPromotedProspect(
        context: Context,
        prospect: AcademyProspect,
        salary: Long,
        teamName: String
    ): Player {
        val gs = current()
        val player = PromotedPlayerFactory.build(prospect, salary, teamName, gs)
        val list = gs.promotedPlayers ?: mutableListOf<Player>().also { gs.promotedPlayers = it }
        list.add(player)
        return player
    }

    // ── Log ──────────────────────────────────────────────────────────────

    fun log(type: String, message: String) {
        val gs = current()
        gs.gameLog.add(0, LogEntry(gs.currentDate, type, message))
        if (gs.gameLog.size > LOG_MAX_SIZE) gs.gameLog.removeAt(gs.gameLog.size - 1)
    }
}
