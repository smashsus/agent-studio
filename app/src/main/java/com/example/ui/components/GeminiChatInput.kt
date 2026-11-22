package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.ArrowUp
import com.adamglin.phosphoricons.fill.Stop
import com.adamglin.phosphoricons.regular.Clock
import com.adamglin.phosphoricons.regular.Microphone
import com.adamglin.phosphoricons.regular.Paperclip
import com.adamglin.phosphoricons.regular.Plus
import com.adamglin.phosphoricons.regular.X
import com.example.ui.PendingAttachment
import com.example.ui.theme.LocalSwissTokens
import com.example.ui.theme.SwissGeometry
import com.example.ui.theme.SwissMotion
import com.example.ui.theme.SwissOpacity

@Composable
fun GeminiChatInputArea(
    inputText: String,
    onInputChange: (String) -> Unit,
    placeholder: String,
    isRunning: Boolean,
    pendingAttachment: PendingAttachment?,
    onClearAttachment: () -> Unit,
    onAttach: () -> Unit,
    onVoice: () -> Unit,
    onSend: () -> Unit,
    onScheduleBackground: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    applyNavigationBarsPadding: Boolean = true,
    inputTestTag: String = "prompt_input_field"
) {
    val tokens = LocalSwissTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) tokens.primaryAccent.copy(alpha = 0.78f) else tokens.border.copy(alpha = 0.55f),
        animationSpec = tween(150, easing = SwissMotion.standardEasing),
        label = "gemini_input_border"
    )
    val containerColor by animateColorAsState(
        targetValue = if (tokens.isDark && isFocused) tokens.surface2 else tokens.surface1,
        animationSpec = tween(150, easing = SwissMotion.standardEasing),
        label = "gemini_input_bg"
    )

    val containerModifier = modifier
        .fillMaxWidth()
        .background(tokens.background)
        .then(if (applyNavigationBarsPadding) Modifier.navigationBarsPadding() else Modifier)
        .padding(horizontal = SwissGeometry.screenPadding, vertical = 8.dp)

    Column(modifier = containerModifier) {
        Surface(
            color = containerColor,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(SwissGeometry.borderWidth, borderColor),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AnimatedVisibility(
                    visible = pendingAttachment != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    pendingAttachment?.let { att ->
                        Surface(
                            color = tokens.primaryAccent.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.75.dp, tokens.primaryAccent.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = PhosphorIcons.Regular.Paperclip,
                                    contentDescription = null,
                                    tint = tokens.primaryAccent,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = att.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.primaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = onClearAttachment,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = PhosphorIcons.Regular.X,
                                        contentDescription = "Remove attachment",
                                        tint = tokens.secondaryText,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Multi-line Gemini-style prompt field
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 28.dp, max = 148.dp)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (inputText.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = tokens.mutedText.copy(alpha = SwissOpacity.placeholder)
                        )
                    }
                    BasicTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        interactionSource = interactionSource,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = tokens.primaryText),
                        cursorBrush = SolidColor(tokens.primaryAccent),
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(inputTestTag)
                    )
                }

                // Bottom Action Toolbar (Attach, Voice, Schedule on left · Send / Stop on right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            color = tokens.surface3.copy(alpha = 0.7f),
                            shape = CircleShape,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onAttach)
                                .testTag("attach_document_button")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = PhosphorIcons.Regular.Plus,
                                    contentDescription = "Attach Document",
                                    tint = tokens.primaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onVoice,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("voice_input_button")
                        ) {
                            Icon(
                                imageVector = PhosphorIcons.Regular.Microphone,
                                contentDescription = "Voice Input",
                                tint = tokens.secondaryText,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = !isRunning && inputText.isNotBlank(),
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Surface(
                                color = tokens.surface3.copy(alpha = 0.6f),
                                shape = CircleShape,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(onClick = onScheduleBackground)
                                    .testTag("schedule_workmanager_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = PhosphorIcons.Regular.Clock,
                                        contentDescription = "Schedule Background Execution",
                                        tint = tokens.secondaryText,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Queue",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.secondaryText
                                    )
                                }
                            }
                        }
                    }

                    if (isRunning) {
                        IconButton(
                            onClick = onStop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(tokens.error.copy(alpha = 0.16f))
                                .testTag("stop_execution_button")
                        ) {
                            Icon(
                                imageVector = PhosphorIcons.Fill.Stop,
                                contentDescription = "Stop Execution",
                                tint = tokens.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        val canSend = inputText.isNotBlank() || pendingAttachment != null
                        IconButton(
                            onClick = onSend,
                            enabled = canSend,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (canSend) tokens.primaryAccent else tokens.surface3)
                                .testTag("send_prompt_button")
                        ) {
                            Icon(
                                imageVector = PhosphorIcons.Fill.ArrowUp,
                                contentDescription = "Send",
                                tint = if (canSend) {
                                    if (tokens.isDark) tokens.background else Color.White
                                } else tokens.mutedText,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
