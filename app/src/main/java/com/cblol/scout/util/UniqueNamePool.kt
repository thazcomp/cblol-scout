package com.cblol.scout.util

import kotlin.random.Random

/**
 * Distribuidor de nomes (gamertags) **sem reposição**.
 *
 * **Problema que resolve:** sortear `lista.random(rng)` independentemente para
 * cada jogador repete nomes com altíssima frequência (problema do aniversário).
 * Com 30 jogadores sorteando de 30 nomes, a chance de TODOS serem únicos é
 * praticamente nula. O resultado eram vários "Trovão", "Zenith" etc. no mesmo
 * mercado/liga.
 *
 * **Como funciona:** embaralha a lista uma vez e entrega os nomes em sequência
 * via [next]. Cada chamada devolve um nome diferente. Se o número de jogadores
 * exceder o tamanho da lista (ex: 48 jogadores para 42 gamertags), os nomes
 * extras recebem um **sufixo numérico** (`"Trovão"`, depois `"Trovão 2"`,
 * `"Trovão 3"`...) garantindo unicidade absoluta sem nunca lançar exceção.
 *
 * Não é thread-safe por si só; os geradores que o usam já chamam dentro de
 * blocos sincronizados / uso single-thread na criação da carreira.
 *
 * SOLID:
 *  - **SRP**: só distribui nomes únicos. Não conhece Player nem regras de jogo.
 *  - **OCP**: serve qualquer lista de strings (gamertags, nomes de orgs, etc.).
 */
class UniqueNamePool(source: List<String>, rng: Random) {

    private val shuffled = source.shuffled(rng)
    private var cursor = 0

    /**
     * Devolve o próximo nome único. Enquanto houver nomes no baralho
     * embaralhado, devolve um deles; ao esgotar, recomeça do início com sufixo
     * numérico crescente (`Nome 2`, `Nome 3`, ...), garantindo que nunca repete.
     */
    fun next(): String {
        if (shuffled.isEmpty()) {
            // Defesa: lista vazia não deveria acontecer, mas evita divisão por zero.
            cursor++
            return "Jogador $cursor"
        }
        val cycle = cursor / shuffled.size       // 0 na 1ª passada, 1 na 2ª, ...
        val index = cursor % shuffled.size
        cursor++
        val base = shuffled[index]
        return if (cycle == 0) base else "$base ${cycle + 1}"
    }
}

/**
 * Distribuidor de **nomes reais** (primeiro + sobrenome) sem repetir a
 * combinação completa.
 *
 * Combina um pool de primeiros nomes com um de sobrenomes. Como o produto
 * cartesiano é grande (ex: 24 × 18 = 432 combinações), embaralha as combinações
 * possíveis e entrega uma por vez — repetição de "Lucas Silva" duas vezes na
 * mesma liga praticamente não ocorre. Se em algum cenário extremo todas as
 * combinações forem usadas, cai num sufixo numérico via [UniqueNamePool].
 */
class UniqueRealNamePool(
    firstNames: List<String>,
    lastNames: List<String>,
    rng: Random
) {
    // Gera o produto cartesiano "Nome Sobrenome", embaralha e distribui único.
    private val combos: UniqueNamePool = UniqueNamePool(
        firstNames.flatMap { first -> lastNames.map { last -> "$first $last" } },
        rng
    )

    fun next(): String = combos.next()
}
