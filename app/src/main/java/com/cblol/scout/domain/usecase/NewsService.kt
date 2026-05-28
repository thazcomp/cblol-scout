package com.cblol.scout.domain.usecase

import com.cblol.scout.data.GameState
import com.cblol.scout.data.NewsCategory
import com.cblol.scout.data.NewsItem
import kotlin.random.Random

/**
 * Serviço do **feed de notícias** — transforma eventos do jogo em manchetes
 * editorializadas, como se portais e canais de esports cobrissem a carreira.
 *
 * Cada método `report*` recebe os dados de um evento (resultado de partida,
 * promoção da base, transferência, etc.) e publica um [NewsItem] no
 * [GameState.news], escolhendo uma fonte fictícia, uma manchete variada (para
 * não repetir o mesmo texto) e uma prioridade editorial.
 *
 * **Por que separado do log (`GameRepository.log`)?** O log é o registro cru e
 * técnico ("Vitória 2-0 contra LOUD"). A notícia é a leitura jornalística do
 * mesmo fato ("Atropelo! Time do gerente não toma sustos e vence a LOUD"). São
 * dois níveis de abstração diferentes — manter separado respeita SRP e permite
 * que o tom e a variação das notícias evoluam sem mexer no log.
 *
 * **SOLID:**
 *  - **SRP**: só compõe e publica notícias. Quem detecta os eventos é o
 *    [com.cblol.scout.game.GameEngine] / Activities, que chamam os `report*`.
 *  - **OCP**: novas editorias entram como novos `report*` + entradas nos pools
 *    de manchetes, sem tocar nos existentes.
 *  - **DIP**: JVM-puro; opera sobre [GameState]. Sem Android, 100% testável.
 */
object NewsService {

    /** Máximo de notícias guardadas no feed (evita inflar o save). */
    const val MAX_FEED_SIZE = 40

    // Prioridades editoriais (quanto maior, mais "manchete de capa").
    private const val PRIORITY_HEADLINE = 100  // título, virada épica, joia revelada
    private const val PRIORITY_HIGH     = 70   // vitória/derrota importante, transferência cara
    private const val PRIORITY_MEDIUM   = 40   // resultado comum, promoção, patrocínio
    private const val PRIORITY_LOW      = 20   // notas de bastidor, finanças rotineiras

    // ── Acesso ──────────────────────────────────────────────────────────

    /** Lista (não-nula) de notícias, mais recentes/relevantes primeiro. */
    fun feed(state: GameState): List<NewsItem> =
        (state.news ?: emptyList()).sortedWith(
            compareByDescending<NewsItem> { it.date }.thenByDescending { it.priority }
        )

    /** Notícia de maior destaque (para o card do Hub), ou null se o feed está vazio. */
    fun latestHeadline(state: GameState): NewsItem? = feed(state).firstOrNull()

    /** Publica uma notícia no feed, mantendo o limite de tamanho. */
    private fun publish(state: GameState, item: NewsItem) {
        val list = state.news ?: mutableListOf<NewsItem>().also { state.news = it }
        list.add(0, item)
        while (list.size > MAX_FEED_SIZE) list.removeAt(list.size - 1)
    }

    private fun newId(state: GameState): String =
        "news_${state.currentDate}_${Random.nextInt(100000, 999999)}"

    // ── Partidas ────────────────────────────────────────────────────────

    /**
     * Publica a cobertura de uma partida do time do gerente.
     *
     * Editorializa conforme o contexto: goleada (2-0), virada/jogo apertado
     * (2-1), zebra (vencer favorito) ou tropeço (perder pra azarão). Como o
     * serviço não conhece a força dos times, recebe [wasUpset] do chamador
     * (que pode comparar overalls), mantendo o serviço desacoplado.
     *
     * @param managerTeamName nome do time do gerente
     * @param opponentName nome do adversário
     * @param managerWon se o time do gerente venceu a série
     * @param managerMaps mapas que o time do gerente fez
     * @param opponentMaps mapas do adversário
     * @param wasUpset true se o resultado foi uma surpresa (favorito caiu)
     */
    fun reportMatchResult(
        state: GameState,
        managerTeamName: String,
        opponentName: String,
        managerWon: Boolean,
        managerMaps: Int,
        opponentMaps: Int,
        wasUpset: Boolean = false
    ) {
        val sweep = (managerMaps == 2 && opponentMaps == 0) || (opponentMaps == 2 && managerMaps == 0)
        val score = "$managerMaps-$opponentMaps"

        val headline: String
        val priority: Int
        when {
            managerWon && wasUpset -> {
                headline = pick(
                    "Zebra no Rift! $managerTeamName surpreende e derruba o favorito $opponentName",
                    "Que zebra! $managerTeamName atropela as previsões e bate $opponentName",
                    "Ninguém esperava: $managerTeamName vence $opponentName e choca a liga"
                )
                priority = PRIORITY_HEADLINE
            }
            managerWon && sweep -> {
                headline = pick(
                    "Atropelo! $managerTeamName não toma sustos e despacha $opponentName por 2-0",
                    "Show de bola: $managerTeamName passeia e vence $opponentName sem dificuldades",
                    "$managerTeamName dá aula e bate $opponentName em dois mapas diretos"
                )
                priority = PRIORITY_HIGH
            }
            managerWon -> {
                headline = pick(
                    "$managerTeamName supera $opponentName em série pegada ($score)",
                    "No sufoco: $managerTeamName vence $opponentName por $score",
                    "$managerTeamName segura a pressão e bate $opponentName ($score)"
                )
                priority = PRIORITY_MEDIUM
            }
            !managerWon && wasUpset -> {
                headline = pick(
                    "Tropeço! $managerTeamName decepciona e perde para $opponentName",
                    "Resultado inesperado: $managerTeamName cai diante de $opponentName",
                    "$managerTeamName vacila e é surpreendido por $opponentName"
                )
                priority = PRIORITY_HIGH
            }
            sweep -> {
                headline = pick(
                    "Sem chances: $managerTeamName é atropelado por $opponentName (0-2)",
                    "Dia para esquecer: $opponentName domina e vence $managerTeamName por 2-0",
                    "$managerTeamName naufraga diante de $opponentName"
                )
                priority = PRIORITY_MEDIUM
            }
            else -> {
                headline = pick(
                    "$opponentName leva a melhor sobre $managerTeamName ($opponentMaps-$managerMaps)",
                    "$managerTeamName cai em série equilibrada contra $opponentName",
                    "Por pouco: $managerTeamName perde para $opponentName por $opponentMaps-$managerMaps"
                )
                priority = PRIORITY_MEDIUM
            }
        }

        val body = if (managerWon) {
            pick(
                "Os três pontos ficam em casa e a comissão técnica comemora o resultado.",
                "Resultado coloca o elenco em alta antes da próxima rodada.",
                "Torcida saiu satisfeita com a atuação da equipe."
            )
        } else {
            pick(
                "Comissão técnica promete revisar os erros nos próximos treinos.",
                "Derrota acende o sinal de alerta para a sequência do campeonato.",
                "Elenco terá que se recuperar rápido para a próxima rodada."
            )
        }

        val source = sourceFor(NewsCategory.MATCH)
        publish(state, NewsItem(
            id = newId(state), date = state.currentDate,
            source = source.first, sourceEmoji = source.second,
            headline = headline, body = body,
            category = NewsCategory.MATCH, priority = priority
        ))
    }

    // ── Marcos de jogadores ─────────────────────────────────────────────

    /**
     * Publica uma notícia sobre o destaque individual de um jogador (ex: melhor
     * atuação da rodada, KDA absurdo). Quem decide se houve marco é o chamador.
     *
     * @param highlight texto curto do feito (ex: "KDA de 12.0 contra a paiN")
     */
    fun reportPlayerMilestone(
        state: GameState,
        playerName: String,
        highlight: String
    ) {
        val headline = pick(
            "$playerName brilha e vira assunto: $highlight",
            "Atuação de gala: $playerName entrega $highlight",
            "$playerName rouba a cena com $highlight",
            "Show individual: $playerName protagoniza $highlight"
        )
        val body = pick(
            "Comunidade nas redes elege o jogador como destaque da rodada.",
            "Analistas rasgam elogios à atuação individual.",
            "Performance coloca o nome do jogador em evidência no cenário."
        )
        val source = sourceFor(NewsCategory.PLAYER)
        publish(state, NewsItem(
            id = newId(state), date = state.currentDate,
            source = source.first, sourceEmoji = source.second,
            headline = headline, body = body,
            category = NewsCategory.PLAYER, priority = PRIORITY_HIGH
        ))
    }

    // ── Transferências ──────────────────────────────────────────────────

    /** Publica a chegada de um reforço ao elenco do gerente. */
    fun reportSigning(
        state: GameState,
        playerName: String,
        teamName: String,
        feeBrl: Long
    ) {
        val expensive = feeBrl >= 300_000L
        val headline = if (expensive) pick(
            "Reforço de peso! $teamName acerta com $playerName",
            "$teamName abre o cofre e contrata $playerName",
            "Bomba no mercado: $playerName é o novo reforço do $teamName"
        ) else pick(
            "$teamName anuncia a contratação de $playerName",
            "$playerName é o novo nome do $teamName",
            "$teamName fecha com $playerName para reforçar o elenco"
        )
        val body = pick(
            "Negociação girou em torno de R$ ${"%,d".format(feeBrl)}.",
            "Jogador chega para somar e brigar por posição no time titular.",
            "Diretoria aposta no novo reforço para a sequência do split."
        )
        val source = sourceFor(NewsCategory.TRANSFER)
        publish(state, NewsItem(
            id = newId(state), date = state.currentDate,
            source = source.first, sourceEmoji = source.second,
            headline = headline, body = body,
            category = NewsCategory.TRANSFER,
            priority = if (expensive) PRIORITY_HIGH else PRIORITY_MEDIUM
        ))
    }

    /** Publica a saída de um jogador (venda/transferência para fora). */
    fun reportDeparture(
        state: GameState,
        playerName: String,
        fromTeamName: String,
        toTeamName: String,
        feeBrl: Long
    ) {
        val headline = pick(
            "$playerName deixa o $fromTeamName rumo ao $toTeamName",
            "Fim de ciclo: $playerName troca o $fromTeamName pelo $toTeamName",
            "$toTeamName acerta a contratação de $playerName, ex-$fromTeamName"
        )
        val body = pick(
            "Transferência avaliada em R$ ${"%,d".format(feeBrl)}.",
            "Saída reforça o caixa do clube para futuras movimentações.",
            "Torcida se divide sobre a negociação."
        )
        val source = sourceFor(NewsCategory.TRANSFER)
        publish(state, NewsItem(
            id = newId(state), date = state.currentDate,
            source = source.first, sourceEmoji = source.second,
            headline = headline, body = body,
            category = NewsCategory.TRANSFER, priority = PRIORITY_MEDIUM
        ))
    }

    // ── Categoria de base ───────────────────────────────────────────────

    /** Publica a promoção de uma joia da base ao elenco principal. */
    fun reportAcademyPromotion(
        state: GameState,
        playerName: String,
        teamName: String,
        overall: Int
    ) {
        val highEnd = overall >= 70
        val headline = if (highEnd) pick(
            "Joia revelada! $teamName promove $playerName da base ao time principal",
            "Nasce uma estrela: $playerName sobe da base do $teamName com status de promessa",
            "$teamName aposta em cria da base: $playerName é promovido ao elenco"
        ) else pick(
            "$teamName promove $playerName da categoria de base",
            "Cria da casa: $playerName sobe ao elenco principal do $teamName",
            "$playerName ganha chance no time principal do $teamName"
        )
        val body = pick(
            "Jovem chega ao profissional com a missão de conquistar espaço.",
            "Aposta da base movimenta a torcida, sempre carente de ídolos locais.",
            "Comissão técnica vê potencial de crescimento no jovem."
        )
        val source = sourceFor(NewsCategory.ACADEMY)
        publish(state, NewsItem(
            id = newId(state), date = state.currentDate,
            source = source.first, sourceEmoji = source.second,
            headline = headline, body = body,
            category = NewsCategory.ACADEMY,
            priority = if (highEnd) PRIORITY_HEADLINE else PRIORITY_MEDIUM
        ))
    }

    // ── Finanças ────────────────────────────────────────────────────────

    /** Publica a assinatura de um novo patrocínio. */
    fun reportSponsorship(
        state: GameState,
        sponsorName: String,
        teamName: String,
        weeklyAmount: Long
    ) {
        val headline = pick(
            "$teamName fecha patrocínio com $sponsorName",
            "Novo aporte: $sponsorName passa a estampar a camisa do $teamName",
            "$teamName e $sponsorName anunciam parceria comercial"
        )
        val body = pick(
            "Acordo injeta R$ ${"%,d".format(weeklyAmount)} por semana no caixa do clube.",
            "Parceria fortalece a estrutura financeira da organização.",
            "Diretoria comemora o reforço de receita."
        )
        val source = sourceFor(NewsCategory.FINANCE)
        publish(state, NewsItem(
            id = newId(state), date = state.currentDate,
            source = source.first, sourceEmoji = source.second,
            headline = headline, body = body,
            category = NewsCategory.FINANCE, priority = PRIORITY_LOW
        ))
    }

    /** Publica um alerta de crise financeira (caixa no vermelho). */
    fun reportFinancialCrisis(state: GameState, teamName: String) {
        val headline = pick(
            "Sinal de alerta: $teamName enfrenta aperto financeiro",
            "Crise no caixa? $teamName precisa equilibrar as contas",
            "$teamName vive momento delicado nas finanças"
        )
        val body = pick(
            "Especialistas apontam necessidade de vendas ou novos patrocínios.",
            "Diretoria estuda alternativas para reforçar o orçamento.",
            "Situação acende debate sobre a gestão financeira do clube."
        )
        val source = sourceFor(NewsCategory.FINANCE)
        publish(state, NewsItem(
            id = newId(state), date = state.currentDate,
            source = source.first, sourceEmoji = source.second,
            headline = headline, body = body,
            category = NewsCategory.FINANCE, priority = PRIORITY_HIGH
        ))
    }

    // ── Vestiário (química / moral) ─────────────────────────────────────

    /** Publica notícia sobre uma parceria forte formada no elenco. */
    fun reportStrongBond(state: GameState, playerA: String, playerB: String) {
        val headline = pick(
            "Dupla afiada: $playerA e $playerB viram referência de entrosamento",
            "$playerA e $playerB mostram sintonia dentro e fora do servidor",
            "Química em alta: $playerA e $playerB formam dupla de respeito"
        )
        val body = pick(
            "Sinergia da dupla tem rendido jogadas ensaiadas nas partidas.",
            "Entrosamento promete frutos na sequência do campeonato.",
            "Companheirismo melhora o ambiente do elenco."
        )
        val source = sourceFor(NewsCategory.LOCKER_ROOM)
        publish(state, NewsItem(
            id = newId(state), date = state.currentDate,
            source = source.first, sourceEmoji = source.second,
            headline = headline, body = body,
            category = NewsCategory.LOCKER_ROOM, priority = PRIORITY_LOW
        ))
    }

    /** Publica notícia sobre uma crise no vestiário (rivalidade tóxica). */
    fun reportLockerRoomCrisis(state: GameState, playerA: String, playerB: String) {
        val headline = pick(
            "Clima pesado: atrito entre $playerA e $playerB preocupa a comissão",
            "Bastidores: relação entre $playerA e $playerB azeda no elenco",
            "Treta interna? $playerA e $playerB vivem clima de tensão"
        )
        val body = pick(
            "Comissão técnica tenta apaziguar os ânimos no vestiário.",
            "Desentendimento pode atrapalhar o rendimento em quadra.",
            "Fontes internas relatam ambiente desgastado."
        )
        val source = sourceFor(NewsCategory.LOCKER_ROOM)
        publish(state, NewsItem(
            id = newId(state), date = state.currentDate,
            source = source.first, sourceEmoji = source.second,
            headline = headline, body = body,
            category = NewsCategory.LOCKER_ROOM, priority = PRIORITY_MEDIUM
        ))
    }

    /** Publica notícia sobre um pedido de transferência de um jogador insatisfeito. */
    fun reportTransferRequest(state: GameState, playerName: String, teamName: String) {
        val headline = pick(
            "Insatisfeito, $playerName pede para deixar o $teamName",
            "$playerName comunica ao $teamName desejo de sair",
            "Mal-estar: $playerName quer transferência do $teamName"
        )
        val body = pick(
            "Diretoria terá que decidir entre segurar o atleta ou negociá-lo.",
            "Pedido pega a comissão técnica de surpresa.",
            "Situação promete movimentar os bastidores do clube."
        )
        val source = sourceFor(NewsCategory.LOCKER_ROOM)
        publish(state, NewsItem(
            id = newId(state), date = state.currentDate,
            source = source.first, sourceEmoji = source.second,
            headline = headline, body = body,
            category = NewsCategory.LOCKER_ROOM, priority = PRIORITY_HIGH
        ))
    }

    // ── Tabela / classificação ──────────────────────────────────────────

    /** Publica notícia sobre a posição do time na tabela (liderança, zona de risco). */
    fun reportStandings(
        state: GameState,
        teamName: String,
        position: Int,
        totalTeams: Int
    ) {
        val headline: String
        val priority: Int
        when {
            position == 1 -> {
                headline = pick(
                    "Na ponta! $teamName assume a liderança do campeonato",
                    "$teamName dispara na tabela e lidera a competição",
                    "Líder isolado: $teamName chega ao topo da classificação"
                )
                priority = PRIORITY_HIGH
            }
            position <= 3 -> {
                headline = pick(
                    "$teamName figura no G3 e mira o topo da tabela",
                    "Entre os melhores: $teamName ocupa o ${position}º lugar",
                    "$teamName se firma no pelotão de frente do campeonato"
                )
                priority = PRIORITY_MEDIUM
            }
            position >= totalTeams - 1 -> {
                headline = pick(
                    "Zona de risco: $teamName afunda para o ${position}º lugar",
                    "Alerta máximo: $teamName ocupa o ${position}º lugar na tabela",
                    "$teamName precisa reagir para sair da parte de baixo"
                )
                priority = PRIORITY_HIGH
            }
            else -> {
                headline = pick(
                    "$teamName ocupa o ${position}º lugar na tabela",
                    "Meio de tabela: $teamName busca embalar no campeonato",
                    "$teamName mira reação para subir na classificação"
                )
                priority = PRIORITY_LOW
            }
        }
        val body = pick(
            "Faltam rodadas decisivas para definir o destino da equipe.",
            "Sequência do campeonato será determinante para os objetivos do time.",
            "Torcida acompanha a campanha com expectativa."
        )
        val source = sourceFor(NewsCategory.STANDINGS)
        publish(state, NewsItem(
            id = newId(state), date = state.currentDate,
            source = source.first, sourceEmoji = source.second,
            headline = headline, body = body,
            category = NewsCategory.STANDINGS, priority = priority
        ))
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** Sorteia uma das variações de texto, para o feed não ficar repetitivo. */
    private fun pick(vararg options: String): String = options.random()

    /**
     * Escolhe uma fonte (portal/canal) fictícia. Algumas fontes são mais
     * "especializadas" em certas editorias, dando coerência ao feed — mas
     * qualquer fonte pode cobrir qualquer assunto (como na vida real).
     */
    private fun sourceFor(category: NewsCategory): Pair<String, String> {
        val pool = when (category) {
            NewsCategory.MATCH       -> SOURCES_MATCH
            NewsCategory.TRANSFER    -> SOURCES_MARKET
            NewsCategory.FINANCE     -> SOURCES_MARKET
            NewsCategory.PLAYER      -> SOURCES_GENERAL
            NewsCategory.ACADEMY     -> SOURCES_GENERAL
            NewsCategory.LOCKER_ROOM -> SOURCES_GOSSIP
            NewsCategory.STANDINGS   -> SOURCES_MATCH
        }
        return pool.random()
    }

    // Portais/canais fictícios — nomes genéricos que não colidem com veículos
    // reais, mantendo a temática de cobertura de esports.
    private val SOURCES_MATCH = listOf(
        "Rift Report" to "📰",
        "Linha de Base" to "🎙️",
        "Central do Rift" to "📡"
    )
    private val SOURCES_MARKET = listOf(
        "Mercado GG" to "💼",
        "Janela Aberta" to "🔔",
        "Boletim Pro" to "📋"
    )
    private val SOURCES_GENERAL = listOf(
        "Portal Nexus" to "🌐",
        "Esports Já" to "⚡",
        "Tribuna Gamer" to "🗞️"
    )
    private val SOURCES_GOSSIP = listOf(
        "Bastidores GG" to "🍿",
        "Raio-X do Elenco" to "🔎",
        "Fofoca do Rift" to "👀"
    )
}
