package net.squanchy.remoteconfig

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class RemoteConfig(
    private val firebaseRemoteConfig: FirebaseRemoteConfig,
    private val debugMode: Boolean
) {

    private val cacheExpiryInSeconds: Long
        get() = if (debugMode) EXPIRY_IMMEDIATELY else EXPIRY_ONE_HOUR

    fun wifiAutoConfigEnabledNow() = firebaseRemoteConfig.getBoolean(KEY_WIFI_CONFIG_ENABLED)

    fun wifiSsid(): String = firebaseRemoteConfig.getString(KEY_WIFI_SSID)

    fun wifiPassword(): String = firebaseRemoteConfig.getString(KEY_WIFI_PASSWORD)

    fun getBoolean(key: String): Single<Boolean> =
        getConfigValue { firebaseRemoteConfig.getBoolean(key) }

    private fun <T> getConfigValue(action: () -> T): Single<T> {
        return fetchAndActivate(cacheExpiryInSeconds)
            .andThen(Single.fromCallable { action() })
    }

    fun fetchNow(): Completable {
        return fetchAndActivate(EXPIRY_IMMEDIATELY)
            .subscribeOn(Schedulers.io())
    }

    private fun fetchAndActivate(cacheExpiryInSeconds: Long): Completable {
        return Completable.create { emitter ->
            firebaseRemoteConfig.fetch(cacheExpiryInSeconds)
                .addOnCompleteListener {
                    firebaseRemoteConfig.activateFetched()
                    emitter.onComplete()
                }
                .addOnFailureListener { exception ->
                    if (emitter.isDisposed) {
                        return@addOnFailureListener
                    }
                    emitter.onError(exception)
                }
        }
    }

    companion object {

        private val EXPIRY_IMMEDIATELY = TimeUnit.HOURS.toSeconds(0)
        private val EXPIRY_ONE_HOUR = TimeUnit.HOURS.toSeconds(1)
        private val KEY_WIFI_CONFIG_ENABLED = "wifi_config_enabled"
        private val KEY_WIFI_SSID = "wifi_ssid"
        private val KEY_WIFI_PASSWORD = "wifi_password"
    }
}
