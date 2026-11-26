package fr.kingsrequin.horizon.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteAccessTest {
    @Test
    fun theRemoteTabFollowsFeaturesAndRights() {
        assertEquals("PC 0.8 : pas de télécommande", Access.Hidden, remoteAccess(setOf("battery"), setOf("battery")))
        assertEquals(Access.ToGrant, remoteAccess(setOf("battery", "media"), setOf("battery", "sound")))
        assertEquals(Access.Granted, remoteAccess(setOf("battery", "media"), setOf("battery", "media")))
        assertEquals("un droit sans la fonction ne sert à rien", Access.Hidden, remoteAccess(setOf("battery"), setOf("battery", "media")))
    }
}

class SoundAccessTest {
    @Test
    fun mixerAndActionsFollowTheSoundRight() {
        assertEquals("PC 0.9 : ni mixeur ni actions", Access.Hidden, soundAccess(setOf("battery", "media"), setOf("battery", "media", "sound")))
        assertEquals(Access.ToGrant, soundAccess(setOf("battery", "media", "mixer", "actions"), setOf("battery", "media")))
        assertEquals(Access.Granted, soundAccess(setOf("battery", "media", "mixer", "actions"), setOf("battery", "sound")))
    }

    @Test
    fun theNextOutputWrapsAround() {
        val outputs = listOf("a", "b", "c")
        assertEquals("b", nextOutput(outputs, "a"))
        assertEquals("a", nextOutput(outputs, "c"))
        assertEquals("sortie par défaut inconnue : la première", "a", nextOutput(outputs, null))
        assertEquals(null, nextOutput(listOf("a"), "a"))
    }
}
