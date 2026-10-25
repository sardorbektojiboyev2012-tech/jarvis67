package com.jarvis.agent.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jarvis.agent.auth.GoogleAuth
import com.jarvis.agent.data.*
import com.jarvis.agent.instagram.InstagramAuth
import com.jarvis.agent.net.MediaHost
import com.jarvis.agent.security.ActionType
import com.jarvis.agent.security.SpendingGuard
import com.jarvis.agent.security.TokenStore
import com.jarvis.agent.video.*
import com.jarvis.agent.work.UploadProgress
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val connected = mutableStateOf(false)
    private val status = mutableStateOf("")
    private lateinit var repo: JobRepository

    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val d = r.data
        if (d == null) { status.value = "Ulash bekor qilindi"; return@registerForActivityResult }
        MainScope().launch {
            val err = GoogleAuth.handleResult(this@MainActivity, d)
            status.value = err ?: "YouTube ulandi"
            connected.value = GoogleAuth.isConnected(this@MainActivity)
        }
    }
    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) MainScope().launch { enqueueExisting(uri) }
    }

    private suspend fun enqueueExisting(uri: Uri) {
        val f = withContext(Dispatchers.IO) {
            File(filesDir, "media/${UUID.randomUUID()}.mp4").also { it.parentFile?.mkdirs() }.also { out ->
                contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { i.copyTo(it) } }
            }
        }
        val payload = JSONObject().put("filePath", f.absolutePath).put("title", "JARVIS video")
            .put("description", "JARVIS orqali yuklandi").put("privacy", "private").toString()
        status.value = describe(repo.enqueue("youtube", "video", payload, System.currentTimeMillis(), ActionType.PUBLISH, null))
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        repo = JobRepository(this)
        connected.value = GoogleAuth.isConnected(this)
        setContent { MaterialTheme(colorScheme = jarvisScheme) { Screen(repo, connected.value, status.value, { status.value = it },
            onYtConnect = {
                if (!GoogleAuth.isConfigured()) status.value = "GOOGLE_CLIENT_ID sozlanmagan (SETUP_YOUTUBE.md)"
                else authLauncher.launch(GoogleAuth.authIntent(this))
            },
            onYtDisconnect = { MainScope().launch { GoogleAuth.disconnect(this@MainActivity); connected.value = false; status.value = "YouTube uzildi" } },
            onPick = { picker.launch("video/*") }) } }
    }
}

fun describe(r: Enqueued) = when (r) {
    is Enqueued.Queued -> "Vazifa #${r.id}: ${stateLabel(r.state)}"
    Enqueued.Duplicate -> "Dublikat bloklandi"
    is Enqueued.Denied -> r.reason
}

fun stateLabel(s: JobState) = when (s) {
    JobState.DRAFTED -> "Qoralama"
    JobState.AWAITING_APPROVAL -> "⏸ Tasdiq kutilmoqda"
    JobState.APPROVED, JobState.SCHEDULED -> "🕒 Navbatda (internet bo'lishi bilan yuboriladi)"
    JobState.UPLOADING -> "⬆ Yuborilmoqda"
    JobState.PUBLISHED -> "✅ Chop etildi"
    JobState.FAILED -> "❌ Xato"
}

fun platformName(p: String) = when (p) { "youtube" -> "YouTube"; "instagram" -> "Instagram"; "telegram" -> "Telegram"; else -> p }

/** Videoni telefon galereyasiga (Movies/JARVIS) saqlaydi. Android 10+ da ruxsat shart emas. */
fun saveToGallery(ctx: Context, f: File): Boolean {
    if (Build.VERSION.SDK_INT < 29) return false
    val v = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, f.name)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/JARVIS")
        put(MediaStore.Video.Media.IS_PENDING, 1)
    }
    val r = ctx.contentResolver
    val uri = r.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v) ?: return false
    r.openOutputStream(uri)?.use { o -> f.inputStream().use { it.copyTo(o) } } ?: return false
    v.clear(); v.put(MediaStore.Video.Media.IS_PENDING, 0); r.update(uri, v, null, null)
    return true
}

@Composable internal fun Section(title: String, content: @Composable ColumnScope.() -> Unit) =
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium); content() } }

@Composable internal fun F(label: String, v: String, on: (String) -> Unit, secret: Boolean = false, lines: Int = 1) =
    OutlinedTextField(v, on, Modifier.fillMaxWidth(), label = { Text(label) }, minLines = lines, singleLine = lines == 1,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None)

@Composable internal fun Primary(text: String, enabled: Boolean = true, onClick: () -> Unit) =
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = enabled) { Text(text) }

@Composable internal fun Check(label: String, v: Boolean, on: (Boolean) -> Unit) =
    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(v, on); Text(label) }

@Composable private fun StatusCard(text: String, onClose: () -> Unit) =
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f)); TextButton(onClick = onClose) { Text("✕") } } }

@Composable
fun Screen(repo: JobRepository, ytConnected: Boolean, status: String, setStatus: (String) -> Unit,
           onYtConnect: () -> Unit, onYtDisconnect: () -> Unit, onPick: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    Scaffold(bottomBar = { NavigationBar {
        listOf("🎙" to "Jarvis", "🎬" to "Studiya", "📋" to "Vazifalar", "🔗" to "Hisoblar").forEachIndexed { i, (ic, l) ->
            NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(ic) }, label = { Text(l) }) } } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (status.isNotBlank()) StatusCard(status) { setStatus("") }
            when (tab) {
                0 -> AssistantTab(setStatus)
                1 -> StudioTab(repo, setStatus)
                2 -> JobsTab(repo)
                else -> AccountsTab(ytConnected, setStatus, onYtConnect, onYtDisconnect, onPick)
            }
        }
    }
}

@Composable
private fun StudioTab(repo: JobRepository, setStatus: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }; var script by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }; var tags by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }; var lang by remember { mutableStateOf("uz") }
    var aspect by remember { mutableStateOf("9:16") }; var more by remember { mutableStateOf(false) }
    var toYt by remember { mutableStateOf(false) }; var toIg by remember { mutableStateOf(false) }; var toTg by remember { mutableStateOf(false) }
    var research by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }
    var lastVideo by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Video yaratish", style = MaterialTheme.typography.headlineSmall)
        F("Sarlavha", title, { title = it })
        F("Ssenariy (har qator = bitta sahna)", script, { script = it }, lines = 5)
        Text("Format", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("9:16", "16:9", "1:1").forEach { a ->
            if (a == aspect) Button(onClick = {}) { Text(a) } else OutlinedButton(onClick = { aspect = a }) { Text(a) } } }
        TextButton(onClick = { more = !more }) { Text(if (more) "Qo'shimcha sozlamalar ▴" else "Qo'shimcha sozlamalar ▾") }
        if (more) Section("Qo'shimcha") {
            F("Tavsif", desc, { desc = it }, lines = 2); F("Teglar (vergul bilan)", tags, { tags = it })
            F("Til kodi (en, ru, tr...)", lang, { lang = it })
            F("Mavzu (AI ssenariy uchun)", topic, { topic = it })
            Check("Internetdan qidirish (pullik)", research) { research = it }
            OutlinedButton(enabled = !busy && topic.isNotBlank(), onClick = { scope.launch {
                busy = true; setStatus("Claude ssenariy yozmoqda...")
                ClaudeScript.generate(ctx, topic, lang, 30, research).onSuccess { s ->
                    title = s.title; desc = s.description; tags = s.tags.joinToString(", "); script = s.scenes.joinToString("\n"); setStatus("Ssenariy tayyor, tekshirib chiqing")
                }.onFailure { setStatus(it.message ?: "Xato") }
                busy = false } }) { Text("AI bilan ssenariy (pullik)") }
        }
        Section("Tayyor bo'lgach yuborish (ixtiyoriy)") {
            Check("YouTube", toYt) { toYt = it }; Check("Instagram", toIg) { toIg = it }; Check("Telegram", toTg) { toTg = it }
            Text("Belgilamasangiz, video faqat yaratiladi.", style = MaterialTheme.typography.bodySmall)
        }
        Primary(if (busy) "Ishlamoqda..." else "🎬 Video yaratish", enabled = !busy && script.isNotBlank()) { scope.launch {
            busy = true
            try {
                val scenes = script.lines().map { it.trim() }.filter { it.isNotEmpty() }
                val tagList = tags.split(",").map { it.trim().trimStart('#') }.filter { it.isNotEmpty() }
                val t = title.ifBlank { topic.ifBlank { "JARVIS video" } }
                val res = VideoPipeline.create(ctx, VideoSpec(t, desc, tagList, scenes, aspect, lang)) { setStatus(it) }
                val ts = System.currentTimeMillis(); val out = mutableListOf<String>()
                val hash = tagList.joinToString(" ") { "#$it" }
                if (toYt) out += "YT: " + describe(repo.enqueue("youtube", "video", JSONObject().put("filePath", res.video.absolutePath).put("title", t)
                    .put("description", desc).put("tags", JSONArray(tagList)).put("privacy", "private").toString(), ts, ActionType.PUBLISH, null))
                if (toIg) out += "IG: " + describe(repo.enqueue("instagram", "video", JSONObject().put("mediaType", "REELS").put("filePath", res.video.absolutePath)
                    .put("caption", "$t\n\n$desc\n\n$hash").toString(), ts, ActionType.PUBLISH, null))
                if (toTg) out += "TG: " + describe(repo.enqueue("telegram", "video", JSONObject().put("kind", "video").put("filePath", res.video.absolutePath)
                    .put("caption", t).toString(), ts, ActionType.PUBLISH, null))
                lastVideo = res.video.absolutePath
                setStatus("✅ Video tayyor. " + out.joinToString("; ") + res.warnings.joinToString("") { " ⚠ $it" })
            } catch (e: Exception) { setStatus("Video xatosi: ${e.message}") }
            busy = false } }
        if (busy) CircularProgressIndicator()
        lastVideo?.let { path ->
            Section("Tayyor video") {
                key(path) {
                    AndroidView(factory = { c ->
                        VideoView(c).apply {
                            setVideoPath(path)
                            val mc = MediaController(c); mc.setAnchorView(this); setMediaController(mc)
                            setOnPreparedListener { it.isLooping = true; start() }
                        }
                    }, modifier = Modifier.fillMaxWidth().height(if (aspect == "9:16") 460.dp else 240.dp))
                }
                Primary("💾 Galereyaga saqlash") { scope.launch {
                    val ok = withContext(Dispatchers.IO) { saveToGallery(ctx, File(path)) }
                    setStatus(if (ok) "Galereyaga saqlandi: Movies/JARVIS" else "Saqlab bo'lmadi (Android 10+ kerak)") } }
            }
        }
    }
}

@Composable
private fun JobsTab(repo: JobRepository) {
    val jobs by repo.observeAll().collectAsState(emptyList())
    val prog by UploadProgress.state.collectAsState()
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Vazifalar", style = MaterialTheme.typography.headlineSmall) }
        if (jobs.isEmpty()) item { Text("Hozircha vazifa yo'q. Studiyada video yaratib, yuborish belgilarini tanlang.") }
        items(jobs) { j ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${platformName(j.platform)} · #${j.id}", style = MaterialTheme.typography.titleSmall)
                Text(stateLabel(j.state))
                prog[j.id]?.let { Text("Yuklanmoqda: $it%") }
                if (j.state == JobState.AWAITING_APPROVAL) Text("Yuborishdan oldin sizning tasdig'ingiz kerak.", style = MaterialTheme.typography.bodySmall)
                else j.lastError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                j.remoteId?.let { Text("ID: $it") }
                if (j.state == JobState.AWAITING_APPROVAL) Button(onClick = { scope.launch { repo.approve(j.id) } }) { Text("✔ Tasdiqlash va yuborish") }
                if (j.state == JobState.FAILED) OutlinedButton(onClick = { scope.launch { repo.approve(j.id) } }) { Text("↻ Qayta urinish") }
            } }
        }
    }
}

@Composable
private fun AccountsTab(ytConnected: Boolean, setStatus: (String) -> Unit, onYtConnect: () -> Unit, onYtDisconnect: () -> Unit, onPick: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { TokenStore(ctx) }
    var igOn by remember { mutableStateOf(InstagramAuth.isConnected(ctx)) }
    var igToken by remember { mutableStateOf("") }
    var tgToken by remember { mutableStateOf("") }; var tgChat by remember { mutableStateOf("") }
    var beUrl by remember { mutableStateOf("") }; var beKey by remember { mutableStateOf("") }
    var aiKey by remember { mutableStateOf("") }; var limit by remember { mutableStateOf(SpendingGuard(ctx).monthlyLimitCents.toString()) }
    var tgOn by remember { mutableStateOf(store.load("telegram_main") != null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Hisoblar", style = MaterialTheme.typography.headlineSmall)
        Section("YouTube: " + if (ytConnected) "✅ ulangan" else "ulanmagan") {
            if (ytConnected) { OutlinedButton(onClick = onYtDisconnect) { Text("Uzish") }; Button(onClick = onPick) { Text("Mavjud video yuklash") } }
            else Button(onClick = onYtConnect) { Text("Ulash") }
        }
        Section("Telegram: " + if (tgOn) "✅ saqlangan" else "ulanmagan") {
            F("Bot tokeni (@BotFather'dan)", tgToken, { tgToken = it }, secret = true); F("Chat ID", tgChat, { tgChat = it })
            Button(enabled = tgToken.isNotBlank() && tgChat.isNotBlank(), onClick = {
                store.save("telegram_main", JSONObject().put("botToken", tgToken.trim()).put("chatId", tgChat.trim()).toString())
                tgToken = ""; tgOn = true; setStatus("Telegram saqlandi") }) { Text("Saqlash") }
        }
        Section("Instagram: " + if (igOn) "✅ @" + (InstagramAuth.username(ctx) ?: "") else "ulanmagan") {
            if (igOn) OutlinedButton(onClick = { InstagramAuth.disconnect(ctx); igOn = false; setStatus("Instagram qurilmadan uzildi") }) { Text("Uzish") }
            else { F("Token (Meta dashboard'dan)", igToken, { igToken = it }, secret = true)
                Button(enabled = igToken.isNotBlank(), onClick = { scope.launch {
                    val e = InstagramAuth.connect(ctx, igToken); igOn = e == null; igToken = ""; setStatus(e ?: "Instagram ulandi") } }) { Text("Ulash") } }
            Text("Reels uchun ochiq URL kerak: pastdagi backend'ni sozlang.", style = MaterialTheme.typography.bodySmall)
        }
        Section("Backend (Instagram Reels uchun)") {
            F("URL (https://...)", beUrl, { beUrl = it }); F("API kaliti", beKey, { beKey = it }, secret = true)
            Button(enabled = beUrl.isNotBlank() && beKey.isNotBlank(), onClick = {
                store.save(MediaHost.ACCOUNT, JSONObject().put("baseUrl", beUrl.trim()).put("apiKey", beKey.trim()).toString()); beKey = ""; setStatus("Backend saqlandi") }) { Text("Saqlash") }
        }
        Section("Claude AI (ixtiyoriy, pullik)") {
            F("Anthropic API kaliti", aiKey, { aiKey = it }, secret = true); F("Oylik limit (sent, 0 = o'chiq)", limit, { limit = it })
            Button(onClick = {
                if (aiKey.isNotBlank()) store.save("anthropic_key", JSONObject().put("key", aiKey.trim()).toString()); aiKey = ""
                SpendingGuard(ctx).monthlyLimitCents = limit.toIntOrNull() ?: 0; setStatus("Saqlandi") }) { Text("Saqlash") }
        }
    }
}
