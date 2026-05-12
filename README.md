# CBLOL Scout · Manager Edition

Aplicativo Android nativo — agora um **jogo de gestão estilo Football Manager** para o **CBLOL 2026 Split 1**.

Você assume o papel de técnico de um dos 8 times, recebe um orçamento, gerencia seu elenco, contrata e vende jogadores, renegocia contratos, e o calendário do split avança em tempo simulado com partidas BO3 sendo decididas pelo motor do jogo.

## Modos do app

### 1. Modo Scout (legado)
Originalmente o app era apenas um catálogo dos 40 jogadores titulares do CBLOL 2026 Split 1.
Esses dados continuam acessíveis dentro do modo carreira (tela de Elenco e Mercado).

### 2. Modo Carreira (v2.0+)
Você controla um time. Funcionalidades:

- **Calendário automático** — round-robin duplo (14 rodadas × 4 jogos = 56 partidas) começando em 28/03/2026
- **Pick & Ban fullscreen** — ao iniciar uma partida do seu time, abre uma tela fullscreen com grid 5×N de campeões. Clique para selecionar (destaque com **contorno ouro de 5dp**). Os picks são persistidos e usados pelo motor de simulação para calcular bônus de composição
- **Simulador de partidas BO3** — vencedor decidido por força do elenco (média do overall dos titulares) + bônus de mando + bônus de composição (picks) + ruído
- **Gestão de elenco** — promova/rebaixe titular ↔ reserva, venda jogadores, renegocie contratos
- **Mercado de transferências** — compre jogadores de outros times pagando o preço calculado a partir do overall + salário
- **Economia** — orçamento inicial por tier, patrocínio semanal automático, folha mensal de salários debitada, premiação por vitória/mapas vencidos
- **Avançar dia** — pule 1 dia, 1 semana ou direto até a próxima partida; eventos econômicos e jogos são processados em sequência
- **Classificação** — tabela atualizada após cada rodada (top 6 = playoffs)
- **Save persistente** — toda a carreira salva automaticamente no SharedPreferences

#### Fluxo
```
LoginActivity → TeamSelectActivity → ManagerHubActivity (centro)
                                          ├─ MainActivity (Elenco)
                                          ├─ TransferMarketActivity (Mercado)
                                          ├─ ScheduleActivity (Calendário)
                                          │   └─ FullPickBanActivity (fullscreen grid de pick)
                                          │       └─ MatchSimulationActivity (simulação com timeline)
                                          └─ StandingsActivity (Classificação)
```

#### Orçamentos por tier
| Tier | Times | Orçamento inicial | Patrocínio/sem |
|------|-------|-------------------|---------------|
| S | FURIA, LOUD, paiN | R$ 5.000.000 | R$ 600.000 |
| A | Fluxo W7M, RED Canids | R$ 3.000.000 | R$ 350.000 |
| B | Keyd Stars, Leviatán, LOS | R$ 1.500.000 | R$ 200.000 |

#### Receitas e despesas
- Patrocínio semanal: pago todo domingo
- Salários: debitados no dia 1 de cada mês (somatório do elenco titular + reserva)
- Vitória em série BO3: R$ 100.000 + R$ 50.000 por mapa vencido

## Screenshots

```
┌──────────────────────────┐   ┌──────────────────────────┐
│ CBLOL Scout   2026 S1  ⋮ │   │ ██████████████████████   │
│ [buscar jogador…]        │   │ JoJo            🇧🇷       │
│ ALL TOP JNG MID ADC SUP  │   │ FURIA                    │
│ TODOS FURIA LOUD RED …   │   │────────────────────────  │
│ 40 jogadores             │   │ Overall          92       │
├──────────────────────────┤   │ Jogos KDA  KP%  CS/min   │
│ ▌ SUP JoJo 🇧🇷    [92]   │   │  18   7.3  80%  1.0      │
│   FURIA                  │   │────────────────────────  │
│  18J  KDA 7.3  CS 1.0    │   │ Lane Phase  ████████ 88  │
│  Lane ████████ 88        │   │ Team Fight  █████████ 92 │
│  TF   █████████ 92       │   │ Criativ.    ███████  78  │
│  …                       │   │ Consist.    ████████ 86  │
│  R$ 80.000/mês  estimado │   │ Clutch      ████████ 82  │
└──────────────────────────┘   └──────────────────────────┘
          Lista                       Detalhe (bottom sheet)
```

## Fluxo do app

```
LoginActivity  →  TeamSelectActivity  →  MainActivity
   (auth)            (escolhe time)        (jogadores do time)
```

1. **LoginActivity** (launcher) — formulário usuário/senha (validação local: senha ≥ 4 chars).
   Tem também um botão "Entrar como convidado" que pula a autenticação.
2. **TeamSelectActivity** — grid 2×4 com os 8 times do CBLOL. Cada card mostra
   tier de orçamento, número de jogadores e overall médio do time.
3. **MainActivity** — recebe o `team_id` via Intent e exibe **apenas** os jogadores
   daquele time. A toolbar fica colorida com a cor da organização. Pelo menu ⋮ é
   possível "Trocar de time" (volta pro grid) ou "Sair" (volta pro login).

## Features

- **40 jogadores titulares** — todos os 8 times do CBLOL 2026 Split 1
- **Cartão por jogador** com cor de acento do time, badge de role, bandeira de nacionalidade e nota overall
- **5 atributos derivados** (Lane Phase, Team Fight, Criatividade, Consistência, Clutch) exibidos como barras coloridas na escala 1-100
- **Estatísticas brutas**: jogos, KDA, CS/min, KP%, DMG%, GD@15, XPD@15, Vision Score/min
- **Filtros por role** (TOP / JNG / MID / ADC / SUP) e **por time** via chips roláveis
- **Busca em tempo real** por nome de invocador, nome real ou time
- **Ordenação** por Overall, Nome, KDA, CS/min ou Salário (menu ⋮)
- **Detalhe em bottom sheet** ao tocar no card — exibe todos os campos incluindo salário e fonte (reportado ou estimado)

## Times incluídos

| Time | Tier | Cor |
|------|------|-----|
| FURIA | S | ⬛ |
| LOUD | S | 🟢 |
| paiN Gaming | S | 🔴 |
| Fluxo W7M | A | 🔵 |
| RED Canids | A | 🟥 |
| Keyd Stars | B | 🟡 |
| Leviatán | B | 🟣 |
| LOS | B | 🟠 |

## Fonte dos dados

- **Rosters**: [Liquipedia CBLOL 2026 Split 1](https://liquipedia.net/leagueoflegends/CBLOL/2026/Split_1)
- **Stats**: [gol.gg CBLOL 2026 Split 1](https://gol.gg/tournament/tournament-stats/CBLOL%202026%20Split%201/)
- Stats com fonte `"reportado"` são públicas; stats com `"estimado"` seguem a heurística de normalização intra-split descrita no schema

## Estrutura do Projeto

```
CBLOLScout/
├── app/
│   ├── src/main/
│   │   ├── assets/
│   │   │   └── cblol_jogadores.json       ← dataset completo dos 40 jogadores
│   │   ├── java/com/cblol/scout/
│   │   │   ├── data/
│   │   │   │   ├── Models.kt              ← Player, Team, SnapshotData, etc.
│   │   │   │   └── PickBan.kt             ← PickRecord, PickBanMap, PickBanPlan
│   │   │   ├── game/
│   │   │   │   ├── GameEngine.kt          ← motor principal de carreira
│   │   │   │   ├── LiveMatchEngine.kt     ← BO3 com timeline eventos
│   │   │   │   └── Champions.kt           ← lista de campeões por role
│   │   │   ├── ui/
│   │   │   │   ├── LoginActivity.kt       ← tela de login (launcher)
│   │   │   │   ├── TeamSelectActivity.kt  ← grid de seleção de time
│   │   │   │   ├── MainActivity.kt        ← jogadores do time + filtros + ordenação
│   │   │   │   ├── ScheduleActivity.kt    ← calendário e matchmaking
│   │   │   │   ├── FullPickBanActivity.kt ← fullscreen grid de pick & ban (5 colunas)
│   │   │   │   ├── MatchSimulationActivity.kt ← timeline BO3 com eventos
│   │   │   │   ├── PlayerAdapter.kt       ← RecyclerView adapter jogadores
│   │   │   │   └── util/
│   │   │   │       ├── JsonLoader.kt      ← lê assets/cblol_jogadores.json
│   │   │   │       └── TeamColors.kt      ← cores de time/role e bandeiras
│   │   └── res/
│   │       ├── layout/
│   │       │   ├── activity_login.xml
│   │       │   ├── activity_team_select.xml
│   │       │   ├── activity_main.xml
│   │       │   ├── activity_pickban_fullscreen.xml    ← grid fullscreen com toolbar
│   │       │   ├── activity_match_simulation.xml      ← timeline BO3
│   │       │   ├── item_champion_card.xml             ← card 140×160 com contorno
│   │       │   ├── item_team_card.xml
│   │       │   ├── item_player.xml
│   │       │   ├── item_match_feed.xml                ← evento na timeline
│   │       │   └── bottom_sheet_player.xml
│   │       ├── drawable/
│   │       │   ├── champ_chip_bg.xml                  ← background chip (#102233)
│   │       │   └── champ_selected_border.xml          ← stroke ouro 5dp (#FFD54F)
│   │       ├── menu/menu_main.xml
│   │       └── values/ (colors, strings, themes)
│   ├── src/test/
│   │   └── java/com/cblol/scout/
│   │       ├── PickBanSerializationTest.kt            ← teste serialização Gson
│   │       └── LiveMatchEngineTest.kt                 ← teste engine com overrides
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── README.md
```

## Features de Pick & Ban (v3.0+)

### FullPickBanActivity
- **Grid fullscreen 5×N** com todos os campeões do jogo
- **Seleção visual**: clique em um campeão para selecionar
  - Selecionado: **contorno ouro em stroke 5dp** (#FFD54F)
  - Não-selecionado: sem contorno (fundo translúcido)
- **Botões de navegação**: Voltar (descarta) e Confirmar (persiste)
- **ActivityResult**: retorna campeões selecionados como comma-separated string

### Integração com Simulação
- Ao iniciar uma partida do seu time (via ScheduleActivity), FullPickBanActivity é aberta
- Após confirmar, os picks são salvos em um **PickBanPlan** persistido no Match
- O motor de simulação (**LiveMatchEngine**) recebe o plano e:
  - Aplica overrides (bans e picks definidos) onde disponíveis
  - Completa automaticamente picks faltantes com campeões aleatórios
  - Calcula bônus de composição baseado nos picks
  - Gera timeline BO3 com eventos de Ban, Pick, matança, objetivo, etc.

### Estrutura de Dados
```kotlin
data class PickRecord(
    val playerId: String?,    // ID do jogador (se disponível)
    val playerName: String?,  // Nome do jogador (se disponível)
    val champion: String      // Nome do campeão
)

data class PickBanMap(
    val gameNumber: Int,
    val homeBans: List<String> = emptyList(),
    val awayBans: List<String> = emptyList(),
    val homePicks: List<PickRecord> = emptyList(),
    val awayPicks: List<PickRecord> = emptyList()
)

data class PickBanPlan(
    val matchId: String,
    val maps: List<PickBanMap>
)
```

## Como abrir no Android Studio

1. **Clone / copie** a pasta `CBLOLScout` para sua máquina
2. Abra o **Android Studio** → *Open* → selecione a pasta raiz `CBLOLScout`
3. Aguarde o sync do Gradle (necessita JDK 8+ e Android SDK 34)
4. Conecte um dispositivo ou inicie o emulador
5. Execute com **▶ Run**

> Requisitos mínimos: Android 7.0 (API 24) · compileSdk 34 · Kotlin 1.9 · AGP 8.2

## Dependências

```groovy
implementation 'com.google.android.material:material:1.11.0'
implementation 'androidx.recyclerview:recyclerview:1.3.2'
implementation 'com.google.code.gson:gson:2.10.1'
implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0'
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
```

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
      "contrato": { "termino", "salario_mensal_estimado_brl", "fonte_salario" },
      "stats_brutas": { "jogos", "kda", "kp_pct", "cs_min", "gd15",
                        "xpd15", "damage_share_pct", "vision_score_min" },
      "atributos_derivados": { "lane_phase", "team_fight",
                               "criatividade", "consistencia", "clutch" }
    }
  ]
}
```

## Licença

MIT — dados de jogadores são públicos (Liquipedia / gol.gg). Atributos derivados e estimativas de salário são gerados automaticamente e marcados como `"estimado"` no JSON.
