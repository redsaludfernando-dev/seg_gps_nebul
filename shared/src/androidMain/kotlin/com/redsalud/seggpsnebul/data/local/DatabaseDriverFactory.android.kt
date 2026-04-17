package com.redsalud.seggpsnebul.data.local

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

class DatabaseDriverFactory(private val context: Context) {
    fun createDriver(): SqlDriver =
        AndroidSqliteDriver(SegGpsDatabase.Schema, context, "seg_gps.db")
}
