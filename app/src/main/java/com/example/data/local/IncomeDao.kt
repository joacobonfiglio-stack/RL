package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Income
import kotlinx.coroutines.flow.Flow

@Dao
interface IncomeDao {
    @Query("SELECT * FROM incomes ORDER BY date DESC, id DESC")
    fun getAllIncomes(): Flow<List<Income>>

    @Query("SELECT * FROM incomes WHERE groupId = :groupId ORDER BY date DESC, id DESC")
    fun getIncomesForGroup(groupId: Long): Flow<List<Income>>

    @Query("SELECT * FROM incomes ORDER BY date DESC, id DESC")
    suspend fun getAllIncomesDirect(): List<Income>

    @Query("SELECT * FROM incomes WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getIncomesBetweenDates(startDate: Long, endDate: Long): Flow<List<Income>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(income: Income): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(incomes: List<Income>): List<Long>

    @Update
    suspend fun update(income: Income)

    @Delete
    suspend fun delete(income: Income)

    @Query("UPDATE incomes SET syncStatus = :newStatus WHERE syncStatus = :oldStatus")
    suspend fun markAllAsSynced(oldStatus: String, newStatus: String)
}
