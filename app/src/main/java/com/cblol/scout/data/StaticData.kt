package com.cblol.scout.data

import com.cblol.scout.domain.datasource.StaticDataSource

/**
 * Ponto de acesso global ao [StaticDataSource] para código que vive em
 * `object` singletons (como `ChampionRepository`, `CompositionRepository`,
 * `SponsorCatalog`, `ChampionPoolRepository`).
 *
 * **Por que existe?** Esses repositórios são `object` Kotlin acessados
 * estaticamente em dezenas de call-sites. Convertê-los em classes injetadas via
 * Koin exigiria refatorar todo o app. Em vez disso, eles continuam como fachada
 * estável (mantendo a mesma API pública) e delegam a leitura de dados a este
 * holder, que é inicializado uma única vez no startup.
 *
 * **Inicialização:**
 *  - **Produção**: `CBLOLApp.onCreate()` chama [install] com a implementação
 *    Realm (`RealmStaticDataSource`), antes de qualquer Activity rodar.
 *  - **Testes (JVM puro)**: o `@Before` de cada teste chama [install] com um
 *    fake em memória (`InMemoryStaticDataSource`), de modo que os repositórios
 *    leiam dos seeds sem precisar do runtime nativo do Realm.
 *
 * **DIP:** os repositórios dependem da abstração [StaticDataSource], nunca da
 * implementação concreta. Este holder só faz a ponte entre o mundo `object` e a
 * injeção de dependência.
 *
 * Thread-safe: [source] é `@Volatile` e setado uma vez no startup, antes de
 * qualquer leitura concorrente.
 */
object StaticData {

    @Volatile
    private var dataSource: StaticDataSource? = null

    /** Instala a implementação concreta. Chamado no startup (app ou teste). */
    fun install(source: StaticDataSource) {
        this.dataSource = source
    }

    /**
     * Acesso ao DataSource instalado. Lança [IllegalStateException] com mensagem
     * clara se acessado antes de [install] — sinaliza erro de inicialização
     * (ex: teste esqueceu o `@Before`, ou app não chamou install no onCreate).
     */
    val source: StaticDataSource
        get() = dataSource ?: error(
            "StaticData não inicializado. Chame StaticData.install(...) no startup " +
                "(CBLOLApp.onCreate em produção; @Before nos testes)."
        )

    /** True se já há um DataSource instalado. Útil para testes/diagnóstico. */
    fun isInstalled(): Boolean = dataSource != null
}
