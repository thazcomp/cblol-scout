package com.cblol.scout.data.seed

import com.cblol.scout.data.CompArchetype
import com.cblol.scout.data.TeamComposition

/**
 * **Dados de seed das composições de time.**
 *
 * Fonte hardcoded única usada apenas para popular o banco Realm na primeira
 * execução (ver [com.cblol.scout.data.realm.RealmSeeder]). Após o seed, o
 * `CompositionRepository` lê do Realm via
 * [com.cblol.scout.domain.datasource.StaticDataSource].
 *
 * Cada comp tem uma lista AMPLA em `requiredPicks` com `minRequired` baixo
 * (2-3), permitindo que múltiplas combinações formem a mesma comp.
 * `keyChampions` são os 2-3 campeões cujo ban quebra a composição.
 */
object CompositionSeed {

    fun all(): List<TeamComposition> = comps

    private val comps: List<TeamComposition> = listOf(

        // ═════════════════════════════════════════════════════════════════
        // TIER S — COMPOSIÇÕES DOMINANTES DO META 2026
        // ═════════════════════════════════════════════════════════════════
        TeamComposition(
            id = "wombo_combo", name = "Wombo Combo",
            description = "AoE devastador: ulti encadeado que apaga o time inimigo em segundos. " +
                "Requer posicionamento e ativação simultânea.",
            archetype = CompArchetype.WOMBO,
            requiredPicks = listOf(
                "Malphite", "Amumu", "Kennen", "Sett", "Wukong", "Sejuani", "Galio", "Zac",
                "Orianna", "MissFortune", "Yasuo", "Yone", "Seraphine",
                "Jarvaniv", "Diana"
            ),
            minRequired = 3,
            keyChampions = listOf("Orianna", "MissFortune", "Yasuo"),
            bonusStrength = 14, tier = "S"
        ),
        TeamComposition(
            id = "protect_the_carry", name = "Protect the Carry",
            description = "Hipercarry protegido por múltiplas camadas de escudos e cura. " +
                "Scales infinitamente no late game.",
            archetype = CompArchetype.PROTECT,
            requiredPicks = listOf(
                "Jinx", "Kaisa", "Aphelios", "Zeri", "Vayne",
                "Lulu", "Karma", "Soraka", "Yuumi", "Milio", "Nami", "Seraphine"
            ),
            minRequired = 3,
            keyChampions = listOf("Lulu", "Jinx", "Kaisa"),
            bonusStrength = 13, tier = "S"
        ),
        TeamComposition(
            id = "poke_siege", name = "Poke & Siege",
            description = "Desgaste contínuo antes de lutas. Difícil de engajar sem tomar dano. " +
                "Vence batendo em objetivos.",
            archetype = CompArchetype.POKE,
            requiredPicks = listOf(
                "Jayce", "Xerath", "Varus", "Ezreal", "Caitlyn", "Nidalee", "Zoe", "Lux",
                "Karma", "Senna",
                "Corki", "Gangplank"
            ),
            minRequired = 3,
            keyChampions = listOf("Jayce", "Xerath", "Varus"),
            bonusStrength = 12, tier = "S"
        ),
        TeamComposition(
            id = "engage_snowball", name = "Hard Engage Snowball",
            description = "Engage agressivo e repetitivo. Ganha com CC em cadeia e " +
                "pressão constante sobre o mapa.",
            archetype = CompArchetype.ENGAGE,
            requiredPicks = listOf(
                "Leona", "Nautilus", "Malphite", "Amumu", "Sejuani", "Rakan", "Alistar",
                "Blitzcrank", "Thresh", "Wukong", "Jarvaniv", "Zac",
                "Diana", "Kennen", "Orianna", "Yasuo", "Yone", "Sett"
            ),
            minRequired = 3,
            keyChampions = listOf("Leona", "Nautilus", "Malphite"),
            bonusStrength = 12, tier = "S"
        ),
        TeamComposition(
            id = "dive_comp", name = "Dive / Tower Dive",
            description = "Pula no back-line inimigo ignorando frontline. " +
                "Vence one-shotando carries antes da troca de dano.",
            archetype = CompArchetype.DIVE,
            requiredPicks = listOf(
                "Diana", "Hecarim", "Wukong", "Camille", "Irelia", "Vi", "Xinzhao",
                "KhaZix", "Nocturne", "Kayn",
                "Kaisa", "Akali", "Fizz", "Ekko", "Yone"
            ),
            minRequired = 3,
            keyChampions = listOf("Hecarim", "Diana", "Wukong"),
            bonusStrength = 13, tier = "S"
        ),

        // ═════════════════════════════════════════════════════════════════
        // TIER A — COMPOSIÇÕES SÓLIDAS
        // ═════════════════════════════════════════════════════════════════
        TeamComposition(
            id = "split_push", name = "Split Push 1-3-1",
            description = "Um splitpusher divide o mapa enquanto o resto faz objetivos. " +
                "Força o oponente a tomar decisão difícil.",
            archetype = CompArchetype.SPLIT,
            requiredPicks = listOf(
                "Fiora", "Camille", "Jax", "Riven", "Gangplank", "Akali", "Renekton",
                "Urgot", "Jayce", "Mordekaiser",
                "Sivir", "Vayne",
                "Senna", "Ashe"
            ),
            minRequired = 2,
            keyChampions = listOf("Fiora", "Camille", "Jax"),
            bonusStrength = 10, tier = "A"
        ),
        TeamComposition(
            id = "pick_comp", name = "Pick Composition",
            description = "Assassinato isolado com alto burst. Uma abertura do oponente " +
                "vira morte certa e vantagem numérica.",
            archetype = CompArchetype.PICK,
            requiredPicks = listOf(
                "Blitzcrank", "Thresh", "Pyke", "Lissandra", "Ashe",
                "Zed", "Talon", "KhaZix", "Evelynn", "LeBlanc", "Ahri",
                "TwistedFate", "Bard", "Nidalee"
            ),
            minRequired = 3,
            keyChampions = listOf("Blitzcrank", "Zed", "Pyke"),
            bonusStrength = 10, tier = "A"
        ),
        TeamComposition(
            id = "azir_control", name = "Mage Control / Teamfight",
            description = "Controle absoluto de espaço e objetivos com zoning de mages. " +
                "Azir/Orianna ditam o posicionamento inimigo.",
            archetype = CompArchetype.CONTROL,
            requiredPicks = listOf(
                "Azir", "Orianna", "Taliyah", "Viktor", "Anivia", "Cassiopeia", "Ryze",
                "Vladimir", "Lissandra", "Malzahar",
                "Corki", "Syndra"
            ),
            minRequired = 2,
            keyChampions = listOf("Azir", "Orianna"),
            bonusStrength = 10, tier = "A"
        ),
        TeamComposition(
            id = "hyperscaling", name = "HyperScaling Late Game",
            description = "Aguenta o early game e explode no late. " +
                "Praticamente imbatível após 35 minutos.",
            archetype = CompArchetype.SCALING,
            requiredPicks = listOf(
                "Vayne", "Vladimir", "Jinx", "Senna", "Veigar",
                "Lulu", "Soraka", "Yuumi", "Karma"
            ),
            minRequired = 2,
            keyChampions = listOf("Vayne", "Veigar", "Vladimir"),
            bonusStrength = 9, tier = "A"
        ),
        TeamComposition(
            id = "xayah_rakan", name = "Xayah & Rakan Synergy",
            description = "Dupla bot com sinergia nativa única. Rakan dança enquanto " +
                "Xayah garante execução no teamfight.",
            archetype = CompArchetype.ENGAGE,
            requiredPicks = listOf("Xayah", "Rakan"),
            minRequired = 2,
            keyChampions = listOf("Xayah", "Rakan"),
            bonusStrength = 9, tier = "A"
        ),
        TeamComposition(
            id = "zeri_enchanters", name = "Zeri + Enchanters",
            description = "Zeri escala com escudos e speed stacks. " +
                "Com dois enchanters fica praticamente intocável.",
            archetype = CompArchetype.PROTECT,
            requiredPicks = listOf(
                "Zeri",
                "Lulu", "Karma", "Nami", "Seraphine", "Milio", "Yuumi", "Soraka"
            ),
            minRequired = 2,
            keyChampions = listOf("Zeri", "Lulu"),
            bonusStrength = 9, tier = "A"
        ),
        TeamComposition(
            id = "skirmish_brawl", name = "Skirmish / Brawl Comp",
            description = "Brigas curtas 2v2 e 3v3 no mapa. " +
                "Vence com micro mecânico e janelas de poder espalhadas.",
            archetype = CompArchetype.SKIRMISH,
            requiredPicks = listOf(
                "Graves", "Ekko", "LeeSin", "Viego", "KhaZix", "Kayn", "Belveth",
                "Camille", "Riven", "Fiora", "Sett", "Irelia", "Akali",
                "Lucian", "Samira", "Nilah", "Pyke", "Senna"
            ),
            minRequired = 3,
            keyChampions = listOf("Graves", "LeeSin", "Ekko"),
            bonusStrength = 10, tier = "A"
        ),
        TeamComposition(
            id = "early_invade", name = "Early Invade Pressure",
            description = "Pressiona o jungle inimigo desde o minuto 1. " +
                "Snowball pesado se acertar a level 2/3 dive.",
            archetype = CompArchetype.INVADE,
            requiredPicks = listOf(
                "LeeSin", "Xinzhao", "Elise", "Graves", "Nidalee", "RekSai",
                "Renekton", "Draven", "Lucian", "Caitlyn", "Zoe", "Syndra",
                "Pyke", "Blitzcrank", "Leona"
            ),
            minRequired = 3,
            keyChampions = listOf("LeeSin", "Elise", "Draven"),
            bonusStrength = 10, tier = "A"
        ),
        TeamComposition(
            id = "front_to_back", name = "Front-to-Back Teamfight",
            description = "Tank na frente, ADC atrás, mages no meio. " +
                "Vitória por engenharia de teamfight, não por jogadas individuais.",
            archetype = CompArchetype.PROTECT,
            requiredPicks = listOf(
                "Ornn", "Malphite", "Sejuani",
                "Aphelios", "Jinx", "Caitlyn", "Sivir",
                "Orianna", "Azir", "Viktor", "Karma"
            ),
            minRequired = 3,
            keyChampions = listOf("Ornn", "Aphelios", "Jinx"),
            bonusStrength = 11, tier = "A"
        ),
        TeamComposition(
            id = "global_ults", name = "Global Pressure",
            description = "Múltiplos ults globais permitem responder a qualquer luta no mapa. " +
                "Força o inimigo a se manter sempre agrupado.",
            archetype = CompArchetype.CONTROL,
            requiredPicks = listOf(
                "TwistedFate", "Ashe", "Nocturne",
                "Senna", "Soraka", "Bard", "Galio", "Taliyah"
            ),
            minRequired = 2,
            keyChampions = listOf("TwistedFate", "Senna", "Galio"),
            bonusStrength = 9, tier = "A"
        ),
        TeamComposition(
            id = "ardent_enchanters", name = "Ardent Enchanters",
            description = "ADC com speed-up e attack speed massivo via enchanters. " +
                "Domina lutas longas.",
            archetype = CompArchetype.PROTECT,
            requiredPicks = listOf(
                "Tristana", "Jinx", "Vayne", "Kaisa", "Aphelios", "Zeri",
                "Nami", "Soraka", "Lulu", "Karma", "Milio", "Seraphine", "Yuumi"
            ),
            minRequired = 2,
            keyChampions = listOf("Nami", "Lulu", "Milio"),
            bonusStrength = 9, tier = "A"
        ),
        TeamComposition(
            id = "objective_control", name = "Objective Control",
            description = "Tudo girando em torno de drakes e Baron. " +
                "Vence pelo controle de monstros do mapa, não lutando.",
            archetype = CompArchetype.CONTROL,
            requiredPicks = listOf(
                "Shyvana", "Belveth", "Hecarim",
                "Ashe", "Caitlyn", "Senna", "Bard",
                "Cassiopeia", "Azir"
            ),
            minRequired = 3,
            keyChampions = listOf("Shyvana", "Belveth", "Ashe"),
            bonusStrength = 9, tier = "A"
        ),

        // ═════════════════════════════════════════════════════════════════
        // TIER B — COMPOSIÇÕES SITUACIONAIS
        // ═════════════════════════════════════════════════════════════════
        TeamComposition(
            id = "double_adc", name = "Double ADC",
            description = "Dois carries físicos para devastar tanques. " +
                "Forte contra stacks de armadura.",
            archetype = CompArchetype.CONTROL,
            requiredPicks = listOf(
                "Lucian", "Senna", "Kalista", "Jinx", "MissFortune", "Draven",
                "Tristana", "Corki", "Vayne",
                "Camille", "Jax", "Fiora"
            ),
            minRequired = 3,
            keyChampions = listOf("Lucian", "Senna", "Kalista"),
            bonusStrength = 7, tier = "B"
        ),
        TeamComposition(
            id = "peel_comp", name = "Full Peel / Protect ADC",
            description = "Máximo de peeling para um ADC farmar e dominar o late. " +
                "Thresh + Janna + tank front.",
            archetype = CompArchetype.PEEL,
            requiredPicks = listOf(
                "Thresh", "Nautilus", "Lulu", "Alistar",
                "Jinx", "Vayne"
            ),
            minRequired = 3,
            keyChampions = listOf("Thresh", "Lulu", "Nautilus"),
            bonusStrength = 7, tier = "B"
        ),
        TeamComposition(
            id = "yone_yasuo", name = "Yone + Yasuo Knockup",
            description = "Knockout combo: qualquer knockup ativa o ult de Yasuo/Yone. " +
                "Terrível para receber, divertido de executar.",
            archetype = CompArchetype.WOMBO,
            requiredPicks = listOf(
                "Yasuo", "Yone",
                "Malphite", "Wukong", "Jarvaniv", "Alistar", "Sett", "Gnar",
                "Nautilus", "Azir", "Zac", "Diana", "Tristana"
            ),
            minRequired = 2,
            keyChampions = listOf("Yasuo", "Yone"),
            bonusStrength = 8, tier = "B"
        ),
        TeamComposition(
            id = "ap_burst", name = "AP Burst / Assassin Mid",
            description = "Dois assassinos AP que one-shotam carries. " +
                "Alta pressão de pick e roam.",
            archetype = CompArchetype.PICK,
            requiredPicks = listOf(
                "Zoe", "LeBlanc", "Fizz", "Ekko", "Katarina", "Akali", "Ahri", "Diana",
                "Annie", "Veigar", "Syndra", "Talon", "Evelynn"
            ),
            minRequired = 2,
            keyChampions = listOf("Zoe", "LeBlanc", "Katarina"),
            bonusStrength = 7, tier = "B"
        ),
        TeamComposition(
            id = "vision_control", name = "Vision Control / Siege",
            description = "Controle de visão superior e siege de torres. " +
                "Vence apertando o mapa sem precisar lutar.",
            archetype = CompArchetype.CONTROL,
            requiredPicks = listOf(
                "Caitlyn", "Jayce", "Jhin", "Karma", "Lux", "Senna", "Ashe",
                "Bard", "Zilean"
            ),
            minRequired = 3,
            keyChampions = listOf("Caitlyn", "Jhin", "Karma"),
            bonusStrength = 7, tier = "B"
        ),
        TeamComposition(
            id = "tank_stack", name = "Tank Stack",
            description = "4-5 tanks que não morrem. " +
                "Vence em chip damage e teamfight prolongado.",
            archetype = CompArchetype.ENGAGE,
            requiredPicks = listOf(
                "Ornn", "Malphite", "Sejuani", "Zac", "Amumu",
                "Galio", "Nautilus", "Leona", "Alistar",
                "Senna", "Veigar"
            ),
            minRequired = 4,
            keyChampions = listOf("Ornn", "Sejuani", "Malphite"),
            bonusStrength = 8, tier = "B"
        ),
        TeamComposition(
            id = "true_damage_shred", name = "True Damage / Anti-Tank",
            description = "Stack de dano real e %max HP. " +
                "Especificamente desenhado contra tank-comp adversária.",
            archetype = CompArchetype.CONTROL,
            requiredPicks = listOf(
                "Vayne", "Fiora", "Camille", "Darius", "Garen",
                "Senna", "Kaisa"
            ),
            minRequired = 3,
            keyChampions = listOf("Vayne", "Fiora", "Darius"),
            bonusStrength = 8, tier = "B"
        ),
        TeamComposition(
            id = "speed_comp", name = "Speed / Kite Comp",
            description = "Movement speed massivo. Pega lutas onde quer e " +
                "foge das que não quer.",
            archetype = CompArchetype.SKIRMISH,
            requiredPicks = listOf(
                "Hecarim", "Rakan", "Sivir", "Zeri", "Talon", "Ahri", "Ezreal"
            ),
            minRequired = 3,
            keyChampions = listOf("Hecarim", "Sivir", "Rakan"),
            bonusStrength = 8, tier = "B"
        ),
        TeamComposition(
            id = "all_in_dive_bot", name = "Bot Lane All-In 2v2",
            description = "Burst infinito na bot lane na level 2-3. " +
                "Snowball total se converter primeira luta.",
            archetype = CompArchetype.DIVE,
            requiredPicks = listOf(
                "Draven", "Lucian", "Samira", "Kalista", "Tristana", "Nilah",
                "Leona", "Nautilus", "Pyke", "Rakan", "Blitzcrank", "Thresh"
            ),
            minRequired = 2,
            keyChampions = listOf("Draven", "Lucian", "Leona"),
            bonusStrength = 8, tier = "B"
        ),
        TeamComposition(
            id = "flex_swap", name = "Flex Pick / Position Swap",
            description = "Picks que jogam em múltiplas roles, confundindo o ban-phase. " +
                "Karma top, Galio sup, Yasuo bot, etc.",
            archetype = CompArchetype.CONTROL,
            requiredPicks = listOf(
                "Karma", "Galio", "Sett", "Yasuo", "Lucian", "Senna",
                "Veigar", "Pyke", "Vladimir", "Gragas"
            ),
            minRequired = 3,
            keyChampions = listOf("Karma", "Sett", "Yasuo"),
            bonusStrength = 7, tier = "B"
        ),
        TeamComposition(
            id = "disengage_kite", name = "Disengage / Kite",
            description = "Stack de ferramentas de disengage. " +
                "Inimigo não consegue engajar; quem manda o ritmo é você.",
            archetype = CompArchetype.PEEL,
            requiredPicks = listOf(
                "Karma", "Gragas", "Anivia", "Lissandra", "Poppy",
                "Sivir", "Ezreal", "Lulu", "Bard"
            ),
            minRequired = 3,
            keyChampions = listOf("Karma", "Gragas", "Lissandra"),
            bonusStrength = 7, tier = "B"
        ),
        TeamComposition(
            id = "executioner", name = "Execute / Finisher",
            description = "Múltiplas habilidades de execução garantem que ninguém sai vivo. " +
                "Cleanup absoluto em teamfight.",
            archetype = CompArchetype.PICK,
            requiredPicks = listOf(
                "Darius", "Garen", "Pyke", "Urgot", "KhaZix", "Veigar", "Naafiri",
                "Talon", "Tristana", "Riven", "Samira"
            ),
            minRequired = 3,
            keyChampions = listOf("Darius", "Pyke", "Veigar"),
            bonusStrength = 7, tier = "B"
        ),
        TeamComposition(
            id = "sustain_battle", name = "Sustain Battle / Drain Tank",
            description = "Sustenta luta longa com lifesteal/heals massivos. " +
                "Vence pelo desgaste e não pelo burst.",
            archetype = CompArchetype.SCALING,
            requiredPicks = listOf(
                "Aatrox", "Mordekaiser", "Vladimir", "Sett", "Soraka", "Yuumi", "Senna"
            ),
            minRequired = 3,
            keyChampions = listOf("Aatrox", "Soraka", "Vladimir"),
            bonusStrength = 7, tier = "B"
        ),
        TeamComposition(
            id = "counter_engage", name = "Counter-Engage",
            description = "Espera o inimigo engajar e vira a luta com hard CC reativo. " +
                "Punisher de comps de engage.",
            archetype = CompArchetype.PEEL,
            requiredPicks = listOf(
                "Orianna", "Anivia", "Lissandra", "Gragas", "Lulu",
                "Sivir", "Sejuani", "Veigar"
            ),
            minRequired = 3,
            keyChampions = listOf("Orianna", "Lissandra", "Lulu"),
            bonusStrength = 8, tier = "B"
        ),
        TeamComposition(
            id = "invisible_pick", name = "Invisibility / Vision Denial",
            description = "Campeões invisíveis caçam sozinhos no mapa. " +
                "Inimigo precisa de control wards para não ser pickado.",
            archetype = CompArchetype.PICK,
            requiredPicks = listOf(
                "Evelynn", "Akali", "Talon", "KhaZix",
                "Pyke", "Senna", "Teemo", "Vayne", "Wukong"
            ),
            minRequired = 3,
            keyChampions = listOf("Evelynn", "Akali", "Talon"),
            bonusStrength = 7, tier = "B"
        )
    )
}
