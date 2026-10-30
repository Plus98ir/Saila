package com.v2ray.ang.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.max


/**
 * Saila's backdrop behind every screen. Glass: a base with vivid accent glows. Nexus: deep navy
 * with blue/violet glows and a faint grid (the DNSGuard panel look). Black and White: flat.
 * Plain gradients, no blur, so it is cheap to draw and scrolling stays smooth.
 */
@Composable
fun SailaBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val dark = LocalDarkTheme.current
    val style = LocalSailaStyle.current
    val accent = MaterialTheme.colorScheme.primary
    val gridStep = with(LocalDensity.current) { 32.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val r = max(size.width, size.height)
                fun glow(color: Color, alpha: Float, x: Float, y: Float, radius: Float) = drawRect(
                    Brush.radialGradient(
                        colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
                        center = Offset(size.width * x, size.height * y),
                        radius = r * radius
                    )
                )
                when (style) {
                    SailaStyle.Glass -> {
                        val k = if (dark) 1f else 0.7f
                        drawRect(if (dark) Color(0xFF080B16) else Color(0xFFEFF2FA))
                        // One hue only: the accent, a lighter and a deeper shade of it.
                        glow(accent, 0.95f * k, 0.0f, 0.0f, 0.72f)
                        glow(lerp(accent, Color.Black, 0.35f), 0.85f * k, 1.05f, 0.40f, 0.62f)
                        glow(lerp(accent, Color.White, 0.25f), 0.45f * k, 0.15f, 0.72f, 0.45f)
                        glow(lerp(accent, Color.Black, 0.2f), 0.70f * k, 0.95f, 1.02f, 0.62f)
                    }

                    SailaStyle.Nexus -> {
                        drawRect(Color(0xFF060A17))
                        glow(accent, 0.38f, 0.1f, 0.05f, 0.75f)
                        glow(lerp(accent, Color.Black, 0.3f), 0.30f, 1.0f, 0.55f, 0.65f)
                        glow(accent, 0.20f, 0.2f, 1.0f, 0.6f)
                        val line = Color(0xFF7896FF).copy(alpha = 0.06f)
                        var x = 0f
                        while (x < size.width) {
                            drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f); x += gridStep
                        }
                        var y = 0f
                        while (y < size.height) {
                            drawLine(line, Offset(0f, y), Offset(size.width, y), 1f); y += gridStep
                        }
                    }

                    SailaStyle.Black -> {
                        drawRect(Color(0xFF000000))
                        glow(accent, 0.10f, 0.5f, 0.0f, 0.6f)
                    }

                    SailaStyle.White -> {
                        drawRect(Color(0xFFF5F7FB))
                        glow(accent, 0.10f, 0.5f, 0.0f, 0.6f)
                    }
                }
            },
        content = content
    )
}

/** Card surface in the current look: frosted glass, navy glass (Nexus) or flat (Black/White). */
@Composable
fun Modifier.glass(shape: Shape, strong: Boolean = false): Modifier {
    val dark = LocalDarkTheme.current
    return when (LocalSailaStyle.current) {
        SailaStyle.Glass -> {
            val top = if (dark) (if (strong) 0.24f else 0.16f) else (if (strong) 0.88f else 0.70f)
            val bottom = if (dark) (if (strong) 0.10f else 0.05f) else (if (strong) 0.58f else 0.40f)
            this.clip(shape)
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = top), Color.White.copy(alpha = bottom))))
                .border(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = if (dark) 0.50f else 1f),
                            Color.White.copy(alpha = if (dark) 0.06f else 0.35f),
                            Color.White.copy(alpha = if (dark) 0.22f else 0.75f)
                        )
                    ),
                    shape
                )
        }

        SailaStyle.Nexus -> this.clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF16203E).copy(alpha = if (strong) 0.75f else 0.55f), Color(0xFF0A1022).copy(alpha = 0.6f))
                )
            )
            .border(1.dp, Color(0xFF82A0FF).copy(alpha = if (strong) 0.30f else 0.16f), shape)

        SailaStyle.Black -> this.clip(shape)
            .background(Color(if (strong) 0xFF22242A else 0xFF141519))
            .border(1.dp, Color(0xFF2A2C33), shape)

        SailaStyle.White -> this.clip(shape)
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E7F0), shape)
    }
}

/** Menu fill: the themed surface, slightly see-through in the glass looks. */
@Composable
fun sailaMenuColor(): Color {
    val style = LocalSailaStyle.current
    val alpha = if (style == SailaStyle.Glass || style == SailaStyle.Nexus) 0.985f else 1f
    return MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha)
}

@Composable
fun sailaMenuBorder() = androidx.compose.foundation.BorderStroke(
    1.dp,
    if (LocalDarkTheme.current) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.08f)
)
