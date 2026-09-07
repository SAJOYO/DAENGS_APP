package com.daengs.app.territory

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** No FK to walk history: discarded short walks must not erase an ambiguous server request. */
@Entity(tableName = "territory_operation", indices = [Index(value = ["identity"], unique = true)])
data class TerritoryOperation(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val identity: String,
    val ownerId: String,
    val sessionId: String,
    val kind: String,
    val body: String,
    val state: String = "PENDING",
    val response: String? = null,
    val failure: String? = null,
    /** Persisted before sending. Recovery must never replay a success animation. */
    val sent: Boolean = false,
)

@Dao
interface TerritoryActionDao {
    @Query("SELECT * FROM territory_operation ORDER BY sequence")
    fun observe(): Flow<List<TerritoryOperation>>
    @Query("SELECT * FROM territory_operation ORDER BY sequence")
    suspend fun all(): List<TerritoryOperation>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: TerritoryOperation): Long
    @Update
    suspend fun update(row: TerritoryOperation)
    @Query("DELETE FROM territory_operation WHERE sequence = :sequence")
    suspend fun delete(sequence: Long)
    @Transaction
    suspend fun replaceRejected(row: TerritoryOperation) {
        delete(row.sequence)
        insert(row.copy(sequence = 0))
    }
}

/** Independent versioned outbox; the existing walk/diary schema stays at its own version. */
@Database(entities = [TerritoryOperation::class], version = 1, exportSchema = true)
abstract class TerritoryActionDatabase : RoomDatabase() {
    abstract fun actions(): TerritoryActionDao
    companion object {
        fun open(context: Context): TerritoryActionDatabase = Room.databaseBuilder(
            context.applicationContext, TerritoryActionDatabase::class.java, "daengs_territory.db").build()
    }
}
