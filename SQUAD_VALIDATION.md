# Sistema de Validação Automática de Elenco

## Resumo

Implementei um sistema automático que:
1. ✅ Garante que o time sempre tenha **exatamente 5 titulares** (1 por role: TOP, JNG, MID, ADC, SUP)
2. ✅ **Promove automaticamente** um reserva quando um titular fica indisponível
3. ✅ **Rebaixa o excesso** se houver mais de 1 titular na mesma role
4. ✅ **Impede** que o time fique com menos ou mais de 5 titulares

## Onde a validação é acionada?

### 1. **Quando um jogador é vendido** (TransferMarket.sellPlayer)
- Se o jogador vendido era titular, um reserva da mesma role é automaticamente promovido
- Se não houver reserva disponível, registra um erro no log

### 2. **Quando um jogador é comprado** (TransferMarket.buyPlayer)
- Se houver vaga de titular (alguma role sem titular), o novo jogador é promovido automaticamente

### 3. **Quando o status de um jogador é alternado manualmente** (TransferMarket.toggleStarter)
- Se rebaixar um titular deixa a role sem titular, um reserva é promovido
- Se promover deixa a role com 2+ titulares, o pior é automaticamente rebaixado

### 4. **Antes de simular uma partida** (GameEngine.advanceDays)
- Garante que há 5 titulares antes de jogar
- Se não houver, promove automaticamente os melhores reservas

### 5. **Na tela de elenco** (SquadActivity.onResume)
- Valida o elenco quando a tela é aberta
- Mostra um alerta ao usuário informando quaisquer ajustes automáticos realizados

## Métodos da API

### SquadManager.validateAndFixRoster(context: Context): List<String>
Valida e corrige o elenco, retornando uma lista de ações tomadas:
```kotlin
// Exemplo de retorno:
// ["Neymar promovido a titular de MID", "Faker rebaixado a reserva de MID"]
```

### SquadManager.isMissingStarter(context: Context): Boolean
Verifica se o time está faltando algum titular em alguma role.

### SquadManager.starterCount(context: Context): Int
Retorna o número total de jogadores titulares.

### SquadManager.initializeRoster(context: Context)
Inicializa um elenco válido para um novo jogo. Define o melhor jogador de cada role como titular e os demais como reservas. Garante que há 5 titulares desde o início.

## Use Cases para UI

Três novas use cases foram criadas para exposição via DI:
- **ValidateRosterUseCase** - Chama validateAndFixRoster
- **IsMissingStarterUseCase** - Chama isMissingStarter
- **StarterCountUseCase** - Chama starterCount

Registradas em `AppModule.kt`:
```kotlin
factory { ValidateRosterUseCase(androidContext()) }
factory { IsMissingStarterUseCase(androidContext()) }
factory { StarterCountUseCase(androidContext()) }
```

## Logs

Todas as ações automáticas são registradas no GameState.gameLog:
- Tipo: "SQUAD_AUTO"
- Mensagens: "Neymar promovido a titular de MID", "Faker rebaixado a reserva de MID", etc.

## Regras de Validação

1. **Uma role sem titular** → Promove o melhor reserva disponível da role
2. **Uma role com 2+ titulares** → Mantém o melhor, rebaixa os outros
3. **Sem reserva para preencher vaga** → Registra erro no log

## Exemplo de Uso Direto

```kotlin
// Validar o elenco
val logs = SquadManager.validateAndFixRoster(context)
logs.forEach { println(it) }

// Verificar se falta algum titular
if (SquadManager.isMissingStarter(context)) {
    println("Alerta: Time com jogadores faltando!")
}

// Contar titulares
val count = SquadManager.starterCount(context)
println("Titulares: $count/5")
```

## Fluxo de Exemplo

```
Cenário: Jogador TOP titular é vendido (era único TOP titular)

1. TransferMarket.sellPlayer() → Remove o TOP
2. validateAndFixRoster() é chamado
3. Detecta: "TOP sem titular"
4. Busca melhor reserva TOP
5. Promove automaticamente
6. GameRepository.log("SQUAD_AUTO", "João promovido a titular de TOP")
7. Retorna ["João promovido a titular de TOP"]
8. UI mostra alerta ao usuário
```
