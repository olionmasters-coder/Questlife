package com.example.questlife.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

enum class HabitType { GOOD, BAD }

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "✨",
    val type: HabitType = HabitType.GOOD,
    val hpEffect: Int = 5,      // GOOD: лечение; BAD: урон
    val xpReward: Int = 10,     // только GOOD
    val tokenValue: Int = 5,    // GOOD: награда; BAD: цена
    val timesPerDay: Int = 1,
    val doneToday: Int = 0,
    val lastDay: String = "",
    val streak: Int = 0
)

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Double,
    val isIncome: Boolean,
    val category: String,
    val date: LocalDate = LocalDate.now()
)

@Entity(tableName = "savings")
data class SavingsGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "🎯",
    val target: Double,
    val saved: Double = 0.0
)

@Entity(tableName = "indulgences")
data class Indulgence(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "🍰",
    val cost: Int,
    val hpDamage: Int = 0
)

@Entity(tableName = "character")
data class GameCharacter(
    @PrimaryKey val id: Int = 1,
    val name: String = "Герой",
    val hp: Int = 100,
    val maxHp: Int = 100,
    val level: Int = 1,
    val xp: Int = 0,
    val tokens: Int = 50
)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY type, name")
    fun observeAll(): Flow<List<Habit>>
    @Insert suspend fun insert(h: Habit)
    @Update suspend fun update(h: Habit)
    @Delete suspend fun delete(h: Habit)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<Transaction>>
    @Insert suspend fun insert(t: Transaction)
    @Delete suspend fun delete(t: Transaction)
}

@Dao
interface SavingsDao {
    @Query("SELECT * FROM savings")
    fun observeAll(): Flow<List<SavingsGoal>>
    @Insert suspend fun insert(g: SavingsGoal)
    @Update suspend fun update(g: SavingsGoal)
    @Delete suspend fun delete(g: SavingsGoal)
}

@Dao
interface IndulgenceDao {
    @Query("SELECT * FROM indulgences")
    fun observeAll(): Flow<List<Indulgence>>
    @Insert suspend fun insert(i: Indulgence)
    @Delete suspend fun delete(i: Indulgence)
}

@Dao
interface CharacterDao {
    @Query("SELECT * FROM character WHERE id = 1")
    fun observe(): Flow<GameCharacter?>
    @Query("SELECT * FROM character WHERE id = 1")
    suspend fun get(): GameCharacter?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(c: GameCharacter)
    @Update suspend fun update(c: GameCharacter)
}

class Converters {
    @TypeConverter fun dateToString(d: LocalDate?): String? = d?.toString()
    @TypeConverter fun stringToDate(s: String?): LocalDate? = s?.let { LocalDate.parse(it) }
    @TypeConverter fun habitTypeToString(t: HabitType): String = t.name
    @TypeConverter fun stringToHabitType(s: String): HabitType = HabitType.valueOf(s)
}

@Database(
    entities = [Habit::class, Transaction::class, SavingsGoal::class, Indulgence::class, GameCharacter::class],
    version = 1
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun transactionDao(): TransactionDao
    abstract fun savingsDao(): SavingsDao
    abstract fun indulgenceDao(): IndulgenceDao
    abstract fun characterDao(): CharacterDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: android.content.Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, AppDatabase::class.java, "questlife.db")
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
