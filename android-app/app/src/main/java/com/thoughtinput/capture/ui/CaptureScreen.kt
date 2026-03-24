package com.thoughtinput.capture.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thoughtinput.capture.R

@Composable
fun CaptureScreen(
    isSending: Boolean,
    showSuccess: Boolean,
    errorMessage: String?,
    isRecording: Boolean,
    speechAvailable: Boolean,
    onSubmit: (String) -> Unit,
    onToggleVoice: () -> Unit,
    onDismiss: () -> Unit,
    speechTranscript: String
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // Icon color animates for feedback
    val iconTint by animateColorAsState(
        targetValue = when {
            showSuccess -> Color(0xFF4CAF50)
            errorMessage != null -> Color(0xFFFF9800)
            else -> Color(0xFFAAAAAA)
        },
        label = "iconTint"
    )

    LaunchedEffect(speechTranscript) {
        if (speechTranscript.isNotEmpty()) {
            text = speechTranscript
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Status bar height so we can position the bar right below it (matching widget)
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Full-screen translucent scrim — tap outside to dismiss
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() },
        contentAlignment = Alignment.TopCenter
    ) {
        // The capture bar — pill-shaped, positioned to overlap the widget
        Surface(
            modifier = Modifier
                .padding(top = statusBarPadding + 16.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth()
                .height(56.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* consume clicks so scrim doesn't dismiss */ },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
            tonalElevation = 2.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                // Left icon — brain/status indicator
                Icon(
                    painter = painterResource(
                        id = when {
                            showSuccess -> R.drawable.ic_check
                            else -> R.drawable.ic_brain
                        }
                    ),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Text input — no border, large font
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (text.isNotBlank()) onSubmit(text)
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (text.isEmpty()) {
                                Text(
                                    "Capture a thought...",
                                    style = TextStyle(
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Right side — mic or progress
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    IconButton(
                        onClick = onToggleVoice,
                        enabled = speechAvailable,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (isRecording) R.drawable.ic_mic_active else R.drawable.ic_mic
                            ),
                            contentDescription = if (isRecording) "Stop dictation" else "Start dictation",
                            tint = if (isRecording) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
