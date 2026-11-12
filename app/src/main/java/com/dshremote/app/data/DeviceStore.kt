package com.dshremote.app.data

import android.content.Context

class DeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences("dsh_remote", Context.MODE_PRIVATE)

    var wsUrl: String?
        get() = prefs.getString("ws_url", null)
        private set(value) = prefs.edit().putString("ws_url", value).apply()

    var deviceToken: String?
        get() = prefs.getString("device_token", null)
        private set(value) = prefs.edit().putString("device_token", value).apply()

    var deviceId: String?
        get() = prefs.getString("device_id", null)
        private set(value) = prefs.edit().putString("device_id", value).apply()

    val isPaired: Boolean
        get() = !wsUrl.isNullOrBlank() && !deviceToken.isNullOrBlank()

    fun save(pair: PairResult) {
        wsUrl = pair.wsUrl
        deviceToken = pair.deviceToken
        deviceId = pair.deviceId
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
