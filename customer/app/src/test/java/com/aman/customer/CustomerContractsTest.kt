package com.aman.customer

import com.aman.customer.data.CustomerScreen
import com.aman.customer.data.ProviderPrefix
import com.aman.customer.data.activationCost
import com.aman.customer.data.discoverProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
    @Test fun activationCostRequiresPositiveServerSuppliedInputs() {
        assertEquals(800L, activationCost(200, 4))
        assertNull(activationCost(0, 4))
        assertNull(activationCost(20, 0))
    }
}
