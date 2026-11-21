package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalSwissTokens
import com.example.ui.theme.SwissGeometry
import com.example.ui.theme.SwissMotion
import com.example.ui.theme.SwissOpacity

@Composable
fun SwissDivider(modifier: Modifier = Modifier) {
    val tokens = LocalSwissTokens.current
    HorizontalDivider(
        modifier = modifier,
        thickness = SwissGeometry.borderWidth,
        color = tokens.border.copy(alpha = SwissOpacity.divider)
    )
}

@Composable
fun SwissTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val tokens = LocalSwissTokens.current
    Column(modifier = Modifier.fillMaxWidth().background(tokens.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = SwissGeometry.screenPadding, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(SwissGeometry.minTouchTarget)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = tokens.primaryText,
                            modifier = Modifier.size(SwissGeometry.iconSize)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = tokens.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                content = actions
            )
        }
        SwissDivider()
    }
}

@Composable
fun SwissSectionHeader(
    title: String,
    trailingText: String? = null,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSwissTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = tokens.primaryText,
            modifier = Modifier.semantics { heading() }
        )
        if (!trailingText.isNullOrBlank()) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.mutedText
            )
        }
    }
}

@Composable
fun SwissSurfaceCard(
    modifier: Modifier = Modifier,
    useSurface2: Boolean = false,
    borderColor: Color? = null,
    padding: PaddingValues = PaddingValues(SwissGeometry.cardPadding),
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = LocalSwissTokens.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (useSurface2) tokens.surface2 else tokens.surface1,
        shape = RoundedCornerShape(SwissGeometry.radiusStandard),
        border = BorderStroke(SwissGeometry.borderWidth, borderColor ?: tokens.border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(SwissGeometry.spacing),
            content = content
        )
    }
}

@Composable
fun SwissStatusIndicator(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val tokens = LocalSwissTokens.current
    val baseModifier = modifier
        .clip(RoundedCornerShape(SwissGeometry.radiusSmall))
        .then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        )
    Surface(
        modifier = baseModifier.defaultMinSize(minHeight = 32.dp),
        color = tokens.surface1,
        shape = RoundedCornerShape(SwissGeometry.radiusSmall),
        border = BorderStroke(SwissGeometry.borderWidth, tokens.border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SwissBadge(
    text: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSwissTokens.current
    Surface(
        modifier = modifier,
        color = tokens.surface2,
        shape = RoundedCornerShape(SwissGeometry.radiusSmall),
        border = BorderStroke(SwissGeometry.borderWidth, textColor.copy(alpha = 0.45f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SwissSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSwissTokens.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp),
        color = tokens.surface1,
        shape = RoundedCornerShape(SwissGeometry.radiusStandard),
        border = BorderStroke(SwissGeometry.borderWidth, tokens.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = tokens.mutedText,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.mutedText
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = tokens.primaryText),
                    cursorBrush = SolidColor(tokens.primaryAccent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = tokens.secondaryText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SwissSettingRow(
    icon: ImageVector,
    title: String,
    description: String? = null,
    currentValue: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSwissTokens.current
    val rowMod = modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 56.dp)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = SwissGeometry.cardPadding, vertical = 12.dp)

    Row(
        modifier = rowMod,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tokens.secondaryText,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = tokens.primaryText
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText
                )
            }
            if (!currentValue.isNullOrBlank()) {
                Text(
                    text = currentValue,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.primaryAccent
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun SwissPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val tokens = LocalSwissTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(SwissMotion.pressDurationMs, easing = SwissMotion.standardEasing),
        label = "btn_press"
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(SwissGeometry.radiusStandard),
        colors = ButtonDefaults.buttonColors(
            containerColor = tokens.primaryAccent,
            contentColor = if (tokens.isDark) tokens.background else Color.White,
            disabledContainerColor = tokens.surface3,
            disabledContentColor = tokens.mutedText
        ),
        modifier = modifier
            .defaultMinSize(minHeight = SwissGeometry.minTouchTarget)
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SwissOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    tint: Color? = null
) {
    val tokens = LocalSwissTokens.current
    val contentColor = tint ?: tokens.primaryText
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(SwissGeometry.radiusStandard),
        border = BorderStroke(SwissGeometry.borderWidth, tokens.border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        modifier = modifier.defaultMinSize(minHeight = SwissGeometry.minTouchTarget)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SwissEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSwissTokens.current
    SwissSurfaceCard(modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tokens.mutedText,
            modifier = Modifier.size(22.dp)
        )
        Text(title, style = MaterialTheme.typography.titleMedium, color = tokens.primaryText)
        Text(description, style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
    }
}
