package fr.kingsrequin.horizon.link.pad

import fr.kingsrequin.horizon.protocol.ToPc
import org.junit.Assert.assertEquals
import org.junit.Test

class TypingTest {
    @Test
    fun textGoesAsUnicode() {
        assertEquals(listOf(ToPc.InputText("Bonjour é ☃")), Typing.typed("Bonjour é ☃", emptySet()))
        assertEquals(emptyList<ToPc>(), Typing.typed("", emptySet()))
    }

    @Test
    fun aNewLineIsTheEnterKey() {
        assertEquals(listOf(ToPc.InputText("a"), ToPc.InputKey("enter"), ToPc.InputText("b")), Typing.typed("a\nb", emptySet()))
        assertEquals(listOf(ToPc.InputKey("tab")), Typing.typed("\t", emptySet()))
    }

    @Test
    fun aHeldModifierTurnsALetterIntoAShortcut() {
        assertEquals(listOf(ToPc.InputKey("c", listOf("ctrl"))), Typing.typed("C", setOf(Mod.CTRL)))
        assertEquals("ordre fixe", listOf(ToPc.InputKey("z", listOf("ctrl", "shift"))), Typing.typed("z", setOf(Mod.SHIFT, Mod.CTRL)))
        assertEquals(listOf(ToPc.InputKey("1", listOf("alt"))), Typing.typed("1", setOf(Mod.ALT)))
        assertEquals("un accent n'a pas de touche nommée : il part en texte", listOf(ToPc.InputText("é")), Typing.typed("é", setOf(Mod.CTRL)))
    }

    @Test
    fun aLongTextIsSplitInto256CharacterMessages() {
        val long = "x".repeat(600)
        val parts = Typing.typed(long, emptySet())
        assertEquals(listOf(256, 256, 88), parts.map { (it as ToPc.InputText).text.length })
    }

    @Test
    fun aSurrogatePairIsNeverSplit() {
        val text = "a".repeat(255) + "😀"
        val parts = Typing.typed(text, emptySet()).map { (it as ToPc.InputText).text }
        assertEquals(listOf("a".repeat(255), "😀"), parts)
    }

    @Test
    fun aSpecialKeyCarriesTheModifiers() {
        assertEquals(ToPc.InputKey("left", listOf("ctrl", "shift")), Typing.key("left", setOf(Mod.SHIFT, Mod.CTRL)))
        assertEquals(ToPc.InputKey("escape"), Typing.key("escape", emptySet()))
    }
}
