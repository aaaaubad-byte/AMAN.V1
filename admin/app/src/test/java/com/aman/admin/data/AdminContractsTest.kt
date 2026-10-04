package com.aman.admin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminContractsTest {
    @Test fun exactlyFifteenStableAdminRoutesAreDefined() {
        assertEquals(15, AdminSection.entries.size)
        assertEquals((1..15).map { "A%02d".format(it) }, AdminSection.entries.map { it.screenId })
        assertEquals(15, AdminSection.entries.map { it.screenId }.distinct().size)
    }

    @Test fun nineDashboardTilesHaveDistinctDestinations() {
        assertEquals(9, adminSections.size)
        assertEquals(9, adminSections.map { it.section }.distinct().size)
        assertTrue(adminSections.none { it.section in setOf(AdminSection.HOME, AdminSection.SEARCH, AdminSection.REPORTS, AdminSection.ACCOUNT) })
    }

    @Test fun allMutationsAreNamedBackendRpcsAndProviderCatalogRpcsArePresent() {
        val names = AdminMutation.entries.map { it.rpcName }.toSet()
        assertTrue(names.containsAll(setOf(
            "approve_points_purchase", "reject_points_purchase", "execute_payment_task", "reschedule_payment_task", "cancel_payment_task",
            "admin_update_profile", "admin_set_subscriber_status", "admin_set_customer_number_status", "admin_save_provider",
            "admin_save_telecom_prefix", "admin_save_provider_tariff", "admin_save_points_package", "admin_save_payment_method",
            "admin_save_task_settings", "send_admin_notification",
        )))
        assertFalse("Admin cannot edit phone numbers", names.any { it.contains("edit_customer_number") || it == "admin_update_customer_number" })
    }

    @Test fun recordSectionsHaveExplicitReadPermissionAndTableContracts() {
        AdminSection.entries.filter { it !in setOf(AdminSection.SEARCH) }.forEach { section ->
            assertTrue("Missing permission for ${section.name}", section.readPermission.startsWith("admin_"))
            assertTrue("Missing table binding for ${section.name}", section.table != null)
        }
        assertEquals(null, AdminSection.SEARCH.table)
    }

    @Test fun providerPrefixAndTariffWritesShareTheManagePermission() {
        assertEquals(AdminPermissions.PROVIDERS_READ, AdminSection.PROVIDERS.readPermission)
        assertEquals("admin_providers.manage", AdminPermissions.PROVIDERS_MANAGE)
        assertEquals("admin_save_telecom_prefix", AdminMutation.SAVE_PREFIX.rpcName)
        assertEquals("admin_save_provider_tariff", AdminMutation.SAVE_TARIFF.rpcName)
    }

    @Test fun reportsUseRealSourceTablesAndHaveDateColumns() {
        assertTrue(ReportType.all.isNotEmpty())
        ReportType.all.forEach { report ->
            assertTrue(report.table.isNotBlank())
            assertTrue(report.dateColumn.endsWith("_at"))
        }
    }

    @Test fun providerChildListsAndA04StatusOnlyContractAreExplicit() {
        assertTrue(RelatedListKind.entries.containsAll(listOf(RelatedListKind.PROVIDER_PREFIXES, RelatedListKind.PROVIDER_TARIFFS)))
        assertEquals("admin_set_customer_number_status", AdminMutation.SET_CUSTOMER_NUMBER_STATUS.rpcName)
        assertFalse(AdminMutation.entries.any { it.name.contains("EDIT_NUMBER") })
    }
}
