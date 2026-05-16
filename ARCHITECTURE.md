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
