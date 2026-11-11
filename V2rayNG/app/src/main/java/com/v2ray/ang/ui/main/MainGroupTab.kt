package com.v2ray.ang.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import com.v2ray.ang.R
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.entities.ServersCache
import kotlinx.coroutines.flow.StateFlow
import java.util.Collections
import kotlin.math.abs

private val TAB_BAR_PADDING = 3.dp
private val TAB_SPACING = 2.dp
private val TAB_HEIGHT = 30.dp
private val SELECTED_TAB_MARGIN = 12.dp

/**
 * Subscription group tabs: a floating rounded bar whose selected tab is a tinted pill and whose
 * server count is a small badge.
 *
 * While all tabs fit, they share the bar width equally (like Telegram's folder tabs); once they
 * no longer fit, they keep their natural width and the bar scrolls horizontally, keeping the
 * selected tab in view.
 */
@Composable
fun GroupTabBar(
    groups: List<GroupMapItem>,
    selectedTabIndex: Int,
    mainViewModel: MainViewModel,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onReorder: (List<String>) -> Unit = {},
) {
    val selectedIndex = selectedTabIndex.coerceIn(0, groups.lastIndex)
    val selectedId = groups.getOrNull(selectedIndex)?.id
    val colors = MaterialTheme.colorScheme
    // The plain surface color equals the screen background in dark mode, so the bar needs its own tone there.
    val dark = colors.surface.luminance() < 0.5f

    // Saila: hold a tab and drag it to reorder the subscriptions. While dragging (and until the saved
    // order comes back from the ViewModel) the bar shows its own order of the same tabs.
    var localOrder by remember { mutableStateOf<List<String>?>(null) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val tabWidths = remember { mutableStateMapOf<String, Int>() }
    val groupIds = groups.map { it.id }
    val order = localOrder?.takeIf { it.toSet() == groupIds.toSet() }
    LaunchedEffect(groupIds) {
        if (draggingId == null && (order == null || order == groupIds)) localOrder = null
    }
    val byId = groups.associateBy { it.id }
    val shown = order?.mapNotNull { byId[it] } ?: groups
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val spacingPx = with(LocalDensity.current) { TAB_SPACING.toPx() }
    val haptics = LocalHapticFeedback.current
    // The drag handlers live as long as their tab, so they read these through updated state.
    val latestIds by rememberUpdatedState(groupIds)
    val latestOnReorder by rememberUpdatedState(onReorder)
    val latestRtl by rememberUpdatedState(rtl)

    // "All" (empty id) is not a subscription: it never moves and nothing moves past it.
    fun canSwap(ids: List<String>, from: Int, to: Int) =
        to in ids.indices && ids[from].isNotEmpty() && ids[to].isNotEmpty()

    fun move(id: String, step: Int, commit: Boolean) {
        val ids = (localOrder ?: latestIds).toMutableList()
        val from = ids.indexOf(id)
        if (from < 0 || !canSwap(ids, from, from + step)) return
        Collections.swap(ids, from, from + step)
        localOrder = ids
        if (commit) latestOnReorder(ids)
    }

    fun dragBy(id: String, deltaX: Float) {
        dragOffset += deltaX
        val ids = localOrder ?: return
        val from = ids.indexOf(id)
        if (from < 0) return
        // Screen x grows to the right; the tab order runs right to left in RTL.
        val forward = if (latestRtl) -dragOffset else dragOffset
        val step = if (forward > 0) 1 else -1
        if (!canSwap(ids, from, from + step)) return
        val neighborWidth = (tabWidths[ids[from + step]] ?: return) + spacingPx
        if (abs(forward) < neighborWidth / 2f) return
        move(id, step, commit = false)
        dragOffset -= if (latestRtl) -neighborWidth * step else neighborWidth * step
    }

    fun endDrag() {
        val ids = localOrder
        draggingId = null
        dragOffset = 0f
        if (ids != null && ids != latestIds) latestOnReorder(ids) else localOrder = null
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (dark) 0.16f else 0.8f)),
        shadowElevation = 0.dp
    ) {
        BoxWithConstraints {
            val barPaddingPx = with(LocalDensity.current) { TAB_BAR_PADDING.roundToPx() }
            val availablePx = constraints.maxWidth - 2 * barPaddingPx
            val earlier = stringResource(R.string.saila_sub_move_earlier)
            val later = stringResource(R.string.saila_sub_move_later)
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(TAB_BAR_PADDING)
            ) {
                EqualOrNaturalTabsLayout(availablePx = availablePx) {
                    shown.forEach { group ->
                        key(group.id) {
                            val serverFlow = remember(group.id, mainViewModel) {
                                mainViewModel.serversForGroup(group.id)
                            }
                            val dragging = draggingId == group.id
                            val dragModifier = if (group.id.isEmpty()) Modifier else Modifier
                                .pointerInput(group.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            localOrder = localOrder ?: latestIds
                                            draggingId = group.id
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragBy(group.id, amount.x)
                                        },
                                        onDragEnd = { endDrag() },
                                        onDragCancel = { endDrag() }
                                    )
                                }
                                .semantics {
                                    customActions = listOf(
                                        CustomAccessibilityAction(earlier) { move(group.id, -1, commit = true); true },
                                        CustomAccessibilityAction(later) { move(group.id, 1, commit = true); true }
                                    )
                                }
                            GroupTabItem(
                                group = group,
                                selected = group.id == selectedId,
                                serverFlow = serverFlow,
                                onClick = {
                                    val target = groups.indexOfFirst { it.id == group.id }
                                    if (target >= 0) onTabClick(target)
                                },
                                modifier = Modifier
                                    .onSizeChanged { tabWidths[group.id] = it.width }
                                    .zIndex(if (dragging) 1f else 0f)
                                    .graphicsLayer {
                                        if (dragging) {
                                            translationX = dragOffset
                                            scaleX = 1.06f
                                            scaleY = 1.06f
                                        }
                                    }
                                    .then(dragModifier),
                                elevated = dragging
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Lays the tabs out in a row. If every tab fits in an equal share of [availablePx] they all get
 * that share; otherwise, if they still fit together, the spare space is spread evenly; otherwise
 * they keep their natural width (the parent scrolls).
 */
@Composable
private fun EqualOrNaturalTabsLayout(
    availablePx: Int,
    content: @Composable () -> Unit
) {
    Layout(content = content) { measurables, _ ->
        if (measurables.isEmpty()) return@Layout layout(0, 0) {}

        val spacing = TAB_SPACING.roundToPx()
        val tabHeight = TAB_HEIGHT.roundToPx()
        val count = measurables.size
        val gaps = spacing * (count - 1)
        val natural = measurables.map { it.maxIntrinsicWidth(tabHeight) }
        val equalShare = (availablePx - gaps) / count

        val widths = when {
            availablePx <= 0 -> natural
            natural.all { it <= equalShare } -> List(count) { equalShare }
            natural.sum() + gaps <= availablePx -> {
                val extra = (availablePx - gaps - natural.sum()) / count
                natural.map { it + extra }
            }
            else -> natural
        }

        val placeables = measurables.mapIndexed { i, measurable ->
            measurable.measure(Constraints.fixed(widths[i], tabHeight))
        }
        layout(widths.sum() + gaps, tabHeight) {
            var x = 0
            placeables.forEach { placeable ->
                placeable.placeRelative(x, 0)
                x += placeable.width + spacing
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupTabItem(
    group: GroupMapItem,
    selected: Boolean,
    serverFlow: StateFlow<List<ServersCache>>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    elevated: Boolean = false
) {
    val servers by serverFlow.collectAsStateWithLifecycle()
    // The default group (empty id) never showed a count.
    val count = if (group.id.isEmpty()) 0 else servers.size
    val colors = MaterialTheme.colorScheme
    val bringIntoView = remember { BringIntoViewRequester() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val marginPx = with(LocalDensity.current) { SELECTED_TAB_MARGIN.toPx() }

    // Bring the tab in with some room around it so the pill never ends up flush against the bar's
    // rounded edge; at the first and last tab the scroll stops at the bar's own inner padding.
    LaunchedEffect(selected, size) {
        if (selected && size != IntSize.Zero) {
            bringIntoView.bringIntoView(
                Rect(-marginPx, 0f, size.width + marginPx, size.height.toFloat())
            )
        }
    }

    // One selectable node: name, count and selected state are announced together.
    Row(
        modifier = modifier
            .bringIntoViewRequester(bringIntoView)
            .onSizeChanged { size = it }
            .clip(CircleShape)
            .background(
                when {
                    elevated -> colors.primary.copy(alpha = 0.22f)
                    selected -> colors.primary.copy(alpha = 0.12f)
                    else -> Color.Transparent
                }
            )
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = group.remarks,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        if (count > 0) {
            Spacer(Modifier.width(3.dp))
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.55f))
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                    color = if (selected) colors.onPrimary else colors.surface,
                    maxLines = 1
                )
            }
        }
    }
}
