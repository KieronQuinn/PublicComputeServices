package com.kieronquinn.app.pcs.directmycall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 *  Menus as on-device recognition wrote them on Turkish service lines (the forms the Root Flags
 *  parser was tuned on): keys as digits with suffixes or as words, missing full stops, and a key
 *  word recognition dropped.
 */
class TurkishMenuParserTest {

    private fun parse(text: String) = TurkishMenuParser.parse(text, 0, text.length)

    private fun keys(text: String) = parse(text).map { it.key }

    @Test
    fun `single option with a digit key`() {
        val text = "Faturanızla ilgili işlemler için 1'i tuşlayınız. Diğer işlemler için lütfen bekleyiniz"
        val option = parse(text).single()
        assertEquals("1", option.key)
        assertEquals("Faturanızla ilgili işlemler için", option.action)
        assertEquals("Faturanızla ilgili işlemler için 1'i tuşlayınız", text.substring(option.start, option.end))
    }

    @Test
    fun `chain of options with key words`() {
        val text = "Müşteri hizmetlerine hoş geldiniz. Mobil hat müşterisiyseniz biri, sabit internete yeni " +
            "başvuru için ikiyi, kampanyalar için üçü, diğer işlemler için dördü tuşlayınız. Teşekkür ederiz"
        assertEquals(listOf("1", "2", "3", "4"), keys(text))
        assertEquals("Mobil hat müşterisiyseniz biri", parse(text).first().action)
    }

    @Test
    fun `symbol keys`() {
        val text = "Ana menüye dönmek için yıldızı tuşlayınız, bir önceki menü için kareye basınız lütfen bekleyin"
        assertEquals(listOf("*", "#"), keys(text))
    }

    @Test
    fun `key before the description`() {
        val text = "Görüşmeniz kayıt altına alınmaktadır. 8'i tuşlayarak kanun hakkındaki aydınlatma " +
            "metnini dinleyebilirsiniz. Lütfen bekleyiniz sizi müşteri temsilcisine aktarıyoruz"
        val option = parse(text).single()
        assertEquals("8", option.key)
        assertTrue(text.substring(option.start, option.end).endsWith("aydınlatma metnini dinleyebilirsiniz"))
    }

    @Test
    fun `key recognition dropped is inferred`() {
        val text = "Kurye çağırmak için biri, kargo ücretleri için ikiyi gönderi takibi ve kargonuzun son " +
            "durumunu öğrenmek için uluslararası gönderileriniz için dördü tuşlayınız lütfen bekleyin"
        assertEquals(listOf("1", "2", "3", "4"), keys(text))
    }

    @Test
    fun `article is not a key`() {
        assertEquals(emptyList<String>(), keys("Size bir sorun için yardımcı olmaktan mutluluk duyarız lütfen bekleyin"))
    }

    @Test
    fun `options before the range are not returned`() {
        val text = "Fatura için 1'i tuşlayınız. Kampanyalar için 2'yi tuşlayınız. Lütfen bekleyiniz"
        val second = text.indexOf("Kampanyalar")
        assertEquals(listOf("2"), TurkishMenuParser.parse(text, second, text.length).map { it.key })
    }

    @Test
    fun `option still being heard is left out`() {
        assertEquals(emptyList<String>(), keys("Lütfen bekleyiniz. 1'i tuşlayınız"))
    }

    @Test
    fun `parser by language`() {
        assertSame(TurkishMenuParser, MenuParser.forLocale(Locale.forLanguageTag("tr-TR")))
        assertNull(MenuParser.forLocale(Locale.US))
    }

}
