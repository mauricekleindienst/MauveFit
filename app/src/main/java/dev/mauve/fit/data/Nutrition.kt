package dev.mauve.fit.data

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** Daily targets, meal-prep recipes, a shopping list and short tips, in German and English. */
object Nutrition {

    data class Targets(val kcal: Int, val protein: Int, val carbs: Int, val fat: Int, val waterMl: Int)

    /**
     * Rough daily targets from body weight and goal: maintenance about 33 kcal per kg, protein
     * 1.6 to 2.2 g per kg, fat 0.9 g per kg, the rest carbohydrates; 35 ml water per kg.
     */
    fun targets(kg: Double, goal: Goal): Targets {
        val maintenance = kg * 33
        val kcal = when (goal) {
            Goal.FatLoss -> maintenance - 450
            Goal.Muscle -> maintenance + 250
            Goal.Strength -> maintenance + 150
            Goal.Fitness -> maintenance
        }
        val protein = kg * when (goal) {
            Goal.FatLoss -> 2.2
            Goal.Muscle, Goal.Strength -> 2.0
            Goal.Fitness -> 1.6
        }
        val fat = kg * 0.9
        val carbs = ((kcal - protein * 4 - fat * 9) / 4).coerceAtLeast(kg * 1.5)
        return Targets(kcal.roundToInt(), protein.roundToInt(), carbs.roundToInt(), fat.roundToInt(), (kg * 35 / 250).roundToInt() * 250)
    }

    @Serializable
    data class Ingredient(val de: String, val en: String, val amount: Double, val unit: String) {
        val name get() = L(de, en)
    }

    data class Recipe(
        val id: String,
        val de: String,
        val en: String,
        val goals: Set<Goal>,
        val minutes: Int,
        val kcal: Int,
        val protein: Int,
        val carbs: Int,
        val fat: Int,
        val keepsDays: Int,
        /** Amounts are per portion. */
        val ingredients: List<Ingredient>,
        val stepsDe: List<String>,
        val stepsEn: List<String>,
    ) {
        val name get() = L(de, en)
        val steps get() = if (I18n.german) stepsDe else stepsEn
    }

    private fun i(de: String, en: String, amount: Double, unit: String) = Ingredient(de, en, amount, unit)

    private val all = setOf(Goal.Muscle, Goal.Strength, Goal.Fitness, Goal.FatLoss)
    private val build = setOf(Goal.Muscle, Goal.Strength)
    private val lean = setOf(Goal.FatLoss, Goal.Fitness)

    val recipes = listOf(
        Recipe(
            "chicken-rice", "Hähnchen, Reis und Brokkoli", "Chicken, rice and broccoli", all, 35, 560, 48, 62, 11, 4,
            listOf(i("Hähnchenbrust", "Chicken breast", 170.0, "g"), i("Basmatireis (roh)", "Basmati rice (dry)", 75.0, "g"),
                i("Brokkoli", "Broccoli", 150.0, "g"), i("Olivenöl", "Olive oil", 5.0, "ml"), i("Paprikapulver", "Paprika", 1.0, "TL")),
            listOf("Reis nach Packung kochen.", "Hähnchen würfeln, mit Paprika und Salz würzen, in Öl 8 Minuten braten.",
                "Brokkoli 5 Minuten dämpfen.", "Auf Boxen verteilen, abkühlen lassen, verschließen."),
            listOf("Cook the rice as the pack says.", "Dice the chicken, season with paprika and salt, fry in oil for 8 minutes.",
                "Steam the broccoli for 5 minutes.", "Split into boxes, let cool, close."),
        ),
        Recipe(
            "beef-bowl", "Rinderhack-Bowl mit Süßkartoffel", "Beef bowl with sweet potato", build, 40, 690, 45, 70, 24, 4,
            listOf(i("Rinderhack (5 % Fett)", "Lean ground beef (5 %)", 160.0, "g"), i("Süßkartoffel", "Sweet potato", 250.0, "g"),
                i("Kidneybohnen", "Kidney beans", 60.0, "g"), i("Mais", "Sweetcorn", 50.0, "g"), i("Tomaten (gehackt)", "Chopped tomatoes", 100.0, "g")),
            listOf("Süßkartoffel würfeln und bei 200 °C 25 Minuten backen.", "Hack krümelig braten, Tomaten, Bohnen und Mais dazugeben, 10 Minuten köcheln.",
                "Mit Kreuzkümmel, Salz und Pfeffer abschmecken.", "Mit der Süßkartoffel in Boxen füllen."),
            listOf("Dice the sweet potato and bake at 200 °C for 25 minutes.", "Brown the beef, add tomatoes, beans and corn, simmer for 10 minutes.",
                "Season with cumin, salt and pepper.", "Fill the boxes with the sweet potato."),
        ),
        Recipe(
            "overnight-oats", "Protein Overnight Oats", "Protein overnight oats", all, 5, 480, 38, 55, 11, 3,
            listOf(i("Haferflocken", "Rolled oats", 60.0, "g"), i("Magerquark", "Low-fat quark", 150.0, "g"), i("Milch", "Milk", 150.0, "ml"),
                i("Beeren (TK)", "Berries (frozen)", 100.0, "g"), i("Chiasamen", "Chia seeds", 10.0, "g")),
            listOf("Alles außer den Beeren in ein Glas geben und verrühren.", "Beeren darauf geben.", "Über Nacht in den Kühlschrank stellen."),
            listOf("Stir everything except the berries in a jar.", "Top with the berries.", "Refrigerate overnight."),
        ),
        Recipe(
            "turkey-chili", "Puten-Chili", "Turkey chili", lean, 45, 430, 42, 38, 9, 5,
            listOf(i("Putenhack", "Ground turkey", 150.0, "g"), i("Kidneybohnen", "Kidney beans", 80.0, "g"), i("Paprika", "Bell pepper", 100.0, "g"),
                i("Zwiebel", "Onion", 50.0, "g"), i("Tomaten (gehackt)", "Chopped tomatoes", 150.0, "g")),
            listOf("Zwiebel und Paprika würfeln und anbraten.", "Putenhack dazugeben und krümelig braten.",
                "Tomaten und Bohnen dazu, mit Chili, Kreuzkümmel und Salz 25 Minuten köcheln.", "Portionieren; hält sich auch eingefroren."),
            listOf("Dice and fry the onion and pepper.", "Add the turkey and brown it.",
                "Add tomatoes and beans, simmer with chili, cumin and salt for 25 minutes.", "Portion it; it also freezes well."),
        ),
        Recipe(
            "salmon-potato", "Lachs mit Ofenkartoffeln und Bohnen", "Salmon with roast potatoes and beans", all, 35, 590, 38, 48, 26, 3,
            listOf(i("Lachsfilet", "Salmon fillet", 140.0, "g"), i("Kartoffeln", "Potatoes", 250.0, "g"), i("Grüne Bohnen", "Green beans", 150.0, "g"),
                i("Olivenöl", "Olive oil", 5.0, "ml"), i("Zitrone", "Lemon", 0.25, "Stk")),
            listOf("Kartoffeln vierteln, mit Öl und Salz bei 210 °C 30 Minuten backen.", "Lachs die letzten 12 Minuten mit aufs Blech legen.",
                "Bohnen 6 Minuten kochen.", "Mit Zitrone beträufeln und portionieren."),
            listOf("Quarter the potatoes, roast with oil and salt at 210 °C for 30 minutes.", "Add the salmon for the last 12 minutes.",
                "Boil the beans for 6 minutes.", "Squeeze over the lemon and portion."),
        ),
        Recipe(
            "egg-muffins", "Eiermuffins mit Gemüse", "Veggie egg muffins", lean, 30, 290, 24, 6, 18, 4,
            listOf(i("Eier", "Eggs", 3.0, "Stk"), i("Spinat", "Spinach", 40.0, "g"), i("Paprika", "Bell pepper", 50.0, "g"),
                i("Feta", "Feta", 25.0, "g")),
            listOf("Ofen auf 180 °C vorheizen, Muffinform einfetten.", "Gemüse klein schneiden und in die Mulden verteilen.",
                "Eier verquirlen, würzen, darüber gießen, Feta darauf bröseln.", "20 Minuten backen."),
            listOf("Heat the oven to 180 °C and grease a muffin tin.", "Chop the vegetables and divide them between the cups.",
                "Whisk and season the eggs, pour over, crumble feta on top.", "Bake for 20 minutes."),
        ),
        Recipe(
            "tofu-curry", "Tofu-Curry mit Reis", "Tofu curry with rice", all, 30, 560, 28, 66, 19, 4,
            listOf(i("Tofu (fest)", "Firm tofu", 150.0, "g"), i("Basmatireis (roh)", "Basmati rice (dry)", 70.0, "g"),
                i("Kokosmilch (light)", "Light coconut milk", 100.0, "ml"), i("Kichererbsen", "Chickpeas", 60.0, "g"), i("Currypaste", "Curry paste", 15.0, "g")),
            listOf("Reis kochen.", "Tofu würfeln und knusprig anbraten.", "Currypaste kurz rösten, Kokosmilch und Kichererbsen dazu, 10 Minuten köcheln.",
                "Tofu unterheben und mit dem Reis portionieren."),
            listOf("Cook the rice.", "Cube the tofu and fry until crisp.", "Toast the curry paste, add coconut milk and chickpeas, simmer for 10 minutes.",
                "Fold in the tofu and portion with the rice."),
        ),
        Recipe(
            "pasta-bolo", "Protein-Pasta Bolognese", "Protein pasta bolognese", build, 30, 720, 52, 82, 16, 4,
            listOf(i("Vollkornnudeln (roh)", "Wholewheat pasta (dry)", 100.0, "g"), i("Rinderhack (5 % Fett)", "Lean ground beef (5 %)", 150.0, "g"),
                i("Passierte Tomaten", "Passata", 150.0, "g"), i("Karotte", "Carrot", 50.0, "g"), i("Parmesan", "Parmesan", 10.0, "g")),
            listOf("Nudeln al dente kochen.", "Hack mit geriebener Karotte anbraten.", "Passata dazugeben, würzen und 15 Minuten köcheln.",
                "Mit den Nudeln mischen, Parmesan darüber, portionieren."),
            listOf("Cook the pasta al dente.", "Brown the beef with grated carrot.", "Add the passata, season and simmer for 15 minutes.",
                "Toss with the pasta, add parmesan, portion."),
        ),
    )

    fun recipe(id: String) = recipes.firstOrNull { it.id == id }

    /** Recipes that fit the goal, the most protein per calorie first. */
    fun forGoal(goal: Goal) = recipes.filter { goal in it.goals }.sortedByDescending { it.protein.toDouble() / it.kcal }

    data class ShoppingItem(val name: String, val amount: Double, val unit: String)

    /** Everything needed for the chosen recipes and portions, merged by ingredient. */
    fun shoppingList(plan: Map<String, Int>): List<ShoppingItem> =
        plan.flatMap { (id, portions) -> recipe(id)?.ingredients.orEmpty().map { it to portions } }
            .groupBy { (ing, _) -> ing.name to ing.unit }
            .map { (key, list) -> ShoppingItem(key.first, list.sumOf { (ing, n) -> ing.amount * n }, key.second) }
            .sortedBy { it.name }

    fun fmtAmount(item: ShoppingItem): String {
        val unit = if (item.unit == "Stk") L("Stk", "pcs") else if (item.unit == "TL") L("TL", "tsp") else item.unit
        val v = item.amount
        val n = when {
            item.unit == "g" && v >= 1000 -> return "%.1f kg".format(v / 1000).replace('.', if (I18n.german) ',' else '.')
            item.unit == "ml" && v >= 1000 -> return "%.1f l".format(v / 1000).replace('.', if (I18n.german) ',' else '.')
            v % 1.0 == 0.0 -> v.toInt().toString()
            else -> "%.1f".format(v).replace('.', if (I18n.german) ',' else '.')
        }
        return "$n $unit"
    }

    /** A short tip; rotates by day so it changes but stays put during a day. */
    fun tip(goal: Goal, dayOfYear: Int): String {
        val tips = listOf(
            L("Koche am Sonntag einmal groß: zwei Rezepte, je vier Portionen, und die Woche ist zu zwei Dritteln versorgt.",
                "Cook once on Sunday: two recipes, four portions each, and two thirds of the week is covered."),
            L("Protein über den Tag verteilen: 3 bis 4 Mahlzeiten mit je 30 bis 50 g bauen mehr Muskeln auf als eine große.",
                "Spread protein over the day: 3 to 4 meals with 30 to 50 g each build more muscle than one big one."),
            L("Gekochter Reis und Hähnchen halten im Kühlschrank 4 Tage, eingefroren 3 Monate. Für Freitag lieber einfrieren.",
                "Cooked rice and chicken keep 4 days in the fridge, 3 months frozen. Freeze the Friday portion."),
            L("Nach dem Training zählt die Tagesmenge mehr als das Timing. Eine Mahlzeit in den 2 Stunden danach reicht völlig.",
                "After training, the daily total matters more than timing. A meal within 2 hours afterwards is plenty."),
            L("Gemüse zum Aufwärmen erst nach 3 Minuten dazugeben, dann bleibt es knackig.",
                "Add vegetables after 3 minutes when reheating so they stay crisp."),
            L("Trink morgens als Erstes ein großes Glas Wasser. Schon 2 % Flüssigkeitsverlust kosten messbar Kraft.",
                "Drink a big glass of water first thing. Losing just 2 % of fluid measurably costs strength."),
            L("Magerquark, Skyr und Hüttenkäse sind die günstigsten Proteinquellen im Supermarkt.",
                "Low-fat quark, skyr and cottage cheese are the cheapest protein in the supermarket."),
        ) + if (goal == Goal.FatLoss) listOf(
            L("Iss zuerst Gemüse und Protein, dann die Beilage. Das sättigt länger bei gleichen Kalorien.",
                "Eat vegetables and protein first, then the sides. It keeps you full longer for the same calories."),
        ) else listOf(
            L("Zum Zunehmen helfen kalorienreiche Extras: Nüsse, Olivenöl oder Haferflocken im Shake.",
                "To gain, calorie-dense extras help: nuts, olive oil or oats in a shake."),
        )
        return tips[dayOfYear % tips.size]
    }
}
