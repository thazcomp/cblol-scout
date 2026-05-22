package com.cblol.scout.data.seed

/**
 * **Dados de seed dos champion pools.**
 *
 * Fonte hardcoded única usada apenas para popular o banco Realm na primeira
 * execução. Após o seed, o `ChampionPoolRepository` lê do Realm via
 * [com.cblol.scout.domain.datasource.StaticDataSource].
 *
 * Dois conjuntos:
 *  - [signaturePools]: pools fixos de jogadores históricos do CBLOL (signature
 *    picks), indexados pela chave em minúsculas (id/nome de jogo).
 *  - [rolePools]: catálogo de mains "comuns" por role, para jogadores sem pool
 *    fixo (o `ChampionPoolRepository` sorteia 3-5 destes de forma determinística).
 */
object ChampionPoolSeed {

    /** Pools fixos para jogadores históricos do cenário brasileiro. */
    val signaturePools: Map<String, List<String>> = mapOf(
        // Top
        "robo"    to listOf("Aatrox", "Sett", "Renekton", "Camille", "Jax"),
        "burdol"  to listOf("Gangplank", "Camille", "Jayce", "Gnar", "Aatrox"),
        "wizer"   to listOf("Yone", "Jax", "Renekton", "Camille", "Sett"),
        // Jungle
        "ranger"  to listOf("Vi", "Viego", "LeeSin", "Xinzhao", "Kayn"),
        "cariok"  to listOf("Sejuani", "Vi", "Hecarim", "Diana", "Wukong"),
        "shini"   to listOf("Xinzhao", "Viego", "LeeSin", "Graves", "Kayn"),
        "trigo"   to listOf("Belveth", "Viego", "Kayn", "LeeSin", "Hecarim"),
        // Mid
        "tinowns" to listOf("Azir", "Orianna", "Syndra", "Ahri", "Viktor"),
        "envy"    to listOf("Yone", "Yasuo", "Akali", "Sylas", "LeBlanc"),
        "mireu"   to listOf("Orianna", "Azir", "Ahri", "Corki", "Taliyah"),
        "kuri"    to listOf("Akali", "Yone", "Sylas", "Zed", "LeBlanc"),
        // ADC
        "titan"   to listOf("Kaisa", "Jinx", "Ezreal", "Aphelios", "Varus"),
        "brance"  to listOf("Aphelios", "Jinx", "Caitlyn", "Kaisa", "Zeri"),
        "stuart"  to listOf("Lucian", "Draven", "Samira", "Tristana", "Kaisa"),
        "scary"   to listOf("Jinx", "Ezreal", "Kaisa", "Varus", "Aphelios"),
        // Support
        "cronos"  to listOf("Thresh", "Nautilus", "Rakan", "Leona", "Karma"),
        "jojo"    to listOf("Leona", "Rakan", "Nautilus", "Thresh", "Alistar"),
        "frosty"  to listOf("Karma", "Lulu", "Nami", "Janna", "Milio"),
        "tay"     to listOf("Thresh", "Rakan", "Pyke", "Nautilus", "Leona")
    )

    /** Catálogo de mains "comuns" por role para jogadores sem pool fixo. */
    val rolePools: Map<String, List<String>> = mapOf(
        "TOP" to listOf(
            "Aatrox", "Camille", "Renekton", "Gnar", "Gangplank", "Jax", "Sett",
            "Fiora", "Jayce", "Yone", "Riven", "Mordekaiser", "Ornn", "Malphite",
            "Darius", "Garen", "Vladimir", "Akali", "Irelia", "Kennen"
        ),
        "JNG" to listOf(
            "Viego", "LeeSin", "Xinzhao", "Vi", "Kayn", "Diana", "Hecarim",
            "Belveth", "Graves", "Sejuani", "KhaZix", "Evelynn", "Wukong", "Nidalee",
            "Jarvaniv", "Lillia", "Ekko", "Elise", "Amumu", "Zac"
        ),
        "MID" to listOf(
            "Azir", "Orianna", "Ahri", "Syndra", "Yone", "Akali", "Sylas",
            "LeBlanc", "Zed", "Viktor", "Taliyah", "Corki", "Lissandra", "Cassiopeia",
            "Veigar", "Annie", "Galio", "TwistedFate", "Ryze", "Talon"
        ),
        "ADC" to listOf(
            "Jinx", "Kaisa", "Aphelios", "Ezreal", "Varus", "Caitlyn", "Zeri",
            "Lucian", "Samira", "Draven", "Jhin", "Tristana", "Vayne", "Xayah",
            "MissFortune", "Sivir", "Ashe", "Kalista", "Nilah"
        ),
        "SUP" to listOf(
            "Thresh", "Nautilus", "Leona", "Rakan", "Karma", "Lulu", "Milio",
            "Nami", "Pyke", "Blitzcrank", "Alistar", "Soraka", "Janna", "Renata",
            "Seraphine", "Yuumi", "Brand", "Zyra", "Bard"
        )
    )
}
