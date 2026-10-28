package com.rajandownloader.app

import android.app.Application
import com.rajandownloader.app.runtime.RuntimeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RajanApp : Application() {

    lateinit var runtimeManager: RuntimeManager
        private set

    private val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this
        runtimeManager = RuntimeManager(this)

        // Initialize runtime in background on app start if not already ready
        appScope.launch {
            if (!runtimeManager.isRuntimeReady()) {
                runtimeManager.ensureRuntimeInitialized()
            }
        }
    }

    companion object {
        lateinit var instance: RajanApp
            private set
    }
}

