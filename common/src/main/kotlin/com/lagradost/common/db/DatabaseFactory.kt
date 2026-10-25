package com.lagradost.common.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.lagradost.common.platform.PlatformPaths
import java.io.File
import java.util.Properties

object DatabaseFactory {
    val database: DesktopDatabase by lazy {
        if (System.getProperty("cloudstream.sandbox.worker") == "true") {
            val memoryDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            DesktopDatabase.Schema.create(memoryDriver)
            return@lazy DesktopDatabase(memoryDriver)
        }

        val dbFile = File(PlatformPaths.dataDir, "cloudstream.db")

        // Ensure parent directories exist
        dbFile.parentFile?.mkdirs()

        val properties = Properties().apply {
            setProperty("journal_mode", "WAL")
            setProperty("busy_timeout", "30000")
            setProperty("synchronous", "NORMAL")
        }
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}", properties)

        try {
            driver.execute(null, "PRAGMA journal_mode = WAL;", 0)
            driver.execute(null, "PRAGMA busy_timeout = 30000;", 0)
            driver.execute(null, "PRAGMA synchronous = NORMAL;", 0)
        } catch (_: Exception) {
            // Safe to ignore if already applied
        }

        // Clean up legacy broken updates table
        try {
            driver.execute(null, "DROP TABLE IF EXISTS PluginUpdates;", 0)
        } catch (e: Exception) {
            // Ignore
        }

        // Create the schema if it doesn't exist
        DesktopDatabase.Schema.create(driver)

        try {
            driver.execute(null, "ALTER TABLE Bookmarks ADD COLUMN watchType INTEGER DEFAULT 0;", 0)
        } catch (e: Exception) {
            // Column already exists or other error, safe to ignore for migrations
        }

        try {
            driver.execute(null, "ALTER TABLE Bookmarks ADD COLUMN dateAdded INTEGER DEFAULT 0;", 0)
        } catch (e: Exception) {
            // Column already exists or other error, safe to ignore for migrations
        }

        try {
            driver.execute(null, "ALTER TABLE WatchHistory ADD COLUMN episodeThumbnailUrl TEXT;", 0)
        } catch (e: Exception) {
            // Column already exists or other error, safe to ignore for migrations
        }

        try {
            driver.execute(null, "ALTER TABLE WatchHistory ADD COLUMN screenshotUrl TEXT;", 0)
        } catch (e: Exception) {
            // Column already exists or other error, safe to ignore for migrations
        }

        try {
            driver.execute(null, "ALTER TABLE WatchHistory ADD COLUMN episodeName TEXT;", 0)
        } catch (e: Exception) {
            // Column already exists or other error, safe to ignore for migrations
        }

        try {
            driver.execute(null, "ALTER TABLE WatchHistory ADD COLUMN episodeDescription TEXT;", 0)
        } catch (e: Exception) {
            // Column already exists or other error, safe to ignore for migrations
        }

        DesktopDatabase(driver)
    }
}
