package com.example.questlife

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.questlife.data.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    private val start = SharingStarted.WhileSubscribed(5_000)

    val character = db.characterDao().observe().stateIn(viewModelScope, start, GameCharacter())
    val habits = db.habitDao().observeAll().stateIn(viewModelScope, start, emptyList())
    val transactions = db.transactionDao().observeAll().stateIn(viewModelScope, start, emptyList())
    val savings = db.savingsDao().observeAll().stateIn(viewModelScope, start, emptyList())
    val indulgences = db.indulgenceDao().observeAll().stateIn(viewModelScope, start, emptyList())

    private val _message = MutableSharedFlow<String>(0, 8, BufferOverflow.DROP_OLDEST)
    val message = _message.asSharedFlow()

    init {
        viewModelScope.launch {
            if (db.characterDao().get() == null) db.characterDao().upsert(GameCharacter())
            seedIfEmpty()
            resetDaily()
        }
    }

    // ---------- Игровая логика ----------

    fun emojiFor(level: Int): String = when {
        level >= 20 -> "🐉"
        level >= 15 -> "🦊"
        level >= 10 -> "🐥"
        level >= 5 -> "🐣"
        else -> "🥚"
    }

    private fun xpToNext(level: Int) = 50 + level * 50

    fun completeGood(habit: Habit) {
        viewModelScope.launch {
            val c = db.characterDao().get() ?: return@launch
            val today = LocalDate.now().toString()
            val done = if (habit.lastDay == today) habit.doneToday else 0
            if (done >= habit.timesPerDay) return@launch
            val firstToday = done == 0
            val streak = when {
                firstToday && habit.lastDay == today.minusDays(1).toString() -> habit.streak + 1
                firstToday -> 1
                else -> habit.streak
            }
            db.habitDao().update(habit.copy(doneToday = done + 1, lastDay = today, streak = streak))
            db.characterDao().update(c.copy(
                hp = (c.hp + habit.hpEffect).coerceAtMost(c.maxHp),
                xp = c.xp + habit.xpReward,
                tokens = c.tokens + habit.tokenValue
            ))
            levelUpIfPossible()
            _message.tryEmit("✅ +${habit.tokenValue}🪙 +${habit.xpReward}⭐ +${habit.hpEffect}❤️")
        }
    }

    fun doBad(habit: Habit) {
        viewModelScope.launch {
            val c = db.characterDao().get() ?: return@launch
            if (c.tokens < habit.tokenValue) {
                _message.tryEmit("❌ Не хватает ${habit.tokenValue - c.tokens} 🪙")
                return@launch
            }
            val today = LocalDate.now().toString()
            val done = if (habit.lastDay == today) habit.doneToday else 0
            db.habitDao().update(habit.copy(doneToday = done + 1, lastDay = today, streak = 0))
            db.characterDao().update(c.copy(
                tokens = c.tokens - habit.tokenValue,
                hp = (c.hp - habit.hpEffect).coerceAtLeast(0)
            ))
            _message.tryEmit("☠️ −${habit.tokenValue}🪙 −${habit.hpEffect}❤️")
        }
    }

    private suspend fun levelUpIfPossible() {
        var c = db.characterDao().get() ?: return
        while (c.xp >= xpToNext(c.level)) {
            c = c.copy(level = c.level + 1, xp = c.xp - xpToNext(c.level), maxHp = c.maxHp + 10, hp = c.maxHp + 10)
            _message.tryEmit("🎉 Новый уровень ${c.level}! Персонаж окреп.")
        }
        db.characterDao().update(c)
    }

    private suspend fun resetDaily() {
        val today = LocalDate.now()
        db.habitDao().observeAll().first().forEach { h ->
            if (h.lastDay != today.toString() && h.doneToday > 0) {
                val kept = if (h.lastDay == today.minusDays(1).toString() && h.doneToday >= h.timesPerDay) h.streak else 0
                db.habitDao().update(h.copy(doneToday = 0, streak = kept))
            }
        }
    }

    // ---------- Привычки ----------
    fun saveHabit(h: Habit) = viewModelScope.launch {
        if (h.id == 0L) db.habitDao().insert(h) else db.habitDao().update(h)
    }
    fun deleteHabit(h: Habit) = viewModelScope.launch { db.habitDao().delete(h) }

    // ---------- Финансы ----------
    fun addTransaction(title: String, amount: Double, isIncome: Boolean, category: String) = viewModelScope.launch {
        db.transactionDao().insert(Transaction(title = title, amount = amount, isIncome = isIncome, category = category))
    }
    fun deleteTransaction(t: Transaction) = viewModelScope.launch { db.transactionDao().delete(t) }

    // ---------- Накопления ----------
    fun addGoal(name: String, emoji: String, target: Double) = viewModelScope.launch {
        db.savingsDao().insert(SavingsGoal(name = name, emoji = emoji, target = target))
    }
    fun addToGoal(g: SavingsGoal, amount: Double) = viewModelScope.launch {
        db.savingsDao().update(g.copy(saved = g.saved + amount))
    }
    fun deleteGoal(g: SavingsGoal) = viewModelScope.launch { db.savingsDao().delete(g) }

    // ---------- Лавка ----------
    fun saveIndulgence(i: Indulgence) = viewModelScope.launch { db.indulgenceDao().insert(i) }
    fun deleteIndulgence(i: Indulgence) = viewModelScope.launch { db.indulgenceDao().delete(i) }

    fun buyPotion() {
        viewModelScope.launch {
            val c = db.characterDao().get() ?: return@launch
            if (c.tokens < 30) { _message.tryEmit("❌ Нужно 30 🪙"); return@launch }
            db.characterDao().update(c.copy(tokens = c.tokens - 30, hp = (c.hp + 30).coerceAtMost(c.maxHp)))
            _message.tryEmit("🧪 +30 ❤️")
        }
    }

    fun buyIndulgence(i: Indulgence) {
        viewModelScope.launch {
            val c = db.characterDao().get() ?: return@launch
            if (c.tokens < i.cost) { _message.tryEmit("❌ Не хватает ${i.cost - c.tokens} 🪙"); return@launch }
            db.characterDao().update(c.copy(tokens = c.tokens - i.cost, hp = (c.hp - i.hpDamage).coerceAtLeast(0)))
            _message.tryEmit("🎉 Куплено: ${i.name} (−${i.cost} 🪙)")
        }
    }

    fun renameCharacter(name: String) = viewModelScope.launch {
        val c = db.characterDao().get() ?: return@launch
        db.characterDao().update(c.copy(name = name))
    }

    private suspend fun seedIfEmpty() {
        if (db.habitDao().observeAll().first().isEmpty()) {
            listOf(
                Habit(name = "Зарядка", emoji = "🏃", hpEffect = 10, xpReward = 20, tokenValue = 5),
                Habit(name = "Пить воду", emoji = "💧", hpEffect = 2, xpReward = 5, tokenValue = 1, timesPerDay = 8),
                Habit(name = "Читать", emoji = "📚", hpEffect = 0, xpReward = 15, tokenValue = 3),
                Habit(name = "Фастфуд", emoji = "🍔", type = HabitType.BAD, hpEffect = 10, tokenValue = 15)
            ).forEach { db.habitDao().insert(it) }
        }
        if (db.indulgenceDao().observeAll().first().isEmpty()) {
            listOf(
                Indulgence(name = "Десерт", emoji = "🍰", cost = 20, hpDamage = 5),
                Indulgence(name = "Час сериалов", emoji = "📺", cost = 15),
                Indulgence(name = "Ночь за игрой", emoji = "🎮", cost = 25, hpDamage = 10)
            ).forEach { db.indulgenceDao().insert(it) }
        }
    }
}
