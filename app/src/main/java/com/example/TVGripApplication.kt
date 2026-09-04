package com.example

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.example.core.data.local.TVGripDatabase
import com.example.core.data.repository.ControllerProfileRepository
import com.example.core.data.repository.SettingsRepository
import com.example.core.data.repository.TvDeviceRepository
import com.example.core.haptics.HapticFeedbackHelper
import com.example.core.network.NetworkMonitor
import com.example.core.network.TvConnectionManager
import com.example.core.network.TvDiscoveryManager
import com.example.core.gamepad.PlayerSlotManager
import com.example.core.sensors.AirMouseEngine
import com.example.core.sensors.MotionSteeringEngine
import com.example.core.voice.VoiceInputManager

class TVGripApplication : Application() {

    lateinit var database: TVGripDatabase
        private set

    lateinit var tvDeviceRepository: TvDeviceRepository
        private set

    lateinit var controllerProfileRepository: ControllerProfileRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var connectionManager: TvConnectionManager
        private set

    lateinit var discoveryManager: TvDiscoveryManager
        private set

    lateinit var networkMonitor: NetworkMonitor
        private set

    lateinit var hapticFeedbackHelper: HapticFeedbackHelper
        private set

    lateinit var playerSlotManager: PlayerSlotManager
        private set

    lateinit var airMouseEngine: AirMouseEngine
        private set

    lateinit var motionSteeringEngine: MotionSteeringEngine
        private set

    lateinit var voiceInputManager: VoiceInputManager
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = TVGripDatabase.getDatabase(this)
        tvDeviceRepository = TvDeviceRepository(database.tvDeviceDao())
        controllerProfileRepository = ControllerProfileRepository(database.controllerProfileDao())
        settingsRepository = SettingsRepository(this)

        connectionManager = TvConnectionManager.getInstance()
        discoveryManager = TvDiscoveryManager(this)
        networkMonitor = NetworkMonitor(this)
        appScope.launch {
            networkMonitor.networkStatusFlow
                .map { it.isConnected && (it.isWifi || it.isEthernet) }
                .distinctUntilChanged()
                .collect { isAvailable ->
                    connectionManager.onNetworkChanged(isAvailable)
                }
        }
        hapticFeedbackHelper = HapticFeedbackHelper(this)
        playerSlotManager = PlayerSlotManager(this, hapticFeedbackHelper)
        airMouseEngine = AirMouseEngine(this, connectionManager)
        motionSteeringEngine = MotionSteeringEngine(this)
        voiceInputManager = VoiceInputManager(this)

        appScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                hapticFeedbackHelper.isHapticsEnabled = settings.hapticFeedbackEnabled
                airMouseEngine.lowPowerMode = settings.batterySaverMode
                motionSteeringEngine.lowPowerMode = settings.batterySaverMode
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            playerSlotManager.cleanupInactivePresets()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        playerSlotManager.cleanupInactivePresets()
    }

    companion object {
        lateinit var instance: TVGripApplication
            private set
    }
}
