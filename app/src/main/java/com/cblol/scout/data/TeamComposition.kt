package com.cblol.scout.data

/**
 * Composição de campeões que concede bônus de força quando montada durante o pick & ban.
 *
 * @param id           identificador único da comp
 * @param name         nome display (ex: "Wombo Combo")
 * @param description  breve descrição da estratégia
 * @param archetype    tipo arquétipo (ENGAGE, POKE, SPLIT, PROTECT, PICK, WOMBO)
 * @param requiredPicks campeões obrigatórios para a comp ser reconhecida (mínimo 3 de 5)
 * @param keyChampions  campeões que, se banidos, destroem a comp (importantes para os bans)
 * @param bonusStrength bônus de força aplicado ao teamStrength (0–15 pontos)
 * @param tier          S = dominante, A = sólida, B = situacional
 */
data class TeamComposition(
    val id: String,
    val name: String,
    val description: String,
    val archetype: CompArchetype,
    val requiredPicks: List<String>,   // precisa de >= minRequired desses
    val minRequired: Int = 3,
    val keyChampions: List<String>,    // banir qualquer um desses quebra a comp
    val bonusStrength: Int,
    val tier: String = "A"
)

enum class CompArchetype {
    ENGAGE,   // dive/teamfight — Malphite, Amumu, Orianna
    POKE,     // desgaste à distância — Jayce, Ezreal, Xerath
    SPLIT,    // pressão em lane — Fiora, Tryndamere, Camille
    PROTECT,  // guardião do carry — Lulu, Karma, Yuumi
    PICK,     // assassinato isolado — Blitzcrank, Lissandra, Zed
    WOMBO,    // AoE devastador — Amumu, MF, Orianna
    PEEL,     // suporte ao ADC — Thresh, Janna, Nautilus
    SCALING,  // hipercarry tardio — Kassadin, Kayle, Vayne
    CONTROL,  // controle de mapa — Taliyah, Azir, Viktor
}

/**
 * Resultado da análise de composição após o pick & ban.
 */
data class CompAnalysisResult(
    val detected: TeamComposition?,
    val matchedChampions: List<String>,
    val bonusStrength: Int,
    val description: String
)
