package com.liuwenli.litelist.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DatasetGeneration::class, DatasetPointer::class, TaskEntity::class, RepeatTemplateEntity::class, RepeatInstanceEntity::class, NormalReminderEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class LiteListDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE dataset_pointer ADD COLUMN themeMode TEXT NOT NULL DEFAULT 'SYSTEM'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `repeat_template` (
                    `generationId` TEXT NOT NULL, `templateId` TEXT NOT NULL, `title` TEXT NOT NULL,
                    `note` TEXT, `startDate` TEXT, `endDate` TEXT, `status` TEXT NOT NULL,
                    `isDeleted` INTEGER NOT NULL, `createdAt` TEXT NOT NULL, `dataVersion` INTEGER NOT NULL,
                    PRIMARY KEY(`generationId`, `templateId`),
                    FOREIGN KEY(`generationId`) REFERENCES `dataset_generation`(`generationId`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_repeat_template_generationId_status_isDeleted` ON `repeat_template` (`generationId`, `status`, `isDeleted`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `repeat_instance` (
                    `generationId` TEXT NOT NULL, `instanceId` TEXT NOT NULL, `taskId` TEXT NOT NULL,
                    `templateId` TEXT NOT NULL, `originalDate` TEXT NOT NULL,
                    `lifecycleStatus` TEXT NOT NULL, `cancellationReason` TEXT,
                    `lifecycleVersion` INTEGER NOT NULL, `isCustomized` INTEGER NOT NULL,
                    `retentionProtection` INTEGER NOT NULL, `protectionReason` TEXT,
                    PRIMARY KEY(`generationId`, `instanceId`),
                    FOREIGN KEY(`generationId`) REFERENCES `dataset_generation`(`generationId`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`generationId`, `templateId`) REFERENCES `repeat_template`(`generationId`, `templateId`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`generationId`, `taskId`) REFERENCES `task`(`generationId`, `taskId`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_repeat_instance_generationId_templateId_originalDate` ON `repeat_instance` (`generationId`, `templateId`, `originalDate`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_repeat_instance_generationId_taskId` ON `repeat_instance` (`generationId`, `taskId`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE task ADD COLUMN deadlineReminderDisabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `normal_reminder` (
                    `generationId` TEXT NOT NULL, `reminderId` TEXT NOT NULL,
                    `taskId` TEXT NOT NULL, `localDateTime` TEXT NOT NULL,
                    `logicalStatus` TEXT NOT NULL, `scheduleVersion` INTEGER NOT NULL,
                    `syncStatus` TEXT NOT NULL, `scheduleKind` TEXT NOT NULL,
                    PRIMARY KEY(`generationId`, `reminderId`),
                    FOREIGN KEY(`generationId`, `taskId`) REFERENCES `task`(`generationId`, `taskId`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_normal_reminder_generationId_taskId_localDateTime` ON `normal_reminder` (`generationId`, `taskId`, `localDateTime`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE task ADD COLUMN deadlineScheduleVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE task ADD COLUMN deadlineSyncStatus TEXT NOT NULL DEFAULT 'SYNC_REQUIRED'")
                db.execSQL("ALTER TABLE task ADD COLUMN deadlineScheduleKind TEXT NOT NULL DEFAULT 'NOT_SCHEDULED'")
            }
        }
    }
}

