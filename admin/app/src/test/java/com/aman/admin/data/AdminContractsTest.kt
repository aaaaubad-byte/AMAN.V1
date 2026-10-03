package com.aman.admin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AdminContractsTest {
    @Test fun exactlyFifteenFunctionalAdminRoutesAreDefined() {
        assertEquals(15, AdminSection.entries.size)
    }

    @Test fun nineAdminDashboardTilesHaveDistinctDestinations() {
        assertEquals(9, adminSections.size)
        assertEquals(9, adminSections.map { it.section }.distinct().size)
    }

    @Test fun onlyImplementedMigrationRpcMutationsAreExposed() {
        assertEquals(setOf("approve_points_purchase", "reject_points_purchase", "execute_payment_task"), AdminMutation.entries.map { it.rpcName }.toSet())
    }

    @Test fun everyRecordSectionHasAnExplicitDatabaseBindingOrSearchContract() {
        AdminSection.entries.filter { it.supportsList && it !in setOf(AdminSection.SEARCH) }
            .forEach { assertNotNull("Missing table contract for ${it.name}", it.table) }
        assertEquals(null, AdminSection.SEARCH.table)
    }

    @Test fun missingBackendOperationsAreRecordedInScreenContracts() {
        assertNotNull(AdminSection.PURCHASES.contractNote)
        assertNotNull(AdminSection.PAYMENT_TASKS.contractNote)
        assertNotNull(AdminSection.NOTIFICATIONS.contractNote)
        assertNotNull(AdminSection.TASK_SETTINGS.contractNote)
    }
}
