package fr.kingsrequin.horizon.link.pad

import fr.kingsrequin.horizon.protocol.ToPc
import org.junit.Assert.assertEquals
import org.junit.Test

class PadSenderTest {
    private val sent = mutableListOf<ToPc>()
    private val sender = PadSender { sent += it }

    @Test
    fun movesAreBatchedAtSixtyASecondKeepingTheRest() {
        sender.act(PadAction.Move(3.4f, 0f), 0)
        assertEquals("le premier part tout de suite", listOf<ToPc>(ToPc.InputMove(3, 0)), sent)
        sender.act(PadAction.Move(3.4f, 0f), 5)
        sender.act(PadAction.Move(3.4f, 0f), 10)
        assertEquals(1, sent.size)
        assertEquals("16 ms après le précédent", true, sender.pending)
        sender.flush(16)
        assertEquals(ToPc.InputMove(7, 0), sent.last())
        sender.flush(40)
        assertEquals("rien de neuf : rien ne part", 2, sent.size)
        sender.act(PadAction.Move(0.8f, 0f), 50)
        assertEquals("0,2 gardé + 0,8 : un pixel", ToPc.InputMove(1, 0), sent.last())
    }

    @Test
    fun aSubPixelMoveIsKeptUntilItAddsUp() {
        for (i in 0 until 10) {
            sender.act(PadAction.Move(0.3f, -0.3f), i * 20L)
        }
        assertEquals(3, sent.filterIsInstance<ToPc.InputMove>().sumOf { it.dx })
        assertEquals(-3, sent.filterIsInstance<ToPc.InputMove>().sumOf { it.dy })
    }

    @Test
    fun aClickSendsThePendingMoveFirst() {
        sender.act(PadAction.Move(5f, 0f), 0)
        sender.act(PadAction.Move(4f, 0f), 5)
        sender.act(PadAction.Click(Button.LEFT), 6)
        assertEquals(listOf(ToPc.InputMove(5, 0), ToPc.InputMove(4, 0), ToPc.InputButton("left", "click")), sent)
    }

    @Test
    fun buttonsDragAndScrollReachTheirMessages() {
        sender.act(PadAction.Down(Button.LEFT), 0)
        sender.act(PadAction.Up(Button.LEFT), 100)
        sender.act(PadAction.Click(Button.MIDDLE), 200)
        sender.act(PadAction.Scroll(0f, 50.5f), 300)
        sender.act(PadAction.Scroll(0f, 0.7f), 400)
        sender.act(PadAction.Zoom(130f), 500)
        assertEquals(
            listOf(
                ToPc.InputButton("left", "down"),
                ToPc.InputButton("left", "up"),
                ToPc.InputButton("middle", "click"),
                ToPc.InputScroll(0, 50),
                ToPc.InputScroll(0, 1),
                ToPc.InputZoom(130),
            ),
            sent,
        )
    }

    @Test
    fun sendNowSendsWhatWaitsWithoutWaitingForTheInterval() {
        sender.act(PadAction.Move(5f, 0f), 0)
        sender.act(PadAction.Scroll(0f, 120f), 4)
        sender.sendNow(5)
        assertEquals(listOf(ToPc.InputMove(5, 0), ToPc.InputScroll(0, 120)), sent)
        assertEquals(false, sender.pending)
    }

    @Test
    fun aBurstOfKeysIsPacedAtSixtyASecond() {
        val burst = (1..10).map { ToPc.InputText("ligne $it") }
        sender.queue(burst, 0)
        assertEquals("le premier part tout de suite", listOf<ToPc>(ToPc.InputText("ligne 1")), sent)
        assertEquals(true, sender.pending)
        sender.flush(10)
        assertEquals(1, sent.size)
        for (t in 1..9) sender.flush(t * 16L)
        assertEquals("un par intervalle, dans l'ordre", burst, sent)
        assertEquals(false, sender.pending)
    }

    @Test
    fun aClickWaitsForTheTextBeforeIt() {
        sender.queue(listOf(ToPc.InputText("a"), ToPc.InputText("b")), 0)
        sender.act(PadAction.Click(Button.LEFT), 1)
        assertEquals("le clic ne double pas le texte qui attend", listOf<ToPc>(ToPc.InputText("a")), sent)
        sender.flush(16)
        sender.flush(32)
        assertEquals(listOf(ToPc.InputText("a"), ToPc.InputText("b"), ToPc.InputButton("left", "click")), sent)
    }

    @Test
    fun nothingIsSentForAZeroMove() {
        sender.act(PadAction.Move(0.2f, 0.1f), 0)
        sender.flush(100)
        assertEquals(emptyList<ToPc>(), sent)
    }
}
