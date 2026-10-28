package com.example.focus

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.ZoneId

class HomeActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { AppRoot(tick) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tick++
    }
}

@Composable
fun AppRoot(tick: Int) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Timer") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Screen time") })
        }
        Box(Modifier.weight(1f)) {
            Box(if (tab == 0) Modifier.fillMaxSize() else Modifier.size(0.dp)) {
                FocusApp()
            }
            if (tab == 1) UsageScreen(tick)
        }
    }
}

data class AppUsage(val name: String, val minutes: Long)

fun hasUsageAccess(ctx: Context): Boolean {
    val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= 29) {
        ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

fun loadUsage(ctx: Context): List<AppUsage> {
    val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val stats = usm.queryUsageStats(
        UsageStatsManager.INTERVAL_DAILY, start, System.currentTimeMillis()
    ) ?: return emptyList()
    val pm = ctx.packageManager
    val result = mutableListOf<AppUsage>()
    val totals = stats
        .filter { it.lastTimeUsed >= start }
        .groupBy { it.packageName }
        .mapValues { entry -> entry.value.sumOf { it.totalTimeInForeground } }
    for ((pkg, ms) in totals) {
        if (ms < 60_000) continue
        if (pm.getLaunchIntentForPackage(pkg) == null) continue
        val label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
        result.add(AppUsage(label, ms / 60_000))
    }
    return result.sortedByDescending { it.minutes }
}

fun fmt(m: Long): String =
    if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"

@Composable
fun UsageScreen(tick: Int) {
    val ctx = LocalContext.current
    var allowed by remember { mutableStateOf(false) }
    var list by remember { mutableStateOf(listOf<AppUsage>()) }
    var manual by remember { mutableIntStateOf(0) }

    LaunchedEffect(tick, manual) {
        allowed = hasUsageAccess(ctx)
        list = if (allowed) loadUsage(ctx) else emptyList()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (!allowed) {
            Text("Usage Access permission is needed to show screen time.", fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            Text("Tap the button, pick Regain in the list, turn the permission on, then come back.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }) { Text("Grant permission") }
        } else {
            val total = list.sumOf { it.minutes }
            Text("Today: ${fmt(total)}", fontSize = 24.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { manual++ }) { Text("Refresh") }
            Spacer(Modifier.height(8.dp))
            if (list.isEmpty()) {
                Text("No data yet.")
            }
            LazyColumn {
                items(list) { app ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(app.name, Modifier.weight(1f))
                        Text(fmt(app.minutes))
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
