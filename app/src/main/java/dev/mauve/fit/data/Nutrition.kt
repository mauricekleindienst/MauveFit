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
        Recipe(
            "greek-chicken-bowl", "Griechische Hähnchen-Bowl", "Greek chicken bowl", all, 30, 505, 45, 50, 14, 4,
            listOf(i("Hähnchenbrust", "Chicken breast", 170.0, "g"), i("Basmatireis (roh)", "Basmati rice (dry)", 60.0, "g"),
                i("Gurke", "Cucumber", 100.0, "g"), i("Tomaten", "Tomatoes", 100.0, "g"), i("Feta", "Feta", 30.0, "g"),
                i("Olivenöl", "Olive oil", 5.0, "ml")),
            listOf("Reis nach Packung kochen.", "Hähnchen würfeln, mit Oregano, Salz und Pfeffer würzen und in Olivenöl braten.",
                "Gurke und Tomaten würfeln.", "Alles in Boxen schichten und den Feta darüber bröseln."),
            listOf("Cook the rice as the pack says.", "Dice the chicken, season with oregano, salt and pepper and fry in olive oil.",
                "Dice the cucumber and tomatoes.", "Layer everything in boxes and crumble the feta on top."),
        ),
        Recipe(
            "teriyaki-salmon", "Teriyaki-Lachs mit Reis und Brokkoli", "Teriyaki salmon with rice and broccoli", all, 30, 580, 38, 58, 22, 3,
            listOf(i("Lachsfilet", "Salmon fillet", 140.0, "g"), i("Basmatireis (roh)", "Basmati rice (dry)", 70.0, "g"),
                i("Brokkoli", "Broccoli", 120.0, "g"), i("Sojasauce", "Soy sauce", 15.0, "ml"), i("Honig", "Honey", 1.0, "TL")),
            listOf("Sojasauce und Honig verrühren, den Lachs 10 Minuten darin marinieren.", "Reis kochen, Brokkoli 5 Minuten dämpfen.",
                "Lachs bei 200 °C 12 Minuten im Ofen garen.", "Auf Boxen verteilen und abkühlen lassen."),
            listOf("Mix soy sauce and honey and marinate the salmon in it for 10 minutes.", "Cook the rice, steam the broccoli for 5 minutes.",
                "Bake the salmon at 200 °C for 12 minutes.", "Divide into boxes and let cool."),
        ),
        Recipe(
            "shakshuka", "Shakshuka mit Feta", "Shakshuka with feta", lean, 30, 395, 26, 28, 20, 3,
            listOf(i("Eier", "Eggs", 3.0, "Stk"), i("Passierte Tomaten", "Passata", 200.0, "g"), i("Paprika", "Bell pepper", 100.0, "g"),
                i("Zwiebel", "Onion", 50.0, "g"), i("Feta", "Feta", 30.0, "g"), i("Paprikapulver", "Paprika", 1.0, "TL")),
            listOf("Zwiebel und Paprika würfeln und weich dünsten.", "Passata und Paprikapulver zugeben, 10 Minuten köcheln lassen.",
                "Mulden in die Sauce drücken, Eier hineingleiten lassen und zugedeckt 8 Minuten stocken lassen.", "Feta darüber bröseln."),
            listOf("Dice the onion and pepper and soften them.", "Add passata and paprika and simmer for 10 minutes.",
                "Make wells in the sauce, slide in the eggs and cook covered for 8 minutes until set.", "Crumble feta on top."),
        ),
        Recipe(
            "chickpea-salad", "Kichererbsen-Salat mit Gurke und Tomate", "Chickpea salad with cucumber and tomato", all, 15, 415, 20, 52, 14, 3,
            listOf(i("Kichererbsen", "Chickpeas", 150.0, "g"), i("Gurke", "Cucumber", 100.0, "g"), i("Tomaten", "Tomatoes", 100.0, "g"),
                i("Zwiebel", "Onion", 30.0, "g"), i("Olivenöl", "Olive oil", 10.0, "ml"), i("Zitrone", "Lemon", 0.5, "Stk")),
            listOf("Kichererbsen abspülen und abtropfen lassen.", "Gurke, Tomaten und Zwiebel klein würfeln.",
                "Aus Olivenöl, Zitronensaft, Salz und Pfeffer ein Dressing rühren.", "Alles mischen und durchziehen lassen."),
            listOf("Rinse and drain the chickpeas.", "Finely dice the cucumber, tomatoes and onion.",
                "Whisk olive oil, lemon juice, salt and pepper into a dressing.", "Toss everything together and let it rest."),
        ),
        Recipe(
            "protein-pancakes", "Protein-Pfannkuchen mit Beeren", "Protein pancakes with berries", all, 20, 440, 36, 52, 10, 2,
            listOf(i("Haferflocken", "Rolled oats", 60.0, "g"), i("Eier", "Eggs", 2.0, "Stk"), i("Magerquark", "Low-fat quark", 150.0, "g"),
                i("Banane", "Banana", 1.0, "Stk"), i("Beeren (TK)", "Berries (frozen)", 80.0, "g")),
            listOf("Haferflocken, Eier, Quark und Banane im Mixer glatt pürieren.", "In einer beschichteten Pfanne kleine Pfannkuchen backen, je 2 Minuten pro Seite.",
                "Beeren kurz auftauen.", "Pfannkuchen abkühlen lassen und mit den Beeren in Boxen packen."),
            listOf("Blend the oats, eggs, quark and banana until smooth.", "Cook small pancakes in a non-stick pan, 2 minutes per side.",
                "Thaw the berries briefly.", "Let the pancakes cool and pack them with the berries."),
        ),
        Recipe(
            "skyr-bowl", "Skyr-Bowl mit Walnüssen", "Skyr bowl with walnuts", lean, 5, 390, 35, 35, 12, 3,
            listOf(i("Skyr", "Skyr", 250.0, "g"), i("Haferflocken", "Rolled oats", 30.0, "g"), i("Beeren (TK)", "Berries (frozen)", 100.0, "g"),
                i("Walnüsse", "Walnuts", 15.0, "g"), i("Honig", "Honey", 1.0, "TL")),
            listOf("Skyr in ein Glas oder eine Schale geben.", "Haferflocken und aufgetaute Beeren darauf schichten.",
                "Walnüsse grob hacken und mit dem Honig darüber geben.", "Gut verschlossen kühlen; Nüsse erst kurz vor dem Essen zugeben, damit sie knackig bleiben."),
            listOf("Put the skyr in a jar or bowl.", "Layer the oats and thawed berries on top.",
                "Roughly chop the walnuts and add them with the honey.", "Keep closed and chilled; add the nuts just before eating to keep them crunchy."),
        ),
        Recipe(
            "tuna-pasta", "Thunfisch-Pasta mit Oliven", "Tuna pasta with olives", all, 20, 570, 47, 68, 12, 3,
            listOf(i("Vollkornnudeln (roh)", "Wholewheat pasta (dry)", 90.0, "g"), i("Thunfisch (Dose, im eigenen Saft)", "Tuna (canned, in water)", 150.0, "g"),
                i("Passierte Tomaten", "Passata", 150.0, "g"), i("Oliven", "Olives", 20.0, "g"), i("Olivenöl", "Olive oil", 5.0, "ml")),
            listOf("Nudeln al dente kochen.", "Passata mit Oliven und Knoblauch, Salz und Pfeffer 8 Minuten köcheln.",
                "Thunfisch abtropfen lassen und unterheben.", "Mit den Nudeln mischen und portionieren."),
            listOf("Cook the pasta al dente.", "Simmer the passata with olives, garlic, salt and pepper for 8 minutes.",
                "Drain the tuna and fold it in.", "Toss with the pasta and portion."),
        ),
        Recipe(
            "burrito-bowl", "Mexikanische Hähnchen-Burrito-Bowl", "Mexican chicken burrito bowl", all, 30, 590, 46, 70, 14, 4,
            listOf(i("Hähnchenbrust", "Chicken breast", 160.0, "g"), i("Basmatireis (roh)", "Basmati rice (dry)", 70.0, "g"),
                i("Kidneybohnen", "Kidney beans", 80.0, "g"), i("Mais", "Sweetcorn", 60.0, "g"), i("Paprika", "Bell pepper", 80.0, "g"),
                i("Olivenöl", "Olive oil", 5.0, "ml")),
            listOf("Reis kochen.", "Hähnchen in Streifen schneiden, mit Kreuzkümmel, Paprikapulver und Salz würzen und in Öl braten.",
                "Paprika würfeln, mit Bohnen und Mais kurz erhitzen.", "Alles in Boxen anrichten."),
            listOf("Cook the rice.", "Slice the chicken, season with cumin, paprika and salt and fry in oil.",
                "Dice the pepper and warm it with the beans and corn.", "Arrange everything in boxes."),
        ),
        Recipe(
            "red-lentil-dal", "Rotes Linsen-Dal", "Red lentil dal", all, 30, 450, 24, 62, 12, 4,
            listOf(i("Rote Linsen (roh)", "Red lentils (dry)", 90.0, "g"), i("Kokosmilch (light)", "Light coconut milk", 100.0, "ml"),
                i("Tomaten (gehackt)", "Chopped tomatoes", 100.0, "g"), i("Zwiebel", "Onion", 50.0, "g"), i("Currypulver", "Curry powder", 2.0, "TL")),
            listOf("Zwiebel würfeln und mit dem Currypulver in einem Topf anrösten.", "Linsen, Tomaten und 250 ml Wasser dazugeben und 15 Minuten köcheln.",
                "Kokosmilch einrühren und weitere 5 Minuten garen.", "Mit Salz abschmecken und portionieren."),
            listOf("Dice the onion and toast it with the curry powder in a pot.", "Add lentils, tomatoes and 250 ml water and simmer for 15 minutes.",
                "Stir in the coconut milk and cook 5 more minutes.", "Season with salt and portion."),
        ),
        Recipe(
            "turkey-meatballs", "Putenbällchen mit Zucchini-Reis", "Turkey meatballs with zucchini rice", lean, 35, 490, 46, 45, 14, 4,
            listOf(i("Putenhack", "Ground turkey", 170.0, "g"), i("Basmatireis (roh)", "Basmati rice (dry)", 50.0, "g"),
                i("Zucchini", "Zucchini", 150.0, "g"), i("Passierte Tomaten", "Passata", 100.0, "g"), i("Eier", "Eggs", 1.0, "Stk")),
            listOf("Putenhack mit Ei, Salz, Pfeffer und Kräutern verkneten und zu Bällchen formen.", "Bällchen bei 200 °C 15 Minuten backen.",
                "Reis kochen, Zucchini raspeln und kurz mit der Passata erhitzen.", "Alles mischen und portionieren."),
            listOf("Knead the turkey with egg, salt, pepper and herbs and shape into balls.", "Bake the balls at 200 °C for 15 minutes.",
                "Cook the rice, grate the zucchini and warm it briefly with the passata.", "Combine and portion."),
        ),
        Recipe(
            "hummus-veggie-box", "Hummus mit Gemüsesticks", "Hummus with veggie sticks", lean, 10, 285, 12, 28, 14, 4,
            listOf(i("Kichererbsen", "Chickpeas", 80.0, "g"), i("Tahini", "Tahini", 15.0, "g"), i("Zitrone", "Lemon", 0.25, "Stk"),
                i("Karotte", "Carrot", 100.0, "g"), i("Gurke", "Cucumber", 100.0, "g")),
            listOf("Kichererbsen mit Tahini, Zitronensaft, Salz und etwas Wasser cremig pürieren.", "Karotte und Gurke in Sticks schneiden.",
                "Hummus in ein kleines Gefäß füllen und die Sticks daneben packen."),
            listOf("Blend the chickpeas with tahini, lemon juice, salt and a little water until creamy.", "Cut the carrot and cucumber into sticks.",
                "Fill a small tub with hummus and pack the sticks beside it."),
        ),
        Recipe(
            "protein-balls", "Protein-Energy-Balls", "Protein energy balls", build, 15, 285, 14, 30, 12, 5,
            listOf(i("Haferflocken", "Rolled oats", 30.0, "g"), i("Erdnussbutter", "Peanut butter", 15.0, "g"), i("Magerquark", "Low-fat quark", 50.0, "g"),
                i("Honig", "Honey", 1.0, "TL"), i("Kakaopulver", "Cocoa powder", 1.0, "TL")),
            listOf("Alle Zutaten in einer Schüssel zu einer festen Masse verkneten.", "Mit feuchten Händen zu kleinen Kugeln formen.",
                "Mindestens 1 Stunde kalt stellen."),
            listOf("Knead all ingredients in a bowl into a firm dough.", "Shape into small balls with damp hands.",
                "Chill for at least 1 hour."),
        ),
        Recipe(
            "choc-quark", "Schoko-Quark-Dessert", "Chocolate quark dessert", lean, 5, 255, 32, 20, 5, 3,
            listOf(i("Magerquark", "Low-fat quark", 250.0, "g"), i("Kakaopulver", "Cocoa powder", 2.0, "TL"), i("Honig", "Honey", 1.0, "TL"),
                i("Beeren (TK)", "Berries (frozen)", 80.0, "g")),
            listOf("Quark mit Kakao und Honig glatt rühren.", "Beeren auftauen und darauf geben.", "Im Glas mindestens 30 Minuten kühlen."),
            listOf("Stir the quark with cocoa and honey until smooth.", "Thaw the berries and add them on top.", "Chill in a jar for at least 30 minutes."),
        ),
        Recipe(
            "quark-cheesecake", "Protein-Käsekuchen im Glas", "Protein cheesecake in a jar", all, 35, 320, 30, 32, 8, 4,
            listOf(i("Magerquark", "Low-fat quark", 200.0, "g"), i("Eier", "Eggs", 1.0, "Stk"), i("Haferflocken", "Rolled oats", 30.0, "g"),
                i("Beeren (TK)", "Berries (frozen)", 80.0, "g"), i("Honig", "Honey", 1.0, "TL")),
            listOf("Haferflocken fein mahlen und als Boden in Gläser drücken.", "Quark, Ei und Honig glatt rühren und darüber füllen.",
                "Bei 170 °C etwa 25 Minuten backen.", "Abkühlen lassen, mit Beeren belegen und kühlen."),
            listOf("Grind the oats finely and press into jars as a base.", "Stir quark, egg and honey smooth and pour over.",
                "Bake at 170 °C for about 25 minutes.", "Let cool, top with berries and chill."),
        ),
        Recipe(
            "lentil-stew", "Tellerlinsen-Eintopf", "German lentil stew", all, 40, 400, 24, 62, 6, 5,
            listOf(i("Tellerlinsen (roh)", "Brown lentils (dry)", 80.0, "g"), i("Karotte", "Carrot", 80.0, "g"), i("Kartoffeln", "Potatoes", 100.0, "g"),
                i("Zwiebel", "Onion", 40.0, "g"), i("Gemüsebrühe", "Vegetable stock", 300.0, "ml"), i("Olivenöl", "Olive oil", 5.0, "ml")),
            listOf("Zwiebel, Karotte und Kartoffeln würfeln.", "Zwiebel in Öl anbraten, Gemüse und Linsen zugeben.",
                "Mit der Brühe aufgießen und 25 Minuten köcheln.", "Mit Essig, Salz und Pfeffer abschmecken."),
            listOf("Dice the onion, carrot and potatoes.", "Fry the onion in oil, add vegetables and lentils.",
                "Pour in the stock and simmer for 25 minutes.", "Season with vinegar, salt and pepper."),
        ),
        Recipe(
            "chicken-goulash", "Hähnchen-Gulasch mit Kartoffeln", "Chicken goulash with potatoes", lean, 40, 450, 46, 40, 12, 4,
            listOf(i("Hähnchenbrust", "Chicken breast", 170.0, "g"), i("Paprika", "Bell pepper", 120.0, "g"), i("Zwiebel", "Onion", 50.0, "g"),
                i("Tomaten (gehackt)", "Chopped tomatoes", 150.0, "g"), i("Kartoffeln", "Potatoes", 200.0, "g"), i("Paprikapulver", "Paprika", 2.0, "TL")),
            listOf("Zwiebel würfeln und anbraten, Hähnchenwürfel dazugeben und bräunen.", "Paprikapulver, Tomaten und gewürfelte Paprika zugeben.",
                "Kartoffeln würfeln und mit etwas Wasser 25 Minuten mitgaren.", "Mit Salz und Pfeffer abschmecken und portionieren."),
            listOf("Dice and fry the onion, add the chicken cubes and brown.", "Add paprika, tomatoes and diced pepper.",
                "Dice the potatoes and cook them in with a little water for 25 minutes.", "Season with salt and pepper and portion."),
        ),
        Recipe(
            "quark-potatoes", "Pellkartoffeln mit Kräuterquark", "Jacket potatoes with herb quark", all, 30, 530, 37, 66, 13, 3,
            listOf(i("Kartoffeln", "Potatoes", 350.0, "g"), i("Magerquark", "Low-fat quark", 250.0, "g"), i("Leinöl", "Flaxseed oil", 10.0, "ml"),
                i("Gurke", "Cucumber", 80.0, "g")),
            listOf("Kartoffeln mit Schale in 20 bis 25 Minuten gar kochen.", "Quark mit Leinöl, Salz, Pfeffer und frischen Kräutern verrühren.",
                "Gurke in Scheiben schneiden.", "Kartoffeln abkühlen lassen und mit Quark und Gurke in Boxen packen."),
            listOf("Boil the potatoes in their skins for 20 to 25 minutes.", "Stir the quark with flaxseed oil, salt, pepper and fresh herbs.",
                "Slice the cucumber.", "Let the potatoes cool and pack them with the quark and cucumber."),
        ),
        Recipe(
            "shrimp-stirfry", "Garnelen-Gemüsepfanne mit Reis", "Shrimp and vegetable stir-fry with rice", lean, 25, 465, 38, 58, 9, 2,
            listOf(i("Garnelen", "Shrimp", 160.0, "g"), i("Basmatireis (roh)", "Basmati rice (dry)", 70.0, "g"), i("Paprika", "Bell pepper", 100.0, "g"),
                i("Brokkoli", "Broccoli", 100.0, "g"), i("Sojasauce", "Soy sauce", 15.0, "ml"), i("Sesamöl", "Sesame oil", 5.0, "ml")),
            listOf("Reis kochen.", "Gemüse in mundgerechte Stücke schneiden und im Sesamöl 5 Minuten braten.",
                "Garnelen dazugeben und 3 Minuten mitbraten.", "Mit Sojasauce abschmecken, mit dem Reis portionieren und zügig kühlen."),
            listOf("Cook the rice.", "Cut the vegetables into bite-sized pieces and fry in sesame oil for 5 minutes.",
                "Add the shrimp and fry for 3 more minutes.", "Season with soy sauce, portion with the rice and cool quickly."),
        ),
        Recipe(
            "tofu-scramble", "Tofu-Rührei mit Vollkornbrot", "Tofu scramble with wholegrain bread", all, 15, 405, 33, 37, 14, 3,
            listOf(i("Tofu (fest)", "Firm tofu", 200.0, "g"), i("Spinat", "Spinach", 50.0, "g"), i("Paprika", "Bell pepper", 80.0, "g"),
                i("Hefeflocken", "Nutritional yeast", 10.0, "g"), i("Vollkornbrot", "Wholegrain bread", 60.0, "g"), i("Kurkuma", "Turmeric", 1.0, "TL")),
            listOf("Tofu mit der Gabel grob zerdrücken.", "Paprika würfeln und 3 Minuten anbraten, Tofu und Kurkuma zugeben.",
                "Spinat und Hefeflocken unterrühren, bis der Spinat zusammenfällt.", "Mit Salz und Pfeffer würzen und mit dem Brot portionieren."),
            listOf("Crumble the tofu roughly with a fork.", "Dice the pepper and fry for 3 minutes, add tofu and turmeric.",
                "Stir in spinach and yeast flakes until the spinach wilts.", "Season with salt and pepper and portion with the bread."),
        ),
        Recipe(
            "beef-noodles", "Rindfleisch mit Reisnudeln und Gemüse", "Beef with rice noodles and vegetables", build, 25, 565, 42, 68, 14, 3,
            listOf(i("Rindfleisch (Streifen)", "Beef strips", 150.0, "g"), i("Reisnudeln (roh)", "Rice noodles (dry)", 80.0, "g"),
                i("Paprika", "Bell pepper", 100.0, "g"), i("Karotte", "Carrot", 60.0, "g"), i("Sojasauce", "Soy sauce", 15.0, "ml"),
                i("Sesamöl", "Sesame oil", 5.0, "ml")),
            listOf("Reisnudeln nach Packung einweichen und abgießen.", "Rindfleisch im Sesamöl scharf anbraten und herausnehmen.",
                "Paprika und Karotte in Streifen 4 Minuten braten.", "Fleisch, Nudeln und Sojasauce untermischen und portionieren."),
            listOf("Soak the rice noodles as the pack says and drain.", "Sear the beef in sesame oil and remove.",
                "Stir-fry strips of pepper and carrot for 4 minutes.", "Mix in the beef, noodles and soy sauce and portion."),
        ),
        Recipe(
            "turkey-wraps", "Puten-Wraps mit Hüttenkäse", "Turkey wraps with cottage cheese", all, 10, 475, 44, 52, 10, 3,
            listOf(i("Vollkorn-Wraps", "Wholegrain wraps", 2.0, "Stk"), i("Putenbrust (Aufschnitt)", "Turkey breast (sliced)", 120.0, "g"),
                i("Hüttenkäse", "Cottage cheese", 80.0, "g"), i("Salat", "Lettuce", 50.0, "g"), i("Tomaten", "Tomatoes", 80.0, "g")),
            listOf("Wraps ausbreiten und mit Hüttenkäse bestreichen.", "Putenbrust, Salat und Tomatenscheiben darauf legen.",
                "Fest einrollen und in Backpapier oder Folie wickeln.", "Gekühlt aufbewahren."),
            listOf("Lay out the wraps and spread with cottage cheese.", "Add turkey, lettuce and tomato slices.",
                "Roll up tightly and wrap in baking paper or foil.", "Keep chilled."),
        ),
        Recipe(
            "baked-cod", "Ofen-Kabeljau mit Gemüse-Couscous", "Baked cod with vegetable couscous", lean, 25, 465, 44, 52, 9, 2,
            listOf(i("Kabeljaufilet", "Cod fillet", 200.0, "g"), i("Couscous (roh)", "Couscous (dry)", 65.0, "g"), i("Zucchini", "Zucchini", 100.0, "g"),
                i("Tomaten", "Tomatoes", 100.0, "g"), i("Olivenöl", "Olive oil", 5.0, "ml"), i("Zitrone", "Lemon", 0.25, "Stk")),
            listOf("Kabeljau mit Salz, Pfeffer und Zitronensaft würzen und bei 190 °C 15 Minuten backen.", "Couscous mit heißem Wasser übergießen und quellen lassen.",
                "Zucchini und Tomaten würfeln und in Olivenöl 4 Minuten braten.", "Alles mischen, portionieren und schnell abkühlen lassen."),
            listOf("Season the cod with salt, pepper and lemon juice and bake at 190 °C for 15 minutes.", "Pour hot water over the couscous and let it swell.",
                "Dice the zucchini and tomatoes and fry in olive oil for 4 minutes.", "Combine, portion and cool quickly."),
        ),
        Recipe(
            "salmon-quinoa", "Lachs-Bowl mit Quinoa und Edamame", "Salmon bowl with quinoa and edamame", build, 30, 555, 40, 43, 25, 3,
            listOf(i("Lachsfilet", "Salmon fillet", 120.0, "g"), i("Quinoa (roh)", "Quinoa (dry)", 60.0, "g"), i("Edamame (TK)", "Edamame (frozen)", 60.0, "g"),
                i("Avocado", "Avocado", 0.25, "Stk"), i("Gurke", "Cucumber", 80.0, "g")),
            listOf("Quinoa gründlich abspülen und 12 Minuten kochen.", "Lachs bei 200 °C 12 Minuten backen und in Stücke zupfen.",
                "Edamame 3 Minuten in heißem Wasser garen.", "Alles mit Gurke und Avocado in Boxen anrichten; Avocado erst am Verzehrtag schneiden."),
            listOf("Rinse the quinoa well and cook for 12 minutes.", "Bake the salmon at 200 °C for 12 minutes and flake it.",
                "Cook the edamame in hot water for 3 minutes.", "Arrange with cucumber and avocado in boxes; cut the avocado on the day you eat it."),
        ),
        Recipe(
            "sweet-potato-curry", "Süßkartoffel-Kichererbsen-Curry", "Sweet potato and chickpea curry", all, 35, 510, 20, 80, 12, 4,
            listOf(i("Süßkartoffel", "Sweet potato", 250.0, "g"), i("Kichererbsen", "Chickpeas", 100.0, "g"),
                i("Kokosmilch (light)", "Light coconut milk", 100.0, "ml"), i("Spinat", "Spinach", 60.0, "g"), i("Currypaste", "Curry paste", 15.0, "g")),
            listOf("Süßkartoffel würfeln und in etwas Wasser 10 Minuten vorgaren.", "Currypaste kurz anrösten, Kokosmilch und Kichererbsen zugeben.",
                "Süßkartoffel unterrühren und 10 Minuten köcheln.", "Spinat unterheben, bis er zusammenfällt, und portionieren."),
            listOf("Dice the sweet potato and pre-cook in a little water for 10 minutes.", "Toast the curry paste, add coconut milk and chickpeas.",
                "Stir in the sweet potato and simmer for 10 minutes.", "Fold in the spinach until wilted and portion."),
        ),
        Recipe(
            "quinoa-bean-pan", "Quinoa-Bohnen-Pfanne", "Quinoa and bean skillet", all, 25, 510, 25, 80, 10, 4,
            listOf(i("Quinoa (roh)", "Quinoa (dry)", 70.0, "g"), i("Kidneybohnen", "Kidney beans", 100.0, "g"), i("Mais", "Sweetcorn", 50.0, "g"),
                i("Paprika", "Bell pepper", 100.0, "g"), i("Olivenöl", "Olive oil", 5.0, "ml"), i("Limette", "Lime", 0.5, "Stk")),
            listOf("Quinoa abspülen und 12 Minuten kochen.", "Paprika würfeln und in Olivenöl anbraten.",
                "Bohnen, Mais und Quinoa zugeben, mit Kreuzkümmel, Salz und Pfeffer würzen.", "Mit Limettensaft abschmecken und portionieren."),
            listOf("Rinse the quinoa and cook for 12 minutes.", "Dice the pepper and fry in olive oil.",
                "Add beans, corn and quinoa and season with cumin, salt and pepper.", "Finish with lime juice and portion."),
        ),
        Recipe(
            "stuffed-peppers", "Gefüllte Paprika mit Hack und Reis", "Stuffed peppers with beef and rice", all, 45, 480, 38, 59, 10, 4,
            listOf(i("Paprika", "Bell pepper", 250.0, "g"), i("Rinderhack (5 % Fett)", "Lean ground beef (5 %)", 150.0, "g"),
                i("Basmatireis (roh)", "Basmati rice (dry)", 50.0, "g"), i("Passierte Tomaten", "Passata", 100.0, "g"), i("Zwiebel", "Onion", 40.0, "g")),
            listOf("Reis kochen, Paprika halbieren und entkernen.", "Hack mit gewürfelter Zwiebel anbraten, mit Reis und der Hälfte der Passata mischen.",
                "Paprikahälften füllen, restliche Passata darüber geben.", "Bei 190 °C 25 Minuten backen."),
            listOf("Cook the rice, halve and deseed the peppers.", "Brown the beef with diced onion, mix with rice and half the passata.",
                "Fill the pepper halves and spoon the remaining passata over.", "Bake at 190 °C for 25 minutes."),
        ),
        Recipe(
            "peanut-chicken", "Thai-Hähnchen mit Erdnusssauce", "Thai chicken with peanut sauce", build, 30, 560, 46, 60, 15, 3,
            listOf(i("Hähnchenbrust", "Chicken breast", 160.0, "g"), i("Reisnudeln (roh)", "Rice noodles (dry)", 70.0, "g"),
                i("Erdnussbutter", "Peanut butter", 20.0, "g"), i("Sojasauce", "Soy sauce", 15.0, "ml"), i("Paprika", "Bell pepper", 80.0, "g")),
            listOf("Reisnudeln nach Packung zubereiten.", "Hähnchen in Streifen braten, Paprika dazugeben.",
                "Erdnussbutter, Sojasauce und etwas heißes Wasser zu einer Sauce verrühren.", "Alles mischen und portionieren."),
            listOf("Prepare the rice noodles as the pack says.", "Fry the chicken strips, add the pepper.",
                "Whisk peanut butter, soy sauce and a little hot water into a sauce.", "Toss everything together and portion."),
        ),
        Recipe(
            "chicken-tikka", "Hähnchen Tikka Masala mit Reis", "Chicken tikka masala with rice", all, 40, 550, 48, 62, 12, 4,
            listOf(i("Hähnchenbrust", "Chicken breast", 170.0, "g"), i("Basmatireis (roh)", "Basmati rice (dry)", 70.0, "g"),
                i("Griechischer Joghurt (0,2 %)", "Greek yogurt (0.2 %)", 80.0, "g"), i("Passierte Tomaten", "Passata", 150.0, "g"),
                i("Zwiebel", "Onion", 40.0, "g"), i("Garam Masala", "Garam masala", 2.0, "TL")),
            listOf("Hähnchenwürfel mit Joghurt und der Hälfte des Garam Masala 15 Minuten marinieren.", "Zwiebel würfeln, anbraten und das Hähnchen dazugeben.",
                "Passata und restliches Gewürz zugeben und 15 Minuten köcheln.", "Reis kochen und mit der Sauce portionieren."),
            listOf("Marinate the diced chicken with yogurt and half the garam masala for 15 minutes.", "Dice and fry the onion, then add the chicken.",
                "Add passata and the remaining spice and simmer for 15 minutes.", "Cook the rice and portion with the sauce."),
        ),
        Recipe(
            "egg-edamame-box", "Eier-Edamame-Snackbox", "Egg and edamame snack box", lean, 15, 285, 24, 14, 15, 4,
            listOf(i("Eier", "Eggs", 2.0, "Stk"), i("Edamame (TK)", "Edamame (frozen)", 100.0, "g"), i("Tomaten", "Tomatoes", 100.0, "g"),
                i("Paprika", "Bell pepper", 50.0, "g")),
            listOf("Eier 9 Minuten hart kochen, abschrecken und pellen.", "Edamame 3 Minuten in Salzwasser garen und abgießen.",
                "Tomaten und Paprika schneiden.", "Alles in einer Box anrichten und mit Salz und Pfeffer würzen."),
            listOf("Hard-boil the eggs for 9 minutes, rinse cold and peel.", "Cook the edamame in salted water for 3 minutes and drain.",
                "Cut the tomatoes and pepper.", "Arrange in a box and season with salt and pepper."),
        ),
        Recipe(
            "oat-bars", "Haferriegel mit Banane und Walnüssen", "Banana walnut oat bars", build, 30, 450, 20, 58, 15, 5,
            listOf(i("Haferflocken", "Rolled oats", 50.0, "g"), i("Banane", "Banana", 1.0, "Stk"), i("Eier", "Eggs", 1.0, "Stk"),
                i("Magerquark", "Low-fat quark", 50.0, "g"), i("Walnüsse", "Walnuts", 10.0, "g")),
            listOf("Banane zerdrücken und mit Ei und Quark verrühren.", "Haferflocken und gehackte Walnüsse unterheben.",
                "Masse in eine Form streichen und bei 180 °C 20 Minuten backen.", "Abkühlen lassen und in Riegel schneiden."),
            listOf("Mash the banana and stir in the egg and quark.", "Fold in the oats and chopped walnuts.",
                "Spread in a tin and bake at 180 °C for 20 minutes.", "Let cool and cut into bars."),
        ),
        Recipe(
            "peanut-tofu-noodles", "Erdnuss-Tofu-Nudeln", "Peanut tofu noodles", all, 25, 520, 28, 62, 18, 3,
            listOf(i("Tofu (fest)", "Firm tofu", 150.0, "g"), i("Reisnudeln (roh)", "Rice noodles (dry)", 70.0, "g"), i("Erdnussbutter", "Peanut butter", 15.0, "g"),
                i("Sojasauce", "Soy sauce", 15.0, "ml"), i("Karotte", "Carrot", 60.0, "g")),
            listOf("Tofu würfeln und in einer Pfanne knusprig braten.", "Reisnudeln nach Packung zubereiten, Karotte raspeln.",
                "Erdnussbutter mit Sojasauce und heißem Wasser glatt rühren.", "Alles vermengen und portionieren."),
            listOf("Cube the tofu and fry until crisp.", "Prepare the rice noodles as the pack says and grate the carrot.",
                "Whisk peanut butter with soy sauce and hot water until smooth.", "Toss everything together and portion."),
        ),
        Recipe(
            "apple-porridge", "Apfel-Zimt-Porridge mit Quark", "Apple cinnamon porridge with quark", all, 10, 440, 30, 66, 6, 3,
            listOf(i("Haferflocken", "Rolled oats", 50.0, "g"), i("Milch", "Milk", 150.0, "ml"), i("Magerquark", "Low-fat quark", 150.0, "g"),
                i("Apfel", "Apple", 1.0, "Stk"), i("Zimt", "Cinnamon", 1.0, "TL")),
            listOf("Haferflocken mit der Milch und Zimt 3 Minuten aufkochen.", "Apfel würfeln und unterrühren.",
                "Etwas abkühlen lassen, dann den Quark unterheben.", "In Gläser füllen und kühlen; vor dem Essen kurz erwärmen."),
            listOf("Bring the oats to the boil with the milk and cinnamon for 3 minutes.", "Dice the apple and stir it in.",
                "Let cool slightly, then fold in the quark.", "Fill jars and chill; warm briefly before eating."),
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
