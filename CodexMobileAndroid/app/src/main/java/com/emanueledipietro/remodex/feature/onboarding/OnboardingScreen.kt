package com.emanueledipietro.remodex.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emanueledipietro.remodex.ui.RemodexBrandMark

@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.28f),
                            accent.copy(alpha = 0.08f),
                            Color(0xFF15171C),
                        ),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                RemodexBrandMark(
                    modifier = Modifier.size(72.dp),
                    cornerRadius = 18.dp,
                    borderColor = Color.White.copy(alpha = 0.16f),
                )

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.1f),
                ) {
                    Text(
                        text = "Android preview",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.92f),
                    )
                }

                Text(
                    text = "Control Codex from your Android phone.",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                )

                Text(
                    text = "Local-first pairing, trusted reconnect, and cross-project conversations stay front and center.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.74f),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Before you scan",
                style = MaterialTheme.typography.labelLarge,
                color = accent.copy(alpha = 0.84f),
            )
            Text(
                text = "The Android client follows the same setup loop as iOS, but with Android-native navigation and recovery surfaces.",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
        }

        SetupStepCard(
            number = "1",
            title = "Install Codex CLI",
            command = "npm install -g @openai/codex@latest",
        )
        SetupStepCard(
            number = "2",
            title = "Install the latest bridge",
            command = "npm install -g remodex@latest",
        )
        SetupStepCard(
            number = "3",
            title = "Start pairing on your Mac",
            command = "remodex up",
        )

        Surface(
            color = Color.White.copy(alpha = 0.06f),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(28.dp),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "One tap drops you into the same local-first shell as iOS, and opens QR pairing right away when this device is not paired yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.82f),
                )
                Surface(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.QrCodeScanner,
                            contentDescription = null,
                            tint = Color.Black,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Set Up QR Pairing",
                            color = Color.Black,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupStepCard(
    number: String,
    title: String,
    command: String,
) {
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        color = Color.White.copy(alpha = 0.06f),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.border(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.08f),
            shape = RoundedCornerShape(28.dp),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = accent.copy(alpha = 0.18f),
                        shape = CircleShape,
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = number,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.07f),
                    ),
                ) {
                    Text(
                        text = command,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.82f),
                    )
                }
            }
        }
    }
}
