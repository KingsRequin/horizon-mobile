package fr.kingsrequin.horizon.link.pad

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max

/** Un doigt posé : identifiant stable tant qu'il reste posé, position en pixels. */
data class Finger(val id: Long, val x: Float, val y: Float)

/** Bouton de la souris du PC ; `wire` : son nom dans le protocole. */
enum class Button(val wire: String) { LEFT("left"), RIGHT("right"), MIDDLE("middle") }

/** Ce que fait un geste sur le pavé. Déplacements en pixels, défilement et zoom en unités de molette (120 = un cran). */
sealed interface PadAction {
    data class Move(val dx: Float, val dy: Float) : PadAction
    data class Click(val button: Button) : PadAction
    data class Down(val button: Button) : PadAction
    data class Up(val button: Button) : PadAction
    data class Scroll(val dx: Float, val dy: Float) : PadAction
    data class Zoom(val delta: Float) : PadAction
}

/**
 * Réglages du pavé. `pointerSpeed` : pixels du PC par pixel d'écran ; `scrollSpeed` : unités
 * de molette par pixel ; `naturalScroll` : le contenu suit les doigts (comme sur un téléphone) ;
 * `buttons` : boutons gauche et droit sous le pavé.
 */
data class PadSettings(
    val pointerSpeed: Float = 2f,
    val scrollSpeed: Float = 3f,
    val naturalScroll: Boolean = true,
    val buttons: Boolean = true,
)

/**
 * Reconnaît les gestes du pavé (comme un pavé tactile de portable) : un doigt glisse = pointeur ;
 * appui bref à un, deux ou trois doigts = clic gauche, droit, milieu ; deux doigts glissent =
 * défilement ; deux doigts s'écartent ou se rapprochent = zoom ; un appui puis un doigt reposé
 * aussitôt = bouton gauche tenu dès que ce doigt bouge, jusqu'au lever (glisser ; levé sans
 * bouger : second clic, donc double clic ; un second doigt : défilement ou clic droit).
 *
 * [touch] reçoit, à chaque changement, tous les doigts posés (liste vide : tous levés), et rend
 * ce qu'il faut faire. `slopPx` : distance en deçà de laquelle un doigt n'a pas bougé.
 */
class PadGestures(var settings: PadSettings = PadSettings(), private val slopPx: Float = 24f) {
    private data class P(val x: Float, val y: Float)

    private enum class Mode { UNDECIDED, SCROLL, ZOOM }

    /** Deux doigts posés : où ils ont commencé et où ils en sont, et ce qu'ils font. */
    private class Two(val a: Long, val b: Long, center: P, span: Float) {
        val startCenter = center
        val startSpan = span
        var center = center
        var span = span
        var mode = Mode.UNDECIDED
    }

    private val start = HashMap<Long, P>()
    private val last = HashMap<Long, P>()
    private var gestureStart = 0L
    private var maxFingers = 0
    private var moved = false
    private var multi = false
    private var two: Two? = null
    /** Doigt du glisser, une fois le bouton gauche tenu. */
    private var dragId: Long? = null
    /** Doigt reposé juste après un appui : glisser si le doigt bouge, double clic s'il se lève. */
    private var armedId: Long? = null
    /** Le doigt n'a encore rien déplacé : son mouvement est retenu tant que ce peut être un appui. */
    private var holding = true
    private var heldX = 0f
    private var heldY = 0f
    private var lastTapUp: Long? = null

    fun touch(fingers: List<Finger>, timeMs: Long): List<PadAction> {
        if (fingers.isEmpty()) return if (last.isEmpty()) emptyList() else end(timeMs)
        val out = mutableListOf<PadAction>()
        if (last.isEmpty()) begin(fingers, timeMs, out)
        for (f in fingers) {
            val s = start.getOrPut(f.id) { P(f.x, f.y) }
            if (hypot(f.x - s.x, f.y - s.y) > slopPx) moved = true
        }
        maxFingers = max(maxFingers, fingers.size)
        if (fingers.size > 1) {
            multi = true
            // Un second doigt après un appui : défilement ou clic droit, pas un glisser.
            armedId = null
        }
        val drag = dragId
        val armed = armedId
        when {
            drag != null -> fingers.firstOrNull { it.id == drag }?.let { slide(it, timeMs, out) }
            armed != null -> fingers.firstOrNull { it.id == armed }?.let {
                slide(it, timeMs, out) {
                    armedId = null
                    dragId = it.id
                    out += PadAction.Down(Button.LEFT)
                }
            }
            !multi -> slide(fingers.single(), timeMs, out)
            fingers.size == 2 && maxFingers == 2 -> twoFingers(fingers, out)
        }
        last.clear()
        for (f in fingers) last[f.id] = P(f.x, f.y)
        return out
    }

    /** Geste interrompu (écran quitté) : un bouton tenu est relâché. */
    fun cancel(): List<PadAction> {
        val out = if (dragId != null) listOf<PadAction>(PadAction.Up(Button.LEFT)) else emptyList()
        reset()
        lastTapUp = null
        return out
    }

    private fun begin(fingers: List<Finger>, timeMs: Long, out: MutableList<PadAction>) {
        reset()
        gestureStart = timeMs
        val armed = fingers.size == 1 && lastTapUp?.let { timeMs - it <= DOUBLE_TAP_MS } == true
        lastTapUp = null
        if (armed) armedId = fingers.single().id
    }

    private fun end(timeMs: Long): List<PadAction> {
        val out = mutableListOf<PadAction>()
        val mode = two?.mode ?: Mode.UNDECIDED
        if (dragId != null) {
            out += PadAction.Up(Button.LEFT)
        } else if (armedId != null) {
            // Second appui d'un double appui, sans glisser : le second clic.
            out += PadAction.Click(Button.LEFT)
        } else if (timeMs - gestureStart <= TAP_MS && !moved && mode == Mode.UNDECIDED) {
            val button = when (maxFingers) {
                1 -> Button.LEFT
                2 -> Button.RIGHT
                else -> Button.MIDDLE
            }
            out += PadAction.Click(button)
            if (button == Button.LEFT) lastTapUp = timeMs
        }
        reset()
        return out
    }

    private fun reset() {
        start.clear()
        last.clear()
        maxFingers = 0
        moved = false
        multi = false
        two = null
        dragId = null
        armedId = null
        holding = true
        heldX = 0f
        heldY = 0f
    }

    /**
     * Le doigt glisse : son mouvement est retenu tant que le geste peut encore être un appui (sous
     * le seuil et avant [TAP_MS]) : un appui qui tremble ne déplace pas le pointeur, et Windows
     * reconnaît le double clic (4 px au plus entre les deux). Ensuite, `onStart`, puis tout le
     * mouvement retenu d'un coup, puis chaque mouvement aussitôt.
     */
    private fun slide(f: Finger, timeMs: Long, out: MutableList<PadAction>, onStart: () -> Unit = {}) {
        val p = last[f.id] ?: return
        heldX += f.x - p.x
        heldY += f.y - p.y
        if (holding) {
            if (!moved && timeMs - gestureStart < TAP_MS) return
            holding = false
            onStart()
        }
        val speed = settings.pointerSpeed
        if (heldX != 0f || heldY != 0f) out += PadAction.Move(plain(heldX * speed), plain(heldY * speed))
        heldX = 0f
        heldY = 0f
    }

    private fun twoFingers(fingers: List<Finger>, out: MutableList<PadAction>) {
        val (a, b) = fingers.sortedBy { it.id }
        val center = P((a.x + b.x) / 2, (a.y + b.y) / 2)
        val span = hypot(b.x - a.x, b.y - a.y)
        val t = two
        if (t == null || t.a != a.id || t.b != b.id) {
            two = Two(a.id, b.id, center, span)
            return
        }
        if (t.mode == Mode.UNDECIDED) {
            val spread = abs(span - t.startSpan)
            val slide = hypot(center.x - t.startCenter.x, center.y - t.startCenter.y)
            t.mode = when {
                spread > slopPx && spread > slide -> Mode.ZOOM
                slide > slopPx -> Mode.SCROLL
                else -> return
            }
        }
        when (t.mode) {
            Mode.SCROLL -> {
                // Naturel : le contenu suit les doigts ; molette positive = vers le haut, vers la droite.
                val sign = if (settings.naturalScroll) 1f else -1f
                val k = settings.scrollSpeed * sign
                val dx = plain(-(center.x - t.center.x) * k)
                val dy = plain((center.y - t.center.y) * k)
                if (dx != 0f || dy != 0f) out += PadAction.Scroll(dx, dy)
            }
            Mode.ZOOM -> if (span > 0f && t.span > 0f && span != t.span) out += PadAction.Zoom(ln(span / t.span) * ZOOM_UNITS)
            Mode.UNDECIDED -> {}
        }
        t.center = center
        t.span = span
    }

    /** Sans le zéro négatif (que `equals` distingue de 0). */
    private fun plain(v: Float): Float = if (v == 0f) 0f else v

    companion object {
        /** Durée au plus d'un appui qui clique. */
        const val TAP_MS = 250L
        /** Délai au plus entre un appui et le doigt reposé qui tient le bouton. */
        const val DOUBLE_TAP_MS = 300L
        /** Unités de molette pour un écart des doigts multiplié par e (≈ 3 crans quand il double). */
        const val ZOOM_UNITS = 600f
    }
}
