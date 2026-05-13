# 🛡️ Validação de Venda - Bloqueio de Titulares

## 📋 Mudança Implementada

Agora o sistema **bloqueia a venda de um jogador titular** se isso deixar o time com menos de 5 titulares.

### ✅ Requisito
**"O time não pode ficar apenas com 4 titulares, tem que barrar a venda caso haja uma tentativa"**

---

## 🔒 Como Funciona

### Cenário 1: Venda de Reserva ✅
```
Usuário tenta vender um RESERVA
    ↓
Sistema valida: é reserva? SIM
    ↓
Venda permitida ✅
```

### Cenário 2: Venda de Titular COM Reserva ✅
```
Usuário tenta vender TOP titular (há TOP reserva)
    ↓
Sistema valida:
  - É titular? SIM
  - Há reserva de TOP? SIM
    ↓
Venda permitida ✅
Sistema promove automaticamente o reserva
```

### Cenário 3: Venda de Titular SEM Reserva ❌
```
Usuário tenta vender TOP titular (SEM TOP reserva)
    ↓
Sistema valida:
  - É titular? SIM
  - Há reserva de TOP? NÃO
    ↓
Venda BLOQUEADA ❌
Mensagem: "Não é possível vender o jogador João. 
           Ele é o único titular de TOP e não há reserva disponível.
           Contrate um substituto primeiro."
```

---

## 🔧 Implementação Técnica

### Modificação em TransferMarket.sellPlayer()

```kotlin
// Validação: se é titular, verificar se há reserva para substituir
if (player.titular) {
    val reserve = roster
        .filter { !it.titular && it.role == player.role }
        .maxByOrNull { it.overallRating() }

    if (reserve == null) {
        return SellResult.Error(
            "Não é possível vender o jogador ${player.nome_jogo}. " +
            "Ele é o único titular de ${player.role} e não há reserva disponível. " +
            "Contrate um substituto primeiro."
        )
    }
}
```

### Novo Método: SquadManager.canSellPlayer()

```kotlin
fun canSellPlayer(context: Context, playerId: String): Boolean {
    val gs = GameRepository.current()
    val roster = GameRepository.rosterOf(context, gs.managerTeamId)
    val player = roster.find { it.id == playerId } ?: return false

    // Se for reserva, sempre pode vender
    if (!player.titular) return true

    // Se for titular, precisa haver reserva para substituir
    val hasReserve = roster
        .filter { !it.titular && it.role == player.role }
        .isNotEmpty()

    return hasReserve
}
```

---

## 📚 API Pública

### Verificar se pode vender um jogador

```kotlin
// Direto
val canSell = SquadManager.canSellPlayer(context, playerId)

// Via Use Case (DI)
@Inject CanSellPlayerUseCase canSellPlayer
val canSell = canSellPlayer(playerId)
```

### Tentar vender

```kotlin
val result = TransferMarket.sellPlayer(context, playerId)
when (result) {
    is SellResult.Ok -> {
        println("Vendido por R$ ${result.price} para ${result.toTeam}")
    }
    is SellResult.Error -> {
        println("❌ ${result.msg}")
        // Exemplo: "Não é possível vender o jogador João..."
    }
}
```

---

## 🎮 Fluxo do Usuário

### Mercado de Transferências

```
Usuário seleciona jogador titular (sem reserva)
    ↓
Clica "VENDER"
    ↓
Sistema verifica:
  ✓ É titular de role X?
  ✓ Há reserva de role X?
    ↓
Se NÃO há reserva:
  ❌ Mostra alerta:
     "Não é possível vender João (TOP).
      Contrate um substituto primeiro."
    ↓
  Venda bloqueada
  Botão desabilitado ou com aviso
```

---

## 📊 Casos Cobertos

| Jogador | Status | Há Reserva | Ação | Resultado |
|---------|--------|-----------|------|-----------|
| João | Reserva | - | Vender | ✅ Permitido |
| João | Titular | Sim | Vender | ✅ Permitido |
| João | Titular | Não | Vender | ❌ Bloqueado |

---

## 💾 SellResult

```kotlin
sealed class SellResult {
    data class Ok(val price: Long, val toTeam: String) : SellResult()
    data class Error(val msg: String) : SellResult()
}
```

### Exemplo de Erro

```
SellResult.Error(
    "Não é possível vender o jogador João. " +
    "Ele é o único titular de TOP e não há reserva disponível. " +
    "Contrate um substituto primeiro."
)
```

---

## 🔄 Relacionado com Validação Automática

### Antes: Validação Automática (Fixa)
```
Venda ocorria → Time fica com 4 titulares → Sistema tentava corrigir
Problema: Sem reserva, não conseguia corrigir
```

### Depois: Bloqueio Preventivo (Novo)
```
Venda tentada → Sistema valida ANTES → Bloqueia se problemático
Resultado: Time sempre fica com 5 titulares
```

---

## 🛡️ Garantia

✅ **Invariante Mantido**: Time sempre tem 5 titulares  
✅ **Nenhuma Exceção**: Nenhum cenário deixa time com 4  
✅ **Feedback Claro**: Usuário sabe exatamente por que não pode vender  
✅ **Caminho de Solução**: Mensagem diz "Contrate um substituto"  

---

## 📋 Checklist de Teste

- [ ] Vender reserva funciona
- [ ] Vender titular com reserva funciona
- [ ] Vender titular sem reserva é bloqueado
- [ ] Mensagem de erro é clara
- [ ] Após comprar substituto, venda é permitida
- [ ] Logs registram tentativas bloqueadas

---

## 🚀 Uso em UI (TransferMarketActivity)

```kotlin
// Ao clicar em "VENDER" de um jogador
private fun sellPlayer(playerId: String) {
    val canSell = SquadManager.canSellPlayer(applicationContext, playerId)
    
    if (!canSell) {
        val player = /* get player */
        AlertDialog.Builder(this)
            .setTitle("❌ Não é possível vender")
            .setMessage(
                "Não é possível vender o jogador ${player.nome_jogo}. " +
                "Ele é o único titular de ${player.role} e não há reserva disponível. " +
                "Contrate um substituto primeiro."
            )
            .setPositiveButton("OK", null)
            .show()
        return
    }
    
    // Prosseguir com venda
    val result = TransferMarket.sellPlayer(applicationContext, playerId)
    when (result) {
        is SellResult.Ok -> {
            Toast.makeText(this, "Vendido!", Toast.LENGTH_SHORT).show()
            vm.load()
        }
        is SellResult.Error -> {
            AlertDialog.Builder(this)
                .setTitle("Erro")
                .setMessage(result.msg)
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
```

---

## 📝 Notas Importantes

1. **Reserva = Jogador com mesmo role mas não titular**
2. **Bloqueio acontece ANTES de qualquer transação**
3. **Mensagem é específica e actionable**
4. **Sem possibilidade de estado inválido**

---

**Versão:** 1.1.0  
**Status:** ✅ Implementado
