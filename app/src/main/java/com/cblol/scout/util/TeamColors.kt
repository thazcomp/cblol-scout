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
        "los"       to Color.parseColor("#E65100")
    )

    fun forTeam(teamId: String): Int = palette[teamId] ?: Color.parseColor("#546E7A")

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
