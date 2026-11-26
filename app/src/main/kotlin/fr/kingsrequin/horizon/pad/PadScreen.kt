package fr.kingsrequin.horizon.pad

import android.content.Context
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import fr.kingsrequin.horizon.R
import fr.kingsrequin.horizon.connection.Link
import fr.kingsrequin.horizon.design.HorizonIcons
import fr.kingsrequin.horizon.design.HorizonShapes
import fr.kingsrequin.horizon.design.HorizonSize
import fr.kingsrequin.horizon.design.HorizonSpace
import fr.kingsrequin.horizon.design.HorizonTheme
import fr.kingsrequin.horizon.design.HorizonType
import fr.kingsrequin.horizon.design.LocalHorizonColors
import fr.kingsrequin.horizon.design.components.Groupe
import fr.kingsrequin.horizon.design.components.LigneReglage
import fr.kingsrequin.horizon.link.pad.Button
import fr.kingsrequin.horizon.link.pad.Finger
import fr.kingsrequin.horizon.link.pad.Mod
import fr.kingsrequin.horizon.link.pad.PadAction
import fr.kingsrequin.horizon.link.pad.PadGestures
import fr.kingsrequin.horizon.link.pad.PadSender
import fr.kingsrequin.horizon.link.pad.PadSettings
import fr.kingsrequin.horizon.link.pad.Typing
import fr.kingsrequin.horizon.protocol.ToPc
import fr.kingsrequin.horizon.remote.Access
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PREFS = "pave"

/** Caractère invisible en tête du champ de saisie : l'effacer, c'est un retour arrière sur le PC. */
private const val SENTINEL = "​"

/** Au-delà, le champ caché repart de zéro (il n'a pas à garder tout ce qui a été tapé). */
private const val FIELD_MAX = 200

private fun loadSettings(context: Context): PadSettings = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).run {
    val d = PadSettings()
    PadSettings(getFloat("pointeur", d.pointerSpeed), getFloat("defilement", d.scrollSpeed), getBoolean("naturel", d.naturalScroll), getBoolean("boutons", d.buttons))
}

private fun saveSettings(context: Context, s: PadSettings) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
    putFloat("pointeur", s.pointerSpeed)
    putFloat("defilement", s.scrollSpeed)
    putBoolean("naturel", s.naturalScroll)
    putBoolean("boutons", s.buttons)
}

/** Ce que l'écran du pavé demande : gestes, boutons, messages du clavier, réglages, fermeture. */
class PadActions(
    val touch: (List<Finger>, Long) -> Unit,
    val button: (Button, Boolean) -> Unit,
    val send: (List<ToPc>) -> Unit,
    val settings: (PadSettings) -> Unit,
    val close: () -> Unit,
)

/**
 * Le pavé branché sur la liaison : gestes reconnus ([PadGestures]), envoyés regroupés
 * ([PadSender]) ; un bouton tenu est relâché quand l'écran se ferme.
 */
@Composable
fun PadRoute(connected: Boolean, access: Access, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(loadSettings(context)) }
    val slop = LocalViewConfiguration.current.touchSlop
    val gestures = remember { PadGestures(settings, slop) }
    SideEffect { gestures.settings = settings }
    val scope = rememberCoroutineScope()
    val sender = remember { PadSender { Link.command(it) } }
    val flushing = remember { arrayOfNulls<Job>(1) }
    fun act(actions: List<PadAction>) {
        val now = SystemClock.uptimeMillis()
        actions.forEach { sender.act(it, now) }
        // Ce qui attend part au plus tard un intervalle plus tard, puis la boucle s'arrête.
        if (sender.pending && flushing[0]?.isActive != true) {
            flushing[0] = scope.launch {
                while (sender.pending) {
                    delay(16)
                    sender.flush(SystemClock.uptimeMillis())
                }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            val now = SystemClock.uptimeMillis()
            gestures.cancel().forEach { sender.act(it, now) }
            sender.sendNow(now)
        }
    }
    BackHandler(onBack = onClose)
    val actions = remember {
        PadActions(
            touch = { fingers, time -> act(gestures.touch(fingers, time)) },
            button = { button, down -> act(listOf(if (down) PadAction.Down(button) else PadAction.Up(button))) },
            send = { messages ->
                sender.sendNow(SystemClock.uptimeMillis())
                messages.forEach(Link::command)
            },
            settings = {
                settings = it
                saveSettings(context, it)
            },
            close = onClose,
        )
    }
    PadScreen(settings, connected, access, actions, modifier)
}

/**
 * Le pavé, plein écran : en haut, fermer, le clavier et les réglages ; la surface tactile ;
 * les boutons gauche et droit ; avec le clavier, deux rangées de touches au-dessus du clavier du
 * téléphone. Sans le droit : seulement où l'accorder.
 */
@Composable
fun PadScreen(settings: PadSettings, connected: Boolean, access: Access, actions: PadActions, modifier: Modifier = Modifier) {
    val colors = LocalHorizonColors.current
    var typing by rememberSaveable { mutableStateOf(false) }
    var tuning by rememberSaveable { mutableStateOf(false) }
    var mods by remember { mutableStateOf(emptySet<Mod>()) }
    val granted = access == Access.Granted
    Column(
        modifier.fillMaxSize().background(colors.surface).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = HorizonSpace.s4),
        verticalArrangement = Arrangement.spacedBy(HorizonSpace.s3),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = HorizonSpace.s2), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(HorizonIcons.X, stringResource(R.string.pad_close), onClick = actions.close)
            Text(stringResource(R.string.pad_title), style = HorizonType.titleMedium, color = colors.onSurface, modifier = Modifier.weight(1f).padding(start = HorizonSpace.s2))
            if (granted) {
                RoundIcon(HorizonIcons.Keyboard, stringResource(R.string.pad_keyboard), selected = typing) { typing = !typing }
                RoundIcon(HorizonIcons.SlidersHorizontal, stringResource(R.string.pad_settings)) { tuning = true }
            }
        }
        if (!granted) {
            ToGrant()
            return@Column
        }
        TouchSurface(connected, actions.touch, Modifier.weight(1f))
        if (settings.buttons) {
            Row(Modifier.fillMaxWidth().height(HorizonSize.row), horizontalArrangement = Arrangement.spacedBy(HorizonSpace.s1)) {
                HoldButton(stringResource(R.string.pad_left)) { actions.button(Button.LEFT, it) }
                HoldButton(stringResource(R.string.pad_right)) { actions.button(Button.RIGHT, it) }
            }
        }
        if (typing) {
            Keys(mods, onMods = { mods = it }) { name, keyMods ->
                actions.send(listOf(Typing.key(name, keyMods)))
                mods = emptySet()
            }
            HiddenField(mods) { messages, typed ->
                actions.send(messages)
                if (typed) mods = emptySet()
            }
        }
        if (!typing) Box(Modifier.height(HorizonSpace.s1))
    }
    if (tuning) SettingsSheet(settings, actions.settings) { tuning = false }
}

/** La surface tactile : chaque changement de doigts part vers [PadGestures]. */
@Composable
private fun TouchSurface(connected: Boolean, onTouch: (List<Finger>, Long) -> Unit, modifier: Modifier) {
    val colors = LocalHorizonColors.current
    var touched by rememberSaveable { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxWidth()
            .clip(HorizonShapes.extraLarge)
            .background(colors.surfaceContainer)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.filter { it.pressed }.map { Finger(it.id.value, it.position.x, it.position.y) }
                        event.changes.forEach { it.consume() }
                        if (fingers.isNotEmpty()) touched = true
                        onTouch(fingers, event.changes.first().uptimeMillis)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val hint = when {
            !connected -> stringResource(R.string.remote_offline)
            !touched -> stringResource(R.string.pad_hint)
            else -> null
        }
        if (hint != null) {
            Text(hint, style = HorizonType.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(HorizonSpace.s6))
        }
    }
}

/** Un bouton de la souris : enfoncé tant que le doigt reste dessus (glisser possible). */
@Composable
private fun RowScope.HoldButton(label: String, onPress: (Boolean) -> Unit) {
    val colors = LocalHorizonColors.current
    var down by remember { mutableStateOf(false) }
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(HorizonShapes.large)
            .background(if (down) colors.primaryContainer else colors.surfaceContainerHigh)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    down = true
                    onPress(true)
                    tryAwaitRelease()
                    down = false
                    onPress(false)
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = HorizonType.labelLarge, color = if (down) colors.onPrimaryContainer else colors.onSurfaceVariant)
    }
}

/** Touches nommées : libellé et nom dans le protocole. */
private data class KeyDef(val label: String, val name: String, val description: String? = null)

/**
 * Deux rangées de touches qui défilent : Échap, Tab, modificateurs (collants pour la touche
 * suivante), flèches, Entrée, Effacer… puis les raccourcis courants et F1 à F12. `onKey` reçoit
 * la touche et les modificateurs à tenir (ceux du raccourci, sinon ceux qui sont collés).
 */
@Composable
private fun Keys(mods: Set<Mod>, onMods: (Set<Mod>) -> Unit, onKey: (String, Set<Mod>) -> Unit) {
    val first = listOf(
        KeyDef(stringResource(R.string.key_escape), "escape"),
        KeyDef(stringResource(R.string.key_tab), "tab"),
    )
    val after = listOf(
        KeyDef("←", "left", stringResource(R.string.key_left)),
        KeyDef("↑", "up", stringResource(R.string.key_up)),
        KeyDef("↓", "down", stringResource(R.string.key_down)),
        KeyDef("→", "right", stringResource(R.string.key_right)),
        KeyDef(stringResource(R.string.key_enter), "enter"),
        KeyDef("⌫", "backspace", stringResource(R.string.key_backspace)),
        KeyDef(stringResource(R.string.key_delete), "delete"),
        KeyDef(stringResource(R.string.key_home), "home"),
        KeyDef(stringResource(R.string.key_end), "end"),
        KeyDef(stringResource(R.string.key_page_up), "page_up"),
        KeyDef(stringResource(R.string.key_page_down), "page_down"),
    )
    val modLabels = listOf(
        Mod.CTRL to stringResource(R.string.key_ctrl),
        Mod.ALT to stringResource(R.string.key_alt),
        Mod.SHIFT to stringResource(R.string.key_shift),
        Mod.WIN to stringResource(R.string.key_win),
    )
    Column(verticalArrangement = Arrangement.spacedBy(HorizonSpace.s1)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HorizonSpace.s1)) {
            first.forEach { KeyChip(it.label, it.description) { onKey(it.name, mods) } }
            modLabels.forEach { (mod, label) ->
                KeyChip(label, selected = mod in mods) { onMods(if (mod in mods) mods - mod else mods + mod) }
            }
            after.forEach { KeyChip(it.label, it.description) { onKey(it.name, mods) } }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HorizonSpace.s1)) {
            Shortcut(stringResource(R.string.pad_copy), "c", Mod.CTRL, onKey)
            Shortcut(stringResource(R.string.pad_paste), "v", Mod.CTRL, onKey)
            Shortcut(stringResource(R.string.pad_cut), "x", Mod.CTRL, onKey)
            Shortcut(stringResource(R.string.pad_undo), "z", Mod.CTRL, onKey)
            Shortcut(stringResource(R.string.pad_select_all), "a", Mod.CTRL, onKey)
            Shortcut(stringResource(R.string.pad_switch_window), "tab", Mod.ALT, onKey)
            Shortcut(stringResource(R.string.pad_desktop), "d", Mod.WIN, onKey)
            (1..12).forEach { n -> KeyChip("F$n") { onKey("f$n", mods) } }
        }
    }
}

/** Un raccourci : son modificateur à lui, quels que soient ceux qui sont collés. */
@Composable
private fun Shortcut(label: String, key: String, mod: Mod, onKey: (String, Set<Mod>) -> Unit) {
    KeyChip(label) { onKey(key, setOf(mod)) }
}

@Composable
private fun KeyChip(label: String, description: String? = null, selected: Boolean = false, onClick: () -> Unit) {
    val colors = LocalHorizonColors.current
    Box(
        Modifier
            .clip(HorizonShapes.full)
            .background(if (selected) colors.primary else colors.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .heightIn(min = HorizonSize.touchMin)
            .widthIn(min = HorizonSize.touchMin)
            .padding(horizontal = HorizonSpace.s3),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = HorizonType.labelLarge, color = if (selected) colors.onPrimary else colors.onSurface)
    }
}

/**
 * Champ invisible qui reçoit le clavier du téléphone. Il commence par un caractère invisible :
 * l'effacer, c'est un retour arrière sur le PC ; il repart alors de lui. `onSend` reçoit les
 * messages et s'il y avait du texte tapé (le modificateur collant a servi).
 */
@Composable
private fun HiddenField(mods: Set<Mod>, onSend: (List<ToPc>, Boolean) -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val field = rememberTextFieldState(SENTINEL)
    val currentMods by rememberUpdatedState(mods)
    val send by rememberUpdatedState(onSend)
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    // Chaque changement du champ (frappe, correction, effacement) devient des messages.
    LaunchedEffect(field) {
        var before = field.text.toString()
        snapshotFlow { field.text.toString() }.collect { after ->
            val edit = Typing.edit(before, after)
            val messages = Typing.send(edit, currentMods)
            if (messages.isNotEmpty()) send(messages, edit.inserted.isNotEmpty())
            before = after
            if (!after.startsWith(SENTINEL) || after.length >= FIELD_MAX) {
                field.setTextAndPlaceCursorAtEnd(SENTINEL)
                before = SENTINEL
            }
        }
    }
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
    BasicTextField(
        state = field,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Text, imeAction = ImeAction.None),
        modifier = Modifier.size(1.dp).alpha(0f).focusRequester(focus),
    )
}

@Composable
private fun RoundIcon(icon: Int, description: String, selected: Boolean = false, onClick: () -> Unit) {
    val colors = LocalHorizonColors.current
    Box(
        Modifier
            .size(HorizonSize.touchMin)
            .clip(HorizonShapes.full)
            .background(if (selected) colors.primary else colors.surface)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = if (selected) colors.onPrimary else colors.onSurface, modifier = Modifier.size(HorizonSize.icon))
    }
}

/** Les réglages du pavé, en feuille du bas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(settings: PadSettings, onChange: (PadSettings) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalHorizonColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surfaceContainerHigh) {
        Column(Modifier.padding(HorizonSpace.s4).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(HorizonSpace.s3)) {
            Text(stringResource(R.string.pad_settings), style = HorizonType.titleMedium, color = colors.onSurface, modifier = Modifier.padding(start = HorizonSpace.s2))
            Speed(stringResource(R.string.pad_pointer_speed), settings.pointerSpeed, 0.5f..4f) { onChange(settings.copy(pointerSpeed = it)) }
            Speed(stringResource(R.string.pad_scroll_speed), settings.scrollSpeed, 1f..8f) { onChange(settings.copy(scrollSpeed = it)) }
            Groupe {
                LigneReglage(stringResource(R.string.pad_natural), detail = stringResource(R.string.pad_natural_detail), selected = settings.naturalScroll) {
                    onChange(settings.copy(naturalScroll = !settings.naturalScroll))
                }
                LigneReglage(stringResource(R.string.pad_buttons), detail = stringResource(R.string.pad_buttons_detail), selected = settings.buttons) {
                    onChange(settings.copy(buttons = !settings.buttons))
                }
            }
        }
    }
}

@Composable
private fun Speed(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val colors = LocalHorizonColors.current
    val state = remember(range) { SliderState(value, 0, range) }
    Column(Modifier.padding(horizontal = HorizonSpace.s2)) {
        Text(label, style = HorizonType.bodyLarge, color = colors.onSurface)
        Slider(
            state = state,
            onValueChange = {
                state.value = it
                onChange(it)
            },
            colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary, inactiveTrackColor = colors.surfaceContainerHighest),
        )
    }
}

/** Le PC sait faire, mais ce téléphone n'en a pas le droit : où l'accorder. */
@Composable
private fun ToGrant() {
    val colors = LocalHorizonColors.current
    Column(
        Modifier.fillMaxWidth().clip(HorizonShapes.extraLarge).background(colors.surfaceContainer).padding(HorizonSpace.s4),
        verticalArrangement = Arrangement.spacedBy(HorizonSpace.s3),
    ) {
        Text(stringResource(R.string.pad_to_grant), style = HorizonType.titleMedium, color = colors.onSurface)
        Text(stringResource(R.string.pad_to_grant_detail), style = HorizonType.bodyMedium, color = colors.onSurfaceVariant)
    }
}

private val previewActions = PadActions({ _, _ -> }, { _, _ -> }, {}, {}, {})

@Preview(name = "Pavé", widthDp = 360, heightDp = 760)
@Composable
private fun PadPreview() = HorizonTheme { PadScreen(PadSettings(), true, Access.Granted, previewActions) }

@Preview(name = "À autoriser", widthDp = 360, heightDp = 760)
@Composable
private fun PadToGrantPreview() = HorizonTheme { PadScreen(PadSettings(), true, Access.ToGrant, previewActions) }
