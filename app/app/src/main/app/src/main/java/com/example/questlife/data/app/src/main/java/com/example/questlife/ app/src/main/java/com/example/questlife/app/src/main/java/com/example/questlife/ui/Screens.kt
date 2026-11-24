package com.example.questlife.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.questlife.AppViewModel
import com.example.questlife.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val expenseCategories = listOf("🍔 Еда", "🚕 Транспорт", "🏠 Дом", "🎮 Развлечения", "💊 Здоровье", "👕 Одежда", "📦 Другое")
private val incomeCategories = listOf("💼 Работа", "🎁 Подарок", "📈 Инвестиции", "📦 Другое")

fun money(a: Double): String =
    if (a % 1.0 == 0.0) "%,d ₽".format(a.toLong()) else "%,.2f ₽".format(a)

// ================= ПЕРСОНАЖ =================

@Composable
fun CharacterScreen(vm: AppViewModel) {
    val c by vm.character.collectAsState()
    var rename by remember { mutableStateOf(false) }
    val need = 50 + c.level * 50

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Text(vm.emojiFor(c.level), fontSize = 96.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.name, style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = { rename = true }) { Text("✏️") }
        }
        Text("Уровень ${c.level}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))

        StatBar("❤️ Здоровье: ${c.hp}/${c.maxHp}", c.hp.toFloat(), c.maxHp.toFloat(), Color(0xFFEF5350))
        StatBar("⭐ Опыт: ${c.xp}/${need}", c.xp.toFloat(), need.toFloat(), Color(0xFFAB47BC))
        Spacer(Modifier.height(8.dp))

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🪙", fontSize = 32.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Токены: ${c.tokens}", style = MaterialTheme.typography.titleMedium)
                    Text("Зарабатывай привычками — трать в Лавке", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (c.hp <= 0) {
            Spacer(Modifier.height(8.dp))
            Text("💀 Персонаж без сознания! Хорошие привычки его вылечат.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.weight(1f))
        Card(Modifier.fillMaxWidth()) {
            Text(
                "Правила игры:\n• Полезные привычки лечат персонажа, дают опыт и токены.\n" +
                "• Вредная еда и привычки наносят урон и стоят токенов.\n" +
                "• С уровнем растёт макс. здоровье, персонаж эволюционирует.",
                Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    if (rename) {
        var newName by remember { mutableStateOf(c.name) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Имя персонажа") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(onClick = { if (newName.isNotBlank()) vm.renameCharacter(newName); rename = false }) { Text("ОК") }
            },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("Отмена") } }
        )
    }
}

@Composable
fun StatBar(label: String, value: Float, max: Float, color: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(
            progress = { (value / max).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(10.dp),
            color = color
        )
    }
}

// ================= ПРИВЫЧКИ =================

@Composable
fun HabitsScreen(vm: AppViewModel) {
    val habits by vm.habits.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Habit?>(null) }

    Box(Modifier.fillMaxSize()) {
        if (habits.isEmpty()) {
            Text("Пока нет привычек. Нажмите +", modifier = Modifier.align(Alignment.Center))
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(habits, key = { it.id }) { h ->
                HabitCard(
                    h = h,
                    onDo = { if (h.type == HabitType.GOOD) vm.completeGood(h) else vm.doBad(h) },
                    onEdit = { editing = h },
                    onDelete = { vm.deleteHabit(h) }
                )
            }
        }
        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Text("+", fontSize = 24.sp) }
    }

    if (showAdd || editing != null) {
        HabitDialog(
            initial = editing,
            onDismiss = { showAdd = false; editing = null },
            onSave = { vm.saveHabit(it); showAdd = false; editing = null }
        )
    }
}

@Composable
fun HabitCard(h: Habit, onDo: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val doneToday = if (h.lastDay == LocalDate.now().toString()) h.doneToday else 0
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(h.emoji, fontSize = 28.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (h.type == HabitType.GOOD)
                            "+${h.hpEffect}❤️ +${h.xpReward}⭐ +${h.tokenValue}🪙 · сегодня $doneToday/${h.timesPerDay}" +
                                if (h.streak > 0) " · 🔥 ${h.streak}" else ""
                        else
                            "☠️ урон ${h.hpEffect}❤️ · цена ${h.tokenValue}🪙",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = onEdit) { Text("✏️") }
                IconButton(onClick = onDelete) { Text("🗑") }
            }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onDo,
                modifier = Modifier.fillMaxWidth(),
                enabled = h.type == HabitType.BAD || doneToday < h.timesPerDay
            ) {
                Text(if (h.type == HabitType.GOOD) "Выполнить" else "Сделать (−${h.tokenValue} 🪙)")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDialog(initial: Habit?, onDismiss: () -> Unit, onSave: (Habit) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "✨") }
    var isGood by remember { mutableStateOf(initial?.type != HabitType.BAD) }
    var hp by remember { mutableStateOf((initial?.hpEffect ?: 5).toString()) }
    var xp by remember { mutableStateOf((initial?.xpReward ?: 10).toString()) }
    var tokens by remember { mutableStateOf((initial?.tokenValue ?: 5).toString()) }
    var perDay by remember { mutableStateOf((initial?.timesPerDay ?: 1).toString()) }
    val base = initial ?: Habit()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новая привычка" else "Изменить привычку") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = emoji, onValueChange = { emoji = it.take(4) }, label = { Text("Эмодзи") })
                Row {
                    FilterChip(selected = isGood, onClick = { isGood = true }, label = { Text("✅ Полезная") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = !isGood, onClick = { isGood = false }, label = { Text("☠️ Вредная") })
                }
                OutlinedTextField(value = hp, onValueChange = { hp = it.filter(Char::isDigit) }, label = { Text(if (isGood) "Лечение ❤️" else "Урон ❤️") })
                if (isGood) {
                    OutlinedTextField(value = xp, onValueChange = { xp = it.filter(Char::isDigit) }, label = { Text("Опыт ⭐") })
                    OutlinedTextField(value = perDay, onValueChange = { perDay = it.filter(Char::isDigit) }, label = { Text("Раз в день (макс.)") })
                }
                OutlinedTextField(value = tokens, onValueChange = { tokens = it.filter(Char::isDigit) }, label = { Text(if (isGood) "Награда 🪙" else "Цена 🪙") })
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) return@Button
                onSave(base.copy(
                    id = initial?.id ?: 0L,
                    name = name,
                    emoji = emoji.ifBlank { "✨" },
                    type = if (isGood) HabitType.GOOD else HabitType.BAD,
                    hpEffect = hp.toIntOrNull() ?: 0,
                    xpReward = if (isGood) xp.toIntOrNull() ?: 0 else 0,
                    tokenValue = tokens.toIntOrNull() ?: 0,
                    timesPerDay = (perDay.toIntOrNull() ?: 1).coerceAtLeast(1)
                ))
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

// ================= ФИНАНСЫ =================

@Composable
fun FinanceScreen(vm: AppViewModel) {
    val txs by vm.transactions.collectAsState()
    val goals by vm.savings.collectAsState()
    var section by rememberSaveable { mutableIntStateOf(0) }
    var showTx by remember { mutableStateOf(false) }
    var showGoal by remember { mutableStateOf(false) }

    val balance = txs.sumOf { if (it.isIncome) it.amount else -it.amount }
    val monthStart = LocalDate.now().withDayOfMonth(1)
    val monthTx = txs.filter { it.date >= monthStart }
    val monthIncome = monthTx.filter { it.isIncome }.sumOf { it.amount }
    val monthExpense = monthTx.filter { !it.isIncome }.sumOf { it.amount }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("💰 Баланс: ${money(balance)}", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Text("В этом месяце: +${money(monthIncome)} · −${money(monthExpense)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
            TabRow(selectedTabIndex = section) {
                listOf("Операции", "Анализ", "Накопления").forEachIndexed { i, t ->
                    Tab(selected = section == i, onClick = { section = i }, text = { Text(t) })
                }
            }
            when (section) {
                0 -> TxList(txs, onDelete = { vm.deleteTransaction(it) }, modifier = Modifier.weight(1f))
                1 -> Analysis(monthTx, Modifier.weight(1f))
                else -> Goals(goals, vm, Modifier.weight(1f))
            }
        }
        FloatingActionButton(
            onClick = { if (section == 2) showGoal = true else showTx = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Text("+", fontSize = 24.sp) }
    }

    if (showTx) {
        AddTransactionDialog(
            onDismiss = { showTx = false },
            onConfirm = { title, amount, isIncome, cat -> vm.addTransaction(title, amount, isIncome, cat); showTx = false }
        )
    }
    if (showGoal) {
        GoalDialog(onDismiss = { showGoal = false }, onSave = { name, emoji, target -> vm.addGoal(name, emoji, target); showGoal = false })
    }
}

@Composable
fun TxList(txs: List<Transaction>, onDelete: (Transaction) -> Unit, modifier: Modifier = Modifier) {
    if (txs.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Нет операций. Нажмите +") }
        return
    }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
        items(txs, key = { it.id }) { t ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.titleSmall)
                        Text("${t.category} · ${t.date.format(DateTimeFormatter.ofPattern("d MMM"))}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        (if (t.isIncome) "+" else "−") + money(t.amount),
                        color = if (t.isIncome) Color(0xFF66BB6A) else Color(0xFFEF5350),
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = { onDelete(t) }) { Text("🗑") }
                }
            }
        }
    }
}

@Composable
fun Analysis(monthTx: List<Transaction>, modifier: Modifier = Modifier) {
    val expenseByCat = monthTx.filter { !it.isIncome }.groupBy { it.category }
        .map { (c, l) -> c to l.sumOf { it.amount } }.sortedByDescending { it.second }
    val incomeByCat = monthTx.filter { it.isIncome }.groupBy { it.category }
        .map { (c, l) -> c to l.sumOf { it.amount } }.sortedByDescending { it.second }
    val totalExp = expenseByCat.sumOf { it.second }
    val totalInc = incomeByCat.sumOf { it.second }

    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 96.dp)) {
        Text("Расходы за месяц", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
        if (expenseByCat.isEmpty()) Text("Нет данных", style = MaterialTheme.typography.bodySmall)
        expenseByCat.forEach { (cat, sum) ->
            CategoryBar(cat, sum, if (totalExp > 0) (sum / totalExp).toFloat() else 0f, Color(0xFFEF5350))
        }
        Spacer(Modifier.height(16.dp))
        Text("Доходы за месяц", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
        if (incomeByCat.isEmpty()) Text("Нет данных", style = MaterialTheme.typography.bodySmall)
        incomeByCat.forEach { (cat, sum) ->
            CategoryBar(cat, sum, if (totalInc > 0) (sum / totalInc).toFloat() else 0f, Color(0xFF66BB6A))
        }
    }
}

@Composable
fun CategoryBar(label: String, amount: Double, fraction: Float, color: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text("${money(amount)} · ${(fraction * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = color
        )
    }
}

@Composable
fun Goals(goals: List<SavingsGoal>, vm: AppViewModel, modifier: Modifier = Modifier) {
    if (goals.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Нет целей. Нажмите +") }
        return
    }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
        items(goals, key = { it.id }) { g ->
            var deposit by remember { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${g.emoji} ${g.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.deleteGoal(g) }) { Text("🗑") }
                    }
                    Text("${money(g.saved)} / ${money(g.target)}", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(
                        progress = { (if (g.target > 0) g.saved / g.target else 0.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = Color(0xFFFFB300)
                    )
                    TextButton(onClick = { deposit = true }) { Text("Пополнить") }
                }
            }
            if (deposit) {
                AmountDialog(title = "Пополнить «${g.name}»", onDismiss = { deposit = false }) {
                    vm.addToGoal(g, it); deposit = false
                }
            }
        }
    }
}

@Composable
fun AmountDialog(title: String, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                label = { Text("Сумма") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = {
                val v = text.replace(',', '.').toDoubleOrNull() ?: return@Button
                if (v > 0) { onConfirm(v); onDismiss() }
            }) { Text("Пополнить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionDialog(onDismiss: () -> Unit, onConfirm: (String, Double, Boolean, String) -> Unit) {
    var isIncome by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(expenseCategories.first()) }
    val cats = if (isIncome) incomeCategories else expenseCategories

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая операция") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    FilterChip(selected = !isIncome, onClick = { isIncome = false; category = expenseCategories.first() }, label = { Text("− Расход") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = isIncome, onClick = { isIncome = true; category = incomeCategories.first() }, label = { Text("+ Доход") })
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("Сумма") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Описание") }, modifier = Modifier.fillMaxWidth())
                Text("Категория", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    cats.forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val a = amount.replace(',', '.').toDoubleOrNull() ?: return@Button
                if (a > 0) { onConfirm(title.ifBlank { category }, a, isIncome, category); onDismiss() }
            }) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun GoalDialog(onDismiss: () -> Unit, onSave: (String, String, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🎯") }
    var target by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая цель накопления") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название (напр. «Отпуск»)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = emoji, onValueChange = { emoji = it.take(4) }, label = { Text("Эмодзи") })
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("Цель, ₽") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val t = target.replace(',', '.').toDoubleOrNull() ?: return@Button
                if (name.isNotBlank() && t > 0) { onSave(name, emoji, t); onDismiss() }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

// ================= ЛАВКА =================

@Composable
fun ShopScreen(vm: AppViewModel) {
    val c by vm.character.collectAsState()
    val items by vm.indulgences.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("🪙 ${c.tokens}", style = MaterialTheme.typography.headlineMedium)
            Text("Покупай льготы за токены. Часть из них наносит урон персонажу — честно плати и здоровьем!",
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))

            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🧪", fontSize = 28.sp)
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text("Зелье лечения", style = MaterialTheme.typography.titleMedium)
                        Text("+30 ❤️ за 30 🪙", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { vm.buyPotion() }, enabled = c.tokens >= 30) { Text("Купить") }
                }
            }
            Spacer(Modifier.height(12.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
                items(items, key = { it.id }) { i ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(i.emoji, fontSize = 28.sp)
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(i.name, style = MaterialTheme.typography.titleMedium)
                                Text("${i.cost} 🪙" + if (i.hpDamage > 0) " · −${i.hpDamage} ❤️" else "",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = { vm.buyIndulgence(i) }, enabled = c.tokens >= i.cost) { Text("Купить") }
                            IconButton(onClick = { vm.deleteIndulgence(i) }) { Text("🗑") }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Text("+", fontSize = 24.sp)
        }
    }

    if (showAdd) {
        IndulgenceDialog(onDismiss = { showAdd = false }, onSave = { vm.saveIndulgence(it); showAdd = false })
    }
}

@Composable
fun IndulgenceDialog(onDismiss: () -> Unit, onSave: (Indulgence) -> Unit) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🍰") }
    var cost by remember { mutableStateOf("10") }
    var dmg by remember { mutableStateOf("0") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая льгота") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название (напр. «Десерт»)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = emoji, onValueChange = { emoji = it.take(4) }, label = { Text("Эмодзи") })
                OutlinedTextField(value = cost, onValueChange = { cost = it.filter(Char::isDigit) }, label = { Text("Цена 🪙") })
                OutlinedTextField(value = dmg, onValueChange = { dmg = it.filter(Char::isDigit) }, label = { Text("Урон ❤️ (0 = без урона)") })
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) return@Button
                onSave(Indulgence(name = name, emoji = emoji.ifBlank { "🍰" }, cost = cost.toIntOrNull() ?: 0, hpDamage = dmg.toIntOrNull() ?: 0))
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
