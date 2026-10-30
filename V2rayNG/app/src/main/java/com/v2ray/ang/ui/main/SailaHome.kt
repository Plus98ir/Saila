package com.v2ray.ang.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.handler.CountryFlag
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.glass
import com.v2ray.ang.ui.compose.sailaMenuBorder
import com.v2ray.ang.ui.compose.sailaMenuColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.v2ray.ang.handler.UpdateCheckerManager
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val HealthGood = Color(0xFF22C55E)
private val HealthBad = Color(0xFFEF4444)
/** Second stop of the accent gradients: the same hue, a bit deeper. */
private fun deeper(c: Color) = lerp(c, Color.Black, 0.22f)
private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/** Health of the running connection as shown next to the server name. */
private enum class Health { Unknown, Checking, Good, Bad }

/**
 * Saila's home: connection status on top, a living particle orb around the connect button (with
 * the connection timer inside while connected), the current server with a big Connect/Disconnect
 * button, live upload/download, the subscription's remaining traffic/days and a floating nav bar.
 */
@Composable
fun SailaHome(
    uiState: MainUiState,
    onAction: (MainAction) -> Unit,
    onMenuClick: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUpdate: () -> Unit,
    onRefreshSelected: () -> Unit,
) {
    val isRunning = uiState.isRunning
    val profile = remember(uiState.selectedGuid) {
        uiState.selectedGuid?.let { MmkvManager.decodeServerConfig(it) }
    }
    val serverName = profile?.remarks
    val serverHost = profile?.server
    val status = uiState.status
    val health = when {
        !isRunning -> Health.Unknown
        status is MainStatus.Testing -> Health.Checking
        status is MainStatus.ConnectionTest && status.result.delayMillis >= 0 -> Health.Good
        status is MainStatus.ConnectionTest -> Health.Bad
        else -> Health.Unknown
    }
    val pingMs = (status as? MainStatus.ConnectionTest)?.result?.delayMillis?.takeIf { it >= 0 }

    // Check the connection right after connecting, then keep the shown server in sync with
    // auto-switch, which changes the selected server from the service side.
    LaunchedEffect(isRunning) {
        if (!isRunning) return@LaunchedEffect
        delay(1500)
        onAction(MainAction.TestCurrentServer)
        while (true) {
            delay(5000)
            onRefreshSelected()
        }
    }

    val group = uiState.groups.firstOrNull { it.id == uiState.selectedGroupId }
    val usage = group?.usageInfo ?: uiState.groups.firstNotNullOfOrNull { it.usageInfo }
    val traffic = rememberLiveTraffic(isRunning)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(Modifier.fillMaxSize()) {
            // Fits any screen: header and the cards keep their size, the orb takes the space left.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StatusHeader(isRunning = isRunning, health = health, onAction = onAction)
                UpdateBanner(onOpen = onOpenUpdate)
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Orb(
                        size = minOf(maxWidth, maxHeight, 340.dp),
                        isRunning = isRunning,
                        onClick = { onAction(MainAction.ToggleService) }
                    )
                }
                ServerCard(
                    serverName = serverName,
                    serverHost = serverHost,
                    subtitle = when {
                        health == Health.Checking -> stringResource(R.string.saila_checking)
                        pingMs != null -> stringResource(R.string.saila_ping_ms, pingMs)
                        health == Health.Bad -> stringResource(R.string.saila_connection_failed)
                        else -> usage?.title ?: group?.remarks ?: stringResource(R.string.saila_current_server)
                    },
                    health = health,
                    isRunning = isRunning,
                    onOpenServers = onOpenServers,
                    onToggle = { onAction(MainAction.ToggleService) },
                    onTest = { onAction(MainAction.TestCurrentServer) }
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SpeedCard(R.drawable.ic_saila_up, stringResource(R.string.saila_upload), traffic.up, Modifier.weight(1f))
                    SpeedCard(R.drawable.ic_saila_down, stringResource(R.string.saila_download), traffic.down, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                InfoStrip(usage = usage, session = traffic.session)
                // Room for the floating nav bar.
                Spacer(Modifier.height(96.dp))
            }
            BottomNav(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
                onServers = onOpenServers,
                onSettings = onOpenSettings,
                onMenu = onMenuClick
            )
        }
    }
}

@Composable
private fun StatusHeader(isRunning: Boolean, health: Health, onAction: (MainAction) -> Unit) {
    var showImportMenu by remember { mutableStateOf(false) }
    val badge = when {
        health == Health.Bad -> HealthBad
        isRunning -> HealthGood
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(46.dp)
                .glass(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(R.drawable.ic_saila_shield), null, tint = badge, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.saila_status_connection),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(if (isRunning) R.string.saila_connected else R.string.saila_not_connected),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { showImportMenu = true }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(R.drawable.ic_add_24dp), null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.saila_add),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            DropdownMenu(
                expanded = showImportMenu,
                onDismissRequest = { showImportMenu = false },
                containerColor = sailaMenuColor(),
                border = sailaMenuBorder(),
            ) {
                ImportMenuContent(onAction = { action ->
                    showImportMenu = false
                    onAction(action)
                })
            }
        }
    }
}

/** Particle orb: rings of dots bent by slow waves, turning faster while connected. */
@Composable
private fun Orb(size: Dp, isRunning: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val dark = LocalDarkTheme.current
    val transition = rememberInfiniteTransition(label = "orb")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(if (isRunning) 7000 else 18000, easing = LinearEasing)),
        label = "phase"
    )
    val dotColor = accent
    val coreTop by animateColorAsState(if (isRunning) accent else lerp(accent, Color.Gray, 0.55f), tween(500), label = "core")

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(this.size.width / 2, this.size.height / 2)
            val maxR = min(this.size.width, this.size.height) / 2
            val rings = 16
            val dots = 84
            val points = ArrayList<Offset>(dots)
            for (i in 0 until rings) {
                val t = i / (rings - 1f)
                val base = maxR * (0.50f + 0.46f * t)
                points.clear()
                for (j in 0 until dots) {
                    val a = (2 * PI * j / dots).toFloat()
                    val wave = 1f +
                        0.07f * sin(3 * a + phase + i * 0.35f) +
                        0.04f * sin(5 * a - phase * 1.4f + i * 0.2f)
                    val r = base * wave
                    points.add(Offset(c.x + r * cos(a), c.y + r * sin(a)))
                }
                drawPoints(
                    points = points,
                    pointMode = PointMode.Points,
                    color = dotColor.copy(alpha = (1f - t * 0.55f).coerceAtMost(if (dark) 1f else 0.9f)),
                    strokeWidth = 3.4f + 2.0f * (1f - t),
                    cap = StrokeCap.Round
                )
            }
        }
        // Dark socket around the button, like a lens.
        Box(
            Modifier
                .size(size * 0.57f)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = if (dark) 0.55f else 0.12f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(size * 0.43f)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(coreTop, deeper(coreTop))))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = onClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isRunning) {
                    ConnectionTimer(fontSize = (size.value * 0.085f).sp)
                } else {
                    Icon(
                        painterResource(R.drawable.ic_saila_power),
                        stringResource(R.string.acc_start),
                        tint = Color.White,
                        modifier = Modifier.size(size * 0.17f)
                    )
                }
            }
        }
    }
}

/** hh:mm:ss since the service connected (stored by the service, so it survives reopening the app). */
@Composable
private fun ConnectionTimer(fontSize: TextUnit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val since = MmkvManager.decodeSettingsLong(AppConfig.PREF_CONNECTED_SINCE, 0L).takeIf { it > 0L } ?: now
    val s = ((now - since) / 1000).coerceAtLeast(0)
    Text(
        text = "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60),
        color = Color.White,
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun ServerCard(
    serverName: String?,
    serverHost: String?,
    subtitle: String,
    health: Health,
    isRunning: Boolean,
    onOpenServers: () -> Unit,
    onToggle: () -> Unit,
    onTest: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(28.dp))
            .padding(16.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onOpenServers),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ServerAvatar(remarks = serverName.orEmpty(), host = serverHost, size = 48.dp, selected = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    serverName?.let { CountryFlag.stripFlag(it) } ?: stringResource(R.string.saila_no_server),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (serverName == null) stringResource(R.string.saila_add_first) else subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (health) {
                        Health.Good -> HealthGood
                        Health.Bad -> HealthBad
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(enabled = isRunning, onClick = onTest)
                )
            }
            Icon(
                painterResource(R.drawable.ic_saila_chevron),
                stringResource(R.string.saila_servers),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(50))
                .then(
                    if (isRunning) Modifier.glass(RoundedCornerShape(50), strong = true)
                    else Modifier.background(Brush.horizontalGradient(listOf(accent, deeper(accent))))
                )
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(if (isRunning) R.string.saila_disconnect else R.string.saila_connect),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isRunning) MaterialTheme.colorScheme.onSurface else Color.White
            )
        }
    }
}

@Composable
private fun SpeedCard(iconRes: Int, label: String, bytesPerSec: Long, modifier: Modifier = Modifier) {
    Row(
        modifier
            .glass(RoundedCornerShape(24.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .glass(CircleShape, strong = true),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(iconRes), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${bytesPerSec.toTrafficString()}/s",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

/** One line: this session's traffic, subscription's remaining traffic and days. */
@Composable
private fun InfoStrip(usage: SubscriptionUsageInfo?, session: Long) {
    val now = System.currentTimeMillis()
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp))
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        InfoStat(session.toTrafficString(), stringResource(R.string.saila_session_usage), Modifier.weight(1f))
        if (usage != null) {
            val remaining = if (usage.total <= 0L) stringResource(R.string.saila_unlimited)
            else (usage.total - usage.used).coerceAtLeast(0L).toTrafficString()
            val expireMillis = usage.expire.takeIf { it > 0L }?.let { it * 1000L }
            val days = when {
                expireMillis == null -> stringResource(R.string.saila_unlimited)
                expireMillis <= now -> stringResource(R.string.saila_expired)
                else -> ((expireMillis - now + DAY_MILLIS - 1) / DAY_MILLIS).toString()
            }
            InfoStat(remaining, stringResource(R.string.saila_remaining_traffic), Modifier.weight(1f))
            InfoStat(days, stringResource(R.string.saila_days_left), Modifier.weight(1f))
        }
    }
}

@Composable
private fun InfoStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BottomNav(
    modifier: Modifier,
    onServers: () -> Unit,
    onSettings: () -> Unit,
    onMenu: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(50), strong = true)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavButton(R.drawable.ic_saila_home, stringResource(R.string.saila_home), selected = true) {}
        NavButton(R.drawable.ic_saila_globe, stringResource(R.string.saila_servers), selected = false, onClick = onServers)
        NavButton(R.drawable.ic_settings_24dp, stringResource(R.string.title_settings), selected = false, onClick = onSettings)
        NavButton(R.drawable.ic_menu_24dp, stringResource(R.string.acc_open_menu), selected = false, onClick = onMenu)
    }
}

@Composable
private fun NavButton(iconRes: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(54.dp)
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(Brush.linearGradient(listOf(accent, deeper(accent))))
                else Modifier.background(Color.Black.copy(alpha = if (LocalDarkTheme.current) 0.35f else 0.06f))
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(iconRes), label,
            tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Asks GitHub once per app start whether a newer Saila is out and offers it. */
@Composable
private fun UpdateBanner(onOpen: () -> Unit) {
    var latest by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        latest = withContext(Dispatchers.IO) {
            runCatching { UpdateCheckerManager.checkForUpdate() }.getOrNull()
                ?.takeIf { it.hasUpdate }?.latestVersion
        }
    }
    val version = latest ?: return
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.18f))
            .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(50))
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.ic_check_update_24dp), null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.saila_update_available, version),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(stringResource(R.string.saila_update_now), color = accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

private class LiveTraffic(val down: Long, val up: Long, val session: Long)

/**
 * Live traffic of this connection: the VPN service runs under the app's UID, so the UID's byte
 * counters are exactly what goes through the tunnel.
 */
@Composable
private fun rememberLiveTraffic(isRunning: Boolean): LiveTraffic {
    var state by remember { mutableStateOf(LiveTraffic(0L, 0L, 0L)) }
    LaunchedEffect(isRunning) {
        state = LiveTraffic(0L, 0L, 0L)
        if (!isRunning) return@LaunchedEffect
        val uid = android.os.Process.myUid()
        val startRx = android.net.TrafficStats.getUidRxBytes(uid)
        val startTx = android.net.TrafficStats.getUidTxBytes(uid)
        var lastRx = startRx
        var lastTx = startTx
        while (true) {
            delay(1000)
            val rx = android.net.TrafficStats.getUidRxBytes(uid)
            val tx = android.net.TrafficStats.getUidTxBytes(uid)
            state = LiveTraffic(
                down = (rx - lastRx).coerceAtLeast(0L),
                up = (tx - lastTx).coerceAtLeast(0L),
                session = (rx - startRx + tx - startTx).coerceAtLeast(0L),
            )
            lastRx = rx
            lastTx = tx
        }
    }
    return state
}
