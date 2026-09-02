package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "investments")
data class Investment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val symbol: String = "",
    val assetType: String = "Stocks", // Stocks, Crypto, Real Estate, ETFs, Mutual Funds, Bonds, Cash
    val investedAmount: Double,
    val currentValuation: Double,
    val quantity: Double = 1.0,
    val notes: String = "",
    val syncStatus: String = SyncStatus.LOCAL_ONLY.name,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val profitLoss: Double get() = currentValuation - investedAmount
    val returnPercentage: Double
        get() = if (investedAmount > 0.0) ((currentValuation - investedAmount) / investedAmount) * 100.0 else 0.0
}
