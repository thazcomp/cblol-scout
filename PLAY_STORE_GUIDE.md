# Guia de publicação na Google Play Store — Rift Manager

Este documento lista **tudo** que falta para publicar o app, separando o que
já foi feito (no código) do que **só você pode fazer** (conta, chaves, prints).

---

## ✅ O QUE JÁ FOI FEITO NO CÓDIGO (por mim)

1. **`app/build.gradle`**
   - `minifyEnabled true` + `shrinkResources true` no release (reduz o tamanho do app).
   - Bloco `signingConfigs.release` que lê credenciais de `keystore.properties`
     (seguro, fora do git). Se o arquivo não existir, o build não quebra — só
     sai sem assinatura.
   - `bundle { ... }` com splits de idioma/densidade/abi (App Bundle otimizado).
   - `resourceConfigurations` limitado a pt/pt-BR (app é em português).
   - `debug` ganhou `applicationIdSuffix ".debug"` (instala debug + release juntos).

2. **`app/proguard-rules.pro`** — regras completas para Gson, Realm, Koin,
   Glide, Kotlin, enums e view binding. Protege a (de)serialização dos saves.

3. **Ícone do app** — os diretórios `mipmap-*` estavam **vazios** (o app não
   tinha ícone!). Criei um **ícone adaptativo vetorial** (troféu dourado sobre
   fundo escuro), em:
   - `drawable/ic_launcher_foreground.xml`
   - `drawable/ic_launcher_background.xml`
   - `mipmap-anydpi-v26/ic_launcher.xml` e `ic_launcher_round.xml`
   - Inclui camada `monochrome` (ícone temático do Android 13+).

4. **`.gitignore`** — adicionado `keystore.properties` para nunca vazar a chave.

5. **`keystore.properties.example`** — modelo para você preencher.

---

## ✅ RENOMEAÇÃO JÁ FEITA: "CBLOL Scout" → "Rift Manager"

O nome do app foi alterado para **Rift Manager** em todos os textos visíveis
ao usuário (nome do app, onboarding, avisos legais, seleção de time).

**Por que é um bom nome:** "Rift Manager" **não contém nenhuma marca da Riot**
— sem "CBLOL", sem "LoL", sem "League". "Rift" é uma palavra comum do inglês
(fenda) e "Manager" é genérico. Risco de IP no NOME é praticamente zero.

O app continua mantendo o aviso legal proeminente (onboarding + rodapé do
login) porque o CONTEÚDO ainda referencia League of Legends (campeões, termos),
o que é coberto pelas diretrizes de projetos de fã da Riot desde que sem fins
lucrativos e não-oficial — ambos já declarados.

> O `applicationId` interno (`com.cblol.scout`) **não muda** — não é visível
> ao público na loja. Trocar depois de publicado é impossível (vira outro app),
> então fica como está. Se quiser que o ID também reflita "riftmanager", isso
> precisa ser feito ANTES do primeiro envio — me avise.

---

## 📋 O QUE VOCÊ PRECISA FAZER

### Passo 1 — Gerar o keystore de assinatura (uma vez na vida)

No terminal, na raiz do projeto:

```bash
keytool -genkey -v -keystore cblol-scout-release.jks \
    -keyalg RSA -keysize 2048 -validity 10000 -alias cblol-scout
```

No Windows o `keytool` costuma estar em:
`"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"`

> **GUARDE ESSE ARQUIVO E AS SENHAS COM A VIDA.** Se perder, você **nunca mais**
> conseguirá atualizar o app publicado (teria que publicar um app novo do zero).
> Faça backup em local seguro (gerenciador de senhas + nuvem privada).

### Passo 2 — Criar o `keystore.properties`

Copie `keystore.properties.example` para `keystore.properties` e preencha com o
caminho do `.jks` e as senhas que você definiu.

### Passo 3 — Gerar o App Bundle (.aab) assinado

```bash
./gradlew bundleRelease
```

O arquivo sai em: `app/build/outputs/bundle/release/app-release.aab`

Esse `.aab` é o que você sobe na Play Store (não é mais `.apk`).

Para testar o release localmente antes (opcional, vira APKs instaláveis):
```bash
./gradlew assembleRelease
```

### Passo 4 — Conta de desenvolvedor Google Play

- Acesse https://play.google.com/console
- Pague a taxa **única** de US$ 25.
- Complete a verificação de identidade (pode levar alguns dias).

### Passo 5 — Criar o app no Play Console e preencher a ficha

Você vai precisar produzir (eu posso ajudar a escrever os textos):
- **Título** (até 30 caracteres)
- **Descrição curta** (até 80 caracteres)
- **Descrição completa** (até 4000 caracteres)
- **Ícone** 512×512 PNG (posso gerar a arte; você exporta em PNG)
- **Feature graphic** 1024×500 PNG
- **Screenshots** — mínimo 2 (telefone), recomendado 4–8. Você captura no
  emulador/celular rodando o app.
- **Categoria**: Jogos → Esportes ou Estratégia
- **Classificação indicativa** (questionário no console)
- **Política de privacidade** (URL pública — obrigatória; posso escrever o texto)

### Passo 6 — Declarações obrigatórias (Play Console)

- **Data safety form**: o app coleta dados? Como ele só salva localmente
  (Realm criptografado) e só usa internet para baixar imagens de campeões, a
  declaração é simples — posso te ajudar a responder.
- **Target audience / conteúdo**: definir faixa etária.
- **Ads**: o app não tem anúncios → declarar "não".

### Passo 7 — Faixa de teste antes de produção

Recomendo subir primeiro em **teste interno** (libera em minutos, até 100
testers por e-mail), validar no seu celular, e só depois promover para
**produção**.

---

## 🔎 PENDÊNCIAS TÉCNICAS QUE VALE CONFERIR

- [ ] **Rodar `./gradlew bundleRelease` e testar o app minificado.** Como
      ativei o R8/shrink, é preciso testar o `.aab`/APK de release num
      dispositivo para garantir que o ProGuard não removeu nada usado por
      reflexão. As regras que escrevi cobrem os casos conhecidos (Gson/Realm),
      mas teste o fluxo completo (criar carreira, salvar, fechar, reabrir).
- [ ] Confirmar que o app abre, salva e recarrega save no build de release.
- [ ] Revisar o `versionName`/`versionCode` (hoje 1.0 / 1) — ok para o 1º envio.
- [ ] Testar em uma tela pequena e uma grande (a tela de pick & ban é densa).

---

## 📝 TEXTOS PARA A LOJA (rascunho — me peça para gerar)

Posso escrever para você:
- Título + descrição curta + descrição completa
- Texto da política de privacidade (para hospedar num GitHub Pages/Notion público)
- Respostas sugeridas do Data Safety form

É só pedir.
