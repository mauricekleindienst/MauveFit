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
}
