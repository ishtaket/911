package com.pca.assistant.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.pca.assistant.data.db.dao.PlaceDao
import com.pca.assistant.data.db.entity.PlaceEntity
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spec §3.5: GPS coordinates bucketed into place names via a local place map.
 *
 * Coordinates themselves never appear in the LLM payload — they are replaced
 * with the [PlaceEntity.label] of the matching geofence ("home" / "work" /
 * "Berlin centre" / ...) by [labelFor], or with the bucketed string
 * `~lat,~lng` rounded to 2 decimals (~1 km cell) when no geofence matches.
 */
@Singleton
class LocationProvider @Inject constructor(
    private val context: Context,
    private val placeDao: PlaceDao,
) {

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(context) }

    data class Snapshot(val lat: Double?, val lng: Double?, val label: String?, val pauseFlag: Boolean)

    fun hasFinePermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun current(): Snapshot {
        if (!hasFinePermission()) return Snapshot(null, null, null, false)
        return try {
            // Fused provider sometimes never resolves when the GPS chip is
            // off and there are no recent fixes. Cap the wait so the 5-min
            // ticker never blocks indefinitely.
            val loc = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
            }
            if (loc == null) {
                Snapshot(null, null, null, false)
            } else {
                val match = PlaceMatcher.match(loc.latitude, loc.longitude, placeDao.all())
                Snapshot(loc.latitude, loc.longitude, match.label, match.pauseRecording)
            }
        } catch (e: SecurityException) {
            Snapshot(null, null, null, false)
        } catch (e: Throwable) {
            Snapshot(null, null, null, false)
        }
    }

    private companion object {
        const val LOCATION_TIMEOUT_MS = 5_000L
    }
}
