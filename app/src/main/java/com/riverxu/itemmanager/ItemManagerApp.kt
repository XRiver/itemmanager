package com.riverxu.itemmanager

import android.app.Application
import com.riverxu.itemmanager.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ItemManagerApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 首次启动写入预置数据（默认住处、预置位置、预置类型）
        appScope.launch { container.initialize() }
    }
}
