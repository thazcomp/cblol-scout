package com.cblol.scout.data.realm

import com.cblol.scout.data.Champion
import com.cblol.scout.data.ChampionTag
import com.cblol.scout.data.CompArchetype
import com.cblol.scout.data.Sponsor
import com.cblol.scout.data.SponsorCategory
import com.cblol.scout.data.SponsorTier
import com.cblol.scout.data.TeamComposition
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

/**
 * Entidades persistidas no banco **Realm criptografado** com os dados estáticos
 * do jogo. Tudo o que antes era hardcoded em objetos Kotlin
 * (`ChampionRepository`, `CompositionRepository`, `SponsorCatalog`,
 * `ChampionPoolRepository`) agora mora aqui.
 *
 * **Convenções de mapeamento Realm:**
 *  - O Realm Kotlin exige classes abertas com `var` e construtor sem argumentos.
 *  - Enums não são suportados nativamente → guardamos o `name` do enum como
 *    `String` e reconvertemos no mapeamento para o modelo de domínio.
 *  - Listas usam `RealmList<String>`.
 *
 * Cada entidade tem um `toDomain()` que a converte de volta para o data class
 * de domínio imutável usado pelo resto do app — assim a camada de UI/regras
 * nunca toca em tipos do Realm (mantém o acoplamento contido na camada `data`).
 *
 * Os campos derivados/computados do domínio (ex: `Champion.imageUrl`,
 * `Champion.shortName`) NÃO são persistidos: são recalculados no `toDomain()`
 * a partir dos campos-base, evitando duplicação e divergência.
 */

/** Entidade Realm de um campeão. */
class ChampionEntity : RealmObject {
    @PrimaryKey
    var id: String = ""
    var name: String = ""
    var primaryRole: String = ""
    var roles: RealmList<String> = realmListOf()
    /** Nomes dos enums [ChampionTag] (ex: "FIGHTER", "DIVE"). */
    var tags: RealmList<String> = realmListOf()
    var ddragonId: String = ""

    fun toDomain(): Champion {
        val short = if (id.length > 8) id.take(7) + "…" else id
        val parsedTags = tags.mapNotNull { runCatching { ChampionTag.valueOf(it) }.getOrNull() }
        return Champion(
            id          = id,
            name        = name,
            shortName   = short,
            roles       = roles.toList(),
            primaryRole = primaryRole,
            tags        = parsedTags,
            ddragonId   = ddragonId.ifBlank { id }
            // imageUrl/splashUrl usam os defaults derivados de ddragonId.
        )
    }

    companion object {
        /** Constrói a entidade Realm a partir do modelo de domínio (para seeding). */
        fun fromDomain(c: Champion): ChampionEntity = ChampionEntity().apply {
            id          = c.id
            name        = c.name
            primaryRole = c.primaryRole
            roles       = c.roles.toRealmList()
            tags        = c.tags.map { it.name }.toRealmList()
            ddragonId   = c.ddragonId
        }
    }
}

/** Entidade Realm de uma composição de time. */
class CompositionEntity : RealmObject {
    @PrimaryKey
    var id: String = ""
    var name: String = ""
    var description: String = ""
    /** Nome do enum [CompArchetype] (ex: "WOMBO"). */
    var archetype: String = CompArchetype.CONTROL.name
    var requiredPicks: RealmList<String> = realmListOf()
    var minRequired: Int = 3
    var keyChampions: RealmList<String> = realmListOf()
    var bonusStrength: Int = 0
    var tier: String = "A"

    fun toDomain(): TeamComposition = TeamComposition(
        id            = id,
        name          = name,
        description   = description,
        archetype     = runCatching { CompArchetype.valueOf(archetype) }
            .getOrDefault(CompArchetype.CONTROL),
        requiredPicks = requiredPicks.toList(),
        minRequired   = minRequired,
        keyChampions  = keyChampions.toList(),
        bonusStrength = bonusStrength,
        tier          = tier
    )

    companion object {
        fun fromDomain(c: TeamComposition): CompositionEntity = CompositionEntity().apply {
            id            = c.id
            name          = c.name
            description   = c.description
            archetype     = c.archetype.name
            requiredPicks = c.requiredPicks.toRealmList()
            minRequired   = c.minRequired
            keyChampions  = c.keyChampions.toRealmList()
            bonusStrength = c.bonusStrength
            tier          = c.tier
        }
    }
}

/** Entidade Realm de um patrocínio. */
class SponsorEntity : RealmObject {
    @PrimaryKey
    var id: String = ""
    var name: String = ""
    /** Nome do enum [SponsorTier]. */
    var tier: String = SponsorTier.BRONZE.name
    /** Nome do enum [SponsorCategory]. */
    var category: String = SponsorCategory.PERIPHERAL.name
    var weeklyAmount: Long = 0
    var durationWeeks: Int = 0
    var minReputation: Int = 0
    var minWinsThisSplit: Int = 0
    var bonusPerWin: Long = 0
    var penaltyPerLoss: Long = 0
    var description: String = ""

    fun toDomain(): Sponsor = Sponsor(
        id               = id,
        name             = name,
        tier             = runCatching { SponsorTier.valueOf(tier) }.getOrDefault(SponsorTier.BRONZE),
        category         = runCatching { SponsorCategory.valueOf(category) }
            .getOrDefault(SponsorCategory.PERIPHERAL),
        weeklyAmount     = weeklyAmount,
        durationWeeks    = durationWeeks,
        minReputation    = minReputation,
        minWinsThisSplit = minWinsThisSplit,
        bonusPerWin      = bonusPerWin,
        penaltyPerLoss   = penaltyPerLoss,
        description      = description
    )

    companion object {
        fun fromDomain(s: Sponsor): SponsorEntity = SponsorEntity().apply {
            id               = s.id
            name             = s.name
            tier             = s.tier.name
            category         = s.category.name
            weeklyAmount     = s.weeklyAmount
            durationWeeks    = s.durationWeeks
            minReputation    = s.minReputation
            minWinsThisSplit = s.minWinsThisSplit
            bonusPerWin      = s.bonusPerWin
            penaltyPerLoss   = s.penaltyPerLoss
            description      = s.description
        }
    }
}

/**
 * Entidade Realm de um champion pool.
 *
 * Cobre dois usos com a mesma forma:
 *  - **Signature pool** de um jogador histórico → [kind] = "SIGNATURE",
 *    [key] = id/nome do jogador em minúsculas.
 *  - **Role pool** (catálogo de mains comuns) → [kind] = "ROLE",
 *    [key] = role em maiúsculas (TOP/JNG/MID/ADC/SUP).
 *
 * A chave primária combina kind+key para evitar colisão entre um jogador
 * chamado "top" e a role "TOP", por exemplo.
 */
class ChampionPoolEntity : RealmObject {
    @PrimaryKey
    var compositeKey: String = ""   // "$kind:$key"
    var kind: String = ""           // SIGNATURE | ROLE
    var key: String = ""            // id/nome do jogador OU role
    var champions: RealmList<String> = realmListOf()

    companion object {
        const val KIND_SIGNATURE = "SIGNATURE"
        const val KIND_ROLE = "ROLE"

        fun signature(key: String, champions: List<String>): ChampionPoolEntity =
            ChampionPoolEntity().apply {
                kind = KIND_SIGNATURE
                this.key = key
                compositeKey = "$KIND_SIGNATURE:$key"
                this.champions = champions.toRealmList()
            }

        fun role(role: String, champions: List<String>): ChampionPoolEntity =
            ChampionPoolEntity().apply {
                kind = KIND_ROLE
                this.key = role
                compositeKey = "$KIND_ROLE:$role"
                this.champions = champions.toRealmList()
            }
    }
}
