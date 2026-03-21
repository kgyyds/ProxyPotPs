package com.example.proxypotps.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create stress_tests table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `stress_tests` (
                `test_id` TEXT NOT NULL,
                `test_name` TEXT NOT NULL,
                `target_type` TEXT NOT NULL,
                `target_url` TEXT NOT NULL,
                `concurrent_connections` INTEGER NOT NULL,
                `connection_timeout_seconds` INTEGER NOT NULL,
                `request_interval_seconds` REAL NOT NULL,
                `max_rounds` INTEGER NOT NULL,
                `selected_node_ids` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `current_round` INTEGER NOT NULL,
                `total_active_connections` INTEGER NOT NULL,
                `total_successful_connections` INTEGER NOT NULL,
                `total_failed_connections` INTEGER NOT NULL,
                `overall_success_rate` REAL NOT NULL,
                `avg_response_time_ms` INTEGER NOT NULL,
                PRIMARY KEY(`test_id`)
            )
        """.trimIndent())
        
        // Create stress_test_results table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `stress_test_results` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `test_id` TEXT NOT NULL,
                `node_id` INTEGER NOT NULL,
                `node_name` TEXT NOT NULL,
                `round` INTEGER NOT NULL,
                `active_connections` INTEGER NOT NULL,
                `total_connections` INTEGER NOT NULL,
                `successful_connections` INTEGER NOT NULL,
                `failed_connections` INTEGER NOT NULL,
                `timeout_connections` INTEGER NOT NULL,
                `response_code_distribution` TEXT NOT NULL,
                `avg_connection_time_ms` INTEGER NOT NULL,
                `start_time` INTEGER NOT NULL,
                `end_time` INTEGER,
                `status` TEXT NOT NULL
            )
        """.trimIndent())
    }
}