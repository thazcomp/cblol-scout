package com.cblol.scout.util

import com.cblol.scout.data.Champion

/**
 * Repositório local de campeões para a fase de pick & ban.
 *
 * Contém os ~80 campeões mais jogados no meta competitivo de 2026 Split 1.
 * IDs seguem exatamente a nomenclatura da Riot Data Dragon
 * (usado para montar a imageUrl/splashUrl automaticamente no Champion).
 *
 * Para expandir: adicione entradas seguindo o mesmo padrão.
 */
object ChampionRepository {

    private val champions: List<Champion> by lazy { buildList() }

    fun getAll(): List<Champion> = champions

    fun getById(id: String): Champion? = champions.firstOrNull { it.id == id }

    fun getByRole(role: String): List<Champion> = champions.filter { role in it.roles }

    private fun champ(id: String, vararg roles: String) = Champion(
        id          = id,
        name        = id,
        shortName   = if (id.length > 8) id.take(7) + "…" else id,
        roles       = roles.toList(),
        primaryRole = roles.first()
    )

    private fun buildList(): List<Champion> = listOf(
        // ── TOP ─────────────────────────────────────────────────────────
        champ("Aatrox",         "TOP"),
        champ("Camille",        "TOP"),
        champ("Darius",         "TOP"),
        champ("Fiora",          "TOP"),
        champ("Gangplank",      "TOP"),
        champ("Garen",          "TOP"),
        champ("Gnar",           "TOP"),
        champ("Gragas",         "TOP", "JNG"),
        champ("Irelia",         "TOP", "MID"),
        champ("Jax",            "TOP", "JNG"),
        champ("Jayce",          "TOP", "MID"),
        champ("Kennen",         "TOP"),
        champ("Malphite",       "TOP"),
        champ("Mordekaiser",    "TOP"),
        champ("Ornn",           "TOP"),
        champ("Renekton",       "TOP"),
        champ("Riven",          "TOP"),
        champ("Sett",           "TOP"),
        champ("Teemo",          "TOP"),
        champ("Urgot",          "TOP"),
        champ("Vladimir",       "TOP", "MID"),

        // ── JUNGLE ──────────────────────────────────────────────────────
        champ("Belveth",        "JNG"),
        champ("Diana",          "JNG", "MID"),
        champ("Ekko",           "JNG", "MID"),
        champ("Elise",          "JNG"),
        champ("Evelynn",        "JNG"),
        champ("Graves",         "JNG"),
        champ("Hecarim",        "JNG"),
        champ("Jarvaniv",       "JNG"),
        champ("Kayn",           "JNG"),
        champ("Khazix",         "JNG"),
        champ("KhaZix",         "JNG"),
        champ("LeeSin",         "JNG"),
        champ("Lillia",         "JNG"),
        champ("Nidalee",        "JNG"),
        champ("Nocturne",       "JNG"),
        champ("Poppy",          "JNG", "TOP"),
        champ("RekSai",         "JNG"),
        champ("Sejuani",        "JNG"),
        champ("Shyvana",        "JNG"),
        champ("Vi",             "JNG"),
        champ("Viego",          "JNG"),
        champ("Wukong",         "JNG", "TOP"),
        champ("Xinzhao",        "JNG"),
        champ("Zac",            "JNG"),

        // ── MID ─────────────────────────────────────────────────────────
        champ("Ahri",           "MID"),
        champ("Akali",          "MID", "TOP"),
        champ("Anivia",         "MID"),
        champ("Annie",          "MID"),
        champ("Azir",           "MID"),
        champ("Cassiopeia",     "MID"),
        champ("Corki",          "MID"),
        champ("Fizz",           "MID"),
        champ("Galio",          "MID"),
        champ("Katarina",       "MID"),
        champ("Leblanc",        "MID"),
        champ("Lissandra",      "MID"),
        champ("Lux",            "MID", "SUP"),
        champ("Malzahar",       "MID"),
        champ("Naafiri",        "MID"),
        champ("Orianna",        "MID"),
        champ("Ryze",           "MID"),
        champ("Syndra",         "MID"),
        champ("Taliyah",        "MID", "JNG"),
        champ("Talon",          "MID", "JNG"),
        champ("TwistedFate",    "MID"),
        champ("Veigar",         "MID"),
        champ("Viktor",         "MID"),
        champ("Xerath",         "MID"),
        champ("Yasuo",          "MID"),
        champ("Yone",           "MID", "TOP"),
        champ("Zed",            "MID"),
        champ("Zoe",            "MID"),

        // ── ADC ─────────────────────────────────────────────────────────
        champ("Aphelios",       "ADC"),
        champ("Ashe",           "ADC"),
        champ("Caitlyn",        "ADC"),
        champ("Draven",         "ADC"),
        champ("Ezreal",         "ADC"),
        champ("Jhin",           "ADC"),
        champ("Jinx",           "ADC"),
        champ("Kaisa",          "ADC"),
        champ("Kalista",        "ADC"),
        champ("Lucian",         "ADC", "MID"),
        champ("MissFortune",    "ADC"),
        champ("Nilah",          "ADC"),
        champ("Samira",         "ADC"),
        champ("Sivir",          "ADC"),
        champ("Tristana",       "ADC", "MID"),
        champ("Varus",          "ADC", "MID"),
        champ("Vayne",          "ADC", "TOP"),
        champ("Xayah",          "ADC"),
        champ("Zeri",           "ADC"),

        // ── SUPPORT ─────────────────────────────────────────────────────
        champ("Alistar",        "SUP"),
        champ("Bard",           "SUP"),
        champ("Blitzcrank",     "SUP"),
        champ("Brand",          "SUP"),
        champ("Karma",          "SUP"),
        champ("Leona",          "SUP"),
        champ("Lulu",           "SUP"),
        champ("Milio",          "SUP"),
        champ("Nami",           "SUP"),
        champ("Nautilus",       "SUP"),
        champ("Pyke",           "SUP"),
        champ("Rakan",          "SUP"),
        champ("Renata",         "SUP"),
        champ("Senna",          "SUP", "ADC"),
        champ("Seraphine",      "SUP", "MID"),
        champ("Soraka",         "SUP"),
        champ("Tahm",           "SUP", "TOP"),
        champ("Taric",          "SUP"),
        champ("Thresh",         "SUP"),
        champ("Yuumi",          "SUP"),
        champ("Zilean",         "SUP"),
        champ("Zyra",           "SUP")
    )
}
