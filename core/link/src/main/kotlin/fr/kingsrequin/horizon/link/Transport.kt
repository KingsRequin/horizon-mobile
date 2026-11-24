package fr.kingsrequin.horizon.link

import fr.kingsrequin.horizon.protocol.FromPc
import fr.kingsrequin.horizon.protocol.ToPc

/** Une session ouverte (ou en train de s'ouvrir) avec le PC. */
interface Session {
    /** Met `msg` en file d'envoi ; faux si la session est fermée. */
    fun send(msg: ToPc): Boolean

    /** Ferme la session ; [SessionListener.onClosed] est appelé avec `null`. */
    fun close()
}

/** Ce qui arrive sur une session. Appelé sur un fil du transport, jamais deux fois à la fois. */
interface SessionListener {
    fun onMessage(msg: FromPc)

    /** La session est finie, une seule fois : `null` pour une fermeture normale, sinon ce qui l'a coupée ([WrongPc]…). */
    fun onClosed(error: Throwable?)
}

/** Ouvre des sessions avec un PC. Derrière une interface : la reprise de la liaison se teste sans réseau. */
interface Transport {
    /** Lance l'ouverture et rend aussitôt ; l'échec arrive par [SessionListener.onClosed]. */
    fun open(host: String, port: Int, fingerprint: String, listener: SessionListener): Session
}
