package com.cblol.scout.data

/**
 * Divisão em que a carreira está sendo disputada.
 *
 * O jogador pode começar tanto na elite (CBLOL, 1ª divisão) quanto subindo de
 * baixo (CD, 2ª divisão). Os modos têm estruturas análogas (round-robin
 * duplo de 8 times, BO3, janelas de transferência, etc.) mas economia
 * diferente — a 2ª divisão tem orçamento e patrocínio bem menores, o que
 * destaca a importância do banco, da categoria de base e do mercado de
 * jogadores baratos.
 *
 * SOLID/OCP: novos formatos (ex: amadora, internacional) entram aqui sem mexer
 * em quem consome o enum, já que cada lugar trata exaustivamente.
 */
enum class Division(val label: String, val shortLabel: String) {
    /** 1ª divisão (CBLOL) — 8 times do snapshot oficial, orçamento alto. */
    FIRST("CBLOL", "1ª"),

    /** 2ª divisão (Circuito Desafiante) — 8 times procedurais, orçamento baixo. */
    SECOND("Circuito Desafiante", "2ª")
}

/**
 * Estado completo de uma carreira (modo jogo). Persistido em SharedPreferences via Gson.
 */
data class GameState(
    val managerName: String,
    val managerTeamId: String,
    val splitStartDate: String,
    val splitEndDate: String,
    var currentDate: String,
    var budget: Long,
    val sponsorshipPerWeek: Long,
    val playerOverrides: MutableMap<String, PlayerOverride> = mutableMapOf(),
    val matches: MutableList<Match> = mutableListOf(),
    val gameLog: MutableList<LogEntry> = mutableListOf(),

    /**
     * Placar parcial de séries BO3 em andamento.
     * Chave = Match.id — criado quando o jogador inicia o pick & ban do mapa 1,
     * removido ao finalizar a série (2 vitórias de qualquer lado).
     * Não precisa ser persistido entre sessões (série sempre recomeça do mapa 1
     * se o app for fechado), mas fica aqui para consistência com o Gson.
     */
    var seriesState: MutableMap<String, SeriesState> = mutableMapOf(),

    /**
     * Perfil acumulativo do técnico — level, XP, estatísticas de carreira.
     * Mantém o progresso do treinador ao longo do split.
     */
    var coachProfile: CoachProfile = CoachProfile(),

    /**
     * Último evento fora de jogo gerado mas ainda não visto pelo jogador.
     * Quando não-nulo, a [com.cblol.scout.ui.ManagerHubActivity] (ou o
     * [com.cblol.scout.ui.MatchResultActivity] após o fim da série) deve abrir
     * a [com.cblol.scout.ui.OffMatchEventActivity] antes de qualquer outra ação.
     *
     * Setado pelo [com.cblol.scout.domain.usecase.OffMatchEventService.maybeGenerateEvent].
     * Limpo quando a Activity de evento chama `consume()` após o usuário
     * tocar em CONTINUAR.
     */
    var pendingOffMatchEvent: OffMatchEvent? = null,

    /**
     * Patrocínios atualmente ativos do time do jogador. Pagam o
     * [SponsorContract.weeklyAmount] toda semana enquanto ativos.
     * Cada contrato tem uma data de início, duração e condições.
     *
     * Nullable porque Gson pode deixar null ao desserializar saves antigos que não têm este field.
     */
    var activeSponsors: MutableList<SponsorContract>? = null,

    /**
     * Ofertas de patrocínio disponíveis no "mercado". Geradas periodicamente
     * pelo [com.cblol.scout.domain.usecase.SponsorService]. O jogador pode
     * aceitar quantas couberem no limite do time.
     *
     * Nullable porque Gson pode deixar null ao desserializar saves antigos que não têm este field.
     */
    var availableSponsorOffers: MutableList<SponsorOffer>? = null,

    /**
     * Data (ISO) da última vez que o motor sorteou novas ofertas de
     * patrocínio. Usado para gerar ofertas a cada [SponsorService.OFFERS_INTERVAL_DAYS]
     * dias.
     */
    var lastSponsorOffersDate: String? = null,

    /**
     * Histórico dos últimos treinos realizados. Limitado em
     * [com.cblol.scout.domain.usecase.TrainingService.HISTORY_MAX] para não
     * inflar o save. Mostrado na [com.cblol.scout.ui.TrainingActivity].
     *
     * Nullable porque Gson pode deixar null ao desserializar saves antigos que não têm este field.
     */
    var trainingHistory: MutableList<TrainingSession>? = null,

    /**
     * Data (ISO) do último treino realizado, por tipo. Usado para impor cooldown
     * (cada tipo de treino tem seu próprio período de descanso — ver
     * [com.cblol.scout.data.TrainingType.cooldownDays]).
     *
     * Chave = nome do enum [TrainingType], valor = data ISO do último uso.
     *
     * Nullable porque Gson pode deixar null ao desserializar saves antigos que não têm este field.
     */
    var lastTrainingByType: MutableMap<String, String>? = null,

    /**
     * Departamento de olheiros do time. Controla quantos jogadores podem ser
     * scoutados simultaneamente, qualidade do scouting (afeta velocidade) e
     * custo de manutenção semanal.
     *
     * Nullable porque Gson pode deixar null ao desserializar saves antigos.
     */
    var scoutingDepartment: ScoutingDepartment? = null,

    /**
     * Janelas de transferência do split (pré-temporada e inter-temporada).
     * O mercado de compra/venda só fica aberto dentro de uma dessas janelas.
     *
     * Calculadas na criação da carreira pelo
     * [com.cblol.scout.domain.usecase.TransferWindowService] e persistidas
     * aqui para que o estado do mercado seja determinístico ao longo do jogo.
     *
     * Default lista vazia (em vez de nullable) — saves antigos sem este campo
     * desserializam como vazio e são repovoados por migração no
     * [com.cblol.scout.game.GameRepository].
     */
    var transferWindows: MutableList<TransferWindow> = mutableListOf(),

    /**
     * Ofertas de compra recebidas de OUTROS times por jogadores do elenco do
     * gerente. Geradas durante as janelas de transferência pelo
     * [com.cblol.scout.domain.usecase.IncomingOfferService].
     *
     * O gerente pode aceitar (jogador sai, recebe o valor) ou recusar cada
     * oferta. Ofertas expiram após alguns dias ou ao fim da janela.
     *
     * Nullable porque Gson pode deixar null ao desserializar saves antigos
     * que não têm este campo.
     */
    var incomingOffers: MutableList<IncomingTransferOffer>? = null,

    /**
     * Data (ISO) da última vez que o motor sorteou ofertas de compra de outros
     * times. Usado para gerar ofertas a cada
     * [com.cblol.scout.domain.usecase.IncomingOfferService.OFFER_INTERVAL_DAYS]
     * dias enquanto a janela está aberta.
     */
    var lastIncomingOffersDate: String? = null,

    /**
     * Laços (química) entre pares de jogadores do elenco do gerente.
     *
     * Cada [PlayerBond] mede a relação entre dois jogadores numa escala de
     * -100 (rivalidade tóxica) a +100 (parceria perfeita). Laços fortes geram
     * bônus de sinergia na simulação; laços ruins penalizam. A química evolui
     * lentamente ao longo do tempo conforme os jogadores convivem, jogam juntos,
     * vencem/perdem, e por eventos fora de partida (jogadas ensaiadas, brigas).
     *
     * Chave = [PlayerBond.keyFor] (ids ordenados, ex: "p1|p2"). Mantido como
     * mapa para lookup O(1) e para o Gson serializar sem problemas.
     *
     * Nullable porque Gson pode deixar null ao desserializar saves antigos que
     * não têm este campo — migrado defensivamente no
     * [com.cblol.scout.game.GameRepository].
     */
    var playerBonds: MutableMap<String, PlayerBond>? = null,

    /**
     * Data (ISO) da última vez que o motor processou a evolução diária dos
     * laços. Usado para evitar processar o mesmo dia duas vezes e para calcular
     * quantos dias de convivência aplicar de uma vez ao avançar o calendário.
     */
    var lastBondTickDate: String? = null,

    /**
     * Categoria de base (academia) do time do gerente. Abriga jovens promessas
     * (16-19 anos) que treinam e evoluem com o tempo até estarem prontos para
     * subir ao elenco principal.
     *
     * Diferente da 2ª divisão (jogadores prontos que se compra no mercado), os
     * prospects da base são desenvolvidos DENTRO da organização: começam com
     * overall baixo mas têm potencial oculto, e crescem conforme a academia é
     * mantida e eles recebem tempo de desenvolvimento.
     *
     * Nullable porque Gson pode deixar null ao desserializar saves antigos que
     * não têm este campo — inicializado defensivamente no
     * [com.cblol.scout.game.GameRepository] e no
     * [com.cblol.scout.domain.usecase.AcademyService].
     */
    var academy: Academy? = null,

    /**
     * Data (ISO) da última vez que o motor processou o desenvolvimento diário
     * dos prospects da base + a geração de novos talentos. Idempotente por data.
     */
    var lastAcademyTickDate: String? = null,

    /**
     * Jogadores PROMOVIDOS da categoria de base ao elenco principal.
     *
     * Diferente dos jogadores do snapshot (1ª divisão) e dos procedurais da 2ª
     * divisão (que existem no [com.cblol.scout.util.SecondDivisionGenerator]),
     * um prospect promovido é um [Player] totalmente novo, criado na hora da
     * promoção. Como não tem origem em nenhuma fonte estática, precisa ser
     * guardado aqui para sobreviver entre sessões e aparecer no
     * [com.cblol.scout.game.GameRepository.rosterOf].
     *
     * Os overrides normais (salário, titularidade, moral) continuam valendo via
     * [playerOverrides], indexados pelo id do Player promovido.
     *
     * Nullable por compatibilidade com saves antigos.
     */
    var promotedPlayers: MutableList<Player>? = null,

    /**
     * Estado bancário do time: empréstimos ativos + histórico de quitações.
     *
     * O banco permite ao gerente tomar empréstimos emergenciais quando o caixa
     * aperta, pagos em parcelas semanais com juros. Ver
     * [com.cblol.scout.domain.usecase.BankService].
     *
     * Nullable por compatibilidade com saves antigos — inicializado
     * defensivamente no [com.cblol.scout.game.GameRepository] e no próprio
     * [com.cblol.scout.domain.usecase.BankService].
     */
    var bank: BankState? = null,

    /**
     * Divisão em que a carreira está sendo disputada. Define quais times são
     * adversários, o calendário do split e o orçamento inicial.
     *
     * Default [Division.FIRST] para manter compatibilidade com saves antigos
     * (criados antes do modo 2ª divisão, sempre na elite).
     */
    var division: Division = Division.FIRST,

    /**
     * Times da 2ª divisão gerados proceduralmente na criação de uma carreira
     * no modo [Division.SECOND]. Persistidos para que o split seja
     * determinístico entre sessões — sem isso, regeneraríamos times com nomes
     * diferentes a cada load.
     *
     * Vazio nas carreiras de 1ª divisão (os times de lá vem do snapshot).
     */
    var secondDivisionTeams: MutableList<Team> = mutableListOf(),

    /**
     * Roster dos times da 2ª divisão (id, nome, role, contrato, atributos),
     * também persistido junto com [secondDivisionTeams] para sobreviver entre
     * sessões. O [com.cblol.scout.game.GameRepository.rosterOf] inclui estes
     * jogadores ao montar o elenco do gerente quando a carreira é na 2ª div.
     *
     * Distinto de [promotedPlayers] (academia) e de [Player]s do snapshot ou
     * do [com.cblol.scout.util.SecondDivisionGenerator] (free agents).
     */
    var secondDivisionPlayers: MutableList<Player> = mutableListOf()
)

/**
 * ╔══════════════════════════════════════════════════╗
 * ║ SISTEMA BANCÁRIO (EMPRÉSTIMOS)                              ║
 * ╚══════════════════════════════════════════════════╝
 *
 * O banco é a rede de segurança financeira do gerente. Quando o orçamento
 * aperta (folha salarial alta, manutenções, contratações), ele pode tomar um
 * **empréstimo emergencial** — recebe o valor à vista e paga em **parcelas
 * semanais** (descontadas todo domingo pelo [com.cblol.scout.game.GameEngine]),
 * acrescidas de juros.
 *
 *  - Cada [LoanOffer] (catálogo) define um valor, prazo em semanas e taxa de
 *    juros. Tiers maiores liberam só com reputação/limite de crédito maior.
 *  - Ao contratar, vira um [BankLoan] ativo com parcelas a pagar.
 *  - O gerente pode **quitar antecipadamente** o saldo devedor a qualquer
 *    momento (sem desconto, mas livra das parcelas futuras).
 *  - O **limite de crédito** depende da reputação do técnico e do patrocínio
 *    semanal — evita que o jogador tome dívida impagável.
 */

/**
 * Estado bancário persistido no save.
 *
 * @property loans empréstimos atualmente ativos (com saldo a pagar)
 * @property lastInstallmentDate data (ISO) da última cobrança semanal de
 *   parcelas — usado para cobrar uma vez por semana de forma idempotente
 * @property totalBorrowedEver soma de tudo que já foi emprestado na carreira
 *   (para estatística/exibição)
 * @property totalInterestPaid soma de juros já pagos na carreira
 */
data class BankState(
    val loans: MutableList<BankLoan> = mutableListOf(),
    var lastInstallmentDate: String? = null,
    var totalBorrowedEver: Long = 0L,
    var totalInterestPaid: Long = 0L
)

/**
 * Um empréstimo ativo.
 *
 * O valor total a pagar é `principal × (1 + taxa)`, dividido em
 * [totalInstallments] parcelas semanais iguais. A cada semana o
 * [com.cblol.scout.game.GameEngine] desconta uma [installmentAmount] do
 * orçamento e incrementa [installmentsPaid], até quitar.
 *
 * @property id identificador único
 * @property label nome amigável da linha de crédito (ex: "Crédito Emergencial")
 * @property principal valor recebido à vista quando contratado
 * @property interestRate taxa de juros total sobre o principal (ex: 0.15 = 15%)
 * @property totalInstallments número de parcelas semanais
 * @property installmentsPaid quantas parcelas já foram pagas
 * @property installmentAmount valor de cada parcela semanal (já com juros)
 * @property takenOn data (ISO) em que foi contratado
 */
data class BankLoan(
    val id: String,
    val label: String,
    val principal: Long,
    val interestRate: Double,
    val totalInstallments: Int,
    var installmentsPaid: Int = 0,
    val installmentAmount: Long = 0L,
    val takenOn: String = ""
) {
    /** Total a pagar ao longo de toda a vida do empréstimo (principal + juros). */
    val totalRepayable: Long get() = installmentAmount * totalInstallments

    /** Quantas parcelas ainda faltam pagar. */
    val installmentsRemaining: Int get() = (totalInstallments - installmentsPaid).coerceAtLeast(0)

    /** Saldo devedor restante (parcelas que faltam × valor da parcela). */
    val outstandingBalance: Long get() = installmentAmount * installmentsRemaining

    /** Empréstimo já foi totalmente pago? */
    val isPaidOff: Boolean get() = installmentsPaid >= totalInstallments

    /** Progresso de quitação (0-100%) para barras na UI. */
    fun repaymentPercent(): Int =
        if (totalInstallments <= 0) 100
        else (installmentsPaid.toFloat() / totalInstallments * 100).toInt().coerceIn(0, 100)
}

/**
 * Modelo imutável de uma linha de crédito disponível no banco (catálogo).
 * Ao ser contratada, vira um [BankLoan] ativo.
 *
 * @property id identificador único da linha
 * @property label nome amigável (ex: "Crédito Emergencial")
 * @property emoji ícone para a UI
 * @property principal valor emprestado à vista
 * @property interestRate juros total sobre o principal (ex: 0.15 = 15%)
 * @property weeks prazo em semanas (= número de parcelas)
 * @property minReputation reputação mínima do técnico para liberar a linha
 * @property description texto curto explicando a linha
 */
data class LoanOffer(
    val id: String,
    val label: String,
    val emoji: String,
    val principal: Long,
    val interestRate: Double,
    val weeks: Int,
    val minReputation: Int = 0,
    val description: String = ""
)

/**
 * Classificação da saúde financeira do time, derivada do orçamento atual em
 * relação à folha/compromissos. Usada para o aviso visual no Hub.
 *
 * SOLID/OCP: novas faixas (ex: "FALÊNCIA") entram aqui sem mexer na lógica.
 */
enum class FinancialHealth(val emoji: String, val label: String) {
    /** Caixa confortável. */
    HEALTHY("🟢", "Saudável"),
    /** Caixa apertado — cuidado com gastos. */
    WARNING("🟡", "Atenção"),
    /** Caixa crítico ou negativo — risco imediato. */
    CRITICAL("🔴", "Crítico")
}

/**
 * ╔══════════════════════════════════════════════════╗
 * ║ SISTEMA DE CATEGORIA DE BASE (ACADEMIA)                      ║
 * ╚══════════════════════════════════════════════════╝
 *
 * A academia abriga **prospects** — jovens jogadores (16-19 anos) da própria
 * organização. Diferente do mercado/2ª divisão (jogadores prontos), aqui o
 * gerente DESENVOLVE talentos do zero:
 *
 *  - Cada prospect tem um overall ATUAL baixo (45-62) e um POTENCIAL oculto
 *    (55-90), revelado só após avaliação (scouting interno).
 *  - Mantendo a academia (custo semanal) e dando tempo, o prospect evolui em
 *    direção ao seu potencial.
 *  - Quando pronto (ou quando o gerente decidir), pode ser PROMOVIDO ao elenco
 *    principal — sem custo de transferência, só o salário de jogador base.
 *  - A academia gera novos prospects periodicamente (recrutamento), limitada
 *    pela capacidade do tier.
 *
 * O tier da academia (BASIC/PRO/ELITE) controla capacidade, velocidade de
 * desenvolvimento, qualidade dos talentos recrutados e custo de manutenção.
 */

/** Tier da academia (categoria de base). Afeta capacidade, qualidade e custo. */
enum class AcademyTier(
    val label: String,
    val emoji: String,
    /** Máximo de prospects que cabem na academia simultaneamente. */
    val capacity: Int,
    /** Multiplicador de velocidade de desenvolvimento (1.0 = base). */
    val growthFactor: Double,
    /** Potencial máximo possível dos talentos recrutados neste tier. */
    val maxPotential: Int,
    /** Custo de manutenção semanal (descontado do orçamento). */
    val weeklyCost: Long,
    /** Custo para fazer upgrade PARA este tier. */
    val upgradeCost: Long,
    /** Reputação mínima do técnico para fazer upgrade para este tier. */
    val minReputation: Int
) {
    BASIC(
        label = "Básica", emoji = "🌱",
        capacity = 4, growthFactor = 1.0, maxPotential = 78,
        weeklyCost = 10_000L, upgradeCost = 0L, minReputation = 0
    ),
    PRO(
        label = "Estruturada", emoji = "🏫",
        capacity = 6, growthFactor = 1.4, maxPotential = 85,
        weeklyCost = 35_000L, upgradeCost = 300_000L, minReputation = 55
    ),
    ELITE(
        label = "Elite", emoji = "🏆",
        capacity = 8, growthFactor = 1.9, maxPotential = 92,
        weeklyCost = 90_000L, upgradeCost = 1_000_000L, minReputation = 75
    )
}

/**
 * Estado da academia do time. Inicializa no tier [AcademyTier.BASIC] com uma
 * leva inicial de prospects gerada na criação da carreira.
 *
 * @property tier tier atual da academia
 * @property prospects prospects atualmente na base
 * @property lastRecruitDate data (ISO) do último recrutamento automático de
 *   novos talentos (controla o intervalo de geração)
 */
data class Academy(
    var tier: AcademyTier = AcademyTier.BASIC,
    val prospects: MutableList<AcademyProspect> = mutableListOf(),
    var lastRecruitDate: String? = null
)

/**
 * Um prospect (jovem promessa) da categoria de base.
 *
 * @property id identificador único (ex: "prospect_mid_3")
 * @property nome nome de jogo (gamertag)
 * @property nomeReal nome civil
 * @property role rota (TOP/JNG/MID/ADC/SUP)
 * @property idade idade atual (16-19); ao chegar em [MAX_ACADEMY_AGE] precisa
 *   ser promovido ou liberado
 * @property currentOverall overall atual (cresce com o tempo rumo ao potencial)
 * @property potential teto de overall que o prospect pode atingir (oculto até
 *   ser avaliado). Mantido como inteiro 1-99.
 * @property evaluated se o gerente já avaliou o prospect (revela o potencial e
 *   uma estimativa mais precisa). Avaliar custa dinheiro/tempo.
 * @property nacionalidade país (sempre BR neste recorte)
 * @property joinedOn data (ISO) em que entrou na base
 * @property championPool campeões de destaque do prospect
 * @property developmentLog histórico curto de marcos de desenvolvimento
 */
data class AcademyProspect(
    val id: String,
    val nome: String,
    val nomeReal: String,
    val role: String,
    var idade: Int,
    var currentOverall: Int,
    val potential: Int,
    var evaluated: Boolean = false,
    val nacionalidade: String = "BR",
    val joinedOn: String = "",
    val championPool: List<String> = emptyList(),
    val developmentLog: MutableList<String> = mutableListOf()
) {
    companion object {
        /** Idade máxima na base — depois disso o prospect precisa subir ou sair. */
        const val MAX_ACADEMY_AGE = 20
    }

    /**
     * Faixa de potencial mostrada quando o prospect AINDA não foi avaliado
     * (estimativa grosseira do olheiro da base). Quando avaliado, a UI mostra
     * o número exato.
     */
    fun potentialBand(): String = when {
        potential >= 85 -> "Excepcional"
        potential >= 75 -> "Alto"
        potential >= 65 -> "Promissor"
        potential >= 55 -> "Mediano"
        else            -> "Limitado"
    }

    /** Quão perto do teto o prospect está (0-100%), para barras de progresso. */
    fun developmentPercent(): Int =
        if (potential <= 0) 0
        else (currentOverall.toFloat() / potential * 100).toInt().coerceIn(0, 100)

    /** Prospect atingiu (ou passou) seu potencial — pronto para subir. */
    fun isReady(): Boolean = currentOverall >= potential - 2
}

/**
 * Laço (química) entre dois jogadores do elenco.
 *
 * Modela a relação interpessoal e a entrosagem em jogo entre [playerAId] e
 * [playerBId], numa escala simétrica de -100 a +100:
 *  - **+60 a +100**: parceria forte — combos ensaiados, leem um ao outro.
 *  - **+20 a +59**: boa relação — entrosamento acima da média.
 *  - **-19 a +19**: neutro — apenas colegas de trabalho.
 *  - **-59 a -20**: atrito — desentendimentos atrapalham a comunicação.
 *  - **-100 a -60**: rivalidade tóxica — sabotam o ambiente do time.
 *
 * O laço é SIMÉTRICO (a relação de A com B é a mesma de B com A), por isso a
 * chave canônica [keyFor] sempre ordena os ids alfabeticamente.
 *
 * @property playerAId id do primeiro jogador (sempre <= playerBId)
 * @property playerBId id do segundo jogador
 * @property level nível atual do laço (-100..100). 0 = neutro/recém-formado.
 * @property daysTogether dias de convivência acumulados no mesmo elenco. Quanto
 *   mais tempo juntos, mais estável e forte o laço tende a ficar.
 * @property history últimas mudanças notáveis (jogadas, brigas, marcos) para a
 *   UI exibir. Limitado em [com.cblol.scout.domain.usecase.PlayerBondService.HISTORY_MAX].
 */
data class PlayerBond(
    val playerAId: String,
    val playerBId: String,
    val level: Int = 0,
    val daysTogether: Int = 0,
    val history: List<BondEvent> = emptyList()
) {
    companion object {
        /**
         * Chave canônica de um par de jogadores, independente da ordem em que
         * os ids são passados. Ordena os dois ids e junta com "|".
         */
        fun keyFor(id1: String, id2: String): String =
            if (id1 <= id2) "$id1|$id2" else "$id2|$id1"
    }
}

/**
 * Faixas qualitativas de um laço, derivadas do nível numérico. Usadas pela UI
 * para escolher rótulo, emoji e cor.
 *
 * SOLID/OCP: adicionar uma faixa nova (ex: "lendário") é só estender este enum
 * e ajustar [from].
 */
enum class BondTier(val label: String, val emoji: String) {
    TOXIC("Rivalidade", "☠️"),
    TENSE("Atrito", "⚡"),
    NEUTRAL("Neutro", "🤝"),
    FRIENDLY("Entrosados", "😎"),
    BONDED("Parceria", "🔥");

    companion object {
        fun from(level: Int): BondTier = when {
            level <= -60 -> TOXIC
            level <= -20 -> TENSE
            level <   20 -> NEUTRAL
            level <   60 -> FRIENDLY
            else         -> BONDED
        }
    }
}

/**
 * Uma entrada no histórico de um laço.
 *
 * @property date data (ISO) do evento
 * @property reason descrição curta em PT-BR (ex: "Jogada ensaiada", "Briga no treino")
 * @property delta variação aplicada ao nível do laço (positiva ou negativa)
 * @property levelAfter nível do laço após o delta, já com clamp
 */
data class BondEvent(
    val date: String,
    val reason: String,
    val delta: Int,
    val levelAfter: Int
)

/**
 * Oferta de compra recebida de outro time por um jogador do elenco do gerente.
 *
 * Diferente das transferências que o gerente inicia (comprar/vender no mercado),
 * estas são propostas que CHEGAM ao gerente durante as janelas — espelhando
 * times rivais tentando reforçar o elenco.
 *
 * O gerente decide se aceita (jogador é vendido pelo [amountBrl], que costuma
 * ser próximo ou acima do valor de mercado) ou recusa. Jogadores que pediram
 * transferência tendem a gerar ofertas mais frequentes e a ficar insatisfeitos
 * se a oferta for recusada.
 *
 * @property id identificador único da oferta (para aceitar/recusar)
 * @property playerId jogador alvo (id no override/snapshot)
 * @property playerName nome do jogador (snapshot, para a UI não refazer lookup)
 * @property playerRole role do jogador (para exibição)
 * @property fromTeamId time que está oferecendo
 * @property fromTeamName nome do time que está oferecendo
 * @property amountBrl valor oferecido pela transferência
 * @property offeredOn data (ISO) em que a oferta chegou
 * @property expiresOn data (ISO) em que a oferta expira
 * @property motivatedByRequest true se o jogador havia pedido para sair (a
 *   recusa nesse caso pesa mais na moral)
 */
data class IncomingTransferOffer(
    val id: String,
    val playerId: String,
    val playerName: String,
    val playerRole: String,
    val fromTeamId: String,
    val fromTeamName: String,
    val amountBrl: Long,
    val offeredOn: String,
    val expiresOn: String,
    val motivatedByRequest: Boolean = false
)

/**
 * Tipo de janela de transferência. Cada split tem uma pré-temporada (antes do
 * primeiro jogo) e uma inter-temporada (pausa no meio do split).
 *
 * SOLID/OCP: adicionar uma nova janela (ex: pós-temporada) é só estender este
 * enum e gerar a janela no [com.cblol.scout.domain.usecase.TransferWindowService].
 */
enum class TransferWindowKind(val label: String, val emoji: String) {
    PRE_SEASON("Pré-temporada", "🌱"),
    MID_SEASON("Inter-temporada", "🔄")
}

/**
 * Uma janela de transferência concreta, com datas de abertura e fechamento
 * (inclusivas, ISO yyyy-MM-dd). O mercado fica aberto entre [startDate] e
 * [endDate], inclusive ambas as pontas.
 */
data class TransferWindow(
    val kind: TransferWindowKind,
    val startDate: String,
    val endDate: String
)

/**
 * Placar parcial de uma série BO3 entre dois times.
 */
data class SeriesState(
    val playerWins: Int = 0,
    val opponentWins: Int = 0
) {
    fun recordMap(playerWon: Boolean) = copy(
        playerWins   = if (playerWon)  playerWins + 1  else playerWins,
        opponentWins = if (!playerWon) opponentWins + 1 else opponentWins
    )
    val isFinished: Boolean get() = playerWins == 2 || opponentWins == 2
}

/**
 * Mudanças aplicadas a um jogador durante a carreira.
 *
 * Tudo aqui é opcional — fields null significam "sem mudança em relação ao snapshot público".
 */
data class PlayerOverride(
    val playerId: String,
    val newTeamId: String? = null,
    val newSalary: Long? = null,
    val newContractEnd: String? = null,
    val titular: Boolean? = null,
    val transferredOn: String? = null,
    /**
     * Nível de moral do jogador (0-100). Default null = ainda não inicializado.
     * Valores típicos:
     *  - 0..33  → 😞 triste
     *  - 34..66 → 😐 neutro
     *  - 67..100 → 😄 feliz
     *
     * Modificado por eventos do jogo (vitória/derrota, transferências, banca).
     * Ver `domain.usecase.MoraleService` para a lógica de transição.
     */
    val mood: Int? = null,

    /**
     * Histórico das últimas mudanças de moral (mais recente primeiro).
     * Limitado em [com.cblol.scout.domain.usecase.MoraleService.HISTORY_MAX_ENTRIES]
     * para não inflar o save. Cada entrada descreve o evento e o delta aplicado.
     */
    val moodHistory: List<MoodEvent> = emptyList(),

    /**
     * Data (ISO yyyy-MM-dd) da última partida em que esse jogador foi titular.
     * Usada pelo decay temporal: se o jogador fica muitas semanas sem jogar,
     * a moral converge para o valor neutro (50).
     *
     * Null = nunca jogou nesta carreira ainda.
     */
    val lastPlayedDate: String? = null,

    /**
     * Data em que o jogador pediu transferência (ou null se não pediu).
     * Disparado quando moral atinge valor extremamente baixo — ver
     * [com.cblol.scout.domain.usecase.MoraleService.TRANSFER_REQUEST_THRESHOLD].
     */
    val transferRequestedOn: String? = null,

    /**
     * Modificador de overall TEMPORÁRIO atribuído por eventos fora de jogo
     * (lesão, relacionamento, entrevista repercussão, etc).
     *
     * Persiste até [overallModifierExpiresOn]. Se a data corrente é > expiresOn,
     * o modificador é considerado expirado (mas só limpamos no próximo evento
     * fora de jogo para manter o save consistente sem migration).
     *
     * Aplicado em cima do modificador de moral do MoraleService — ver
     * [com.cblol.scout.domain.usecase.OffMatchEventService.activeOverallModifierFor].
     */
    val overallModifier: Int = 0,

    /**
     * Data (ISO yyyy-MM-dd) em que o [overallModifier] expira. Null quando não
     * há modificador ativo.
     */
    val overallModifierExpiresOn: String? = null,

    /**
     * Descrição curta do motivo do modificador ativo (ex: "Lesão no dedo",
     * "Família assistindo"). Mostrada em tooltip/dialog quando relevante.
     */
    val overallModifierReason: String? = null,

    /**
     * Nível atual de scouting deste jogador (0-5). Quanto maior, mais
     * informações ficam visíveis sobre ele na TransferMarketActivity e
     * PlayerDetailDialog. 0 = jogador desconhecido (apenas nome/role/time/idade);
     * 5 = totalmente revelado.
     *
     * Ver `domain.usecase.ScoutingService` para a tabela de visibilidade.
     * Default 0 — todo jogador começa desconhecido exceto os do roster do gerente.
     */
    val scoutLevel: Int = 0,

    /**
     * Quando o scouting deste jogador foi iniciado (ISO date). Null = sem
     * scouting em andamento. Usado para calcular progressão automática ao
     * avançar dias — cada `ScoutingService.DAYS_PER_LEVEL` dias sobe 1 nível.
     */
    val scoutStartedOn: String? = null,

    /**
     * Quantos dias o jogador foi escotado desde o último upgrade de nível.
     * Reseta cada vez que o nível sobe. Persistido para sobreviver a fechar
     * o app no meio de um scouting.
     */
    val scoutDaysAccumulated: Int = 0,

    /**
     * Cláusulas avançadas do contrato deste jogador (multa rescisória,
     * bônus, cláusula de saída). Null = sem cláusulas configuradas
     * (usa apenas as informações básicas do [Contrato] do snapshot).
     *
     * Adicionadas pelo [com.cblol.scout.domain.usecase.ContractService] quando
     * o jogador é renegociado pela primeira vez ou contratado.
     */
    val contractClauses: ContractClauses? = null
)

/**
 * Cláusulas adicionais de um contrato. Estendem o [Contrato] básico do
 * snapshot com regras avançadas que afetam venda, renovação e adesão do
 * jogador.
 *
 * **Multa rescisória** ([releaseClauseBrl]): valor mínimo que outro time
 * precisa oferecer para tirar o jogador (ou que o próprio time precisa pagar
 * para encerrar o contrato antes do prazo).
 *
 * **Bônus de assinatura** ([signingBonusBrl]): pago no ato da assinatura.
 * Aumenta a aceitação do jogador em propostas com salário mais baixo.
 *
 * **Cláusula de performance** ([performanceClauseBrl]): bônus pago por
 * temporada se o jogador atingir certas metas (ex: 60% de win rate). Ainda
 * não avaliada em produção, mas serializada para uso futuro.
 */
data class ContractClauses(
    val releaseClauseBrl: Long = 0L,
    val signingBonusBrl: Long = 0L,
    val performanceClauseBrl: Long = 0L
)

/**
 * Uma entrada no histórico de moral de um jogador.
 *
 * @property date data (ISO) em que o evento ocorreu
 * @property reason descrição curta em PT-BR (ex: "Vitória contra LOUD", "Renovação")
 * @property delta variação aplicada (positiva ou negativa)
 * @property valueAfter valor de moral após o delta, já com clamping
 */
data class MoodEvent(
    val date: String,
    val reason: String,
    val delta: Int,
    val valueAfter: Int
)

/**
 * Estados de moral derivados do valor numérico do [PlayerOverride.mood].
 *
 * Cada estado tem um emoji associado mostrado nos cards de jogador da SquadActivity.
 * SOLID/OCP: adicionar um novo estado (ex: "furioso", "motivado") é só estender este enum.
 */
enum class Mood(val emoji: String, val label: String) {
    SAD("😞", "Triste"),
    NEUTRAL("😐", "Neutro"),
    HAPPY("😄", "Feliz");

    companion object {
        /** Converte um valor 0-100 no estado discreto correspondente. */
        fun from(value: Int): Mood = when {
            value <= 33  -> SAD
            value <= 66  -> NEUTRAL
            else         -> HAPPY
        }
    }
}

/**
 * Partida do split (BO3). homeScore/awayScore = mapas vencidos.
 */
data class Match(
    val id: String,
    val date: String,
    val round: Int,
    val homeTeamId: String,
    val awayTeamId: String,
    var played: Boolean = false,
    var homeScore: Int = 0,
    var awayScore: Int = 0,
    var pickBanPlan: PickBanPlan? = null
) {
    fun winnerTeamId(): String? = when {
        !played -> null
        homeScore > awayScore -> homeTeamId
        else -> awayTeamId
    }
}

/**
 * Resultado do pick & ban de um mapa, armazenado junto à partida.
 */
data class PickBanPlan(
    val mapNumber: Int,
    val bluePicks: List<String>,
    val redPicks: List<String>,
    val blueBans: List<String>,
    val redBans: List<String>,
    /**
     * Atribuição de campeões a jogadores e roles do lado do jogador.
     * Permite que o treinador remaneje quem joga em qual rota após o draft.
     *
     * Se vazio, o engine assume que cada jogador joga seu próprio campeão na
     * sua role natural (fluxo clássico). Se preenchido, o engine usa
     * estas atribuições para gerar eventos e calcular penalidades de
     * "rota errada" na simulação.
     */
    val roleAssignments: List<RoleAssignment> = emptyList()
)

/**
 * Atribuição de um campeão pickado a um jogador e uma role específica.
 *
 * Exemplo: jogador "Robo" (TOP nato) pode ser atribuído a "Akshan" jogando MID.
 * Como Robo é TOP de origem, isso conta como rota errada — a simulação aplica
 * penalidade em lane phase, dano e outros stats.
 *
 * @property championId id do campeão pickado (case do app, ex: "KhaZix")
 * @property playerName nome do jogador que vai jogar esse campeão
 * @property assignedRole role onde ele vai jogar (TOP/JNG/MID/ADC/SUP)
 * @property naturalRole role nativa do jogador no roster
 */
data class RoleAssignment(
    val championId: String,
    val playerName: String,
    val assignedRole: String,
    val naturalRole: String
) {
    /** Verdadeiro quando o jogador está jogando fora da sua role nativa. */
    val isWrongRole: Boolean get() = assignedRole != naturalRole
}

/**
 * Linha da classificação.
 */
data class Standing(
    val teamId: String,
    val teamName: String,
    val wins: Int,
    val losses: Int,
    val mapsWon: Int,
    val mapsLost: Int
) {
    val mapDiff: Int get() = mapsWon - mapsLost
    val games: Int  get() = wins + losses
}

/**
 * Perfil do técnico com progressão ao longo da carreira.
 *
 * **Level e XP**: o técnico ganha XP por várias ações (ver `domain.CoachXp`).
 * O level é derivado do XP via curva quadrática simples — cada level pede
 * `(level^2) * 100` XP total para alcançar.
 *
 * **Atributos**: refletem o estilo de jogo do treinador, calculados a partir
 * das estatísticas acumuladas. Cada atributo é normalizado para 0-99.
 *
 * **Título**: derivado do level, dando um nome em PT-BR ao estágio do técnico
 * ("Iniciante" → "Veterano" → "Lendário").
 *
 * O perfil é persistido junto com o `GameState` no save.
 */
data class CoachProfile(
    /** XP acumulado em toda a carreira. */
    var xp: Int = 0,

    // ── Estatísticas brutas (incrementadas pelos eventos) ──
    /** Mapas vencidos no split atual. */
    var mapsWon: Int = 0,
    /** Mapas perdidos no split atual. */
    var mapsLost: Int = 0,
    /** Séries BO3 vencidas. */
    var seriesWon: Int = 0,
    /** Séries BO3 perdidas. */
    var seriesLost: Int = 0,
    /** Pick & bans manuais conduzidos pelo treinador (não auto-simulados). */
    var manualPickBansDone: Int = 0,
    /** Jogadores contratados via mercado. */
    var playersHired: Int = 0,
    /** Jogadores vendidos via mercado. */
    var playersSold: Int = 0,
    /** Contratos renegociados com sucesso. */
    var contractsRenewed: Int = 0,
    /** Soma de R$ gasto em transferências durante a carreira. */
    var totalSpent: Long = 0L,
    /** Soma de R$ recebido em transferências durante a carreira. */
    var totalEarned: Long = 0L,
    /** Reputação (0-100) — sobe com vitórias e desce com derrotas pesadas. */
    var reputation: Int = 50
)

/**
 * Eventos do log do jogo.
 */
data class LogEntry(
    val date: String,
    val type: String,
    val message: String
)

/**
 * Evento fora de jogo (entre BO3s). Apresentado ao jogador em uma tela própria
 * antes da próxima partida. Tem narrativa, efeitos imediatos em moral e/ou
 * modificadores temporários de overall, e pode afetar um único jogador ou o
 * time inteiro.
 *
 * **Categorias** (via [OffMatchEventCategory]):
 *  - INTERVIEW: entrevista — boa ou ruim, afeta moral/reputação
 *  - INJURY: lesão leve — penaliza overall por X dias
 *  - RELATIONSHIP_START / RELATIONSHIP_END: relacionamentos pessoais
 *  - FAMILY_SUPPORT: família assistindo — bônus emocional
 *  - SCANDAL: polêmica que pega o time todo
 *  - TRAINING_BREAKTHROUGH: avanço no treino, bônus temporário
 *  - SPONSOR_VISIT: patrocinador presente, pressão extra
 *  - FAN_SUPPORT: torcida fazendo evento de apoio
 *  - PERSONAL_TRAGEDY: tragédia pessoal séria
 *
 * **Sinal** ([OffMatchEventSentiment]): POSITIVE, NEUTRAL ou NEGATIVE — para a UI
 * escolher ícones/cores apropriados.
 *
 * **Alvo** (via [targetPlayerId]):
 *  - null = afeta o time inteiro
 *  - playerId = afeta só esse jogador
 *
 * **Efeitos** (já calculados quando o evento é criado, para a UI poder mostrar):
 *  - [moodDelta]: variação de moral aplicada (positiva ou negativa)
 *  - [overallModifierDelta]: modificador de overall que vai durar [durationDays]
 *  - [durationDays]: por quantos dias o modificador permanece ativo
 */
data class OffMatchEvent(
    val id: String,
    val date: String,
    val category: OffMatchEventCategory,
    val sentiment: OffMatchEventSentiment,
    /** Título curto do evento (mostrado no topo da tela). */
    val title: String,
    /** Texto narrativo do evento (parágrafo curto em PT-BR). */
    val description: String,
    /** Player ID se o evento afeta um jogador específico, null se time inteiro. */
    val targetPlayerId: String? = null,
    /** Nome do jogador (snapshot, para a UI mostrar sem refazer lookup). */
    val targetPlayerName: String? = null,
    /**
     * SEGUNDO jogador envolvido, quando o evento é entre uma DUPLA (jogada
     * ensaiada / briga de laços). Null para eventos de jogador único ou de time.
     */
    val secondPlayerId: String? = null,
    /** Nome do segundo jogador da dupla (quando [secondPlayerId] != null). */
    val secondPlayerName: String? = null,
    /**
     * Variação aplicada ao LAÇO entre os dois jogadores da dupla (eventos de
     * dupla). Exibida no card de efeitos. 0 para eventos sem laço.
     */
    val bondDelta: Int = 0,
    /** Variação de moral aplicada ao(s) afetado(s). */
    val moodDelta: Int = 0,
    /** Modificador temporário de overall. */
    val overallModifierDelta: Int = 0,
    /** Duração em dias do modificador de overall (0 = sem modificador). */
    val durationDays: Int = 0
)

/** Categorias de evento. Cada uma vem com um conjunto de templates narrativos. */
enum class OffMatchEventCategory(val emoji: String, val label: String) {
    INTERVIEW("🎤", "Entrevista"),
    INJURY("🩹", "Lesão"),
    RELATIONSHIP_START("💕", "Relacionamento"),
    RELATIONSHIP_END("💔", "Término"),
    FAMILY_SUPPORT("👨‍👩‍👧", "Família"),
    SCANDAL("📰", "Polêmica"),
    TRAINING_BREAKTHROUGH("💡", "Treino"),
    SPONSOR_VISIT("💼", "Patrocinador"),
    FAN_SUPPORT("🎉", "Torcida"),
    PERSONAL_TRAGEDY("⚫", "Tragédia"),

    /** Jogada ensaiada entre dois jogadores — fortalece o laço da dupla. */
    TEAM_COMBO("🎯", "Jogada Ensaiada"),

    /** Briga entre dois jogadores — deteriora o laço da dupla. */
    TEAM_FIGHT("🥊", "Briga")
}

/** Sentimento geral do evento, usado pela UI para escolher cores. */
enum class OffMatchEventSentiment { POSITIVE, NEUTRAL, NEGATIVE }

/**
 * ╔═════════════════════════════════════════════════════╗
 * ║ SISTEMA DE PATROCÍNIOS                                       ║
 * ╚══════════════════════════════════════════════════════╝
 *
 * Catalog de patrocínios disponíveis. Cada [Sponsor] é um template imutável
 * com nome, valor semanal, duração e requisitos. Ao ser aceito, vira um
 * [SponsorContract] ativo no [GameState.activeSponsors].
 */

/** Tier do patrocínio (afeta valor pago e dificuldade de conseguir). */
enum class SponsorTier(val emoji: String, val label: String) {
    BRONZE("🥉", "Bronze"),
    SILVER("🥈", "Prata"),
    GOLD("🥇", "Ouro"),
    DIAMOND("💎", "Diamante")
}

/** Categoria do patrocínio (afeta temática visual + bonus narrativos). */
enum class SponsorCategory(val emoji: String, val label: String) {
    ENERGY_DRINK("⚡", "Energético"),
    PERIPHERAL("⌨️", "Periféricos"),
    HARDWARE("🖥️", "Hardware"),
    APPAREL("👕", "Vestuário"),
    FOOD_DELIVERY("🍔", "Delivery"),
    BANK("🏦", "Banco"),
    TELECOM("📶", "Telecom"),
    AUTOMOTIVE("🚗", "Automotivo"),
    BETTING("🎰", "Apostas"),
    STREAMING("📺", "Streaming")
}

/**
 * Modelo imutável de um patrocínio disponível.
 *
 * @property id identificador único (não muda entre saves)
 * @property name nome comercial visto pelo jogador (ex: "VoltKick Energy")
 * @property tier prestígio do patrocínio (BRONZE..DIAMOND)
 * @property category temática (energético, banco, etc)
 * @property weeklyAmount R$ pago toda semana enquanto ativo
 * @property durationWeeks duração em semanas do contrato
 * @property minReputation reputação mínima do técnico para receber a oferta
 * @property minWinsThisSplit mapas vencidos mínimos no split atual
 * @property bonusPerWin R$ adicional pago a cada vitória de mapa enquanto ativo
 * @property penaltyPerLoss R$ deduzido do próximo pagamento a cada derrota
 */
data class Sponsor(
    val id: String,
    val name: String,
    val tier: SponsorTier,
    val category: SponsorCategory,
    val weeklyAmount: Long,
    val durationWeeks: Int,
    val minReputation: Int = 0,
    val minWinsThisSplit: Int = 0,
    val bonusPerWin: Long = 0,
    val penaltyPerLoss: Long = 0,
    /** Texto curto descrevendo o tipo de empresa (mostrado no card). */
    val description: String = ""
)

/**
 * Oferta de patrocínio disponível no mercado. Contém o [sponsor] + uma
 * `expiresOn` para que a oferta não fique para sempre.
 */
data class SponsorOffer(
    val sponsor: Sponsor,
    val offeredOn: String,
    val expiresOn: String
)

/**
 * Contrato ATIVO de patrocínio. Instância mutável para acumular
 * estatísticas (semanas pagas, bônus recebidos) durante a vida útil.
 *
 * @property sponsor o template original
 * @property startDate quando foi aceito
 * @property endDate quando expira (start + durationWeeks * 7 dias)
 * @property weeksPaid quantos pagamentos semanais já foram processados
 * @property totalReceived R$ acumulado recebido (para mostrar na UI)
 */
data class SponsorContract(
    val sponsor: Sponsor,
    val startDate: String,
    val endDate: String,
    var weeksPaid: Int = 0,
    var totalReceived: Long = 0L
)

/**
 * ╔═════════════════════════════════════════════════════╗
 * ║ SISTEMA DE TREINOS                                          ║
 * ╚══════════════════════════════════════════════════════╝
 *
 * Treinos consomem dias do calendário + custo financeiro. Têm chance de
 * sucesso baseada em diversos fatores (moral da equipe, level do técnico).
 *
 * Cada [TrainingType] tem efeitos próprios em sucesso/falha:
 *  - SCRIM: melhora team_fight de todos
 *  - VOD_REVIEW: melhora criatividade e consistencia
 *  - SOLO_QUEUE: melhora lane_phase e clutch individuais
 *  - GYM: aumenta moral e reduz risco de lesão
 *  - TEAM_BUILDING: sobe moral do time inteiro
 *  - BOOT_CAMP: muito caro, mas o mais poderoso (afeta vários atributos)
 */

/** Tipo de treino. Cada um tem seu próprio cost, duração e efeitos. */
enum class TrainingType(
    val emoji: String,
    val label: String,
    val description: String,
    val durationDays: Int,
    val cooldownDays: Int,
    val cost: Long
) {
    SCRIM(
        emoji = "⚔️",
        label = "Scrim",
        description = "Treina contra outro time profissional. Melhora a coordenação em team fights.",
        durationDays = 1,
        cooldownDays = 2,
        cost = 30_000L
    ),
    VOD_REVIEW(
        emoji = "🎥",
        label = "Revisão de VOD",
        description = "Estudar replays das partidas anteriores. Identifica erros e melhora a tomada de decisão.",
        durationDays = 1,
        cooldownDays = 3,
        cost = 10_000L
    ),
    SOLO_QUEUE(
        emoji = "🎮",
        label = "Sessão de Solo Queue",
        description = "Cada jogador foca em melhorar sua lane individual em ranked. Risco de tilt.",
        durationDays = 2,
        cooldownDays = 4,
        cost = 5_000L
    ),
    GYM(
        emoji = "💪",
        label = "Academia",
        description = "Treino físico orientado por preparador. Reduz risco de lesões e aumenta o foco.",
        durationDays = 1,
        cooldownDays = 3,
        cost = 20_000L
    ),
    TEAM_BUILDING(
        emoji = "🎉",
        label = "Team Building",
        description = "Atividade recreativa fora do jogo. Fortalece o espírito de equipe e levanta a moral.",
        durationDays = 1,
        cooldownDays = 7,
        cost = 40_000L
    ),
    BOOT_CAMP(
        emoji = "🔥",
        label = "Boot Camp",
        description = "Imersão intensa de uma semana. Caro e cansativo, mas com ganhos significativos se executado bem.",
        durationDays = 7,
        cooldownDays = 30,
        cost = 250_000L
    )
}

/** Resultado de uma sessão de treino. */
enum class TrainingOutcome(val emoji: String, val label: String) {
    GREAT("✨", "Excelente"),     // efeitos máximos
    GOOD("✅", "Bom"),             // efeitos normais
    NEUTRAL("🟡", "Regular"),      // efeitos modestos
    BAD("⚠️", "Ruim"),             // sem efeito ou pequena perda
    DISASTER("💥", "Desastre")      // efeito negativo sério
}

/**
 * Sessão de treino registrada no histórico.
 *
 * @property date data (ISO) em que o treino aconteceu
 * @property type tipo do treino
 * @property outcome resultado sorteado
 * @property cost custo financeiro pago (já descontado do orçamento)
 * @property summary texto curto narrando o resultado (mostrado na lista)
 */
data class TrainingSession(
    val date: String,
    val type: TrainingType,
    val outcome: TrainingOutcome,
    val cost: Long,
    val summary: String
)

/**
 * ╔══════════════════════════════════════════════════╗
 * ║ SISTEMA DE OLHEIROS                                          ║
 * ╚══════════════════════════════════════════════════╝
 *
 * Jogadores de outros times (1ª e 2ª divisão) começam com informações
 * **ocultas** — o jogador só vê nome, role, time, idade e salário. Para
 * desbloquear atributos, overall e champion pool, precisa investir em
 * scouting.
 *
 * O departamento de olheiros possui:
 *  - Um TIER (BASIC/PRO/ELITE) que afeta velocidade e capacidade
 *  - Quantos scoutings simultâneos pode rodar (3/5/8)
 *  - Custo de manutenção semanal (descontado do orçamento)
 *
 * O upgrade do departamento custa R$ proporcional ao tier alvo e exige
 * reputação mínima do técnico.
 */

/** Tier do departamento de olheiros. */
enum class ScoutingDepartmentTier(
    val label: String,
    val maxConcurrentScouts: Int,
    val daysPerLevel: Int,
    val weeklyMaintenanceCost: Long,
    val upgradeCost: Long,
    val minReputation: Int
) {
    BASIC(
        label = "Básico",
        maxConcurrentScouts = 3,
        daysPerLevel = 3,
        weeklyMaintenanceCost = 8_000L,
        upgradeCost = 0L,
        minReputation = 0
    ),
    PRO(
        label = "Profissional",
        maxConcurrentScouts = 5,
        daysPerLevel = 2,
        weeklyMaintenanceCost = 25_000L,
        upgradeCost = 200_000L,
        minReputation = 55
    ),
    ELITE(
        label = "Elite",
        maxConcurrentScouts = 8,
        daysPerLevel = 1,
        weeklyMaintenanceCost = 80_000L,
        upgradeCost = 800_000L,
        minReputation = 75
    )
}

/**
 * Estado do departamento de olheiros. Inicializado no [BASIC] por padrão.
 */
data class ScoutingDepartment(
    var tier: ScoutingDepartmentTier = ScoutingDepartmentTier.BASIC
)