package com.cblol.scout.data

/**
 * Características funcionais de um campeão relevantes para o draft.
 *
 * Cada tag representa uma capacidade tática que influencia:
 *  - Sugestão de picks (complementar o time)
 *  - Detecção de composições (CompositionRepository)
 *  - Cálculo de bônus no simulador (ex: ANTI_TANK contra time com 3+ tanques)
 *  - UI do pick & ban (ícones de tag sobre o card do campeão)
 */
enum class ChampionTag(val label: String, val icon: String, val description: String) {
    // ── Classe / Arquétipo ─────────────────────────────────────────────
    TANK(          "Tank",          "🛡️",  "Alta durabilidade, frontline, absorve dano"),
    FIGHTER(       "Bruiser",       "⚔️",  "Dano e durabilidade equilibrados"),
    ASSASSIN(      "Assassino",     "🗡️",  "Alto burst, elimina alvos isolados"),
    MAGE(          "Mago",          "🔮",  "Dano mágico, controle de teamfight"),
    MARKSMAN(      "Atirador",      "🏹",  "Dano físico contínuo à distância"),
    SUPPORT(       "Suporte",       "💚",  "Utilidade, visão, proteção ou engajamento"),
    SPECIALIST(    "Especialista",  "🌀",  "Não se encaixa em classe padrão"),

    // ── Dano ──────────────────────────────────────────────────────────
    MAGIC_DAMAGE(  "Dano Mágico",   "✨",  "Dano principal é mágico (AP)"),
    PHYSICAL_DAMAGE("Dano Físico",  "💪",  "Dano principal é físico (AD)"),
    TRUE_DAMAGE(   "Dano Verdadeiro","⚡", "Causa dano verdadeiro (ignora resistências)"),
    ANTI_TANK(     "Antitanque",    "🔱",  "Kit eficaz contra alvos com alta resistência"),
    BURST(         "Burst",         "💥",  "Mata alvos em poucos instantes"),
    SUSTAINED_DPS( "DPS Contínuo",  "🔄",  "Dano consistente em lutas prolongadas"),
    POKE(          "Poke",          "🎯",  "Dano à distância antes do engajamento"),

    // ── Engage / Mobilidade ────────────────────────────────────────────
    ENGAGE(        "Engage",        "🚀",  "Inicia teamfight com CC de área"),
    DISENGAGE(     "Disengage",     "🛡",  "Impede ou reverte engajamentos inimigos"),
    MOBILITY(      "Mobilidade",    "💨",  "Alta mobilidade / dashes"),
    DIVE(          "Dive",          "🏊",  "Salta sobre frontline para alcançar carries"),
    GAP_CLOSER(    "Gap Closer",    "🏃",  "Fecha distância rapidamente"),

    // ── Controle / Utilidade ───────────────────────────────────────────
    CROWD_CONTROL( "CC",            "🔒",  "Possui controle de grupo (stun, root, knock-up)"),
    KNOCK_UP(      "Knock-up",      "☝️",  "Causa knock-up (ativa Wind Wall / Last Breath)"),
    SLOW(          "Slow",          "🐢",  "Possui slow significativo"),
    SILENCE(       "Silêncio",      "🤫",  "Pode silenciar inimigos"),
    SUPPRESSION(   "Suppressão",    "⛓",  "Supressão (imune a CC normais)"),
    VISION(        "Visão",         "👁",  "Gera visão / ward especial"),
    ZONE_CONTROL(  "Zone Control",  "🗺️",  "Controla áreas do mapa com habilidades"),

    // ── Suporte / Sustain ──────────────────────────────────────────────
    HEAL(          "Cura",          "💉",  "Cura aliados ou a si mesmo"),
    SHIELD(        "Escudo",        "🔰",  "Concede escudos a aliados"),
    ENCHANTER(     "Enchanter",     "🧚",  "Amplifica carries com buffs e curas"),
    SUSTAIN(       "Sustain",       "♻️",  "Auto-sustain em lane (lifesteal / regeneração)"),

    // ── Objetivos / Mapa ──────────────────────────────────────────────
    OBJECTIVE_CONTROL("Objetivos",  "🏆",  "Forte em drakes, baron, torre"),
    SPLIT_PUSH(    "Split Push",    "🔀",  "Pressiona lanes individualmente"),
    WAVE_CLEAR(    "Wave Clear",    "🌊",  "Limpa ondas de minions rápido"),
    ROAMER(        "Roamer",        "🗺",  "Forte impacto ao rotacionar entre lanes"),

    // ── Ultimate ──────────────────────────────────────────────────────
    GLOBAL_ULT(    "Ult Global",    "🌐",  "Ultimate alcança qualquer ponto do mapa"),
    GAME_CHANGING_ULT("Ult Decisivo","💫","Ultimate pode mudar o resultado de lutas"),
    REVIVE(        "Ressurreição",  "👼",  "Pode reviver aliado ou a si mesmo"),

    // ── Escala ────────────────────────────────────────────────────────
    HYPERCARRY(    "HyperCarry",    "🌟",  "Escala absurdamente no late game"),
    EARLY_GAME(    "Early Game",    "⏩",  "Dominante no início da partida"),
    LATE_GAME(     "Late Game",     "📈",  "Aumenta de poder com o tempo"),

    // ── Estilo ────────────────────────────────────────────────────────
    TEAMFIGHT(     "TeamFight",     "🥊",  "Excelente em lutas de time"),
    DUELIST(       "Duelista",      "🤺",  "Forte em confrontos 1v1"),
    PROTECT_CARRY( "Protect",       "🤝",  "Kit voltado a proteger aliados"),
    SKIRMISHER(    "Skirmisher",    "🌪️",  "Luta em pequenos grupos, escapes e dano sustentado"),
    INVISIBLE(     "Invisível",     "🫥",  "Possui invisibilidade ou ocultação"),
    UNTARGETABLE(  "Intocável",     "🌫️",  "Habilidade que concede untargetable"),
    EXECUTE(       "Execução",      "☠️",  "Dano extra em alvos com pouca vida")
}
