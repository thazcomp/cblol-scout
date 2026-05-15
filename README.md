# CBLOL Scout · Manager Edition

Jogo de gestão de e-sports estilo **Football Manager**, ambientado no **CBLOL 2026 Split 1**.

Você assume o papel de técnico de um dos 8 times, recebe um orçamento, gerencia seu elenco, contrata e vende jogadores, renegocia contratos, faz **pick & ban manual** antes de cada partida e acompanha o split inteiro com simulação BO3 ao vivo.

---

## Sumário

- [Modos do app](#modos-do-app)
- [Fluxo de telas](#fluxo-de-telas)
- [Pick & Ban Manual](#pick--ban-manual)
- [Sistema de Composições e Sinergia](#sistema-de-composições-e-sinergia)
- [Simulação ao vivo](#simulação-ao-vivo)
- [Tela de Resultado](#tela-de-resultado)
- [Arquitetura MVVM + Koin](#arquitetura-mvvm--koin)
- [Estrutura do Projeto](#estrutura-do-projeto)
- [Testes Unitários](#testes-unitários)
- [Economia](#economia)
- [Dependências](#dependências)
- [Schema do JSON](#schema-do-json)
- [Como abrir no Android Studio](#como-abrir-no-android-studio)

---

## Modos do app

### Modo Scout (legado)
Catálogo dos 40 jogadores titulares do CBLOL 2026 Split 1. Acessível dentro do modo carreira (tela de Elenco e Mercado).

### Modo Carreira
Controle completo de um time ao longo do split. Funcionalidades:

- Calendário round-robin duplo (56 partidas em 14 rodadas)
- **Pick & Ban manual** com grid imersivo de campeões, imagens via Riot Data Dragon, contornos destacados e sugestão de bans por sinergia
- **Sistema de composições** com 15 comps catalogadas (Tier S / A / B) que concedem bônus de força quando montadas
- Simulador BO3 ao vivo com timeline de eventos (picks, bans, kills, torres, drakes, baron)
- **Tela de resultado animada** com estatísticas da série após cada partida
- Gestão de elenco: titular ↔ reserva, venda, renegociação de contratos
- Mercado de transferências com preço calculado por overall + salário
- Economia: orçamento por tier, patrocínio semanal, folha de salários, premiação por vitória
- Classificação ao vivo (top 6 = playoffs)
- Save persistente em SharedPreferences via Gson

---

## Fluxo de telas

```
LoginActivity
    └─ TeamSelectActivity (grid 2×4 com 8 times)
           └─ ManagerHubActivity (hub central)
                  ├─ SquadActivity (elenco titular + banco)
                  ├─ TransferMarketActivity (mercado de jogadores)
                  ├─ ScheduleActivity (calendário BO3)
                  │    └─ [clique em partida do seu time]
                  │         ├─ PickBanActivity (pick & ban manual, landscape)
                  │         │    └─ MatchSimulationActivity (timeline ao vivo)
                  │         │         └─ MatchResultActivity (resultado animado)
                  │         │              └─ [série não encerrada]
                  │         │                   └─ PickBanRouterActivity (transparente)
                  │         │                        └─ PickBanActivity (próximo mapa)
                  │         └─ MatchSimulationActivity (simular direto)
                  └─ StandingsActivity (tabela de classificação)
```

**Botão "Jogar Próxima Partida"** no hub abre diretamente o fluxo de pick & ban ou simulação sem precisar entrar no calendário.

---

## Pick & Ban Manual

### PickBanActivity

Tela fullscreen em **modo landscape** com layout imersivo estilo broadcast de e-sports.

```
┌─────────────────────────────────────────────────────────────────────┐
│ [BAN 1][BAN 2][BAN 3][BAN 4][BAN 5]  Mapa N / Timer  [BAN 1..5]   │  ← faixa de bans
├──────────┬──────────────────────────────────────────┬───────────────┤
│  PICK 1  │  [Buscar campeão…]                       │  PICK 1       │
│  PICK 2  │  [ALL][TOP][JNG][MID][ADC][SUP]          │  PICK 2       │
│  PICK 3  │                                          │  PICK 3       │
│  PICK 4  │  Grade 7 colunas com ícones Data Dragon  │  PICK 4       │
│  PICK 5  │  Selecionado: borda gold 3dp + scale 1.08│  PICK 5       │
├──────────┴──────────────────────────────────────────┴───────────────┤
│                    [CONFIRMAR]   [IA ESCOLHE]                        │  ← rodapé
└─────────────────────────────────────────────────────────────────────┘
```

**Funcionalidades:**

- **Loading com barra de progresso** — pré-carrega todas as imagens via Glide antes de abrir a UI; barra gold animada exibe "Carregando campeões… X/Y"
- **Ordem LoL padrão** — 6 bans alternados (3+3), picks snake (1-2-2-2-1), 4 bans (2+2), picks finais (2-2) = 20 turnos
- **Timer de 30s** por turno do jogador (vermelho nos últimos 5s); IA age em 1,5s
- **Imagens da Riot Data Dragon** — ícone quadrado nos slots de ban, splash art landscape nos slots de pick
- **Nome do campeão** exibido sobre a splash art (sem a role)
- **Contorno gold animado** pulsante no slot de pick ativo; escala 1.08 + elevação no campeão selecionado na grade
- **Campeões banidos** ficam em escala de cinza (ColorMatrix) e alpha 0.22 na grade
- **Filtro por role** (chips) + **busca em tempo real** por nome
- **Detecção de composição em tempo real** — a partir do 2º pick, o label de fase exibe `⚡ [NomeComp] (X% montada) · +Y força` se uma sinergia for detectada

### Fluxo BO3 com pick & ban manual

```
Escolhe "Fazer Pick & Ban"
    → PickBanActivity (Mapa 1)
    → MatchSimulationActivity (simula com os picks escolhidos)
    → MatchResultActivity ("1-0 · Série não encerrada")
         → CONTINUAR → Dialog "Mapa 2"
              ├─ "Fazer Pick & Ban"
              │    → PickBanRouterActivity (transparente)
              │         → PickBanActivity (Mapa 2, com novos picks)
              │         → MatchSimulationActivity
              │         → MatchResultActivity
              │              → [2-0 ou 2-1] → hub
              │              → [1-1] → Dialog "Mapa 3" → mesmo fluxo
              └─ "Simular direto"
                   → MatchSimulationActivity (sem pick & ban)
                   → MatchResultActivity
```

**Cada mapa é simulado individualmente** (`generateSingleMap`) — o BO3 não é gerado de uma vez. Isso garante que:
- O pick & ban do mapa 2 acontece *depois* de ver o resultado do mapa 1
- Os campeões escolhidos em cada mapa são independentes
- O placar da série é acumulado corretamente (1-0 → 2-0 ou 1-1)

### PickBanRouterActivity

Activity totalmente transparente (sem layout) cuja única função é receber o resultado do `PickBanActivity` via `onActivityResult`, salvar o `PickBanPlan` no `Match` e abrir o `MatchSimulationActivity`. Resolve o problema de cadeia de `startActivityForResult` a partir da `MatchResultActivity`.

---

## Sistema de Composições e Sinergia

### CompositionRepository

15 composições catalogadas com base no meta competitivo 2026 (CBLOL/LCK/LEC):

| Tier | Composição | Bônus | Campeões-chave |
|---|---|---|---|
| **S** | Wombo Combo | +14 | Malphite, Orianna, Amumu |
| **S** | Protect the Carry | +13 | Lulu, Jinx, Kaisa |
| **S** | Poke & Siege | +12 | Jayce, Xerath, Varus |
| **S** | Hard Engage Snowball | +12 | Leona, Nautilus, Malphite |
| **A** | Split Push | +10 | Fiora, Camille, Tryndamere |
| **A** | Pick Composition | +10 | Blitzcrank, Zed, Pyke |
| **A** | Azir Control | +10 | Azir, Orianna |
| **A** | HyperScaling | +9 | Kassadin, Vayne, Kayle |
| **A** | Xayah & Rakan Synergy | +9 | Xayah, Rakan |
| **A** | Zeri + Enchanters | +9 | Zeri, Lulu |
| **B** | Double ADC | +7 | Lucian, Senna, Kalista |
| **B** | Full Peel | +7 | Thresh, Janna |
| **B** | Yone + Yasuo Knockup | +8 | Yasuo, Yone |
| **B** | AP Burst | +7 | Zoe, LeBlanc, Katarina |
| **B** | Vision Control/Siege | +7 | Caitlyn, Jhin, Karma |

**Lógica de bônus:**
- Comp completa (todos os required picks) → bônus total
- Comp parcial (mínimo atingido) → metade do bônus
- Campeão-chave banado pelo oponente → **bônus bloqueado**

Isso cria decisões genuínas nos bans:
> *"Bano o Orianna (quebra a Wombo Combo deles +14) ou o Zed (ameaça meu mid)?"*

**Sugestão de bans:** ao abrir o pick & ban, um dialog mostra os top 5 campeões mais importantes para banir ordenados por Tier S → A → B.

**No simulador:** `generateSingleMap` analisa os picks de cada lado e aplica o bônus ao `teamStrength` antes de calcular o vencedor. Anúncios de sinergia aparecem no feed da simulação.

---

## Simulação ao vivo

### MatchSimulationActivity

Timeline ao vivo de um único mapa (não o BO3 inteiro) com eventos em tempo real:

- **Pick & Ban** exibido como chips com ícone de ban (❌ riscado) ou pick (dourado)
- **Kills** com killer, campeão, vítima e timestamp
- **Objetivos**: torres, drakes (por tipo), Baron Nashor, Arauto do Vale, inibidores, buffs
- **Velocidade** ajustável: 1x / 2x / 4x via botão
- **Pular pro fim**: gera o resultado instantaneamente acumulando as stats, abre a tela animada
- O placar da série (ex: "1 - 0") é inicializado com o placar parcial existente e atualizado ao final

**Respeito ao PickBanPlan:**
Se um `PickBanPlan` está salvo no `Match` com o `mapNumber` correspondente ao mapa atual, o `LiveMatchEngine` usa os campeões escolhidos. Picks/bans faltantes são completados automaticamente.

---

## Tela de Resultado

### MatchResultActivity

Tela animada com 7 camadas de animação sequenciais:

| Delay | Elemento | Animação |
|---|---|---|
| 0ms | Fundo gradiente (verde vitória / vermelho derrota) | Fade in |
| 200ms | Ícone 🏆 / 💔 | Scale 0→1 `BounceInterpolator` |
| 600ms | Label VITÓRIA / DERROTA | Slide up + fade |
| 1000ms | Placar da série (ex: 2 — 1) | Scale 0.7→1 `OvershootInterpolator` |
| 1200ms | Pulso dourado no label | Só em vitória, 3 repetições |
| 1400ms | Contadores de stats | `ValueAnimator` 0 → valor real |
| 1500–2100ms | Cards de stats + prêmio + botão | Slide up sequencial |

**Estatísticas exibidas:** kills, torres, drakes, barons — com barras proporcionais home/away.

**Prêmio financeiro:** exibido em verde apenas para partidas do time do jogador.

**Botão CONTINUAR:**
- Série encerrada → volta ao `ManagerHubActivity`
- Série em andamento → dialog "Mapa N" com opções:
  - **Fazer Pick & Ban** → abre `PickBanRouterActivity`
  - **Simular direto** → abre `MatchSimulationActivity` sem pick & ban
  - **Cancelar** → permanece na tela de resultado

---

## Arquitetura MVVM + Koin

O projeto segue **MVVM limpo** com injeção de dependência via **Koin 3.5.3**:

```
UI (Activity)
    └─ ViewModel (androidx.lifecycle)
            └─ UseCase (domain/usecase)
                    └─ GameRepository / GameEngine (game)
                            └─ GameState / Models (data)
```

### ViewModels

| ViewModel | Tela | LiveData expostos |
|---|---|---|
| `ManagerHubViewModel` | ManagerHubActivity | `hubState`, `sessionReady` |
| `ScheduleViewModel` | ScheduleActivity | `matches`, `event` (sealed) |
| `SquadViewModel` | SquadActivity | `starters`, `reserves`, `swapResult`, `promoteResult` |
| `TransferMarketViewModel` | TransferMarketActivity | `players`, `buyResult` |
| `StandingsViewModel` | StandingsActivity | `standings` |
| `TeamSelectViewModel` | TeamSelectActivity | `hasSave`, `careerStarted` |

### UseCases (`domain/usecase/`)

| Arquivo | UseCases |
|---|---|
| `CareerUseCases.kt` | StartNewCareer, LoadCareer, HasSave, ClearCareer |
| `HubUseCases.kt` | GetHubState → `HubState`, `NextMatchDisplay` |
| `MatchUseCases.kt` | GetNextMatch, GetAllMatches, SavePickBanPlan, SimulateMapWithPicks, UpdateSeriesState, FinalizeMatch → `MatchResultData` |
| `SquadUseCases.kt` | GetRoster, GetStarters, GetReserves, SwapStarters, PromoteFromBench, GetStandings |
| `TransferUseCases.kt` | GetMarketRoster, BuyPlayer, GetMarketPrice |

### Módulo Koin (`di/AppModule.kt`)

Todos os UseCases registrados como `factory` (nova instância por injeção).
Todos os ViewModels registrados com `viewModel { }`.

### CBLOLApp

`Application` class que inicializa o Koin com `startKoin { androidContext(this); modules(appModule) }`.

---

## Estrutura do Projeto

```
CBLOLScout/
├── app/src/main/
│   ├── assets/
│   │   └── cblol_jogadores.json              ← dataset dos 40 jogadores
│   └── java/com/cblol/scout/
│       ├── CBLOLApp.kt                        ← Application + startKoin
│       ├── data/
│       │   ├── Models.kt                      ← Player, Team, SnapshotData
│       │   ├── GameState.kt                   ← GameState, SeriesState, Match,
│       │   │                                    PickBanPlan, Standing, LogEntry
│       │   ├── MatchEvent.kt                  ← sealed class com ~12 subtipos
│       │   ├── TeamComposition.kt             ← TeamComposition, CompArchetype,
│       │   │                                    CompAnalysisResult
│       │   └── PickBan.kt                     ← (stub — estrutura antiga removida)
│       ├── di/
│       │   └── AppModule.kt                   ← módulo Koin completo
│       ├── domain/
│       │   └── usecase/
│       │       ├── CareerUseCases.kt
│       │       ├── HubUseCases.kt
│       │       ├── MatchUseCases.kt           ← inclui MatchResultData, SeriesStats
│       │       ├── SquadUseCases.kt
│       │       └── TransferUseCases.kt
│       ├── game/
│       │   ├── Champions.kt                   ← pool de campeões por role
│       │   ├── GameEngine.kt                  ← motor de carreira, avanço de dias
│       │   ├── GameRepository.kt              ← singleton estado + SharedPreferences
│       │   ├── LiveMatchEngine.kt             ← generateSingleMap + generateSeries
│       │   │                                    + bônus de composição
│       │   ├── MatchSimulator.kt              ← teamStrength, computeStandings
│       │   ├── ScheduleGenerator.kt           ← round-robin duplo 8 times
│       │   ├── SquadManager.kt                ← swap titular/reserva
│       │   └── TransferMarket.kt              ← marketPriceOf, buyPlayer
│       ├── ui/
│       │   ├── ChampionGridAdapter.kt         ← adapter grade de campeões no pick & ban
│       │   ├── FullPickBanActivity.kt         ← legado (mantido)
│       │   ├── LoginActivity.kt
│       │   ├── MainActivity.kt                ← catálogo de jogadores
│       │   ├── ManagerHubActivity.kt          ← hub central MVVM
│       │   ├── MatchResultActivity.kt         ← tela animada de resultado
│       │   ├── MatchResultIntentExt.kt        ← extension fun toResultIntent()
│       │   ├── MatchSimulationActivity.kt     ← timeline ao vivo mapa a mapa
│       │   ├── PickBanActivity.kt             ← pick & ban manual fullscreen
│       │   ├── PickBanRouterActivity.kt       ← Activity transparente orquestradora
│       │   ├── PickSlotView.kt                ← custom view slot de pick
│       │   ├── PlayerAdapter.kt
│       │   ├── PlayerDetailDialog.kt
│       │   ├── ScheduleActivity.kt            ← calendário MVVM
│       │   ├── SquadActivity.kt               ← elenco MVVM
│       │   ├── StandingsActivity.kt           ← classificação MVVM
│       │   ├── TeamSelectActivity.kt          ← seleção de time MVVM
│       │   ├── TransferMarketActivity.kt      ← mercado MVVM
│       │   └── viewmodel/
│       │       ├── ManagerHubViewModel.kt
│       │       ├── MiscViewModels.kt          ← Transfer, Standings, TeamSelect
│       │       ├── ScheduleViewModel.kt       ← com ScheduleEvent sealed class
│       │       └── SquadViewModel.kt
│       └── util/
│           ├── ChampionRepository.kt          ← ~100 campeões do meta 2026
│           ├── CompositionRepository.kt       ← 15 comps com análise de sinergia
│           ├── JsonLoader.kt
│           └── TeamColors.kt
├── app/src/test/java/com/cblol/scout/
│   ├── TestHelpers.kt                         ← makePlayer, makeRoster5, makeMatch
│   ├── AdvanceReportTest.kt
│   ├── ChampionRepositoryTest.kt
│   ├── ChampionsTest.kt
│   ├── CompositionRepositoryTest.kt           ← 25 testes de sinergia
│   ├── LiveMatchEngineTest.kt
│   ├── MatchEventTest.kt
│   ├── MatchSimulatorTest.kt
│   ├── MatchTest.kt
│   ├── PickBanPlanTest.kt
│   ├── PickBanStateTest.kt
│   ├── PlayerTest.kt
│   ├── ScheduleGeneratorTest.kt
│   ├── SeriesStateTest.kt
│   ├── StandingTest.kt
│   └── TransferMarketTest.kt
└── app/src/main/res/layout/
    ├── activity_login.xml
    ├── activity_manager_hub.xml
    ├── activity_match_result.xml              ← NOVO: resultado animado
    ├── activity_match_simulation.xml
    ├── activity_pick_ban.xml                  ← NOVO: layout landscape imersivo
    ├── activity_schedule.xml
    ├── activity_squad.xml
    ├── activity_standings.xml
    ├── activity_team_select.xml
    ├── activity_transfer_market.xml
    ├── item_champion_pick.xml                 ← card da grade de campeões
    ├── item_log.xml
    ├── item_market.xml
    ├── item_match.xml
    ├── item_match_feed.xml
    ├── item_player.xml
    ├── item_squad_player.xml
    ├── item_standing.xml
    ├── item_team_card.xml
    └── view_pick_slot.xml                     ← slot individual de pick
```

---

## Testes Unitários

17 arquivos de teste, um por classe de produção testável:

| Arquivo | Cobertura |
|---|---|
| `PlayerTest` | `overallRating()`, bordas, `copy()` |
| `MatchTest` | `winnerTeamId()`, Gson round-trip |
| `SeriesStateTest` | máquina BO3, imutabilidade, todos os cenários |
| `PickBanPlanTest` | serialização Gson, listas vazias |
| `PickBanStateTest` | estado mutável, `usedChampions` |
| `ChampionsTest` | `forRole()`, duplicatas, fallback |
| `ChampionRepositoryTest` | `getAll`, `getById`, `getByRole`, URLs Data Dragon |
| `CompositionRepositoryTest` | `analyze` (full/parcial/banida), `suggestBans`, Tier S priority |
| `ScheduleGeneratorTest` | 56 partidas, round-robin, exceptions |
| `MatchSimulatorTest` | `teamStrength`, estatística de win rate |
| `LiveMatchEngineTest` | 10 bans + 10 picks, sem reuso, plano respeitado |
| `TransferMarketTest` | todos os brackets de overall, bordas |
| `AdvanceReportTest` | valores default, acumulação |
| `StandingTest` | `mapDiff`, `games` |
| `MatchEventTest` | todos os 12 subtipos de `MatchEvent` |

---

## Economia

| Evento | Valor |
|---|---|
| Orçamento inicial Tier S | R$ 5.000.000 |
| Orçamento inicial Tier A | R$ 3.000.000 |
| Orçamento inicial Tier B | R$ 1.500.000 |
| Patrocínio semanal Tier S | R$ 600.000 |
| Patrocínio semanal Tier A | R$ 350.000 |
| Patrocínio semanal Tier B | R$ 200.000 |
| Prêmio por vitória em série | R$ 100.000 |
| Prêmio por mapa vencido | R$ 50.000 |
| Salários | debitado dia 1 de cada mês |

**Preço de mercado por overall:**

| Overall | Multiplicador |
|---|---|
| ≥ 85 | salário × 12 × 2,4 |
| 75–84 | salário × 12 × 1,8 |
| 65–74 | salário × 12 × 1,2 |
| 55–64 | salário × 12 × 0,85 |
| < 55 | salário × 12 × 0,6 |

---

## Times incluídos

| Time | Tier | Cor |
|---|---|---|
| FURIA | S | ⬛ |
| LOUD | S | 🟢 |
| paiN Gaming | S | 🔴 |
| Fluxo W7M | A | 🔵 |
| RED Canids | A | 🟥 |
| Keyd Stars | B | 🟡 |
| Leviatán | B | 🟣 |
| LOS | B | 🟠 |

---

## Dependências

```groovy
// UI / Material
implementation 'androidx.core:core-ktx:1.12.0'
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'com.google.android.material:material:1.11.0'
implementation 'androidx.recyclerview:recyclerview:1.3.2'
implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
implementation 'androidx.activity:activity-ktx:1.8.2'

// Imagens
implementation 'com.github.bumptech.glide:glide:4.16.0'
annotationProcessor 'com.github.bumptech.glide:compiler:4.16.0'

// Serialização
implementation 'com.google.code.gson:gson:2.10.1'

// Arquitetura
implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0'
implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.7.0'

// Coroutines
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'

// Injeção de dependência
implementation 'io.insert-koin:koin-android:3.5.3'
implementation 'io.insert-koin:koin-androidx-viewmodel:3.5.3'

// Flexbox (legado)
implementation 'com.google.android.flexbox:flexbox:3.0.0'

// Testes
testImplementation 'junit:junit:4.13.2'
testImplementation 'io.insert-koin:koin-test:3.5.3'
```

---

## Schema do JSON (`cblol_jogadores.json`)

```jsonc
{
  "meta": { "liga", "split", "atualizado_em", "fontes" },
  "times": [ { "id", "nome", "tier_orcamento" } ],
  "jogadores": [
    {
      "id", "nome_jogo", "nome_real",
      "time_id", "time_nome", "role", "titular",
      "idade", "nacionalidade",
      "contrato": {
        "termino", "valor_estimado_brl",
        "salario_mensal_estimado_brl", "fonte_salario"
      },
      "stats_brutas": {
        "jogos", "kda", "kp_pct", "cs_min",
        "gd15", "xpd15", "damage_share_pct", "vision_score_min"
      },
      "atributos_derivados": {
        "lane_phase", "team_fight",
        "criatividade", "consistencia", "clutch"
      }
    }
  ]
}
```

`overall = (lane_phase + team_fight + criatividade + consistencia + clutch) / 5`

---

## Fonte dos dados

- **Rosters**: [Liquipedia CBLOL 2026 Split 1](https://liquipedia.net/leagueoflegends/CBLOL/2026/Split_1)
- **Stats**: [gol.gg CBLOL 2026 Split 1](https://gol.gg/tournament/tournament-stats/CBLOL%202026%20Split%201/)
- **Imagens de campeões**: [Riot Data Dragon](https://ddragon.leagueoflegends.com) (patch 14.10.1)
- **Composições**: meta competitivo 2026 (CBLOL / LCK / LEC)

---

## Como abrir no Android Studio

1. Clone ou copie a pasta `CBLOLScout` para sua máquina
2. Abra o **Android Studio** → *Open* → selecione a pasta raiz
3. Aguarde o sync do Gradle
4. Conecte um dispositivo ou inicie o emulador
5. Execute com **▶ Run**

> **Requisitos:** Android 7.0 (API 24+) · compileSdk 34 · Kotlin 1.9 · AGP 8.2 · JDK 11+
>
> **Permissões:** `INTERNET` (necessária para carregar imagens da Data Dragon)

---

## Licença

MIT — dados de jogadores são públicos (Liquipedia / gol.gg). Atributos derivados e estimativas de salário são gerados automaticamente e marcados como `"estimado"` no JSON.
