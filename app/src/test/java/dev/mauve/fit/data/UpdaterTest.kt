package dev.mauve.fit.data

import dev.mauve.fit.system.Updater
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test fun versionsCompareAsNumbers() {
        assertTrue(Updater.newer("1.20.0", "1.19.17"))
        assertTrue(Updater.newer("v1.19.10", "1.19.9"))
        assertTrue(Updater.newer("2.0", "1.99.99"))
        assertFalse(Updater.newer("1.19.17", "1.19.17"))
        assertFalse(Updater.newer("1.19.9", "1.19.10"))
    }
}
