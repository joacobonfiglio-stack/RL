package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

object SplitJsonConverter {
    fun toJson(splits: List<MemberSplit>): String {
        val array = JSONArray()
        for (split in splits) {
            val obj = JSONObject().apply {
                put("memberId", split.memberId)
                put("memberName", split.memberName)
                put("shareValue", split.shareValue)
                put("computedAmount", split.computedAmount)
                put("isSettled", split.isSettled)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun fromJson(json: String): List<MemberSplit> {
        if (json.isBlank()) return emptyList()
        val list = mutableListOf<MemberSplit>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    MemberSplit(
                        memberId = obj.optLong("memberId", 0L),
                        memberName = obj.optString("memberName", "Member"),
                        shareValue = obj.optDouble("shareValue", 1.0),
                        computedAmount = obj.optDouble("computedAmount", 0.0),
                        isSettled = obj.optBoolean("isSettled", false)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
