package com.cblol.scout.util

import com.cblol.scout.data.Player
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
 * **Curadoria por role**: cada role tem um catálogo de mains competitivos típicos.
 * Para grandes nomes do CBLOL (ex: Tinowns, Robo) há pools fixos com signature picks.
 *
 * Uso:
 *   ```
 *   val playerWithPool = ChampionPoolRepository.attach(player)
 *   ```
 */
object ChampionPoolRepository {

    /** Pools fixos para jogadores históricos do cenário brasileiro. */
    private val SIGNATURE_POOLS: Map<String, List<String>> = mapOf(
        // Top
        "robo"      to listOf("Aatrox", "Sett", "Renekton", "Camille", "Jax"),
        "burdol"    to listOf("Gangplank", "Camille", "Jayce", "Gnar", "Aatrox"),
        "wizer"     to listOf("Yone", "Jax", "Renekton", "Camille", "Sett"),
        // Jungle
        "ranger"    to listOf("Vi", "Viego", "LeeSin", "Xinzhao", "Kayn"),
        "cariok"    to listOf("Sejuani", "Vi", "Hecarim", "Diana", "Wukong"),
        "shini"     to listOf("Xinzhao", "Viego", "LeeSin", "Graves", "Kayn"),
        "trigo"     to listOf("Belveth", "Viego", "Kayn", "LeeSin", "Hecarim"),
        // Mid
        "tinowns"   to listOf("Azir", "Orianna", "Syndra", "Ahri", "Viktor"),
        "envy"      to listOf("Yone", "Yasuo", "Akali", "Sylas", "LeBlanc"),
        "mireu"     to listOf("Orianna", "Azir", "Ahri", "Corki", "Taliyah"),
        "kuri"      to listOf("Akali", "Yone", "Sylas", "Zed", "LeBlanc"),
        // ADC
        "titan"     to listOf("Kaisa", "Jinx", "Ezreal", "Aphelios", "Varus"),
        "brance"    to listOf("Aphelios", "Jinx", "Caitlyn", "Kaisa", "Zeri"),
        "stuart"    to listOf("Lucian", "Draven", "Samira", "Tristana", "Kaisa"),
        "scary"     to listOf("Jinx", "Ezreal", "Kaisa", "Varus", "Aphelios"),
        // Support
        "cronos"    to listOf("Thresh", "Nautilus", "Rakan", "Leona", "Karma"),
        "jojo"      to listOf("Leona", "Rakan", "Nautilus", "Thresh", "Alistar"),
        "frosty"    to listOf("Karma", "Lulu", "Nami", "Janna", "Milio"),
        "tay"       to listOf("Thresh", "Rakan", "Pyke", "Nautilus", "Leona")
    )

    /** Catálogo de mains "comuns" por role para jogadores sem pool fixo. */
    private val ROLE_POOLS: Map<String, List<String>> = mapOf(
        "TOP" to listOf(
            "Aatrox", "Camille", "Renekton", "Gnar", "Gangplank", "Jax", "Sett",
            "Fiora", "Jayce", "Yone", "Riven", "Mordekaiser", "Ornn", "Malphite",
            "Darius", "Garen", "Vladimir", "Akali", "Irelia", "Kennen"
        ),
        "JNG" to listOf(
            "Viego", "LeeSin", "Xinzhao", "Vi", "Kayn", "Diana", "Hecarim",
            "Belveth", "Graves", "Sejuani", "KhaZix", "Evelynn", "Wukong", "Nidalee",
            "Jarvaniv", "Lillia", "Ekko", "Elise", "Amumu", "Zac"
        ),
        "MID" to listOf(
            "Azir", "Orianna", "Ahri", "Syndra", "Yone", "Akali", "Sylas",
            "LeBlanc", "Zed", "Viktor", "Taliyah", "Corki", "Lissandra", "Cassiopeia",
            "Veigar", "Annie", "Galio", "TwistedFate", "Ryze", "Talon"
        ),
        "ADC" to listOf(
            "Jinx", "Kaisa", "Aphelios", "Ezreal", "Varus", "Caitlyn", "Zeri",
            "Lucian", "Samira", "Draven", "Jhin", "Tristana", "Vayne", "Xayah",
            "MissFortune", "Sivir", "Ashe", "Kalista", "Nilah"
        ),
        "SUP" to listOf(
            "Thresh", "Nautilus", "Leona", "Rakan", "Karma", "Lulu", "Milio",
            "Nami", "Pyke", "Blitzcrank", "Alistar", "Soraka", "Janna", "Renata",
            "Seraphine", "Yuumi", "Brand", "Zyra", "Bard"
        )
    )

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
        if (player.championPool.isNotEmpty()) return player

        val signature = SIGNATURE_POOLS[player.id.lowercase()]
                     ?: SIGNATURE_POOLS[player.nome_jogo.lowercase()]
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
        val catalog = ROLE_POOLS[player.role] ?: return emptyList()
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
            player.championPool.any { main -> main.lowercase() in picksLower }
        }
    }

    /**
     * Retorna pares (jogador, campeão-pickado-do-pool) para o roster.
     * Útil para mostrar no dialog de pré-simulação quem está jogando no main.
     */
    fun playersOnTheirMains(roster: List<Player>, picks: List<String>): List<Pair<Player, String>> {
        val picksLower = picks.associateBy { it.lowercase() }
        return roster.mapNotNull { player ->
            val mainPicked = player.championPool.firstOrNull { it.lowercase() in picksLower.keys }
            if (mainPicked != null) player to picksLower[mainPicked.lowercase()]!! else null
        }
    }
}
