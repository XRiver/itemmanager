package com.riverxu.itemmanager.data.local.database

import androidx.room.RoomDatabase

/** 保留回调占位：预置数据由 [com.riverxu.itemmanager.data.repository.SQLiteItemRepository.ensureSeeded] 负责写入，
 * 以便参与事务并可在导入数据后重复调用。 */
class PresetSeeder : RoomDatabase.Callback()
