package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "incomes")
data class Income(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupId: Long = 1L,
    val memberId: Long? = null,
    val memberName: String = "",
    val title: String,
    val amount: Double,
    val source: String = "Sueldo", // Sueldo, Freelance, Negocio, Inversiones, Bono, Otro
    val date: Long = System.currentTimeMillis(),
    val notes: String = "",
    val syncStatus: String = SyncStatus.LOCAL_ONLY.name,
    val createdAt: Long = System.currentTimeMillis()
)
