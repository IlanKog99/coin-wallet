package com.ilankog.coinwallet.core

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

object Money {
    val MAX_RATE: BigDecimal = BigDecimal("1000000")

    fun toMajor(minor: Long, c: CurrencyInfo): BigDecimal = BigDecimal.valueOf(minor, c.decimals)

    fun format(minor: Long, c: CurrencyInfo, locale: Locale = Locale.getDefault()): String =
        withSymbol(number(toMajor(minor, c), c.decimals, locale), c)

    /** Denomination label: whole values without decimals ("€2"), fractions with ("€0.50"). */
    fun label(value: Long, c: CurrencyInfo, locale: Locale = Locale.getDefault()): String {
        val digits = if (value % c.minorPerMajor == 0L) 0 else c.decimals
        return withSymbol(number(toMajor(value, c), digits, locale), c)
    }

    fun formatRate(rate: BigDecimal): String =
        rate.round(MathContext(5, RoundingMode.HALF_EVEN)).stripTrailingZeros().toPlainString()

    /** Positive decimal ≤ MAX_RATE, "." or "," separator, digits only. Null when invalid. */
    fun parseRate(text: String): BigDecimal? {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty() || t.count { it == '.' } > 1 || !t.all { it in '0'..'9' || it == '.' }) return null
        val v = t.toBigDecimalOrNull() ?: return null
        return v.takeIf { it > BigDecimal.ZERO && it <= MAX_RATE }
    }

    /** A price in [c]: positive, "." or "," separator, at most [CurrencyInfo.decimals] fraction digits. */
    fun parseAmount(text: String, c: CurrencyInfo): Long? {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty() || t.count { it == '.' } > 1 || !t.all { it in '0'..'9' || it == '.' }) return null
        val v = t.toBigDecimalOrNull() ?: return null
        if (v.stripTrailingZeros().scale() > c.decimals) return null
        val minor = try {
            v.movePointRight(c.decimals).longValueExact()
        } catch (e: ArithmeticException) {
            return null // too large for a Long
        }
        return minor.takeIf { it > 0 }
    }

    private fun number(v: BigDecimal, digits: Int, locale: Locale): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
            isGroupingUsed = true
            roundingMode = RoundingMode.HALF_EVEN
        }.format(v)

    private fun withSymbol(num: String, c: CurrencyInfo): String =
        if (c.symbolFirst) "${c.symbol}$num" else "$num ${c.symbol}"
}

object RelativeTime {
    fun ago(then: Long, now: Long): String {
        val mins = (now - then).coerceAtLeast(0) / 60_000
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "$mins min ago"
            mins < 24 * 60 -> "${mins / 60}h ago"
            else -> "${mins / (24 * 60)}d ago"
        }
    }
}
