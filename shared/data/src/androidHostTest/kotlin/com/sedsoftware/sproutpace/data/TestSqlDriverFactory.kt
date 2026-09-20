package com.sedsoftware.sproutpace.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import com.sedsoftware.sproutpace.data.db.SproutPaceDatabase
import java.util.Properties

internal class TestSqlDriverFactory(
    private val url: String = JdbcSqliteDriver.IN_MEMORY,
    private val initializeSchema: Boolean = true,
) : SqlDriverFactory {
    private var driver: SqlDriver? = null
    override fun create(): SqlDriver = checkNotNull(driver ?: JdbcSqliteDriver(
        url = url,
        properties = Properties().apply { put("foreign_keys", "true") },
    ).also {
        if (initializeSchema) SproutPaceDatabase.Schema.create(it)
        driver = it
    })
    override fun close() { driver?.close(); driver = null }
}
