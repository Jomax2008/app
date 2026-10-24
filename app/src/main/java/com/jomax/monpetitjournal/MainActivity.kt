package com.jomax.monpetitjournal

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class JournalEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis(),
    val mood: String = "🙂"
)

data class CompanionSettings(
    val name: String = "Milo",
    val tone: String = "Doux",
    val ageGroup: String = "Ado",
    val theme: String = "Lavande",
    val reminders: Boolean = true,
    val reminderHour: Int = 20
)

class JournalStore(context: Context) {
    private val prefs = context.getSharedPreferences("journal_private_store", Context.MODE_PRIVATE)

    fun entries(): List<JournalEntry> {
        val arr = try { JSONArray(prefs.getString("entries", "[]")) } catch (_: Exception) { JSONArray() }
        return (0 until arr.length()).mapNotNull { i ->
            try {
                val o = arr.getJSONObject(i)
                JournalEntry(o.getString("id"), o.optString("title"), o.optString("body"),
                    o.optLong("createdAt"), o.optString("mood", "🙂"))
            } catch (_: Exception) { null }
        }.sortedByDescending { it.createdAt }
    }

    fun saveEntries(entries: List<JournalEntry>) {
        val arr = JSONArray()
        entries.forEach {
            arr.put(JSONObject().put("id", it.id).put("title", it.title).put("body", it.body)
                .put("createdAt", it.createdAt).put("mood", it.mood))
        }
        prefs.edit().putString("entries", arr.toString()).apply()
    }

    fun settings(): CompanionSettings = CompanionSettings(
        name = prefs.getString("name", "Milo") ?: "Milo",
        tone = prefs.getString("tone", "Doux") ?: "Doux",
        ageGroup = prefs.getString("ageGroup", "Ado") ?: "Ado",
        theme = prefs.getString("theme", "Lavande") ?: "Lavande",
        reminders = prefs.getBoolean("reminders", true),
        reminderHour = prefs.getInt("reminderHour", 20)
    )

    fun saveSettings(s: CompanionSettings) {
        prefs.edit().putString("name", s.name).putString("tone", s.tone)
            .putString("ageGroup", s.ageGroup).putString("theme", s.theme)
            .putBoolean("reminders", s.reminders).putInt("reminderHour", s.reminderHour).apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createChannel()
        setContent { JournalApp(this) }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("journal_reminders", "Rappels du journal",
                NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Rappels doux pour écrire dans ton journal"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

private val palettes = mapOf(
    "Lavande" to listOf(Color(0xFFF6F0FF), Color(0xFF6D4C9A), Color(0xFFE8DDF8)),
    "Rose" to listOf(Color(0xFFFFF1F5), Color(0xFFAD456E), Color(0xFFF9D6E3)),
    "Océan" to listOf(Color(0xFFECF8FC), Color(0xFF176B87), Color(0xFFCDECF4)),
    "Forêt" to listOf(Color(0xFFF0F7EF), Color(0xFF3D7047), Color(0xFFD9EAD7)),
    "Soleil" to listOf(Color(0xFFFFF8E6), Color(0xFF98651B), Color(0xFFF7E5B5)),
    "Nuit" to listOf(Color(0xFF171A2B), Color(0xFFB9A7FF), Color(0xFF292E49))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalApp(context: Context) {
    val store = remember { JournalStore(context) }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(store.entries()) }
    var settings by remember { mutableStateOf(store.settings()) }
    var tab by remember { mutableStateOf(0) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf("🙂") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var companionMessage by remember { mutableStateOf("Je suis là, à ton rythme. Tu peux déposer ici ce que tu as sur le cœur.") }
    var showCompanion by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showEntry by remember { mutableStateOf<JournalEntry?>(null) }
    val palette = palettes[settings.theme] ?: palettes.getValue("Lavande")
    val dark = settings.theme == "Nuit"

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && settings.reminders) sendReminder(context, settings.name)
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme(
        primary = palette[1], background = palette[0], surface = Color(0xFF20243A),
        onBackground = Color(0xFFF6F2FF), onSurface = Color(0xFFF6F2FF)
    ) else lightColorScheme(
        primary = palette[1], background = palette[0], surface = Color.White,
        onBackground = Color(0xFF252238), onSurface = Color(0xFF252238)
    )) {
        Scaffold(
            containerColor = palette[0],
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Mon petit journal", fontWeight = FontWeight.Bold)
                            Text("Un endroit rien qu'à toi", fontSize = 12.sp)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Tune, contentDescription = "Personnaliser")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = palette[0])
                )
            },
            bottomBar = {
                NavigationBar(containerColor = palette[2]) {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(Icons.Default.EditNote, null) }, label = { Text("Écrire") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.Book, null) }, label = { Text("Mes pages") })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                        icon = { Icon(Icons.Default.Favorite, null) }, label = { Text(settings.name) })
                }
            }
        ) { padding ->
            when (tab) {
                0 -> WriteScreen(
                    Modifier.padding(padding), title, { title = it }, body, { body = it }, mood,
                    { mood = it }, palette, settings.name,
                    onSave = {
                        if (body.isNotBlank()) {
                            val entry = JournalEntry(id = editingId ?: UUID.randomUUID().toString(),
                                title = title.ifBlank { "Une page de mon cœur" }, body = body,
                                createdAt = System.currentTimeMillis(), mood = mood)
                            entries = (listOf(entry) + entries.filterNot { it.id == entry.id })
                            store.saveEntries(entries)
                            val response = CompanionBrain.respond(body, settings)
                            companionMessage = response
                            title = ""; body = ""; mood = "🙂"; editingId = null
                            if (response.second) showCompanion = true
                            else { tab = 1 }
                        }
                    }
                )
                1 -> EntriesScreen(Modifier.padding(padding), entries, palette,
                    onOpen = { showEntry = it },
                    onDelete = { e -> entries = entries.filterNot { it.id == e.id }; store.saveEntries(entries) },
                    onEdit = { e ->
                        title = e.title; body = e.body; mood = e.mood; editingId = e.id; tab = 0
                    })
                else -> CompanionScreen(Modifier.padding(padding), settings, palette,
                    companionMessage, { showCompanion = true }, { showSettings = true })
            }
        }

        if (showCompanion) {
            AlertDialog(
                onDismissRequest = { showCompanion = false },
                title = { Text("${settings.name} est là 💛") },
                text = { Text(companionMessage) },
                confirmButton = { TextButton(onClick = { showCompanion = false; tab = 2 }) { Text("Lui parler") } },
                dismissButton = { TextButton(onClick = { showCompanion = false }) { Text("Pas maintenant") } },
                containerColor = palette[2]
            )
        }

        if (showSettings) {
            SettingsDialog(settings, palette, onDismiss = { showSettings = false }) { updated ->
                settings = updated
                store.saveSettings(updated)
                if (updated.reminders && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else if (updated.reminders) sendReminder(context, updated.name)
            }
        }

        showEntry?.let { e ->
            AlertDialog(
                onDismissRequest = { showEntry = null },
                title = { Text(e.title) },
                text = { Column {
                    Text("${e.mood}  •  ${formatDate(e.createdAt)}", color = palette[1])
                    Spacer(Modifier.height(12.dp))
                    Text(e.body)
                } },
                confirmButton = { TextButton(onClick = { showEntry = null }) { Text("Fermer") } },
                containerColor = palette[2]
            )
        }
    }
}

@Composable
fun WriteScreen(
    modifier: Modifier, title: String, onTitle: (String) -> Unit, body: String,
    onBody: (String) -> Unit, mood: String, onMood: (String) -> Unit,
    palette: List<Color>, companionName: String, onSave: () -> Unit
) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 18.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Spacer(Modifier.height(14.dp))
            Card(colors = CardDefaults.cardColors(containerColor = palette[2]), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Une petite pause pour toi 🌱", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text("$companionName te laisse la place. Écris juste quelques lignes, même si tout est en désordre.")
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Comment tu te sens ?", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("😊", "🙂", "😐", "😔", "😣", "😡", "🥹").forEach { face ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (mood == face) palette[2] else Color.Transparent,
                        modifier = Modifier.clickable { onMood(face) }
                    ) { Text(face, fontSize = 23.sp, modifier = Modifier.padding(7.dp)) }
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(value = title, onValueChange = onTitle,
                label = { Text("Un titre (facultatif)") }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp), singleLine = true)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = body, onValueChange = onBody,
                label = { Text("Qu'est-ce que tu as envie de raconter ?") },
                placeholder = { Text("Aujourd'hui, j'ai pensé à…") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                shape = RoundedCornerShape(18.dp))
            Spacer(Modifier.height(8.dp))
            Text("Tes pages restent sur cet appareil dans cette version.", fontSize = 12.sp, color = palette[1])
            Spacer(Modifier.height(14.dp))
            Button(onClick = onSave, enabled = body.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.FavoriteBorder, null)
                Spacer(Modifier.width(8.dp))
                Text("Garder cette page")
            }
        }
    }
}

@Composable
fun EntriesScreen(
    modifier: Modifier, entries: List<JournalEntry>, palette: List<Color>,
    onOpen: (JournalEntry) -> Unit, onDelete: (JournalEntry) -> Unit, onEdit: (JournalEntry) -> Unit
) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(14.dp))
        Text("Tes petits morceaux de vie", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("${entries.size} page(s) dans ton journal", color = palette[1])
        Spacer(Modifier.height(12.dp))
        if (entries.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = palette[2])) {
                Column(Modifier.padding(20.dp)) {
                    Text("La première page t'attend ✨", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Reviens dans « Écrire » quand tu veux déposer quelques pensées.")
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                items(entries, key = { it.id }) { e ->
                    Card(colors = CardDefaults.cardColors(containerColor = palette[2]),
                        shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().clickable { onOpen(e) }) {
                        Column(Modifier.padding(15.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(e.mood, fontSize = 25.sp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(formatDate(e.createdAt), fontSize = 12.sp, color = palette[1])
                                }
                                IconButton(onClick = { onEdit(e) }) { Icon(Icons.Default.Edit, "Modifier") }
                                IconButton(onClick = { onDelete(e) }) { Icon(Icons.Default.DeleteOutline, "Supprimer") }
                            }
                            Text(e.body, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompanionScreen(
    modifier: Modifier, settings: CompanionSettings, palette: List<Color>,
    message: String, onTalk: () -> Unit, onCustomize: () -> Unit
) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Ton petit compagnon", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = palette[2]), shape = RoundedCornerShape(26.dp)) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("♡", fontSize = 52.sp, color = palette[1])
                Text(settings.name, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("${settings.tone} • ${settings.ageGroup}", color = palette[1])
                Spacer(Modifier.height(14.dp))
                Text(message, fontSize = 17.sp)
                Spacer(Modifier.height(18.dp))
                Button(onClick = onTalk, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.ChatBubbleOutline, null)
                    Spacer(Modifier.width(8.dp)); Text("Prendre un petit moment")
                }
            }
        }
        OutlinedButton(onClick = onCustomize, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Palette, null); Spacer(Modifier.width(8.dp)); Text("Personnaliser mon journal")
        }
        Text("Ton compagnon n'est pas là pour te juger. Tu peux aussi fermer l'application et parler à une personne de confiance.", fontSize = 13.sp)
    }
}

@Composable
fun SettingsDialog(
    settings: CompanionSettings, palette: List<Color>, onDismiss: () -> Unit,
    onSave: (CompanionSettings) -> Unit
) {
    var name by remember(settings.name) { mutableStateOf(settings.name) }
    var tone by remember(settings.tone) { mutableStateOf(settings.tone) }
    var age by remember(settings.ageGroup) { mutableStateOf(settings.ageGroup) }
    var theme by remember(settings.theme) { mutableStateOf(settings.theme) }
    var reminders by remember(settings.reminders) { mutableStateOf(settings.reminders) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("À ton image ✨") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.heightIn(max = 520.dp)) {
                item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom du compagnon") }, singleLine = true) }
                item { Text("Sa personnalité", fontWeight = FontWeight.Bold) }
                item { ChoiceRow(listOf("Doux", "Drôle", "Calme", "Motivant"), tone) { tone = it } }
                item { Text("Pour qui ?", fontWeight = FontWeight.Bold) }
                item { ChoiceRow(listOf("Enfant", "Ado", "Adulte"), age) { age = it } }
                item { Text("Thème", fontWeight = FontWeight.Bold) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("Lavande", "Rose", "Océan", "Forêt", "Soleil", "Nuit").chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                row.forEach { t ->
                                    FilterChip(selected = theme == t, onClick = { theme = t },
                                        label = { Text(t, fontSize = 11.sp) })
                                }
                            }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Petit rappel quotidien")
                            Text("Une notification douce pour écrire", fontSize = 12.sp)
                        }
                        Switch(checked = reminders, onCheckedChange = { reminders = it })
                    }
                }
                item { Text("Le journal s'adapte aux préférences choisies. Les thèmes ne sont pas réservés à un genre.", fontSize = 12.sp, color = palette[1]) }
            }
        },
        confirmButton = { TextButton(onClick = {
            onSave(settings.copy(name = name.trim().ifBlank { "Milo" }, tone = tone, ageGroup = age, theme = theme, reminders = reminders))
            onDismiss()
        }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        containerColor = palette[2]
    )
}

@Composable
fun ChoiceRow(options: List<String>, selected: String, onChoose: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        options.forEach { option ->
            FilterChip(selected = selected == option, onClick = { onChoose(option) },
                label = { Text(option, fontSize = 11.sp) })
        }
    }
}

object CompanionBrain {
    private val heavyWords = listOf(
        "épuis", "fatigu", "j'en peux plus", "je n'en peux plus", "trop lourd", "déprim",
        "triste", "seul", "seule", "angoiss", "stress", "peur", "pleur", "désesp",
        "nul", "honte", "perdu", "perdue", "personne ne", "ça va pas", "mal au cœur",
        "envie de disparaître", "je craque", "à bout"
    )
    private val urgentWords = listOf("me suicider", "suicide", "me faire du mal", "en finir", "me tuer")

    fun respond(text: String, settings: CompanionSettings): Pair<String, Boolean> {
        val t = text.lowercase(Locale.FRENCH)
        val urgent = urgentWords.any { t.contains(it) }
        val heavy = heavyWords.count { t.contains(it) } >= 1 || text.length > 1100
        if (urgent) return Pair(
            "Je suis content que tu aies écrit ce que tu traverses. Je ne peux pas évaluer ta sécurité, mais tu mérites de l'aide humaine maintenant. Si tu risques de te faire du mal, éloigne-toi de ce qui pourrait te blesser et contacte immédiatement une personne de confiance qui peut rester avec toi ou les services d'urgence locaux. Est-ce que tu es en danger immédiat ?",
            true
        )
        if (heavy) {
            val opener = when (settings.tone) {
                "Drôle" -> "Je range les blagues deux minutes et je t'écoute vraiment."
                "Motivant" -> "Tu n'as pas besoin de tout régler d'un coup."
                "Calme" -> "On peut ralentir un instant, sans rien forcer."
                else -> "Ça a l'air de prendre beaucoup de place dans ton cœur."
            }
            return Pair("$opener ${settings.name} est là. Tu veux me raconter ce qui pèse le plus, ou préfères-tu juste faire une pause et respirer un peu ? Tu peux aussi choisir « pas maintenant ». ", true)
        }
        val replies = when (settings.tone) {
            "Drôle" -> listOf("Page déposée, cerveau un peu moins en mode 37 onglets ? 😄", "Merci pour la chronique du jour. Je garde la place pour la suite !")
            "Motivant" -> listOf("Chaque petite page compte. Continue à ton rythme.", "Tu viens de prendre un moment pour toi. C'est déjà quelque chose.")
            "Calme" -> listOf("Merci de m'avoir confié ce petit morceau de ta journée.", "Tu n'as pas besoin de trouver les mots parfaits ici.")
            else -> listOf("Merci de m'avoir raconté ça. 💛", "Je suis là pour accueillir tes pensées, sans jugement.")
        }
        return Pair(replies[(text.length + t.length) % replies.size], false)
    }
}

fun formatDate(time: Long): String =
    SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.getDefault()).format(Date(time))

fun sendReminder(context: Context, name: String) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    val notification = NotificationCompat.Builder(context, "journal_reminders")
        .setSmallIcon(android.R.drawable.ic_menu_edit)
        .setContentTitle("Un petit moment pour toi 💌")
        .setContentText("$name te rappelle que quelques lignes suffisent.")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()
    try { NotificationManagerCompat.from(context).notify(101, notification) } catch (_: SecurityException) { }
}
