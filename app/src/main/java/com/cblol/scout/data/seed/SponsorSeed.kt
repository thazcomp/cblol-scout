package com.cblol.scout.data.seed

import com.cblol.scout.data.Sponsor
import com.cblol.scout.data.SponsorCategory
import com.cblol.scout.data.SponsorTier

/**
 * **Dados de seed dos patrocínios.**
 *
 * Fonte hardcoded única usada apenas para popular o banco Realm na primeira
 * execução. Após o seed, o `SponsorCatalog` lê do Realm via
 * [com.cblol.scout.domain.datasource.StaticDataSource].
 *
 * Empresas fictícias inspiradas em categorias reais do esports brasileiro.
 * Convenção de valores por tier:
 *  - BRONZE:  R$ 50k-100k/sem — sem requisitos
 *  - SILVER:  R$ 150k-250k/sem — reputação 50+ ou 3 vitórias
 *  - GOLD:    R$ 350k-500k/sem — reputação 65+ e 6 vitórias
 *  - DIAMOND: R$ 700k-1M/sem — reputação 80+ e 10 vitórias
 */
object SponsorSeed {

    fun all(): List<Sponsor> = sponsors

    private val sponsors: List<Sponsor> = listOf(
        // ── BRONZE ──────────────────────────────────────────────────────
        Sponsor(
            id = "voltkick", name = "VoltKick Energy",
            tier = SponsorTier.BRONZE, category = SponsorCategory.ENERGY_DRINK,
            weeklyAmount = 80_000, durationWeeks = 6,
            description = "Energético de entrada — patrocinador da casa, sempre disposto."
        ),
        Sponsor(
            id = "pixelgear", name = "PixelGear Periféricos",
            tier = SponsorTier.BRONZE, category = SponsorCategory.PERIPHERAL,
            weeklyAmount = 60_000, durationWeeks = 8,
            description = "Mouse-pads, teclados e fones para o time."
        ),
        Sponsor(
            id = "burgerquest", name = "BurgerQuest Delivery",
            tier = SponsorTier.BRONZE, category = SponsorCategory.FOOD_DELIVERY,
            weeklyAmount = 50_000, durationWeeks = 4,
            description = "Delivery local oferecendo refeições para a gaming house."
        ),
        Sponsor(
            id = "lojavestir", name = "Vestir Esports Apparel",
            tier = SponsorTier.BRONZE, category = SponsorCategory.APPAREL,
            weeklyAmount = 70_000, durationWeeks = 10,
            description = "Confecção de uniformes oficiais e mercadoria do time."
        ),
        Sponsor(
            id = "fibranet", name = "FibraNet Telecom",
            tier = SponsorTier.BRONZE, category = SponsorCategory.TELECOM,
            weeklyAmount = 90_000, durationWeeks = 6,
            description = "Provedor de internet regional querendo presença no esports."
        ),

        // ── SILVER ──────────────────────────────────────────────────────
        Sponsor(
            id = "rapidhardware", name = "Rapid Hardware",
            tier = SponsorTier.SILVER, category = SponsorCategory.HARDWARE,
            weeklyAmount = 180_000, durationWeeks = 8,
            minReputation = 50,
            description = "Fornece PCs gamer high-end para os jogadores."
        ),
        Sponsor(
            id = "neoenergy", name = "NEO Energy Drink",
            tier = SponsorTier.SILVER, category = SponsorCategory.ENERGY_DRINK,
            weeklyAmount = 220_000, durationWeeks = 6,
            minWinsThisSplit = 3, bonusPerWin = 15_000,
            description = "Marca emergente que paga bônus por vitória — adoram visibilidade."
        ),
        Sponsor(
            id = "primebank", name = "PrimeBank Digital",
            tier = SponsorTier.SILVER, category = SponsorCategory.BANK,
            weeklyAmount = 200_000, durationWeeks = 12,
            minReputation = 50,
            description = "Banco digital expandindo presença no público jovem."
        ),
        Sponsor(
            id = "drivelite", name = "DriveLite Motors",
            tier = SponsorTier.SILVER, category = SponsorCategory.AUTOMOTIVE,
            weeklyAmount = 250_000, durationWeeks = 10,
            minWinsThisSplit = 4,
            description = "Concessionária de carros populares com campanha jovem."
        ),
        Sponsor(
            id = "ultrastream", name = "UltraStream",
            tier = SponsorTier.SILVER, category = SponsorCategory.STREAMING,
            weeklyAmount = 170_000, durationWeeks = 8,
            minReputation = 45,
            description = "Plataforma de streaming local querendo conteúdo dos jogadores."
        ),

        // ── GOLD ────────────────────────────────────────────────────────
        Sponsor(
            id = "blazedrink", name = "Blaze Performance",
            tier = SponsorTier.GOLD, category = SponsorCategory.ENERGY_DRINK,
            weeklyAmount = 450_000, durationWeeks = 10,
            minReputation = 65, minWinsThisSplit = 6, bonusPerWin = 30_000,
            description = "Líder de mercado em performance — premium, paga muito bem."
        ),
        Sponsor(
            id = "titaniumtech", name = "Titanium Tech Hardware",
            tier = SponsorTier.GOLD, category = SponsorCategory.HARDWARE,
            weeklyAmount = 500_000, durationWeeks = 12,
            minReputation = 65, minWinsThisSplit = 7,
            description = "Hardware enterprise — workstations e setup completo da gaming house."
        ),
        Sponsor(
            id = "novaperipheral", name = "Nova Peripherals Pro",
            tier = SponsorTier.GOLD, category = SponsorCategory.PERIPHERAL,
            weeklyAmount = 380_000, durationWeeks = 10,
            minReputation = 60, bonusPerWin = 20_000,
            description = "Linha pro de periféricos. Cada jogador recebe equipamento custom."
        ),
        Sponsor(
            id = "metropolitano", name = "Metropolitano Veículos",
            tier = SponsorTier.GOLD, category = SponsorCategory.AUTOMOTIVE,
            weeklyAmount = 420_000, durationWeeks = 14,
            minReputation = 70, minWinsThisSplit = 8,
            description = "Marca premium de carros — só patrocina times de elite."
        ),

        // ── DIAMOND ─────────────────────────────────────────────────────
        Sponsor(
            id = "lunarbank", name = "Lunar Bank",
            tier = SponsorTier.DIAMOND, category = SponsorCategory.BANK,
            weeklyAmount = 850_000, durationWeeks = 16,
            minReputation = 80, minWinsThisSplit = 10,
            description = "Banco internacional com presença global no esports. Contrato cobiçado."
        ),
        Sponsor(
            id = "phantomtelecom", name = "Phantom Telecom",
            tier = SponsorTier.DIAMOND, category = SponsorCategory.TELECOM,
            weeklyAmount = 900_000, durationWeeks = 14,
            minReputation = 82, minWinsThisSplit = 10, bonusPerWin = 50_000,
            description = "Maior operadora do país, paga gordo + bônus por vitória."
        ),
        Sponsor(
            id = "luckybet", name = "LuckyBet (Apostas)",
            tier = SponsorTier.DIAMOND, category = SponsorCategory.BETTING,
            weeklyAmount = 1_000_000, durationWeeks = 12,
            minReputation = 75, minWinsThisSplit = 8,
            bonusPerWin = 40_000, penaltyPerLoss = 60_000,
            description = "Casa de apostas. Paga muito, mas pune derrotas com dureza."
        ),
        Sponsor(
            id = "summitstream", name = "Summit Stream Network",
            tier = SponsorTier.DIAMOND, category = SponsorCategory.STREAMING,
            weeklyAmount = 720_000, durationWeeks = 18,
            minReputation = 78, minWinsThisSplit = 9,
            description = "Plataforma exclusiva de streaming. Quer presença mundial."
        )
    )
}
