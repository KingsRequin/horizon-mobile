package fr.kingsrequin.horizon.link

import fr.kingsrequin.horizon.protocol.Codec
import fr.kingsrequin.horizon.protocol.ToPc
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.SocketFactory
import javax.net.ssl.SSLContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Sessions WebSocket sur TLS (`wss://hôte:port/`), épinglées sur la clé du PC. Aucun ping : le
 * téléphone ne se réveille pas pour tenir la liaison, c'est le système qui signale une coupure.
 *
 * `bind` choisit par où sortent les connexions : l'appli y met la fabrique du réseau Wi-Fi, pour
 * ne pas chercher le PC sur les données mobiles.
 */
class OkHttpTransport(private val bind: (SocketFactory) -> SocketFactory = { it }) : Transport {
    private val base = OkHttpClient.Builder()
        .connectTimeout(CONNECT_SECONDS, TimeUnit.SECONDS)
        // Poignées de main TLS et WebSocket ; une session ouverte n'a pas de délai de lecture.
        .readTimeout(CONNECT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        // Le PC est sur le réseau local : ni proxy du Wi-Fi, ni redirection à suivre.
        .proxy(Proxy.NO_PROXY)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    override fun open(host: String, port: Int, fingerprint: String, listener: SessionListener): Session {
        val trust = PinnedTrustManager(fingerprint)
        val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        val client = base.newBuilder()
            .socketFactory(bind(SocketFactory.getDefault()))
            .sslSocketFactory(tls.socketFactory, trust)
            // Le certificat du PC ne porte pas son adresse : c'est l'empreinte de sa clé qui fait foi.
            .hostnameVerifier { _, _ -> true }
            .build()
        val ended = AtomicBoolean(false)
        fun end(error: Throwable?) {
            if (ended.compareAndSet(false, true)) listener.onClosed(error)
        }
        val request = try {
            Request.Builder().url("https://$host:$port/").build()
        } catch (e: IllegalArgumentException) {
            // Un hôte qui ne s'écrit pas dans une adresse : un échec comme un autre, pas une exception.
            end(e)
            return Closed
        }
        val socket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (!ended.get()) Codec.decode(text)?.let(listener::onMessage)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = end(null)

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = end(wrongPc(t) ?: t)
            },
        )
        return object : Session {
            override fun send(msg: ToPc): Boolean = !ended.get() && socket.send(Codec.encode(msg))

            override fun close() {
                // Fermeture propre si la session est ouverte ; sinon l'ouverture en cours est abandonnée.
                if (!socket.close(NORMAL_CLOSURE, null)) socket.cancel()
            }
        }
    }

    /**
     * Le refus de la clé du serveur, s'il est quelque part dans `error`. Quand un nom a plusieurs
     * adresses, OkHttp essaie les suivantes et rend la dernière erreur : le refus de la première
     * n'est alors qu'une erreur « jointe ».
     */
    private fun wrongPc(error: Throwable, depth: Int = 0): WrongPc? {
        if (error is WrongPc) return error
        if (depth >= MAX_DEPTH) return null
        return (listOfNotNull(error.cause) + error.suppressed).firstNotNullOfOrNull { wrongPc(it, depth + 1) }
    }

    /** Une session qui n'a jamais pu s'ouvrir. */
    private object Closed : Session {
        override fun send(msg: ToPc): Boolean = false

        override fun close() = Unit
    }

    private companion object {
        const val MAX_DEPTH = 8
        const val CONNECT_SECONDS = 5L
        const val NORMAL_CLOSURE = 1000
    }
}
