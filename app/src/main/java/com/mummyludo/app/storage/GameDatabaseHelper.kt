package com.mummyludo.app.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class GameDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "mummyludo.db"
        const val DATABASE_VERSION = 1

        const val TABLE_SAVED_MATCH = "saved_match"
        const val COL_MATCH_ID = "match_id"
        const val COL_SCHEMA_VERSION = "schema_version"
        const val COL_REVISION = "revision"
        const val COL_STATE_JSON = "state_json"
        const val COL_STATE_HASH = "state_hash"
        const val COL_UPDATED_AT = "updated_at"
        const val COL_COMPLETED = "completed"

        const val ACTIVE_MATCH_KEY = "active_local_match"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_SAVED_MATCH (
                $COL_MATCH_ID TEXT PRIMARY KEY,
                $COL_SCHEMA_VERSION INTEGER NOT NULL,
                $COL_REVISION INTEGER NOT NULL,
                $COL_STATE_JSON TEXT NOT NULL,
                $COL_STATE_HASH TEXT NOT NULL,
                $COL_UPDATED_AT INTEGER NOT NULL,
                $COL_COMPLETED INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the initial release
    }

    fun saveMatch(
        matchId: String,
        revision: Int,
        stateJson: String,
        stateHash: String = "",
        completed: Boolean = false,
    ) {
        val values = ContentValues().apply {
            put(COL_MATCH_ID, matchId)
            put(COL_SCHEMA_VERSION, DATABASE_VERSION)
            put(COL_REVISION, revision)
            put(COL_STATE_JSON, stateJson)
            put(COL_STATE_HASH, stateHash)
            put(COL_UPDATED_AT, System.currentTimeMillis())
            put(COL_COMPLETED, if (completed) 1 else 0)
        }
        writableDatabase.insertWithOnConflict(TABLE_SAVED_MATCH, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun loadMatchJson(matchId: String): String? {
        val cursor = readableDatabase.query(
            TABLE_SAVED_MATCH,
            arrayOf(COL_STATE_JSON, COL_COMPLETED),
            "$COL_MATCH_ID = ?",
            arrayOf(matchId),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                val completed = it.getInt(it.getColumnIndexOrThrow(COL_COMPLETED)) == 1
                if (!completed) {
                    return it.getString(it.getColumnIndexOrThrow(COL_STATE_JSON))
                }
            }
        }
        return null
    }

    fun deleteMatch(matchId: String) {
        writableDatabase.delete(TABLE_SAVED_MATCH, "$COL_MATCH_ID = ?", arrayOf(matchId))
    }

    fun hasActiveMatch(matchId: String): Boolean {
        val cursor = readableDatabase.query(
            TABLE_SAVED_MATCH,
            arrayOf(COL_COMPLETED),
            "$COL_MATCH_ID = ? AND $COL_COMPLETED = 0",
            arrayOf(matchId),
            null,
            null,
            null
        )
        return cursor.use { it.moveToFirst() }
    }
}
