package fr.kingsrequin.horizon.link

import fr.kingsrequin.horizon.protocol.FromPc
import java.security.cert.X509Certificate
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate

/** Un faux PC : serveur WebSocket sur TLS avec un certificat auto-signé, comme Horizon. */
class FakePc(sessions: Int = 1) : AutoCloseable {
    private val held = HeldCertificate.Builder().commonName("Horizon").addSubjectAlternativeName("horizon.local").build()
    private val server = MockWebServer()
    private val inbox = LinkedBlockingQueue<String>()
    private val sockets = LinkedBlockingQueue<WebSocket>()
    private val closed = LinkedBlockingQueue<Int>()

    val certificate: X509Certificate get() = held.certificate
    val host: String get() = server.hostName
    val port: Int get() = server.port

    init {
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(held).build().sslSocketFactory())
        repeat(sessions) {
            server.enqueue(
                MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        sockets.put(webSocket)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        inbox.put(text)
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        closed.put(code)
                        webSocket.close(code, null)
                    }
                }).build(),
            )
        }
        server.start()
    }

    private val current: WebSocket by lazy { checkNotNull(sockets.poll(5, TimeUnit.SECONDS)) { "aucun téléphone ne s'est connecté" } }

    /** Le prochain message reçu du téléphone. */
    fun received(): JsonObject = Json.parseToJsonElement(checkNotNull(inbox.poll(5, TimeUnit.SECONDS)) { "rien reçu" }).jsonObject

    fun send(text: String) {
        check(current.send(text))
    }

    /** Ferme la session côté PC. */
    fun hangUp() {
        current.close(1000, null)
    }

    /** Code de fermeture reçu du téléphone, ou `null` s'il n'a pas fermé. */
    fun closedByPhone(): Int? = closed.poll(5, TimeUnit.SECONDS)

    override fun close() = server.close()
}

/** Écoute une session et garde ce qui arrive. */
class Recorder : SessionListener {
    private val messages = LinkedBlockingQueue<FromPc>()
    private val ends = LinkedBlockingQueue<Result<Unit>>()

    override fun onMessage(msg: FromPc) {
        messages.put(msg)
    }

    override fun onClosed(error: Throwable?) {
        ends.put(if (error == null) Result.success(Unit) else Result.failure(error))
    }

    fun awaitMessages(count: Int): List<FromPc> = List(count) { checkNotNull(messages.poll(5, TimeUnit.SECONDS)) { "message $it jamais reçu" } }

    /** La fin de la session : l'erreur, ou `null` pour une fermeture normale. Échoue si rien n'arrive en 10 s. */
    fun awaitClosed(): Throwable? = checkNotNull(ends.poll(10, TimeUnit.SECONDS)) { "la session ne s'est pas terminée" }.exceptionOrNull()

    /** Vrai si la session s'est terminée plus d'une fois (elle ne doit le dire qu'une fois). */
    fun closedAgain(): Boolean = ends.poll(300, TimeUnit.MILLISECONDS) != null
}
