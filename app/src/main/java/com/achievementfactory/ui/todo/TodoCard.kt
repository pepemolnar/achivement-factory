package com.achievementfactory.ui.todo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import com.achievementfactory.domain.model.TodoItem
import com.achievementfactory.ui.theme.Spacing

/**
 * A one-off todo in a period tab (TODO-02) — deliberately unlike an achievement card so the two
 * read differently at a glance: an outlined, transparent card with a plain round checkbox instead
 * of the accent ring, no icon, no counter, no medal, no streak flame. Tapping toggles it (TODO-03 —
 * tapping again is the undo); long-pressing asks to delete it (TODO-06).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodoCard(
    todo: TodoItem,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.todoMinHeight)
            .semantics { stateDescription = if (todo.done) "Done" else "Not done" }
            .combinedClickable(role = Role.Checkbox, onClick = onToggle, onLongClick = onLongPress),
        shape = RoundedCornerShape(Spacing.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(Spacing.todoBorderWidth, outline),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (todo.done) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (todo.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Spacing.todoCheckSize),
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = todo.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (todo.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (todo.done) TextDecoration.LineThrough else null,
            )
        }
    }
}
