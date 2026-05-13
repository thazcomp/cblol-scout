# Guia de Teste - Sistema de Validação de Elenco

## 🎯 Objetivo
Verificar que o time **sempre** possui exatamente 5 titulares (1 por role) e que nenhum jogador da reserva é promovido automaticamente quando falta um titular.

---

## ✅ Teste 1: Venda de Titular

### Passos
1. Entrar no jogo
2. Ir para **Mercado de Transferências**
3. Vender o **TOP titular** (qualquer um)
4. Ir para **Elenco**
5. Voltar para **Mercado** e entrar em **Elenco** novamente

### Resultado Esperado
- ⚠ **Alerta na tela**: "Elenco ajustado automaticamente"
- 📝 **Log contém**: "João promovido a titular de TOP" (ou nome do novo titular)
- 👥 **Titulares**: Ainda há 5 (1 TOP novo, 1 JNG, 1 MID, 1 ADC, 1 SUP)
- 🎮 **Qualidade**: TOP novo tem menor OVR que o vendido? Tudo normal

---

## ✅ Teste 2: Compra de Jogador com Vaga

### Passos
1. Vender um **MID titular** (use Teste 1 como base)
2. Ir para **Mercado de Transferências**
3. Comprar um **MID** (qualquer um disponível)
4. Verificar status

### Resultado Esperado
- ⚠ **Alerta**: "Elenco ajustado automaticamente"
- 📝 **Log contém**: "Novo MID promovido a titular de MID"
- 👥 **Titulares**: 5 (o novo MID virou titular automaticamente)
- 💰 **Budget**: Reduzido pelo preço do jogador

---

## ✅ Teste 3: Rebaixa Manual de Titular

### Passos
1. Ir para **Elenco** → **Titulares**
2. Clicar em um **TOP titular** e depois **"↔ TROCAR"**
3. Selecionar um **TOP reserva** para swap
4. Após a troca, rebaixar o TOP que virou novo titular:
   - Clique em um TOP titular
   - Clique no botão de ação
5. Selecionar um TOP reserva para promover
6. Verificar resultado

### Resultado Esperado
- ✅ **Swap funciona**: 2 jogadores trocam de posição
- ⚠ **Alerta**: "Elenco ajustado automaticamente" (na 2ª ação)
- 👥 **Titulares**: Sempre 5
- 📝 **Log**: Registra tanto o swap manual quanto as auto-promoções

---

## ✅ Teste 4: Simular Partida com Problema de Elenco

### Passos
1. Vender um **MID titular**
2. **Sem** ir para a tela de Elenco (sem validação manual)
3. Ir para **Calendário/Partidas**
4. Avançar dias até ter uma partida
5. Simular a partida

### Resultado Esperado
- ⚠ **Antes da partida**: validateAndFixRoster executado internamente
- 📝 **Log**: "MID promovido a titular de MID" aparece
- 🎮 **Partida**: Ocorre normalmente com elenco válido
- 🏆 **Resultado**: Sem erros de elenco incompleto

---

## ✅ Teste 5: Múltiplas Vagas Simultaneamente

### Passos
1. Vender **TOP e MID titulares** (dois em sequência)
2. Ir para **Elenco**

### Resultado Esperado
- ⚠ **Alerta**: "Elenco ajustado automaticamente"
- 📝 **Log contém 2+ linhas**:
  - "XX promovido a titular de TOP"
  - "YY promovido a titular de MID"
- 👥 **Titulares**: 5 (todas as roles preenchidas)

---

## ✅ Teste 6: Sem Reserva Disponível

### Passos
1. Vender todos os **MID reservas** (use Mercado)
2. Vender o **MID titular**
3. Ir para **Elenco**

### Resultado Esperado
- ⚠ **Alerta**: "Elenco ajustado automaticamente"
- 📝 **Log contém**: "ERRO: Sem reserva disponível para MID"
- ⚠ **Visível na tela**: MID vazio (sem titular) até comprar um novo
- 👥 **Titulares**: 4 (faltando MID)

---

## ✅ Teste 7: Inicialização de Novo Jogo

### Passos
1. Iniciar novo jogo (clicar em time no TeamSelectActivity)
2. Confirmar criação de carreira
3. Ir para **Elenco** imediatamente

### Resultado Esperado
- ✅ **Sem alerta**: validateAndFixRoster já deixou tudo pronto
- 👥 **Titulares**: 5 (1 por role, todos os melhores)
- 📝 **Log**: "SQUAD_INIT: Elenco inicializado com 5 titulares"

---

## 🔍 Verificações Técnicas

### Logs
```
Abra o histórico do jogo e procure por:
- "SQUAD_AUTO" - Ajustes automáticos
- "SQUAD_INIT" - Inicialização
- "SQUAD" - Ações manuais

Exemplo:
2026-04-15 | SQUAD_AUTO | João promovido a titular de TOP
2026-04-15 | SQUAD_AUTO | Maria rebaixada a reserva de JNG
```

### Debug
```kotlin
// Adicione este código em qualquer Activity para debug:
val count = SquadManager.starterCount(applicationContext)
val missing = SquadManager.isMissingStarter(applicationContext)
Toast.makeText(this, "Titulares: $count/5, Missing: $missing", Toast.LENGTH_LONG).show()
```

---

## ❌ Erros que NÃO devem ocorrer

- ❌ "GameState não carregado" - Ao validar elenco
- ❌ Team com 4 titulares - Deveria ser sempre 5
- ❌ Team com 6 titulares - Deveria ser sempre 5
- ❌ Partida jogada com < 5 titulares - Validação antes de simular
- ❌ Crash ao vender/comprar jogador - Validação automática deve tratar
- ❌ Sem alerta ao abrir SquadActivity - Se houve ajustes, deve avisar

---

## 📊 Relatório de Teste

Criar checklist:

```
[ ] Teste 1: Venda de Titular - ✅ PASSOU
[ ] Teste 2: Compra com Vaga - ✅ PASSOU  
[ ] Teste 3: Rebaixa Manual - ✅ PASSOU
[ ] Teste 4: Partida Automática - ✅ PASSOU
[ ] Teste 5: Múltiplas Vagas - ✅ PASSOU
[ ] Teste 6: Sem Reserva - ✅ PASSOU
[ ] Teste 7: Novo Jogo - ✅ PASSOU

Logs verificados: ✅
Debug funcionando: ✅
Sem erros técnicos: ✅

CONCLUSÃO: ✅ SISTEMA FUNCIONA CORRETAMENTE
```

---

## 🎮 Teste Prático Rápido

Para um teste rápido sem muitos passos:

1. Ir para **Mercado**
2. Vender um **TOP titular** (qualquer um)
3. Ir para **Elenco**
4. Se houver alerta → ✅ PASSOU
5. Verificar que há 5 titulares → ✅ PASSOU

**Tempo**: ~2 minutos
**Resultado**: Validação automática funcionando
