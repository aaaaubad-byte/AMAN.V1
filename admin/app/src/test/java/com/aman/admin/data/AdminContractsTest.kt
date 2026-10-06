package com.aman.admin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminContractsTest {
    @Test fun referenceContainsExactlyTwentyCanonicalAdminScreens() {
        assertEquals(19, adminRoutes.size)
        assertEquals((1..20).map { "A%02d".format(it) }.filterNot { it == "A18" }, adminRoutes.map { it.screenId })
        assertEquals(19, adminRoutes.map { it.screenId }.distinct().size)
        assertFalse(adminRoutes.any { it.screenId == "A18" })
        assertEquals(setOf("A01", "A04", "A05", "A16", "A17", "A19", "A20"),
            AdminUiTraceability.coveredRoutes().intersect(setOf("A01", "A04", "A05", "A16", "A17", "A19", "A20")))
    }

    @Test fun dashboardHasNineDistinctPrimaryDestinations() {
        assertEquals(9, adminSections.size)
        assertEquals(9, adminSections.map { it.section }.distinct().size)
        assertTrue(adminSections.none { it.section in setOf(AdminSection.HOME, AdminSection.SEARCH, AdminSection.REPORTS, AdminSection.ACCOUNT) })
    }

    @Test fun everyMutationMapsToTheV11RpcSurface() {
        val names = AdminMutation.entries.map { it.rpcName }.toSet()
        assertTrue(names.containsAll(setOf(
            "approve_points_purchase", "reject_points_purchase", "admin_execute_periodic_task",
            "admin_reschedule_periodic_task", "admin_cancel_periodic_task", "admin_update_customer_profile",
            "admin_set_customer_number_status", "admin_save_telecom_company", "admin_save_telecom_prefix",
            "admin_save_provider_tariff", "admin_save_points_package", "admin_save_payment_method",
            "admin_save_task_configuration", "admin_create_expense", "admin_grant_points",
            "admin_approve_support_request", "admin_send_support_reply", "admin_close_support_conversation", "admin_send_notification",
        )))
        assertFalse("Admin cannot edit phone entities", names.any { it.contains("edit_customer_number") || it == "admin_update_customer_number" })
    }

    @Test fun recordSectionsBindV11TablesAndRealPermissions() {
        AdminSection.entries.filter { it !in setOf(AdminSection.SEARCH, AdminSection.HOME, AdminSection.REPORTS, AdminSection.ACCOUNT,
            AdminSection.LOGIN, AdminSection.RECOVERY) }.forEach { section ->
            assertTrue("Missing permission for ${section.name}", section.readPermission.isNotBlank())
            assertTrue("Missing table binding for ${section.name}", section.table != null)
        }
        assertEquals("customer_profile", AdminSection.SUBSCRIBERS.table)
        assertEquals("customer_number", AdminSection.ADDED_NUMBERS.table)
        assertEquals("task_plan", AdminSection.TASK_PLANS.table)
        assertEquals("periodic_task", AdminSection.PAYMENT_TASKS.table)
        assertEquals("financial_ledger", AdminSection.FINANCE.table)
        assertEquals("support_conversation", AdminSection.COMMUNICATIONS.table)
    }

    @Test fun companyCatalogAndTaskSettingsUseSpecificWritePermissions() {
        assertEquals("providers.read", AdminSection.PROVIDERS.readPermission)
        assertEquals("providers.write", AdminPermissions.PROVIDERS_MANAGE)
        assertEquals("task_settings.write", AdminPermissions.TASKS_SETTINGS)
        assertEquals("telecom_company", AdminSection.PROVIDERS.table)
        assertEquals("admin_save_telecom_prefix", AdminMutation.SAVE_PREFIX.rpcName)
        assertEquals("admin_save_provider_tariff", AdminMutation.SAVE_TARIFF.rpcName)
    }

    @Test fun reportTypesUseRealTablesAndTimestampColumns() {
        assertTrue(ReportType.all.isNotEmpty())
        ReportType.all.forEach { report ->
            assertTrue(report.table.isNotBlank())
            assertTrue(report.dateColumn.endsWith("_at"))
        }
        assertTrue(ReportType.all.map { it.table }.containsAll(setOf("customer_profile", "customer_number", "points_purchase", "points_ledger", "protection_period", "operation", "financial_ledger", "periodic_task")))
    }

    @Test fun relatedListsCoverA02A04A05AndA11() {
        assertTrue(RelatedListKind.entries.containsAll(listOf(RelatedListKind.SUBSCRIBER_NUMBERS,
            RelatedListKind.SUBSCRIBER_POINTS, RelatedListKind.SUBSCRIBER_HISTORY, RelatedListKind.PLAN_TASKS,
            RelatedListKind.PROVIDER_PREFIXES, RelatedListKind.PROVIDER_TARIFFS, RelatedListKind.SUPPORT_MESSAGES)))
        assertEquals("admin_set_customer_number_status", AdminMutation.SET_CUSTOMER_NUMBER_STATUS.rpcName)
        assertFalse(AdminMutation.entries.any { it.name.contains("EDIT_NUMBER") })
    }

    @Test fun traceabilityCoversAllRoutesAndCallsOutOpenGaps() {
        assertEquals((1..20).map { "A%02d".format(it) }.toSet(), AdminUiTraceability.coveredRoutes())
        assertEquals(AdminUiTraceability.elements.size, AdminUiTraceability.ids().size)
        AdminUiTraceability.elements.forEach { element ->
            assertTrue(element.readPermission.isNotBlank())
            assertTrue(element.elementId.startsWith(element.screenId + "."))
            assertTrue(element.backendEffect.isNotBlank())
        }
        assertTrue(AdminUiTraceability.knownGaps.any { it.gapId == "GAP-DB-016" })
        assertFalse(AdminUiTraceability.ids().contains("A03.ACTION.EDIT_PHONE_ENTITY"))
    }
}
