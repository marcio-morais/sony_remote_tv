@file:OptIn(ExperimentalMaterial3Api::class)

package app.sonyremote.tv.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.sonyremote.tv.SonyKey
import app.sonyremote.tv.TransportMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val Brand = Color(0xFFD93C2F)
private val Background = Color(0xFFF0F0F0)
private val Accent = Color(0xFF1A56DB)
private val PowerRed = Color(0xFFC0332A)
private val Ring = Color(0xFFE2E2E2)
private val Line = Color(0xFFCFCFCF)
private val Ink = Color(0xFF212121)
private val Muted = Color(0xFF5F6368)
private val Good = Color(0xFF1B7F3B)
private val Warning = Color(0xFF8A5A00)
private val Bad = Color(0xFFB3261E)
private val OkKey = Color(0xFF2B2F38)

private const val REPEAT_DELAY_MS = 450L
private const val REPEAT_INTERVAL_MS = 160L

@Composable
fun RemoteApp(viewModel: RemoteViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    // Opening (or coming back to) the app wakes the TV if it is in standby
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.wakeIfStandby() }

    Scaffold(
        containerColor = Background,
        topBar = {
            RemoteTopBar(
                title = state.settings.name.ifBlank { "Sony KDL-42W655A" },
                onSettings = viewModel::openSettings,
            )
        },
        bottomBar = {
            RemoteTabs(selected = pagerState.currentPage) { page ->
                scope.launch { pagerState.animateScrollToPage(page) }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            StatusLine(
                text = state.status,
                tone = state.tone,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 16.dp),
                    ) {
                        if (page == 0) {
                            RemotePage(onKey = viewModel::press)
                        } else {
                            NumbersPage(onKey = viewModel::press)
                        }
                    }
                }
            }
        }
    }

    if (state.showSettings) {
        SettingsSheet(state = state, viewModel = viewModel)
    }
}

@Composable
private fun RemoteTopBar(title: String, onSettings: () -> Unit) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        actions = {
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Conexão e histórico")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Brand,
            titleContentColor = Color.White,
            actionIconContentColor = Color.White,
        ),
    )
}

@Composable
private fun RemoteTabs(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = Color.White) {
        NavigationBarItem(
            selected = selected == 0,
            onClick = { onSelect(0) },
            icon = { Icon(Icons.Filled.SettingsRemote, contentDescription = null) },
            label = { Text("Controle") },
        )
        NavigationBarItem(
            selected = selected == 1,
            onClick = { onSelect(1) },
            icon = { Icon(Icons.Filled.Dialpad, contentDescription = null) },
            label = { Text("Números") },
        )
    }
}

@Composable
private fun StatusLine(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val color = when (tone) {
        Tone.IDLE -> Muted
        Tone.OK -> Good
        Tone.WARN -> Warning
        Tone.ERROR -> Bad
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(text, color = Muted, fontSize = 13.sp)
    }
}

// ---------------------------------------------------------------- pages

@Composable
private fun RemotePage(onKey: (SonyKey) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyButton(
                onPress = { onKey(SonyKey.INPUT) },
                modifier = Modifier.weight(1f),
                label = "Entrada",
            )
            KeyButton(
                onPress = { onKey(SonyKey.HOME) },
                modifier = Modifier.weight(1f),
                label = "Home",
                container = Accent,
                content = Color.White,
            )
            KeyButton(
                onPress = { onKey(SonyKey.SEN) },
                modifier = Modifier.weight(1f),
                label = "SEN",
                container = Accent,
                content = Color.White,
            )
            KeyButton(
                onPress = { onKey(SonyKey.POWER) },
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.PowerSettingsNew,
                description = "Ligar ou desligar",
                container = PowerRed,
                content = Color.White,
            )
        }

        DPad(onKey)
        AudioRow(onKey)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyButton(onPress = { onKey(SonyKey.OPTIONS) }, modifier = Modifier.weight(1f), label = "Opções")
            KeyButton(onPress = { onKey(SonyKey.BACK) }, modifier = Modifier.weight(1f), label = "Voltar")
            KeyButton(onPress = { onKey(SonyKey.EXIT) }, modifier = Modifier.weight(1f), label = "Sair")
            KeyButton(onPress = { onKey(SonyKey.JUMP) }, modifier = Modifier.weight(1f), label = "Jump")
        }
    }
}

@Composable
private fun NumbersPage(onKey: (SonyKey) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { digits ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    digits.forEach { digit ->
                        DigitKey(digit, onKey, Modifier.weight(1f))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyButton(
                    onPress = { onKey(SonyKey.INFO) },
                    modifier = Modifier.weight(1f),
                    label = "Info",
                    height = 56.dp,
                )
                DigitKey(0, onKey, Modifier.weight(1f))
                KeyButton(
                    onPress = { onKey(SonyKey.BACK) },
                    modifier = Modifier.weight(1f),
                    label = "Voltar",
                    height = 56.dp,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorKey(SonyKey.RED, "Vermelho", Color(0xFFC0332A), onKey, Modifier.weight(1f))
            ColorKey(SonyKey.GREEN, "Verde", Color(0xFF1B7F3B), onKey, Modifier.weight(1f))
            ColorKey(SonyKey.YELLOW, "Amarelo", Color(0xFFF2C200), onKey, Modifier.weight(1f))
            ColorKey(SonyKey.BLUE, "Azul", Color(0xFF1A56DB), onKey, Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MediaKey(Icons.Filled.FastRewind, "Retroceder", SonyKey.REWIND, onKey, Modifier.weight(1f))
            MediaKey(Icons.Filled.PlayArrow, "Reproduzir", SonyKey.PLAY, onKey, Modifier.weight(1f))
            MediaKey(Icons.Filled.Pause, "Pausar", SonyKey.PAUSE, onKey, Modifier.weight(1f))
            MediaKey(Icons.Filled.Stop, "Parar", SonyKey.STOP, onKey, Modifier.weight(1f))
            MediaKey(Icons.Filled.FastForward, "Avançar", SonyKey.FORWARD, onKey, Modifier.weight(1f))
        }

        AudioRow(onKey)
    }
}

@Composable
private fun DigitKey(digit: Int, onKey: (SonyKey) -> Unit, modifier: Modifier) {
    KeyButton(
        onPress = { onKey(SonyKey.valueOf("NUM$digit")) },
        modifier = modifier,
        label = digit.toString(),
        height = 56.dp,
        fontSize = 20.sp,
    )
}

@Composable
private fun ColorKey(key: SonyKey, description: String, color: Color, onKey: (SonyKey) -> Unit, modifier: Modifier) {
    KeyButton(
        onPress = { onKey(key) },
        modifier = modifier,
        description = description,
        container = color,
        outline = false,
    )
}

@Composable
private fun MediaKey(icon: ImageVector, description: String, key: SonyKey, onKey: (SonyKey) -> Unit, modifier: Modifier) {
    KeyButton(
        onPress = { onKey(key) },
        modifier = modifier,
        icon = icon,
        description = description,
    )
}

@Composable
private fun DPad(onKey: (SonyKey) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .clip(CircleShape)
                .background(Ring)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ArrowKey(Icons.Filled.KeyboardArrowUp, "Cima", SonyKey.UP, onKey)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArrowKey(Icons.Filled.KeyboardArrowLeft, "Esquerda", SonyKey.LEFT, onKey)
                KeyButton(
                    onPress = { onKey(SonyKey.CONFIRM) },
                    modifier = Modifier.width(64.dp),
                    label = "OK",
                    description = "Confirmar",
                    container = OkKey,
                    content = Color.White,
                    shape = CircleShape,
                    height = 64.dp,
                    fontSize = 16.sp,
                    outline = false,
                )
                ArrowKey(Icons.Filled.KeyboardArrowRight, "Direita", SonyKey.RIGHT, onKey)
            }
            ArrowKey(Icons.Filled.KeyboardArrowDown, "Baixo", SonyKey.DOWN, onKey)
        }
    }
}

@Composable
private fun ArrowKey(icon: ImageVector, description: String, key: SonyKey, onKey: (SonyKey) -> Unit) {
    KeyButton(
        onPress = { onKey(key) },
        modifier = Modifier.width(64.dp),
        icon = icon,
        description = description,
        shape = CircleShape,
        height = 64.dp,
        repeat = true,
    )
}

@Composable
private fun AudioRow(onKey: (SonyKey) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Spacer(Modifier.height(16.dp))
            KeyButton(
                onPress = { onKey(SonyKey.MUTE) },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Filled.VolumeOff,
                description = "Mudo",
            )
        }
        RockerGroup("VOL", "volume", SonyKey.VOLUME_DOWN, SonyKey.VOLUME_UP, onKey, Modifier.weight(2f))
        RockerGroup("CH", "canal", SonyKey.CHANNEL_DOWN, SonyKey.CHANNEL_UP, onKey, Modifier.weight(2f))
    }
}

@Composable
private fun RockerGroup(
    label: String,
    name: String,
    down: SonyKey,
    up: SonyKey,
    onKey: (SonyKey) -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp),
            textAlign = TextAlign.Center,
            color = Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            KeyButton(
                onPress = { onKey(down) },
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Remove,
                description = "Diminuir $name",
                repeat = true,
            )
            KeyButton(
                onPress = { onKey(up) },
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Add,
                description = "Aumentar $name",
                repeat = true,
            )
        }
    }
}

// ---------------------------------------------------------------- key button

/**
 * A remote key. A tap fires on release, so a swipe that starts on a key (the pager takes
 * the gesture over) never presses it. With [repeat], holding the key keeps firing it.
 */
@Composable
private fun KeyButton(
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
    description: String? = label,
    container: Color = Color.White,
    content: Color = Ink,
    shape: Shape = RoundedCornerShape(24.dp),
    height: Dp = 48.dp,
    fontSize: TextUnit = 13.sp,
    repeat: Boolean = false,
    outline: Boolean = true,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val currentPress by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }

    fun fire() {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        currentPress()
    }

    Box(
        modifier = modifier
            .height(height)
            .alpha(if (pressed) 0.7f else 1f)
            .shadow(1.dp, shape)
            .clip(shape)
            .background(container)
            .then(if (outline) Modifier.border(1.dp, Line, shape) else Modifier)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (description != null) contentDescription = description
                onClick {
                    fire()
                    true
                }
            }
            .pointerInput(repeat) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        held = false
                        val job = if (repeat) {
                            scope.launch {
                                delay(REPEAT_DELAY_MS)
                                held = true
                                while (isActive) {
                                    fire()
                                    delay(REPEAT_INTERVAL_MS)
                                }
                            }
                        } else {
                            null
                        }
                        tryAwaitRelease()
                        job?.cancel()
                        pressed = false
                    },
                    onTap = {
                        if (!held) fire()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content)
        } else if (label != null) {
            Text(
                text = label,
                color = content,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------- settings

@Composable
private fun SettingsSheet(state: UiState, viewModel: RemoteViewModel) {
    ModalBottomSheet(
        onDismissRequest = viewModel::closeSettings,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Conexão", style = MaterialTheme.typography.titleLarge, color = Ink)
            val context = LocalContext.current
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                    .getOrNull().orEmpty()
            }
            if (version.isNotBlank()) Text("Versão $version", color = Muted, fontSize = 13.sp)

            OutlinedButton(onClick = viewModel::discover, enabled = !state.discovering) {
                Text(if (state.discovering) "Buscando..." else "Buscar TV na rede")
            }
            state.discoverMessage?.let { Text(it, color = Muted, fontSize = 13.sp) }

            state.devices.forEach { device ->
                OutlinedButton(
                    onClick = { viewModel.choose(device) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(device.name, fontWeight = FontWeight.SemiBold)
                        if (device.model.isNotBlank()) {
                            Text(device.model, color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.settings.ip,
                    onValueChange = viewModel::setIp,
                    label = { Text("IP da TV") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = state.settings.psk,
                    onValueChange = viewModel::setPsk,
                    label = { Text("Chave PSK") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = state.settings.mac,
                onValueChange = viewModel::setMac,
                label = { Text("MAC da TV (para ligar)") },
                supportingText = { Text("Ex.: A4:17:31:A6:52:42. Usado para ligar a TV em standby.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Como enviar os comandos", fontWeight = FontWeight.SemiBold, color = Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransportMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.settings.mode == mode,
                        onClick = { viewModel.setMode(mode) },
                        enabled = mode != TransportMode.IR || state.irAvailable,
                        label = { Text(mode.label) },
                    )
                }
            }
            if (!state.irAvailable) {
                Text(
                    "Este celular não tem emissor infravermelho, então o app envia por Wi-Fi.",
                    color = Muted,
                    fontSize = 13.sp,
                )
            }
            Text(
                "Na TV: Configurações > Rede > Controle IP, com autenticação \"Normal e Pre-Shared Key\" e a mesma chave acima.",
                color = Muted,
                fontSize = 13.sp,
            )

            Text(
                "Últimos comandos",
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (state.log.isEmpty()) {
                Text("Aperte uma tecla para ver o retorno da TV.", color = Muted, fontSize = 13.sp)
            } else {
                state.log.forEach { entry -> LogRow(entry) }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(entry.time, color = Muted, fontSize = 12.sp, modifier = Modifier.width(64.dp))
        Text(entry.key, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(96.dp))
        Text(
            text = if (entry.ok) "OK · ${entry.detail}" else "Falhou · ${entry.detail}",
            color = if (entry.ok) Good else Warning,
            fontSize = 12.sp,
        )
    }
}
