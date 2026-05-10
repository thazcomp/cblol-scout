package com.cblol.scout.game

/**
 * Pool de campeões do LoL agrupados por role meta. Não é exaustivo — é um subconjunto
 * popular o suficiente pra que pick/bans em uma partida pareçam plausíveis.
 *
 * Mantido como código (não JSON) porque é um recurso estático e pequeno.
 */
object Champions {

    val TOP = listOf(
        "Aatrox", "Camille", "Fiora", "Gangplank", "Garen", "Gnar", "Gwen", "Irelia",
        "Jax", "Jayce", "K'Sante", "Kennen", "Kled", "Malphite", "Mordekaiser", "Nasus",
        "Ornn", "Poppy", "Renekton", "Rumble", "Sett", "Shen", "Singed", "Sion",
        "Tahm Kench", "Trundle", "Tryndamere", "Urgot", "Volibear", "Yone", "Yorick", "Zac"
    )

    val JNG = listOf(
        "Bel'Veth", "Diana", "Elise", "Evelynn", "Fiddlesticks", "Graves", "Hecarim",
        "Ivern", "Jarvan IV", "Karthus", "Kayn", "Kha'Zix", "Kindred", "Lee Sin",
        "Lillia", "Master Yi", "Nidalee", "Nocturne", "Olaf", "Rammus", "Rek'Sai",
        "Rengar", "Sejuani", "Shaco", "Skarner", "Taliyah", "Udyr", "Vi", "Viego",
        "Warwick", "Wukong", "Xin Zhao", "Zac"
    )

    val MID = listOf(
        "Ahri", "Akali", "Anivia", "Annie", "Aurelion Sol", "Azir", "Cassiopeia",
        "Corki", "Ekko", "Fizz", "Galio", "Hwei", "Kassadin", "Katarina", "LeBlanc",
        "Lissandra", "Lux", "Malzahar", "Neeko", "Orianna", "Qiyana", "Ryze", "Sylas",
        "Syndra", "Talon", "Twisted Fate", "Veigar", "Vex", "Viktor", "Vladimir",
        "Xerath", "Yasuo", "Yone", "Zed", "Ziggs", "Zoe"
    )

    val ADC = listOf(
        "Aphelios", "Ashe", "Caitlyn", "Draven", "Ezreal", "Jhin", "Jinx", "Kai'Sa",
        "Kalista", "Kog'Maw", "Lucian", "Miss Fortune", "Nilah", "Samira", "Senna",
        "Sivir", "Tristana", "Twitch", "Varus", "Vayne", "Xayah", "Zeri"
    )

    val SUP = listOf(
        "Alistar", "Bard", "Blitzcrank", "Braum", "Brand", "Janna", "Karma", "Leona",
        "Lulu", "Maokai", "Milio", "Morgana", "Nami", "Nautilus", "Pantheon", "Pyke",
        "Rakan", "Rell", "Renata Glasc", "Seraphine", "Sona", "Soraka", "Swain",
        "Tahm Kench", "Taric", "Thresh", "Velkoz", "Yuumi", "Zilean", "Zyra"
    )

    val ALL_FLAT: List<String> = (TOP + JNG + MID + ADC + SUP).distinct()

    fun forRole(role: String): List<String> = when (role) {
        "TOP" -> TOP
        "JNG" -> JNG
        "MID" -> MID
        "ADC" -> ADC
        "SUP" -> SUP
        else  -> ALL_FLAT
    }
}
