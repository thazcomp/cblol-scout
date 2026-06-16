package com.cblol.scout

import com.cblol.scout.util.SecondDivisionTeamsGenerator
import org.junit.Assert.*
import org.junit.Test

/**
 * Testes do [SecondDivisionTeamsGenerator] — geração procedural de times +
 * rosters da 2ª divisão (Circuito Desafiante).
 *
 * O gerador é JVM-puro (recebe um seed, devolve dados imutáveis). As partes
 * determinísticas (mesmo seed → mesma saída) são testadas por igualdade
 * estrutural; as partes probabilísticas (faixas de overall, idades) por
 * invariantes que valem em qualquer geração.
 */
class SecondDivisionTeamsGeneratorTest {

    @Test
    fun generate_produces8Teams() {
        val gen = SecondDivisionTeamsGenerator.generate(seed = 42L)
        assertEquals(SecondDivisionTeamsGenerator.TEAMS_COUNT, gen.teams.size)
        assertEquals(8, gen.teams.size)  // sanity, deve bater com a constante
    }

    @Test
    fun generate_eachTeamHas6Players_with5Starters() {
        val gen = SecondDivisionTeamsGenerator.generate(seed = 42L)
        gen.teams.forEach { team ->
            val roster = gen.players.filter { it.time_id == team.id }
            assertEquals("Time ${team.nome} deveria ter 6 jogadores", 6, roster.size)
            assertEquals("Time ${team.nome} deveria ter exatamente 5 titulares",
                5, roster.count { it.titular })
        }
    }

    @Test
    fun generate_eachTeamHasOneStarterPerRole() {
        val gen = SecondDivisionTeamsGenerator.generate(seed = 42L)
        val roles = listOf("TOP", "JNG", "MID", "ADC", "SUP")
        gen.teams.forEach { team ->
            val starters = gen.players.filter { it.time_id == team.id && it.titular }
            roles.forEach { role ->
                assertEquals(
                    "Time ${team.nome} deveria ter 1 titular em $role",
                    1, starters.count { it.role == role }
                )
            }
        }
    }

    @Test
    fun generate_isDeterministicWithSameSeed() {
        val a = SecondDivisionTeamsGenerator.generate(seed = 12345L)
        val b = SecondDivisionTeamsGenerator.generate(seed = 12345L)
        // Times: ids e nomes idênticos, mesma ordem.
        assertEquals(a.teams.map { it.id }, b.teams.map { it.id })
        assertEquals(a.teams.map { it.nome }, b.teams.map { it.nome })
        // Jogadores: mesma quantidade, mesmos ids, mesmos overalls.
        assertEquals(a.players.size, b.players.size)
        assertEquals(a.players.map { it.id }, b.players.map { it.id })
        assertEquals(
            a.players.map { it.atributos_derivados.lane_phase },
            b.players.map { it.atributos_derivados.lane_phase }
        )
    }

    @Test
    fun generate_differsBetweenSeeds() {
        val a = SecondDivisionTeamsGenerator.generate(seed = 1L)
        val b = SecondDivisionTeamsGenerator.generate(seed = 2L)
        // Pelo menos algum nome deve diferir entre dois seeds — a chance de
        // colidir nos 8 nomes em 15 possíveis é ínfima.
        assertNotEquals(a.teams.map { it.nome }, b.teams.map { it.nome })
    }

    @Test
    fun teams_haveCorrectIdPrefix_andTierB() {
        val gen = SecondDivisionTeamsGenerator.generate(seed = 42L)
        gen.teams.forEach { team ->
            assertTrue(
                "Id deveria começar com '${SecondDivisionTeamsGenerator.ID_PREFIX}': ${team.id}",
                team.id.startsWith(SecondDivisionTeamsGenerator.ID_PREFIX)
            )
            assertEquals("Todos os times da 2ª divisão devem ser tier B", "B", team.tier_orcamento)
        }
    }

    @Test
    fun teams_haveUniqueIdsAndNames() {
        val gen = SecondDivisionTeamsGenerator.generate(seed = 42L)
        assertEquals(gen.teams.size, gen.teams.map { it.id }.toSet().size)
        assertEquals(gen.teams.size, gen.teams.map { it.nome }.toSet().size)
    }

    @Test
    fun players_haveUniqueIds() {
        val gen = SecondDivisionTeamsGenerator.generate(seed = 42L)
        val ids = gen.players.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun players_overallsInExpectedRange() {
        // Faixas declaradas: típico 58-68, diamante 70-75. Vamos verificar que
        // todos caem em [50, 80] (margem leve por causa dos jitters de atributo).
        val gen = SecondDivisionTeamsGenerator.generate(seed = 42L)
        gen.players.forEach { p ->
            val ov = p.atributos_derivados.let {
                (it.lane_phase + it.team_fight + it.criatividade + it.consistencia + it.clutch) / 5
            }
            assertTrue("Overall fora da faixa esperada: $ov (jogador ${p.nome_jogo})",
                ov in 50..80)
        }
    }

    @Test
    fun players_agesInExpectedRange() {
        val gen = SecondDivisionTeamsGenerator.generate(seed = 42L)
        gen.players.forEach { p ->
            assertTrue("Idade fora de 17..24: ${p.idade}", p.idade in 17..24)
        }
    }

    @Test
    fun players_havePositiveSalaries() {
        val gen = SecondDivisionTeamsGenerator.generate(seed = 42L)
        gen.players.forEach { p ->
            val salary = p.contrato.salario_mensal_estimado_brl ?: 0L
            assertTrue("Salário deve ser positivo (${p.nome_jogo}: $salary)", salary > 0)
        }
    }

    @Test
    fun players_pointToValidTeamIds() {
        val gen = SecondDivisionTeamsGenerator.generate(seed = 42L)
        val teamIds = gen.teams.map { it.id }.toSet()
        gen.players.forEach { p ->
            assertTrue(
                "Jogador ${p.nome_jogo} aponta para time inexistente: ${p.time_id}",
                p.time_id in teamIds
            )
        }
    }

    @Test
    fun players_haveChampionPool() {
        val gen = SecondDivisionTeamsGenerator.generate(seed = 42L)
        gen.players.forEach { p ->
            assertTrue("Jogador ${p.nome_jogo} sem champion pool", p.championPool.isNotEmpty())
        }
    }

    @Test
    fun players_haveUniqueGamerTags() {
        // Regressão: antes os gamertags eram sorteados COM reposição, repetindo
        // muito (vários "Trovão"/"Zenith" na mesma liga). Agora o distribuidor
        // único garante que cada um dos ~48 jogadores tem um nome_jogo distinto.
        val gen = SecondDivisionTeamsGenerator.generate(seed = 42L)
        val tags = gen.players.map { it.nome_jogo }
        assertEquals(
            "Gamertags repetidos detectados: " +
                tags.groupingBy { it }.eachCount().filter { it.value > 1 },
            tags.size, tags.toSet().size
        )
    }

    @Test
    fun players_haveUniqueRealNames() {
        // Mesmo raciocínio: nomes reais (nome + sobrenome) não devem repetir
        // dentro da mesma liga gerada.
        val gen = SecondDivisionTeamsGenerator.generate(seed = 42L)
        val names = gen.players.map { it.nome_real }
        assertEquals(
            "Nomes reais repetidos: " +
                names.groupingBy { it }.eachCount().filter { it.value > 1 },
            names.size, names.toSet().size
        )
    }

    @Test
    fun players_uniqueGamerTagsAcrossMultipleSeeds() {
        // A unicidade deve valer para qualquer seed, não só o 42.
        listOf(1L, 7L, 99L, 2024L, 555L).forEach { seed ->
            val gen = SecondDivisionTeamsGenerator.generate(seed = seed)
            val tags = gen.players.map { it.nome_jogo }
            assertEquals("Tags repetidos no seed $seed", tags.size, tags.toSet().size)
        }
    }
}
