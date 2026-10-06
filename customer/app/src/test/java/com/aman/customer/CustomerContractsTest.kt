package com.aman.customer

import com.aman.customer.data.CustomerReportCategory
import com.aman.customer.data.CustomerScreen
import com.aman.customer.data.CustomerScreenData
import com.aman.customer.data.CustomerUiTraceability
import com.aman.customer.data.ProviderPrefix
import com.aman.customer.data.buildCustomerReportRows
import com.aman.customer.data.calculateProtectionQuote
import com.aman.customer.data.customerReportCsv
import com.aman.customer.data.discoverProvider
import com.aman.customer.data.isValidCustomerReportRange
import com.aman.customer.data.normalizePhoneE164
import com.aman.customer.data.phoneDigits
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerContractsTest {
    @Test fun allTwentyReferenceScreensUseOrderedC01ThroughC20Ids() {
        assertEquals(20, CustomerScreen.entries.size)
        assertEquals((1..20).map { "C%02d".format(it) }, CustomerScreen.entries.map { it.id })
        assertEquals("C01", CustomerScreen.INITIALIZATION.id)
        assertEquals("C02", CustomerScreen.LOGIN.id)
        assertEquals("C03", CustomerScreen.SIGN_UP.id)
        assertEquals("C19", CustomerScreen.ABOUT.id)
        assertEquals("C20", CustomerScreen.RECOVERY.id)
        assertEquals(CustomerScreen.LOGIN, CustomerScreen.fromId("missing"))
    }

    @Test fun longestMatchingPrefixWins() {
        val prefixes = listOf(ProviderPrefix("07", "عام", true), ProviderPrefix("0799", "أدق", true))
        assertEquals("أدق" to "0799", discoverProvider("0799123", prefixes))
    }

    @Test fun inactiveOrNonmatchingPrefixIsIgnored() {
        val prefixes = listOf(ProviderPrefix("07", "معطل", false), ProviderPrefix("09", "آخر", true))
        assertNull(discoverProvider("0799", prefixes))
    }

    @Test fun phoneInputNormalizesInternationalDigitsAndRejectsNonE164() {
        assertEquals("+9647701234567", normalizePhoneE164("+٩٦٤ (770) 123-4567"))
        assertEquals("9647701234567", phoneDigits("+٩٦٤ (770) 123-4567"))
        assertNull(normalizePhoneE164("9647701234567"))
        assertNull(normalizePhoneE164("++9647701234567"))
        assertNull(normalizePhoneE164("+12abc4567"))
    }

    @Test fun providerDiscoverySupportsPrefixesStoredWithPlus() {
        val prefixes = listOf(ProviderPrefix("+964", "عام", true), ProviderPrefix("96477", "أدق", true))
        assertEquals("أدق" to "96477", discoverProvider("+964771234567", prefixes))
    }

    @Test fun protectionCostUsesWholeAdminDefinedUnitsAndNeverCustomerEnteredDays() {
        val quote = calculateProtectionQuote(unitDays = 30, pointsPerUnit = 120, units = 4)
        assertEquals(120, quote?.durationDays)
        assertEquals(480L, quote?.pointsCost)
        assertEquals(7, calculateProtectionQuote(7, 7, 3)?.durationDays)
        assertEquals(21L, calculateProtectionQuote(7, 7, 3)?.pointsCost)
        assertNull(calculateProtectionQuote(0, 120, 4))
        assertNull(calculateProtectionQuote(30, 0, 4))
        assertNull(calculateProtectionQuote(30, 120, 0))
        assertNull(calculateProtectionQuote(Int.MAX_VALUE, 120, 4))
    }

    @Test fun customerTraceabilityHasUniqueValidScreenScopedIdsForAllTwentyScreens() {
        val ids = CustomerUiTraceability.elements.map { it.elementId }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(CustomerUiTraceability.elements.all {
            it.screenId in CustomerUiTraceability.screenIds && it.elementId.startsWith("${it.screenId}.")
        })
        assertEquals(20, CustomerUiTraceability.screenIds.size)
        (1..20).forEach { assertTrue("missing screen C%02d".format(it), "C%02d".format(it) in CustomerUiTraceability.screenIds) }
        listOf("C02.SUBMIT", "C03.SUBMIT", "C05.ADD", "C06.SUBMIT", "C07.LIST", "C11.CONFIRM",
            "C12.CONFIRM", "C13.CONFIRM", "C14.LIST", "C15.SEND", "C16.RESULTS", "C17.EXPORT",
            "C18.SAVE", "C19.SECTIONS", "C20.SUBMIT").forEach { id ->
            assertTrue("missing element $id", CustomerUiTraceability.elements.any { it.elementId == id })
        }
    }

    @Test fun reportFiltersRealRowsByCategoryAndInclusiveDateRange() {
        val ledger = JSONArray()
            .put(JSONObject().put("id", "a").put("entry_type", "PURCHASE_CREDIT").put("direction", "CREDIT").put("amount", 10).put("created_at", "2026-04-01T12:00:00Z"))
            .put(JSONObject().put("id", "b").put("entry_type", "ACTIVATION_DEBIT").put("direction", "DEBIT").put("amount", 3).put("created_at", "2026-04-02T12:00:00Z"))
            .put(JSONObject().put("id", "c").put("entry_type", "ADMIN_GRANT").put("direction", "CREDIT").put("amount", 99).put("created_at", "2026-04-03T12:00:00Z"))
        val data = CustomerScreenData(CustomerScreen.REPORTS, related = mapOf("ledger" to ledger))
        assertTrue(isValidCustomerReportRange("2026-04-01", "2026-04-02"))
        assertFalse(isValidCustomerReportRange("2026-04-03", "2026-04-01"))
        assertFalse(isValidCustomerReportRange("not-a-date", ""))
        val rows = buildCustomerReportRows(data, CustomerReportCategory.POINTS, "2026-04-01", "2026-04-02")
        assertEquals(listOf("b", "a"), rows.map { it.id })
        assertEquals(3, buildCustomerReportRows(data, CustomerReportCategory.POINTS).size)
        assertEquals("-3", rows.first().points)
    }

    @Test fun csvQuotesCellsAndNeutralizesFormulaLikeText() {
        val csv = customerReportCsv(listOf(com.aman.customer.data.CustomerReportRow("id", "النقاط", "=SUM(1,1)", "2026-04-01", "", "-2", "", "", "قال \"مرحبًا\"")))
        assertTrue(csv.contains("'=SUM(1,1)"))
        assertTrue(csv.contains("قال \"\"مرحبًا\"\""))
        assertTrue(csv.startsWith("\"الفئة\""))
    }
}
