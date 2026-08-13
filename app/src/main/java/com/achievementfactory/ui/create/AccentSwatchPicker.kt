package com.achievementfactory.ui.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.achievementfactory.data.achievement.AccentSwatch
import com.achievementfactory.ui.theme.Spacing
import com.achievementfactory.ui.theme.color

/**
 * The fixed ten-swatch accent picker (D-16) — every [AccentSwatch] entry, no free-form color
 * entry of any kind. Each target is at least [Spacing.xxl] (48dp), the Material accessibility
 * minimum touch size, and every swatch resolves its own light/dark [color] so it reads correctly
 * in both themes.
 */
@Composable
fun AccentSwatchPicker(
    selected: AccentSwatch,
    onSelect: (AccentSwatch) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        AccentSwatch.entries.forEach { swatch ->
            val swatchColor = swatch.color()
            val isSelected = swatch == selected
            Box(
                modifier = Modifier
                    .size(Spacing.xxl)
                    .clip(CircleShape)
                    .background(swatchColor)
                    .border(
                        width = if (isSelected) 3.dp else 0.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(swatch) }
                    .semantics { contentDescription = swatch.name },
            )
        }
    }
}
