package org.geekio.floorplanner.core

import java.util.Locale
import kotlin.math.roundToInt

enum class UnitSystem { METRIC, IMPERIAL }

/** Formats and parses lengths and areas in metres or feet and inches. Numbers use [Locale] digits. */
object Units {
    private const val M_PER_IN = 0.0254
    private const val FT2_PER_M2 = 10.763910416709722
    private const val FT_PER_M = 3.280839895013123
    private const val GAL_PER_L = 0.2641720523581484

    /** A length such as "3.25 m" or "10' 8\"". */
    fun length(meters: Double, u: UnitSystem, locale: Locale = Locale.getDefault()): String = when (u) {
        UnitSystem.METRIC -> String.format(locale, "%.2f m", meters)
        UnitSystem.IMPERIAL -> feetInches(meters, locale)
    }

    /** A length without the metre unit, for crowded dimension labels ("3.25", or "10' 8\""). */
    fun lengthShort(meters: Double, u: UnitSystem, locale: Locale = Locale.getDefault()): String = when (u) {
        UnitSystem.METRIC -> String.format(locale, "%.2f", meters)
        UnitSystem.IMPERIAL -> feetInches(meters, locale)
    }

    /** A small size such as a wall thickness: "20 cm" or "7.9\"". */
    fun small(cm: Double, u: UnitSystem, locale: Locale = Locale.getDefault()): String = when (u) {
        UnitSystem.METRIC -> String.format(locale, "%.0f cm", cm)
        UnitSystem.IMPERIAL -> String.format(locale, "%.1f\"", cm / 100 / M_PER_IN)
    }

    fun area(m2: Double, u: UnitSystem, locale: Locale = Locale.getDefault()): String = when (u) {
        UnitSystem.METRIC -> String.format(locale, "%.2f m²", m2)
        UnitSystem.IMPERIAL -> String.format(locale, "%.1f ft²", m2 * FT2_PER_M2)
    }

    /** A paint quantity: litres, or US gallons for imperial. */
    fun volume(liters: Double, u: UnitSystem, locale: Locale = Locale.getDefault()): String = when (u) {
        UnitSystem.METRIC -> String.format(locale, "%.1f L", liters)
        UnitSystem.IMPERIAL -> String.format(locale, "%.1f gal", liters * GAL_PER_L)
    }

    /** Display units per m², per m and per litre, to turn metric unit prices into displayed ones. */
    fun areaFactor(u: UnitSystem) = if (u == UnitSystem.IMPERIAL) FT2_PER_M2 else 1.0
    fun lengthFactor(u: UnitSystem) = if (u == UnitSystem.IMPERIAL) FT_PER_M else 1.0
    fun volumeFactor(u: UnitSystem) = if (u == UnitSystem.IMPERIAL) GAL_PER_L else 1.0

    fun areaUnit(u: UnitSystem) = if (u == UnitSystem.IMPERIAL) "ft²" else "m²"
    fun lengthUnit(u: UnitSystem) = if (u == UnitSystem.IMPERIAL) "ft" else "m"
    fun volumeUnit(u: UnitSystem) = if (u == UnitSystem.IMPERIAL) "gal" else "L"

    /** A whole amount of money with digit grouping. */
    fun money(v: Double, locale: Locale = Locale.getDefault()): String = String.format(locale, "%,.0f", v)

    private fun feetInches(meters: Double, locale: Locale): String {
        val totalIn = (meters / M_PER_IN).roundToInt()
        val sign = if (totalIn < 0) "-" else ""
        val abs = kotlin.math.abs(totalIn)
        return String.format(locale, "%s%d' %d\"", sign, abs / 12, abs % 12)
    }

    /** The value to prefill an edit box with, in a form [parseLength] accepts. */
    fun editValue(meters: Double, u: UnitSystem): String = when (u) {
        UnitSystem.METRIC -> String.format(Locale.US, "%.2f", meters)
        UnitSystem.IMPERIAL -> feetInches(meters, Locale.US)
    }

    /**
     * Parses a length typed by the user into metres. Metric: metres ("3.25"). Imperial: feet and inches
     * ("10' 8\"", "10'8", "10 8"), or decimal feet ("10.5"). Persian and Arabic digits are accepted.
     */
    fun parseLength(text: String, u: UnitSystem): Double? {
        val t = normalizeDigits(text).trim()
        if (t.isEmpty()) return null
        return when (u) {
            UnitSystem.METRIC -> t.removeSuffix("m").trim().toDoubleOrNull()
            UnitSystem.IMPERIAL -> {
                val m = Regex("""^(\d+(?:\.\d+)?)\s*(?:'|ft|’|′)?\s*(?:(\d+(?:\.\d+)?)\s*(?:"|in|”|″)?)?$""").find(t) ?: return null
                val feet = m.groupValues[1].toDouble()
                val inches = m.groupValues[2].toDoubleOrNull() ?: 0.0
                // "10 8" or "10'8" means feet and inches, a lone "10.5" means feet.
                (feet * 12 + inches) * M_PER_IN
            }
        }?.takeIf { it > 0 }
    }

    /** Accepts Latin, Persian and Arabic digits and either decimal separator. */
    fun normalizeDigits(text: String): String = text.map { ch ->
        when (ch) {
            in '۰'..'۹' -> '0' + (ch - '۰')
            in '٠'..'٩' -> '0' + (ch - '٠')
            '٫', ',' -> '.'
            else -> ch
        }
    }.joinToString("")
}
