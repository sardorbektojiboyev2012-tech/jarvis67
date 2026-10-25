package com.jarvis.agent.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.agent.assistant.AssistantService
import com.jarvis.agent.assistant.CallRinger
import com.jarvis.agent.assistant.Conversation

/** Qo'ng'iroqqa o'xshash ekran: qulflangan telefonda ham chiqadi. "Javob berish" bosilsa Jarvis gapiradi va savol berishga imkon beradi. */
class CallActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val msg = intent.getStringExtra("msg") ?: "Salom!"
        setContent { MaterialTheme(colorScheme = jarvisScheme) { CallScreen(msg) { CallRinger.dismiss(this); finish() } } }
    }
}

@Composable
private fun CallScreen(msg: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var stage by remember { mutableStateOf(0) }   // 0 jiringlayapti, 1 gaplashmoqda
    val inf = rememberInfiniteTransition(label = "call")
    val pulse by inf.animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")

    LaunchedEffect(stage) {
        if (stage == 1) {
            CallRinger.dismiss(ctx)
            AssistantService.pause(ctx)
            try {
                Conversation.say(ctx, msg)
                Conversation.run(ctx, "Yana biror narsa kerakmi?", 2, apologizeIfSilent = false)
            } finally { AssistantService.resume(ctx); onDone() }
        }
    }

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF071A24), Color(0xFF05080C)))).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        Spacer(Modifier.height(40.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    if (stage == 0) drawCircle(Cyan.copy(alpha = 0.15f), radius = size.minDimension / 2 * pulse)
                    drawCircle(Cyan.copy(alpha = 0.25f), radius = size.minDimension / 2 * 0.65f)
                }
                Text("J", fontSize = 72.sp, color = Cyan)
            }
            Text("JARVIS", fontSize = 30.sp, color = Color.White)
            Text(if (stage == 0) "qo'ng'iroq qilyapti…" else "gaplashmoqda…", color = Color(0xFFB8D4E0))
            if (stage == 1) Text(msg, textAlign = TextAlign.Center, color = Color(0xFFE6F6FF), modifier = Modifier.padding(top = 16.dp))
        }
        if (stage == 0) Row(Modifier.fillMaxWidth().padding(bottom = 40.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = onDone, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)), modifier = Modifier.size(width = 130.dp, height = 56.dp)) { Text("✖ Rad etish") }
            Button(onClick = { stage = 1 }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ECC71)), modifier = Modifier.size(width = 150.dp, height = 56.dp)) { Text("📞 Javob berish", color = Color.Black) }
        } else Spacer(Modifier.height(96.dp))
    }
}
