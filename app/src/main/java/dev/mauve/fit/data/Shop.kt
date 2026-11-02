package dev.mauve.fit.data

/**
 * Coins, skills and collections, in the spirit of an RPG: training earns coins, each muscle
 * group levels up as a skill, each exercise fills a collection in tiers, and coins buy things in
 * the shop. Earned coins come from the history (edits change them); spent coins are booked.
 */
object Shop {

    // --- Skills: a level per muscle group ------------------------------------------------

    data class Skill(val name: String, val level: Int, val into: Int, val need: Int, val sets: Int) {
        val fraction get() = (into.toFloat() / need).coerceIn(0f, 1f)
    }

    /** Sets from one level to the next: 10 at first, 5 more each level, so level 10 is about 300 sets. */
    private fun skillNeed(level: Int) = 5 + 5 * level

    fun skillLevel(sets: Int): Triple<Int, Int, Int> {
        var level = 0
        var left = sets
        while (left >= skillNeed(level + 1)) { left -= skillNeed(level + 1); level++ }
        return Triple(level, left, skillNeed(level + 1))
    }

    /** One skill per big muscle group, from the working sets done for it. */
    fun skills(d: AppData, targetOf: (String) -> String?): List<Skill> = Progress.groups.map { (name, muscles) ->
        val sets = d.history.sumOf { w -> w.entries.filter { targetOf(it.exerciseId) in muscles }.sumOf { e -> e.sets.count { it.done && !it.warmup } } }
        val (level, into, need) = skillLevel(sets)
        Skill(name, level, into, need, sets)
    }

    // --- Collections: tiers per exercise ----------------------------------------------------

    /** Working sets needed for tier I to X of an exercise's collection. */
    val TIERS = listOf(10, 25, 50, 100, 250, 500, 750, 1000, 1500, 2000)

    data class Collection(val exerciseId: String, val sets: Int, val tier: Int) {
        /** Sets for the next tier, or null at the top. */
        val next get() = TIERS.getOrNull(tier)
        val fraction get() = next?.let { n -> val from = TIERS.getOrElse(tier - 1) { 0 }; ((sets - from).toFloat() / (n - from)).coerceIn(0f, 1f) } ?: 1f
    }

    fun collections(d: AppData): List<Collection> =
        d.history.flatMap { w -> w.entries }.groupBy { it.exerciseId }
            .map { (id, es) -> val sets = es.sumOf { e -> e.sets.count { it.done && !it.warmup } }; Collection(id, sets, TIERS.count { sets >= it }) }
            .filter { it.sets > 0 }
            .sortedWith(compareByDescending<Collection> { it.tier }.thenByDescending { it.sets })

    fun roman(tier: Int) = listOf("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X").getOrElse(tier) { "$tier" }

    // --- Coins --------------------------------------------------------------------------------

    /** Coins earned in all: training, records, challenges, collection tiers, skill levels, quests. */
    fun earned(d: AppData, targetOf: (String) -> String?): Int =
        d.history.sumOf { w -> 20 + w.doneSets.size.coerceAtMost(30) } +
            Insights.prTimeline(d.history, Int.MAX_VALUE).size * 15 +
            d.challengesDone.size * 50 +
            collections(d).sumOf { c -> (1..c.tier).sum() * 25 } +
            skills(d, targetOf).sumOf { it.level * 10 } +
            Progress.achievements(d).filter { it.unlocked }.sumOf(Progress::coins) +
            d.questCoins

    fun balance(d: AppData, targetOf: (String) -> String?): Int = earned(d, targetOf) - d.coinsSpent

    // --- The shop -----------------------------------------------------------------------------

    enum class Kind { Freeze, Reroll, Boost, Accent, Title }

    data class Item(
        val key: String, val kind: Kind, val title: String, val detail: String, val price: Int,
        val rarity: Rarity = Rarity.Common, val level: Int = 1,
    )

    /** Premium accent keys, sold in the shop; the plain ones are free. */
    val premiumAccents = listOf("gold", "ruby", "emerald", "ocean", "rose")

    /** Rarity, as in an RPG: sets the frame colour of an item and orders the shop. */
    enum class Rarity(val de: String, val en: String) { Common("Gewöhnlich", "Common"), Rare("Selten", "Rare"), Epic("Episch", "Epic"), Legendary("Legendär", "Legendary"), Mythic("Mythisch", "Mythic") }

    data class TitleDef(val key: String, val de: String, val en: String, val price: Int, val level: Int, val rarity: Rarity)

    /** Titles: the better ones need a level first, so there is always one more to work towards. */
    val titles = listOf(
        TitleDef("rookie", "Frischling", "Fresh blood", 150, 1, Rarity.Common),
        TitleDef("iron", "Eisenbieger", "Iron bender", 250, 3, Rarity.Common),
        TitleDef("rat", "Gym-Ratte", "Gym rat", 300, 5, Rarity.Common),
        TitleDef("bar", "Hantelheld", "Barbell hero", 450, 8, Rarity.Rare),
        TitleDef("grinder", "Satzmaschine", "Set grinder", 600, 10, Rarity.Rare),
        TitleDef("beast", "Biest", "Beast", 800, 14, Rarity.Rare),
        TitleDef("monk", "Mönch der Disziplin", "Monk of discipline", 1000, 18, Rarity.Epic),
        TitleDef("spartan", "Spartaner", "Spartan", 1300, 22, Rarity.Epic),
        TitleDef("forge", "Schmied des Körpers", "Forger of steel", 1600, 26, Rarity.Epic),
        TitleDef("titan", "Titan", "Titan", 2000, 30, Rarity.Legendary),
        TitleDef("colossus", "Koloss", "Colossus", 2500, 36, Rarity.Legendary),
        TitleDef("warlord", "Kriegsherr", "Warlord", 3000, 42, Rarity.Legendary),
        TitleDef("legend", "Lebende Legende", "Living legend", 3600, 50, Rarity.Mythic),
        TitleDef("olymp", "Bewohner des Olymp", "Of Olympus", 4200, 60, Rarity.Mythic),
        TitleDef("god", "Halbgott", "Demigod", 4600, 68, Rarity.Mythic),
        TitleDef("immortal", "Unsterblich", "Immortal", 5000, 75, Rarity.Mythic),
    )

    fun titleLabel(key: String): String? = titles.firstOrNull { it.key == key }?.let { L(it.de, it.en) }

    const val FREEZE_MAX_BOUGHT = 3
    const val BOOST_HOURS = 24

    fun items(): List<Item> = listOf(
        Item("reroll", Kind.Reroll, L("Quests neu würfeln", "Reroll quests"), L("Drei neue Tagesquests", "Three new daily quests"), 40),
        Item("boost", Kind.Boost, L("XP-Booster", "XP booster"), L("Doppelte Quest-XP für 24 Stunden", "Double quest XP for 24 hours"), 150, Rarity.Rare),
        Item("freeze", Kind.Freeze, L("Joker", "Streak freeze"), L("Rettet die Serie in einer verpassten Woche", "Saves your streak in a missed week"), 300, Rarity.Epic),
    ) + premiumAccents.mapIndexed { i, k ->
        Item("accent:$k", Kind.Accent, L("Farbe ${accentName(k).first}", "${accentName(k).second} colour"), L("Neue Akzentfarbe für die App", "A new accent colour for the app"),
            400 + i * 150, if (i < 2) Rarity.Rare else Rarity.Epic, level = 2 + i * 4)
    } + titles.map { t ->
        Item("title:${t.key}", Kind.Title, L("Titel „${t.de}“", "Title \"${t.en}\""), L("Steht neben deinem Level", "Shown next to your level"), t.price, t.rarity, t.level)
    }

    fun accentName(key: String) = when (key) {
        "gold" -> "Gold" to "Gold"
        "ruby" -> "Rubin" to "Ruby"
        "emerald" -> "Smaragd" to "Emerald"
        "ocean" -> "Ozean" to "Ocean"
        else -> "Rosé" to "Rose"
    }

    /** Whether [item] can be bought now (one-off things only once, the freeze up to a cap). */
    /** The level an item asks for is reached (titles and colours unlock step by step). */
    fun unlocked(d: AppData, item: Item) = Quests.level(Quests.xp(d)).level >= item.level

    fun available(d: AppData, item: Item): Boolean = unlocked(d, item) && when (item.kind) {
        Kind.Freeze -> d.freezes < FREEZE_MAX_BOUGHT
        Kind.Accent, Kind.Title -> item.key !in d.owned
        Kind.Boost -> System.currentTimeMillis() >= d.boostUntil
        Kind.Reroll -> true
    }

    /** Applies a purchase; returns the data unchanged when it cannot be bought. */
    fun buy(d: AppData, item: Item, targetOf: (String) -> String?, today: java.time.LocalDate = java.time.LocalDate.now()): AppData {
        if (!available(d, item) || balance(d, targetOf) < item.price) return d
        val paid = d.copy(coinsSpent = d.coinsSpent + item.price)
        return when (item.kind) {
            Kind.Freeze -> paid.copy(freezes = paid.freezes + 1)
            Kind.Reroll -> {
                val n = if (d.rerollDay == today.toString()) d.rerollCount + 1 else 1
                paid.copy(rerollDay = today.toString(), rerollCount = n)
            }
            Kind.Boost -> paid.copy(boostUntil = System.currentTimeMillis() + BOOST_HOURS * 3_600_000L)
            Kind.Accent, Kind.Title -> paid.copy(owned = paid.owned + item.key)
        }
    }
}
