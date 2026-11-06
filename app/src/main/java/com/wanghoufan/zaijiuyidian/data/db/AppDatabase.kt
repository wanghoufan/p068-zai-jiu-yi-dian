package com.wanghoufan.zaijiuyidian.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProjectEntity::class,
        SessionEntity::class,
        ActiveSessionEntity::class,
        RepSetEntity::class,
        RepDraftEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun sessionDao(): SessionDao
    abstract fun activeSessionDao(): ActiveSessionDao
    abstract fun repSetDao(): RepSetDao
    abstract fun repDraftDao(): RepDraftDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `projects` ADD COLUMN `mode` TEXT NOT NULL DEFAULT 'TIME'"
                )
                db.execSQL(
                    "ALTER TABLE `projects` ADD COLUMN `plannedSets` INTEGER NOT NULL DEFAULT 3"
                )
                db.execSQL(
                    "ALTER TABLE `projects` ADD COLUMN `targetRepsPerSet` INTEGER NOT NULL DEFAULT 10"
                )
                db.execSQL(
                    "ALTER TABLE `sessions` ADD COLUMN `mode` TEXT NOT NULL DEFAULT 'TIME'"
                )
                db.execSQL(
                    "ALTER TABLE `sessions` ADD COLUMN `plannedSets` INTEGER NOT NULL DEFAULT 3"
                )
                db.execSQL(
                    "ALTER TABLE `sessions` ADD COLUMN `targetRepsPerSet` INTEGER NOT NULL DEFAULT 10"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `rep_sets` (" +
                        "`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, " +
                        "`ordinal` INTEGER NOT NULL, `actualReps` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_rep_sets_sessionId` " +
                        "ON `rep_sets` (`sessionId`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_rep_sets_sessionId_ordinal` " +
                        "ON `rep_sets` (`sessionId`, `ordinal`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `rep_draft` (" +
                        "`id` INTEGER NOT NULL, `projectId` TEXT NOT NULL, " +
                        "`plannedSets` INTEGER NOT NULL, `targetRepsPerSet` INTEGER NOT NULL, " +
                        "`setsCsv` TEXT NOT NULL, `nextReps` INTEGER NOT NULL, " +
                        "`touchedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "zai-jiu-yi-dian.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
