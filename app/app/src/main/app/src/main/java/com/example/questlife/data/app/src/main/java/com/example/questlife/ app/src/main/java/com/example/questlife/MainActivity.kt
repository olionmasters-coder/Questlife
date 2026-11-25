
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class App : Application() {

    val database by lazy { com.example.questlife.data.AppDatabase.get(this) }

    override fun onCreate() {
        // «Чёрный ящик» — включается раньше всего
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
        logStep(this, "4. super.onCreate() выполнен")

        // Читаем прошлый отчёт, если он был
        val oldReport = try {
            val f = File(filesDir, "crash.txt")
            if (f.exists()) f.readText() else null
        } catch (_: Exception) {
            null
        }

        // Номер версии — чтобы убедиться, что стоит свежая сборка
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                if (oldReport != null) {
                    CrashReportScreen(oldReport, this@MainActivity) {
                        try { File(filesDir, "crash.txt").delete() } catch (_: Exception) {}
                        recreate()
                    }
                } else {
                    Surface(Modifier.fillMaxSize()) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("ТЕСТ $version", fontSize = 34.sp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Если ты видишь этот текст — значит запуск приложения работает.",
                                fontSize = 15.sp
                            )
                        }
                    }
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
            "Нажми «📋 Скопировать» и пришли мне весь текст (или пришли скриншот).",
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
