package com.cblol.scout.data.realm

import android.content.Context
import com.cblol.scout.data.Champion
import com.cblol.scout.data.Sponsor
import com.cblol.scout.data.TeamComposition
import com.cblol.scout.data.seed.ChampionPoolSeed
import com.cblol.scout.data.seed.ChampionSeed
import com.cblol.scout.data.seed.CompositionSeed
import com.cblol.scout.data.seed.SponsorSeed
import com.cblol.scout.domain.datasource.StaticDataSource
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.query

/**
 * Implementação [StaticDataSource] respaldada por um **banco Realm
 * criptografado** (AES-256). É a fonte de verdade dos dados estáticos do jogo
 * em produção.
 *
 * **Ciclo de vida:**
 *  1. No primeiro acesso, abre o Realm com a chave de 64 bytes do
 *     [RealmKeyProvider] (cifrada via Android Keystore).
 *  2. Se o banco está vazio (primeira execução / pós-instalação), executa o
 *     **seed** a partir dos objetos `*Seed` (única fonte hardcoded restante).
 *  3. Carrega tudo para caches imutáveis em memória, espelhando o comportamento
 *     `by lazy` que os antigos repositórios tinham — leituras subsequentes não
 *     tocam o disco.
 *
 * **Por que cache em memória?** Os dados estáticos são pequenos (~110 campeões,
 * 30 comps, 18 patrocínios) e lidos com altíssima frequência (cada análise de
 * pick & ban). Carregar uma vez e servir da memória mantém a performance
 * idêntica à dos objetos hardcoded, sem reabrir o Realm a cada chamada.
 *
 * **Thread-safety:** a inicialização é protegida por `synchronized`. Após
 * inicializado, os caches são listas/maps imutáveis — seguros para leitura
 * concorrente.
 *
 * **SOLID:**
 *  - **SRP**: abre o Realm, faz seed e expõe leituras. Regras de jogo ficam
 *    fora (nos repositórios/serviços que consomem esta interface).
 *  - **LSP**: substituível pelo fake em memória dos testes sem afetar callers.
 *  - **DIP**: implementa a abstração do domínio.
 */
class RealmStaticDataSource(
    private val context: Context,
    private val keyProvider: RealmKeyProvider = RealmKeyProvider(context)
) : StaticDataSource {

    @Volatile private var initialized = false

    private lateinit var championsCache: List<Champion>
    private lateinit var championsById: Map<String, Champion>
    private lateinit var compositionsCache: List<TeamComposition>
    private lateinit var sponsorsCache: List<Sponsor>
    private lateinit var signaturePoolsCache: Map<String, List<String>>
    private lateinit var rolePoolsCache: Map<String, List<String>>

    // ── Inicialização / seeding ─────────────────────────────────────────

    private fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val realm = openRealm()
            try {
                seedIfEmpty(realm)
                loadCaches(realm)
            } finally {
                realm.close()
            }
            initialized = true
        }
    }

    private fun openRealm(): Realm {
        val config = RealmConfiguration.Builder(
            schema = setOf(
                ChampionEntity::class,
                CompositionEntity::class,
                SponsorEntity::class,
                ChampionPoolEntity::class
            )
        )
            .name(DB_NAME)
            .encryptionKey(keyProvider.getOrCreateKey())
            // Dados estáticos são versionados pelo app; em mudança de schema
            // simplesmente recriamos e re-semeamos (não há dado de usuário aqui).
            .deleteRealmIfMigrationNeeded()
            .build()
        return Realm.open(config)
    }

    /** Popula o Realm a partir dos seeds caso ainda esteja vazio. */
    private fun seedIfEmpty(realm: Realm) {
        val alreadySeeded = realm.query<ChampionEntity>().count().find() > 0L
        if (alreadySeeded) return

        realm.writeBlocking {
            ChampionSeed.all().forEach { copyToRealm(ChampionEntity.fromDomain(it)) }
            CompositionSeed.all().forEach { copyToRealm(CompositionEntity.fromDomain(it)) }
            SponsorSeed.all().forEach { copyToRealm(SponsorEntity.fromDomain(it)) }
            ChampionPoolSeed.signaturePools.forEach { (key, champs) ->
                copyToRealm(ChampionPoolEntity.signature(key, champs))
            }
            ChampionPoolSeed.rolePools.forEach { (role, champs) ->
                copyToRealm(ChampionPoolEntity.role(role, champs))
            }
        }
    }

    /** Carrega todas as entidades para caches imutáveis em memória. */
    private fun loadCaches(realm: Realm) {
        championsCache = realm.query<ChampionEntity>().find().map { it.toDomain() }
        championsById = championsCache.associateBy { it.id }
        compositionsCache = realm.query<CompositionEntity>().find().map { it.toDomain() }
        sponsorsCache = realm.query<SponsorEntity>().find().map { it.toDomain() }

        val pools = realm.query<ChampionPoolEntity>().find()
        signaturePoolsCache = pools
            .filter { it.kind == ChampionPoolEntity.KIND_SIGNATURE }
            .associate { it.key to it.champions.toList() }
        rolePoolsCache = pools
            .filter { it.kind == ChampionPoolEntity.KIND_ROLE }
            .associate { it.key to it.champions.toList() }
    }

    // ── StaticDataSource ────────────────────────────────────────────────

    override fun allChampions(): List<Champion> {
        ensureInitialized(); return championsCache
    }

    override fun championById(id: String): Champion? {
        ensureInitialized(); return championsById[id]
    }

    override fun allCompositions(): List<TeamComposition> {
        ensureInitialized(); return compositionsCache
    }

    override fun allSponsors(): List<Sponsor> {
        ensureInitialized(); return sponsorsCache
    }

    override fun signaturePool(key: String): List<String>? {
        ensureInitialized(); return signaturePoolsCache[key]
    }

    override fun rolePool(role: String): List<String> {
        ensureInitialized(); return rolePoolsCache[role] ?: emptyList()
    }

    companion object {
        private const val DB_NAME = "cblol_static.realm"
    }
}
