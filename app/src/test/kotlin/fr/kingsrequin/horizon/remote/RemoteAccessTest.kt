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
