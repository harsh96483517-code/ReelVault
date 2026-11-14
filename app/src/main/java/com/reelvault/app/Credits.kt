package com.reelvault.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CreditsScreen(onBack: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    val ctx = LocalContext.current
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize()) {
        AppBackground()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(60.dp)
                    .glass(RoundedCornerShape(30.dp)).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlassIconButton(Icons.Rounded.ArrowBack, "Back", onBack, size = 42.dp)
                Text("App Credits", style = MaterialTheme.typography.titleMedium, color = ink)
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LogoMark(72.dp)
                Image(
                    painterResource(R.drawable.dev_photo), "Harsh",
                    Modifier.size(136.dp).clip(CircleShape).border(2.5.dp, AccentBrush, CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Designed & Developed by", color = ink.copy(alpha = 0.65f), style = MaterialTheme.typography.bodyMedium)
                    Text("Harsh", color = ink, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                }
                GlassCard {
                    Text("Concept  •  UI/UX Design  •  Development", Modifier.fillMaxWidth(), color = ink,
                        textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
                }
                GlassCard {
                    Text("© 2026 Harsh. All Rights Reserved.", Modifier.fillMaxWidth(), color = ink, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("Made with passion. Built for the next generation.", Modifier.fillMaxWidth(),
                        color = ink.copy(alpha = 0.75f), textAlign = TextAlign.Center, fontStyle = FontStyle.Italic)
                }
                Text("ReelVault v${Updater.currentVersion(ctx)}", color = ink.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
