package com.cblol.scout.domain.datasource

import com.cblol.scout.data.Champion
import com.cblol.scout.data.Sponsor
import com.cblol.scout.data.TeamComposition

/**
 * Contrato de acesso aos **dados estáticos do jogo** — campeões, composições,
 * patrocínios e champion pools — que antes ficavam hardcoded em objetos Kotlin
 * (`ChampionRepository`, `CompositionRepository`, `SponsorCatalog`,
 * `ChampionPoolRepository`).
 *
 * Esses dados agora vivem num **banco Realm criptografado** (ver
 * [com.cblol.scout.data.realm.RealmStaticDataSource]). Esta interface é a
 * abstração que desacopla o resto do app da tecnologia de persistência:
 *
 *  - **Produção**: implementada por `RealmStaticDataSource`, lendo do Realm.
 *  - **Testes (JVM puro)**: implementada por um fake em memória
 *    (`InMemoryStaticDataSource` em `src/test`), pois o runtime nativo do Realm
 *    não roda fora do Android.
 *
 * **SOLID:**
 *  - **DIP**: os repositórios (`ChampionRepository` etc.) dependem desta
 *    abstração, não da implementação concreta. O Koin injeta a impl correta.
 *  - **ISP**: a interface expõe apenas leituras dos dados estáticos; seeding e
 *    criptografia ficam encapsulados na camada Realm.
 *  - **SRP**: só lê dados estáticos; não contém regras de jogo (análise de
 *    sinergia, geração de pool, etc. continuam nos repositórios/serviços).
 *
 * Todas as leituras são síncronas e baratas: a implementação Realm carrega os
 * dados uma vez e mantém em cache imutável na memória, espelhando o
 * comportamento `by lazy` que os objetos hardcoded tinham.
 */
interface StaticDataSource {

    // ── Campeões ────────────────────────────────────────────────────────

    /** Todos os campeões cadastrados. */
    fun allChampions(): List<Champion>

    /** Campeão por id exato, ou null se não existe. */
    fun championById(id: String): Champion?

    // ── Composições de time ─────────────────────────────────────────────

    /** Todas as composições (Tier S/A/B). */
    fun allCompositions(): List<TeamComposition>

    // ── Patrocínios ─────────────────────────────────────────────────────

    /** Catálogo completo de patrocínios disponíveis no jogo. */
    fun allSponsors(): List<Sponsor>

    // ── Champion pools assinatura (jogadores históricos) ────────────────

    /**
     * Pool fixo de um jogador histórico (signature picks), indexado pela chave
     * em minúsculas (id ou nome de jogo). Null se o jogador não tem pool fixo.
     */
    fun signaturePool(key: String): List<String>?

    /** Catálogo de mains "comuns" por role, para jogadores sem pool fixo. */
    fun rolePool(role: String): List<String>
}
