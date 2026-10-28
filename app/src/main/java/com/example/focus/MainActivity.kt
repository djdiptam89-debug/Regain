package com.example.focus

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { FocusApp() }
            }
        }
    }
}

@Composable
fun FocusApp() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("focus", Context.MODE_PRIVATE) }
    val today = LocalDate.now().toString()
    val yesterday = LocalDate.now().minusDays(1).toString()

    var goal by remember { mutableIntStateOf(prefs.getInt("goal", 4)) }
    var done by remember {
        mutableIntStateOf(if (prefs.getString("doneDate", "") == today) prefs.getInt("done", 0) else 0)
    }
    var lastGoalDate by remember { mutableStateOf(prefs.getString("lastGoalDate", "") ?: "") }
    var streak by remember { mutableIntStateOf(prefs.getInt("streak", 0)) }
    val shownStreak = if (lastGoalDate == today || lastGoalDate == yesterday) streak else 0

    var isBreak by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var left by remember { mutableLongStateOf(25 * 60L) }
    var endAt by remember { mutableLongStateOf(0L) }

    fun save() {
        prefs.edit()
            .putInt("goal", goal)
            .putInt("done", done)
            .putString("doneDate", today)
            .putInt("streak", streak)
            .putString("lastGoalDate", lastGoalDate)
            .apply()
    }

    fun finishSession() {
        if (!isBreak) {
            done += 1
            if (done >= goal && lastGoalDate != today) {
                streak = if (lastGoalDate == yesterday) streak + 1 else 1
                lastGoalDate = today
            }
            save()
        }
        isBreak = !isBreak
        left = if (isBreak) 5 * 60L else 25 * 60L
    }

    LaunchedEffect(running) {
        while (running) {
            val r = (endAt - System.currentTimeMillis() + 999) / 1000
            if (r <= 0) {
                running = false
                finishSession()
            } else {
                left = r
            }
            delay(250)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(if (isBreak) "Break (5 min)" else "Focus (25 min)", fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            String.format("%02d:%02d", left / 60, left % 60),
            fontSize = 72.sp
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                if (running) {
                    running = false
                } else {
                    endAt = System.currentTimeMillis() + left * 1000
                    running = true
                }
            }) { Text(if (running) "Pause" else "Start") }
            OutlinedButton(onClick = {
                running = false
                left = if (isBreak) 5 * 60L else 25 * 60L
            }) { Text("Reset") }
        }
        Spacer(Modifier.height(40.dp))
        Text("Today's goal: $done / $goal sessions", fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (done.toFloat() / goal).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = { if (goal > 1) { goal -= 1; save() } }) { Text("−") }
            Text("Goal: $goal")
            OutlinedButton(onClick = { goal += 1; save() }) { Text("+") }
        }
        Spacer(Modifier.height(24.dp))
        Text("🔥 Streak: $shownStreak days", fontSize = 22.sp)
    }
}
