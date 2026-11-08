package com.kieronquinn.app.pcs.directmycall

import java.util.Locale
import java.util.regex.Pattern

/**
 *  Reads Turkish phone menus: "faturanız için 1'i tuşlayınız", "müşterimizseniz biri, yeni başvuru
 *  için 2'yi tuşlayınız", "8'i tuşlayarak aydınlatma metnini dinleyebilirsiniz". In Turkish the key
 *  comes after the description and takes a case suffix, and on-device recognition often writes it
 *  as a word ("ikiyi", "dördü") or leaves out punctuation, so the rules below cover those forms.
 */
object TurkishMenuParser : MenuParser {

    private val TURKISH = Locale.forLanguageTag("tr-TR")
    private const val FLAGS = Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE

    /** How far back from Phone's range to read, so the previous option's key is known. */
    private const val CONTEXT = 120
    /** The longest description read before a key. */
    private const val MAX_DESCRIPTION = 140
    /** Options without a verb count when another option is this close before or after. */
    private const val CHAIN_BEFORE = 160
    private const val CHAIN_AFTER = 180
    /** How far a "<key>'i tuşlayarak <description>" option reads after the key. */
    private const val MAX_TRAILING_DESCRIPTION = 90
    /** Words of the description shown on the button. */
    private const val ACTION_WORDS = 4

    private val KEYS: Map<String, String> = buildMap {
        fun keys(key: String, vararg forms: String) = forms.forEach { put(it, key) }
        keys("0", "0", "sıfır", "sıfırı", "sıfıra", "sifir", "sifiri", "sifira")
        keys("1", "1", "bir", "biri", "bire", "birin")
        keys("2", "2", "iki", "ikiyi", "ikiye", "ikinin")
        keys("3", "3", "üç", "üçü", "üçe", "üçün", "uc", "ucu", "uce", "ucun")
        keys("4", "4", "dört", "dördü", "dörde", "dördün", "dort", "dordu", "dorde", "dordun")
        // "deşi" and "deşin" are how recognition often hears "beşi" and "beşin" on a phone line.
        keys("5", "5", "beş", "beşi", "beşe", "beşin", "bes", "besi", "bese", "besin", "deşi", "deşin", "desi", "desin")
        keys("6", "6", "altı", "altıyı", "altıya", "altının", "alti", "altiyi", "altiya", "altinin")
        keys("7", "7", "yedi", "yediyi", "yediye", "yedinin")
        keys("8", "8", "sekiz", "sekizi", "sekize", "sekizin")
        keys("9", "9", "dokuz", "dokuzu", "dokuza", "dokuzun")
        keys("*", "*", "yıldız", "yıldızı", "yıldıza", "yildiz", "yildizi", "yildiza")
        keys("#", "#", "kare", "kareyi", "kareye", "diyez", "diyezi", "diyeze")
    }

    /** Key words with a case suffix: these are keys even without a verb ("bir" alone may be "a"). */
    private val SUFFIXED_KEYS = listOf(
        "biri", "bire", "birin", "ikiyi", "ikiye", "ikinin", "üçü", "üçe", "ucu", "uce",
        "dördü", "dörde", "dordu", "dorde", "beşi", "beşe", "beşin", "besi", "deşi", "deşin",
        "altıyı", "altıya", "altiyi", "yediyi", "yediye", "sekizi", "sekize", "dokuzu", "dokuza",
        "sıfırı", "sıfıra", "yıldızı", "yıldıza", "kareyi", "kareye"
    )

    /** A key: a digit or symbol with an optional suffix ("1", "2'yi", "8ize"), or a key word. */
    private const val KEY =
        "(?:" +
            "([0-9*#])(?:['’]?[a-zçğıöşü]{1,5})?" +
            "|" +
            "(sıfır(?:ı|a|ın)?|sifir(?:i|a|in)?|bir(?:i|e|in)?|iki(?:yi|ye|nin)?" +
            "|üç(?:ü|e|ün|a)?|uc(?:u|e|un|a)?|dört|dörd(?:ü|e|ün)|dort|dord(?:u|e|un)" +
            "|beş(?:i|e|in)?|bes(?:i|e|in)?|deş(?:i|in)|des(?:i|in)" +
            "|altı(?:yı|ya|nın)?|alti(?:yi|ya|nin)?|yedi(?:yi|ye|nin)?" +
            "|sekiz(?:i|e|in)?|dokuz(?:u|a|un)?|yıldız(?:ı|a)?|yildiz(?:i|a)?" +
            "|kare(?:yi|ye)?|diyez(?:i|e)?)" +
        ")"

    /** The verb: "tuşlayınız", "tuşuna basın", "tuşlayarak", "seçiniz", "kodlayın"... */
    private const val VERB =
        "(?:\\s+(?:lütfen|lutfen))?" +
        "(?:\\s+(?:numaralı|numarali))?" +
        "(?:\\s+tuş(?:una|unu|a|u)|\\s+tus(?:una|unu|a|u))?" +
        "\\s+(?:tuşlay[ıi]n(?:[ıi]z)?|tuslay[ıi]n(?:[ıi]z)?" +
        "|tuşla(?:yarak|\\s+yarak|yabilirsiniz|mak)?|tusla(?:yarak|\\s+yarak|yabilirsiniz|mak)?" +
        "|bas(?:[ıi]n(?:[ıi]z)?|arak|abilirsiniz)?" +
        "|seç(?:in(?:iz)?|erek|ebilirsiniz)|sec(?:in(?:iz)?|erek|ebilirsiniz)" +
        "|kodlay[ıi]n(?:[ıi]z)?)"

    /**
     *  "<description> için|ise|-yseniz (lütfen) <key> (<verb>)": single options and chains
     *  ("müşterimizseniz biri, yeni başvuru için 2'yi, ... dördü tuşlayınız").
     */
    private val CLAUSE = Pattern.compile(
        "(?:\\b(için|icin|ise|iseniz)" +
            "|([a-zçğıöşü0-9]{2,}(?:yseniz|ysanız|ysaniz|sanız|seniz|ysa|yse|['’](?:in|ın|un|ün))))" +
            "(?:\\s+(?:lütfen|lutfen))?" +
            "\\s+" + KEY +
            "(" + VERB + ")?" +
            "(?=\\s|$|[.,;:])",
        FLAGS
    )

    /** "<key> <verb>" without "için": "8ize tuşla yarak kanun hakkındaki aydınlatma metnini...". */
    private val KEY_AND_VERB = Pattern.compile("\\b$KEY($VERB)(?=\\s|$|[.,;:])", FLAGS)

    /**
     *  "<description> için <next description> için <key>": recognition dropped a key word between
     *  two options ("... ikiyi gönderi takibi ... öğrenmek için uluslararası gönderileriniz için
     *  dördü").
     */
    private val ORPHAN_ICIN = Pattern.compile(
        "\\b(için|icin)\\s+(?=[a-zçğıöşü]{3,}(?:\\s+[a-zçğıöşü0-9]{2,}){0,5}\\s+(?:için|icin)\\b)",
        FLAGS
    )

    /** Ends of greetings and privacy notices; an option starts after them even without a full stop. */
    private val INTRO_END = Pattern.compile(
        "(?:hoş\\s+geldiniz|hos\\s+geldiniz|inceleyebilirsiniz|dinleyebilirsiniz" +
            "|ziyaret\\s+edebilirsiniz|kayıt\\s+altına\\s+alınmaktadır" +
            "|kayit\\s+altina\\s+alinmaktadir|kaydedilmektedir|izlenmektedir" +
            "|aradığınız\\s+için\\s+teşekkür(?:ler|\\s+ederiz)|aradiginiz\\s+icin\\s+tesekkur(?:ler|\\s+ederiz)" +
            "|seçeneklerimiz\\s+değişmiştir|seceneklerimiz\\s+degismistir" +
            "|dikkatlice\\s+dinleyiniz|customer\\s+[a-z]+(?:\\s+[a-z]+)?)" +
            "[.,;:\\s]*",
        FLAGS
    )

    /** Filler at the start of a description: greetings, "lütfen", "daha fazla bilgi için"... */
    private val LEADING_FILLER = Pattern.compile(
        "^(?:[.,;:\\s]+" +
            "|(?:hoş\\s+geldiniz|hos\\s+geldiniz|merhaba|iyi\\s+günler|iyi\\s+gunler" +
            "|iyi\\s+akşamlar|iyi\\s+aksamlar|sayın\\s+müşterimiz|sayin\\s+musterimiz" +
            "|değerli\\s+müşterimiz|degerli\\s+musterimiz|lütfen|lutfen|teşekkürler|tesekkurler" +
            "|teşekkür\\s+ederiz|tesekkur\\s+ederiz|aradığınız\\s+için\\s+teşekkür(?:ler|\\s+ederiz)" +
            "|devam\\s+etmek\\s+için|menüden|menuden|şimdi|simdi|öncelikle|oncelikle" +
            "|daha\\s+fazla\\s+bilgi\\s+için|bilgi\\s+almak\\s+için|yardım\\s+almak\\s+için" +
            "|size\\s+daha\\s+iyi\\s+hizmet\\s+verebilmek\\s+için|sizi\\s+doğru\\s+birime\\s+aktarabilmek\\s+için" +
            "|aşağıdaki\\s+seçeneklerden\\s+birini\\s+seçiniz|lütfen\\s+seçiminizi\\s+yapınız)[.,;:\\s]*)+",
        FLAGS
    )

    private class Match(
        val key: String,
        /** Where the trigger ("için", "-seniz") or, without one, the key starts. */
        val start: Int,
        val keyStart: Int,
        val end: Int,
        val hasVerb: Boolean,
        /** The trigger is a conditional word ("müşterimizseniz") that belongs to the description. */
        val triggerInDescription: Boolean
    )

    override fun parse(text: String, rangeStart: Int, rangeEnd: Int): List<MenuParser.Option> {
        if (rangeEnd - rangeStart < 4) return emptyList()
        // Phone locks options it has confirmed and moves the range start past them; reading a
        // little before it shows which key came last, for chains and for a dropped key.
        val contextStart = maxOf(0, rangeStart - CONTEXT)
        val slice = text.substring(contextStart, rangeEnd)
        val matches = ArrayList<Match>()

        val clauses = CLAUSE.matcher(slice)
        while (clauses.find()) {
            val token = clauses.group(3) ?: clauses.group(4)
            val key = keyOf(token, clauses.group()) ?: continue
            val keyStart = if (clauses.start(3) >= 0) clauses.start(3) else clauses.start(4)
            matches += Match(
                key,
                contextStart + clauses.start(),
                contextStart + keyStart,
                contextStart + clauses.end(),
                hasVerb = !clauses.group(5).isNullOrEmpty(),
                triggerInDescription = clauses.group(2) != null
            )
        }

        val direct = KEY_AND_VERB.matcher(slice)
        while (direct.find()) {
            val start = contextStart + direct.start()
            val end = contextStart + direct.end()
            if (matches.any { start < it.end && end > it.start }) continue
            val key = keyOf(direct.group(1) ?: direct.group(2), direct.group()) ?: continue
            matches += Match(key, start, start, end, hasVerb = true, triggerInDescription = false)
        }
        if (matches.isEmpty()) return emptyList()
        matches.sortBy { it.start }

        // Between keys N and N+2, a "<description> için" with no key of its own is option N+1.
        val orphans = ORPHAN_ICIN.matcher(slice)
        val inferred = ArrayList<Match>()
        while (orphans.find()) {
            val start = contextStart + orphans.start(1)
            val end = contextStart + orphans.end(1)
            if (matches.any { start < it.end && end > it.start }) continue
            val previous = matches.lastOrNull { it.end <= start } ?: continue
            val next = matches.firstOrNull { it.start >= end } ?: continue
            val previousKey = previous.key.singleOrNull() ?: continue
            if (previousKey in '1'..'7' && next.key.singleOrNull() == previousKey + 2) {
                inferred += Match((previousKey + 1).toString(), start, end, end, hasVerb = true, triggerInDescription = false)
            }
        }
        if (inferred.isNotEmpty()) {
            matches += inferred
            matches.sortBy { it.start }
        }

        // An option without a verb ("... için 1'i, ... için 2'yi tuşlayınız") counts when it is part
        // of a chain of options or its key form cannot be anything else.
        val valid = matches.filterIndexed { index, match ->
            if (match.hasVerb) return@filterIndexed true
            val afterPrevious = index > 0 && match.start - matches[index - 1].end <= CHAIN_BEFORE
            val beforeNext = matches.getOrNull(index + 1)?.let { it.start - match.end <= CHAIN_AFTER } == true
            afterPrevious || beforeNext || isExplicitKey(text.substring(match.keyStart, match.end))
        }
        if (valid.isEmpty()) return emptyList()

        // Phone drops options that start within the last two words, which are still being heard.
        val lastStart = thirdFromLastWordBoundary(text)
        val options = ArrayList<MenuParser.Option>()
        var previousEnd = rangeStart
        for (match in valid) {
            if (match.end <= rangeStart) {
                previousEnd = maxOf(previousEnd, match.end)
                continue
            }
            var start = maxOf(previousEnd, match.start - MAX_DESCRIPTION)
            var before = text.substring(start, match.start)
            // Start after the previous sentence.
            val sentenceEnd = maxOf(
                maxOf(before.lastIndexOf('.'), before.lastIndexOf('!')),
                maxOf(before.lastIndexOf('?'), before.lastIndexOf(';'))
            )
            if (sentenceEnd >= 0) {
                start += sentenceEnd + 1
                before = text.substring(start, match.start)
            }
            // Or after a greeting or notice that recognition did not end with a full stop.
            val intro = INTRO_END.matcher(before)
            var introEnd = -1
            while (intro.find()) introEnd = intro.end()
            if (introEnd > 0 && start + introEnd < match.keyStart) start += introEnd
            val filler = LEADING_FILLER.matcher(text.substring(start, match.end))
            if (filler.find() && start + filler.end() < match.keyStart) start += filler.end()
            while (start < match.keyStart && !text[start].isLetterOrDigit()) start++
            var end = trimEnd(text, start, match.end)

            // "<key>'i tuşlayarak <description>": the description follows the key.
            val keyAndVerb = text.substring(match.keyStart, match.end).lowercase(TURKISH)
            val descriptionFollows = listOf("yarak", "yerek", "basarak", "seçerek", "secerek").any { it in keyAndVerb }
            if (descriptionFollows && match.end < rangeEnd) {
                start = match.keyStart
                val after = text.substring(match.end, minOf(rangeEnd, match.end + MAX_TRAILING_DESCRIPTION))
                var stop = after.indexOfFirst { it == '.' || it == '!' || it == '?' || it == ';' }
                if (stop < 0) stop = after.length
                val notice = INTRO_END.matcher(after.substring(0, stop))
                if (notice.find()) stop = notice.end()
                end = trimEnd(text, start, match.end + stop)
            }
            if (start >= end || start >= lastStart) {
                previousEnd = end
                continue
            }
            val optionText = text.substring(start, end).trim()
            if (optionText.isEmpty()) {
                previousEnd = end
                continue
            }
            // Phone keeps an option only while its action is inside the (possibly shortened)
            // option text, so the action is the text's first words.
            options += MenuParser.Option(match.key, firstWords(optionText, ACTION_WORDS), start, end)
            previousEnd = end
        }
        return options
    }

    private fun keyOf(token: String?, match: String): String? {
        if (token.isNullOrEmpty()) return null
        val word = token.lowercase(TURKISH).trim()
        KEYS[word]?.let { key ->
            // "bir" is also "a" ("bir sorun için"): only a key with a verb.
            if (word == "bir") {
                val lower = match.lowercase(TURKISH)
                if (listOf("tuş", "tus", "bas", "seç", "sec").none { it in lower }) return null
            }
            return key
        }
        val first = word.first()
        return if (first in '0'..'9' || first == '*' || first == '#') first.toString() else null
    }

    private fun isExplicitKey(keyText: String): Boolean {
        val lower = keyText.lowercase(TURKISH).trim()
        val first = lower.firstOrNull() ?: return false
        return first in '0'..'9' || first == '*' || first == '#' ||
            '\'' in lower || '’' in lower || SUFFIXED_KEYS.any { lower.startsWith(it) }
    }

    private fun trimEnd(text: String, start: Int, end: Int): Int {
        var result = end
        while (result > start && (text[result - 1].isWhitespace() || text[result - 1] == '.' || text[result - 1] == ',')) {
            result--
        }
        return result
    }

    private fun firstWords(text: String, count: Int): String {
        var words = 0
        var inWord = false
        text.forEachIndexed { index, char ->
            if (char.isWhitespace()) {
                if (inWord && ++words >= count) return text.substring(0, index)
                inWord = false
            } else {
                inWord = true
            }
        }
        return text
    }

    private fun thirdFromLastWordBoundary(text: String): Int {
        val parts = text.split(" ")
        if (parts.size < 3) return Int.MAX_VALUE
        return parts.dropLast(2).sumOf { it.length } + (parts.size - 3)
    }

}
