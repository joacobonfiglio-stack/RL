package com.example.data.model

data class MemberSplit(
    val memberId: Long,
    val memberName: String,
    val shareValue: Double, // % for PERCENTAGE, part count for PARTS, exact dollar for EXACT, 1.0 for EQUAL
    val computedAmount: Double,
    val isSettled: Boolean = false
)
