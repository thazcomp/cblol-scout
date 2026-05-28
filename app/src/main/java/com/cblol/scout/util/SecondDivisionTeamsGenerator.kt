package com.cblol.scout.util

import com.cblol.scout.data.AtributosDeriv
import com.cblol.scout.data.Contrato
import com.cblol.scout.data.Player
import com.cblol.scout.data.StatsBrutas
import com.cblol.scout.data.Team
import kotlin.random.Random

/**
 * Gerador procedural de **times da 2ª divisão** (Circuito Desafiante) com seus
 * próprios rosters. Diferente do [SecondDivisionGenerator] (que cria jogadores
 * soltos como "free agents" da 2ª divisão para o mercado da 1ª), aqui montamos
 * 8 organizações COMPETIDORAS — cada uma com elenco completo (5 titulares +
 * reservas) — para o modo de carreira que começa na 2ª divisão.
 *
 * **Por que procedural e não JSON estático?** Times reais do CD mudam todo
 * split e ter dados oficiais por trás seria pesado (e ficaria datado em
 * semanas). Geração procedural com nomes genéricos respeita IP de orgs reais
 * e mantém o universo do jogo coerente sem manutenção.
 *
 * **Determinismo da carreira:** os times são gerados uma vez na criação da
 * carreira e PERSISTIDOS no [com.cblol.scout.data.GameState.secondDivisionTeams]
 * (e os jogadores deles em `secondDivisionPlayers`). Isso garante que o split
 * da 2ª divisão seja consistente entre sessões.
 *
 * **Características dos times (vs 1ª divisão):**
 *  - 8 times com nomes fictícios estilo "Aurora E-sports", "Phoenix Gaming"
 *  - Todos tier B (orçamento baixo) — pressão financeira é o desafio do modo
 *  - Roster overall médio 58-68 (vs 70-82 da 1ª divisão)
 *  - 5 titulares + 1-2 reservas por time
 *
 * SOLID:
 *  - **SRP**: só gera times+rosters. Persistência fica com o
 *    [com.cblol.scout.game.GameRepository]; calendário com o
 *    [com.cblol.scout.game.ScheduleGenerator].
 *  - **OCP**: novos pools de nomes/lemas/cores entram nas listas sem mexer na
 *    lógica.
 *  - **DIP**: JVM-puro, depende só dos data classes. Sem Android.
 */
object SecondDivisionTeamsGenerator {

    /** Quantidade de times da 2ª divisão (mesmo formato da 1ª: 8 = round-robin duplo). */
    const val TEAMS_COUNT = 8

    /** Prefixo dos ids dos times da 2ª divisão (ex: "cd2_aurora", "cd2_phoenix"). */
    const val ID_PREFIX = "cd2_"

    /** Quantos jogadores cada time tem (5 titulares + 1 reserva fixo). */
    private const val PLAYERS_PER_TEAM = 6

    private val ROLES = listOf("TOP", "JNG", "MID", "ADC", "SUP")

    /**
     * Resultado completo da geração: times + todos os jogadores deles. Os dois
     * são gerados juntos para garantir consistência (jogadores apontam para
     * times que existem).
     */
    data class Generated(
        val teams: List<Team>,
        val players: List<Player>
    )

    /**
     * Gera 8 times + rosters completos. A geração é determinística por seed:
     * passar o mesmo seed sempre produz o mesmo bracket (útil para testes), e
     * usar seed aleatório (default) garante variação entre carreiras.
     */
    fun generate(seed: Long = System.currentTimeMillis()): Generated {
        val rng = Random(seed)
        val picks = TEAM_NAMES.shuffled(rng).take(TEAMS_COUNT)
        val teams = picks.map { name ->
            Team(
                id = "$ID_PREFIX${name.lowercase().replace(Regex("[^a-z0-9]"), "")}",
                nome = name,
                // Todos os times da 2ª divisão são tier B — orçamento apertado é
                // parte da identidade do modo "começar de baixo".
                tier_orcamento = "B"
            )
        }
        val players = teams.flatMap { team -> generateRoster(team, rng) }
        return Generated(teams = teams, players = players)
    }

    // ── Construção de um roster ─────────────────────────────────────────

    private fun generateRoster(team: Team, rng: Random): List<Player> {
        val roster = mutableListOf<Player>()
        // 1 titular por role (5).
        ROLES.forEachIndexed { idx, role ->
            roster += buildPlayer(team, role, indexInRole = idx, titular = true, rng = rng)
        }
        // 1 reserva sorteado em uma role aleatória.
        val benchRole = ROLES.random(rng)
        roster += buildPlayer(team, benchRole, indexInRole = 99, titular = false, rng = rng)
        return roster
    }

    private fun buildPlayer(
        team: Team,
        role: String,
        indexInRole: Int,
        titular: Boolean,
        rng: Random
    ): Player {
        val gamerTag = pickGamerTag(rng)
        val realName = pickRealName(rng)
        val id       = "${team.id}_${role.lowercase()}_$indexInRole"
        val age      = rng.nextInt(17, 25)

        // Overall típico do CD: 58-68 (médio), com 8% de "diamante" 70-75.
        val isDiamond = rng.nextInt(100) < 8
        val targetOverall = if (isDiamond) rng.nextInt(70, 76) else rng.nextInt(58, 69)
        val attrs   = generateAttributes(targetOverall, rng)
        val stats   = generateStats(role, targetOverall, rng)
        val salary  = generateSalary(targetOverall, rng)
        val pool    = pickChampionPool(role, rng)

        return Player(
            id            = id,
            nome_jogo     = gamerTag,
            nome_real     = realName,
            time_id       = team.id,
            time_nome     = team.nome,
            role          = role,
            titular       = titular,
            idade         = age,
            nacionalidade = "BR",
            contrato      = Contrato(
                termino                       = "2026-12-15",
                valor_estimado_brl            = salary * 12,
                salario_mensal_estimado_brl   = salary,
                fonte_salario                 = "estimado"
            ),
            stats_brutas        = stats,
            atributos_derivados = attrs,
            championPool        = pool
        )
    }

    private fun generateAttributes(targetOverall: Int, rng: Random): AtributosDeriv {
        fun jitter() = targetOverall + rng.nextInt(-3, 4)
        return AtributosDeriv(
            lane_phase   = jitter().coerceIn(40, 80),
            team_fight   = jitter().coerceIn(40, 80),
            criatividade = jitter().coerceIn(40, 80),
            consistencia = jitter().coerceIn(40, 80),
            clutch       = jitter().coerceIn(40, 80)
        )
    }

    private fun generateStats(role: String, overall: Int, rng: Random): StatsBrutas {
        val ratingFactor = overall / 70.0
        return StatsBrutas(
            jogos             = rng.nextInt(10, 26),
            kda               = (2.2 * ratingFactor + rng.nextDouble() * 1.3).coerceIn(1.0, 5.5),
            kp_pct            = (52.0 + rng.nextDouble() * 22.0).coerceIn(38.0, 80.0),
            cs_min            = when (role) {
                "SUP" -> rng.nextDouble() * 1.0 + 0.5
                "JNG" -> rng.nextDouble() * 1.3 + 4.3
                else  -> rng.nextDouble() * 1.4 + 6.8
            },
            gd15              = (rng.nextInt(-400, 350) * ratingFactor).toInt(),
            xpd15             = (rng.nextInt(-300, 300) * ratingFactor).toInt(),
            damage_share_pct  = when (role) {
                "ADC", "MID" -> 21.0 + rng.nextDouble() * 7.0
                "TOP"        -> 17.0 + rng.nextDouble() * 7.0
                "JNG"        -> 14.0 + rng.nextDouble() * 6.0
                else         ->  8.0 + rng.nextDouble() * 4.0
            },
            vision_score_min  = when (role) {
                "SUP" -> 1.6 + rng.nextDouble() * 0.6
                "JNG" -> 1.1 + rng.nextDouble() * 0.5
                else  -> 0.6 + rng.nextDouble() * 0.4
            }
        )
    }

    /**
     * Salários do CD são modestos — uma das pressões financeiras do modo é que
     * mesmo com folha baixa, o orçamento inicial pequeno faz cada R$ contar.
     */
    private fun generateSalary(overall: Int, rng: Random): Long {
        val base = when {
            overall >= 72 -> 18_000L
            overall >= 66 -> 12_000L
            overall >= 60 -> 8_000L
            else          -> 6_000L
        }
        val variance = rng.nextLong(-1_500L, 2_500L)
        return base + variance
    }

    private fun pickChampionPool(role: String, rng: Random): List<String> {
        val pool = SECOND_DIV_POOLS[role] ?: return emptyList()
        return pool.shuffled(rng).take(rng.nextInt(3, 5))
    }

    private fun pickGamerTag(rng: Random): String = GAMER_TAGS.random(rng)
    private fun pickRealName(rng: Random): String =
        "${FIRST_NAMES.random(rng)} ${LAST_NAMES.random(rng)}"

    // ── Pools de nomes ──────────────────────────────────────────────────

    /**
     * Nomes fictícios de organizações estilo CD/access. NÃO inclui marcas reais
     * (LOUD, paiN, FURIA etc.) para não confundir com a 1ª divisão e para
     * respeitar IP. O catálogo tem mais que 8 para sortear variação entre
     * carreiras — cada nova carreira na 2ª divisão vê times diferentes.
     */
    private val TEAM_NAMES = listOf(
        "Aurora E-sports", "Phoenix Gaming", "Lynx Athletics",
        "Nimbus Club", "Vortex Pro", "Eclipse Squad",
        "Helix E-sports", "Quasar Gaming", "Onyx Athletics",
        "Cobra Club", "Mantis Pro", "Nova Squad",
        "Titans CR", "Apex E-sports", "Zenith Gaming"
    )

    private val GAMER_TAGS = listOf(
        "Trovão", "Zenith", "Kanji", "Vorth", "Naelo", "Praxx",
        "Sonic", "Frostt", "Lyric", "Cypher", "Drako", "Vexen",
        "Sword", "Hellion", "Krieg", "Solace", "Vyper", "Quantum",
        "Rage", "Nyx", "Onslaught", "Phantom", "Skyfall", "Mirage",
        "Tempest", "Wraith", "Echo", "Blitz", "Crisis", "Zone",
        "Aether", "Bolt", "Orion", "Helio", "Lumen", "Saber",
        "Comet", "Atlas", "Sigma", "Reaper", "Vesper", "Halo"
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

    private val SECOND_DIV_POOLS = mapOf(
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
