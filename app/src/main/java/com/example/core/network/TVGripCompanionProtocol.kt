package com.example.core.network

import android.util.Log
import com.example.core.model.CapabilitySet
import com.example.core.model.TvCommand
import com.example.core.model.TvDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Placeholder protocol for legacy "TVGrip Companion" entries.
 *
 * The Companion service is not Android TV Remote v2 and there is no public
 * protocol for it. To keep the app honest and production-safe, this protocol
 * never reports a usable connection: connecting returns a clear failure that
 * tells the user to delete the stale entry and pair the real Android TV.
 */
class TVGripCompanionProtocol : TvProtocol {

    private val TAG = "TVGripCompanionProtocol"

    override suspend fun connect(device: TvDevice, pairingCode: String?): ConnectionResult {
        return withContext(Dispatchers.IO) {
            Log.w(TAG, "Blocked connection to non-TVGrip-Companion entry: ${device.name}")
            ConnectionResult.Failed(
                "\"${device.name}\" was saved as a TVGrip Companion device, which is not a real " +
                    "Android TV Remote v2 device. Delete it and pair your actual Android TV / Google TV again."
            )
        }
    }

    override suspend fun sendCommand(command: TvCommand): Boolean = false

    override suspend fun measureLatency(): Long = -1L

    override suspend fun fetchCapabilities(): CapabilitySet = CapabilitySet.DEFAULT_ANDROID_TV

    override suspend fun disconnect() = Unit

    override fun isConnected(): Boolean = false
}
