package com.cblol.scout.util

import com.cblol.scout.data.Champion
import com.cblol.scout.data.ChampionTag
import com.cblol.scout.data.ChampionTag.*

/**
 * Repositório de campeões com características completas para o pick & ban.
 *
 * Tags são ordenadas por importância para o draft (as primeiras 3 são exibidas no card).
 * Baseado nas classes oficiais da Riot (wiki.leagueoflegends.com/en-us/Champion_classes)
 * e no meta competitivo 2026.
 */
object ChampionRepository {

    private val champions: List<Champion> by lazy { buildList() }

    fun getAll(): List<Champion> = champions
    fun getById(id: String): Champion? = champions.firstOrNull { it.id == id }
    fun getByRole(role: String): List<Champion> = champions.filter { role in it.roles }
    fun getByTag(tag: ChampionTag): List<Champion> = champions.filter { it.hasTag(tag) }

    private fun champ(
        id: String,
        vararg roles: String,
        tags: List<ChampionTag>
    ): Champion {
        val short = if (id.length > 8) id.take(7) + "…" else id
        return Champion(
            id          = id,
            name        = id,
            shortName   = short,
            roles       = roles.toList(),
            primaryRole = roles.first(),
            tags        = tags
        )
    }

    private fun buildList(): List<Champion> = listOf(

        // ═══════════════════════════════════════════════════════
        // TOP
        // ═══════════════════════════════════════════════════════

        champ("Aatrox", "TOP", tags = listOf(
            FIGHTER, SUSTAINED_DPS, HEAL, DIVE, CROWD_CONTROL, LATE_GAME, TEAMFIGHT
        )),
        champ("Camille", "TOP", tags = listOf(
            FIGHTER, ASSASSIN, DIVE, CROWD_CONTROL, ANTI_TANK, PHYSICAL_DAMAGE, DUELIST, MOBILITY
        )),
        champ("Darius", "TOP", tags = listOf(
            FIGHTER, TRUE_DAMAGE, EXECUTE, SUSTAINED_DPS, ANTI_TANK, CROWD_CONTROL, DUELIST
        )),
        champ("Fiora", "TOP", tags = listOf(
            FIGHTER, DUELIST, ANTI_TANK, TRUE_DAMAGE, SPLIT_PUSH, PHYSICAL_DAMAGE, SUSTAIN, MOBILITY
        )),
        champ("Gangplank", "TOP", tags = listOf(
            SPECIALIST, PHYSICAL_DAMAGE, POKE, SPLIT_PUSH, GAME_CHANGING_ULT, WAVE_CLEAR, LATE_GAME
        )),
        champ("Garen", "TOP", tags = listOf(
            FIGHTER, TANK, EXECUTE, TRUE_DAMAGE, SUSTAINED_DPS, DUELIST
        )),
        champ("Gnar", "TOP", tags = listOf(
            FIGHTER, CROWD_CONTROL, ENGAGE, POKE, TEAMFIGHT, PHYSICAL_DAMAGE
        )),
        champ("Gragas", "TOP", "JNG", tags = listOf(
            FIGHTER, MAGE, ENGAGE, DISENGAGE, CROWD_CONTROL, MAGIC_DAMAGE, TEAMFIGHT, SUSTAIN
        )),
        champ("Irelia", "TOP", "MID", tags = listOf(
            FIGHTER, PHYSICAL_DAMAGE, MOBILITY, CROWD_CONTROL, ANTI_TANK, DUELIST, DIVE
        )),
        champ("Jax", "TOP", "JNG", tags = listOf(
            FIGHTER, PHYSICAL_DAMAGE, DUELIST, ANTI_TANK, LATE_GAME, SPLIT_PUSH, SUSTAINED_DPS
        )),
        champ("Jayce", "TOP", "MID", tags = listOf(
            FIGHTER, PHYSICAL_DAMAGE, POKE, WAVE_CLEAR, ANTI_TANK, EARLY_GAME
        )),
        champ("Kennen", "TOP", tags = listOf(
            MAGE, MAGIC_DAMAGE, CROWD_CONTROL, ENGAGE, TEAMFIGHT, ZONE_CONTROL
        )),
        champ("Malphite", "TOP", tags = listOf(
            TANK, MAGIC_DAMAGE, ENGAGE, KNOCK_UP, CROWD_CONTROL, GAME_CHANGING_ULT, TEAMFIGHT, ANTI_TANK
        )),
        champ("Mordekaiser", "TOP", tags = listOf(
            FIGHTER, MAGIC_DAMAGE, SUSTAINED_DPS, LATE_GAME, DUELIST, HEAL, TEAMFIGHT
        )),
        champ("Ornn", "TOP", tags = listOf(
            TANK, CROWD_CONTROL, ENGAGE, KNOCK_UP, GAME_CHANGING_ULT, TEAMFIGHT, EARLY_GAME
        )),
        champ("Poppy", "TOP", "JNG", tags = listOf(
            TANK, FIGHTER, PHYSICAL_DAMAGE, CROWD_CONTROL, DISENGAGE, ANTI_TANK
        )),
        champ("Renekton", "TOP", tags = listOf(
            FIGHTER, PHYSICAL_DAMAGE, CROWD_CONTROL, EARLY_GAME, DIVE, DUELIST, SUSTAINED_DPS
        )),
        champ("Riven", "TOP", tags = listOf(
            FIGHTER, PHYSICAL_DAMAGE, MOBILITY, SHIELD, CROWD_CONTROL, EXECUTE, BURST, DUELIST
        )),
        champ("Sett", "TOP", tags = listOf(
            FIGHTER, PHYSICAL_DAMAGE, ENGAGE, CROWD_CONTROL, TRUE_DAMAGE, TEAMFIGHT, SUSTAINED_DPS
        )),
        champ("Teemo", "TOP", tags = listOf(
            SPECIALIST, MAGIC_DAMAGE, POKE, INVISIBLE, VISION, ZONE_CONTROL, SLOW
        )),
        champ("Urgot", "TOP", tags = listOf(
            FIGHTER, PHYSICAL_DAMAGE, EXECUTE, ANTI_TANK, CROWD_CONTROL, SUSTAINED_DPS, POKE
        )),
        champ("Vladimir", "TOP", "MID", tags = listOf(
            MAGE, MAGIC_DAMAGE, SUSTAINED_DPS, SUSTAIN, TEAMFIGHT, LATE_GAME, UNTARGETABLE
        )),

        // ═══════════════════════════════════════════════════════
        // JUNGLE
        // ═══════════════════════════════════════════════════════

        champ("Belveth", "JNG", tags = listOf(
            FIGHTER, PHYSICAL_DAMAGE, SUSTAINED_DPS, ANTI_TANK, MOBILITY, OBJECTIVE_CONTROL
        )),
        champ("Diana", "JNG", "MID", tags = listOf(
            FIGHTER, MAGIC_DAMAGE, BURST, DIVE, ENGAGE, CROWD_CONTROL, TEAMFIGHT
        )),
        champ("Ekko", "JNG", "MID", tags = listOf(
            ASSASSIN, MAGE, MAGIC_DAMAGE, BURST, MOBILITY, TEAMFIGHT, GAME_CHANGING_ULT, REVIVE
        )),
        champ("Elise", "JNG", tags = listOf(
            MAGE, MAGIC_DAMAGE, BURST, CROWD_CONTROL, EARLY_GAME, ROAMER
        )),
        champ("Evelynn", "JNG", tags = listOf(
            ASSASSIN, MAGIC_DAMAGE, BURST, INVISIBLE, EXECUTE, MOBILITY, TEAMFIGHT
        )),
        champ("Graves", "JNG", tags = listOf(
            FIGHTER, PHYSICAL_DAMAGE, BURST, SUSTAINED_DPS, EARLY_GAME, SKIRMISHER
        )),
        champ("Hecarim", "JNG", tags = listOf(
            FIGHTER, PHYSICAL_DAMAGE, ENGAGE, CROWD_CONTROL, MOBILITY, TEAMFIGHT, GAP_CLOSER
        )),
        champ("Jarvaniv", "JNG", tags = listOf(
            FIGHTER, PHYSICAL_DAMAGE, ENGAGE, CROWD_CONTROL, ZONE_CONTROL, TEAMFIGHT, KNOCK_UP
        )),
        champ("Kayn", "JNG", tags = listOf(
            ASSASSIN, FIGHTER, PHYSICAL_DAMAGE, MOBILITY, BURST, SUSTAINED_DPS, UNTARGETABLE, DIVE
        )),
        champ("KhaZix", "JNG", tags = listOf(
            ASSASSIN, PHYSICAL_DAMAGE, BURST, EXECUTE, INVISIBLE, MOBILITY, EARLY_GAME
        )),
        champ("LeeSin", "JNG", tags = listOf(
            FIGHTER, PHYSICAL_DAMAGE, MOBILITY, CROWD_CONTROL, EARLY_GAME, DIVE, GAME_CHANGING_ULT
        )),
        champ("Lillia", "JNG", tags = listOf(
            MAGE, MAGIC_DAMAGE, CROWD_CONTROL, SUSTAINED_DPS, MOBILITY, TEAMFIGHT, SLOW
        )),
        champ("Nidalee", "JNG", tags = listOf(
            MAGE, MAGIC_DAMAGE, POKE, HEAL, MOBILITY, EARLY_GAME, ROAMER
        )),
        champ("Nocturne", "JNG", tags = listOf(
            ASSASSIN, PHYSICAL_DAMAGE, BURST, INVISIBLE, GLOBAL_ULT, CROWD_CONTROL, DIVE
        )),
        champ("RekSai", "JNG", tags = listOf(
            FIGHTER, PHYSICAL_DAMAGE, CROWD_CONTROL, EARLY_GAME, ANTI_TANK, DIVE, SUSTAINED_DPS
        )),
        champ("Sejuani", "JNG", tags = listOf(
            TANK, CROWD_CONTROL, ENGAGE, SLOW, TEAMFIGHT, GAME_CHANGING_ULT
        )),
        champ("Shyvana", "JNG", tags = listOf(
            FIGHTER, MAGIC_DAMAGE, PHYSICAL_DAMAGE, SUSTAINED_DPS, OBJECTIVE_CONTROL, ANTI_TANK
        )),
        champ("Vi", "JNG", tags = listOf(
            FIGHTER, PHYSICAL_DAMAGE, ENGAGE, CROWD_CONTROL, DIVE, ANTI_TANK, GAME_CHANGING_ULT
        )),
        champ("Viego", "JNG", tags = listOf(
            ASSASSIN, FIGHTER, PHYSICAL_DAMAGE, SUSTAIN, INVISIBLE, MOBILITY, DIVE
        )),
        champ("Wukong", "JNG", "TOP", tags = listOf(
            FIGHTER, PHYSICAL_DAMAGE, ENGAGE, CROWD_CONTROL, KNOCK_UP, TEAMFIGHT, INVISIBLE
        )),
        champ("Xinzhao", "JNG", tags = listOf(
            FIGHTER, PHYSICAL_DAMAGE, CROWD_CONTROL, EARLY_GAME, DIVE, ENGAGE, SUSTAINED_DPS
        )),
        champ("Zac", "JNG", tags = listOf(
            TANK, MAGIC_DAMAGE, ENGAGE, CROWD_CONTROL, TEAMFIGHT, KNOCK_UP, SUSTAIN
        )),

        // ═══════════════════════════════════════════════════════
        // MID
        // ═══════════════════════════════════════════════════════

        champ("Ahri", "MID", tags = listOf(
            MAGE, ASSASSIN, MAGIC_DAMAGE, BURST, MOBILITY, CROWD_CONTROL, ROAMER
        )),
        champ("Akali", "MID", "TOP", tags = listOf(
            ASSASSIN, MAGIC_DAMAGE, BURST, MOBILITY, INVISIBLE, DIVE, DUELIST
        )),
        champ("Anivia", "MID", tags = listOf(
            MAGE, MAGIC_DAMAGE, CROWD_CONTROL, ZONE_CONTROL, WAVE_CLEAR, LATE_GAME, REVIVE
        )),
        champ("Annie", "MID", "SUP", tags = listOf(
            MAGE, MAGIC_DAMAGE, BURST, CROWD_CONTROL, ENGAGE, TEAMFIGHT
        )),
        champ("Azir", "MID", tags = listOf(
            MAGE, MAGIC_DAMAGE, ZONE_CONTROL, POKE, DISENGAGE, WAVE_CLEAR, GAME_CHANGING_ULT, SUSTAINED_DPS
        )),
        champ("Cassiopeia", "MID", tags = listOf(
            MAGE, MAGIC_DAMAGE, SUSTAINED_DPS, CROWD_CONTROL, ZONE_CONTROL, LATE_GAME, SLOW
        )),
        champ("Corki", "MID", tags = listOf(
            MARKSMAN, PHYSICAL_DAMAGE, MAGIC_DAMAGE, POKE, WAVE_CLEAR, SUSTAINED_DPS, MOBILITY
        )),
        champ("Fizz", "MID", tags = listOf(
            ASSASSIN, MAGIC_DAMAGE, BURST, MOBILITY, UNTARGETABLE, GAME_CHANGING_ULT, CROWD_CONTROL
        )),
        champ("Galio", "MID", "SUP", tags = listOf(
            TANK, MAGIC_DAMAGE, ENGAGE, CROWD_CONTROL, GLOBAL_ULT, TEAMFIGHT, DISENGAGE, ANTI_TANK
        )),
        champ("Katarina", "MID", tags = listOf(
            ASSASSIN, MAGIC_DAMAGE, BURST, MOBILITY, SUSTAINED_DPS, TEAMFIGHT, EARLY_GAME
        )),
        champ("LeBlanc", "MID", tags = listOf(
            ASSASSIN, MAGE, MAGIC_DAMAGE, BURST, MOBILITY, CROWD_CONTROL, EARLY_GAME
        )),
        champ("Lissandra", "MID", "SUP", tags = listOf(
            MAGE, MAGIC_DAMAGE, CROWD_CONTROL, ENGAGE, TEAMFIGHT, REVIVE, ZONE_CONTROL
        )),
        champ("Lux", "MID", "SUP", tags = listOf(
            MAGE, MAGIC_DAMAGE, POKE, SHIELD, CROWD_CONTROL, BURST, WAVE_CLEAR
        )),
        champ("Malzahar", "MID", tags = listOf(
            MAGE, MAGIC_DAMAGE, SUPPRESSION, CROWD_CONTROL, WAVE_CLEAR, ZONE_CONTROL, SUSTAINED_DPS
        )),
        champ("Naafiri", "MID", tags = listOf(
            ASSASSIN, PHYSICAL_DAMAGE, BURST, MOBILITY, EXECUTE, EARLY_GAME
        )),
        champ("Orianna", "MID", tags = listOf(
            MAGE, MAGIC_DAMAGE, ZONE_CONTROL, CROWD_CONTROL, SHIELD, TEAMFIGHT, GAME_CHANGING_ULT, POKE
        )),
        champ("Ryze", "MID", tags = listOf(
            MAGE, MAGIC_DAMAGE, SUSTAINED_DPS, WAVE_CLEAR, LATE_GAME, CROWD_CONTROL, ANTI_TANK
        )),
        champ("Syndra", "MID", tags = listOf(
            MAGE, MAGIC_DAMAGE, BURST, CROWD_CONTROL, POKE, EXECUTE, EARLY_GAME
        )),
        champ("Taliyah", "MID", "JNG", tags = listOf(
            MAGE, MAGIC_DAMAGE, ZONE_CONTROL, WAVE_CLEAR, GLOBAL_ULT, CROWD_CONTROL, ROAMER
        )),
        champ("Talon", "MID", "JNG", tags = listOf(
            ASSASSIN, PHYSICAL_DAMAGE, BURST, MOBILITY, ROAMER, INVISIBLE, EXECUTE
        )),
        champ("TwistedFate", "MID", tags = listOf(
            MAGE, MAGIC_DAMAGE, CROWD_CONTROL, GLOBAL_ULT, ROAMER, WAVE_CLEAR, BURST
        )),
        champ("Veigar", "MID", "SUP", tags = listOf(
            MAGE, MAGIC_DAMAGE, BURST, EXECUTE, CROWD_CONTROL, ZONE_CONTROL, LATE_GAME, HYPERCARRY
        )),
        champ("Viktor", "MID", tags = listOf(
            MAGE, MAGIC_DAMAGE, SUSTAINED_DPS, ZONE_CONTROL, WAVE_CLEAR, LATE_GAME, CROWD_CONTROL
        )),
        champ("Xerath", "MID", "SUP", tags = listOf(
            MAGE, MAGIC_DAMAGE, POKE, BURST, CROWD_CONTROL, WAVE_CLEAR, ZONE_CONTROL
        )),
        champ("Yasuo", "MID", "TOP", tags = listOf(
            FIGHTER, PHYSICAL_DAMAGE, MOBILITY, DUELIST, KNOCK_UP, TEAMFIGHT, DISENGAGE, SUSTAINED_DPS
        )),
        champ("Yone", "MID", "TOP", tags = listOf(
            FIGHTER, PHYSICAL_DAMAGE, MAGIC_DAMAGE, MOBILITY, CROWD_CONTROL, TEAMFIGHT, SUSTAINED_DPS
        )),
        champ("Zed", "MID", tags = listOf(
            ASSASSIN, PHYSICAL_DAMAGE, BURST, MOBILITY, EXECUTE, UNTARGETABLE, EARLY_GAME
        )),
        champ("Zoe", "MID", tags = listOf(
            MAGE, MAGIC_DAMAGE, BURST, POKE, CROWD_CONTROL, EARLY_GAME
        )),

        // ═══════════════════════════════════════════════════════
        // ADC
        // ═══════════════════════════════════════════════════════

        champ("Aphelios", "ADC", tags = listOf(
            MARKSMAN, PHYSICAL_DAMAGE, SUSTAINED_DPS, POKE, ZONE_CONTROL, TEAMFIGHT, LATE_GAME
        )),
        champ("Ashe", "ADC", tags = listOf(
            MARKSMAN, PHYSICAL_DAMAGE, CROWD_CONTROL, SLOW, GLOBAL_ULT, VISION, OBJECTIVE_CONTROL
        )),
        champ("Caitlyn", "ADC", tags = listOf(
            MARKSMAN, PHYSICAL_DAMAGE, POKE, VISION, EARLY_GAME, ZONE_CONTROL, OBJECTIVE_CONTROL
        )),
        champ("Draven", "ADC", tags = listOf(
            MARKSMAN, PHYSICAL_DAMAGE, BURST, EARLY_GAME, SUSTAINED_DPS, CROWD_CONTROL, EXECUTE
        )),
        champ("Ezreal", "ADC", tags = listOf(
            MARKSMAN, PHYSICAL_DAMAGE, MAGIC_DAMAGE, POKE, MOBILITY, GLOBAL_ULT, SUSTAINED_DPS
        )),
        champ("Jhin", "ADC", tags = listOf(
            MARKSMAN, PHYSICAL_DAMAGE, BURST, POKE, CROWD_CONTROL, SLOW, TEAMFIGHT
        )),
        champ("Jinx", "ADC", tags = listOf(
            MARKSMAN, PHYSICAL_DAMAGE, SUSTAINED_DPS, WAVE_CLEAR, OBJECTIVE_CONTROL, HYPERCARRY, LATE_GAME
        )),
        champ("Kaisa", "ADC", tags = listOf(
            MARKSMAN, PHYSICAL_DAMAGE, MAGIC_DAMAGE, BURST, MOBILITY, DIVE, LATE_GAME, ANTI_TANK
        )),
        champ("Kalista", "ADC", tags = listOf(
            MARKSMAN, PHYSICAL_DAMAGE, SUSTAINED_DPS, MOBILITY, OBJECTIVE_CONTROL, ENGAGE, CROWD_CONTROL
        )),
        champ("Lucian", "ADC", "MID", tags = listOf(
            MARKSMAN, PHYSICAL_DAMAGE, BURST, MOBILITY, EARLY_GAME, SUSTAINED_DPS
        )),
        champ("MissFortune", "ADC", tags = listOf(
            MARKSMAN, PHYSICAL_DAMAGE, BURST, POKE, GAME_CHANGING_ULT, TEAMFIGHT, ZONE_CONTROL, SLOW
        )),
        champ("Nilah", "ADC", tags = listOf(
            FIGHTER, PHYSICAL_DAMAGE, DUELIST, CROWD_CONTROL, TEAMFIGHT, SUSTAINED_DPS, DISENGAGE
        )),
        champ("Samira", "ADC", tags = listOf(
            MARKSMAN, PHYSICAL_DAMAGE, BURST, SUSTAINED_DPS, TEAMFIGHT, MOBILITY, DISENGAGE
        )),
        champ("Sivir", "ADC", tags = listOf(
            MARKSMAN, PHYSICAL_DAMAGE, WAVE_CLEAR, SUSTAINED_DPS, DISENGAGE, TEAMFIGHT, OBJECTIVE_CONTROL
        )),
        champ("Tristana", "ADC", "MID", tags = listOf(
            MARKSMAN, PHYSICAL_DAMAGE, BURST, MOBILITY, EXECUTE, EARLY_GAME, SUSTAINED_DPS
        )),
        champ("Varus", "ADC", "MID", tags = listOf(
            MARKSMAN, PHYSICAL_DAMAGE, MAGIC_DAMAGE, POKE, CROWD_CONTROL, ANTI_TANK, SLOW
        )),
        champ("Vayne", "ADC", "TOP", tags = listOf(
            MARKSMAN, PHYSICAL_DAMAGE, TRUE_DAMAGE, ANTI_TANK, MOBILITY, INVISIBLE, DUELIST, HYPERCARRY, LATE_GAME
        )),
        champ("Xayah", "ADC", tags = listOf(
            MARKSMAN, PHYSICAL_DAMAGE, SUSTAINED_DPS, CROWD_CONTROL, UNTARGETABLE, TEAMFIGHT, ZONE_CONTROL
        )),
        champ("Zeri", "ADC", tags = listOf(
            MARKSMAN, PHYSICAL_DAMAGE, MOBILITY, SUSTAINED_DPS, SHIELD, TEAMFIGHT, HYPERCARRY, LATE_GAME
        )),

        // ═══════════════════════════════════════════════════════
        // SUPPORT
        // ═══════════════════════════════════════════════════════

        champ("Alistar", "SUP", tags = listOf(
            TANK, CROWD_CONTROL, ENGAGE, HEAL, TEAMFIGHT, KNOCK_UP, DISENGAGE
        )),
        champ("Bard", "SUP", tags = listOf(
            SUPPORT, SPECIALIST, CROWD_CONTROL, GLOBAL_ULT, HEAL, VISION, ROAMER, GAME_CHANGING_ULT
        )),
        champ("Blitzcrank", "SUP", tags = listOf(
            TANK, CROWD_CONTROL, ENGAGE, KNOCK_UP, SILENCE, EARLY_GAME, GAME_CHANGING_ULT
        )),
        champ("Brand", "SUP", "MID", tags = listOf(
            MAGE, MAGIC_DAMAGE, BURST, CROWD_CONTROL, TEAMFIGHT, ZONE_CONTROL, WAVE_CLEAR
        )),
        champ("Karma", "SUP", "MID", tags = listOf(
            ENCHANTER, MAGIC_DAMAGE, SHIELD, POKE, SLOW, DISENGAGE, CROWD_CONTROL, WAVE_CLEAR
        )),
        champ("Leona", "SUP", tags = listOf(
            TANK, CROWD_CONTROL, ENGAGE, TEAMFIGHT, EARLY_GAME, ZONE_CONTROL, GAME_CHANGING_ULT
        )),
        champ("Lulu", "SUP", tags = listOf(
            ENCHANTER, MAGIC_DAMAGE, SHIELD, HEAL, PROTECT_CARRY, CROWD_CONTROL, DISENGAGE, SLOW
        )),
        champ("Milio", "SUP", tags = listOf(
            ENCHANTER, SHIELD, HEAL, PROTECT_CARRY, DISENGAGE, CROWD_CONTROL
        )),
        champ("Nami", "SUP", tags = listOf(
            ENCHANTER, MAGIC_DAMAGE, HEAL, SHIELD, CROWD_CONTROL, SLOW, TEAMFIGHT, ENGAGE
        )),
        champ("Nautilus", "SUP", tags = listOf(
            TANK, CROWD_CONTROL, ENGAGE, KNOCK_UP, TEAMFIGHT, EARLY_GAME, DIVE, GAME_CHANGING_ULT
        )),
        champ("Pyke", "SUP", tags = listOf(
            ASSASSIN, PHYSICAL_DAMAGE, BURST, EXECUTE, CROWD_CONTROL, INVISIBLE, TRUE_DAMAGE, TEAMFIGHT
        )),
        champ("Rakan", "SUP", tags = listOf(
            SUPPORT, MAGIC_DAMAGE, ENGAGE, CROWD_CONTROL, SHIELD, HEAL, MOBILITY, TEAMFIGHT
        )),
        champ("Renata", "SUP", tags = listOf(
            SUPPORT, MAGIC_DAMAGE, SHIELD, HEAL, CROWD_CONTROL, GAME_CHANGING_ULT, TEAMFIGHT, PROTECT_CARRY
        )),
        champ("Senna", "SUP", "ADC", tags = listOf(
            MARKSMAN, PHYSICAL_DAMAGE, MAGIC_DAMAGE, HEAL, SHIELD, GLOBAL_ULT, CROWD_CONTROL, INVISIBLE
        )),
        champ("Seraphine", "SUP", "MID", tags = listOf(
            MAGE, MAGIC_DAMAGE, HEAL, SHIELD, CROWD_CONTROL, TEAMFIGHT, WAVE_CLEAR, ENCHANTER
        )),
        champ("Soraka", "SUP", tags = listOf(
            ENCHANTER, HEAL, SHIELD, GLOBAL_ULT, CROWD_CONTROL, SILENCE, PROTECT_CARRY, SUSTAIN
        )),
        champ("Thresh", "SUP", tags = listOf(
            SUPPORT, CROWD_CONTROL, ENGAGE, SHIELD, VISION, TEAMFIGHT, CROWD_CONTROL, GAME_CHANGING_ULT
        )),
        champ("Yuumi", "SUP", tags = listOf(
            ENCHANTER, HEAL, SHIELD, PROTECT_CARRY, CROWD_CONTROL, SUSTAIN, UNTARGETABLE
        )),
        champ("Zilean", "SUP", tags = listOf(
            SUPPORT, MAGIC_DAMAGE, REVIVE, CROWD_CONTROL, SLOW, PROTECT_CARRY, TEAMFIGHT
        )),
        champ("Zyra", "SUP", "MID", tags = listOf(
            MAGE, MAGIC_DAMAGE, POKE, CROWD_CONTROL, ZONE_CONTROL, TEAMFIGHT, WAVE_CLEAR
        ))
    )
}

