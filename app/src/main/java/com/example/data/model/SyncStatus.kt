package com.example.data.model

enum class SyncStatus(val label: String) {
    SYNCED("Synced"),
    PENDING_SYNC("Pending Sync"),
    LOCAL_ONLY("Saved Locally")
}
