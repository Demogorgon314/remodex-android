package com.emanueledipietro.remodex.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.emanueledipietro.remodex.R
import com.emanueledipietro.remodex.ui.RemodexBrandMark
import kotlinx.coroutines.launch

private data class OnboardingFeature(
    val icon: ImageVector,
    val accent: Color,
    val title: String,
    val subtitle: String,
)

private data class OnboardingStep(
    val stepNumber: Int,
    val icon: ImageVector,
    val title: String,
    val description: String,
    val command: String,
)

private val onboardingFeatures = listOf(
    OnboardingFeature(
        icon = Icons.Outlined.Bolt,
        accent = Color(0xFFFFD54F),
        title = "Fast mode",
        subtitle = "Lower-latency turns for quick interactions",
    ),
    OnboardingFeature(
        icon = Icons.Outlined.AccountTree,
        accent = Color(0xFF7BD88F),
        title = "Git from your phone",
        subtitle = "Commit, push, pull, and switch branches",
    ),
    OnboardingFeature(
        icon = Icons.Outlined.Lock,
        accent = Color(0xFF6EE7F2),
        title = "End-to-end encrypted",
        subtitle = "The relay never sees your prompts or code",
    ),
    OnboardingFeature(
        icon = Icons.Outlined.Waves,
        accent = Color(0xFFC4B5FD),
        title = "Voice mode",
        subtitle = "Talk to Codex with speech-to-text",
    ),
    OnboardingFeature(
        icon = Icons.Outlined.AccountTree,
        accent = Color(0xFFFFB86B),
        title = "Subagents, skills and /commands",
        subtitle = "Spawn and monitor parallel agents from your phone",
    ),
)

private val onboardingSteps = listOf(
    OnboardingStep(
        stepNumber = 1,
        icon = Icons.Outlined.Terminal,
        title = "Install Codex CLI",
        description = "The AI coding agent that lives in your terminal. Remodex connects to it from your Android phone.",
        command = "npm install -g @openai/codex@latest",
    ),
    OnboardingStep(
        stepNumber = 2,
        icon = Icons.Outlined.Link,
        title = "Install the Bridge",
        description = "A lightweight relay that securely connects your Mac to your Android phone.",
        command = "npm install -g remodex@latest",
    ),
    OnboardingStep(
        stepNumber = 3,
        icon = Icons.Outlined.QrCodeScanner,
        title = "Start Pairing",
        description = "Run this on your Mac. A QR code will appear in your terminal, and you will scan it next.",
        command = "remodex up",
    ),
)

private const val OnboardingPageCount = 5
private const val CodexInstallStepPageIndex = 2
private const val CodexInstallCommand = "npm install -g @openai/codex@latest"

internal enum class OnboardingContinueAction {
    SHOW_CODEX_INSTALL_CONFIRMATION,
    ADVANCE,
    COMPLETE,
}

internal fun onboardingContinueAction(currentPage: Int): OnboardingContinueAction {
    return when {
        currentPage == CodexInstallStepPageIndex -> OnboardingContinueAction.SHOW_CODEX_INSTALL_CONFIRMATION
        currentPage < OnboardingPageCount - 1 -> OnboardingContinueAction.ADVANCE
        else -> OnboardingContinueAction.COMPLETE
    }
}

@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { OnboardingPageCount })
    val coroutineScope = rememberCoroutineScope()
    var showCodexInstallReminder by rememberSaveable { mutableStateOf(false) }

    fun advanceToNextPage() {
        coroutineScope.launch {
            pagerState.animateScrollToPage(pagerState.currentPage + 1)
        }
    }

    fun handleContinue() {
        when (onboardingContinueAction(currentPage = pagerState.currentPage)) {
            OnboardingContinueAction.SHOW_CODEX_INSTALL_CONFIRMATION -> {
                showCodexInstallReminder = true
            }

            OnboardingContinueAction.ADVANCE -> advanceToNextPage()
            OnboardingContinueAction.COMPLETE -> onContinue()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                when (page) {
                    0 -> OnboardingWelcomePage()
                    1 -> OnboardingFeaturesPage()
                    2 -> OnboardingStepPage(step = onboardingSteps[0])
                    3 -> OnboardingStepPage(step = onboardingSteps[1])
                    else -> OnboardingStepPage(step = onboardingSteps[2])
                }
            }

            OnboardingBottomBar(
                currentPage = pagerState.currentPage,
                onContinue = ::handleContinue,
            )
        }

        if (showCodexInstallReminder) {
            AlertDialog(
                onDismissRequest = { showCodexInstallReminder = false },
                dismissButton = {
                    TextButton(onClick = { showCodexInstallReminder = false }) {
                        Text("Stay Here")
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showCodexInstallReminder = false
                            advanceToNextPage()
                        },
                    ) {
                        Text("Continue Anyway")
                    }
                },
                title = {
                    Text("Install Codex CLI First")
                },
                text = {
                    Text(
                        "Copy and paste \"$CodexInstallCommand\" on your Mac before moving on. " +
                            "Remodex will not work until Codex CLI is installed and available in your PATH.",
                    )
                },
            )
        }
    }
}

@Composable
private fun OnboardingBottomBar(
    currentPage: Int,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(OnboardingPageCount) { index ->
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                color = if (index == currentPage) {
                                    Color.White
                                } else {
                                    Color.White.copy(alpha = 0.18f)
                                },
                                shape = CircleShape,
                            )
                            .height(8.dp)
                            .width(if (index == currentPage) 24.dp else 8.dp),
                    )
                }
            }
        }

        Surface(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shape = RoundedCornerShape(999.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (currentPage == OnboardingPageCount - 1) {
                    Icon(
                        imageVector = Icons.Outlined.QrCodeScanner,
                        contentDescription = null,
                        tint = Color.Black,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = onboardingButtonTitle(currentPage),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                )
            }
        }
    }
}

@Composable
private fun OnboardingWelcomePage() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding(),
    ) {
        Image(
            painter = painterResource(id = R.drawable.onboarding_welcome_hero),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp)
                .align(Alignment.TopCenter),
            contentScale = ContentScale.Crop,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.42f),
                            Color.Black.copy(alpha = 0.86f),
                            Color.Black,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 28.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            RemodexBrandMark(
                modifier = Modifier.size(72.dp),
                cornerRadius = 18.dp,
                borderColor = Color.White.copy(alpha = 0.22f),
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Remodex",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = "Control Codex from your Android phone.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.56f),
                    textAlign = TextAlign.Center,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color.White.copy(alpha = 0.58f),
                )
                Text(
                    text = "End-to-end encrypted",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.58f),
                )
            }
        }
    }
}

@Composable
private fun OnboardingFeaturesPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "What you get",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = "Everything runs on your Mac.\nYour phone is the remote.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center,
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                onboardingFeatures.forEach { feature ->
                    OnboardingFeatureRow(feature = feature)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun OnboardingFeatureRow(
    feature: OnboardingFeature,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(feature.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = feature.accent,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = feature.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Text(
                text = feature.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
private fun OnboardingStepPage(
    step: OnboardingStep,
) {
    val accent = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.16f),
                            Color.Transparent,
                        ),
                        radius = 900f,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(36.dp),
            ) {
                Box(
                    modifier = Modifier.size(140.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        accent.copy(alpha = 0.22f),
                                        Color.Transparent,
                                    ),
                                ),
                                shape = CircleShape,
                            ),
                    )
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    accent.copy(alpha = 0.38f),
                                    accent.copy(alpha = 0.08f),
                                ),
                            ),
                        ),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = step.icon,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = Color.White,
                            )
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "STEP ${step.stepNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent.copy(alpha = 0.72f),
                    )
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = step.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.45f),
                        textAlign = TextAlign.Center,
                    )
                }

                OnboardingCommandCard(
                    command = step.command,
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun OnboardingCommandCard(
    command: String,
) {
    Surface(
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.08f),
        ),
    ) {
        Text(
            text = command,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.84f),
            textAlign = TextAlign.Center,
        )
    }
}

private fun onboardingButtonTitle(currentPage: Int): String {
    return when (currentPage) {
        0 -> "Get Started"
        1 -> "Set Up"
        OnboardingPageCount - 1 -> "Scan QR Code"
        else -> "Continue"
    }
}
