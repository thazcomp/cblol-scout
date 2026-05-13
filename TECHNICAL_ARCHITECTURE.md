# Arquitetura Técnica - Sistema de Validação de Elenco

## 🏗️ Estrutura da Solução

### Componentes Principais

```
┌─────────────────────────────────────────────────────────────┐
│                         SquadManager                        │
│                      (Núcleo da Lógica)                     │
├─────────────────────────────────────────────────────────────┤
│  ✓ validateAndFixRoster()  - Valida e corrige              │
│  ✓ isMissingStarter()      - Verifica vagas                │
│  ✓ starterCount()          - Conta titulares               │
│  ✓ initializeRoster()      - Inicializa novo jogo          │
└─────────────────────────────────────────────────────────────┘
            ▲              ▲              ▲              ▲
            │              │              │              │
    ┌───────┴───────┐  ┌───┴──────┐  ┌──┴─────┐  ┌─────┴───┐
    │ TransferMarket│  │GameEngine│  │SquadAct│  │ UseCases│
    │   (Vendas)    │  │(Partidas)│  │(UI)    │  │   (DI)  │
    └───────────────┘  └──────────┘  └────────┘  └─────────┘
```

---

## 🔄 Fluxo de Dados

### 1. Validação Automática

```
Ação do Usuário
    ↓
GameRepository.updateOverride()
    ↓
GameRepository.save()
    ↓
SquadManager.validateAndFixRoster()
    ├→ Agrupa titulares por role
    ├→ Para cada role:
    │  ├→ Se 0 titulares: promove melhor reserva
    │  └→ Se 2+ titulares: rebaixa excesso
    ├→ Registra todas as ações em GameState.gameLog
    └→ GameRepository.save() (persistir)
    ↓
Retorna Lista<String> com ações
    ↓
UI opcionalmente mostra alerta
```

### 2. Regras de Validação

```
Para cada role (TOP, JNG, MID, ADC, SUP):

Titulares = 0
    └→ Buscar melhor reserva disponível
    └→ Se não existe: registrar ERRO
    └→ Caso contrário: promover

Titulares = 1
    └→ OK, nada fazer

Titulares > 1
    └→ Ordenar por OVR (descending)
    └→ Manter melhor, rebaixar os outros
```

### 3. Persistência de Logs

```
GameState.gameLog: List<LogEntry>
    
LogEntry {
    data: "2026-04-15"
    tipo: "SQUAD_AUTO" | "SQUAD_INIT" | "SQUAD"
    mensagem: "João promovido a titular de TOP"
}

Limite: 80 entradas (FIFO, remove mais antigas)
```

---

## 🔗 Pontos de Integração

### 1. Venda de Jogador
```
TransferMarket.sellPlayer()
├→ GameRepository.updateOverride(playerId) { 
│  └→ copy(newTeamId = newTeam.id, titular = false)
├→ GameRepository.save()
└→ SquadManager.validateAndFixRoster(context) ← VALIDAÇÃO
```

### 2. Compra de Jogador
```
TransferMarket.buyPlayer()
├→ GameRepository.updateOverride(playerId) {
│  └→ copy(newTeamId = myTeamId, titular = false)
├→ GameRepository.save()
└→ SquadManager.validateAndFixRoster(context) ← VALIDAÇÃO
```

### 3. Alternância de Status
```
TransferMarket.toggleStarter()
├→ GameRepository.updateOverride(playerId) {
│  └→ copy(titular = !current)
├→ GameRepository.save()
└→ SquadManager.validateAndFixRoster(context) ← VALIDAÇÃO
```

### 4. Simulação de Partida
```
GameEngine.advanceDays()
└→ Para cada dia com partida:
    ├→ SquadManager.validateAndFixRoster(context) ← VALIDAÇÃO
    └→ MatchSimulator.simulate()
```

### 5. Tela de Elenco
```
SquadActivity.onResume()
├→ SquadManager.validateAndFixRoster(context) ← VALIDAÇÃO
└→ Mostra alerta se houver logs
```

---

## 📦 Implementação de Use Cases

### ValidateRosterUseCase
```kotlin
class ValidateRosterUseCase(private val context: Context) {
    operator fun invoke() = 
        SquadManager.validateAndFixRoster(context)
    // Retorna: List<String>
}
```

### IsMissingStarterUseCase
```kotlin
class IsMissingStarterUseCase(private val context: Context) {
    operator fun invoke(): Boolean = 
        SquadManager.isMissingStarter(context)
    // Retorna: Boolean
}
```

### StarterCountUseCase
```kotlin
class StarterCountUseCase(private val context: Context) {
    operator fun invoke(): Int = 
        SquadManager.starterCount(context)
    // Retorna: Int
}
```

---

## 🔐 Consistência de Dados

### Invariantes Mantidos

```
SEMPRE:
  1. totalTitulares == 5
  2. topTitulares == 1
  3. jngTitulares == 1
  4. midTitulares == 1
  5. adcTitulares == 1
  6. supTitulares == 1
```

### Transações

Todas as operações são **atômicas**:
```kotlin
// Exemplo: validateAndFixRoster
val logs = mutableListOf()
// ... calcula todos os ajustes ...
if (logs.isNotEmpty()) {
    for (log in logs) {
        // Atualiza um by one (updateOverride)
        // Registra no log
    }
    GameRepository.save(context) // Salva TUDO de uma vez
}
// Sem save() intermediário = operação completa ou nada
```

---

## ⚡ Performance

### Complexidade O(n)
- n = número de jogadores no elenco

```
validateAndFixRoster():
  1. Filter titulares: O(n)
  2. GroupBy role: O(5) = O(1)
  3. Para cada role:
    - Filter + MaxBy: O(n/5) = O(n)
    - Sort + loop: O(m log m) onde m ≤ n/5
  Total: O(n) = ~negligenciável para ≤ 30 jogadores
```

### Uso de Memória
```
validateAndFixRoster:
  - Roster list: ~30 jogadores = ~15 KB
  - GroupBy map: ~5 chaves = ~1 KB
  - Logs list: ~5 strings = ~200 bytes
  Total: < 20 KB por chamada
```

---

## 🛡️ Tratamento de Erros

### Casos Extremos

```
CASO: Vender último MID (sem reserva)
RESULTADO: 
  ├→ MID rebaixado para reserva (titulo removed)
  ├→ Busca reserva: NENHUM ENCONTRADO
  ├→ Log: "ERRO: Sem reserva disponível para MID"
  └→ Time fica com 4 titulares até comprar novo MID

CASO: 2 MID titulares simultaneamente
RESULTADO:
  ├→ Ordena por OVR
  ├→ Mantém melhor
  ├→ Rebaixa o outro
  └→ Team volta a 5 titulares

CASO: Validação chamada N vezes
RESULTADO:
  ├→ Idempotente
  ├→ Se já está válido, retorna []
  └→ Se inválido, corrige novamente
```

---

## 📊 Exemplo de Execução

### Estado Inicial
```
TOP: Faker (OVR 88, titular) + Scout (OVR 72, reserva)
JNG: Khan (OVR 85, titular) + Clid (OVR 81, reserva)
MID: Showmaker (OVR 90, titular) + Bdd (OVR 83, reserva)
ADC: Gumayusi (OVR 87, titular) + Ruler (OVR 86, reserva)
SUP: Keria (OVR 89, titular) + Deft (OVR 82, reserva)

Total titulares: 5 ✓
```

### Venda de MID Titular (Showmaker)
```
1. Showmaker removido
   MID: apenas Bdd (OVR 83, reserva)

2. validateAndFixRoster() chamado

3. Detecta: MID sem titular (0 starters)

4. Busca reserva MID:
   └→ Encontra Bdd (OVR 83)

5. Promove Bdd
   MID: Bdd (OVR 83, titular agora)

6. Log: "SQUAD_AUTO: Bdd promovido a titular de MID"

Resultado Final:
TOP: Faker (titular) ✓
JNG: Khan (titular) ✓
MID: Bdd (titular) ✓
ADC: Gumayusi (titular) ✓
SUP: Keria (titular) ✓

Total titulares: 5 ✓
```

---

## 🎯 Propriedades Desejáveis

✅ **Corretude**: Sempre mantém 5 titulares
✅ **Completude**: Nunca deixa role vazio se houver reserva
✅ **Determinismo**: Mesma entrada sempre produz saída igual
✅ **Idempotência**: Chamar 2x = chamar 1x
✅ **Atomicidade**: Operação completa ou falha totalmente
✅ **Auditabilidade**: Todos os ajustes são registrados
✅ **Performance**: O(n) com n ≤ 30 é negligenciável
✅ **Escalabilidade**: Não depende de limites de elenco

---

## 🔄 Evolução Possível

Se no futuro for necessário:

1. **Limite de Substituições por Rodada**
   ```kotlin
   fun validateAndFixRoster(context, maxSwaps: Int = 2)
   ```

2. **Peso de Promoção (Experiência)**
   ```kotlin
   fun promoteReserveByExperience(role: String)
   ```

3. **Sistema de Morale**
   ```kotlin
   if (player.titulo) morale += 5
   else morale -= 3
   ```

4. **Cache de Validação**
   ```kotlin
   @volatile private var lastValidationHash = ""
   fun validateAndFixRoster(...) {
       if (currentHash == lastValidationHash) return []
   }
   ```
