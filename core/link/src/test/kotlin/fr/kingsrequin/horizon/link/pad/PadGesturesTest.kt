package fr.kingsrequin.horizon.link.pad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PadGesturesTest {
    /** Vitesses à 1 : les valeurs attendues se lisent en pixels. */
    private fun pad(natural: Boolean = true) = PadGestures(PadSettings(pointerSpeed = 1f, scrollSpeed = 1f, naturalScroll = natural), slopPx = 20f)

    private fun f(id: Long, x: Float, y: Float) = Finger(id, x, y)

    /** Un appui d'un doigt, de `at` à `at + 80`. */
    private fun PadGestures.tap(at: Long, x: Float = 100f, y: Float = 100f): List<PadAction> =
        touch(listOf(f(9, x, y)), at) + touch(emptyList(), at + 80)

    @Test
    fun aSlideMovesThePointer() {
        val p = pad()
        assertEquals(emptyList<PadAction>(), p.touch(listOf(f(0, 100f, 100f)), 0))
        assertEquals(listOf(PadAction.Move(40f, -5f)), p.touch(listOf(f(0, 140f, 95f)), 16))
        assertEquals(listOf(PadAction.Move(10f, 0f)), p.touch(listOf(f(0, 150f, 95f)), 32))
        assertEquals("un glissé n'est pas un clic", emptyList<PadAction>(), p.touch(emptyList(), 300))
    }

    @Test
    fun theSpeedScalesTheMove() {
        val p = PadGestures(PadSettings(pointerSpeed = 2f), slopPx = 20f)
        p.touch(listOf(f(0, 0f, 0f)), 0)
        assertEquals(listOf(PadAction.Move(40f, 20f)), p.touch(listOf(f(0, 20f, 10f)), 16))
    }

    @Test
    fun aQuickTapClicks() {
        assertEquals(listOf(PadAction.Click(Button.LEFT)), pad().tap(0))
    }

    @Test
    fun aTremblingTapClicksWithoutMovingThePointer() {
        val p = pad()
        p.touch(listOf(f(0, 100f, 100f)), 0)
        assertEquals("un appui qui tremble ne déplace pas le pointeur (double clic de Windows : 4 px)", emptyList<PadAction>(), p.touch(listOf(f(0, 104f, 103f)), 40))
        assertEquals(listOf(PadAction.Click(Button.LEFT)), p.touch(emptyList(), 120))
    }

    @Test
    fun aSlowSmallMoveIsSentOnceItIsNoLongerATap() {
        val p = pad()
        p.touch(listOf(f(0, 100f, 100f)), 0)
        assertEquals(emptyList<PadAction>(), p.touch(listOf(f(0, 105f, 100f)), 100))
        assertEquals("passé la durée d'un appui, le mouvement retenu part avec le nouveau", listOf(PadAction.Move(10f, 0f)), p.touch(listOf(f(0, 110f, 100f)), 300))
        assertEquals(listOf(PadAction.Move(2f, 0f)), p.touch(listOf(f(0, 112f, 100f)), 316))
    }

    @Test
    fun aLongPressIsNotAClick() {
        val p = pad()
        p.touch(listOf(f(0, 100f, 100f)), 0)
        assertEquals(emptyList<PadAction>(), p.touch(emptyList(), 600))
    }

    @Test
    fun twoAndThreeFingerTapsClickRightAndMiddle() {
        val p = pad()
        p.touch(listOf(f(0, 100f, 100f)), 0)
        p.touch(listOf(f(0, 100f, 100f), f(1, 160f, 100f)), 30)
        p.touch(listOf(f(1, 160f, 100f)), 90)
        assertEquals("doigts posés et levés en décalé : un seul clic droit", listOf(PadAction.Click(Button.RIGHT)), p.touch(emptyList(), 110))
        p.touch(listOf(f(2, 100f, 100f), f(3, 150f, 100f), f(4, 200f, 100f)), 1_000)
        assertEquals(listOf(PadAction.Click(Button.MIDDLE)), p.touch(emptyList(), 1_090))
    }

    @Test
    fun aDoubleTapGivesADoubleClick() {
        val p = pad()
        assertEquals(listOf(PadAction.Click(Button.LEFT)), p.tap(0))
        assertEquals(emptyList<PadAction>(), p.touch(listOf(f(1, 102f, 101f)), 200))
        assertEquals("tremblement du second appui : retenu", emptyList<PadAction>(), p.touch(listOf(f(1, 105f, 103f)), 220))
        assertEquals("deux clics au même endroit : Windows voit un double clic", listOf(PadAction.Click(Button.LEFT)), p.touch(emptyList(), 260))
    }

    @Test
    fun aDoubleTapHeldDragsUntilTheFingerLifts() {
        val p = pad()
        p.tap(0)
        assertEquals(emptyList<PadAction>(), p.touch(listOf(f(1, 100f, 100f)), 200))
        assertEquals("le bouton se tient au premier vrai mouvement", listOf(PadAction.Down(Button.LEFT), PadAction.Move(50f, 0f)), p.touch(listOf(f(1, 150f, 100f)), 300))
        assertEquals("un second doigt ne change rien au glisser", emptyList<PadAction>(), p.touch(listOf(f(1, 150f, 100f), f(2, 200f, 200f)), 350))
        assertEquals(listOf(PadAction.Move(0f, 30f)), p.touch(listOf(f(1, 150f, 130f)), 400))
        assertEquals(listOf(PadAction.Up(Button.LEFT)), p.touch(emptyList(), 2_000))
    }

    @Test
    fun aTapAfterTheDoubleTapWindowIsASingleClickAgain() {
        val p = pad()
        p.tap(0)
        assertEquals(emptyList<PadAction>(), p.touch(listOf(f(1, 100f, 100f)), 700))
        assertEquals(listOf(PadAction.Click(Button.LEFT)), p.touch(emptyList(), 780))
    }

    @Test
    fun aTapThenTwoFingersScrollInsteadOfDragging() {
        val p = pad()
        p.tap(0)
        assertEquals(emptyList<PadAction>(), p.touch(listOf(f(1, 100f, 100f)), 150))
        assertEquals(emptyList<PadAction>(), p.touch(listOf(f(1, 100f, 100f), f(2, 160f, 100f)), 170))
        assertEquals(listOf(PadAction.Scroll(0f, 30f)), p.touch(listOf(f(1, 100f, 130f), f(2, 160f, 130f)), 186))
        assertEquals(emptyList<PadAction>(), p.touch(emptyList(), 300))
    }

    @Test
    fun aTapThenATwoFingerTapIsARightClick() {
        val p = pad()
        p.tap(0)
        p.touch(listOf(f(1, 100f, 100f)), 150)
        p.touch(listOf(f(1, 100f, 100f), f(2, 160f, 100f)), 170)
        assertEquals(listOf(PadAction.Click(Button.RIGHT)), p.touch(emptyList(), 240))
    }

    @Test
    fun aRightTapDoesNotArmTheDrag() {
        val p = pad()
        p.touch(listOf(f(0, 100f, 100f), f(1, 150f, 100f)), 0)
        p.touch(emptyList(), 80)
        assertEquals(emptyList<PadAction>(), p.touch(listOf(f(2, 100f, 100f)), 200))
        assertEquals(listOf(PadAction.Click(Button.LEFT)), p.touch(emptyList(), 280))
    }

    @Test
    fun twoFingersScrollBothWays() {
        val natural = pad()
        natural.touch(listOf(f(0, 100f, 100f), f(1, 160f, 100f)), 0)
        val down = natural.touch(listOf(f(0, 100f, 130f), f(1, 160f, 130f)), 16)
        assertEquals("doigts vers le bas : le contenu suit, la vue monte (molette positive)", listOf(PadAction.Scroll(0f, 30f)), down)
        val left = natural.touch(listOf(f(0, 70f, 130f), f(1, 130f, 130f)), 32)
        assertEquals("doigts vers la gauche : la vue va à droite", listOf(PadAction.Scroll(30f, 0f)), left)
        assertEquals("un défilement n'est pas un clic droit", emptyList<PadAction>(), natural.touch(emptyList(), 60))

        val classic = pad(natural = false)
        classic.touch(listOf(f(0, 100f, 100f), f(1, 160f, 100f)), 0)
        assertEquals(listOf(PadAction.Scroll(0f, -30f)), classic.touch(listOf(f(0, 100f, 130f), f(1, 160f, 130f)), 16))
    }

    @Test
    fun aPinchZooms() {
        val p = pad()
        p.touch(listOf(f(0, 100f, 100f), f(1, 200f, 100f)), 0)
        val spread = p.touch(listOf(f(0, 50f, 100f), f(1, 250f, 100f)), 16)
        assertEquals(1, spread.size)
        val zoomIn = spread.single() as PadAction.Zoom
        assertTrue("écarter : zoom avant", zoomIn.delta > 0)
        val pinch = p.touch(listOf(f(0, 100f, 100f), f(1, 200f, 100f)), 32).single() as PadAction.Zoom
        assertTrue("pincer : zoom arrière", pinch.delta < 0)
        assertEquals("revenir à l'écart de départ : autant de zoom arrière que de zoom avant", zoomIn.delta, -pinch.delta, 0.01f)
        assertEquals(emptyList<PadAction>(), p.touch(emptyList(), 60))
    }

    @Test
    fun liftingOneOfTwoFingersDoesNotJumpThePointer() {
        val p = pad()
        p.touch(listOf(f(0, 100f, 100f), f(1, 160f, 100f)), 0)
        p.touch(listOf(f(0, 100f, 140f), f(1, 160f, 140f)), 16)
        assertEquals(emptyList<PadAction>(), p.touch(listOf(f(1, 160f, 140f)), 32))
        assertEquals("le doigt restant d'un défilement ne déplace pas le pointeur", emptyList<PadAction>(), p.touch(listOf(f(1, 300f, 300f)), 48))
        p.touch(emptyList(), 60)
        p.touch(listOf(f(5, 0f, 0f)), 1_000)
        assertEquals(listOf(PadAction.Move(30f, 0f)), p.touch(listOf(f(5, 30f, 0f)), 1_016))
    }

    @Test
    fun aDragEndsIfTheGestureIsCancelled() {
        val p = pad()
        p.tap(0)
        p.touch(listOf(f(1, 100f, 100f)), 200)
        assertEquals("rien de tenu avant le mouvement", emptyList<PadAction>(), PadGestures().apply { tap(0); touch(listOf(f(1, 100f, 100f)), 200) }.cancel())
        p.touch(listOf(f(1, 150f, 100f)), 300)
        assertEquals(listOf(PadAction.Up(Button.LEFT)), p.cancel())
        assertEquals("rien de tenu : rien à relâcher", emptyList<PadAction>(), p.cancel())
    }
}
