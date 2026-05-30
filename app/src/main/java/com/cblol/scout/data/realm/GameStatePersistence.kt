package com.cblol.scout.data.realm

import android.content.Context
import com.cblol.scout.data.Division
import com.cblol.scout.data.GameState
import com.cblol.scout.game.repo.GameStateMigrator
import com.google.gson.Gson
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.query

/**
 * Camada de persistência do save da carreira no **Realm criptografado**.
 *
 * Toma o lugar do antigo `SharedPreferences + Gson` que o [com.cblol.scout.game.GameRepository]
 * usava. Mantém a mesma chave AES-256 (envelope encryption via Android
 * Keystore) compartilhada com os dados estáticos (mas em arquivo Realm
 * SEPARADO, para isolar schema/migração).
 *
 * **Modelo de uso (Realm como API):**
 *
 *  - [hasSave] — checa se existe slot sem desserializar nada.
 *  - [load] — lê a entidade, desserializa o payload e devolve o [GameState]
 *    aplicando o [GameStateMigrator] (compatibilidade com saves antigos).
 *  - [save] — grava a entidade em transação atômica: copia os campos-chave +
 *    serializa o restante em JSON.
 *  - [clear] — apaga o slot.
 *  - [currentBudget] — exemplo de query pontual: lê só `budget` sem
 *    desserializar payload.
 *  - [migrateLegacySaveIfNeeded] — importa um save antigo das SharedPreferences
 *    para o Realm na primeira execução pós-update, e apaga o legado.
 *
 * **Thread-safety:** cada operação abre e fecha um `Realm` próprio (o
 * `Realm.open` é leve para o mesmo arquivo já configurado). Para chamadas
 * de alta frequência considere injetar um Realm singleton via DI; hoje a
 * granularidade do save (1 vez por ação do usuário) não justifica.
 *
 * **SOLID:**
 *  - **SRP**: só persiste o GameState. Regras de jogo, migração defensiva e
 *    resolução de roster ficam fora.
 *  - **DIP**: a fachada [com.cblol.scout.game.GameRepository] depende desta
 *    classe via instância única — substituível por um fake em testes.
 */
class GameStatePersistence(
    private val context: Context,
    private val keyProvider: RealmKeyProvider = RealmKeyProvider(context)
) {

    private val gson = Gson()

    // ── API pública ──────────────────────────────────────────────────────

    /** Existe um save persistido? Não desserializa nada. */
    fun hasSave(): Boolean = withRealm { realm ->
        realm.query<GameStateBlobEntity>("id == $0", GameStateBlobEntity.SLOT_ID)
            .count().find() > 0L
    }

    /**
     * Carrega o save existente do Realm e devolve um [GameState] já com
     * migrações aplicadas. Retorna null se não há save.
     *
     * Reconstrói o GameState combinando:
     *  - Os **campos diretos** (managerName, budget, currentDate, etc.) — fonte
     *    de verdade para essas propriedades. O Realm pode ter sido atualizado
     *    pontualmente, então preferir os campos diretos sobre o JSON evita ler
     *    valores stale do payload.
     *  - O **payload JSON** para o restante (matches, gameLog, mapas, etc.).
     */
    fun load(): GameState? = withRealm { realm ->
        val entity = realm.query<GameStateBlobEntity>("id == $0", GameStateBlobEntity.SLOT_ID)
            .first().find() ?: return@withRealm null
        val fromJson = runCatching { gson.fromJson(entity.payloadJson, GameState::class.java) }
            .getOrNull() ?: return@withRealm null
        // Garante que os campos-chave da entidade prevaleçam (o payload JSON é o
        // estado serializado na ÚLTIMA gravação completa — desde então pode ter
        // havido um save parcial que atualizou só os campos diretos).
        val merged = fromJson.copy(currentDate = entity.currentDate).also {
            it.budget = entity.budget
        }
        GameStateMigrator.migrate(merged)
    }

    /**
     * Grava o estado completo: extrai os campos-chave para colunas do Realm
     * (consultáveis sem desserializar) e serializa o resto em JSON.
     * Operação atômica via `realm.writeBlocking`.
     */
    fun save(state: GameState) {
        val json = gson.toJson(state)
        withRealm { realm ->
            realm.writeBlocking {
                val existing = query<GameStateBlobEntity>("id == $0", GameStateBlobEntity.SLOT_ID)
                    .first().find()
                val target = existing ?: copyToRealm(GameStateBlobEntity().apply {
                    id = GameStateBlobEntity.SLOT_ID
                })
                target.managerName        = state.managerName
                target.managerTeamId      = state.managerTeamId
                target.splitStartDate     = state.splitStartDate
                target.splitEndDate       = state.splitEndDate
                target.currentDate        = state.currentDate
                target.budget             = state.budget
                target.sponsorshipPerWeek = state.sponsorshipPerWeek
                target.division           = state.division.name
                target.payloadJson        = json
                target.savedAtMillis      = System.currentTimeMillis()
            }
        }
    }

    /** Apaga o save. Idempotente. */
    fun clear() {
        withRealm { realm ->
            realm.writeBlocking {
                val existing = query<GameStateBlobEntity>("id == $0", GameStateBlobEntity.SLOT_ID)
                    .first().find()
                if (existing != null) delete(existing)
            }
        }
    }

    // ── Queries pontuais (Realm como API) ───────────────────────────────

    /**
     * Lê SÓ o orçamento sem desserializar o payload. Exemplo do uso "banco
     * como API" — para um badge no Hub não vale a pena reidratar todo o
     * GameState quando só queremos um número.
     */
    fun currentBudget(): Long? = withRealm { realm ->
        realm.query<GameStateBlobEntity>("id == $0", GameStateBlobEntity.SLOT_ID)
            .first().find()?.budget
    }

    /** Idem para a data atual. */
    fun currentDate(): String? = withRealm { realm ->
        realm.query<GameStateBlobEntity>("id == $0", GameStateBlobEntity.SLOT_ID)
            .first().find()?.currentDate
    }

    /** Idem para a divisão. */
    fun currentDivision(): Division? = withRealm { realm ->
        val raw = realm.query<GameStateBlobEntity>("id == $0", GameStateBlobEntity.SLOT_ID)
            .first().find()?.division ?: return@withRealm null
        runCatching { Division.valueOf(raw) }.getOrNull()
    }

    // ── Migração de save legado ─────────────────────────────────────────

    /**
     * Importa um eventual save antigo (SharedPreferences "cblol_scout_game" /
     * chave "game_state") para o Realm e apaga o registro legado.
     *
     * Roda na primeira execução pós-update e é idempotente: se já há save no
     * Realm OU não há nada nas prefs, sai sem fazer nada.
     */
    fun migrateLegacySaveIfNeeded() {
        // Já existe save no Realm → nada a fazer (Realm é a fonte de verdade).
        if (hasSave()) {
            // Mesmo assim, limpa as prefs legadas se ainda existirem, para não
            // deixarem ruído.
            context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                .edit().remove(LEGACY_KEY).apply()
            return
        }
        val prefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val legacy = prefs.getString(LEGACY_KEY, null) ?: return
        val migrated = runCatching {
            val raw = gson.fromJson(legacy, GameState::class.java) ?: return@runCatching null
            GameStateMigrator.migrate(raw)
        }.getOrNull()
        if (migrated != null) save(migrated)
        prefs.edit().remove(LEGACY_KEY).apply()
    }

    // ── Internals ────────────────────────────────────────────────────────

    /**
     * Abre um Realm, roda [block] e fecha — garantindo que o handle nunca
     * vaze mesmo em exceção.
     *
     * **Por que não `realm.use { }`?** O `Realm` do Realm Kotlin SDK não
     * implementa [java.io.Closeable] — tem `close()` mas não é o `Closeable`
     * do JDK que a função genérica `use` do Kotlin requer. Em vez de duplicar
     * `try/finally` em cada método público, encapsulamos aqui.
     */
    private inline fun <T> withRealm(block: (Realm) -> T): T {
        val realm = openRealm()
        try {
            return block(realm)
        } finally {
            realm.close()
        }
    }

    /**
     * Abre o Realm de save. Arquivo SEPARADO do Realm estático para isolar
     * schemas (dados de carreira evoluem com mais frequência que catálogo de
     * campeões/comps/sponsors).
     */
    private fun openRealm(): Realm {
        val config = RealmConfiguration.Builder(schema = setOf(GameStateBlobEntity::class))
            .name(DB_NAME)
            .encryptionKey(keyProvider.getOrCreateKey())
            // Como o payload JSON acomoda evolução do data class, não temos
            // schema fields para migrar. Mudanças na entidade Realm em si
            // (adicionar campos diretos) podem ser tratadas com migration
            // policy específica; por enquanto, em desenvolvimento, recriar
            // basta — o save é local, sem dado precioso de servidor.
            .deleteRealmIfMigrationNeeded()
            .build()
        return Realm.open(config)
    }

    companion object {
        private const val DB_NAME = "cblol_save.realm"

        /** SharedPreferences usado pela versão anterior do save. */
        private const val LEGACY_PREFS = "cblol_scout_game"
        private const val LEGACY_KEY = "game_state"
    }
}
