package fr.kingsrequin.horizon.link.pad

import fr.kingsrequin.horizon.protocol.ToPc

/**
 * Envoie les gestes du pavé au PC : déplacements, défilement et zoom regroupés (un envoi toutes
 * les `intervalMs` au plus, 60 par seconde par défaut ; le PC en accepte 120), en entiers, le
 * reste sous l'unité gardé pour l'envoi suivant. Un clic ou un bouton part aussitôt, après ce qui
 * attendait (le pointeur arrive avant le clic). [flush] envoie ce qui attend quand l'intervalle
 * est passé : à appeler tant que [pending].
 */
class PadSender(private val intervalMs: Long = 16, private val send: (ToPc) -> Unit) {
    private var moveX = 0.0
    private var moveY = 0.0
    private var scrollX = 0.0
    private var scrollY = 0.0
    private var zoom = 0.0
    private var lastSent: Long? = null

    /** Quelque chose attend son envoi. */
    val pending: Boolean
        get() = whole(moveX) != 0 || whole(moveY) != 0 || whole(scrollX) != 0 || whole(scrollY) != 0 || whole(zoom) != 0

    fun act(action: PadAction, nowMs: Long) {
        when (action) {
            is PadAction.Move -> {
                moveX += action.dx
                moveY += action.dy
            }
            is PadAction.Scroll -> {
                scrollX += action.dx
                scrollY += action.dy
            }
            is PadAction.Zoom -> zoom += action.delta
            is PadAction.Click -> button(action.button, "click", nowMs)
            is PadAction.Down -> button(action.button, "down", nowMs)
            is PadAction.Up -> button(action.button, "up", nowMs)
        }
        flush(nowMs)
    }

    /** Envoie ce qui attend si l'intervalle est passé depuis le dernier envoi. */
    fun flush(nowMs: Long) {
        val last = lastSent
        if (last == null || nowMs - last >= intervalMs) sendNow(nowMs)
    }

    private fun button(button: Button, action: String, nowMs: Long) {
        sendNow(nowMs)
        send(ToPc.InputButton(button.wire, action))
    }

    /** Envoie tout de suite ce qui attend (avant une touche ou du texte, ou en quittant le pavé). */
    fun sendNow(nowMs: Long) {
        if (!pending) return
        val (mx, my, sx, sy, z) = listOf(whole(moveX), whole(moveY), whole(scrollX), whole(scrollY), whole(zoom))
        if (mx != 0 || my != 0) send(ToPc.InputMove(mx, my))
        if (sx != 0 || sy != 0) send(ToPc.InputScroll(sx, sy))
        if (z != 0) send(ToPc.InputZoom(z))
        moveX -= mx
        moveY -= my
        scrollX -= sx
        scrollY -= sy
        zoom -= z
        lastSent = nowMs
    }

    /** Partie entière, vers zéro : le reste garde son signe. */
    private fun whole(v: Double): Int = v.toInt()
}
