package dev.mauve.fit.data

/** Starter plans shown on first launch; users edit or delete them like their own. */
object Templates {
    private fun plan(id: String, sets: Int = 3, reps: Int = 10, rest: Int = 90) = PlanExercise(id, sets, reps, rest)

    val routines get() = listOf(
        Routine(
            id = "tpl-push", name = "Push", note = L("Brust · Schultern · Trizeps", "Chest · Shoulders · Triceps"),
            exercises = listOf(
                plan("0025", 4, 8, 150),  // barbell bench press
                plan("0314", 3, 10, 120), // dumbbell incline bench press
                plan("0405", 3, 10, 120), // dumbbell seated shoulder press
                plan("0334", 3, 15, 60),  // dumbbell lateral raise
                plan("0201", 3, 12, 60),  // cable pushdown
            ),
        ),
        Routine(
            id = "tpl-pull", name = "Pull", note = L("Rücken · Bizeps", "Back · Biceps"),
            exercises = listOf(
                plan("0652", 4, 8, 150),  // pull-up
                plan("0027", 4, 8, 120),  // barbell bent over row
                plan("0861", 3, 12, 90),  // cable seated row
                plan("0203", 3, 15, 60),  // cable rear delt row (rope)
                plan("0313", 3, 12, 60),  // dumbbell hammer curl
            ),
        ),
        Routine(
            id = "tpl-legs", name = L("Beine", "Legs"), note = L("Quadrizeps · Beinbeuger · Waden", "Quads · Hamstrings · Calves"),
            exercises = listOf(
                plan("0043", 4, 8, 180),  // barbell full squat
                plan("0085", 3, 10, 120), // barbell romanian deadlift
                plan("1463", 3, 12, 120), // sled 45° leg press
                plan("0586", 3, 12, 60),  // lever lying leg curl
                plan("0585", 3, 12, 60),  // lever leg extension
                plan("0605", 4, 15, 60),  // lever standing calf raise
            ),
        ),
        Routine(
            id = "tpl-full", name = L("Ganzkörper", "Full body"), note = L("Einsteiger · 3× pro Woche", "Beginner · 3× a week"),
            exercises = listOf(
                plan("1760", 3, 10, 90),  // dumbbell goblet squat
                plan("0662", 3, 12, 60),  // push-up
                plan("0861", 3, 12, 90),  // cable seated row
                plan("0405", 3, 10, 90),  // dumbbell seated shoulder press
                plan("0549", 3, 15, 60),  // kettlebell swing
            ),
        ),
        Routine(
            id = "tpl-home", name = L("Zuhause", "Home"), note = L("Ohne Geräte · 25 Minuten", "No equipment · 25 minutes"),
            exercises = listOf(
                plan("0662", 3, 15, 45),  // push-up
                plan("0514", 3, 15, 45),  // jump squat
                plan("1460", 3, 12, 45),  // walking lunge
                plan("3013", 3, 15, 45),  // low glute bridge on floor
                plan("0276", 3, 12, 45),  // dead bug
                plan("0630", 3, 20, 45),  // mountain climber
                plan("1160", 3, 10, 60),  // burpee
            ),
        ),
    )

    // --- Template packs ---------------------------------------------------------------

    /** A ready-made split the user can add from the plans screen; [routines] builds fresh copies. */
    class Pack(val key: String, val name: String, val blurb: String, val routines: () -> List<Routine>)

    /** Same as [plan], but trained as a superset with the neighbours that share [group]. */
    private fun pair(id: String, group: String, sets: Int = 3, reps: Int = 12, rest: Int = 75) = PlanExercise(id, sets, reps, rest, group)

    val packs get() = listOf(
        Pack("upper-lower", L("Oberkörper / Unterkörper", "Upper / Lower"), L("4× pro Woche · Studio", "4× a week · Gym")) {
            listOf(
                Routine(
                    name = L("Oberkörper A", "Upper A"), note = L("Brust · Rücken · Schultern · Arme", "Chest · Back · Shoulders · Arms"),
                    exercises = listOf(
                        plan("0025", 4, 6, 150),  // barbell bench press
                        plan("0027", 4, 8, 120),  // barbell bent over row
                        plan("0405", 3, 10, 90),  // dumbbell seated shoulder press
                        plan("0198", 3, 10, 90),  // cable pulldown
                        pair("0031", "arms"),     // barbell curl
                        pair("0201", "arms"),     // cable pushdown
                    ),
                ),
                Routine(
                    name = L("Unterkörper A", "Lower A"), note = L("Quadrizeps · Beinbeuger · Waden", "Quads · Hamstrings · Calves"),
                    exercises = listOf(
                        plan("0043", 4, 6, 180),  // barbell full squat
                        plan("0085", 3, 8, 120),  // barbell romanian deadlift
                        plan("0585", 3, 12, 60),  // lever leg extension
                        plan("0586", 3, 12, 60),  // lever lying leg curl
                        plan("0605", 4, 15, 60),  // lever standing calf raise
                    ),
                ),
                Routine(
                    name = L("Oberkörper B", "Upper B"), note = L("Schräg · Klimmzüge · Arme", "Incline · Pull-ups · Arms"),
                    exercises = listOf(
                        plan("0314", 4, 8, 120),  // dumbbell incline bench press
                        plan("0652", 4, 8, 120),  // pull-up
                        plan("0861", 3, 12, 90),  // cable seated row
                        pair("0334", "shoulders", reps = 15, rest = 60), // dumbbell lateral raise
                        pair("0203", "shoulders", reps = 15, rest = 60), // cable rear delt row (rope)
                        pair("0313", "arms"),     // dumbbell hammer curl
                        pair("0194", "arms"),     // cable overhead triceps extension (rope)
                    ),
                ),
                Routine(
                    name = L("Unterkörper B", "Lower B"), note = L("Kreuzheben · Beinpresse · Bauch", "Deadlift · Leg press · Core"),
                    exercises = listOf(
                        plan("0032", 3, 5, 180),  // barbell deadlift
                        plan("0739", 3, 10, 120), // sled 45° leg press
                        plan("0336", 3, 10, 90),  // dumbbell lunge
                        plan("0599", 3, 12, 60),  // lever seated leg curl
                        pair("0594", "finish", reps = 15, rest = 60), // lever seated calf raise
                        pair("0175", "finish", reps = 15, rest = 60), // cable kneeling crunch
                    ),
                ),
            )
        },
        Pack("ppl", "Push / Pull / " + L("Beine", "Legs"), L("3 oder 6× pro Woche · Studio", "3 or 6× a week · Gym")) {
            listOf(
                Routine(
                    name = "Push", note = L("Brust · Schultern · Trizeps", "Chest · Shoulders · Triceps"),
                    exercises = listOf(
                        plan("0025", 4, 8, 150),  // barbell bench press
                        plan("0314", 3, 10, 120), // dumbbell incline bench press
                        plan("0405", 3, 10, 120), // dumbbell seated shoulder press
                        pair("0334", "finish", reps = 15, rest = 60), // dumbbell lateral raise
                        pair("0201", "finish", rest = 60),            // cable pushdown
                    ),
                ),
                Routine(
                    name = "Pull", note = L("Rücken · Bizeps", "Back · Biceps"),
                    exercises = listOf(
                        plan("0652", 4, 8, 150),  // pull-up
                        plan("0027", 4, 8, 120),  // barbell bent over row
                        plan("0861", 3, 12, 90),  // cable seated row
                        pair("0203", "finish", reps = 15, rest = 60), // cable rear delt row (rope)
                        pair("0313", "finish", rest = 60),            // dumbbell hammer curl
                    ),
                ),
                Routine(
                    name = L("Beine", "Legs"), note = L("Quadrizeps · Beinbeuger · Waden", "Quads · Hamstrings · Calves"),
                    exercises = listOf(
                        plan("0043", 4, 8, 180),  // barbell full squat
                        plan("0085", 3, 10, 120), // barbell romanian deadlift
                        plan("0739", 3, 12, 120), // sled 45° leg press
                        pair("0586", "legs", rest = 60), // lever lying leg curl
                        pair("0585", "legs", rest = 60), // lever leg extension
                        plan("0605", 4, 15, 60),  // lever standing calf raise
                    ),
                ),
            )
        },
        Pack("home", L("Zuhause ohne Geräte", "Home, no equipment"), L("Nur Körpergewicht · 2 Einheiten", "Body weight only · 2 sessions")) {
            listOf(
                Routine(
                    name = L("Zuhause A", "Home A"), note = L("Ohne Geräte · Ganzkörper", "No equipment · Full body"),
                    exercises = listOf(
                        pair("0662", "a", reps = 12, rest = 60), // push-up
                        pair("2368", "a", reps = 10, rest = 60), // split squats
                        plan("3166", 3, 12, 60),  // bodyweight standing row
                        plan("3013", 3, 15, 45),  // low glute bridge on floor
                        plan("3662", 3, 8, 60),   // pike-to-cobra push-up
                        plan("0276", 3, 12, 45),  // dead bug
                    ),
                ),
                Routine(
                    name = L("Zuhause B", "Home B"), note = L("Ohne Geräte · Ganzkörper", "No equipment · Full body"),
                    exercises = listOf(
                        pair("0815", "b", reps = 12, rest = 60), // triceps dips floor
                        pair("3769", "b", reps = 12, rest = 60), // curtsey squat
                        plan("3168", 3, 12, 60),  // bodyweight squatting row
                        plan("0489", 3, 12, 60),  // hyperextension
                        plan("1373", 3, 20, 45),  // bodyweight standing calf raise
                        plan("0872", 3, 12, 45),  // reverse crunch
                        plan("1160", 3, 10, 60),  // burpee
                    ),
                ),
            )
        },
    )
}
