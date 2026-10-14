package com.riverxu.itemmanager.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2：新增「标签」与两个系统时间字段。
 *
 * 变更内容：
 *  1. 新表 `tag`（用户可创建的文字标签，含预置项）
 *  2. 新表 `item_tag`（物品与标签的多对多关联）
 *  3. `item` 表新增 `locationUpdatedAt`（最近更新位置时间）
 *
 * 已有的 `item` 行用 `createdAt` 回填 `locationUpdatedAt`：录入选位置也算第一次设定位置，
 * 比留 0 更合理。整个迁移不触碰既有业务数据。
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. 标签表
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tag` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`isPreset` INTEGER NOT NULL, " +
                "`usageCount` INTEGER NOT NULL, " +
                "`lastUsedAt` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tag_name` ON `tag` (`name`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_tag_lastUsedAt_usageCount` " +
                "ON `tag` (`lastUsedAt`, `usageCount`)",
        )

        // 2. 物品 ↔ 标签 关联表
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `item_tag` (" +
                "`itemId` INTEGER NOT NULL, " +
                "`tagId` INTEGER NOT NULL, " +
                "PRIMARY KEY(`itemId`, `tagId`), " +
                "FOREIGN KEY(`itemId`) REFERENCES `item`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`tagId`) REFERENCES `tag`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_item_tag_tagId` ON `item_tag` (`tagId`)")

        // 3. item 表新增字段（SQLite 的 ADD COLUMN 只能带常量默认值，先加后用 createdAt 回填）
        db.execSQL(
            "ALTER TABLE `item` ADD COLUMN `locationUpdatedAt` INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL("UPDATE `item` SET `locationUpdatedAt` = `createdAt` WHERE `locationUpdatedAt` = 0")

        // 4. 预置标签
        db.execSQL(
            "INSERT OR IGNORE INTO `tag` (`name`, `isPreset`, `usageCount`, `lastUsedAt`) " +
                "VALUES ('待汰换', 1, 0, 0)",
        )
        db.execSQL(
            "INSERT OR IGNORE INTO `tag` (`name`, `isPreset`, `usageCount`, `lastUsedAt`) " +
                "VALUES ('待出售', 1, 0, 0)",
        )
    }
}

/** 全部迁移，按版本顺序排列。 */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
