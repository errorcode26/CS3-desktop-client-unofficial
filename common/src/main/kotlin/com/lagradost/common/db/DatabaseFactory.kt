package com.lagradost.common.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.lagradost.common.platform.PlatformPaths
import java.io.File

object DatabaseFactory {
    val database: DesktopDatabase by lazy {
        val dbFile = File(PlatformPaths.dataDir, "cloudstream.db")
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")

        // Ensure parent directories exist
        dbFile.parentFile?.mkdirs()

        // Create the schema if it doesn't exist
        DesktopDatabase.Schema.create(driver)

        DesktopDatabase(driver)
    }
}
