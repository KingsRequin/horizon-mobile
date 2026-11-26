package fr.kingsrequin.horizon.link.pad

import fr.kingsrequin.horizon.protocol.ToPc

/**
 * Envoie les gestes du pavé au PC : déplacements, défilement et zoom regroupés (un envoi toutes
 * les `intervalMs` au plus, 60 par seconde par défaut ; le PC en accepte 120), en entiers, le
 * reste sous l'unité gardé pour l'envoi suivant. Touches et texte ([queue]) passent par une file,
 * un message par intervalle : un long collage arrive en entier au lieu d'être coupé par le débit
 * du PC. Un clic ou un bouton part aussitôt (après le pointeur qui attendait), sauf s'il y a du
 * texte en file : il le suit. [flush] envoie ce qui attend quand l'intervalle est passé : à
 * appeler tant que [pending].
 */
class PadSender(private val intervalMs: Long = 16, private val send: (ToPc) -> Unit) {
    private var moveX = 0.0
    private var moveY = 0.0
    private var scrollX = 0.0
    private var scrollY = 0.0
    private var zoom = 0.0
    private var lastSent: Long? = null
    private val queued = ArrayDeque<ToPc>()

    /** Quelque chose attend son envoi. */
    val pending: Boolean
        get() = moving || queued.isNotEmpty()

    private val moving: Boolean
        get() = whole(moveX) != 0 || whole(moveY) != 0 || whole(scrollX) != 0 || whole(scrollY) != 0 || whole(zoom) != 0

    /** Touches et texte, à envoyer dans l'ordre, un par intervalle. */
    fun queue(messages: List<ToPc>, nowMs: Long) {
        queued.addAll(messages)
        flush(nowMs)
    }

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

    /** Envoie ce qui attend si l'intervalle est passé depuis le dernier envoi : le mouvement, puis un message de la file. */
    fun flush(nowMs: Long) {
        val last = lastSent
        if (last != null && nowMs - last < intervalMs) return
        sendMoves(nowMs)
        queued.removeFirstOrNull()?.let {
            send(it)
            lastSent = nowMs
        }
    }

    private fun button(button: Button, action: String, nowMs: Long) {
        val message = ToPc.InputButton(button.wire, action)
        if (queued.isNotEmpty()) {
            queued.addLast(message)
            return
        }
        sendMoves(nowMs)
        send(message)
    }

    /** Envoie tout de suite tout ce qui attend, file comprise (en quittant le pavé). */
    fun sendNow(nowMs: Long) {
        sendMoves(nowMs)
        while (queued.isNotEmpty()) send(queued.removeFirst())
    }

    private fun sendMoves(nowMs: Long) {
        if (!moving) return
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
