package fr.kingsrequin.horizon.connection

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchTest {
    @Test
    fun mediaIsAlwaysWatchedAndScreensAddTheirTopics() {
        assertEquals(setOf("media"), watchedTopics(emptyMap()))
        assertEquals(setOf("media", "volume", "mixer"), watchedTopics(mapOf("volume" to 1, "mixer" to 2, "actions" to 0)))
        assertEquals(setOf("media", "actions"), watchedTopics(mapOf("actions" to 1)))
    }
}
