package com.example.questlife

import android.app.Application
import android.content.Context
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class App : Application() {

    val database by lazy { com.example.questlife.data.AppDatabase.get(this) }

    override fun onCreate() {
        // «Чёрный ящик» включаем В САМОМ НАЧАЛЕ запуска приложения
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val text = "Поток: " + thread.name + "\n\n" + Log.getStackTraceString(error)
                File(filesDir, "crash.txt").writeText(text)
                logStep(this, "!!! ПАДЕНИЕ: " + error.javaClass.name)
            } catch (_: Exception) {
            }
            defaultHandler?.uncaughtException(thread, error)
        }
        logStep(this, "1. Приложение начинает запускаться")
        super.onCreate()
        logStep(this, "2. Application готово")
    }
}

// Журнал шагов: помогает понять, докуда дошёл запуск
fun logStep(context: Context, text: String) {
    try {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        File(context.filesDir, "steps.txt").appendText("[$time] $text\n")
    } catch (_: Exception) {
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        logStep(this, "3. Открыт экран MainActivity")
        super.onCreate(savedInstanceState)
        logStep(this, "4. Система готова, начинаем строить приложение")

        // Пытаемся создать «мозг» приложения; любую ошибку запоминаем
        var startupError: String? = null
        val vm: AppViewModel? = try {
            ViewModelProvider(this)[AppViewModel::class.java]
        } catch (t: Throwable) {
            startupError = Log.getStackTraceString(t)
            null
        }
        logStep(this, if (vm != null) "5. Мозг приложения создан успешно" else "5. ОШИБКА при создании мозга")

        // Если приложение уже падало — читаем сохранённый отчёт
        val oldReport = try {
            val f = File(filesDir, "crash.txt")
            if (f.exists()) f.readText() else null
        } catch (_: Exception) {
            null
        }

        val report = startupError ?: oldReport
        logStep(this, "6. Готовимся показать интерфейс")

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var dismissed by remember { mutableStateOf(false) }
                when {
                    report != null && !dismissed -> CrashReportScreen(report, this@MainActivity) {
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
fun CrashReportScreen(report: String, context: Context, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val steps = try {
        val f = File(context.filesDir, "steps.txt")
        if (f.exists()) f.readText() else "(журнал пуст)"
    } catch (_: Exception) {
        "(журнал недоступен)"
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("⚠️ Отчёт об ошибке", fontSize = 22.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Нажми «📋 Скопировать» и пришли мне весь текст (или пришли скриншот этого экрана).",
            fontSize = 14.sp
        )
        Spacer(Modifier.height(8.dp))
        SelectionContainer(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Column {
                Text("=== ЖУРНАЛ ЗАПУСКА ===", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text(steps, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(8.dp))
                Text("=== ТЕКСТ ОШИБКИ ===", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text(report, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                clipboard.setText(AnnotatedString("=== ЖУРНАЛ ===\n$steps\n\n=== ОШИБКА ===\n$report"))
            }) {
                Text("📋 Скопировать")
            }
            OutlinedButton(onClick = onDismiss) {
                Text("Закрыть и запустить заново")
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
