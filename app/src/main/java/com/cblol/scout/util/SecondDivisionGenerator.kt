package com.cblol.scout.util

import com.cblol.scout.data.AtributosDeriv
import com.cblol.scout.data.Contrato
import com.cblol.scout.data.Player
import com.cblol.scout.data.StatsBrutas
import kotlin.random.Random

/**
 * Gerador procedural de jogadores da **2ª divisão** (Circuito Desafiante).
 *
 * Esses jogadores aparecem no [com.cblol.scout.game.GameRepository.marketRoster]
 * misturados com jogadores das outras orgs da 1ª divisão. Servem como alternativa
 * mais barata e mais jovem — geralmente com overall 55-72 (vs 70-85 dos titulares
 * de 1ª divisão), mas com potencial em alguns casos.
 *
 * **Características típicas:**
 *  - Idade 17-22 anos (mais jovens que os profissionais)
 *  - Overall 55-72 (com alguns "diamantes brutos" 72-76 raros)
 *  - Salário R$ 8k-25k/mês (vs R$ 30k-200k da 1ª divisão)
 *  - `time_id = "FREE_AGENT_CD"` para identificá-los visualmente
 *  - Champion pool típico de soloQ ranqueado, não de competitivo formal
 *
 * **Determinismo**: a geração usa `Random.Default` mas é cacheada em [generate]
 * para que o mesmo conjunto apareça em todas as chamadas durante a sessão do
 * app — assim o jogador vê o mesmo mercado se reabrir a tela em sequência.
 * Após reiniciar o app, novos jogadores são sorteados (simulando rotatividade).
 *
 * SOLID:
 *  - **SRP**: apenas gera; não filtra, não persiste. O Repository plugga isso.
 *  - **OCP**: novos pools de nomes/sobrenomes/champion pools podem ser adicionados
 *    nas listas sem mexer na lógica.
 *  - **DIP**: depende só dos data classes do modelo. Sem Android, JVM-puro.
 */
object SecondDivisionGenerator {

    /** ID virtual usado como `time_id` desses jogadores. */
    const val SECOND_DIVISION_TEAM_ID = "FREE_AGENT_CD"

    /** Nome mostrado na coluna "time" do card. */
    const val SECOND_DIVISION_TEAM_NAME = "Circuito Desafiante"

    /** Quantos jogadores são gerados (distribuídos entre as 5 roles). */
    private const val PLAYERS_PER_ROLE = 6
    private val ROLES = listOf("TOP", "JNG", "MID", "ADC", "SUP")

    // Cache para evitar regerar a cada chamada
    @Volatile
    private var cached: List<Player>? = null

    /**
     * Retorna a lista de jogadores da 2ª divisão. Gera na primeira chamada e
     * cacheia para chamadas subsequentes na mesma sessão do app.
     */
    fun generate(): List<Player> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val rng = Random.Default
            // Distribuidores de nomes SEM reposição: garantem que cada jogador
            // gerado recebe um gamertag e um nome real únicos (o sorteio com
            // reposição anterior repetia nomes com frequência — problema do
            // aniversário). Ver [UniqueNamePool].
            val tagPool  = UniqueNamePool(GAMER_TAGS, rng)
            val namePool = UniqueRealNamePool(FIRST_NAMES, LAST_NAMES, rng)
            val list = ROLES.flatMap { role ->
                (1..PLAYERS_PER_ROLE).map { idx ->
                    buildPlayer(role, idx, rng, tagPool, namePool)
                }
            }
            cached = list
            return list
        }
    }

    /** Força regeração — útil para testes ou para mercado "atualizar" entre splits. */
    fun invalidate() {
        synchronized(this) { cached = null }
    }

    // ── Construção de um jogador ───────────────────────────────────────

    private fun buildPlayer(
        role: String,
        indexInRole: Int,
        rng: Random,
        tagPool: UniqueNamePool,
        namePool: UniqueRealNamePool
    ): Player {
        val gamerTag = tagPool.next()
        val realName = namePool.next()
        val id       = "cd_${role.lowercase()}_$indexInRole"
        val age      = rng.nextInt(17, 23)  // 17-22

        // Overall típico: 55-72, com 10% de chance de "diamante bruto" 72-76
        val isDiamond = rng.nextInt(100) < 10
        val targetOverall = if (isDiamond) rng.nextInt(72, 77) else rng.nextInt(55, 73)
        val attrs   = generateAttributes(targetOverall, rng)
        val stats   = generateStats(role, targetOverall, rng)
        val salary  = generateSalary(targetOverall, rng)
        val pool    = pickChampionPool(role, rng)
        val endDate = "2026-12-15"  // contrato livre — termina no fim do ano

        return Player(
            id            = id,
            nome_jogo     = gamerTag,
            nome_real     = realName,
            time_id       = SECOND_DIVISION_TEAM_ID,
            time_nome     = SECOND_DIVISION_TEAM_NAME,
            role          = role,
            titular       = false,
            idade         = age,
            nacionalidade = "BR",
            contrato      = Contrato(
                termino                       = endDate,
                valor_estimado_brl            = salary * 12,
                salario_mensal_estimado_brl   = salary,
                fonte_salario                 = "estimado"
            ),
            stats_brutas        = stats,
            atributos_derivados = attrs,
            championPool        = pool
        )
    }

    /**
     * Gera atributos derivados que somam a uma média próxima de `targetOverall`,
     * com pequena variação por atributo (±3) para parecer natural.
     */
    private fun generateAttributes(targetOverall: Int, rng: Random): AtributosDeriv {
        fun jitter() = targetOverall + rng.nextInt(-3, 4)  // -3 a +3
        return AtributosDeriv(
            lane_phase   = jitter().coerceIn(40, 85),
            team_fight   = jitter().coerceIn(40, 85),
            criatividade = jitter().coerceIn(40, 85),
            consistencia = jitter().coerceIn(40, 85),
            clutch       = jitter().coerceIn(40, 85)
        )
    }

    /**
     * Stats brutos coerentes com o overall. Como esses jogadores vieram de
     * ranqueado/CD, `jogos` é baixo (mais sample size pequeno) e os valores
     * têm mais variância que profissionais.
     */
    private fun generateStats(role: String, overall: Int, rng: Random): StatsBrutas {
        val ratingFactor = overall / 70.0  // base 1.0 quando overall = 70

        return StatsBrutas(
            jogos             = rng.nextInt(8, 28),
            kda               = (2.5 * ratingFactor + rng.nextDouble() * 1.5).coerceIn(1.0, 6.0),
            kp_pct            = (55.0 + rng.nextDouble() * 25.0).coerceIn(40.0, 85.0),
            cs_min            = when (role) {
                "SUP" -> rng.nextDouble() * 1.2 + 0.5
                "JNG" -> rng.nextDouble() * 1.5 + 4.5
                else  -> rng.nextDouble() * 1.5 + 7.0
            },
            gd15              = (rng.nextInt(-300, 400) * ratingFactor).toInt(),
            xpd15             = (rng.nextInt(-200, 350) * ratingFactor).toInt(),
            damage_share_pct  = when (role) {
                "ADC", "MID" -> 22.0 + rng.nextDouble() * 8.0
                "TOP"        -> 18.0 + rng.nextDouble() * 8.0
                "JNG"        -> 15.0 + rng.nextDouble() * 6.0
                else         ->  8.0 + rng.nextDouble() * 4.0  // SUP
            },
            vision_score_min  = when (role) {
                "SUP" -> 1.8 + rng.nextDouble() * 0.6
                "JNG" -> 1.2 + rng.nextDouble() * 0.5
                else  -> 0.7 + rng.nextDouble() * 0.4
            }
        )
    }

    /**
     * Salário em R$ mensais. Diamantes brutos (74+) pedem um pouco mais
     * — algumas orgs já estavam de olho neles. Jogadores 55-65 são bem baratos.
     */
    private fun generateSalary(overall: Int, rng: Random): Long {
        val base = when {
            overall >= 73 -> 22_000L
            overall >= 68 -> 16_000L
            overall >= 62 -> 12_000L
            else          ->  8_000L
        }
        val variance = rng.nextLong(-2_000L, 3_000L)
        return base + variance
    }

    private fun pickChampionPool(role: String, rng: Random): List<String> {
        val pool = SECOND_DIVISION_CHAMPION_POOLS[role] ?: emptyList()
        if (pool.isEmpty()) return emptyList()
        return pool.shuffled(rng).take(rng.nextInt(3, 5))
    }

    // ── Pools de nomes ─────────────────────────────────────────────────

    /**
     * 30 gamertags fictícios no estilo do CBLOL/CD — mistura de gírias gamer,
     * nomes curtos em inglês, e referências à cultura do LoL brasileiro.
     */
    private val GAMER_TAGS = listOf(
        "Trovão", "Zenith", "Kanji", "Vorth", "Naelo", "Praxx",
        "Sonic", "Frostt", "Lyric", "Cypher", "Drako", "Vexen",
        "Sword", "Hellion", "Krieg", "Solace", "Vyper", "Quantum",
        "Rage", "Nyx", "Onslaught", "Phantom", "Skyfall", "Mirage",
        "Tempest", "Wraith", "Echo", "Blitz", "Crisis", "Zone",
        "Pulse", "Raze", "Drift", "Ember", "Surge", "Talon",
        "Vandal", "Specter", "Riptide", "Kaze", "Volt", "Strix"
    )

    private val FIRST_NAMES = listOf(
        "Lucas", "Pedro", "Gabriel", "Matheus", "João", "Felipe",
        "Bruno", "Rafael", "Vinícius", "Daniel", "Henrique", "Thiago",
        "Gustavo", "Rodrigo", "Caio", "Eduardo", "Diego", "Igor",
        "André", "Leonardo", "Mateus", "Vitor", "Otávio", "Erick"
    )

    private val LAST_NAMES = listOf(
        "Silva", "Santos", "Oliveira", "Pereira", "Costa", "Rodrigues",
        "Almeida", "Souza", "Lima", "Carvalho", "Ferreira", "Martins",
        "Araújo", "Ribeiro", "Gomes", "Cardoso", "Cavalcanti", "Moreira"
    )

    /**
     * Champion pools típicos de jogadores da 2ª divisão por role. Geralmente
     * picks soloQ fortes e meta-picks consagrados (em vez de pocket picks
     * competitivos).
     */
    private val SECOND_DIVISION_CHAMPION_POOLS = mapOf(
        "TOP" to listOf("Aatrox", "Camille", "Fiora", "Gnar", "Jax", "Ornn",
                        "Renekton", "Sett", "Riven", "Gwen", "Olaf", "Darius"),
        "JNG" to listOf("Viego", "LeeSin", "Vi", "Sejuani", "Maokai", "Diana",
                        "Wukong", "KhaZix", "Graves", "Xinzhao", "Nidalee"),
        "MID" to listOf("Azir", "Ahri", "Akali", "Sylas", "Yone", "Yasuo",
                        "Orianna", "LeBlanc", "Veigar", "Vladimir", "Cassiopeia"),
        "ADC" to listOf("Caitlyn", "Jinx", "Aphelios", "Lucian", "Varus",
                        "Xayah", "Ezreal", "Kalista", "Zeri", "Ashe", "Senna"),
        "SUP" to listOf("Thresh", "Nautilus", "Leona", "Lulu", "Karma",
                        "Rakan", "Braum", "Pyke", "Yuumi", "Renata", "Alistar")
    )
}
