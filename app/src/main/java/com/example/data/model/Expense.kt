package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = Group::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("groupId"), Index("date")]
)
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupId: Long,
    val title: String,
    val amount: Double,
    val category: String,
    val paidByMemberId: Long,
    val date: Long, // timestamp in millis
    val notes: String = "",
    val splitType: String = SplitType.EQUAL.name,
    val splitsJson: String = "[]", // JSON array of MemberSplit
    val syncStatus: String = SyncStatus.LOCAL_ONLY.name,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
