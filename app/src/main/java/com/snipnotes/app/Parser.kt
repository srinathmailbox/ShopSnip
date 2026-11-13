package com.snipnotes.app

import com.google.mlkit.vision.text.Text
import java.math.RoundingMode
import kotlin.math.abs

data class Detected(val name: String, val price: String)

/** Picks an item name and a price out of the text found on a shelf label or a pack. */
object LabelParser {
    private val priceRegex = Regex("""£?\s?(\d{1,4})[.,](\d{2})(?!\d)""")

    // Lines like "£0.78 per 100g" or "was £2.00" are not the shelf price.
    private val notPrice =
        Regex("""(?i)(\bper\b|/\s?\d|\bkg\b|\b100\s?g\b|\b100\s?ml\b|\bwas\b|\bsave\b|clubcard|nectar|multibuy)""")
    private val notName =
        Regex("""(?i)(\bper\b|/\s?\d|clubcard|nectar|multibuy|\bsave\b|\bwas\b)""")

    private class Candidate(val line: Text.Line, val value: String, val pound: Boolean, val height: Int)

    private fun letterRatio(t: String): Float {
        val visible = t.count { !it.isWhitespace() }
        if (visible == 0) return 0f
        return t.count { it.isLetter() } / visible.toFloat()
    }

    private fun nameOk(line: Text.Line): Boolean {
        val t = line.text.trim()
        return t.length >= 3 && letterRatio(t) >= 0.6f && !notName.containsMatchIn(t)
    }

    private fun tidy(raw: String): String {
        val s = raw.replace(Regex("""\s+"""), " ").trim().take(60)
        val letters = s.filter { it.isLetter() }
        // "BASMATI RICE" reads better as "Basmati rice".
        return if (letters.length > 3 && letters == letters.uppercase()) {
            s.lowercase().replaceFirstChar { it.uppercase() }
        } else s
    }

    /** Item name and shelf price, for the Item + price page. */
    fun parse(text: Text): Detected? {
        val lines = text.textBlocks.flatMap { it.lines }.filter { it.boundingBox != null }

        val candidates = lines.mapNotNull { line ->
            val t = line.text
            if (notPrice.containsMatchIn(t)) return@mapNotNull null
            val m = priceRegex.find(t) ?: return@mapNotNull null
            Candidate(line, "${m.groupValues[1]}.${m.groupValues[2]}", t.contains('£'), line.boundingBox!!.height())
        }

        // Prefer a £ amount, then the biggest print.
        val best = candidates.maxWithOrNull(compareBy<Candidate>({ it.pound }, { it.height })) ?: return null
        val priceTop = best.line.boundingBox!!.top

        val above = lines
            .filter { it !== best.line && it.boundingBox!!.top <= priceTop && nameOk(it) }
            .sortedByDescending { it.boundingBox!!.top }

        val nameLines: List<Text.Line> = when {
            above.isEmpty() -> listOfNotNull(
                lines.filter { nameOk(it) }.maxByOrNull { it.boundingBox!!.height() }
            )
            above.size >= 2 -> {
                val first = above[0]
                val second = above[1]
                val gap = first.boundingBox!!.top - second.boundingBox!!.bottom
                if (gap < first.boundingBox!!.height() * 1.5) listOf(second, first) else listOf(first)
            }
            else -> listOf(above[0])
        }

        val name = tidy(nameLines.joinToString(" ") { it.text.trim() })
        return Detected(name, best.value)
    }

    /** Just the product name, for the Shopping list page: the biggest readable text. */
    fun parseName(text: Text): String? {
        val lines = text.textBlocks.flatMap { it.lines }.filter { it.boundingBox != null }
        val ok = lines.filter { nameOk(it) && !priceRegex.containsMatchIn(it.text) }
        val best = ok.maxByOrNull { it.boundingBox!!.height() } ?: return null
        val b = best.boundingBox!!

        // A second line right underneath in similar-sized print belongs to the same name.
        val partner = ok.firstOrNull {
            val r = it.boundingBox!!
            it !== best &&
                r.top >= b.top &&
                abs(r.top - b.bottom) < b.height() &&
                r.height() >= b.height() * 0.7f
        }
        val joined = if (partner != null) "${best.text.trim()} ${partner.text.trim()}" else best.text.trim()
        return tidy(joined).takeIf { it.length >= 3 }
    }
}

/** "1.5", "£1,55" and "1.55" all become "1.50" / "1.55" so a spreadsheet reads a clean number. */
fun normalizePrice(raw: String): String? {
    val s = raw.replace("£", "").replace(" ", "").replace(',', '.')
    if (!Regex("""\d{1,5}(\.\d{1,2})?""").matches(s)) return null
    return s.toBigDecimal().setScale(2, RoundingMode.HALF_UP).toPlainString()
}
