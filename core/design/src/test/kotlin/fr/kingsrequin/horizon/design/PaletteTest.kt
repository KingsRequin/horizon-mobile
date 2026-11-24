package fr.kingsrequin.horizon.design

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les valeurs attendues sont celles de la charte, à ±1 par canal. */
class PaletteTest {
    private fun assertColor(role: String, seed: Int, expected: Int, actual: Int) {
        assertEquals("$role : opaque", 0xFF, actual ushr 24)
        for (shift in intArrayOf(16, 8, 0)) {
            val (e, a) = (expected shr shift and 0xFF) to (actual shr shift and 0xFF)
            assertTrue("$role de %06X : attendu %06X, obtenu %06X".format(seed and 0xFFFFFF, expected, actual and 0xFFFFFF), abs(e - a) <= 1)
        }
    }

    private fun assertPalette(
        seed: Int, surface: Int, surfaceContainer: Int, surfaceContainerHigh: Int, surfaceContainerHighest: Int,
        onSurface: Int, onSurfaceVariant: Int, outline: Int, outlineVariant: Int, primary: Int, onPrimary: Int,
        primaryContainer: Int, onPrimaryContainer: Int, coverGround: Int,
    ) {
        val p = palette(seed)
        assertColor("surface", seed, surface, p.surface)
        assertColor("surfaceContainer", seed, surfaceContainer, p.surfaceContainer)
        assertColor("surfaceContainerHigh", seed, surfaceContainerHigh, p.surfaceContainerHigh)
        assertColor("surfaceContainerHighest", seed, surfaceContainerHighest, p.surfaceContainerHighest)
        assertColor("onSurface", seed, onSurface, p.onSurface)
        assertColor("onSurfaceVariant", seed, onSurfaceVariant, p.onSurfaceVariant)
        assertColor("outline", seed, outline, p.outline)
        assertColor("outlineVariant", seed, outlineVariant, p.outlineVariant)
        assertColor("primary", seed, primary, p.primary)
        assertColor("onPrimary", seed, onPrimary, p.onPrimary)
        assertColor("primaryContainer", seed, primaryContainer, p.primaryContainer)
        assertColor("onPrimaryContainer", seed, onPrimaryContainer, p.onPrimaryContainer)
        assertColor("coverGround", seed, coverGround, p.coverGround)
    }

    /** Contraste WCAG entre deux couleurs opaques. */
    private fun contrast(a: Int, b: Int): Double {
        fun luminance(c: Int): Double {
            fun channel(v: Int): Double = (v / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
            return 0.2126 * channel(c shr 16 and 0xFF) + 0.7152 * channel(c shr 8 and 0xFF) + 0.0722 * channel(c and 0xFF)
        }
        val (la, lb) = luminance(a) to luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    @Test
    fun thePaletteMatchesTheCharterForFourSeeds() {
        assertPalette(0xFFF97316.toInt(), surface = 0x130D0A, surfaceContainer = 0x211712, surfaceContainerHigh = 0x2E221C, surfaceContainerHighest = 0x3F2F26, onSurface = 0xF3E5DE, onSurfaceVariant = 0xCFB8AD, outline = 0x968177, outlineVariant = 0x4D3F38, primary = 0xFFAE85, onPrimary = 0x411902, primaryContainer = 0x58321D, onPrimaryContainer = 0xFBD5C2, coverGround = 0x381704)
        assertPalette(0xFFA06CD5.toInt(), surface = 0x0F0D13, surfaceContainer = 0x1B1720, surfaceContainerHigh = 0x28232E, surfaceContainerHighest = 0x362F3F, onSurface = 0xEAE5F2, onSurfaceVariant = 0xC2B9CE, outline = 0x8A8295, outlineVariant = 0x45404C, primary = 0xD5B2FE, onPrimary = 0x2E1C41, primaryContainer = 0x453458, onPrimaryContainer = 0xE5D6F9, coverGround = 0x281938)
        assertPalette(0xFF22B8CF.toInt(), surface = 0x081011, surfaceContainer = 0x0F1C1E, surfaceContainerHigh = 0x18282B, surfaceContainerHighest = 0x21373B, onSurface = 0xDBEBEF, onSurfaceVariant = 0xA9C3C9, outline = 0x728B90, outlineVariant = 0x354649, primary = 0x60D7EC, onPrimary = 0x002C34, primaryContainer = 0x004650, onPrimaryContainer = 0xB9E8F1, coverGround = 0x00272E)
        assertPalette(0xFF4CC26A.toInt(), surface = 0x0B100C, surfaceContainer = 0x141C15, surfaceContainerHigh = 0x1E281F, surfaceContainerHighest = 0x29372B, onSurface = 0xE0EBE1, onSurfaceVariant = 0xB1C3B3, outline = 0x7A8C7C, outlineVariant = 0x3B463C, primary = 0x90D89D, onPrimary = 0x052F12, primaryContainer = 0x22462A, onPrimaryContainer = 0xC8E8CC, coverGround = 0x072910)
    }

    @Test
    fun aGreySeedStillGivesAReadablePalette() {
        val p = palette(0xFF808080.toInt())
        assertTrue(contrast(p.onSurface, p.surfaceContainerHighest) >= 10.0)
        assertTrue(contrast(p.onPrimary, p.primary) >= 8.0)
        assertEquals("un gris prend la teinte par défaut", palette(DEFAULT_SEED), p)
        assertEquals(palette(DEFAULT_SEED), palette(0xFF000000.toInt()))
        assertEquals(palette(DEFAULT_SEED), palette(0xFFFFFFFF.toInt()))
    }

    @Test
    fun theSeedSAlphaDoesNotMatter() {
        assertEquals(palette(0xFFA06CD5.toInt()), palette(0x00A06CD5))
    }
}
