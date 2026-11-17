package com.example

import android.app.Application
import com.example.ai.GeminiLiveManager
import com.example.ai.GeminiVisionAnalyzer
import com.example.data.local.BrstiDatabase
import com.example.data.local.MemoryAndReminderRepository
import com.example.data.local.SettingsRepository
import com.example.data.network.OnlineToolsClient
import com.example.device.DeviceActionExecutor
import kotlinx.coroutines.flow.MutableStateFlow

class AppContainer(application: Application) {
    val database by lazy { BrstiDatabase.getInstance(application) }
    val memoryRepository by lazy { MemoryAndReminderRepository(database) }
    val settingsRepository by lazy { SettingsRepository(application) }
    val onlineToolsClient by lazy { OnlineToolsClient() }
    val deviceActionExecutor by lazy {
        DeviceActionExecutor(
            context = application,
            repository = memoryRepository,
            settingsRepository = settingsRepository,
            onlineToolsClient = onlineToolsClient
        )
    }
    val geminiLiveManager by lazy {
        GeminiLiveManager(
            context = application,
            deviceActionExecutor = deviceActionExecutor
        )
    }
    val geminiVisionAnalyzer by lazy { GeminiVisionAnalyzer() }

    val isMicActive = MutableStateFlow(false)
    val isWakeStandbyActive = MutableStateFlow(false)
    val standbyEnergyLevel = MutableStateFlow(0f)
}

class BrstiApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
