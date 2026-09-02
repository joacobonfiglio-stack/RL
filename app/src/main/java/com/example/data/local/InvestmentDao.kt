package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Investment
import kotlinx.coroutines.flow.Flow

@Dao
interface InvestmentDao {
    @Query("SELECT * FROM investments ORDER BY currentValuation DESC")
    fun getAllInvestments(): Flow<List<Investment>>

    @Query("SELECT * FROM investments ORDER BY currentValuation DESC")
    suspend fun getAllInvestmentsDirect(): List<Investment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(investment: Investment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(investments: List<Investment>): List<Long>

    @Update
    suspend fun update(investment: Investment)

    @Delete
    suspend fun delete(investment: Investment)

    @Query("UPDATE investments SET syncStatus = :newStatus WHERE syncStatus = :oldStatus")
    suspend fun markAllAsSynced(oldStatus: String, newStatus: String)
}
