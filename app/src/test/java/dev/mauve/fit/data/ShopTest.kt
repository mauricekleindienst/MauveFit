package dev.mauve.fit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShopTest {
    private val target: (String) -> String? = { "pectorals" }
    private fun workout(sets: Int, id: String = "bench") = Workout(name = "W", startedAt = 1L, finishedAt = 2L,
        entries = listOf(ExerciseEntry(id, sets = List(sets) { SetEntry(60.0, 8, done = true) })))

    @Test fun skillsLevelWithSets() {
        assertEquals(0, Shop.skillLevel(9).first)
        assertEquals(1, Shop.skillLevel(10).first)
        assertEquals(2, Shop.skillLevel(25).first)
    }

    @Test fun collectionsFillInTiers() {
        val d = AppData(history = listOf(workout(30)))
        val c = Shop.collections(d).single()
        assertEquals(2, c.tier)
        assertEquals(50, c.next)
    }

    @Test fun buyingNeedsCoinsAndBooksThem() {
        val poor = AppData()
        val freeze = Shop.items().first { it.kind == Shop.Kind.Freeze }
        assertSame(poor, Shop.buy(poor, freeze, target))
        val rich = AppData(history = List(20) { workout(30) }, questCoins = 1000)
        val bought = Shop.buy(rich, freeze, target)
        assertEquals(rich.freezes + 1, bought.freezes)
        assertEquals(Shop.balance(rich, target) - freeze.price, Shop.balance(bought, target))
    }

    @Test fun aRerollDrawsNewQuests() {
        val day = LocalDate.of(2026, 11, 3)
        val before = Quests.daily(AppData(), day).map { it.key }
        val rolled = Quests.daily(AppData(rerollDay = day.toString(), rerollCount = 1), day).map { it.key }
        val again = Quests.daily(AppData(rerollDay = day.toString(), rerollCount = 2), day).map { it.key }
        assertTrue(before != rolled || rolled != again)
    }

    @Test fun aColourOrTitleIsBoughtOnce() {
        val rich = AppData(questCoins = 5000, questXp = 1000)
        val gold = Shop.items().first { it.key == "accent:gold" }
        val once = Shop.buy(rich, gold, target)
        assertTrue("accent:gold" in once.owned)
        assertSame(once, Shop.buy(once, gold, target))
        assertNotEquals(rich.coinsSpent, once.coinsSpent)
    }

    @Test fun betterItemsWaitForTheirLevel() {
        val immortal = Shop.items().first { it.key == "title:immortal" }
        val rich = AppData(questCoins = 100000)
        assertSame(rich, Shop.buy(rich, immortal, target))
    }
}
