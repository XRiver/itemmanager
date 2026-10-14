package com.riverxu.itemmanager.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.riverxu.itemmanager.data.local.database.dao.FilterShortcutDao
import com.riverxu.itemmanager.data.local.database.dao.ItemDao
import com.riverxu.itemmanager.data.local.database.dao.LocationDao
import com.riverxu.itemmanager.data.local.database.dao.ResidenceDao
import com.riverxu.itemmanager.data.local.database.dao.TagDao
import com.riverxu.itemmanager.data.local.database.dao.TypeDao

@Database(
    entities = [
        ResidenceEntity::class,
        LocationEntity::class,
        TypeEntity::class,
        TagEntity::class,
        ItemEntity::class,
        ItemTagCrossRef::class,
        FilterShortcutEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun residenceDao(): ResidenceDao
    abstract fun locationDao(): LocationDao
    abstract fun typeDao(): TypeDao
    abstract fun tagDao(): TagDao
    abstract fun itemDao(): ItemDao
    abstract fun filterShortcutDao(): FilterShortcutDao

    companion object {
        const val DB_NAME = "item_manager.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .addMigrations(*ALL_MIGRATIONS)
                .addCallback(PresetSeeder())
                .build()
    }
}
