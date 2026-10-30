package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.handler.CountryFlag

/** Round server badge: the server's country flag, a globe for CDN addresses, else the initials. */
@Composable
fun ServerAvatar(remarks: String, host: String?, size: Dp, selected: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val resolved by CountryFlag.resolved.collectAsState()
    val fromName = remember(remarks) {
        CountryFlag.flagInName(remarks) ?: CountryFlag.codeFromName(remarks)?.let { CountryFlag.emojiOf(it) }
    }
    val looked = remember(host, fromName) { if (fromName == null && host != null) CountryFlag.lookup(host) else null }
    val code = if (fromName == null && host != null) resolved[host] ?: looked else null
    val flag = fromName ?: code?.takeIf { it != CountryFlag.CDN }?.let { CountryFlag.emojiOf(it) }

    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (selected) accent.copy(alpha = 0.35f) else accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        when {
            flag != null -> Text(flag, fontSize = (size.value * 0.52f).sp)
            code == CountryFlag.CDN -> Icon(
                painterResource(R.drawable.ic_saila_globe), null,
                tint = accent, modifier = Modifier.size(size * 0.5f)
            )
            else -> Text(
                CountryFlag.stripFlag(remarks).split(' ', '·', '-', '|', '_')
                    .filter { it.isNotBlank() }.take(2)
                    .joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" },
                fontSize = (size.value * 0.32f).sp,
                color = if (selected) Color.White else accent
            )
        }
    }
}
