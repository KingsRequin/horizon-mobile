package fr.kingsrequin.horizon.remote

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.kingsrequin.horizon.R
import fr.kingsrequin.horizon.connection.Link
import fr.kingsrequin.horizon.design.HorizonIcons
import fr.kingsrequin.horizon.design.R as DesignR
import fr.kingsrequin.horizon.design.HorizonShapes
import fr.kingsrequin.horizon.design.HorizonSize
import fr.kingsrequin.horizon.design.HorizonSpace
import fr.kingsrequin.horizon.design.HorizonTheme
import fr.kingsrequin.horizon.design.HorizonType
import fr.kingsrequin.horizon.design.LocalHorizonColors
import fr.kingsrequin.horizon.design.components.BoutonSortie
import fr.kingsrequin.horizon.design.components.GlissiereVolume
import fr.kingsrequin.horizon.design.components.Groupe
import fr.kingsrequin.horizon.design.components.LigneMixeur
import fr.kingsrequin.horizon.design.components.LigneReglage
import fr.kingsrequin.horizon.design.components.Pochette
import fr.kingsrequin.horizon.design.components.Progression
import fr.kingsrequin.horizon.design.components.Transport
import fr.kingsrequin.horizon.link.MediaNow
import fr.kingsrequin.horizon.link.MixerNow
import fr.kingsrequin.horizon.link.RemoteView
import fr.kingsrequin.horizon.link.positionAt
import fr.kingsrequin.horizon.protocol.AppInfo
import fr.kingsrequin.horizon.protocol.OutputInfo
import fr.kingsrequin.horizon.protocol.SessionInfo
import fr.kingsrequin.horizon.protocol.ToPc
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.conflate

/** Ce que la télécommande demande au PC. */
class RemoteActions(
    val playPause: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val seek: (Long) -> Unit,
    val select: (String) -> Unit,
    val volume: (Float) -> Unit,
    val mute: (Boolean) -> Unit,
    val appVolume: (String, Float) -> Unit = { _, _ -> },
    val appMute: (String, Boolean) -> Unit = { _, _ -> },
    val output: (String) -> Unit = {},
    val pad: () -> Unit = {},
)

/** Pause entre deux envois du volume pendant un glissé : la valeur finale part toujours. */
private const val VOLUME_EVERY_MS = 50L

/**
 * L'onglet Télécommande, branché sur la liaison : regarde le volume du PC tant qu'il est à
 * l'écran, fait avancer la position pendant la lecture et espace les envois du volume.
 */
@Composable
fun RemoteRoute(connected: Boolean, access: Access, sound: Access, input: Access, onPad: () -> Unit, modifier: Modifier = Modifier) {
    val view by Link.remote.view.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        Link.watch("volume", true)
        Link.watch("mixer", true)
        Link.volumeKeysOnPc = true
        onPauseOrDispose {
            Link.volumeKeysOnPc = false
            Link.watch("mixer", false)
            Link.watch("volume", false)
        }
    }
    val playing = view.media?.playing == true
    val now by produceState(System.currentTimeMillis(), playing) {
        value = System.currentTimeMillis()
        while (playing) {
            delay(500)
            value = System.currentTimeMillis()
        }
    }
    val volumes = remember { MutableSharedFlow<Float>(extraBufferCapacity = 1) }
    LaunchedEffect(Unit) {
        volumes.conflate().collect {
            Link.command(ToPc.VolumeSet(it))
            delay(VOLUME_EVERY_MS)
        }
    }
    // Jauges des applis : on n'en règle qu'une à la fois, la dernière valeur part toujours.
    val appVolumes = remember { MutableSharedFlow<Pair<String, Float>>(extraBufferCapacity = 1) }
    LaunchedEffect(Unit) {
        appVolumes.conflate().collect { (key, volume) ->
            Link.command(ToPc.MixerAppVolume(key, volume))
            delay(VOLUME_EVERY_MS)
        }
    }
    val actions = remember {
        RemoteActions(
            playPause = { Link.command(ToPc.MediaCommand("play_pause")) },
            next = { Link.command(ToPc.MediaCommand("next")) },
            previous = { Link.command(ToPc.MediaCommand("previous")) },
            seek = { Link.command(ToPc.MediaSeek(it)) },
            select = { Link.command(ToPc.MediaSelect(it)) },
            volume = {
                Link.remote.localVolume(it)
                volumes.tryEmit(it)
            },
            mute = { Link.command(ToPc.VolumeMute(it)) },
            appVolume = { key, volume ->
                Link.remote.localAppVolume(key, volume)
                appVolumes.tryEmit(key to volume)
            },
            appMute = { key, muted -> Link.command(ToPc.MixerAppMute(key, muted)) },
            output = { Link.command(ToPc.MixerOutput(it)) },
            pad = onPad,
        )
    }
    RemoteScreen(view, rememberCover(view.art), connected, access, now, actions, modifier, sound, input)
}

/** La pochette reçue, décodée une fois par image. */
@Composable
fun rememberCover(art: ByteArray?): ImageBitmap? = remember(art) { art?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }

/**
 * La télécommande : la sortie audio en haut, grande pochette, titre, artiste, choix du lecteur
 * s'il y en a plusieurs, progression, puis, dans la moitié basse, les commandes et le volume
 * (toujours visible), et sous lui le volume de chaque appli. `access` à accorder : seulement où
 * l'accorder ; `sound` (mixeur et sortie) et `input` (le bouton du clavier et de la souris, en
 * haut à droite) ont leur propre accès.
 */
@Composable
fun RemoteScreen(
    view: RemoteView,
    cover: ImageBitmap?,
    connected: Boolean,
    access: Access,
    nowMs: Long,
    actions: RemoteActions,
    modifier: Modifier = Modifier,
    sound: Access = Access.Hidden,
    input: Access = Access.Hidden,
) {
    val colors = LocalHorizonColors.current
    val mixer = view.mixer.takeIf { sound == Access.Granted }
    var choosingOutput by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxSize().background(colors.surface).statusBarsPadding()) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(horizontal = HorizonSpace.s4),
            verticalArrangement = Arrangement.spacedBy(HorizonSpace.s4),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(HorizonSpace.s6))
            val outputs = if (access == Access.Granted) mixer?.outputs.orEmpty() else emptyList()
            if (outputs.isNotEmpty() || input != Access.Hidden) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (mixer != null && outputs.isNotEmpty()) {
                        val current = outputs.firstOrNull { it.id == mixer.defaultOutput } ?: outputs.first()
                        BoutonSortie(
                            name = current.name,
                            headset = current.kind == "headset",
                            several = outputs.size > 1,
                            onNext = { nextOutput(outputs.map { it.id }, mixer.defaultOutput)?.let(actions.output) },
                            onChoose = { choosingOutput = true },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (input != Access.Hidden) PadButton(actions.pad)
                }
            }
            if (access != Access.Granted) {
                ToGrant()
                return@Column
            }
            val media = view.media
            Pochette(cover.takeIf { media != null }, 172.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HorizonSpace.s1)) {
                val title = when {
                    media != null -> media.title.ifBlank { media.appName }
                    connected -> stringResource(DesignR.string.media_nothing)
                    else -> stringResource(R.string.remote_offline)
                }
                Text(title, style = HorizonType.headlineMedium, color = colors.onSurface, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (media != null && media.artist.isNotBlank()) {
                    Text(media.artist, style = HorizonType.bodyLarge, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (media != null) Player(media, view.sessions, actions.select)
            if (media != null) Progression(media.positionAt(nowMs), media.durationMs, media.playing, actions.seek)
            Spacer(Modifier.weight(1f))
            Transport(
                playing = media?.playing == true,
                canPrevious = true,
                canNext = true,
                enabled = media != null,
                onPrevious = actions.previous,
                onPlayPause = actions.playPause,
                onNext = actions.next,
            )
            GlissiereVolume(view.volume ?: 0f, view.muted, actions.volume, actions.mute, enabled = view.volume != null)
            if (mixer != null && mixer.apps.isNotEmpty()) {
                Groupe(Modifier.fillMaxWidth()) {
                    // Une clé par appli : quand l'ordre change (une appli se met à jouer et remonte),
                    // le geste en cours reste sur l'appli qu'il règle.
                    mixer.apps.forEach { app ->
                        key(app.key) {
                            LigneMixeur(app.name, app.volume, app.muted, { actions.appVolume(app.key, it) }, { actions.appMute(app.key, it) })
                        }
                    }
                }
            }
            if (sound == Access.ToGrant) {
                Text(stringResource(R.string.remote_sound_to_grant), style = HorizonType.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(HorizonSpace.s4))
        }
    }
    if (choosingOutput && mixer != null) {
        OutputSheet(mixer, onPick = {
            choosingOutput = false
            if (it != mixer.defaultOutput) actions.output(it)
        }, onDismiss = { choosingOutput = false })
    }
}

/** Ouvre le pavé (clavier et souris du PC). */
@Composable
private fun PadButton(onClick: () -> Unit) {
    val colors = LocalHorizonColors.current
    val label = stringResource(R.string.pad_open)
    Box(
        Modifier
            .size(HorizonSize.touchMin)
            .clip(HorizonShapes.full)
            .background(colors.surfaceContainerHigh)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(HorizonIcons.Keyboard), contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(HorizonSize.icon))
    }
}

/** La liste des sorties audio du PC, en feuille du bas ; la sortie en cours est cochée. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OutputSheet(mixer: MixerNow, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalHorizonColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surfaceContainerHigh) {
        Column(Modifier.padding(HorizonSpace.s4).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(HorizonSpace.s2)) {
            Text(stringResource(R.string.output_sheet_title), style = HorizonType.titleMedium, color = colors.onSurface, modifier = Modifier.padding(start = HorizonSpace.s2))
            Groupe {
                mixer.outputs.forEach { output ->
                    LigneReglage(output.name, selected = output.id == mixer.defaultOutput) { onPick(output.id) }
                }
            }
        }
    }
}

/** Le lecteur suivi : son nom seul, ou un choix quand le PC en pilote plusieurs. */
@Composable
private fun Player(media: MediaNow, sessions: List<SessionInfo>, onSelect: (String) -> Unit) {
    val colors = LocalHorizonColors.current
    if (sessions.size < 2) {
        Text(media.appName, style = HorizonType.labelMedium, color = colors.onSurfaceVariant)
        return
    }
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(HorizonShapes.full)
                .background(colors.surfaceContainerHigh)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.remote_choose_player)) { open = true }
                .heightIn(min = HorizonSize.touchMin)
                .padding(horizontal = HorizonSpace.s4),
            horizontalArrangement = Arrangement.spacedBy(HorizonSpace.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(HorizonIcons.List), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Text(media.appName, style = HorizonType.labelLarge, color = colors.onSurface)
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            sessions.forEach { session ->
                DropdownMenuItem(
                    text = { Text(session.name, style = HorizonType.bodyLarge) },
                    onClick = {
                        open = false
                        if (session.appId != media.appId) onSelect(session.appId)
                    },
                )
            }
        }
    }
}

/** La télécommande existe sur le PC mais n'est pas accordée à ce téléphone : où l'accorder. */
@Composable
private fun ToGrant() {
    val colors = LocalHorizonColors.current
    Column(
        Modifier.fillMaxWidth().clip(HorizonShapes.extraLarge).background(colors.surfaceContainer).padding(HorizonSpace.s4),
        verticalArrangement = Arrangement.spacedBy(HorizonSpace.s3),
    ) {
        Text(stringResource(R.string.remote_to_grant), style = HorizonType.titleMedium, color = colors.onSurface)
        Text(stringResource(R.string.remote_to_grant_detail), style = HorizonType.bodyMedium, color = colors.onSurfaceVariant)
    }
}

private val previewActions = RemoteActions({}, {}, {}, {}, {}, {}, {})

private fun song(playing: Boolean = true, duration: Long = 233_000) = MediaNow("Spotify.exe", "Spotify", "FROGZ", "SPLATA", playing, 68_000, duration, null, 0)

private val twoPlayers = listOf(SessionInfo("Spotify.exe", "Spotify"), SessionInfo("Chrome", "Google Chrome"))

private val previewMixer = MixerNow(
    listOf(OutputInfo("o1", "Casque", "headset"), OutputInfo("o2", "Enceintes", "speaker")),
    "o1",
    listOf(AppInfo("spotify.exe", "Spotify", 0.8f, false, true), AppInfo("discord.exe", "Discord", 0.55f, true, false)),
)

@Composable
private fun RemotePreview(view: RemoteView, access: Access = Access.Granted, connected: Boolean = true, seed: Int = 0xFFF97316.toInt(), sound: Access = Access.Granted) = HorizonTheme(seed) {
    RemoteScreen(view, null, connected, access, 0, previewActions, sound = sound, input = Access.Granted)
}

@Preview(name = "En lecture, deux lecteurs, mixeur", widthDp = 360, heightDp = 1000)
@Composable
private fun RemotePlaying() = RemotePreview(RemoteView(song(), twoPlayers, null, 0.72f, false, previewMixer))

@Preview(name = "Son et actions à autoriser", widthDp = 360, heightDp = 800)
@Composable
private fun RemoteSoundToGrant() = RemotePreview(RemoteView(song(), twoPlayers, null, 0.72f, false), sound = Access.ToGrant)

@Preview(name = "En pause, violet", widthDp = 360, heightDp = 740)
@Composable
private fun RemotePaused() = RemotePreview(RemoteView(song(playing = false), twoPlayers.take(1), null, 0.4f, true), seed = 0xFFA06CD5.toInt())

@Preview(name = "Rien en lecture", widthDp = 360, heightDp = 740)
@Composable
private fun RemoteNothing() = RemotePreview(RemoteView(volume = 0.72f))

@Preview(name = "En direct", widthDp = 360, heightDp = 740)
@Composable
private fun RemoteLive() = RemotePreview(RemoteView(song(duration = 0), emptyList(), null, 0.72f, false))

@Preview(name = "Hors ligne", widthDp = 360, heightDp = 740)
@Composable
private fun RemoteOffline() = RemotePreview(RemoteView(), connected = false)

@Preview(name = "À autoriser", widthDp = 360, heightDp = 740)
@Composable
private fun RemoteToGrant() = RemotePreview(RemoteView(), access = Access.ToGrant)

@Preview(name = "Police à 200 %", widthDp = 360, heightDp = 740, fontScale = 2f)
@Composable
private fun RemoteLarge() = RemotePreview(RemoteView(song(), twoPlayers, null, 0.72f, false))
