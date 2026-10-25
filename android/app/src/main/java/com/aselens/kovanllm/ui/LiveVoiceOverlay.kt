package com.aselens.kovanllm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aselens.kovanllm.data.I18n
import com.aselens.kovanllm.data.LiveVoiceState

@Composable
fun LiveVoiceOverlay(
    state: LiveVoiceState,
    audioLevel: Float,
    userTranscript: String,
    assistantTranscript: String,
    isMuted: Boolean,
    language: String,
    onInterrupt: () -> Unit,
    onToggleMute: () -> Unit,
    onEndCall: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")

    // Ambient floating glow animation
    val ambientPulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_scale"
    )

    // Dynamic scale driven by microphone audio decibel levels
    val micScale by animateFloatAsState(
        targetValue = 1.0f + (audioLevel * 0.45f),
        animationSpec = tween(durationMillis = 80, easing = LinearEasing),
        label = "mic_scale"
    )

    val finalOrbScale = if (state == LiveVoiceState.LISTENING) micScale * ambientPulse else ambientPulse

    // Palette per state
    val orbGlowColor = when (state) {
        LiveVoiceState.LISTENING -> Color(0xFF10B981) // Emerald Green
        LiveVoiceState.THINKING -> Color(0xFF8B5CF6)  // Purple / Violet
        LiveVoiceState.SPEAKING -> Color(0xFF06B6D4)  // Cyan / Electric Blue
        LiveVoiceState.IDLE -> Color(0xFF6B7280)
    }

    val stateTitle = when (state) {
        LiveVoiceState.LISTENING -> if (isMuted) I18n.t("live_mute", language) else I18n.t("live_listening", language)
        LiveVoiceState.THINKING -> I18n.t("live_thinking", language)
        LiveVoiceState.SPEAKING -> I18n.t("live_speaking", language)
        LiveVoiceState.IDLE -> I18n.t("live_voice_mode", language)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xF50A0F1D) // Deep Obsidian Frosted Glass
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Background ambient blur lights
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height * 0.42f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            orbGlowColor.copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = size.width * 0.7f
                    ),
                    center = center,
                    radius = size.width * 0.7f
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Status Bar: Live badge & State pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(30.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, orbGlowColor.copy(alpha = 0.6f), RoundedCornerShape(30.dp))
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(orbGlowColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stateTitle,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Center Dynamic Sound Orb / Waveform
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .scale(finalOrbScale)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            if (state == LiveVoiceState.SPEAKING) {
                                onInterrupt()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Outer ripple 1
                    Box(
                        modifier = Modifier
                            .size(230.dp)
                            .clip(CircleShape)
                            .background(orbGlowColor.copy(alpha = 0.12f))
                    )
                    // Outer ripple 2
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .clip(CircleShape)
                            .background(orbGlowColor.copy(alpha = 0.22f))
                    )
                    // Core Orb
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color.White,
                                        orbGlowColor,
                                        orbGlowColor.copy(alpha = 0.85f),
                                        Color(0xFF030712)
                                    )
                                )
                            )
                            .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (state) {
                                LiveVoiceState.LISTENING -> if (isMuted) Icons.Default.MicOff else Icons.Default.Mic
                                LiveVoiceState.THINKING -> Icons.Default.AutoAwesome
                                LiveVoiceState.SPEAKING -> Icons.AutoMirrored.Filled.VolumeUp
                                LiveVoiceState.IDLE -> Icons.Default.GraphicEq
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Subtitles / Live Transcript Area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF131B2E))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (userTranscript.isNotBlank()) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "Sen: ",
                                color = Color(0xFF38BDF8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = userTranscript,
                                color = Color.White.copy(alpha = 0.95f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    if (assistantTranscript.isNotBlank()) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "Kovan: ",
                                color = Color(0xFFF59E0B),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = assistantTranscript,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    if (userTranscript.isBlank() && assistantTranscript.isBlank()) {
                        Text(
                            text = I18n.t("live_tap_to_interrupt", language),
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 30.dp)
                        )
                    }
                }

                // Bottom Action Control Deck
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute / Unmute Button
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (isMuted) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFF1E293B))
                            .border(1.dp, if (isMuted) Color(0xFFEF4444) else Color(0xFF334155), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isMuted) I18n.t("live_unmute", language) else I18n.t("live_mute", language),
                            tint = if (isMuted) Color(0xFFEF4444) else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Interrupt / Stop Speaking Button
                    IconButton(
                        onClick = onInterrupt,
                        enabled = state == LiveVoiceState.SPEAKING || state == LiveVoiceState.THINKING,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                if (state == LiveVoiceState.SPEAKING || state == LiveVoiceState.THINKING)
                                    Color(0xFFD97706)
                                else
                                    Color(0xFF1E293B).copy(alpha = 0.5f)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.StopCircle,
                            contentDescription = "Interrupt",
                            tint = if (state == LiveVoiceState.SPEAKING || state == LiveVoiceState.THINKING) Color.White else Color.Gray,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // End Live Call Button
                    IconButton(
                        onClick = onEndCall,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFDC2626)) // Vibrant Red
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = I18n.t("live_end_call", language),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}
