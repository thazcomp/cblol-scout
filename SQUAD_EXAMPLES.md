# Exemplos de Uso - Sistema de Validação de Elenco

## Cenário 1: Venda de Titular

```kotlin
// Jogador TOP titular é vendido
TransferMarket.sellPlayer(context, "playerId123")

// Internamente:
// 1. Jogador removido do time
// 2. validateAndFixRoster() é chamado
// 3. Detecta que TOP ficou sem titular
// 4. Busca melhor reserva TOP
// 5. Promove João (OVR 78)
// Resultado: Time mantém 5 titulares
```

## Cenário 2: Compra de Jogador

```kotlin
// Compra um novo MID
TransferMarket.buyPlayer(context, "newPlayerMid")

// Internamente:
// 1. Jogador adicionado como reserva
// 2. validateAndFixRoster() é chamado
// 3. Verifica se MID está completo (já tem 1 titular?)
// 4. Como não há outro MID titular neste exemplo, promove o novo
// Resultado: Novo jogador torna-se MID titular automaticamente
```

## Cenário 3: Alternância Manual de Titular/Reserva

```kotlin
// Usuário clica para rebaixar um MID titular para reserva
TransferMarket.toggleStarter(context, "midPlayerId")

// Internamente:
// 1. MID rebaixado para reserva
// 2. validateAndFixRoster() é chamado
// 3. Detecta que MID ficou sem titular
// 4. Busca melhor reserva MID
// 5. Promove automaticamente
// Resultado: Outro MID vira titular, regra de 5 titulares mantida
```

## Cenário 4: Simulação de Partida com Problema de Elenco

```kotlin
// Usuário avança 3 dias (GameEngine.advanceDays)
GameEngine.advanceDays(context, 3)

// Dia 1: OK
// Dia 2: 1 partida → Detecta falta de titular
//   - validateAndFixRoster() é chamado antes de jogar
//   - Promove automaticamente
//   - Partida ocorre com elenco válido
// Dia 3: OK

// Resultado: Jogo nunca ocorre com elenco inválido
```

## Cenário 5: Validação Manual via UI

```kotlin
// SquadActivity.onResume()
override fun onResume() {
    super.onResume()
    
    val logs = SquadManager.validateAndFixRoster(context)
    if (logs.isNotEmpty()) {
        // Mostra alerta ao usuário com detalhes
        AlertDialog.Builder(this)
            .setTitle("⚠ Elenco ajustado automaticamente")
            .setMessage(logs.joinToString("\n"))
            .setPositiveButton("OK", null).show()
    }
    
    vm.load()
}

// Exemplo de logs:
// ["João promovido a titular de TOP", "Maria rebaixada a reserva de JNG"]
```

## Cenário 6: Inicializar Elenco para Novo Jogo

```kotlin
// Após criar nova carreira
SquadManager.initializeRoster(context)

// Internamente:
// 1. Agrupa jogadores por role
// 2. Para cada role (TOP, JNG, MID, ADC, SUP):
//    - Define melhor jogador como titular
//    - Demais como reservas
// Resultado: Elenco inicializado com 5 titulares (1 por role)
```

## Verificação de Estado

```kotlin
// Verificar quantos titulares há
val count = SquadManager.starterCount(context)
if (count < 5) {
    println("Alerta: Apenas $count titulares!")
}

// Verificar se falta algum titular
if (SquadManager.isMissingStarter(context)) {
    println("Alerta: Time incompleto!")
    val logs = SquadManager.validateAndFixRoster(context)
    logs.forEach { println(it) }
}

// Validar sem necessidade de alerta anterior
val logs = SquadManager.validateAndFixRoster(context)
if (logs.isEmpty()) {
    println("✓ Elenco válido")
} else {
    println("Ajustes realizados:")
    logs.forEach { println("  - $it") }
}
```

## Fluxo Completo de Uma Carreira

```
Início:
1. initializeRoster() → 5 titulares iniciais (1 por role)
2. Log: "SQUAD_INIT: Elenco inicializado com 5 titulares"

Gameplay:
3. Venda de TOP titular
4. validateAndFixRoster() → "João promovido a titular"
5. Compra de novo MID
6. validateAndFixRoster() → "Pedro promovido a titular de MID"
7. Usuário rebaixa ADC titular
8. validateAndFixRoster() → "Silva promovido a titular de ADC"
9. Partida importante
10. validateAndFixRoster() (antes da partida) → elenco 100% válido

Resultado: Usuário nunca enfrenta problema de elenco incompleto
```
