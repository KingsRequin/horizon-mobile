package fr.kingsrequin.horizon.link

import fr.kingsrequin.horizon.protocol.FromPc
import fr.kingsrequin.horizon.protocol.PROTOCOL
import fr.kingsrequin.horizon.protocol.PairingLink
import fr.kingsrequin.horizon.protocol.ToPc
import fr.kingsrequin.horizon.protocol.isHostLabel
import fr.kingsrequin.horizon.protocol.isPlainName
import fr.kingsrequin.horizon.protocol.parseColor
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Tient la liaison avec le PC appairé, sans jamais se réveiller de lui-même.
 *
 * Une **série** : un essai tout de suite, puis après 2, 5, 15, 30 et 60 s ([BACKOFF_S]). Un
 * **essai** parcourt les adresses du PC l'une après l'autre : la dernière qui a marché, celles
 * du QR, puis `<nom>.local`. Une série part quand le réseau revient, quand l'appli s'ouvre ou
 * quand la session tombe. Au bout d'une série sans réponse, plus rien ne tourne : le prochain
 * essai (un seul) vient d'un message à envoyer, c'est-à-dire d'un changement de batterie, moment
 * où le téléphone est de toute façon réveillé. Le PC allumé après coup est donc retrouvé sans
 * minuterie ni ping.
 *
 * Tous les événements passent par une file lue par une seule coroutine de `scope` : les
 * méthodes s'appellent de n'importe quel fil.
 */
class ConnectionManager(
    private val store: PairingStore,
    private val transport: Transport,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
    private val deviceName: () -> String,
    private val appVersion: String,
    private val log: LinkLog = LinkLog(),
) {
    private val current = MutableStateFlow<LinkState>(LinkState.Starting)
    val state: StateFlow<LinkState> = current.asStateFlow()

    /** Appelé à chaque session ouverte (fil de la liaison) : c'est le moment d'envoyer l'état courant. */
    @Volatile
    var onConnected: (() -> Unit)? = null

    /**
     * Messages du PC pendant une session (fil de la liaison) : lecture, pochette, volume. L'accent
     * et les droits n'y passent pas : ils changent [state].
     */
    @Volatile
    var onPcMessage: ((FromPc) -> Unit)? = null

    /** Ce que l'appli regarde ([watch]) : redemandé à chaque session. */
    private var watched: Set<String> = emptySet()
    private val watchLock = Any()

    private sealed interface Event {
        data class Network(val available: Boolean) : Event
        data object AppOpened : Event
        data class Active(val active: Boolean) : Event

        /** Un message attend sans session : l'occasion d'un essai. */
        data object Kick : Event
        data class Message(val attempt: Int, val msg: FromPc) : Event
        data class Closed(val attempt: Int, val error: Throwable?) : Event
        data class NoReply(val attempt: Int) : Event
        data object Retry : Event
        class Pair(val link: PairingLink, val result: CompletableDeferred<PairResult>) : Event
        class Forget(val done: CompletableDeferred<Unit>) : Event
    }

    /** Un essai en cours : une passe sur les adresses, pour ouvrir une session ou pour appairer. */
    private class Attempt(val candidates: List<String>, val deviceId: String, val pair: Event.Pair?, val single: Boolean) {
        var index = 0

        /** Change à chaque adresse : ce qui revient d'une adresse abandonnée est ignoré. */
        var id = 0
        var session: Session? = null
        var noReply: Job? = null
        var connected = false
        var sawWrongPc = false
        val host get() = candidates[index]

        /** L'adresse en cours pour le journal : son rang, jamais elle-même ni le nom du PC. */
        val rank get() = "adresse ${index + 1}"
    }

    private val events = Channel<Event>(Channel.UNLIMITED)

    // Tout ce qui suit n'est lu et écrit que par la coroutine qui vide `events`.
    private var pairing: Pairing? = null
    private var network = false
    private var attempt: Attempt? = null
    private var attemptIds = 0
    private var backoff = 0
    private var retry: Job? = null
    private var offlineSince: Long? = null

    /** État définitif tant qu'on n'a pas appairé de nouveau : téléphone refusé, versions incompatibles. */
    private var stopped: LinkState? = null

    /** Liaison suspendue ([setActive]) : aucune session, aucun essai. */
    private var paused = false

    /** La session ouverte, pour [send] (lu de n'importe quel fil). */
    @Volatile
    private var live: Session? = null

    init {
        scope.launch {
            pairing = try {
                store.load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.add(clock(), "appairage illisible : ${e.javaClass.simpleName}")
                null
            }
            rest()
            for (event in events) {
                // Une erreur imprévue (coffre de clés, réseau) ne doit pas arrêter la liaison, ni
                // l'appli avec elle : l'essai en cours est lâché, la boucle continue.
                try {
                    handle(event)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    recover(event, e)
                }
            }
        }
    }

    private fun recover(event: Event, error: Exception) {
        log.add(clock(), "erreur : ${error.javaClass.simpleName}")
        attempt?.pair?.result?.complete(PairResult.Error)
        (event as? Event.Pair)?.result?.complete(PairResult.Error)
        (event as? Event.Forget)?.done?.complete(Unit)
        abort()
        rest()
    }

    /**
     * Suspend la liaison (`false` : session fermée, plus aucun essai) ou la reprend. Suspendue
     * quand rien ne doit tourner : « Arrêter » dans la notification, appli fermée en mode « à
     * l'ouverture ». Sans cela la session resterait ouverte tant que le processus vit, et le PC
     * montrerait un téléphone « connecté » dont la batterie ne bouge plus.
     */
    fun setActive(active: Boolean) {
        events.trySend(Event.Active(active))
    }

    /** Le réseau local est là, ou ne l'est plus. À appeler aussi au démarrage, avec l'état du moment. */
    fun onNetwork(available: Boolean) {
        events.trySend(Event.Network(available))
    }

    fun onAppOpened() {
        events.trySend(Event.AppOpened)
    }

    /** Envoie `msg` si la session est ouverte. Sinon rend faux, et tente de la rouvrir (un essai). */
    fun send(msg: ToPc): Boolean {
        if (live?.send(msg) == true) return true
        events.trySend(Event.Kick)
        return false
    }

    /**
     * Ce que l'appli regarde (`media`, `volume`) : le PC n'envoie que cela, et seulement pendant
     * que l'appli le regarde. Envoyé tout de suite si la session est ouverte, et à chaque session.
     * Ne provoque aucun essai de connexion.
     */
    fun watch(topics: Set<String>) {
        synchronized(watchLock) {
            if (topics == watched) return
            watched = topics
            live?.send(ToPc.Watch(topics.sorted()))
        }
    }

    /** Appaire avec le PC du QR. Remplace l'appairage précédent en cas de succès seulement. */
    suspend fun pair(link: PairingLink): PairResult = CompletableDeferred<PairResult>().also { events.send(Event.Pair(link, it)) }.await()

    /** Oublie le PC : session fermée, appairage effacé. */
    suspend fun forget() {
        CompletableDeferred<Unit>().also { events.send(Event.Forget(it)) }.await()
    }

    private suspend fun handle(event: Event) {
        when (event) {
            is Event.Network -> {
                network = event.available
                if (event.available) series() else {
                    abort()
                    rest()
                }
            }
            Event.AppOpened -> series()
            is Event.Active -> if (event.active == paused) {
                paused = !event.active
                if (paused) abort()
                rest()
                series()
            }
            Event.Kick -> if (attempt == null && retry == null) round(single = true)
            Event.Retry -> {
                retry = null
                round(single = false)
            }
            is Event.Message -> attempt?.takeIf { it.id == event.attempt }?.let { message(it, event.msg) }
            is Event.Closed -> attempt?.takeIf { it.id == event.attempt }?.let { closed(it, event.error) }
            // Le PC a pris la connexion mais ne répond rien : on ferme, `closed` passe à la suite.
            is Event.NoReply -> attempt?.takeIf { it.id == event.attempt && !it.connected }?.session?.close()
            is Event.Pair -> {
                abort()
                val link = event.link
                // Les adresses du code seulement : un nom à résoudre pourrait mener ailleurs que sur le
                // réseau local, et le code vient d'être affiché par le PC, ses adresses sont fraîches.
                // L'identifiant de ce téléphone n'est repris que pour le même PC (même clé) : montré à
                // un autre, il servirait à se faire passer pour ce téléphone auprès du premier.
                val deviceId = pairing?.takeIf { it.fingerprint == link.fingerprint }?.deviceId ?: UUID.randomUUID().toString()
                start(Attempt(link.hosts.distinct(), deviceId, event, single = true))
            }
            is Event.Forget -> {
                abort()
                store.clear()
                pairing = null
                stopped = null
                offlineSince = null
                log.add(clock(), "PC oublié")
                rest()
                event.done.complete(Unit)
            }
        }
    }

    /** Lance une série, sauf si une session est ouverte ou qu'il n'y a rien à joindre. */
    private fun series() {
        if (attempt?.connected == true || attempt?.pair != null) return
        backoff = 0
        retry?.cancel()
        retry = null
        if (attempt == null) round(single = false)
    }

    /** Un essai de session avec le PC appairé. `single` : sans suite s'il échoue. */
    private fun round(single: Boolean) {
        val p = pairing ?: return
        if (paused || !network || stopped != null || attempt != null) return
        if (!single) current.value = LinkState.Connecting(p.pcName)
        // Le nom du PC ne sert d'adresse que s'il peut en être une.
        val byName = "${p.hostName}.local".takeIf { isHostLabel(p.hostName) }
        start(Attempt((listOfNotNull(p.lastHost) + p.hosts + listOfNotNull(byName)).distinct(), p.deviceId, pair = null, single))
    }

    private fun start(a: Attempt) {
        attempt = a
        open(a)
    }

    /** Ouvre l'adresse courante de l'essai et envoie le premier message ; plus d'adresse : l'essai a échoué. */
    private fun open(a: Attempt) {
        if (a.index >= a.candidates.size) return failed(a)
        val id = ++attemptIds
        a.id = id
        val pair = a.pair
        val listener = object : SessionListener {
            override fun onMessage(msg: FromPc) {
                events.trySend(Event.Message(id, msg))
            }

            override fun onClosed(error: Throwable?) {
                events.trySend(Event.Closed(id, error))
            }
        }
        val session = if (pair != null) {
            transport.open(a.host, pair.link.port, pair.link.fingerprint, listener).also { it.send(ToPc.PairRequest(pair.link.token, a.deviceId, deviceName(), appVersion)) }
        } else {
            val p = pairing ?: return failed(a)
            transport.open(a.host, p.port, p.fingerprint, listener).also { it.send(ToPc.Hello(p.deviceId, p.deviceToken, PROTOCOL, appVersion)) }
        }
        a.session = session
        a.noReply = scope.launch {
            delay(REPLY_TIMEOUT_MS)
            events.send(Event.NoReply(id))
        }
    }

    private suspend fun message(a: Attempt, msg: FromPc) {
        val pair = a.pair
        when {
            a.connected -> when (msg) {
                is FromPc.Accent -> {
                    val state = current.value
                    val seed = parseColor(msg.color)
                    if (state is LinkState.Connected && seed != null) current.value = state.copy(seed = seed)
                }
                is FromPc.Rights -> {
                    val state = current.value
                    if (state is LinkState.Connected) current.value = state.copy(granted = msg.granted.toSet())
                }
                FromPc.Unknown -> Unit
                else -> onPcMessage?.invoke(msg)
            }
            pair != null && msg is FromPc.PairAccepted -> {
                val link = pair.link
                // Le nom que donne le PC n'est gardé que s'il est montrable ; sinon celui du code.
                val name = msg.pcName.takeIf(::isPlainName) ?: link.name
                val p = Pairing(msg.pcId, name, link.hosts, link.name, link.port, link.fingerprint, a.deviceId, msg.deviceToken, lastHost = a.host)
                store.save(p)
                pairing = p
                stopped = null
                offlineSince = null
                // Jamais le nom du PC dans le journal : il part dans le rapport de diagnostic.
                log.add(clock(), "appairé")
                // La session d'appairage ne dit ni les fonctions ni la couleur du PC : on la ferme
                // et on en ouvre une vraie, avec le jeton reçu.
                release(a)
                pair.result.complete(PairResult.Paired(name))
                rest()
                series()
            }
            pair != null && msg is FromPc.PairRejected -> {
                log.add(clock(), "appairage refusé : ${known(msg.reason, "expired", "used", "unknown")}")
                release(a)
                pair.result.complete(PairResult.Refused(msg.reason))
                // Le PC déjà relié, s'il y en a un, revient.
                rest()
                series()
            }
            pair == null && msg is FromPc.HelloOk -> connected(a, msg)
            pair == null && msg is FromPc.HelloRejected -> {
                val p = pairing ?: return
                log.add(clock(), "refusé par le PC : ${known(msg.reason, "unknown_device", "bad_token", "protocol")}")
                release(a)
                stopped = when (msg.reason) {
                    "unknown_device", "bad_token" -> LinkState.Rejected(p.pcName)
                    // « protocol », ou une raison que cette version ne connaît pas.
                    else -> LinkState.Offline(p.pcName, OfflineReason.Incompatible, since())
                }
                rest()
            }
        }
    }

    private suspend fun connected(a: Attempt, ok: FromPc.HelloOk) {
        val p = pairing ?: return
        a.connected = true
        a.noReply?.cancel()
        retry?.cancel()
        retry = null
        backoff = 0
        offlineSince = null
        val name = ok.pcName.takeIf(::isPlainName) ?: p.pcName
        if (p.lastHost != a.host || p.pcName != name) {
            pairing = p.copy(lastHost = a.host, pcName = name).also { store.save(it) }
        }
        live = a.session
        // Un PC 0.8 ne dit pas les droits : tout ce qu'il sait faire est permis.
        current.value = LinkState.Connected(name, parseColor(ok.accent) ?: DEFAULT_ACCENT, ok.features.toSet(), (ok.granted ?: ok.features).toSet())
        log.add(clock(), "connecté (${a.rank})")
        synchronized(watchLock) {
            if (watched.isNotEmpty()) a.session?.send(ToPc.Watch(watched.sorted()))
        }
        onConnected?.invoke()
    }

    private fun closed(a: Attempt, error: Throwable?) {
        a.noReply?.cancel()
        if (a.connected) {
            attempt = null
            live = null
            offlineSince = clock()
            log.add(clock(), "liaison coupée : ${describe(error)}")
            if (network) series() else rest()
            return
        }
        if (error is WrongPc) a.sawWrongPc = true
        log.add(clock(), "${a.rank} : ${describe(error)}")
        a.index++
        open(a)
    }

    /** Aucune adresse n'a répondu. */
    private fun failed(a: Attempt) {
        attempt = null
        val pair = a.pair
        if (pair != null) {
            pair.result.complete(if (a.sawWrongPc) PairResult.WrongPc else PairResult.Unreachable)
            rest()
            return series()
        }
        val p = pairing ?: return rest()
        if (!a.single && backoff < BACKOFF_S.size) {
            val wait = BACKOFF_S[backoff++]
            retry = scope.launch {
                delay(wait * 1000L)
                events.send(Event.Retry)
            }
            return
        }
        val reason = if (a.sawWrongPc) OfflineReason.WrongPc else OfflineReason.Unreachable
        val offline = LinkState.Offline(p.pcName, reason, since())
        if (current.value != offline) log.add(clock(), if (reason == OfflineReason.WrongPc) "une autre machine répond à l'adresse du PC" else "PC injoignable")
        current.value = offline
    }

    /** Abandonne l'essai en cours et la série ; un appairage en cours échoue. */
    private fun abort() {
        retry?.cancel()
        retry = null
        val a = attempt ?: return
        if (a.connected) offlineSince = clock()
        release(a)
        a.pair?.result?.complete(PairResult.Unreachable)
    }

    /** Lâche l'essai : ce que sa session dira encore sera ignoré. */
    private fun release(a: Attempt) {
        attempt = null
        live = null
        a.noReply?.cancel()
        a.session?.close()
    }

    /** L'état quand rien n'est en cours. */
    private fun rest() {
        val p = pairing
        current.value = when {
            p == null -> LinkState.NotPaired
            stopped != null -> stopped!!
            paused -> LinkState.Offline(p.pcName, OfflineReason.Paused, since())
            !network -> LinkState.Offline(p.pcName, OfflineReason.NoNetwork, since())
            else -> LinkState.Offline(p.pcName, OfflineReason.Unreachable, since())
        }
    }

    /** Depuis quand la liaison est tombée (maintenant, si on ne le savait pas). */
    private fun since(): Long = offlineSince ?: clock().also { offlineSince = it }

    /**
     * Une erreur pour le journal : son genre seulement. Son message répète souvent l'adresse ou le
     * nom du PC, et le journal part dans le rapport de diagnostic.
     */
    private fun describe(error: Throwable?): String = when (error) {
        null -> "fermée"
        is WrongPc -> "ce n'est pas le PC appairé"
        else -> error.javaClass.simpleName
    }

    /** Une raison donnée par le PC, pour le journal : telle quelle si elle est connue, jamais un texte libre. */
    private fun known(reason: String, vararg reasons: String): String = if (reason in reasons) reason else "autre raison"

    private companion object {
        /** Temps laissé au PC pour répondre au premier message, connexion comprise. */
        const val REPLY_TIMEOUT_MS = 10_000L
    }
}
