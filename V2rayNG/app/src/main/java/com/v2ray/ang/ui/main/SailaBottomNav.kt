package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.glass

/** The four places of Saila's floating bottom bar. */
enum class SailaTab { Home, Servers, Settings, Menu }

/**
 * Saila: the floating bottom bar, shared by Home, Servers and Settings so it stays in place
 * when the page changes (Sadeq 2026-11-25). Carries its own bottom inset and side margins.
 */
@Composable
fun SailaBottomNav(selected: SailaTab, onSelect: (SailaTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 18.dp, end = 18.dp, bottom = 12.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(50), strong = true)
            .padding(8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavButton(R.drawable.ic_saila_home, stringResource(R.string.saila_home), selected == SailaTab.Home) { onSelect(SailaTab.Home) }
        NavButton(R.drawable.ic_saila_globe, stringResource(R.string.saila_servers), selected == SailaTab.Servers) { onSelect(SailaTab.Servers) }
        NavButton(R.drawable.ic_settings_24dp, stringResource(R.string.title_settings), selected == SailaTab.Settings) { onSelect(SailaTab.Settings) }
        NavButton(R.drawable.ic_menu_24dp, stringResource(R.string.acc_open_menu), selected == SailaTab.Menu) { onSelect(SailaTab.Menu) }
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
                if (selected) Modifier.background(Brush.linearGradient(listOf(accent, lerp(accent, Color.Black, 0.22f))))
                else Modifier.background(Color.Black.copy(alpha = if (LocalDarkTheme.current) 0.35f else 0.06f))
            )
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(iconRes), label,
            tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
    }
}
