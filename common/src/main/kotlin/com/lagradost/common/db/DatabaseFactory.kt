package com.lagradost.common.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

object DatabaseFactory {
    @Volatile
    private var instance: DesktopDatabase? = null
    private val lock = Any()

    val database: DesktopDatabase
        get() {
            instance?.let { return it }
            synchronized(lock) {
                instance?.let { return it }
                val db = initDatabaseWithRecovery()
                instance = db
                return db
            }
        }

    fun isInitialized(): Boolean = instance != null

    private fun initDatabaseWithRecovery(): DesktopDatabase {
        if (System.getProperty("cloudstream.sandbox.worker") == "true") {
            throw IllegalStateException("Direct SQLite access is strictly forbidden in sandboxed worker mode.")
        }

        val dir = PlatformPaths.dataDir
        if (!dir.exists()) dir.mkdirs()
        val dbFile = File(dir, "cloudstream.db")
        dbFile.parentFile?.mkdirs()

        return try {
            createAndMigrateDatabase(dbFile)
        } catch (t: Throwable) {
            AppLogger.e("DatabaseFactory: SQLite database corrupted or initialization failed. Initiating auto-recovery...", t)
            quarantineAndRebuildDatabase(dbFile)
        }
    }

    private fun createAndMigrateDatabase(dbFile: File): DesktopDatabase {
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
        } catch (_: Exception) {}

        // Verify database integrity if database file already exists and has content
        if (dbFile.exists() && dbFile.length() > 0L) {
            try {
                driver.execute(null, "PRAGMA quick_check(1);", 0)
            } catch (e: Exception) {
                try { driver.close() } catch (_: Throwable) {}
                throw java.sql.SQLException("SQLite quick_check failed on ${dbFile.name}: ${e.message}", e)
            }
        }

        // Clean up legacy broken updates table
        try {
            driver.execute(null, "DROP TABLE IF EXISTS PluginUpdates;", 0)
        } catch (_: Exception) {}

        // Create the schema if it doesn't exist
        DesktopDatabase.Schema.create(driver)

        fun tryMigrate(sql: String) {
            try {
                driver.execute(null, sql, 0)
            } catch (_: Exception) {}
        }

        tryMigrate("ALTER TABLE Bookmarks ADD COLUMN watchType INTEGER DEFAULT 0;")
        tryMigrate("ALTER TABLE Bookmarks ADD COLUMN dateAdded INTEGER DEFAULT 0;")
        tryMigrate("ALTER TABLE WatchHistory ADD COLUMN episodeThumbnailUrl TEXT;")
        tryMigrate("ALTER TABLE WatchHistory ADD COLUMN screenshotUrl TEXT;")
        tryMigrate("ALTER TABLE WatchHistory ADD COLUMN episodeName TEXT;")
        tryMigrate("ALTER TABLE WatchHistory ADD COLUMN episodeDescription TEXT;")

        return DesktopDatabase(driver)
    }

    private fun quarantineAndRebuildDatabase(dbFile: File): DesktopDatabase {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val corruptBackup = File(dbFile.parentFile, "cloudstream.db.corrupt_$timestamp.bak")
            val walFile = File(dbFile.parentFile, "cloudstream.db-wal")
            val shmFile = File(dbFile.parentFile, "cloudstream.db-shm")

            if (dbFile.exists()) {
                val renamed = dbFile.renameTo(corruptBackup)
                if (!renamed) {
                    try {
                        dbFile.copyTo(corruptBackup, overwrite = true)
                        dbFile.delete()
                    } catch (_: Throwable) {}
                }
                AppLogger.w("DatabaseFactory: Quarantined corrupted database to '${corruptBackup.name}'")
            }
            if (walFile.exists()) {
                val walBackup = File(dbFile.parentFile, "cloudstream.db-wal.corrupt_$timestamp.bak")
                walFile.renameTo(walBackup)
                walFile.delete()
            }
            if (shmFile.exists()) {
                shmFile.delete()
            }
        } catch (e: Throwable) {
            AppLogger.e("DatabaseFactory: Failed to quarantine corrupted database files", e)
        }

        return createAndMigrateDatabase(dbFile)
    }

    fun repairDatabase(): Boolean {
        synchronized(lock) {
            val dir = PlatformPaths.dataDir
            val dbFile = File(dir, "cloudstream.db")
            return try {
                val newDb = quarantineAndRebuildDatabase(dbFile)
                instance = newDb
                true
            } catch (t: Throwable) {
                AppLogger.e("DatabaseFactory: Manual database repair failed", t)
                false
            }
        }
    }
}
