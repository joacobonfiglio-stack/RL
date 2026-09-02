package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Member
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    @Query("SELECT * FROM members WHERE groupId = :groupId ORDER BY id ASC")
    fun getMembersForGroup(groupId: Long): Flow<List<Member>>

    @Query("SELECT * FROM members WHERE groupId = :groupId ORDER BY id ASC")
    suspend fun getMembersForGroupDirect(groupId: Long): List<Member>

    @Query("SELECT * FROM members ORDER BY id ASC")
    fun getAllMembers(): Flow<List<Member>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(member: Member): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(members: List<Member>): List<Long>

    @Update
    suspend fun update(member: Member)

    @Delete
    suspend fun delete(member: Member)
}
