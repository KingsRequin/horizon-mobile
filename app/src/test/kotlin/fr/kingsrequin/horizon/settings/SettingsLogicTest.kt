package fr.kingsrequin.horizon.settings

import fr.kingsrequin.horizon.connection.LinkMode
import fr.kingsrequin.horizon.link.LinkState
import fr.kingsrequin.horizon.link.OfflineReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLogicTest {
    @Test
    fun theReportHelpsWithoutLeakingAnything() {
        val log = listOf("12:01:00 192.168.1.49 : ConnectException : Failed to connect to /192.168.1.49:28680", "12:01:05 BUREAU.local : UnknownHostException", "12:01:06 10.0.0.5 : fermée", "12:01:07 PC injoignable")
        val text = diagnostic(DeviceInfo("Xiaomi", "24129PN74G", 37, gms = true), "0.1.0", LinkMode.Continuous, LinkState.Offline("BUREAU", OfflineReason.Unreachable, 0), "signaled : kill background", log)
        for (must in listOf("Horizon Mobile 0.1.0", "Xiaomi 24129PN74G", "Android API 37", "Continuous", "Offline", "Unreachable", "signaled : kill background", "adresse 1 : ConnectException : Failed to connect to /adresse 1:28680", "adresse 2 : fermée", "nom du PC.local : UnknownHostException", "PC injoignable")) {
            assertTrue("$must dans :\n$text", text.contains(must))
        }
        for (never in listOf("192.168.1.49", "10.0.0.5", "BUREAU")) assertFalse(never, text.contains(never))
        val connected = diagnostic(DeviceInfo("Google", "Pixel 9", 36, gms = false), "0.1.0", LinkMode.WhenOpen, LinkState.Connected("Mon PC", 0, setOf("battery"), setOf("battery")), null, emptyList())
        assertTrue(connected.contains("Connected") && connected.contains("battery") && connected.contains("services Google : non"))
        assertFalse(connected.contains("Mon PC"))
    }

    @Test
    fun onlyAStrictlyNewerReleaseIsOffered() {
        val apk = "https://github.com/KingsRequin/horizon-mobile/releases/download/v0.2.0/horizon-mobile-0.2.0.apk"
        val json = """{"tag_name":"v0.2.0","assets":[{"name":"SHA256SUMS","browser_download_url":"https://x/sums"},{"name":"horizon-mobile-0.2.0.apk","browser_download_url":"$apk"}]}"""
        assertEquals(Update("0.2.0", apk), newerVersion("0.1.0", json))
        assertEquals(Update("0.2.0", apk), newerVersion("0.1.9-dev", json))
        assertNull("seulement un fichier des versions de ce dépôt", newerVersion("0.1.0", json.replace(apk, "https://exemple.fr/a.apk")))
        assertNull(newerVersion("0.2.0", json))
        assertNull(newerVersion("0.10.0", json))
        assertNull(newerVersion("0.1.0", "pas du json"))
        assertNull(newerVersion("0.1.0", """{"tag_name":"v0.2.0","assets":[]}"""))
        assertNull(newerVersion("0.1.0", """{"tag_name":"nightly","assets":[{"name":"a.apk","browser_download_url":"https://x/a.apk"}]}"""))
        assertNull("jamais un lien qui n'est pas en https", newerVersion("0.1.0", json.replace("https://github", "http://github")))
    }

    @Test
    fun backgroundHelpComesOnlyWhenItIsNeeded() {
        assertNull(backgroundAdvice(LinkMode.Continuous, connectedOnce = false, ignoringOptimizations = false, exitReason = null))
        assertEquals(Advice.AllowBackground, backgroundAdvice(LinkMode.Continuous, true, false, null))
        assertNull(backgroundAdvice(LinkMode.Continuous, true, true, "user_requested"))
        assertEquals(Advice.WasKilled, backgroundAdvice(LinkMode.Continuous, true, true, "signaled : kill background"))
        assertEquals(Advice.WasKilled, backgroundAdvice(LinkMode.Continuous, true, false, "low_memory"))
        assertEquals(Advice.WasKilled, backgroundAdvice(LinkMode.Continuous, true, true, "freezer"))
        assertNull(backgroundAdvice(LinkMode.WhenOpen, true, false, "signaled"))
    }
}
