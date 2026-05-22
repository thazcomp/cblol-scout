package com.cblol.scout.util

import com.cblol.scout.data.Player
import com.cblol.scout.data.StaticData
import kotlin.random.Random

/**
 * Atribui champion pools (mains) a cada jogador.
 *
 * Como o JSON do CBLOL não traz champion pool por jogador, geramos pseudo-aleatoriamente
 * a partir do `player.id` (semente determinística) escolhendo 3-5 campeões da role.
 *
 * **Determinístico**: o mesmo `player.id` sempre gera o mesmo pool, então recarregar
 * o save não muda os mains do jogador.
 *
 * **Origem dos dados**: os catálogos de pools (signature por jogador histórico e
 * comuns por role) NÃO ficam mais hardcoded aqui — vivem no banco **Realm
 * criptografado** e são lidos via [StaticData]. Os dados de seed estão em
 * [com.cblol.scout.data.seed.ChampionPoolSeed]. Esta classe mantém apenas a
 * LÓGICA de atribuição/sorteio (que é regra de jogo, não dado).
 *
 * Uso:
 *   ```
 *   val playerWithPool = ChampionPoolRepository.attach(player)
 *   ```
 */
object ChampionPoolRepository {

    private const val MIN_POOL_SIZE = 3
    private const val MAX_POOL_SIZE = 5

    /**
     * Devolve uma cópia do [player] com [Player.championPool] preenchido.
     *
     * - Se o pool já estiver presente (vindo do JSON, por exemplo), retorna o player intocado.
     * - Se existe pool fixo em [SIGNATURE_POOLS] para o id (case-insensitive), usa ele.
     * - Caso contrário, sorteia 3-5 campeões do catálogo da role do jogador.
     *
     * O sorteio é determinístico via `Random(player.id.hashCode())`, garantindo que
     * o mesmo jogador sempre tenha o mesmo pool em saves diferentes.
     */
    fun attach(player: Player): Player {
        // Gson não respeita default values do Kotlin — quando o JSON não tem o
        // campo, championPool chega como null em vez de emptyList(). Tratamos
        // ambos os casos como "sem pool atribuído ainda".
        @Suppress("SENSELESS_COMPARISON")
        val currentPool: List<String>? = player.championPool
        if (currentPool != null && currentPool.isNotEmpty()) return player

        val signature = StaticData.source.signaturePool(player.id.lowercase())
                     ?: StaticData.source.signaturePool(player.nome_jogo.lowercase())
        val pool = signature ?: generatePool(player)

        return player.copy(championPool = pool)
    }

    /** Versão para coleções: aplica [attach] em cada item. */
    fun attachAll(players: List<Player>): List<Player> = players.map { attach(it) }

    /**
     * Gera um pool determinístico baseado no id do jogador.
     *
     * O tamanho do pool varia entre [MIN_POOL_SIZE] e [MAX_POOL_SIZE] conforme
     * a consistência do jogador (jogadores mais consistentes têm pool menor e
     * mais focado; jogadores mais versáteis têm pool maior).
     */
    private fun generatePool(player: Player): List<String> {
        val catalog = StaticData.source.rolePool(player.role)
        if (catalog.isEmpty()) return emptyList()
        val seed    = player.id.hashCode().toLong()
        val rng     = Random(seed)

        val poolSize = when {
            player.atributos_derivados.consistencia >= 85 -> MIN_POOL_SIZE       // focado
            player.atributos_derivados.criatividade >= 80 -> MAX_POOL_SIZE       // versátil
            else -> rng.nextInt(MIN_POOL_SIZE, MAX_POOL_SIZE + 1)
        }
        return catalog.shuffled(rng).take(poolSize)
    }

    /**
     * Conta quantos jogadores do roster pickaram um dos seus mains.
     * Usado pelo motor de simulação para somar bônus de pool.
     */
    fun countMainsPicked(roster: List<Player>, picks: List<String>): Int {
        val picksLower = picks.map { it.lowercase() }.toSet()
        return roster.count { player ->
            poolOf(player).any { main -> main.lowercase() in picksLower }
        }
    }

    /**
     * Retorna pares (jogador, campeão-pickado-do-pool) para o roster.
     * Útil para mostrar no dialog de pré-simulação quem está jogando no main.
     */
    fun playersOnTheirMains(roster: List<Player>, picks: List<String>): List<Pair<Player, String>> {
        val picksLower = picks.associateBy { it.lowercase() }
        return roster.mapNotNull { player ->
            val mainPicked = poolOf(player).firstOrNull { it.lowercase() in picksLower.keys }
            if (mainPicked != null) player to picksLower[mainPicked.lowercase()]!! else null
        }
    }

    /**
     * Devolve o champion pool do jogador, tratando `null` (Gson) e vazio como "nenhum".
     * Toda leitura externa deve usar este helper para evitar NPE.
     */
    private fun poolOf(player: Player): List<String> {
        @Suppress("SENSELESS_COMPARISON")
        val raw: List<String>? = player.championPool
        return raw ?: emptyList()
    }
}
