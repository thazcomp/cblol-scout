# Arquitetura · CBLOL Scout

Este documento descreve o padrão de código adotado no projeto. **Todo trabalho novo deve seguir essas regras** para manter consistência, testabilidade e manutenibilidade.

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
