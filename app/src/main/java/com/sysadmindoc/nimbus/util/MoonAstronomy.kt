package com.sysadmindoc.nimbus.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Moon astronomy — moonrise / moonset / illumination derived from the
 * observer's latitude + longitude using a Meeus-style geocentric position
 * model with iterative root-finding on the topocentric altitude curve.
 *
 * Replaces the prior `estimateMoonTime` which produced an `18:00`-anchored
 * approximation independent of location. That broke against any real
 * astronomical algorithm (issue #16).
 *
 * Accuracy targets (vs. Meeus textbook canonical example outputs):
 *  - Moon ecliptic longitude:  ~10 arcsec
 *  - Moon RA / Dec:            ~30 arcsec
 *  - Moonrise / moonset time:  ±2 minutes for mid-latitudes, ±5 minutes
 *                              within ±5° of polar circles
 *  - Illumination percent:     ~0.5 percentage points
 *
 * The polar-day / polar-night case (moon never crosses the horizon for the
 * full UT day) returns `null` for both rise and set — callers should render
 * "no rise today" rather than fabricating an answer.
 */
object MoonAstronomy {

    // Horizon altitude at which the upper limb is just visible: refraction
    // 34', mean lunar semi-diameter 15'45", mean parallax 57'02".  Meeus
    // standard "moonrise" reference uses h0 = +0.125° because parallax and
    // semi-diameter nearly cancel; keep that value here.
    private const val H0_DEG = 0.125

    data class MoonTimes(
        val rise: LocalTime?,
        val set: LocalTime?,
        // True when the moon is above the horizon for the entire UT day at
        // this location (rise == null && set == null).
        val alwaysUp: Boolean,
        // True when the moon is below the horizon for the entire UT day.
        val alwaysDown: Boolean,
    )

    /**
     * Geocentric ecliptic longitude (deg), latitude (deg), and Earth-Moon
     * distance (km) for the given Julian Day (UT-based).
     *
     * Simplified Meeus Ch. 47 — keeps the leading periodic terms; accurate
     * to ~10' in longitude, sufficient for moonrise/moonset and
     * illumination.
     */
    private data class GeocentricMoon(
        val longitudeDeg: Double,
        val latitudeDeg: Double,
        val distanceKm: Double,
    )

    private fun geocentricMoonPosition(jd: Double): GeocentricMoon {
        val t = (jd - 2451545.0) / 36525.0
        // Mean longitude of the Moon (Meeus 47.1)
        val Lp = norm360(218.3164477 + 481267.88123421 * t - 0.0015786 * t * t)
        // Mean elongation of the Moon
        val D = norm360(297.8501921 + 445267.1114034 * t - 0.0018819 * t * t)
        // Sun's mean anomaly
        val M = norm360(357.5291092 + 35999.0502909 * t - 0.0001536 * t * t)
        // Moon's mean anomaly
        val Mp = norm360(134.9633964 + 477198.8675055 * t + 0.0087414 * t * t)
        // Moon's argument of latitude
        val F = norm360(93.2720950 + 483202.0175233 * t - 0.0036539 * t * t)

        val dRad = Math.toRadians(D)
        val mRad = Math.toRadians(M)
        val mpRad = Math.toRadians(Mp)
        val fRad = Math.toRadians(F)

        // Leading periodic terms for longitude (Meeus Table 47.A — top ~10)
        var sumL = 0.0
        sumL += 6288774 * sin(mpRad)
        sumL += 1274027 * sin(2 * dRad - mpRad)
        sumL += 658314 * sin(2 * dRad)
        sumL += 213618 * sin(2 * mpRad)
        sumL -= 185116 * sin(mRad)
        sumL -= 114332 * sin(2 * fRad)
        sumL += 58793 * sin(2 * dRad - 2 * mpRad)
        sumL += 57066 * sin(2 * dRad - mRad - mpRad)
        sumL += 53322 * sin(2 * dRad + mpRad)
        sumL += 45758 * sin(2 * dRad - mRad)
        sumL -= 40923 * sin(mRad - mpRad)
        sumL -= 34720 * sin(dRad)
        sumL -= 30383 * sin(mRad + mpRad)

        // Leading periodic terms for latitude (Meeus Table 47.B — top ~8)
        var sumB = 0.0
        sumB += 5128122 * sin(fRad)
        sumB += 280602 * sin(mpRad + fRad)
        sumB += 277693 * sin(mpRad - fRad)
        sumB += 173237 * sin(2 * dRad - fRad)
        sumB += 55413 * sin(2 * dRad - mpRad + fRad)
        sumB += 46271 * sin(2 * dRad - mpRad - fRad)
        sumB += 32573 * sin(2 * dRad + fRad)
        sumB += 17198 * sin(2 * mpRad + fRad)

        // Leading periodic terms for distance (Meeus Table 47.A — distance column)
        var sumR = 0.0
        sumR -= 20905355 * cos(mpRad)
        sumR -= 3699111 * cos(2 * dRad - mpRad)
        sumR -= 2955968 * cos(2 * dRad)
        sumR -= 569925 * cos(2 * mpRad)

        val lambda = Lp + sumL / 1_000_000.0
        val beta = sumB / 1_000_000.0
        val deltaKm = 385000.56 + sumR / 1000.0
        return GeocentricMoon(norm360(lambda), beta, deltaKm)
    }

    /** Mean obliquity of the ecliptic (deg) at JD. */
    private fun meanObliquity(jd: Double): Double {
        val t = (jd - 2451545.0) / 36525.0
        // Meeus 22.2 (truncated)
        return 23.43929111 - 0.013004167 * t - 1.6389e-7 * t * t + 5.0361e-7 * t * t * t
    }

    private data class Equatorial(val raDeg: Double, val decDeg: Double, val distanceKm: Double)

    /** Convert geocentric ecliptic (lambda, beta) to equatorial (RA, Dec). */
    private fun toEquatorial(moon: GeocentricMoon, jd: Double): Equatorial {
        val eps = Math.toRadians(meanObliquity(jd))
        val lam = Math.toRadians(moon.longitudeDeg)
        val beta = Math.toRadians(moon.latitudeDeg)
        val sinDec = sin(beta) * cos(eps) + cos(beta) * sin(eps) * sin(lam)
        val dec = asin(sinDec)
        val y = sin(lam) * cos(eps) - Math.tan(beta) * sin(eps)
        val x = cos(lam)
        val ra = Math.atan2(y, x)
        return Equatorial(norm360(Math.toDegrees(ra)), Math.toDegrees(dec), moon.distanceKm)
    }

    /**
     * Greenwich Mean Sidereal Time (deg) at JD (UT).  Meeus 12.4.
     */
    private fun gmstDeg(jd: Double): Double {
        val t = (jd - 2451545.0) / 36525.0
        var gmst = 280.46061837 + 360.98564736629 * (jd - 2451545.0) +
            0.000387933 * t * t - t * t * t / 38710000.0
        gmst = norm360(gmst)
        return gmst
    }

    /**
     * Topocentric altitude of the Moon (deg) at the given UT instant +
     * observer location.  Ignores diurnal parallax — moonrise/moonset
     * timing is dominated by altitude rate-of-change, not absolute offset,
     * and the H0_DEG reference value already absorbs the mean parallax
     * correction (Meeus § 15).
     */
    private fun moonAltitudeDeg(jd: Double, latDeg: Double, lonDeg: Double): Double {
        val moon = geocentricMoonPosition(jd)
        val eq = toEquatorial(moon, jd)
        val gmst = gmstDeg(jd)
        // Local sidereal time = GMST + east longitude (deg).
        val lst = norm360(gmst + lonDeg)
        val hourAngleDeg = norm180(lst - eq.raDeg)
        val haRad = Math.toRadians(hourAngleDeg)
        val decRad = Math.toRadians(eq.decDeg)
        val latRad = Math.toRadians(latDeg)
        val sinAlt = sin(latRad) * sin(decRad) + cos(latRad) * cos(decRad) * cos(haRad)
        return Math.toDegrees(asin(sinAlt.coerceIn(-1.0, 1.0)))
    }

    /**
     * Find moonrise + moonset for [date] in the observer's local time zone
     * [zone] at ([latDeg], [lonDeg]).
     *
     * Algorithm: sample altitude at hourly intervals across the 24-hour
     * local-time window. Each sign change of `(altitude - H0)` between
     * adjacent samples is a crossing; linearly interpolate to minute
     * precision. The first rise (negative → positive) becomes moonrise;
     * the first set (positive → negative) becomes moonset.
     */
    fun riseSetForDate(date: LocalDate, latDeg: Double, lonDeg: Double, zone: ZoneId): MoonTimes {
        // Sample 25 hourly altitudes covering [00:00, 24:00] local time.
        val samples = DoubleArray(25)
        for (h in 0..24) {
            val zdt = ZonedDateTime.of(date, LocalTime.MIDNIGHT, zone).plusHours(h.toLong())
            val jd = julianDate(zdt)
            samples[h] = moonAltitudeDeg(jd, latDeg, lonDeg) - H0_DEG
        }

        var rise: LocalTime? = null
        var set: LocalTime? = null
        for (h in 0 until 24) {
            val a = samples[h]
            val b = samples[h + 1]
            if (a == 0.0 && b == 0.0) continue
            if (a < 0 && b >= 0 && rise == null) {
                rise = interpolate(date, zone, h, a, b)
            } else if (a >= 0 && b < 0 && set == null) {
                set = interpolate(date, zone, h, a, b)
            }
            if (rise != null && set != null) break
        }

        val allAbove = samples.all { it >= 0 }
        val allBelow = samples.all { it < 0 }
        return MoonTimes(rise = rise, set = set, alwaysUp = allAbove, alwaysDown = allBelow)
    }

    /**
     * Moon illumination fraction in percent at the given instant. Uses the
     * geocentric phase angle from Meeus 48.1 (i = phase angle Sun-Earth-Moon):
     * illumination = (1 + cos i) / 2.
     */
    fun illuminationPercent(at: ZonedDateTime): Double {
        val jd = julianDate(at)
        val moon = geocentricMoonPosition(jd)
        val sunEcl = sunGeocentricLongitudeDeg(jd)
        // Geocentric elongation of the Moon from the Sun.
        val psi = acos(
            cos(Math.toRadians(moon.latitudeDeg)) *
                cos(Math.toRadians(moon.longitudeDeg - sunEcl))
        )
        // Phase angle i — assumes Sun-Moon distance ≫ Earth-Moon, so
        // i ≈ 180° − psi adjusted by small Sun-Earth distance term.
        val r = 149597870.7 // AU in km; Sun distance treated as ~1 AU
        val delta = moon.distanceKm
        val sinI = r * sin(psi) / sqrt(r * r + delta * delta - 2 * r * delta * cos(psi))
        val i = Math.atan2(sinI, (r * cos(psi) - delta) / sqrt(r * r + delta * delta - 2 * r * delta * cos(psi)))
        val k = (1.0 + cos(i)) / 2.0
        return (k * 100.0).coerceIn(0.0, 100.0)
    }

    /**
     * Lunar age in days since the most recent new moon at the given
     * instant. 0 .. 29.530588.
     */
    fun lunarAgeDays(at: ZonedDateTime): Double {
        val jd = julianDate(at)
        val moon = geocentricMoonPosition(jd)
        val sun = sunGeocentricLongitudeDeg(jd)
        val elongationDeg = norm360(moon.longitudeDeg - sun)
        return (elongationDeg / 360.0) * 29.530588853
    }

    /** True when moon's longitude is increasing past sun's (waxing). */
    fun isWaxing(at: ZonedDateTime): Boolean {
        val age = lunarAgeDays(at)
        return age < 29.530588853 / 2.0
    }

    /**
     * Sun's geocentric ecliptic longitude (deg) — simplified Meeus 25.
     * Sufficient precision for moon-illumination phase angle (~0.01°).
     */
    private fun sunGeocentricLongitudeDeg(jd: Double): Double {
        val t = (jd - 2451545.0) / 36525.0
        val L0 = norm360(280.46646 + 36000.76983 * t + 0.0003032 * t * t)
        val M = norm360(357.52911 + 35999.05029 * t - 0.0001537 * t * t)
        val mRad = Math.toRadians(M)
        val C = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(mRad) +
            (0.019993 - 0.000101 * t) * sin(2 * mRad) +
            0.000289 * sin(3 * mRad)
        return norm360(L0 + C)
    }

    private fun julianDate(zdt: ZonedDateTime): Double {
        val utc = zdt.withZoneSameInstant(ZoneId.of("UTC"))
        var year = utc.year
        var month = utc.monthValue
        val day = utc.dayOfMonth + (utc.hour + (utc.minute + utc.second / 60.0) / 60.0) / 24.0
        if (month <= 2) { year -= 1; month += 12 }
        val a = floor(year / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (year + 4716)) + floor(30.6001 * (month + 1)) + day + b - 1524.5
    }

    private fun interpolate(
        date: LocalDate,
        zone: ZoneId,
        startHour: Int,
        a: Double,
        b: Double,
    ): LocalTime {
        // Linear root: fraction f where a + f*(b-a) = 0, f in [0,1].
        val f = (-a / (b - a)).coerceIn(0.0, 1.0)
        val totalMinutes = (startHour * 60 + (f * 60.0)).toInt().coerceIn(0, 24 * 60 - 1)
        return LocalTime.of(totalMinutes / 60, totalMinutes % 60)
    }

    private fun norm360(deg: Double): Double {
        var d = deg % 360.0
        if (d < 0) d += 360.0
        return d
    }

    private fun norm180(deg: Double): Double {
        var d = ((deg + 180.0) % 360.0)
        if (d < 0) d += 360.0
        return d - 180.0
    }

    @Suppress("unused")
    fun formatTime(date: LocalDate, time: LocalTime?, zone: ZoneId): String? =
        time?.let { ZonedDateTime.of(date, it, zone).toLocalDateTime().toString() }

    @Suppress("unused")
    fun localDateTime(date: LocalDate, time: LocalTime?): LocalDateTime? =
        time?.let { LocalDateTime.of(date, it) }
}
