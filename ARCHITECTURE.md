# Arquitetura · CBLOL Scout

Este documento descreve o padrão de código adotado no projeto. **Todo trabalho novo deve seguir essas regras** para manter consistência, testabilidade e manutenibilidade.

## Seletor de lane antes das sugestões no Pick & Ban manual

Durante cada pick do treinador na `PickBanActivity`, **antes** das sugestões contextuais aparece uma faixa de chips (TOP/JNG/MID/ADC/SUP) perguntando para qual lane esse pick será.

### Por que existe
O `PickSuggestionEngine.suggest()` recebe um `currentPlayer` para devolver sugestões contextualizadas (MAIN do champion pool dele, role natural, etc). Antes, o `currentPlayer` era deduzido pelo **índice** do pick (1º pick → TOP, 2º → JNG, etc), assumindo ordem fixa. Isso forava o coach a pickar sempre em ordem TOP→JNG→MID→ADC→SUP, o que não reflete a realidade do esporte. Agora ele declara explicitamente "este pick é da MID" antes de ver as sugestões.

### Camadas
1. **UI** (`activity_pick_ban.xml`): novo `ll_lane_picker_container` com `cg_lane_picker` chipgroup, mostrado em fase de PICK do treinador.
2. **Activity** (`PickBanActivity`): cria os 5 chips em `setupLanePicker()`, reage em `onLaneSelected(role)` (pré-marca o chip de filtro de role e dispara `refreshSuggestions`), e em `confirmAction` registra a role na lista `pickedLanes`. Default sem toque: fallback para `firstUnpickedLane()` que preserva o comportamento legacy TOP→SUP.
3. **PickSlotView**: ganhou parâmetro `roleLabel` em `setChampion` — quando o coach pickou explicitamente uma lane, o slot mostra "MID" em vez do nome do campeão, dando contexto visual.
4. **PickBanActivity → Router**: envia `RESULT_PICKED_LANES` (lista de roles em ordem dos picks do treinador) junto com os outros results.
5. **PickBanRouterActivity**: quando recebe 5 lanes (uma por pick), monta `RoleAssignment`s direto via `buildAssignmentsFromLanes` e **pula** a `RoleAssignmentActivity`. O coach já disse a lane de cada pick durante o draft — não precisa decidir de novo. Quando as lanes não vieram completas (caso defensivo), continua abrindo a `RoleAssignmentActivity` como antes.

### Lanes já utilizadas ficam disabled
O `refreshLanePicker` (chamado em todo `advanceTurn`) desabilita + esmaece (alpha 0.35) chips de roles que já estão em `pickedLanes`. Garante que cada role só é escolhida uma vez.


## Level up + Badges do técnico

O sistema de progressão do técnico foi estendido com **tela de level up dramática** + **badges desbloqueadas** com bônus passivos. Três camadas:

### 1. Modelo de dados (`data/GameState.kt`)
- `CoachProfile.unlockedBadges: MutableList<String>` — ids das badges já desbloqueadas, sempre contém `"rookie"`.
- `GameState.coachBonuses: CoachBonuses?` — bônus passivos acumulados pelos milestones (9 campos: `extraPickBanSuggestions`, `contractedMoraleBonus`, `scoutingDaysReduction`, `bondGrowthMultiplier`, `trainingOutcomeBonus`, `loanInterestReduction`, `sponsorWeeklyBonusBrl`, `incomingOfferBonusPercent`, `academyGrowthMultiplier`).
- `GameState.pendingCoachLevelUps: MutableList<Int>?` — fila de levels conquistados ainda não apresentados ao usuário.

### 2. Catálogo de recompensas (`domain/LevelUpRewards.kt`)
Objeto com 9 milestones (lv 2, 4, 7, 10, 15, 18, 20, 25, 30), cada um com `badgeId`/emoji/nome/descrição + lambda `apply(CoachBonuses)` que SOMA os bônus correspondentes. A badge `"rookie"` (lv 1) é desbloqueada automaticamente no início da carreira.

### 3. Detecção e enfileiramento (`CoachProgressionService`)
- `detectAndQueueLevelUps(state, oldXp)` — chamado APÓS `record*` em cada call site. Compara `levelFor(oldXp)` com o novo `levelFor(profile.xp)`. Para cada level cruzado: enfileira em `pendingCoachLevelUps`, adiciona badge a `unlockedBadges` se for milestone, e aplica `reward.apply(coachBonuses)`.
- `rebuildBonusesFromBadges(profile)` — usado pela migração pra reconstruir `coachBonuses` em saves antigos a partir das badges já desbloqueadas. Idempotente.

**Call sites** onde XP é ganho e `detectAndQueueLevelUps` foi adicionado:
- `MatchResultActivity.awardCoachXp` (vitórias/derrotas + série + off-match event)
- `PickBanActivity.registerPickBanXp` (pick & ban manual + bonus perfect draft)
- `TransferMarket.sellPlayer`/`buyPlayer`/`renegotiateContract`/`acceptIncomingOffer`

### 4. UI (`CoachLevelUpActivity`)
- Layout dramático: "LEVEL UP!" dourado, "Nv X → Nv Y" com animação overshoot, título derivado.
- Dois blocos via `View.GONE`:
  - **Milestone**: badge gigante + nome + descrição + bullets de bônus (construídos dinamicamente).
  - **Intermediário**: mensagem motivacional + "próximo marco: Nv X · emoji Nome".
- Botão CONTINUAR remove o level da fila e fecha; se ainda há levels enfileirados, o próximo `onResume` do Hub abre de novo (cadeia natural).
- `onBackPressed()` consumido para evitar sair sem desempilhar.

### 5. Trigger (`ManagerHubActivity.onResume`)
Nova função `showPendingCoachLevelUpIfAny()` chamada ANTES de `showPendingOffMatchEventIfAny()`. Lê o primeiro level da fila e abre a Activity — sem laço, sem complexidade.

### 6. Visível no perfil (`CoachProfileDialog`)
Nova seção "🏅 BADGES" com lista de todos os 9 milestones. Cada linha:
- **Desbloqueado**: emoji da badge + nome bold + descrição em cinza
- **Bloqueado**: emoji esmaecido alpha 0.4 + cadeado 🔒 + nome cinza + legenda "Desbloqueia no Nv X"

### 7. Consumo dos bônus em serviços
- `MoraleService.recordPlayerHired` — soma `contractedMoraleBonus` ao delta inicial de moral
- `IncomingOfferService.applyCoachOfferBonus` — multiplica valor da oferta por `(100 + incomingOfferBonusPercent)/100`
- Os demais bônus (`extraPickBanSuggestions`, `scoutingDaysReduction`, `bondGrowthMultiplier`, `trainingOutcomeBonus`, `loanInterestReduction`, `sponsorWeeklyBonusBrl`, `academyGrowthMultiplier`) estão **declarados** no modelo `CoachBonuses` mas não consumidos ainda — cada serviço pode lê-los quando for natural fazer.

### Compatibilidade com saves antigos
- `GameStateMigrator.migrateCoachBonuses` reconstrói `coachBonuses` a partir das badges já desbloqueadas (chama `rebuildBonusesFromBadges`). Idempotente.
- `migrateCoachProfile` garante que `unlockedBadges` sempre contém `"rookie"`.
- Saves novos: nascem com badge `"rookie"` e `CoachBonuses()` vazio.


---

## Visão geral

```
┌─────────────────────────────────────────────────────────────────┐
│                          ui/ (Activities)                       │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ - SRP: cada Activity tem uma responsabilidade visual única │ │
│  │ - Lê strings de R.string, cores de R.color, dims de R.dimen│ │
│  │ - Não contém regra de negócio (delega para UseCase/Engine) │ │
│  │ - Companion object com EXTRA_* constantes (chaves Intent)  │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                                ▲
                                │ depende de
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                  ui/viewmodel/ (LiveData state)                 │
│  - Expõe LiveData de estado                                     │
│  - Não toca em View nem Context                                 │
│  - Recebe UseCases via construtor (Koin injeta)                 │
└─────────────────────────────────────────────────────────────────┘
                                ▲
                                │ depende de
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                  domain/usecase/ (regra de negócio)             │
│  - Sealed classes para resultados (BuyResult.Ok/Error etc.)     │
│  - Métodos puros: entrada → saída, sem side-effects             │
│  - Pode chamar GameRepository, mas não Android SDK              │
└─────────────────────────────────────────────────────────────────┘
                                ▲
                                │ depende de
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│   game/ (engines)    util/ (repos)   data/ (DTOs)               │
│  - GameEngine, LiveMatchEngine, MatchSimulator, etc.            │
│  - ChampionRepository, CompositionRepository                    │
│  - Models.kt, GameState.kt, MatchEvent.kt                       │
└─────────────────────────────────────────────────────────────────┘
                                ▲
                                │ depende de
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                  domain/GameConstants.kt                        │
│  - Constantes de regra de negócio                               │
│  - Economy, Series, Synergy, Draft, Player, Schedule, Result    │
└─────────────────────────────────────────────────────────────────┘
```

A dependência **só flui para baixo**. Nunca uma camada inferior conhece uma superior.

---

## Princípios SOLID — como aplicar

### S — Single Responsibility

Cada classe ou função faz **uma** coisa.

✅ **Bom** (`MatchSimulationActivity`):

```kotlin
private fun renderEvent(event: MatchEvent) {
    when (event) {
        is MatchEvent.Pick   -> onPick(event)
        is MatchEvent.Kill   -> onKill(event)
        is MatchEvent.Dragon -> onDragon(event)
        // ...
    }
}

private fun onPick(event: MatchEvent.Pick) { /* só lida com pick */ }
private fun onKill(event: MatchEvent.Kill) { /* só lida com kill */ }
```

❌ **Ruim**: um único `renderEvent` com 200 linhas e 12 ramos de `when` inline.

### O — Open/Closed

Estruturas declarativas permitem adicionar coisas sem mudar lógica.

✅ **Bom** (`PickBanActivity`):

```kotlin
private val DRAFT_TAGS_RESOURCE: List<Pair<ChampionTag, Int>> = listOf(
    ChampionTag.TANK to R.string.tag_tank,
    ChampionTag.ENGAGE to R.string.tag_engage,
    // ... adicionar uma tag aqui não muda setupTagFilters()
)
```

### L — Liskov Substitution

`MatchEvent` é sealed class; cada subtipo é substituível pelo pai sem quebrar o consumer. Aplicar quando criar novas hierarquias de tipo.

### I — Interface Segregation

`UseCases` retornam resultados granulares (`BuyResult.Ok`, `BuyResult.NotEnoughMoney`, `BuyResult.Error`). A Activity só lê o que precisa.

### D — Dependency Inversion

Activities dependem de **abstrações** (UseCase, Repository), nunca de Activities concretas. Constantes vêm de `GameConstants`, não de literais inline.

---

## Onde colocar cada coisa

| Tipo de valor                          | Local                                              | Exemplo                                  |
| -------------------------------------- | -------------------------------------------------- | ---------------------------------------- |
| Texto exibido ao usuário (PT-BR)       | `res/values/strings.xml`                           | `R.string.dialog_exit_pickban_title`     |
| Format string com placeholders         | `res/values/strings.xml` com `%1$s`, `%1$d`        | `R.string.synergy_single_comp`           |
| Cor hex usada em Kotlin                | `res/values/colors.xml`                            | `R.color.sim_home_accent`                |
| Cor estática usada apenas em XML       | `res/values/colors.xml`                            | `@color/color_primary`                   |
| Dimensão visual (dp/sp)                | `res/values/dimens.xml`                            | `R.dimen.pickban_synergy_bars_height`    |
| Regra de negócio (prêmio, multiplica.) | `domain/GameConstants.kt`                          | `GameConstants.Economy.PRIZE_PER_MAP_WIN`|
| Constante interna de uma classe        | `companion object` da própria classe               | `private const val ALPHA_DIMMED = 0.4f`  |
| Chave de Intent extra                  | `companion object` da Activity destino             | `MatchResultActivity.EXTRA_HOME_NAME`    |
| ID de tag (chave do adapter)           | `companion object` privado, `const val`            | `private const val ROLE_ALL = "ALL"`     |

### Por que não tudo em `res/`?

- `res/values/` só pode ser lido por código que tem acesso ao `Context` Android.
- Constantes de domínio (`GameConstants`) precisam estar em código Kotlin puro para serem usadas por engines, testes JUnit e (futuramente) módulos não-Android.
- Acoplar regra de negócio a `R.integer.*` torna a regra invisível para quem lê o domínio.

### Quando uma constante deve virar `GameConstants` vs `companion object`?

- **`GameConstants`**: a constante governa uma **regra do jogo** que afeta múltiplas classes. Ex: `MAPS_TO_WIN`, `PRIZE_PER_MAP_WIN`, `SYNERGY_BAR_SCALE`.
- **`companion object` local**: a constante é **detalhe de implementação** de uma única classe. Ex: `ALPHA_DIMMED = 0.4f` (só importa para `PickBanActivity`).

---

## Estrutura do `GameConstants`

```kotlin
object GameConstants {
    object Economy   { /* prêmios, salários, brackets */ }
    object Series    { /* MAPS_TO_WIN, HOME_SIDE_BONUS */ }
    object Synergy   { /* pesos da análise de composição */ }
    object Draft     { /* pick & ban, IA, grid */ }
    object Player    { /* OVERALL_DIFF_HUGE, DEFAULT_OVERALL */ }
    object Schedule  { /* TEAMS_COUNT, ROUNDS_TOTAL */ }
    object Result    { /* delays/durations de animação */ }
    object Simulation { /* delays de eventos da simulação */ }
}
```

Cada bounded context é um `object` aninhado. Não criar constantes top-level no `GameConstants` raiz — sempre dentro de um sub-objeto.

---

## Regras de strings

### Sempre

- Toda string exibida ao usuário **deve** estar em `R.string.*`.
- Format strings usam placeholders `%1$s`, `%1$d`, `%1$d%%`, etc.
- Ícones emoji são strings também (`R.string.icon_kill`, `R.string.icon_dragon`). Centraliza para trocar em massa se decidir mudar o estilo visual.

### Nunca

- `setText("texto")` ou `setText("Mapa $n")` direto em código.
- Concatenação de strings traduzíveis com `+`.
- Strings de log/debug **podem** ficar inline (não são exibidas ao usuário).

### Padrão de nomenclatura

```
dialog_<nome>_title           → título de dialog
dialog_<nome>_message         → corpo de dialog
btn_<acao>                    → texto de botão genérico
<tela>_<contexto>_<sufixo>    → demais (ex: sim_phase_in_game, presim_header_synergy)
event_<tipo>                  → format strings de eventos do feed
icon_<contexto>               → emojis isolados
```

---

## Regras de cores

### Sempre

- Cores **dinâmicas** usadas em código Kotlin: ficam em `res/values/colors.xml` com nome semântico (`sim_home_accent`, `result_victory_text`).
- Cores estáticas de layout XML: também em `colors.xml` referenciadas como `@color/...`.
- Acesso em código: `ContextCompat.getColor(this, R.color.x)` ou através do helper `color(@ColorRes res: Int)` da Activity.

### Nunca

- `Color.parseColor("#RRGGBB")` inline.
- `setBackgroundColor(0xFF0AC8B9.toInt())`.

---

## Regras de dialogs

Todos os dialogs **devem** usar o helper:

```kotlin
stylizedDialog(this)
    .setTitle(R.string.dialog_xxx_title)
    .setMessage(R.string.dialog_xxx_message)
    .setPositiveButton(R.string.btn_yyy) { _, _ -> doSomething() }
    .show()
```

- Nunca usar `AlertDialog.Builder(this)` direto — ignora o tema customizado.
- Títulos sempre em `R.string.dialog_<contexto>_title`.

---

## Padrão de Activity

```kotlin
class MyActivity : AppCompatActivity() {

    // 1. Bindings/lateinits no topo (organizados por seção visual)
    private lateinit var binding: ActivityMyBinding

    // 2. Estado mutável
    private var someState = 0

    // ── Lifecycle ────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        readIntentExtras()       // ← SRP
        bindViews()
        setupListeners()
        loadInitialData()
    }

    // ── Inicialização ───────────────────────────────────────────────────
    private fun readIntentExtras() { ... }
    private fun bindViews() { ... }
    private fun setupListeners() { ... }
    private fun loadInitialData() { ... }

    // ── Renderização ────────────────────────────────────────────────────
    private fun render(state: State) { ... }

    // ── Ações do usuário ────────────────────────────────────────────────
    private fun onSomethingClicked() { ... }

    // ── Helpers ─────────────────────────────────────────────────────────
    private fun color(@ColorRes res: Int) = ContextCompat.getColor(this, res)

    companion object {
        const val EXTRA_X = "extra_x"
        private const val SOME_LOCAL_THRESHOLD = 0.5f
    }
}
```

### Métodos `on<Event>` para sealed classes

Quando uma Activity processa `sealed class` (como `MatchEvent`), o padrão é:

```kotlin
private fun renderEvent(event: MatchEvent) = when (event) {
    is MatchEvent.Pick    -> onPick(event)
    is MatchEvent.Kill    -> onKill(event)
    is MatchEvent.Dragon  -> onDragon(event)
    // ...
}

private fun onPick(event: MatchEvent.Pick) { /* curto, foco em uma coisa */ }
private fun onKill(event: MatchEvent.Kill) { /* idem */ }
```

---

## Snapshot imutável de dados de Intent

Para Activities que recebem muitos extras, encapsular em data class imutável:

```kotlin
private data class MatchResultData(
    val homeName: String,
    val awayName: String,
    val homeScore: Int,
    // ...
) {
    val isMyMatch: Boolean get() = managerId == homeId || managerId == awayId
    val playerWon: Boolean get() = isMyMatch && winnerId == managerId

    companion object {
        fun fromIntent(intent: Intent) = MatchResultData(
            homeName = intent.getStringExtra(EXTRA_HOME_NAME) ?: "",
            // ...
        )
    }
}
```

Benefícios:
- Properties derivadas (`isMyMatch`, `playerWon`) ficam declarativas e em um só lugar.
- `onCreate` fica curto: `val data = MatchResultData.fromIntent(intent)`.
- Fácil de testar — passe um `MatchResultData` fake para um helper de renderização.

---

## Testes

- Testes unitários (JUnit) ficam em `app/src/test/java/`.
- Testes **podem importar `GameConstants`** porque é Kotlin puro.
- Testes **não podem importar Activities** (acoplamento com Android Framework).
- Para constantes deprecadas mantidas para compat de teste, marcar com `@Deprecated` e apontar para o substituto:

```kotlin
@Deprecated("Use GameConstants.Economy.PRIZE_PER_MAP_WIN")
const val PRIZE_PER_MAP_WIN = GameConstants.Economy.PRIZE_PER_MAP_WIN
```

---

## Checklist antes de commitar uma Activity

- [ ] Nenhuma string em PT-BR inline (todas em `R.string.*`)
- [ ] Nenhum `Color.parseColor("#...")` (todas em `R.color.*` + `ContextCompat.getColor`)
- [ ] Nenhum número mágico de regra de negócio (em `GameConstants.X.Y`)
- [ ] Nenhum `AlertDialog.Builder` direto (sempre `stylizedDialog`)
- [ ] `onCreate` curto, com chamadas a métodos `setupX/bindX/loadX`
- [ ] Métodos `on<Event>` curtos quando processa sealed class
- [ ] Constantes locais em `companion object` privado
- [ ] Chaves de Intent em `companion object` público (`EXTRA_*`)
- [ ] Dimens visuais em `R.dimen.*` quando reutilizadas
- [ ] Documentação SOLID no KDoc da classe explicando aplicação dos princípios

---

## Onde está o quê (índice rápido)

| Coisa                                | Arquivo                                        |
| ------------------------------------ | ---------------------------------------------- |
| Todas as strings em PT-BR            | `app/src/main/res/values/strings.xml`          |
| Todas as cores hex                   | `app/src/main/res/values/colors.xml`           |
| Dimensões reutilizadas               | `app/src/main/res/values/dimens.xml`           |
| Regras de negócio (constantes)       | `app/src/main/java/com/cblol/scout/domain/GameConstants.kt` |
| Tema do dialog                       | `app/src/main/res/values/themes.xml` (`ThemeOverlay.CBLOLScout.Dialog`) |
| Helper de dialog                     | `app/src/main/java/com/cblol/scout/ui/DialogHelper.kt` (`stylizedDialog`) |
| Composições e análise de sinergia    | `app/src/main/java/com/cblol/scout/util/CompositionRepository.kt` |
| Tags de campeões                     | `app/src/main/java/com/cblol/scout/data/ChampionTag.kt` |
| Dialog de pré-simulação              | `app/src/main/java/com/cblol/scout/ui/PreSimulationDialog.kt` |
| Fonte de dados estáticos (interface) | `app/src/main/java/com/cblol/scout/domain/datasource/StaticDataSource.kt` |
| Banco Realm criptografado (impl)     | `app/src/main/java/com/cblol/scout/data/realm/RealmStaticDataSource.kt` |
| Dados de seed (campeões/comps/etc.)  | `app/src/main/java/com/cblol/scout/data/seed/` |

---

## Camada de dados estáticos (Realm criptografado)

Os dados estáticos do jogo — campeões (roles, tags, ddragonId), composições,
patrocínios e champion pools — **não ficam hardcoded na lógica**. Eles vivem em
um **banco Realm criptografado** (AES-256) e são acessados por uma abstração.

### Fluxo

```
Repositórios (object)              StaticData (holder)        Realm criptografado
ChampionRepository      ──lê──>   StaticData.source   ──>   RealmStaticDataSource
CompositionRepository                  ▲                    (cache em memória +
SponsorCatalog                         │ install()           seed na 1ª execução)
ChampionPoolRepository                 │
                              CBLOLApp.onCreate (prod)
                              @Before dos testes (fake)
```

- **`domain/datasource/StaticDataSource`**: interface (DIP) com as leituras dos
  dados estáticos. Não conhece Realm.
- **`data/realm/RealmStaticDataSource`**: implementação que abre o Realm
  criptografado, faz **seed** na primeira execução (a partir de `data/seed/*`) e
  serve os dados de um cache imutável em memória.
- **`data/realm/RealmKeyProvider`**: gera/protege a chave de 64 bytes do Realm
  via **Android Keystore** (envelope encryption — a chave nunca é persistida em
  claro).
- **`data/realm/RealmEntities`**: entidades `RealmObject` + mapeadores
  `toDomain()`/`fromDomain()`. Enums viram `String` (o Realm não suporta enum).
- **`data/StaticData`**: holder que liga o mundo `object` (repositórios) à
  implementação injetada. Instalado no startup.
- **`data/seed/*`**: ÚNICA fonte hardcoded restante, usada só para popular o
  Realm. Não é consultada em runtime pela lógica do jogo.

### Regras

- **Novos dados estáticos** (ex: itens, runas): criar entidade Realm + mapeadores,
  adicionar ao schema do `RealmStaticDataSource`, criar o seed em `data/seed/` e
  expor leitura na interface `StaticDataSource`.
- **Nunca** voltar a hardcodar listas de dados na lógica (`util/`, `game/`,
  `domain/`). Dado é seed → Realm; lógica fica nos repositórios/serviços.
- **Testes JVM**: usam `InMemoryStaticDataSource` (mesmos seeds), instalado via
  `installTestStaticData()` no `@Before`. O runtime nativo do Realm não roda em
  testes JVM puros, por isso a abstração.

---

## Mercado de transferências dinâmico (pedidos de saída + ofertas recebidas)

Além de comprar/vender por iniciativa do gerente, o mercado é **vivo**: jogadores
podem pedir para sair e times rivais mandam propostas durante as janelas.

### Jogador pede para sair

`MoraleService.applyDailyDecay` (rodado a cada dia avançado) avalia, por jogador,
se há motivo para pedir transferência via `transferRequestReason`:

- **Moral no fundo** (`<= TRANSFER_REQUEST_THRESHOLD`, 10): profundamente infeliz.
- **Reserva frustrado**: insatisfeito (`<= DISSATISFIED_THRESHOLD`, 30), não-titular
  e há mais de `RESERVE_FRUSTRATION_DAYS` (21) dias no banco.

Havendo motivo, sorteia (`TRANSFER_REQUEST_DAILY_PROB`, 15%/dia) e, se sair, marca
`PlayerOverride.transferRequestedOn` + registra no histórico de moral. O pedido é
limpo ao renovar contrato ou vender (`clearTransferRequest`). Novos motivos entram
em `transferRequestReason` (OCP) sem mexer no loop de decay.

### Outros times oferecem (ofertas recebidas)

`IncomingOfferService` (domain/usecase, JVM-puro) gera/expira/avalia propostas:

- **Geração** (`generateOffersIfDue`): só com mercado aberto
  (`TransferWindowService.isMarketOpen`), a cada `OFFER_INTERVAL_DAYS` (3) dias.
  Prioriza quem pediu pra sair (prob 0.70) > craques (0.25) > demais (0.06).
  Valor = preço de mercado × multiplicador aleatório (+bônus se pediu pra sair).
  Respeita teto de ofertas ativas e não duplica alvo.
- **Expiração** (`expireOffers`): remove ofertas vencidas (validade ou janela
  fechada).
- **Resposta**: `TransferMarket.acceptIncomingOffer` (vende pelo valor da proposta,
  reusando a mecânica de venda) e `TransferMarket.rejectIncomingOffer` (descarta +
  `MoraleService.recordTransferOfferRejected`, que dói mais se o jogador queria sair).

O motor (`GameEngine.processIncomingOffers`) chama isso nos ticks diários e
reporta no `AdvanceReport.incomingOffers`. A UI fica em `IncomingOffersActivity`
(card 📩 "Propostas" no Hub, com badge de contagem). Persistência:
`GameState.incomingOffers` + `lastIncomingOffersDate` (migrados defensivamente no
`GameRepository`).

**Por que `IncomingOfferService` é separado do `TransferMarket`?** O TransferMarket
cuida de transações iniciadas pelo gerente; o IncomingOfferService cuida do fluxo
inverso (iniciativa da IA). Separar respeita SRP e mantém a regra de geração
isolada e testável (`IncomingOfferServiceTest`, `MoraleTransferRequestTest`).

---

## Sistema de laços entre jogadores (química / chemistry)

Cada par de jogadores do elenco tem um **laço** (-100 a +100) que evolui com o
tempo e conecta vários sistemas. A regra vive em `PlayerBondService`
(domain/usecase, JVM-puro) e os dados em `GameState.playerBonds`
(`Map<chave, PlayerBond>`, chave canônica `PlayerBond.keyFor` = ids ordenados).

### Como o laço evolui

- **Tempo de convivência** (`tickDaily`, rodado nos ticks diários do
  `GameEngine.processPlayerBonds`): a cada `DRIFT_INTERVAL_DAYS` (3) dias de
  convivência o laço dá um passo de drift, **modulado pelo humor médio da
  dupla** (lido do `MoraleService`):
  - ambos felizes (média ≥ 66): esquenta acelerado;
  - clima ruim (média ≤ 34): azeda;
  - neutro: converge devagar para `NATURAL_DRIFT_TARGET` (45).
- **Contrato**: se um dos dois pediu transferência
  (`MoraleService.hasRequestedTransfer`), o laço sofre `TRANSFER_REQUEST_BOND_PENALTY`
  (-2) por dia — clima pesado no vestiário.
- **Resultado** (`recordSeriesResult`, chamado na `MatchResultActivity` ao fim da
  série): vitória +3 / derrota -2 em todos os pares titulares.
- **Eventos fora de partida** (`recordCombo` +12 / `recordFight` -16): ver abaixo.

Drift e deltas são sempre clampados em [-100, 100]. `tickDaily` é idempotente por
data via `GameState.lastBondTickDate`.

### Efeito na simulação

`teamStrengthBonus` = `round(laço_médio_do_time × TEAM_BOND_STRENGTH_FACTOR)`
(fator 0.08 → time totalmente entrosado ganha +8 de força; tóxico perde 8). O
`LiveMatchEngine` soma esse bônus à força de cada lado, junto de moral,
composição e mains.

### Eventos de jogada ensaiada / briga

Duas novas categorias de `OffMatchEvent` (`TEAM_COMBO`, `TEAM_FIGHT`) entram no
`OffMatchEventService`:

- **Jogada ensaiada** (`buildTeamCombo`): escolhe a dupla mais entrosada
  (`pickComboPair`), fortalece o laço (+12) e dá moral leve aos dois.
- **Briga** (`buildTeamFight`): escolhe a dupla com pior química
  (`pickFightPair`), deteriora o laço (-16) e tira moral dos dois.

O `OffMatchEvent` ganhou `secondPlayerId/Name` + `bondDelta` para a
`OffMatchEventActivity` exibir os dois nomes e a variação de química.

### UI e persistência

`PlayerBondsDialog` (aberto pelo menu "Laços" na `SquadActivity`) mostra a
química média + bônus de força e a lista de pares com faixa
(`BondTier`: ☠️ Rivalidade / ⚡ Atrito / 🤝 Neutro / 😎 Entrosados / 🔥 Parceria).
Laços são inicializados (neutros) em `GameEngine.startNewCareer`
(`ensureBondsFor`) e migrados defensivamente no `GameRepository`. Testes em
`PlayerBondServiceTest`.

**Por que separado do `MoraleService`?** Moral é um valor por **jogador**; laço é
uma relação por **par**. Modelos e regras distintos → serviços distintos (SRP). O
`PlayerBondService` *lê* moral e flag de transferência, mas não os reimplementa
(DIP).

---

## Categoria de base (academia)

A organização tem uma **academia** que forma jovens promessas do zero. Vive em
`AcademyService` (domain/usecase, JVM-puro); os dados em `GameState.academy`
(um `Academy` com tier + lista de `AcademyProspect`) e os jogadores já
promovidos em `GameState.promotedPlayers`.

### Diferença vs. 2ª divisão / mercado

O mercado e a 2ª divisão (`SecondDivisionGenerator`) vendem jogadores
**prontos** — caro e imediato. A academia forma talentos **crus**: cada
`AcademyProspect` começa com overall baixo (45-62) e um **potencial oculto**
(até o teto do tier), revelado só após **avaliação** (`evaluateProspect`, custa
R$ 15k). Barato no curto prazo (só manutenção semanal), mas leva tempo e o
retorno é incerto.

### Ciclo de vida do prospect

1. **Recrutado** — automaticamente a cada 30 dias (`tickDaily`) se há vaga, ou
   manualmente (`recruitManually`, R$ 50k).
2. **Avaliado** (opcional) — revela o potencial exato.
3. **Desenvolvido** — a cada 7 dias de convivência o overall sobe rumo ao
   potencial (`developmentStepFor`), modulado por tier (`growthFactor`), idade
   (jovens aprendem mais rápido) e proximidade do teto (rendimento decrescente).
4. **Promovido** (`promoteProspect`) — sai da base e o
   `GameRepository.addPromotedProspect` materializa um `Player` real (entra como
   reserva, contrato `fonte_salario="base"`), seguido de
   `SquadManager.validateAndFixRoster`. **Ou liberado** (`releaseProspect`).

### Tiers

`AcademyTier` (BASIC/PRO/ELITE) controla capacidade (4/6/8), velocidade de
desenvolvimento (1.0/1.4/1.9×), potencial máximo dos recrutas (78/85/92) e custo
semanal (10k/35k/90k). Upgrade exige reputação mínima e custo, igual ao
departamento de olheiros.

### Integração no motor

`GameEngine.processDailyTicks` chama `processAcademy` (desenvolve + recruta,
logando marcos via `AdvanceReport.academyReady`). A manutenção semanal é
cobrada no bloco de domingo, ao lado da dos olheiros.
`startNewCareer` chama `initializeForNewCareer` (leva inicial = metade da
capacidade). O `rosterOf` une três fontes — snapshot (1ª div) +
`SecondDivisionGenerator` (2ª div) + `promotedPlayers` (base). UI em
`AcademyActivity` (abas PROSPECTS / ACADEMIA), acessível pelo card 🌱 Base do
Hub. Testes em `AcademyServiceTest`.

**Por que `promotedPlayers` no `GameState`?** Um prospect promovido não existe
em nenhuma fonte estática (nem snapshot, nem gerador procedural), então precisa
ser persistido no save para sobreviver entre sessões. Os overrides normais
(salário, titularidade, moral) continuam valendo por cima, indexados pelo id.

---

## Banco (empréstimos emergenciais)

O **banco** é a rede de segurança financeira do gerente — ele dá crédito à
vista em troca de parcelas semanais com juros, evitando que o orçamento zerar
seja fim de jogo. Vive em `BankService` (domain/usecase, JVM-puro), com estado
em `GameState.bank` (`BankState` com lista de [BankLoan] ativos + metadados).

### Saúde financeira e aviso visual

O mesmo serviço classifica o caixa em 3 faixas pelo orçamento atual
(`GameConstants.Bank`):

- 🟢 **HEALTHY** — acima de R$ 600k.
- 🟡 **WARNING** — entre R$ 100k e R$ 600k. "Caixa apertado."
- 🔴 **CRITICAL** — abaixo de R$ 100k (ou negativo). "Caixa crítico!"

Isto alimenta o **aviso visual** no Hub (subtitle dourado/vermelho no card
🏦 Banco + Toast ao fim de cada avanço de dia, via
`AdvanceReport.financialHealthWarning`) e o **banner colorido** no topo da
`BankActivity` com a dica acionável de `healthAdvice` ("Considere vender
jogadores...", "Acompanhe a folha...", etc.).

### Ciclo de um empréstimo

1. **Catálogo dinâmico** (`offersFor`): quatro linhas de crédito escalonadas
   pelo limite do time — 💸 micro (15% do limite, 8% juros, 6 semanas),
   🆘 emergencial (35%, 15%, 10 semanas), 📈 investimento (65%, 22%, 16
   semanas, reputação 45+) e 🏦 estrutural (100%, 30%, 24 semanas, reputação
   65+). O catálogo é filtrado pelo crédito ainda disponível.
2. **Contratação** (`takeLoan`): credita o principal no orçamento e cria um
   [BankLoan] com parcela calculada (`principal × (1+juros) ÷ semanas`,
   arredondada para cima).
3. **Cobrança semanal** (`chargeWeeklyInstallments`): o
   `GameEngine.processDailyTicks` chama todo domingo. Idempotente por semana
   via `BankState.lastInstallmentDate` (só cobra se passaram ≥ 7 dias). Cada
   empréstimo paga uma parcela; quitados são removidos da lista.
4. **Quitação antecipada** (`payOffEarly`): paga todo o saldo devedor de uma
   vez, livra das parcelas futuras. Exige saldo no caixa.

### Limite de crédito (anti-dívida impagável)

`creditLimit` = `patrocínioSemanal × 20 × repFactor`, com piso de R$ 500k.
`repFactor = 0.5 + reputação/100` (varia 0.5x a 1.5x). O catálogo nunca oferece
mais do que o saldo de crédito (limite − dívida atual) — ou seja, o jogador
NÃO consegue se afundar mais do que consegue pagar com o patrocínio. É a
linha de defesa que justifica deixar o empréstimo ser uma ferramenta livre, e
não algo "travado" por mecânicas externas.

### Integração e UI

`GameEngine.processDailyTicks` chama `BankService.chargeWeeklyInstallments` no
bloco de domingo (depois dos pagamentos de patrocínios e manutenções) e marca
`report.financialHealthWarning` ao final, se a saúde ficou em zona de atenção.
A `BankActivity` (abas EMPRÉSTIMOS / MINHAS DÍVIDAS) acessada pelo card 🏦 Banco
do Hub mostra ofertas, empréstimos ativos (com barra de quitação + saldo +
botão QUITAR ANTECIPADO) e o banner colorido de saúde financeira. Testes em
`BankServiceTest`.

**Por que separado dos demais serviços financeiros?** Patrocínios geram
receita; folha salarial é despesa fixa; o banco é uma fonte de **liquidez
emergencial com custo futuro** — uma terceira categoria com regras próprias
(juros, parcelas semanais, limite de crédito). Separar respeita SRP e mantém a
rede de segurança fora do caminho crítico do orçamento mensal.

---

## Modo "começar na 2ª divisão"

O jogador pode iniciar a carreira tanto na **1ª divisão (CBLOL)** — com os 8
times do snapshot oficial — quanto na **2ª divisão (Circuito Desafiante)**, com
8 times procedurais gerados na hora. As duas vivem como entidades de primeira
classe no `GameState`, distinguidas pelo enum `Division`.

### Por que procedural na 2ª divisão?

Times reais do CD mudam todo split, e ter dados oficiais por trás seria pesado
e ficaria datado em semanas. O `SecondDivisionTeamsGenerator` cria 8 times com
nomes fictícios ("Aurora E-sports", "Phoenix Gaming"...) e roster completo (5
titulares + 1 reserva) a partir de um seed. Mesmo seed → mesma saída, o que
permite à `TeamSelectActivity` mostrar exatamente os times que serão criados
na carreira (consistência de UX).

### O que muda entre divisões

- **Orçamento e patrocínio**: a 2ª divisão usa
  `GameConstants.Economy.STARTING_BUDGET_SECOND_DIV` (R$ 500k) e
  `WEEKLY_SPONSOR_SECOND_DIV` (R$ 80k/semana) — bem menores que o tier B da 1ª.
  É o que torna o modo um "começar de baixo": obriga o uso ativo do banco, da
  categoria de base e do mercado de jogadores baratos.
- **Roster do gerente**: vem de `gs.secondDivisionPlayers` em vez do snapshot.
  O `GameRepository.rosterOf` já une as 4 fontes (snapshot + free agents CD +
  academia + roster da 2ª div), então o restante do código não precisa saber
  da divisão ativa.
- **Mercado**: o `GameRepository.marketRoster` filtra por divisão. Em carreira
  na 2ª, jogadores da 1ª div ficam **fora de alcance** (não aparecem no
  mercado) — a economia da 2ª div não cobriria mesmo, e a separação é uma
  trava narrativa coerente. Free agents do CD ficam disponíveis em ambas.
- **Classificação e adversários**: o `MatchSimulator.computeStandings` e
  outras telas que listam times usam `GameRepository.teamsForCurrentDivision`
  em vez de `snapshot.times`. Sem isso, a classificação ficaria vazia (os ids
  `cd2_*` não existem no snapshot).
- **Cores dos times**: o `TeamColors.forTeam` gera uma cor estável via hash do
  id quando o time tem prefixo `cd2_`, evitando ter que cadastrar 15 paletas.

### Fluxo do `startNewCareer`

O `GameEngine.startNewCareer` agora aceita `division` e `seed`. O método
privado `resolveDivisionSetup` empacota os parâmetros iniciais (orçamento,
lista de times, roster) conforme a divisão escolhida, mantendo o fluxo
principal limpo. Em carreira na 2ª div, `gs.secondDivisionTeams` e
`gs.secondDivisionPlayers` são populados ANTES do primeiro `rosterOf` — sem
isso, `PlayerBondService.ensureBondsFor` pegaria roster vazio.

### UI

A `TeamSelectActivity` ganhou um `TabLayout` com duas abas. O seed da 2ª
divisão é sorteado uma vez no `onCreate` e reutilizado tanto para gerar a
lista mostrada quanto para a chamada `vm.startCareer(..., seed)`, garantindo
que o time que o usuário vê é exatamente o que existirá na carreira.

### Promoção/rebaixamento

**NÃO implementados nesta iteração** — escopo controlado. O split na 2ª
divisão termina e o próximo começa na mesma divisão. Promoção automática pelo
desempenho é um próximo passo natural (`GameState.division` já é mutável para
isso) e a infraestrutura toda já contempla as duas divisões coexistindo.

---

## Feed de notícias

O **feed de notícias** simula a cobertura da imprensa de esports sobre a
carreira do gerente. Vive em `NewsService` (domain/usecase, JVM-puro), com o
feed persistido em `GameState.news` (lista de `NewsItem`, mais recentes
primeiro, limitada a `MAX_FEED_SIZE = 40`).

### Conceito e diferença do log

O `gameLog` (`GameRepository.log`) é o registro **cru e técnico** dos eventos
("Rodada 3: Meu Time 2-0 Rival"). A notícia é a **leitura jornalística** do
mesmo fato, com fonte fictícia, manchete chamativa e lead ("Atropelo! Meu Time
não toma sustos e despacha o Rival por 2-0"). São dois níveis de abstração:
manter separado respeita SRP e deixa o tom das notícias evoluir sem mexer no
log. **As notícias não afetam a simulação** — são puramente imersivas.

### Fontes fictícias

Para não colidir com veículos reais, as fontes são portais inventados, agrupados
por afinidade de editoria (mas qualquer um pode cobrir qualquer assunto):
partidas (Rift Report, Linha de Base, Central do Rift), mercado/finanças
(Mercado GG, Janela Aberta, Boletim Pro), geral (Portal Nexus, Esports Já,
Tribuna Gamer) e bastidores (Bastidores GG, Raio-X do Elenco, Fofoca do Rift).
Cada manchete tem 3-4 variações sorteadas (`pick(...)`) para o feed não repetir
o mesmo texto.

### Eventos cobertos e prioridade editorial

Cada `report*` publica um `NewsItem` com uma prioridade (HEADLINE/HIGH/MEDIUM/
LOW). A prioridade ordena empates de data e define a manchete de capa no Hub:

- **Partidas** (`reportMatchResult`): editorializa por contexto — zebra
  (favorito caiu, HEADLINE), goleada 2-0, jogo apertado 2-1, tropeço. O
  chamador informa `wasUpset` comparando a força dos elencos
  (`MatchSimulator.teamStrength`), mantendo o serviço desacoplado.
- **Marcos de jogador** (`reportPlayerMilestone`): atuações de destaque.
- **Transferências** (`reportSigning`/`reportDeparture`): chegada cara é HIGH.
- **Base** (`reportAcademyPromotion`): joia de overall ≥ 70 é HEADLINE.
- **Finanças** (`reportSponsorship`/`reportFinancialCrisis`).
- **Vestiário** (`reportStrongBond`/`reportLockerRoomCrisis`/
  `reportTransferRequest`): química e crises.
- **Tabela** (`reportStandings`): liderança e zona de risco são HIGH.

### Hooks de geração

- `GameEngine.simulateMatchesOn` → `publishMatchNews` para partidas
  auto-simuladas do gerente.
- `MatchSimulationActivity.applyResult` → `publishMatchNews` para partidas
  jogadas/assistidas manualmente. As duas usam a mesma heurística de zebra.
- `GameEngine.processPlayerBonds` → parceria forte / crise tóxica.
- `GameEngine` (decay de moral) → pedido de transferência.
- `AcademyActivity.onPromote` → promoção da base.

Outros pontos (patrocínio assinado, venda/compra no mercado, crise financeira
no tick) têm métodos prontos no serviço e podem ser plugados conforme desejado.

### UI

O card 📰 Notícias no Hub mostra a manchete de maior destaque
(`NewsService.latestHeadline`) como subtítulo; tocá-lo abre a `NewsActivity`,
que lista o feed completo via `item_news` (barra lateral colorida por
editoria, fonte + data + categoria + manchete + lead). Testes em
`NewsServiceTest`.

**Por que separado dos demais sistemas?** A notícia é uma camada de
*apresentação narrativa* sobre eventos que já existem. Concentrar a redação num
único serviço (em vez de espalhar strings pelos hooks) mantém o tom consistente
e facilita adicionar/variar manchetes — os hooks só dizem "isto aconteceu", o
`NewsService` decide "como noticiar".

---

## Simulação automática dos outros times (tabela sempre em dia)

O calendário de um split tem partidas de TODOS os 8 times. O gerente joga
apenas as do seu time — mas o tempo segue passando e as partidas dos demais
precisam acontecer, senão a classificação congelaria. O motor
resolve isso automaticamente em dois pontos:

### Onde acontece

- **`GameEngine.advanceCalendarTo(targetDate)`** — chamada em dois fluxos:
  - Pelo Hub ao apertar "Avançar 1 dia".
  - Pela `MatchSimulationActivity` quando o jogador termina uma partida
    agendada para uma data futura (o calendário salta para `match.date`).

  Em cada dia intermediário (`isBefore(target)`), o método roda
  `processDailyTicks` (moral, scouting, economia, janelas de transferência)
  E chama `simulateOpponentMatchesOn(iso)` que simula as partidas do dia
  filtrando **apenas as que não envolvem o time do gerente** — as dele ficam
  pendentes para que ele jogue.

- **`GameEngine.simulateOpponentMatchesToday()`** — chamada pela
  `MatchSimulationActivity.applyResult` logo depois de a série do gerente
  acabar. Resolve as partidas dos demais times **do mesmo dia** da partida
  jogada. É separada de `advanceCalendarTo` porque o loop deste último usa
  `isBefore`, ou seja, não entra no dia da partida do gerente — sem esta
  chamada extra, os outros jogos daquele dia ficariam pendentes.

### Garantias

- **Time do gerente nunca é auto-simulado.** O filtro
  `homeTeamId != managerTeamId && awayTeamId != managerTeamId` no
  `simulateOpponentMatchesOn` garante que o jogador sempre seja quem disputa
  os próprios jogos. O Hub reforça isso bloqueando "Avançar 1 dia" quando
  há partida do gerente no dia seguinte.
- **Idempotência.** O filtro `!it.played` evita re-simular partidas já
  resolvidas — se uma data tem 3 jogos e 1 já foi jogado, os outros 2 entram
  no lote.
- **A simulação usa o mesmo motor** (`MatchSimulator.simulate`) das partidas
  auto-simuladas convencionais — mesma fórmula de força, mesmo ruído, mesmas
  regras. As partidas são logadas no `gameLog` para histórico, mas NÃO viram
  notícia (o feed é sobre a carreira do gerente, não sobre a liga inteira).

### Por que essa separação (e não auto-simular tudo)?

A partida do gerente é o evento central da experiência — ela tem pick & ban
manual, simulação visual, telas de resultado e premiação. Auto-simulá-la
seria tirar do jogador o motivo de jogar. Os outros times são coadjuvantes:
ser auto-resolvidos pela IA é exatamente o comportamento desejado e mantém a
liga viva no fundo, com a tabela sempre coerente com a data atual.

---

 ## Tela de resultado: vencedor do mapa atual vs. líder da série

A `MatchSimulationActivity` chama `applyResult(homeMapsFinal, awayMapsFinal,
mapWonByHome)` após cada mapa de uma série BO3. O parâmetro `mapWonByHome`
é essencial e não pode ser inferido do placar acumulado:

- **Série encerrada** (2-0 / 2-1 / 0-2 / 1-2): o vencedor exibido na
  `MatchResultActivity` é o líder do placar (`homeMapsFinal > awayMapsFinal`).
- **Mapa intermediário com placar empatado** (1-1 após mapa 2): o vencedor
  *deste* mapa NÃO é dedutível do placar. A flag `mapWonByHome` vem direto
  do motor (`LiveMatchEngine.generateSingleMap(...).homeWon`) e indica
  inequivocamente quem venceu o mapa recém-jogado.

**Histórico do bug:** antes, o código usava `homeMapsFinal > awayMapsFinal`
para escolher o vencedor em todos os casos. Num empate 1-1, a comparação é
`false` e o `else` apontava sempre para o lado away — então o time que estava
do lado direito da tela aparecia como vencedor mesmo quando tinha perdido o
mapa. Isso afetava em cascata XP do técnico, moral do elenco, bônus de
patrocínio e laços, todos baseados em `MatchResultData.playerWon`.

---

## Decomposição de classes grandes (SRP em escala)

Algumas classes-chave do app cresceram acima de ~500 linhas conforme novos
sistemas foram plugados (moral, scouting, banco, base, notícias, 2ª divisão,
etc.). Como manter tudo num arquivo só deixa SRP de fachada, o trabalho foi
fatiado: a classe original virou uma **fachada fina** que delega para helpers
especializados no mesmo pacote. A API pública em cada caso ficou
**100% retro-compatível** — quem chama o motor não muda.

O padrão é sempre o mesmo: identificar 3–5 responsabilidades isoláveis,
criar um subpacote (`game/engine`, `game/repo`, `game/live`, `ui/hub`,
`ui/match`), mover cada bloco para sua classe com KDoc explicando o porquê da
separacão.

### `GameEngine` (~700 → ~190 linhas)

Fachada do motor de progressão do jogo. Subpacote `game/engine/`:

- **`DailyTicksProcessor`** — orquestra os ticks "humanos" de cada dia (moral,
  scouting, ofertas recebidas, laços, academia). Resolve nomes de jogadores em
  scouting nas 4 fontes (snapshot, free agents, 2ª div, base).
- **`EconomyProcessor`** — bloco de domingo (patrocínio + manutenções +
  parcelas de empréstimo) + folha mensal + aviso de saúde financeira.
- **`MatchDaySimulator`** — simula partidas (`simulateAllOn` para o modo
  automático, `simulateOpponentsOn` para o modo manual do gerente), aplica
  prêmios e gera a cobertura jornalística das partidas do gerente.
- **`CareerStarter`** — `startNewCareer` + `resolveDivisionSetup` privado que
  empacota orçamento/times/jogadores conforme a divisão escolhida.
- **`TransferWindowDetector`** — detecta abertura/fechamento de janela entre
  dois dias consecutivos e loga a transição.

### `GameRepository` (~290 → ~140 linhas)

Fachada de persistência + acesso unificado ao estado. Subpacote `game/repo/`:

- **`GameStateMigrator`** — todas as blindagens defensivas para saves
  anteriores aos campos novos (coachProfile, scoutingDepartment, transferWindows,
  incomingOffers, playerBonds, academy, bank, division, news). Idempotente —
  cada `migrate*()` checa se o campo já existe.
- **`RosterResolver`** — `rosterOf` (une 4 fontes), `marketRoster` (filtra por
  divisão), `teamsForCurrentDivision` (1ª vs 2ª div) e `applyOverride`.
  Stateless: recebe `snapshot` e `state` como parâmetros, devolve listas puras.
- **`PromotedPlayerFactory`** — materializa um `AcademyProspect` em `Player`
  do elenco (distribuição de atributos com `jitter` em torno do overall +
  contrato base com `fonte_salario="base"`).

### `LiveMatchEngine` (~500 → ~260 linhas)

Fachada do motor de simulação ao vivo. Subpacote `game/live/`:

- **`SideNormalizer`** — traduz plano azul/vermelho ↔ home/away conforme o
  número do mapa (jogador alterna lado a cada mapa).
- **`PickBanGenerator`** — 5 bans + 5 picks por lado, com plano humano ou via
  IA; devolve `playerName → campeão` para uso nos kills.
- **`MapStrengthCalculator`** — força final dos lados (overall + moral +
  off-match + composição + mains + laços − rota errada).
- **`GameOutcomeCalculator`** — decisão probabilística do mapa (vencedor,
  kills, duração). Constantes da heurística vivem aqui.
- **`TimedEventGenerator`** — distribui kills/torres/dragões/baron/herald/
  buffs/inibidor ao longo dos minutos do jogo.

### `ManagerHubActivity` (~580 → ~290 linhas)

Activity coordena lifecycle + observers + ações. Subpacote `ui/hub/`:

- **`HubCardSummaryRenderer`** — renderiza os 9 badges/subtítulos dos cards
  (mercado, técnico, sponsors, scouting, payroll, ofertas, base, banco,
  notícias). Defensivo contra GameRepository não carregado.
- **`HubLogAdapter`** — adapter standalone do log + mapa
  `LOG_TYPE_ICON_RES` declarativo.

### `MatchSimulationActivity` (~600 → ~210 linhas)

Activity coordena lifecycle + coroutine de tocagem + controles. Subpacote
`ui/match/`:

- **`MatchStatsAccumulator`** — contadores do mapa (kills, torres, dragões,
  baron, herald) + binding com o scoreboard. Reset por mapa num único
  `reset()`.
- **`MatchFeedAdapter`** — adapter standalone do feed de eventos (LIFO com
  limite [GameConstants.Simulation.FEED_MAX_ITEMS]).
- **`MatchEventRenderer`** — dispatcher dos 13 tipos de `MatchEvent`
  (handlers `onKill/onTower/onDragon/onBaron/...` privados, cada um curto).
- **`MatchResultPublisher`** — encerra mapa/série, calcula prêmio, avança
  calendário, publica notícia e lança a `MatchResultActivity`.

### Por que esse padrão vale a pena

1. **Compreensão local.** Para entender "como funciona o pick & ban" basta
   abrir `PickBanGenerator.kt`. Não é mais preciso navegar 500 linhas de
   `LiveMatchEngine` procurando o trecho relevante.
2. **Mudanças cirúrgicas.** Ajustar a probabilidade de baron toca apenas
   `TimedEventGenerator`/`GameOutcomeCalculator`. O motor não se importa.
3. **Testabilidade.** Helpers pequenos e focados são mais fáceis de testar
   em isolamento (ex: `GameOutcomeCalculator.calculate(homeStr, awayStr)` não
   precisa de Android nem do GameRepository).
4. **Reuso por composição.** Outros motores futuros podem reutilizar pedaços
   (`PickBanGenerator` para um simulador de scrim, por exemplo) sem arrastar
   o motor inteiro.
5. **Fachada preserva API.** Ninguém que chama o motor precisa mudar nada.
   A refatoração é invisível de fora.

---

## Padrão Activity → ViewModel → UseCase + Event

Todas as telas que executam regras de negócio seguem o **mesmo molde**, para
ter coerência e testabilidade. O modelo nasceu pelas Activities maiores
(`ManagerHubActivity`, `SquadActivity`, `TransferMarketActivity`) e foi
uniformizado pelas demais (`AcademyActivity`, `BankActivity`,
`IncomingOffersActivity`, `NewsActivity`, `SponsorsActivity`,
`ScoutingActivity`, `TrainingActivity`).

```
   Activity (só UI)
      ├── observa  vm.state    : LiveData<UiState>     → renderiza
      ├── observa  vm.events   : LiveData<Event<X>>   → mostra diálogo
      ├── observa  vm.running  : LiveData<Boolean>     → bloqueia UI (opcional)
      └── delega   vm.acao()                          → dispara intent

   ViewModel
      ├── state    : LiveData<UiState>      (imutável para a Activity)
      ├── events   : LiveData<Event<Sealed>> (one-shot)
      └── métodos públicos para cada ação do usuário
           └── chamam o UseCase em viewModelScope (Dispatchers.IO)

   UseCase
      └── Service (regra pura) + GameRepository (persist/log/notice)
           └── retorna sealed class tipada (Ok / Erro1 / Erro2 / ...)
```

### Componentes-chave

- **`Event<T>`** (`ui/viewmodel/Event.kt`) — wrapper one-shot para LiveData.
  `Event.consume()` devolve o conteúdo na primeira chamada e null nas
  seguintes; evita re-disparar diálogos em rotação de tela.
- **`UiState`** (data class no UseCase) — snapshot pronto para a tela
  consumir, sem precisar reler vários services.
- **Sealed class de resultado** (em cada UseCase) — `Ok` + as falhas
  possíveis com os campos que a Activity precisa para a mensagem (ex:
  `InsufficientFunds(cost, budget)`). A Activity faz `when` exaustivo.
- **Sealed class de evento** (no ViewModel) — carrega o input + o
  resultado para a Activity decidir qual diálogo abrir sem reler o estado.

### Regras do molde

1. **Nenhuma Activity nova chama `GameRepository.current()`/`save()`/`log()`
   diretamente.** Tudo passa por UseCase. Única exceção tolerada: leitura
   pontual de `managerTeamId` para pintar a toolbar com a cor do time —
   informação trivialmente estável durante a tela.
2. **Activities não chamam `Service` direto** (sem `BankService.takeLoan(...)`
   na Activity). Sempre via UseCase, que isola o conjunto
   `Service + GameRepository.save + log + News`.
3. **`viewModelScope` + `Dispatchers.IO`** para qualquer operação de UseCase.
   Mesmo que hoje seja síncrona, mantemos a fronteira para o dia em que algo
   virar suspend.
4. **Activity dispara o diálogo de confirmação** (UI puro), mas só chama
   `vm.xxx()` no positivo. O diálogo de **resultado** vem do `Event`.
5. **O cabeçalho de cores/toolbar** é OK fazer in-line com `TeamColors`
   porque é puramente visual. Não vale a pena empacotar isso no `UiState`.

### Inventories

**ViewModels** (em `ui/viewmodel/`):
`ManagerHubViewModel`, `ScheduleViewModel`, `SquadViewModel`,
`TransferMarketViewModel`, `StandingsViewModel`, `TeamSelectViewModel`,
`AcademyViewModel`, `BankViewModel`, `IncomingOffersViewModel`,
`NewsViewModel`, `SponsorsViewModel`, `ScoutingViewModel`,
`TrainingViewModel`.

**UseCases agrupados por bounded context** (em `domain/usecase/`):
- `MoreUseCases.kt` — Academia, Banco, Propostas recebidas, Notícias
- `HubFeatureUseCases.kt` — Patrocínios, Olheiros, Treinos

### Activities ainda fora do padrão

As Activities envolvendo **timing / coreografia** ainda guardam algum
estado mutável mais complexo:
`PickBanActivity`, `FullPickBanActivity`, `RoleAssignmentActivity`,
`PickBanRouterActivity`, `MatchSimulationActivity`, `MatchResultActivity`,
`OffMatchEventActivity`. A migração delas é viável mas exige uma extração
de máquina de estado de pick & ban / coroutine de simulação para fora da
Activity — deixadas para um próximo passo controlado.

---

## Persistência do save da carreira (Realm criptografado)

O save da carreira NÃO fica mais em `SharedPreferences + Gson`. Agora vive no
mesmo motor do catálogo estático: um **banco Realm criptografado** (AES-256,
chave protegida pelo Android Keystore via [RealmKeyProvider]). A migração
foi pensada para tratar o Realm como **API**: além de carregar/salvar o save
inteiro, expomos métodos de consulta pontual que leem campos diretos da
entidade sem reidratar o GameState completo.

### Estrutura

```
data/realm/GameStateBlobEntity   (entidade Realm com slot único "current")
    ├─ campos-chave consultáveis (managerName, currentDate, budget, division…)
    └─ payloadJson  (resto do GameState serializado em JSON)

data/realm/GameStatePersistence  (camada de IO)
    ├─ hasSave() / load() / save() / clear()
    ├─ currentBudget() / currentDate() / currentDivision()  (queries pontuais)
    └─ migrateLegacySaveIfNeeded()  (importa save antigo da SharedPreferences)

game/GameRepository                (fachada do domínio, inalterada por fora)
    └─ delega tudo à GameStatePersistence, mantém cache em memória de sessão
```

### Design híbrido: campos diretos + payload JSON

O [GameState] tem listas/mapas aninhados (matches, gameLog, playerOverrides,
incomingOffers, academia, bonds…) que sempre são lidos e escritos em bloco a
cada tick. Modelar tudo como entidades Realm normalizadas exigiria dezenas de
classes auxiliares para representar dados que sempre andam juntos — sem
ganho real.

Então usamos um modelo híbrido:

- **Campos diretos** na entidade para as propriedades-chave + contadores leves
  (`managerName`, `managerTeamId`, `splitStartDate`, `splitEndDate`,
  `currentDate`, `budget`, `sponsorshipPerWeek`, `division`,
  `savedAtMillis`). São consultáveis sem desserializar o resto — permitem usar
  o Realm como API ("o save existe?", "qual o orçamento atual?", "qual a data?").
- **`payloadJson`** para o restante. É o que serializávamos antes em
  SharedPreferences; o [GameStateMigrator] continua aplicando blindagem
  defensiva em campos novos sobre o data class reidratado.

### Por que Realm e não SharedPreferences

1. **Criptografia transparente** — o arquivo `.realm` já é cifrado com a
   mesma chave de 64 bytes guardada no Keystore. Sem dump em claro nas prefs.
2. **Consistência de motor de persistência** — o app passa a ter UM banco
   para tudo (dados estáticos + saves), em arquivos separados
   (`cblol_static.realm` e `cblol_save.realm`) para isolar schemas.
3. **Queries pontuais** — ler `budget` ou `currentDate` sem parsear o JSON
   inteiro. Hoje aproveitado em badge/telas auxiliares; ganho real quando
   futuras telas precisarem ler campos específicos antes do load completo
   (ex: tela de "continuar carreira" com prévia do save).
4. **Transações atômicas** — `writeBlocking` garante save íntegro mesmo se o
   processo morrer no meio. O `apply()` da SharedPreferences é assíncrono e
   menos garantido.

### Migração automática de saves antigos

Na primeira execução pós-update,
[GameStatePersistence.migrateLegacySaveIfNeeded] roda automaticamente quando o
[GameRepository.persistence] é acessado pela primeira vez:

1. Se já há save no Realm → limpa as prefs legadas (`cblol_scout_game`) por
   higiene e sai. O Realm é a fonte de verdade.
2. Se não há save no Realm mas há JSON antigo nas prefs → desserializa,
   aplica o [GameStateMigrator] e grava no Realm; depois apaga das prefs.
3. Se não há nada → no-op.

Idempotente. Carreiras em andamento sobrevivem ao update sem reset.

### Cache em memória mantido

O [GameRepository] continua mantendo o `GameState` carregado em memória
durante a sessão, exatamente como antes. O Realm é tocado apenas em
load/save/clear/queries pontuais explícitas — a performance percebida no
ciclo de jogo (tick, partida, UI) não muda.

### Regras

- **`SharedPreferences` está BANIDO para dados de jogo.** Continua aceitável
  apenas para flags de UX que existem antes do save (ex: `cblol_onboarding_prefs`
  com a flag de onboarding visto, ou `cblol_realm_keys` que guarda o blob
  cifrado da chave de 64 bytes do Realm).
- **Acessar `cblol_scout_game` direto está PROIBIDO** em código novo. Toda
  leitura/escrita do save vai por [GameRepository] (em memória) ou pelo
  [GameStatePersistence] (consulta pontual ao Realm).
- **Novos campos de estado** entram como propriedade do data class
  [GameState] e vêm embalados no `payloadJson` automaticamente. Quando algum
  campo merece ser consultável sem reidratar tudo, promover para coluna direta
  na [GameStateBlobEntity] (e atualizar `save()`).
- **Mudanças no schema da entidade Realm** (adicionar/remover campo direto)
  usam `deleteRealmIfMigrationNeeded()` durante o desenvolvimento. Quando o
  app for publicado e não pudermos mais derrubar o save dos usuários,
  escrevemos `RealmMigration` específicos por versão.

---

## Calendário mensal (`ScheduleActivity`)

A tela de calendário mostra uma **grade mensal real** (estilo Google Calendar)
em vez de uma lista plana de partidas. Cada dia exibe pontos coloridos por
categoria de evento; tocar num dia abre o painel inferior com os eventos
daquele dia.

### O que aparece no calendário

Não só partidas — eventos de **todos os subsistemas**:

1. **Partidas do gerente** (dourado) — com ação de pick & ban ou simular.
2. **Partidas dos outros times** (azul) — opção de assistir.
3. **Janelas de transferência** (lavanda) — abertura e fechamento.
4. **Propostas recebidas expirando** (lavanda) — prazo limite para responder.
5. **Ofertas de patrocínio expirando** (ciano) — prazo de aceitação.
6. **Contratos de patrocínio terminando** (ciano) — última cobrança.
7. **Folha salarial** (mint) — todo dia 1 do mês.
8. **Patrocínio semanal** (mint) — todo domingo, valor estimado consolidado.
9. **Marcos do split** (coral) — início e fim do campeonato.

As categorias 7 e 8 são **derivadas** (não vivem em listas do save). O
agregador as calcula iterando o intervalo do calendário.

### Arquitetura

```
CalendarEvent (sealed)              ← modelo unificado polimórfico
   ├─ ManagerMatch / OtherMatch
   ├─ TransferWindowOpens / TransferWindowCloses / IncomingOfferExpires
   ├─ SponsorOfferExpires / SponsorContractEnds
   ├─ WeeklySponsorPayout / Payroll
   └─ SplitStart / SplitEnd

CalendarEventCategory               ← cor + emoji por subsistema

CalendarMonthState                  ← grade pronta + eventos por dia
CalendarDayCell                     ← célula com estado (today/selected/in-month/within-split)

CalendarEventsAggregator (UseCase)  ← coleta de TODOS os subsistemas
   invoke(month) → CalendarMonthState
   eventsOn(date) → List<CalendarEvent>

ScheduleViewModel
   monthState        : LiveData<CalendarMonthState>
   selectedDate      : LiveData<LocalDate>
   selectedDayEvents : LiveData<List<CalendarEvent>>
   showMonth() / nextMonth() / previousMonth() / selectDay()

ScheduleActivity
   2 RecyclerViews:
     - recycler_grid    (GridLayoutManager spanCount=7) → MonthGridAdapter
     - recycler_events  (Linear, vertical)              → DayEventsAdapter
```

### Decisões de design

1. **Grade fechada em quadrado** (35–42 células, 5–6 semanas × 7 colunas).
   Primeira coluna alinha com o domingo da semana que contém o dia 1, e a
   última com o sábado da semana do último dia. Dias dos meses adjacentes
   aparecem em cinza com alpha reduzido — clicáveis para navegar.
2. **Sealed class polimórfica** para os eventos. A UI só sabe `title`,
   `subtitle` e `category` — cada subclasse expõe estrutura própria só onde
   precisar (ex: `matchId` em `ManagerMatch`). Adicionar tipo novo de evento
   = uma `data class` + entrada no `collectAll`, sem mexer no resto.
3. **Recorrentes derivadas** (não persistidas) — folha do dia 1 e patrocínio
   do domingo são calculadas pelo agregador iterando o intervalo. Incluímos
   só datas futuras ou hoje (passado polui sem agregar valor).
4. **Atualização cirúrgica de seleção** no adapter da grade: `setSelected(date)`
   notifica APENAS as 2 células afetadas (antiga e nova), preservando
   animações e evitando piscar a grade inteira.
5. **Dias cinzas navegam** — ao clicar num dia do mês adjacente, o
   calendário salta para esse mês automáticamente (UX padrão de calendários
   mobile).
6. **Atalho "hoje"** — tocar no nome do mês no header retorna para o mês
   atual da carreira + seleciona o dia atual. Útil para voltar depois de
   navegar para meses distantes.
7. **Cores das categorias** são resources nomeados (`cal_cat_*`), com mapeamento
   centralizado em `ScheduleActivity.categoryColor(category)` — único lugar a
   editar quando adicionar nova categoria.
8. **Pick & ban / simulação** mantém exatamente o mesmo fluxo legado: tocar
   numa partida do gerente abre diálogo perguntando se quer fazer pick & ban
   ou simular direto, com `startActivityForResult` para a `PickBanActivity`.
   A diferença é que agora o gatilho está dentro de um evento de calendário.

### Por que o agregador não vive no Realm

O Realm guarda **o estado**, não as **projeções derivadas**. Eventos como
"folha do próximo dia 1" ou "patrocínio do próximo domingo" são cálculos
feitos a partir das datas, não registros persistidos. Criá-los como entidades
seria duplicar informação e introduzir necessidade de invalidar/recriar a
cada tick — muito mais código, sem benefício.

O agregador é puro: lê o estado em memória (já reidratado do Realm pelo
`GameRepository`) e calcula. Em rotações de tela ou navegação de mês,
recalcula — trivialmente barato.

---

## Histórico recente completo (`RecentHistoryDialog`)

Dialog global acessível pelo card **📜 Histórico** no Hub ou pelo botão
"Ver tudo →" abaixo do mini-log. Mostra TODOS os eventos passados da carreira,
agregados de TODOS os subsistemas, com filtros por categoria e scroll
infinito (sem limite de itens).

### O que entra no histórico

Fontes agregadas pelo `RecentHistoryAggregator`:

1. **`GameState.gameLog`** — fonte principal. O motor já escreve nele em
   cada momento relevante (partidas, transferências, patrocínios, banco,
   academia, scouting, off-match events). 90% das categorias vem daqui.
2. **`GameState.trainingHistory`** — sessões de treino com outcome enum
   (vira subclasse própria `Training` para a UI mostrar o emoji do outcome).
3. **`GameState.news`** — manchetes do feed editorial.
4. **`PlayerOverride.moodHistory`** (de todos os jogadores) — mood events
   com delta numérico.
5. **`PlayerBond.history`** (de todos os pares) — bond events com delta.

**Por que combinar gameLog + fontes estruturadas?** O gameLog é texto cru
sem estrutura semantica (delta, outcome, etc). Para categorias onde a UI
se beneficia desses campos (mostrar cores de ganho/perda, emojis de outcome),
vamos direto na fonte primária. Para o resto, o gameLog basta.

### Arquitetura

```
RecentHistoryEvent (sealed)         ← modelo unificado polimórfico
   ├─ LogBased                       ← 90% dos casos (do gameLog)
   ├─ Training                       ← outcome enum exposto
   ├─ News                           ← fonte editorial exposta
   ├─ Mood                           ← delta numérico exposto
   └─ Bond                           ← delta numérico exposto

RecentHistoryCategory (enum)        ← 13 categorias com emoji + cor
   MATCH, TRANSFER, SPONSOR, FINANCE, ACADEMY, SCOUTING, TRAINING,
   OFF_MATCH, MOOD, BOND, NEWS, COACH, SYSTEM

RecentHistoryAggregator (UseCase)
   invoke(filtros?) → List<RecentHistoryEvent>  (ordenado por data desc)

RecentHistoryDialog
   - Chips de filtro (HorizontalScrollView com geração dinâmica)
   - RecyclerView vertical com scroll, sem limite
   - Estado vazio quando filtros zeram a lista

Pontos de entrada no Hub:
   - card_history (no GridLayout)
   - btn_history_see_all (abaixo do mini-log)
```

### Decisões de design

1. **Sem limite, com scroll** — escolha do usuário. Carreira longa pode ter
   centenas de eventos; melhor confiar no scroll do RecyclerView que truncar.
   Performance OK porque o agregador roda em memória sobre listas modestas.
2. **Mapeamento `gameLog.type` → categoria** centralizado em
   `categoryFromLogType`. Tipos desconhecidos caem em `SYSTEM` (visível mas
   sem cor especial) para não serem esquecidos.
3. **Chips de categorias sem eventos são escondidos** para não poluir.
   Carreira nova mostra só as categorias que já têm algo.
4. **Estado de filtros vive no Dialog** (não em ViewModel). É efeito local e
   efêmero; o ViewModel só valeria com persistência entre aberturas, que não
   é necessidade aqui.
5. **Re-agrega a cada toggle** em vez de filtrar em memória. Como o cálculo
   é leve e a lista total cabe em RAM, isto mantém o código simples sem custo
   perceptível. Se virar gargalo, fazemos cache.
6. **`MoodHistoryDialog` continua existindo** — ele é por-jogador (só moral
   daquele jogador). O novo dialog é GLOBAL. Os dois coexistem com finalidades
   distintas.
7. **Dois pontos de entrada** — card no grid (descoberta) e botão "Ver tudo"
   (continuidade do feed do Hub). Cada um cobre um caminho mental do usuário.

### Por que reusa o `gameLog` em vez de criar uma tabela específica

O `gameLog` já é escrito pelo motor a cada evento relevante — patrocínio
aceito, empréstimo contratado, jogador vendido, treino realizado, etc. Criar
uma entidade `CareerHistoryEvent` separada seria duplicar essas escritas em
dois lugares (com risco de drift). Em vez disso, ler do log + complementar
com as fontes estruturadas onde precisa de mais detalhe atinge a mesma
funcionalidade com zero mudanças no motor.

---

## Status da migração

Todas as Activities foram migradas para o padrão SOLID + recursos:

- ✅ `LoginActivity`
- ✅ `MainActivity`
- ✅ `ManagerHubActivity`
- ✅ `MatchResultActivity`
- ✅ `MatchSimulationActivity`
- ✅ `PickBanActivity`
- ✅ `PickBanRouterActivity`
- ✅ `ScheduleActivity`
- ✅ `SquadActivity`
- ✅ `StandingsActivity`
- ✅ `TeamSelectActivity`
- ✅ `TransferMarketActivity`
- ✅ `PlayerDetailDialog`
- ✅ `PreSimulationDialog`
- ✅ `PlayerAdapter`
- ✅ `ChampionGridAdapter`

Qualquer trabalho novo deve seguir o template documentado neste arquivo. Antes
de commitar, rode o checklist da seção anterior em cada Activity tocada.
