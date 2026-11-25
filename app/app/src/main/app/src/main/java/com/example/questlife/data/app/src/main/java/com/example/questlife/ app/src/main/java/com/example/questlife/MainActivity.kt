
package com.example.questlife

import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.questlife.ui.CharacterScreen
import com.example.questlife.ui.FinanceScreen
import com.example.questlife.ui.HabitsScreen
import com.example.questlife.ui.ShopScreen
import java.io.File

class App : Application() {
    val database by lazy { com.example.questlife.data.AppDatabase.get(this) }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // «Чёрный ящик»: если приложение падает — сохраняем текст ошибки в файл
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                File(filesDir, "crash.txt").writeText(
                    "Поток: " + thread.name + "\n\n" + Log.getStackTraceString(error)
                )
            } catch (_: Exception) {
            }
            defaultHandler?.uncaughtException(thread, error)
        }

        // 1. Пытаемся создать «мозг» приложения. Если падает — запоминаем ошибку.
        var startupError: String? = null
        val vm: AppViewModel? = try {
            ViewModelProvider(this)[AppViewModel::class.java]
        } catch (t: Throwable) {
            startupError = Log.getStackTraceString(t)
            null
        }

        // 2. Если приложение уже падало раньше — читаем сохранённый отчёт.
        val oldReport = try {
            val f = File(filesDir, "crash.txt")
            if (f.exists()) f.readText() else null
        } catch (_: Exception) {
            null
        }

        val report = startupError ?: oldReport

        // 3. Показываем либо отчёт об ошибке, либо само приложение.
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var dismissed by remember { mutableStateOf(false) }
                when {
                    report != null && !dismissed -> CrashReportScreen(report) {
                        try { File(filesDir, "crash.txt").delete() } catch (_: Exception) {}
                        recreate()
                    }
                    vm != null -> AppRoot(vm)
                    else -> Text("Ошибка запуска приложения", Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
fun CrashReportScreen(report: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("⚠️ Отчёт об ошибке", fontSize = 22.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Приложение не смогло запуститься. Нажми «Скопировать» и пришли мне этот текст (или пришли скриншот).",
            fontSize = 14.sp
        )
        Spacer(Modifier.height(8.dp))
        SelectionContainer {
            Text(
                report,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { clipboard.setText(AnnotatedString(report)) }) {
                Text("📋 Скопировать")
            }
            OutlinedButton(onClick = onDismiss) {
                Text("Закрыть отчёт")
            }
        }
    }
}

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.message.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                listOf("🧙" to "Персонаж", "✅" to "Привычки", "💰" to "Финансы", "🛒" to "Лавка")
                    .forEachIndexed { i, (icon, label) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Text(icon) },
                            label = { Text(label) }
                        )
                    }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> CharacterScreen(vm)
                1 -> HabitsScreen(vm)
                2 -> FinanceScreen(vm)
                else -> ShopScreen(vm)
            }
        }
    }
}
