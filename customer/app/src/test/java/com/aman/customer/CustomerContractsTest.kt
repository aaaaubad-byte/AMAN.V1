package com.aman.customer

import com.aman.customer.data.CustomerScreen
import com.aman.customer.data.ProviderPrefix
import com.aman.customer.data.activationCost
import com.aman.customer.data.discoverProvider
import com.aman.customer.data.normalizePhoneE164
import com.aman.customer.data.phoneDigits
import com.aman.customer.data.CustomerReportCategory
import com.aman.customer.data.CustomerScreenData
import com.aman.customer.data.CustomerUiTraceability
import com.aman.customer.data.buildCustomerReportRows
import com.aman.customer.data.customerReportCsv
import com.aman.customer.data.isValidCustomerReportRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class CustomerContractsTest {
    @Test fun allFifteenCustomerScreensRemainInTheNavigationContract() {
        assertEquals(15, CustomerScreen.entries.size)
        assertEquals("C01", CustomerScreen.HOME.id)
        assertEquals("C15", CustomerScreen.ABOUT.id)
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
    @Test fun activationCostRequiresPositiveServerSuppliedInputs() {
        assertEquals(800L, activationCost(200, 4))
        assertNull(activationCost(0, 4))
        assertNull(activationCost(20, 0))
    }
    @Test fun customerTraceabilityHasUniqueValidScreenScopedIds() {
        val ids = CustomerUiTraceability.elements.map { it.elementId }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(CustomerUiTraceability.elements.all { it.screenId in CustomerUiTraceability.screenIds && it.elementId.startsWith("${it.screenId}.") })
        assertEquals(15, CustomerUiTraceability.screenIds.size)
        assertNotNull(CustomerUiTraceability.elements.firstOrNull { it.elementId == "C06.ACTION.ADD" })
        assertNotNull(CustomerUiTraceability.elements.firstOrNull { it.elementId == "C10.ACTION.SEND" })
        assertNotNull(CustomerUiTraceability.elements.firstOrNull { it.elementId == "C12.ACTION.EXPORT" })
    }
    @Test fun reportFiltersRealRowsByCategoryAndInclusiveDateRange() {
        val ledger = JSONArray()
            .put(JSONObject().put("id", "a").put("entry_type", "credit").put("amount", 10).put("created_at", "2026-04-01T12:00:00Z"))
            .put(JSONObject().put("id", "b").put("entry_type", "debit").put("amount", -3).put("created_at", "2026-04-02T12:00:00Z"))
            .put(JSONObject().put("id", "c").put("entry_type", "credit").put("amount", 99).put("created_at", "2026-04-03T12:00:00Z"))
        val data = CustomerScreenData(CustomerScreen.REPORTS, related = mapOf("ledger" to ledger))
        assertTrue(isValidCustomerReportRange("2026-04-01", "2026-04-02"))
        assertFalse(isValidCustomerReportRange("2026-04-03", "2026-04-01"))
        assertFalse(isValidCustomerReportRange("not-a-date", ""))
        val rows = buildCustomerReportRows(data, CustomerReportCategory.POINTS, "2026-04-01", "2026-04-02")
        assertEquals(listOf("b", "a"), rows.map { it.id })
        assertEquals(2, buildCustomerReportRows(data, CustomerReportCategory.POINTS).size)
    }
    @Test fun csvQuotesCellsAndNeutralizesFormulaLikeText() {
        val csv = customerReportCsv(listOf(com.aman.customer.data.CustomerReportRow("id", "النقاط", "=SUM(1,1)", "2026-04-01", "", "-2", "", "", "قال \"مرحبًا\"")))
        assertTrue(csv.contains("'=SUM(1,1)"))
        assertTrue(csv.contains("قال \"\"مرحبًا\"\""))
        assertTrue(csv.startsWith("\"الفئة\""))
    }
}
