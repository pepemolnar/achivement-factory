package com.achievementfactory.ui.period

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.achievementfactory.domain.model.TrackedAchievement
import com.achievementfactory.ui.achievement.AchievementGlyph
import com.achievementfactory.ui.theme.MotionSpecs
import com.achievementfactory.ui.theme.Spacing
import com.achievementfactory.ui.theme.color
import com.achievementfactory.ui.theme.rememberReducedMotion

/**
 * The tab in reorder mode (ACH-09): every achievement as a simple row — no counting, no
 * celebrations, no Done section — with a drag handle. Dragging a handle moves that row; the new
 * order is handed to [onReorder] once, when the finger lifts, and persisted by the ViewModel.
 *
 * A hand-rolled drag over [androidx.compose.foundation.lazy.LazyListState]'s layout info rather
 * than a reorder library: the list is short, it never scrolls while a drag is in progress, and
 * this keeps the dependency list unchanged. The dragged row follows the finger by drawing at
 * "where the finger put it" minus "where layout currently puts it", read in the draw phase — so
 * it stays under the finger across swaps even when rows differ in height.
 */
@Composable
fun ReorderList(
    innerPadding: PaddingValues,
    achievements: List<TrackedAchievement>,
    onReorder: (List<Long>) -> Unit,
) {
    val upstreamIds = achievements.map { it.id }
    val byId = achievements.associateBy { it.id }
    val listState = rememberLazyListState()
    val latestOnReorder by rememberUpdatedState(onReorder)
    val latestUpstreamIds by rememberUpdatedState(upstreamIds)

    var order by remember { mutableStateOf(upstreamIds) }
    var draggedId by remember { mutableStateOf<Long?>(null) }
    var dragStartOffset by remember { mutableFloatStateOf(0f) }
    var dragTotal by remember { mutableFloatStateOf(0f) }

    // Follow the stored order whenever no drag is in progress (including after our own commit).
    LaunchedEffect(upstreamIds) {
        if (draggedId == null) order = upstreamIds
    }

    val reducedMotion = rememberReducedMotion()
    val placement = MotionSpecs.resolvePlacement(MotionSpecs.reorderPlacement, reducedMotion)

    fun moveDraggedRowIfNeeded() {
        val id = draggedId ?: return
        val visible = listState.layoutInfo.visibleItemsInfo
        val dragged = visible.firstOrNull { it.key == id } ?: return
        val draggedCenter = dragStartOffset + dragTotal + dragged.size / 2f
        val target = visible.firstOrNull { item ->
            item.key != id && draggedCenter >= item.offset && draggedCenter <= item.offset + item.size
        } ?: return
        val from = order.indexOf(id)
        val to = order.indexOf(target.key as? Long ?: return)
        if (from < 0 || to < 0 || from == to) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun endDrag() {
        if (draggedId == null) return
        draggedId = null
        dragTotal = 0f
        if (order != latestUpstreamIds) latestOnReorder(order)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = innerPadding.calculateTopPadding() + Spacing.md,
            bottom = innerPadding.calculateBottomPadding() + Spacing.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(order, key = { it }) { id ->
            val achievement = byId[id]
            val isDragged = id == draggedId
            if (achievement != null) ReorderRow(
                achievement = achievement,
                isDragged = isDragged,
                modifier = Modifier
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer {
                        if (isDragged) {
                            val layoutOffset = listState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.key == id }?.offset?.toFloat() ?: dragStartOffset
                            translationY = dragStartOffset + dragTotal - layoutOffset
                        }
                    }
                    .animateItem(placementSpec = if (isDragged) null else placement),
                handleModifier = Modifier.pointerInput(id) {
                    detectDragGestures(
                        onDragStart = {
                            draggedId = id
                            dragTotal = 0f
                            dragStartOffset = listState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.key == id }?.offset?.toFloat() ?: 0f
                        },
                        onDragEnd = { endDrag() },
                        onDragCancel = { endDrag() },
                        onDrag = { change, amount ->
                            change.consume()
                            dragTotal += amount.y
                            moveDraggedRowIfNeeded()
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun ReorderRow(
    achievement: TrackedAchievement,
    isDragged: Boolean,
    modifier: Modifier,
    handleModifier: Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.cardMinHeight),
        shape = RoundedCornerShape(Spacing.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragged) 8.dp else 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AchievementGlyph(icon = achievement.icon, name = achievement.name, accent = achievement.accent.color())
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = achievement.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.DragHandle,
                contentDescription = "Drag to reorder ${achievement.name}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = handleModifier.size(Spacing.xxl).padding(Spacing.sm),
            )
        }
    }
}
