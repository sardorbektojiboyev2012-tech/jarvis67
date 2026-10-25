package com.jarvis.agent.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jarvis.agent.assistant.*
import com.jarvis.agent.security.TokenStore
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun AssistantTab(setStatus: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { TokenStore(ctx) }
    val running by AssistantService.running.collectAsState()
    val log by AssistantService.log.collectAsState()
    var pvKey by remember { mutableStateOf("") }; var pvOn by remember { mutableStateOf(Secrets.picovoice(ctx) != null) }
    var azKey by remember { mutableStateOf("") }; var azRegion by remember { mutableStateOf("westeurope") }
    var azOn by remember { mutableStateOf(AzureTts.configured(ctx)) }

    fun micOk() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    fun startNow() { if (!pvOn) setStatus("Avval Picovoice kalitini kiriting (pastda, 1-qadam)") else AssistantService.start(ctx) }
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.RECORD_AUDIO] == true || micOk()) setStatus("Ruxsatlar berildi. Endi doira ustiga bosing.") else setStatus("Mikrofon ruxsati kerak")
    }
    fun requestAll() = perms.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS))

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("J.A.R.V.I.S", fontSize = 28.sp, color = Cyan)
        Box(Modifier.size(190.dp).clip(CircleShape)
            .background(if (running) Cyan.copy(alpha = 0.18f) else Color(0x22FFFFFF))
            .border(3.dp, if (running) Cyan else Color(0xFF3A5566), CircleShape)
            .clickable { if (running) AssistantService.stop(ctx) else if (!micOk()) requestAll() else startNow() },
            contentAlignment = Alignment.Center) { Text(if (running) "🎙" else "⏻", fontSize = 70.sp) }
        Text(if (running) "Tinglayapti. «Jarvis» deng" else "O'chiq. Yoqish uchun doiraga bosing", textAlign = TextAlign.Center)
        if (log.isNotBlank()) Text(log, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        OutlinedButton(onClick = { scope.launch { Voice.speak(ctx, "Salom, men Jarvisman. Sizga qanday yordam bera olaman?")?.let { setStatus(it) } } }) { Text("🔊 Ovozni sinash") }

        Section("1. Uyg'otish so'zi «Jarvis» " + if (pvOn) "✅" else "") {
            F("Picovoice AccessKey", pvKey, { pvKey = it }, secret = true)
            Button(enabled = pvKey.isNotBlank(), onClick = { store.save("picovoice_key", JSONObject().put("key", pvKey.trim()).toString()); pvKey = ""; pvOn = true; setStatus("Picovoice kaliti saqlandi") }) { Text("Saqlash") }
            Text("Kalit: console.picovoice.ai (bepul ro'yxatdan o'ting)", style = MaterialTheme.typography.bodySmall)
        }
        Section("2. Ovoz: o'zbek yigit (Azure) " + if (azOn) "✅" else "(ixtiyoriy)") {
            F("Azure Speech kaliti", azKey, { azKey = it }, secret = true); F("Region (masalan westeurope)", azRegion, { azRegion = it })
            Button(enabled = azKey.isNotBlank() && azRegion.isNotBlank(), onClick = {
                store.save("azure_tts", JSONObject().put("key", azKey.trim()).put("region", azRegion.trim()).toString()); azKey = ""; azOn = true; setStatus("Azure ovozi saqlandi") }) { Text("Saqlash") }
            Text("Kalitsiz telefonning o'z ovozi gapiradi (o'zbek aksenti yomonroq).", style = MaterialTheme.typography.bodySmall)
        }
        Section("3. Ruxsatlar (telefon qulflanganda ham ishlashi uchun)") {
            Button(onClick = { requestAll() }) { Text("Mikrofon, qo'ng'iroq, kontaktlar") }
            OutlinedButton(onClick = { Setup.overlay(ctx) }) { Text("Boshqa ilovalar ustida ko'rsatish") }
            OutlinedButton(onClick = { Setup.battery(ctx) }) { Text("Batareya cheklovini o'chirish") }
            OutlinedButton(onClick = { Setup.autostart(ctx) }) { Text("Avto-ishga tushirish (Xiaomi)") }
        }
        Section("Buyruq namunalari") {
            Text("• salom\n• soat necha?\n• bugun nechanchi sana?\n• hisobot  /  hisobotni telegramga yubor\n• fonarni yoq  /  fonarni o'chir\n• budilnik 7 30\n• Dilshodga qo'ng'iroq qil\n• YouTube och\n• rahmat")
        }
    }
}
