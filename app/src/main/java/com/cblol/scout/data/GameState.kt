package com.cblol.scout.data

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
    var lastSponsorOffersDate: String? = null
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
    val overallModifierReason: String? = null
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
    PERSONAL_TRAGEDY("⚫", "Tragédia")
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