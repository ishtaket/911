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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

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
            val loc = fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .await()
            if (loc == null) {
                Snapshot(null, null, null, false)
            } else {
                val places = placeDao.all()
                val match = places.firstOrNull { distanceMetres(loc.latitude, loc.longitude, it.lat, it.lng) <= it.radius }
                val label = match?.label ?: bucketLabel(loc.latitude, loc.longitude)
                Snapshot(loc.latitude, loc.longitude, label, match?.pauseRecording == true)
            }
        } catch (e: SecurityException) {
            Snapshot(null, null, null, false)
        } catch (e: Throwable) {
            Snapshot(null, null, null, false)
        }
    }

    private fun bucketLabel(lat: Double, lng: Double): String {
        val rl = String.format("%.2f", lat)
        val rn = String.format("%.2f", lng)
        return "cell:$rl,$rn"
    }

    private fun distanceMetres(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
