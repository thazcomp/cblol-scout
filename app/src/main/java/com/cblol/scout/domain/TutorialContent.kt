package com.cblol.scout.domain

/**
 * Conteúdo do **tutorial sob demanda** do app.
 *
 * O [com.cblol.scout.ui.OnboardingActivity] (15 slides one-shot na primeira
 * execução) apresenta os sistemas em alto nível. Este tutorial complementa
 * com explicações mais profundas, **acessíveis a qualquer momento** durante a
 * carreira pelo card "❓ Tutorial" no Hub ou pelo botão no Login.
 *
 * **Por que separado do onboarding?** Onboarding é uma sequência fixa de
 * slides genéricos; tutorial é um catálogo indexado por sistema, que o jogador
 * consulta sob demanda quando bate dúvida em uma tela específica ("o que é
 * cooldown de treino?", "como funciona limite de crédito?"). Os dois coexistem.
 *
 * **Por que data em código e não em strings.xml?** Cada tópico tem várias
 * seções estruturadas (overview / como funciona / dicas / atalhos) — colocar
 * tudo em strings.xml viraria um forest de string-arrays correlacionados. Como
 * todo o app é PT-BR, manter inline é mais limpo e mantém a estrutura visível.
 *
 * **SOLID/OCP:** adicionar tópico = adicionar entrada em [topics]. A UI
 * (`TutorialActivity`/`TutorialDetailDialog`) itera a lista sem mudar.
 */
object TutorialContent {

    /** Uma seção dentro de um tópico (overview, dicas, atalhos, etc.). */
    data class Section(
        val title: String,
        val body: String
    )

    /**
     * Um tópico do tutorial — corresponde a um sistema do jogo.
     *
     * @property id chave estável (não muda entre versões para deep-link futuro)
     * @property emoji ícone do tópico (mostrado no card da lista)
     * @property title nome curto (mostrado no card)
     * @property summary 1-2 linhas que aparecem no card da lista como teaser
     * @property sections corpo do tópico, dividido em blocos titulados
     */
    data class Topic(
        val id: String,
        val emoji: String,
        val title: String,
        val summary: String,
        val sections: List<Section>
    )

    /** Catálogo completo do tutorial (ordem importa — define a sequência na UI). */
    val topics: List<Topic> = listOf(
        Topic(
            id = "basics",
            emoji = "🎯",
            title = "Conceitos básicos",
            summary = "O que é um split, BO3, divisões e o ciclo de jogo.",
            sections = listOf(
                Section("Você é o técnico",
                    "Sua função é montar o elenco, decidir táticas (pick & ban), administrar " +
                    "as finanças e tomar decisões fora do rift que afetam o desempenho do time."),
                Section("Ciclo de jogo",
                    "O jogo gira em torno do calendário. Você avança dias um a um (ou pula para " +
                    "a próxima partida) e a cada dia o motor processa:\n" +
                    "• Patrocínio semanal (domingo) e folha salarial (dia 1)\n" +
                    "• Ofertas recebidas, eventos fora de jogo, evolução de moral e química\n" +
                    "• Desenvolvimento da base, scouting, ticks de treino\n" +
                    "• Partidas agendadas para aquele dia"),
                Section("Split + Divisões",
                    "Um SPLIT é a temporada do CBLOL/CD — 8 times, todos contra todos em ida e " +
                    "volta, com BO3 (Melhor de 3 mapas) por partida.\n\n" +
                    "Você pode começar em 1ª divisão (CBLOL — orçamento alto, jogadores famosos) " +
                    "ou na 2ª (Circuito Desafiante — orçamento baixo, mais foco em banco e base).")
            )
        ),
        Topic(
            id = "pickban",
            emoji = "✨",
            title = "Pick & Ban",
            summary = "Como funcionam picks, bans, sugestões da IA e atribuição de roles.",
            sections = listOf(
                Section("Fluxo padrão",
                    "Para cada mapa da BO3 você decide:\n" +
                    "• 5 bans (alternados, começando pelo lado azul)\n" +
                    "• 5 picks (alternados, começando pelo lado azul)\n\n" +
                    "O time inimigo escolhe via IA. Você pode também deixar a IA jogar pelo seu " +
                    "lado a qualquer momento, se quiser pular o draft."),
                Section("Sugestões",
                    "Ao seu turno, aparecem cards com sugestões e o MOTIVO:\n" +
                    "• MAIN — campeão do pool do seu jogador (bônus de força no mapa)\n" +
                    "• COMP — completa uma composição reconhecida pelo motor\n" +
                    "• COUNTER — responde a algum pick do adversário\n" +
                    "• SINERGIA — preenche uma lacuna (CC, AP/AD, sustain)"),
                Section("Roles e atribuição",
                    "Após o draft você pode REMANEJAR quem joga em qual rota. Útil pra colocar " +
                    "um pick \"flex\" (ex: Akshan) num jogador que tem ele no main. Jogar fora " +
                    "da role nativa aplica penalidade na simulação."),
                Section("Dica",
                    "Bans 1-2 servem pra tirar ameaças do meta global; bans 3 pra cima são pra " +
                    "negar o jogador específico do adversário (alguém com pool curto/forte).")
            )
        ),
        Topic(
            id = "simulation",
            emoji = "⚡",
            title = "Simulação ao vivo",
            summary = "Como o motor decide o resultado e o que a barra de eventos mostra.",
            sections = listOf(
                Section("Fatores que pesam",
                    "O motor combina:\n" +
                    "• Overall médio do elenco escalado\n" +
                    "• Sinergia da composição detectada (caçada nas comps cadastradas)\n" +
                    "• Bônus por jogadores no MAIN do champion pickado\n" +
                    "• Química do elenco (laços entre titulares)\n" +
                    "• Vantagem lado azul (~3%)\n" +
                    "• Penalidades por rota errada\n" +
                    "• Modificadores temporários (lesão, eventos fora de jogo)\n" +
                    "• Pitada de aleatoriedade pra evitar resultados pré-determinados"),
                Section("Velocidade",
                    "Você pode acelerar a partida (1x → 2x → 4x → pular para o fim) usando o " +
                    "botão no canto da tela. Útil pra séries longas ou partidas de outros times."),
                Section("Estatísticas",
                    "Durante a simulação o motor gera eventos narrados (kills, torres, drag, baron) " +
                    "e acumula stats — KDA, gold, dano causado. No fim, a tela de resultado mostra " +
                    "tudo organizado por time.")
            )
        ),
        Topic(
            id = "calendar",
            emoji = "🗓️",
            title = "Calendário mensal",
            summary = "A grade mensal com TODOS os eventos do split.",
            sections = listOf(
                Section("O que aparece",
                    "Cada dia mostra até 4 pontos coloridos por categoria:\n" +
                    "🟨 Sua partida   🟦 Partida de outros times\n" +
                    "🟪 Mercado (janela, propostas expirando)\n" +
                    "🟩 Finanças (folha mensal, patrocínio semanal)\n" +
                    "🟧 Patrocínios (contratos, ofertas)\n" +
                    "🟥 Marcos do split (início, fim)"),
                Section("Tocar em um dia",
                    "Mostra TODOS os eventos daquele dia no painel inferior. Toque numa partida " +
                    "do seu time para fazer pick & ban (apenas se for AMANHÃ no jogo) ou em outra " +
                    "partida para assistir (idem, só amanhã)."),
                Section("Atalhos",
                    "• Setas ◀ ▶ no topo navegam entre meses\n" +
                    "• Toque no nome do mês volta direto pra hoje\n" +
                    "• Dias em cinza são do mês anterior/próximo, tocáveis pra navegar")
            )
        ),
        Topic(
            id = "squad",
            emoji = "👥",
            title = "Elenco e moral",
            summary = "Titulares vs reservas, atributos, moral e pedidos de transferência.",
            sections = listOf(
                Section("Composição do elenco",
                    "5 titulares (TOP/JNG/MID/ADC/SUP) + reservas. Você pode promover reserva ↔ " +
                    "titular a qualquer momento. O motor sempre valida e garante 5 titulares — " +
                    "se vender um titular, a melhor reserva da role sobe."),
                Section("Atributos",
                    "Cada jogador tem 5 atributos derivados (0-99), calculados por IA a partir " +
                    "de stats reais (KDA, KP%, CS/min, GD@15, Damage Share, Vision):\n" +
                    "• LANE PHASE — performance de lane 0-15min\n" +
                    "• TEAM FIGHT — confrontos coletivos\n" +
                    "• CRIATIVIDADE — picks fora do meta\n" +
                    "• CONSISTÊNCIA — desempenho estável\n" +
                    "• CLUTCH — performance em momentos decisivos\n\n" +
                    "O OVERALL é a média desses 5."),
                Section("Moral",
                    "Cada jogador tem moral 0-100 (😞 0-33, 😐 34-66, 😄 67-100). Sobe com " +
                    "vitórias, renovação, eventos positivos. Desce com derrotas, sentar no banco, " +
                    "salário baixo, propostas recusadas.\n\n" +
                    "Moral muito baixa por tempo prolongado → o jogador PEDE TRANSFERÊNCIA e " +
                    "fica mais visado por propostas externas."),
                Section("Histórico de moral",
                    "Clique no nome do jogador na tela Elenco para ver o detalhe + histórico " +
                    "completo de mudanças de moral.")
            )
        ),
        Topic(
            id = "transfers",
            emoji = "🔄",
            title = "Mercado e Propostas",
            summary = "Como comprar/vender, valor de mercado, janelas e propostas recebidas.",
            sections = listOf(
                Section("Janelas de transferência",
                    "Mercado só funciona dentro das JANELAS:\n" +
                    "• PRÉ-TEMPORADA — antes da rodada 1\n" +
                    "• INTER-TEMPORADA — pausa no meio do split\n\n" +
                    "Fora delas, compra/venda está bloqueada e propostas recebidas pendentes " +
                    "expiram automaticamente."),
                Section("Valor de mercado",
                    "Calculado a partir do overall e do salário anual:\n" +
                    "• Overall 85+ → vale 2.4× salário anual\n" +
                    "• Overall 75-84 → 1.8×\n" +
                    "• Overall 65-74 → 1.2×\n" +
                    "• Overall 55-64 → 0.85×\n" +
                    "• Overall <55 → 0.6×"),
                Section("Comprar",
                    "Você paga o valor de mercado e o jogador vem como RESERVA. " +
                    "Se tiver vaga de titular livre na role, o sistema promove automaticamente."),
                Section("Vender",
                    "Vender titular SEM reserva da mesma role precisa de confirmação dupla — o " +
                    "elenco fica desfalcado até contratar substituto. Multa por encerramento " +
                    "antecipado pode se aplicar."),
                Section("Propostas recebidas",
                    "Outros times mandam ofertas pelo seu elenco durante as janelas. Jogadores " +
                    "que pediram pra sair atraem propostas com 70% de chance por rodada e por " +
                    "valores mais agressivos (até +15% sobre o mercado).\n\n" +
                    "Recusar uma proposta que o jogador queria aceitar PESA MUITO na moral. As " +
                    "propostas FICAM NA LISTA mesmo depois de resolvidas (aceitas/recusadas/expiradas) " +
                    "para você revisar o histórico.")
            )
        ),
        Topic(
            id = "sponsors",
            emoji = "💼",
            title = "Patrocínios",
            summary = "Tiers, contratos, bônus por vitória e multas de cancelamento.",
            sections = listOf(
                Section("Como funciona",
                    "Ofertas de patrocínio aparecem periodicamente. Cada uma tem:\n" +
                    "• TIER — bronze 🥉 / prata 🥈 / ouro 🥇 / diamante 💎 (define o valor)\n" +
                    "• CATEGORIA — energético, periféricos, banco, fast food, telecom etc.\n" +
                    "• VALOR SEMANAL — pago todo domingo enquanto ativo\n" +
                    "• DURAÇÃO — em semanas\n" +
                    "• BÔNUS POR VITÓRIA / PENALIDADE POR DERROTA (opcional)"),
                Section("Requisitos",
                    "Patrocínios maiores exigem REPUTAÇÃO mínima do técnico (sobe com vitórias) " +
                    "e/ou WIN RATE mínimo no split atual. Ofertas que não atendem ficam visíveis " +
                    "mas com indicação de bloqueio."),
                Section("Cancelar",
                    "Você pode cancelar um contrato antes do fim, mas paga MULTA de 4 semanas " +
                    "de pagamento. Use apenas se o contrato estiver claramente dando prejuízo " +
                    "(muitas derrotas + penalty alta).")
            )
        ),
        Topic(
            id = "bank",
            emoji = "🏦",
            title = "Banco e Finanças",
            summary = "Empréstimos, saúde financeira, folha salarial e limite de crédito.",
            sections = listOf(
                Section("Linhas de crédito",
                    "O banco oferece 4 linhas, liberadas conforme sua reputação:\n" +
                    "💰 Micro-empréstimo (rep 0+)\n" +
                    "🆘 Emergencial (rep 20+)\n" +
                    "📈 Investimento (rep 50+)\n" +
                    "🏛️ Mega (rep 75+)\n\n" +
                    "Cada uma tem principal, juros e prazo (em semanas) próprios. As parcelas " +
                    "são cobradas TODO DOMINGO automaticamente."),
                Section("Saúde financeira",
                    "Indicador visual no Hub que reflete sua relação caixa × despesas semanais:\n" +
                    "🟢 SAUDÁVEL — caixa confortável\n" +
                    "🟡 ATENÇÃO — apertando\n" +
                    "🔴 CRÍTICO — perto de quebrar"),
                Section("Quitação antecipada",
                    "Você pode quitar o saldo devedor de um empréstimo a qualquer momento (paga " +
                    "tudo de uma vez, sem desconto, mas livra-se das parcelas semanais). Útil " +
                    "quando um patrocínio diamante entra e sobra caixa.")
            )
        ),
        Topic(
            id = "academy",
            emoji = "🌱",
            title = "Categoria de Base",
            summary = "Como recrutar prospects, avaliar potencial e promover ao elenco.",
            sections = listOf(
                Section("Prospects",
                    "Jovens de 16-19 anos vivem na academia. Têm overall ATUAL baixo (45-62) " +
                    "mas potencial OCULTO (55-90+). Crescem com o tempo enquanto a academia " +
                    "estiver mantida."),
                Section("Avaliar",
                    "Custa R$ pra avaliar um prospect e REVELAR o potencial exato (sem avaliação, " +
                    "você vê só a faixa: Limitado/Mediano/Promissor/Alto/Excepcional). Avaliar " +
                    "ajuda a decidir em quem investir tempo."),
                Section("Promover",
                    "Quando o prospect está próximo do potencial (ou na idade limite de 20), " +
                    "você pode PROMOVER ao elenco principal. Vira um Player de verdade, sem " +
                    "custo de transferência — só o salário do contrato base."),
                Section("Upgrade da academia",
                    "Tier afeta capacidade, velocidade de desenvolvimento e teto de potencial:\n" +
                    "🌱 BÁSICA (4 prospects, teto 78) — custo semanal R$ 10k\n" +
                    "🏫 ESTRUTURADA (6, teto 85, +40% velocidade) — upgrade R$ 300k\n" +
                    "🏆 ELITE (8, teto 92, +90% velocidade) — upgrade R$ 1M")
            )
        ),
        Topic(
            id = "scouting",
            emoji = "🔍",
            title = "Olheiros",
            summary = "Investigar jogadores rivais antes de contratar.",
            sections = listOf(
                Section("Por que escotear",
                    "Jogadores de outros times começam com atributos OCULTOS — você vê só nome, " +
                    "role, time, idade. Investir em scouting REVELA gradualmente o overall, " +
                    "salário real, contrato e champion pool, evitando contratações ruins."),
                Section("5 níveis",
                    "Cada jogador tem scout level 0-5. A cada N dias acompanhando (N depende do " +
                    "tier do departamento), sobe 1 nível e revela mais informação. Nível 5 = " +
                    "totalmente visível."),
                Section("Departamento",
                    "🔎 BÁSICO — 3 jogadores em paralelo, 3 dias/nível, R$ 8k/sem\n" +
                    "🔬 PROFISSIONAL — 5 jogadores, 2 dias/nível, R$ 25k/sem\n" +
                    "🛰️ ELITE — 8 jogadores, 1 dia/nível, R$ 80k/sem")
            )
        ),
        Topic(
            id = "training",
            emoji = "🏋️",
            title = "Treinos",
            summary = "Tipos de treino, cooldowns e outcomes.",
            sections = listOf(
                Section("Tipos de treino",
                    "⚔️ SCRIM — melhora team fight\n" +
                    "🎥 VOD REVIEW — criatividade + consistência\n" +
                    "🎮 SOLO QUEUE — lane phase + clutch (risco de tilt)\n" +
                    "💪 ACADEMIA — moral + reduz risco de lesão\n" +
                    "🎉 TEAM BUILDING — moral coletivo\n" +
                    "🔥 BOOT CAMP — vários atributos, mas caro e cansativo"),
                Section("Duração + Cooldown",
                    "Cada tipo bloqueia X dias do calendário (duração) e depois precisa Y dias " +
                    "de descanso antes de repetir (cooldown). Boot Camp é o mais longo: 7 dias " +
                    "de treino + 30 de cooldown."),
                Section("Outcomes",
                    "O resultado é sorteado, ponderado por moral do elenco e level do técnico:\n" +
                    "✨ EXCELENTE — efeitos máximos\n" +
                    "✅ BOM — efeitos normais\n" +
                    "🟡 REGULAR — efeitos modestos\n" +
                    "⚠️ RUIM — sem efeito ou pequena perda\n" +
                    "💥 DESASTRE — efeito negativo sério")
            )
        ),
        Topic(
            id = "bonds",
            emoji = "🤝",
            title = "Química e laços",
            summary = "Como pares de jogadores formam laços e afetam a simulação.",
            sections = listOf(
                Section("Como funciona",
                    "Cada par de jogadores TITULARES forma um LAÇO (-100 a +100). O laço evolui " +
                    "com:\n" +
                    "• Convivência (dias juntos no mesmo elenco)\n" +
                    "• Vitórias/derrotas compartilhadas\n" +
                    "• Eventos especiais (jogada ensaiada, briga, etc.)"),
                Section("Faixas",
                    "☠️ Rivalidade (-100 a -60) — sabotam o ambiente\n" +
                    "⚡ Atrito (-59 a -20) — desentendimentos\n" +
                    "🤝 Neutro (-19 a +19) — colegas de trabalho\n" +
                    "😎 Entrosados (+20 a +59) — entrosagem acima da média\n" +
                    "🔥 Parceria (+60 a +100) — combos ensaiados"),
                Section("Impacto",
                    "A média dos laços entre os 5 titulares vira um bônus/penalidade aplicado na " +
                    "força do time durante a simulação. Times entrosados ganham brigas que " +
                    "estariam empatadas em overall.")
            )
        ),
        Topic(
            id = "events",
            emoji = "🎭",
            title = "Eventos fora de jogo",
            summary = "Lesões, entrevistas, polêmicas e como reagir.",
            sections = listOf(
                Section("Quando acontecem",
                    "Entre BO3s, o motor pode gerar eventos narrativos: lesão, entrevista boa/ruim, " +
                    "polêmica, jogada ensaiada entre dois jogadores, briga, família assistindo, etc. " +
                    "Aparecem numa tela própria antes da próxima partida."),
                Section("Efeitos",
                    "Cada evento traz mudança imediata em MORAL e/ou modificador TEMPORÁRIO de " +
                    "overall (com duração em dias). Pode afetar:\n" +
                    "• Um jogador específico (lesão, entrevista)\n" +
                    "• Uma dupla (jogada ensaiada, briga — afeta o laço)\n" +
                    "• O time inteiro (polêmica, visita de patrocinador)"),
                Section("Sem ação direta",
                    "Você apenas LÊ o evento e segue jogando — não há como evitar. Mas a moral " +
                    "alta do elenco antes do evento reduz o impacto negativo, então cuidar do " +
                    "vestiário continuamente ajuda.")
            )
        ),
        Topic(
            id = "history",
            emoji = "📜",
            title = "Histórico e Notícias",
            summary = "Onde acompanhar tudo que aconteceu na carreira.",
            sections = listOf(
                Section("Histórico completo",
                    "Card 📜 no Hub abre um dialog com TODOS os eventos da carreira: partidas, " +
                    "transferências, patrocínios, banco, treinos, moral, química. Filtros por " +
                    "categoria no topo, rolagem infinita."),
                Section("Feed de notícias",
                    "Card 📰 (ou botão \"Notícias\" no Hub) abre o feed editorial — manchetes " +
                    "redigidas por portais fictícios cobrindo a sua trajetória. Não afeta " +
                    "simulação, é puro imersivo."),
                Section("Mini-log no Hub",
                    "A lista \"Atividade Recente\" no Hub mostra os últimos eventos. Para o " +
                    "histórico completo com filtros, use o botão \"Ver tudo →\".")
            )
        ),
        Topic(
            id = "coach",
            emoji = "🎓",
            title = "Perfil do Técnico",
            summary = "Level, XP, atributos derivados e reputação.",
            sections = listOf(
                Section("Level + XP",
                    "Você ganha XP por várias ações:\n" +
                    "• Vencer mapas/séries\n" +
                    "• Fazer pick & ban manual (vs. deixar a IA)\n" +
                    "• Contratar/renegociar jogadores\n" +
                    "• Promover prospects da base\n\n" +
                    "Cada level pede mais XP (curva quadrática). O TÍTULO do técnico evolui:\n" +
                    "Iniciante → Aprendiz → Profissional → Veterano → Lendário"),
                Section("Reputação",
                    "0-100. Sobe com vitórias e marcos; desce com derrotas pesadas. Influencia:\n" +
                    "• Linhas de crédito disponíveis no banco\n" +
                    "• Patrocínios elegíveis\n" +
                    "• Upgrade dos departamentos (academia, olheiros)\n" +
                    "• Aceitação dos jogadores em renegociações de salário"),
                Section("Atributos derivados",
                    "Calculados a partir das estatísticas acumuladas — mostra seu \"estilo\" de " +
                    "treinador (ofensivo, paciente, gestor financeiro, formador de base etc.)")
            )
        )
    )
}
