# CBLOL Scout

Aplicativo Android nativo para visualizar os atributos de todos os jogadores do **CBLOL 2026 Split 1**, estilo FIFA/Football Manager.

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
│   │   │   │   └── Models.kt              ← Player, Team, SnapshotData, etc.
│   │   │   ├── ui/
│   │   │   │   ├── LoginActivity.kt       ← tela de login (launcher)
│   │   │   │   ├── TeamSelectActivity.kt  ← grid de seleção de time
│   │   │   │   ├── MainActivity.kt        ← jogadores do time + filtros + ordenação
│   │   │   │   └── PlayerAdapter.kt       ← RecyclerView adapter
│   │   │   └── util/
│   │   │       ├── JsonLoader.kt          ← lê assets/cblol_jogadores.json
│   │   │       └── TeamColors.kt          ← cores de time/role e bandeiras
│   │   └── res/
│   │       ├── layout/
│   │       │   ├── activity_login.xml
│   │       │   ├── activity_team_select.xml
│   │       │   ├── activity_main.xml
│   │       │   ├── item_team_card.xml
│   │       │   ├── item_player.xml
│   │       │   └── bottom_sheet_player.xml
│   │       ├── menu/menu_main.xml
│   │       └── values/ (colors, strings, themes)
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── README.md
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
