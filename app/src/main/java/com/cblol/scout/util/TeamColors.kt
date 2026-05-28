package com.cblol.scout.util

import android.graphics.Color

object TeamColors {
    private val palette = mapOf(
        "fluxo_w7m" to Color.parseColor("#0057B8"),
        "furia"     to Color.parseColor("#1A1A1A"),
        "keyd"      to Color.parseColor("#C8A600"),
        "loud"      to Color.parseColor("#00C851"),
        "pain"      to Color.parseColor("#D32F2F"),
        "red"       to Color.parseColor("#B71C1C"),
        "leviatan"  to Color.parseColor("#6A0DAD"),
        "los"       to Color.parseColor("#E65100"),
        // Time virtual da 2ª divisão — cor neutra cinza-azulada para
        // diferenciar visualmente dos clubes da 1ª divisão sem competir com
        // as marcas reais.
        "FREE_AGENT_CD" to Color.parseColor("#37474F")
    )

    /**
     * Cor associada a um time. Para ids cadastrados na [palette] (1ª divisão
     * e time virtual do CD), retorna a cor exata. Para ids procedurais da 2ª
     * divisão (prefixo "cd2_"), deriva uma cor estável do hash do id — mesmo
     * time sempre tem a mesma cor entre sessões, sem precisar cadastrar 15
     * paletas. Fallback final é um cinza neutro.
     */
    fun forTeam(teamId: String): Int {
        palette[teamId]?.let { return it }
        if (teamId.startsWith("cd2_")) return generatedColorFor(teamId)
        return Color.parseColor("#546E7A")
    }

    /**
     * Cores distintas e legíveis sobre fundo escuro, sorteadas pelo hash do id
     * do time. Evita verde/vermelho usados pelos times reais e mantém luminosidade
     * coerente com a UI em modo dark do app.
     */
    private val CD2_PALETTE = listOf(
        Color.parseColor("#3949AB"),  // índigo
        Color.parseColor("#00838F"),  // turquesa escuro
        Color.parseColor("#5D4037"),  // marrom
        Color.parseColor("#7B1FA2"),  // roxo
        Color.parseColor("#0277BD"),  // azul oceânico
        Color.parseColor("#558B2F"),  // verde-oliva
        Color.parseColor("#EF6C00"),  // laranja queimado
        Color.parseColor("#AD1457"),  // magenta escuro
        Color.parseColor("#455A64"),  // grafite
        Color.parseColor("#6A1B9A")   // ametista
    )

    private fun generatedColorFor(teamId: String): Int {
        val idx = (teamId.hashCode().let { if (it < 0) -it else it }) % CD2_PALETTE.size
        return CD2_PALETTE[idx]
    }

    fun roleColor(role: String): Int = when (role) {
        "TOP" -> Color.parseColor("#4CAF50")
        "JNG" -> Color.parseColor("#9C27B0")
        "MID" -> Color.parseColor("#2196F3")
        "ADC" -> Color.parseColor("#F44336")
        "SUP" -> Color.parseColor("#FF9800")
        else  -> Color.parseColor("#607D8B")
    }

    fun flagEmoji(code: String): String = when (code.uppercase()) {
        "BR" -> "🇧🇷"
        "KR" -> "🇰🇷"
        "AR" -> "🇦🇷"
        "CL" -> "🇨🇱"
        "VE" -> "🇻🇪"
        "CO" -> "🇨🇴"
        "PE" -> "🇵🇪"
        "HK" -> "🇭🇰"
        else -> "🏳"
    }
}
