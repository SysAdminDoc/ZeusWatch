package com.sysadmindoc.nimbus.ui.component

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.sysadmindoc.nimbus.util.isReducedMotionEnabled

/**
 * Gravity sensor hook for parallax effects on weather particles.
 * Inspired by breezy-weather's DelayRotateController.
 * Returns a smoothed tilt offset (x, y) normalized to roughly -1..1 range.
 * Falls back to zero tilt if sensor unavailable or reduced motion is on.
 */
data class GravityTilt(val x: Float, val y: Float)

@Composable
fun rememberGravityTilt(): GravityTilt {
    if (isReducedMotionEnabled()) return GravityTilt(0f, 0f)

    val context = LocalContext.current
    val tiltX = remember { mutableFloatStateOf(0f) }
    val tiltY = remember { mutableFloatStateOf(0f) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)

        val listener = if (sensorManager != null && gravitySensor != null) {
            val alpha = 0.15f
            val l = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val rawX = (event.values[0] / 9.8f).coerceIn(-1f, 1f)
                    val rawY = (event.values[1] / 9.8f).coerceIn(-1f, 1f)
                    tiltX.floatValue = tiltX.floatValue + alpha * (rawX - tiltX.floatValue)
                    tiltY.floatValue = tiltY.floatValue + alpha * (rawY - tiltY.floatValue)
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(l, gravitySensor, SensorManager.SENSOR_DELAY_UI)
            l
        } else {
            null
        }

        onDispose {
            if (listener != null && sensorManager != null) {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    return GravityTilt(tiltX.floatValue, tiltY.floatValue)
}
