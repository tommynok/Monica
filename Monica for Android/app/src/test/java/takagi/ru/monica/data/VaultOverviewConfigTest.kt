package takagi.ru.monica.data

import java.util.Date
import org.junit.Assert.*
import org.junit.Test

class VaultOverviewConfigTest {
    @Test fun defaultsEnableOverviewWithoutChangingListLayout() {
        val settings = AppSettings()
        assertTrue(settings.vaultOverviewEnabled)
        assertEquals("local", settings.vaultOverviewConfig.scope)
        assertEquals(8, settings.vaultOverviewConfig.order.size)
        assertEquals(settings.vaultV2LayoutMode, settings.copy(vaultOverviewEnabled = false).vaultV2LayoutMode)
    }

    @Test fun layoutAndIndependentPinsSurviveRoundTrip() {
        val config = VaultOverviewConfig(
            order = VaultOverviewModule.defaultOrder.reversed(), hidden = setOf("FOLDERS"),
            collapsed = setOf("FAVORITES"), pinnedCards = listOf("local/BANK_CARD/7:10"),
            pinnedItems = listOf("keepass:2/password/uuid"), recommendCards = false,
            recommendItems = true, scope = "keepass:2",
        )
        assertEquals(config, VaultOverviewConfig.decode(config.encode()))
        assertEquals(config.pinnedItems, config.copy(pinnedCards = emptyList()).pinnedItems)
    }

    @Test fun malformedAndFuturePreferencesCannotRemoveRequiredModulesOrSelectInvalidScopes() {
        assertEquals(VaultOverviewConfig(), VaultOverviewConfig.decode("broken JSON"))
        val normalized = VaultOverviewConfig.decode("""{"order":["ITEMS","ITEMS","FUTURE"],"hidden":["FUTURE"],"scope":"mdbx:-2","future":true}""")
        assertEquals("ITEMS", normalized.order.first())
        assertEquals(VaultOverviewModule.defaultOrder.toSet(), normalized.order.toSet())
        assertTrue(normalized.hidden.isEmpty())
        assertEquals("local", normalized.scope)
        assertEquals("local", VaultOverviewConfig(scope = "keepass:9999999999999999999999").normalized().scope)
    }

    @Test fun identitiesSeparateDatabasesTypesAndReusedLocalIdsButSurviveExternalRowRecreation() {
        val password = PasswordEntry(id = 1, title = "One", website = "", username = "", password = "secret", createdAt = Date(10))
        val card = SecureItem(id = 1, title = "One", itemType = ItemType.BANK_CARD, itemData = "secret", createdAt = Date(10))
        assertNotEquals(password.vaultOverviewKey(), card.vaultOverviewKey())
        assertNotEquals(password.vaultOverviewKey(), password.copy(createdAt = Date(20)).vaultOverviewKey())
        val synced = password.copy(bitwardenVaultId = 2, bitwardenCipherId = "cipher")
        assertEquals(synced.vaultOverviewKey(), synced.copy(id = 200, createdAt = Date(99)).vaultOverviewKey())
        assertNotEquals(synced.vaultOverviewKey(), synced.copy(bitwardenVaultId = 3).vaultOverviewKey())
        val keepass = password.copy(keepassDatabaseId = 2, keepassEntryUuid = "uuid")
        assertEquals(keepass.vaultOverviewKey(), keepass.copy(id = 500, createdAt = Date(99)).vaultOverviewKey())
        assertFalse(synced.vaultOverviewKey().contains("secret"))
    }
}
