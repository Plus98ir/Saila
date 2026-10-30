package com.v2ray.ang.ui.settings

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.PreferenceGroupHeader
import com.v2ray.ang.ui.compose.SailaAccent
import com.v2ray.ang.ui.compose.SailaStyle
import com.v2ray.ang.handler.IranAppDetector
import com.v2ray.ang.core.AutoSwitchManager
import com.v2ray.ang.handler.SettingsChangeManager
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.v2ray.ang.ui.compose.SettingsListItem
import com.v2ray.ang.ui.compose.SettingsEditItem
import android.provider.Settings
import com.v2ray.ang.ui.compose.SettingsMenuItem
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.ThemeManager

/** Saila's short settings page; everything else lives behind "Advanced settings". */
class SailaSettingsActivity : BaseComponentActivity() {

    @Composable
    override fun ScreenContent() {
        SailaSettingsScreen(
            onBackClick = { finish() },
            onAdvancedClick = { startActivity(Intent(this, SettingsActivity::class.java)) }
        )
    }
}

@Composable
fun SailaSettingsScreen(
    onBackClick: () -> Unit,
    onAdvancedClick: () -> Unit,
) {
    val context = LocalContext.current
    val themeMode by ThemeManager.themeMode.collectAsState()
    val accent by ThemeManager.accent.collectAsState()
    val style by ThemeManager.style.collectAsState()
    val scope = rememberCoroutineScope()
    var reserve by remember {
        mutableStateOf(
            MmkvManager.decodeSettingsString(AppConfig.PREF_RESERVE_SERVER)?.takeIf { it.isNotBlank() }
                ?: AutoSwitchManager.RESERVE_AUTO
        )
    }
    val uaSaila = "Saila/${com.v2ray.ang.BuildConfig.VERSION_NAME}"
    val uaPresets = listOf("" to "Saila ($uaSaila)", "v2rayNG/2.3.14" to "v2rayNG", "HiddifyNext/2.5.7" to "Hiddify")
    var userAgent by remember {
        mutableStateOf(MmkvManager.decodeSettingsString(AppConfig.PREF_SUB_USER_AGENT).orEmpty())
    }
    var iranDirect by remember {
        mutableStateOf(MmkvManager.decodeSettingsBool(AppConfig.PREF_IRAN_APPS_DIRECT, false))
    }
    var iranCount by remember {
        mutableStateOf(MmkvManager.decodeSettingsStringSet(AppConfig.PREF_IRAN_APPS)?.size ?: 0)
    }
    // Servers of all subscriptions, for the reserve picker.
    val servers = remember {
        MmkvManager.decodeSubscriptions().flatMap { sub ->
            MmkvManager.decodeServerList(sub.guid).mapNotNull { guid ->
                MmkvManager.decodeServerConfig(guid)?.let { guid to it.remarks }
            }
        }
    }
    var autoSwitch by remember {
        mutableStateOf(MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SWITCH, true))
    }
    var language by remember {
        mutableStateOf(MmkvManager.decodeSettingsString(AppConfig.PREF_LANGUAGE, "auto") ?: "auto")
    }
    val languageEntries = stringArrayResource(R.array.language_select).toList()
    val languageValues = stringArrayResource(R.array.language_select_value).toList()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(title = stringResource(R.string.title_settings), onBackClick = onBackClick)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            PreferenceGroupHeader(stringResource(R.string.saila_settings_connection))
            SettingsSwitchItem(
                title = stringResource(R.string.saila_auto_switch),
                summary = stringResource(R.string.saila_auto_switch_summary),
                checked = autoSwitch,
                onCheckedChange = {
                    autoSwitch = it
                    MmkvManager.encodeSettings(AppConfig.PREF_AUTO_SWITCH, it)
                }
            )

            SettingsListItem(
                title = stringResource(R.string.saila_reserve_server),
                entries = listOf(stringResource(R.string.saila_reserve_auto)) + servers.map { it.second },
                values = listOf(AutoSwitchManager.RESERVE_AUTO) + servers.map { it.first },
                selectedValue = reserve,
                onSelected = {
                    reserve = it
                    MmkvManager.encodeSettings(AppConfig.PREF_RESERVE_SERVER, it)
                }
            )
            SettingsSwitchItem(
                title = stringResource(R.string.saila_iran_apps),
                summary = if (iranDirect) stringResource(R.string.saila_iran_apps_found, iranCount)
                else stringResource(R.string.saila_iran_apps_summary),
                checked = iranDirect,
                onCheckedChange = { on ->
                    iranDirect = on
                    MmkvManager.encodeSettings(AppConfig.PREF_IRAN_APPS_DIRECT, on)
                    if (on) scope.launch {
                        iranCount = withContext(Dispatchers.IO) { IranAppDetector.scanAndStore(context).size }
                    }
                    SettingsChangeManager.makeRestartService()
                }
            )

            SettingsMenuItem(
                title = stringResource(R.string.saila_kill_switch),
                subtitle = stringResource(R.string.saila_kill_switch_summary),
                onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
                }
            )

            PreferenceGroupHeader(stringResource(R.string.saila_settings_subscription))
            val custom = uaPresets.none { it.first == userAgent }
            SettingsListItem(
                title = stringResource(R.string.saila_user_agent),
                entries = uaPresets.map { it.second } + stringResource(R.string.saila_user_agent_custom),
                values = uaPresets.map { it.first } + "custom",
                selectedValue = if (custom) "custom" else userAgent,
                onSelected = {
                    if (it != "custom") {
                        userAgent = it
                        MmkvManager.encodeSettings(AppConfig.PREF_SUB_USER_AGENT, it)
                    } else if (!custom) {
                        userAgent = uaSaila
                        MmkvManager.encodeSettings(AppConfig.PREF_SUB_USER_AGENT, uaSaila)
                    }
                }
            )
            if (custom) {
                SettingsEditItem(
                    title = stringResource(R.string.saila_user_agent_custom),
                    value = userAgent,
                    onValueChanged = {
                        userAgent = it
                        MmkvManager.encodeSettings(AppConfig.PREF_SUB_USER_AGENT, it)
                    }
                )
            }

            PreferenceGroupHeader(stringResource(R.string.saila_settings_appearance))
            Text(
                text = stringResource(R.string.saila_theme_style),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            val styles = listOf(
                SailaStyle.Glass to stringResource(R.string.saila_style_glass),
                SailaStyle.Nexus to stringResource(R.string.saila_style_nexus),
                SailaStyle.Black to stringResource(R.string.saila_style_black),
                SailaStyle.White to stringResource(R.string.saila_style_white),
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                styles.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = style == value,
                        onClick = { ThemeManager.setStyle(value) },
                        shape = SegmentedButtonDefaults.itemShape(index, styles.size),
                    ) { Text(label, maxLines = 1) }
                }
            }
            if (style == SailaStyle.Glass) {
            Text(
                text = stringResource(R.string.title_pref_ui_mode_night),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
            )
            val modes = listOf(
                "0" to stringResource(R.string.saila_theme_system),
                "1" to stringResource(R.string.saila_theme_light),
                "2" to stringResource(R.string.saila_theme_dark),
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                modes.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = themeMode == value,
                        onClick = { ThemeManager.setThemeMode(value) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    ) { Text(label) }
                }
            }
            }

            Text(
                text = stringResource(R.string.saila_accent_color),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp)
            )
            AccentPicker(selected = accent, onSelect = { ThemeManager.setAccent(it) })

            Spacer(Modifier.height(8.dp))
            SettingsListItem(
                title = stringResource(R.string.title_language),
                entries = languageEntries,
                values = languageValues,
                selectedValue = language,
                onSelected = {
                    language = it
                    AppLocaleManager.setApplicationLanguage(it)
                }
            )

            PreferenceGroupHeader(stringResource(R.string.saila_settings_more))
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_settings_24dp),
                title = stringResource(R.string.saila_advanced_settings),
                subtitle = stringResource(R.string.saila_advanced_settings_summary),
                onClick = onAdvancedClick
            )
            NavigationBarsSpacer()
        }
    }
}

@Composable
private fun AccentPicker(selected: SailaAccent, onSelect: (SailaAccent) -> Unit) {
    val dark = LocalDarkTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SailaAccent.entries.forEach { accent ->
            val isSelected = accent == selected
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(
                        BorderStroke(
                            if (isSelected) 3.dp else 0.dp,
                            MaterialTheme.colorScheme.onBackground
                        ),
                        CircleShape
                    )
                    .padding(if (isSelected) 5.dp else 0.dp)
                    .clip(CircleShape)
                    .background(accent.color(dark))
                    .clickable { onSelect(accent) },
                contentAlignment = Alignment.Center
            ) {}
        }
    }
}
