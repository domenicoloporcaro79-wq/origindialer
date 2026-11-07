package com.example.origindialer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.util.Date

private val Blue = Color(0xFF2F6BFF)
private val Green = Color(0xFF1DB954)
private val KeyBg = Color(0xFFF1F3F7)
private val Red = Color(0xFFE5484D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial = intent?.data?.schemeSpecificPart.orEmpty()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Blue)) {
                DialerApp(initial)
            }
        }
    }
}

data class CallItem(val number: String, val name: String?, val type: Int, val date: Long)

@Composable
fun DialerApp(initialNumber: String) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var number by remember { mutableStateOf(initialNumber) }
    var recents by remember { mutableStateOf(listOf<CallItem>()) }

    fun has(p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun loadRecents() {
        if (!has(Manifest.permission.READ_CALL_LOG)) return
        val list = mutableListOf<CallItem>()
        ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE, CallLog.Calls.DATE
            ),
            null, null, "${CallLog.Calls.DATE} DESC"
        )?.use { c ->
            while (c.moveToNext() && list.size < 50) {
                list += CallItem(c.getString(0).orEmpty(), c.getString(1), c.getInt(2), c.getLong(3))
            }
        }
        recents = list
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { loadRecents() }

    LaunchedEffect(Unit) {
        permLauncher.launch(
            arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CALL_LOG)
        )
    }

    fun call(n: String) {
        if (n.isBlank()) return
        val uri = Uri.parse("tel:" + Uri.encode(n))
        val action = if (has(Manifest.permission.CALL_PHONE)) Intent.ACTION_CALL else Intent.ACTION_DIAL
        ctx.startActivity(Intent(action, uri))
    }

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Text("⌗", fontSize = 22.sp) }, label = { Text("Tastierino") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1; loadRecents() },
                    icon = { Text("↺", fontSize = 22.sp) }, label = { Text("Recenti") }
                )
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (tab == 0) Keypad(number, { number = it }, ::call) else Recents(recents, ::call)
        }
    }
}

@Composable
fun Keypad(number: String, onChange: (String) -> Unit, onCall: (String) -> Unit) {
    val rows = listOf(
        listOf("1" to "", "2" to "ABC", "3" to "DEF"),
        listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
        listOf("*" to "", "0" to "+", "#" to "")
    )
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                number, fontSize = 36.sp, fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center, maxLines = 1
            )
        }
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { (d, l) -> Key(d, l) { onChange(number + d) } }
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.size(76.dp))
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(Green)
                    .clickable { onCall(number) },
                contentAlignment = Alignment.Center
            ) { Text("☎", fontSize = 32.sp, color = Color.White) }
            Box(
                Modifier.size(76.dp).clip(CircleShape)
                    .clickable { if (number.isNotEmpty()) onChange(number.dropLast(1)) },
                contentAlignment = Alignment.Center
            ) { Text("⌫", fontSize = 28.sp, color = Color.Gray) }
        }
    }
}

@Composable
fun Key(digit: String, letters: String, onClick: () -> Unit) {
    Box(
        Modifier.size(76.dp).clip(CircleShape).background(KeyBg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(digit, fontSize = 28.sp, fontWeight = FontWeight.Medium)
            if (letters.isNotEmpty()) Text(letters, fontSize = 9.sp, color = Color.Gray)
        }
    }
}

@Composable
fun Recents(items: List<CallItem>, onCall: (String) -> Unit) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nessuna chiamata recente", color = Color.Gray)
        }
        return
    }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    LazyColumn(Modifier.fillMaxSize()) {
        items(items) { c ->
            val missed = c.type == CallLog.Calls.MISSED_TYPE
            Row(
                Modifier.fillMaxWidth().clickable { onCall(c.number) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        c.name?.takeIf { it.isNotBlank() } ?: c.number,
                        fontSize = 17.sp, color = if (missed) Red else Color.Black
                    )
                    Text(fmt.format(Date(c.date)), fontSize = 13.sp, color = Color.Gray)
                }
                Text("☎", color = Blue, fontSize = 20.sp)
            }
        }
    }
}
