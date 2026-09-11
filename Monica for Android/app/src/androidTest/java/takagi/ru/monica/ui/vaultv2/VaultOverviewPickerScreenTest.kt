package takagi.ru.monica.ui.vaultv2

import android.graphics.Bitmap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Date
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.R
import takagi.ru.monica.data.*
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.data.model.CardWalletDataCodec
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_SIMPLE

@RunWith(AndroidJUnit4::class)
class VaultOverviewPickerScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val cards = (1L..3L).map { id ->
        val secure = SecureItem(id = id, title = "Everyday card", itemType = ItemType.BANK_CARD,
            bitwardenVaultId = if (id == 3L) 2 else null, createdAt = Date(10),
            itemData = CardWalletDataCodec.encodeBankCardData(BankCardData(
                cardNumber = if (id == 3L) "6222021234569876" else "411111111111000$id",
                cardholderName = "Demo", bankName = if (id == 3L) "Work Bank" else "Test bank $id",
                expiryMonth = "09", expiryYear = "2030")))
        VaultV2Item("bank_card:$id", VaultV2ItemType.BANK_CARD, secure.title, "", false, "$id", emptyList(), secureItem = secure)
    }
    private val passwords = (10L..12L).map { id -> buildVaultV2PasswordItems(listOf(PasswordEntry(
        id = id, title = if (id == 11L) "GitHub" else "Google", username = "demo$id@example.test", password = "",
        website = "google.com", bitwardenVaultId = if (id == 12L) 2 else null, createdAt = Date(10),
        customIconType = if (id == 11L) PASSWORD_ICON_TYPE_SIMPLE else "NONE", customIconValue = if (id == 11L) "github" else null,
    ))).single() }
    private val sources = listOf(VaultOverviewSource("local", "Personal", "Monica"),
        VaultOverviewSource("bitwarden:2", "Work", "Bitwarden"), VaultOverviewSource("keepass:3", "Locked", "KeePass", true),
        VaultOverviewSource("mdbx:4", "Project MDBX", "MDBX"))
    private var config by mutableStateOf(VaultOverviewConfig(pinnedCards = cards.take(2).map { it.overviewIdentity() },
        pinnedItems = passwords.map { it.overviewIdentity() }))
    private var shown by mutableStateOf(true)

    private fun showPicker(wallet: Boolean, scope: String = "all", dark: Boolean = false) {
        val security = SecurityManager(context)
        compose.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                TextButton(onClick = { shown = true }) { Text("Open picker") }
                if (shown) VaultOverviewPickerSheet(
                    cards = wallet, items = (cards + passwords).filter { scope == "all" || it.overviewSource() == scope },
                    sources = sources, currentScope = scope,
                    keepassDatabases = listOf(LocalKeePassDatabase(3, "Locked", "locked.kdbx")),
                    mdbxDatabases = listOf(LocalMdbxDatabase(4, "Project MDBX", "project.mdbx")),
                    bitwardenVaults = listOf(BitwardenVault(id = 2, email = "work@example.test", displayName = "Work", isLocked = false)),
                    config = config, securityManager = security, onConfigChange = { config = it(config).normalized() },
                    onDismiss = { shown = false },
                )
            }
        }
    }

    @Test fun bankBrandAndTailDistinguishCardsAndSelectionsSurviveDatabaseFilters() {
        showPicker(wallet = true, dark = true)
        val first = compose.onNodeWithTag("overview_pin_row_bank_card:1")
        first.assertIsOn().assertTextContains("Test bank 1").assertTextContains("•••• 0001")
        compose.onNodeWithTag("overview_pin_brand_bank_card:1", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("overview_pin_row_password:10").assertDoesNotExist()
        val position = first.fetchSemanticsNode().boundsInRoot
        first.performClick().assertIsOff()
        assertEquals(position, first.fetchSemanticsNode().boundsInRoot)
        first.performClick().assertIsOn()
        chooseDatabase("work@example.test")
        first.assertDoesNotExist()
        compose.onNodeWithTag("overview_pin_row_bank_card:3").assertIsOff().assertTextContains("Work Bank").performClick().assertIsOn()
        chooseDatabase(context.getString(R.string.category_selection_menu_local_database))
        first.assertIsOn()
        compose.onNodeWithTag("overview_pin_row_bank_card:3").assertDoesNotExist()
        chooseDatabase(context.getString(R.string.category_all))
        capturePicker("picker-cards-dark.png")
        compose.onNodeWithTag("overview_pin_done").performClick()
        compose.onNodeWithTag("overview_pin_sheet").assertDoesNotExist()
        compose.onNodeWithText("Open picker").performClick()
        compose.onNodeWithTag("overview_pin_row_bank_card:3").assertIsOn()
        compose.runOnIdle { assertEquals("local", config.scope); assertEquals(3, config.pinnedCards.size) }
    }

    @Test fun accountIconsAndSearchRemainVisibleAfterKeyboardBackAndImeSearch() {
        showPicker(wallet = false)
        compose.onNodeWithTag("overview_pin_row_password:10").assertTextContains("Google").assertTextContains("demo10@example.test")
        compose.onNodeWithTag("overview_pin_icon_password:11", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("overview_pin_row_bank_card:1").assertDoesNotExist()
        capturePicker("picker-items-light.png")
        chooseDatabase("work@example.test")
        compose.onNodeWithTag("overview_pin_row_password:10").assertDoesNotExist()
        val search = compose.onNodeWithTag("overview_pin_search")
        search.performClick().performTextInput("demo")
        try {
            compose.waitUntil(5000) {
                compose.onAllNodesWithText(context.getString(R.string.vault_overview_pin_hint)).fetchSemanticsNodes().isEmpty()
            }
        } finally {
            // The platform IME animates in a different process from Compose.
            // Let that animation settle for the visual evidence, not assertions.
            android.os.SystemClock.sleep(500)
            File(context.getExternalFilesDir("overview-verification"), "picker-items-keyboard.png").outputStream().use {
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithTag("overview_pin_done").assertIsDisplayed()
        assertSheetMeetsKeyboard()
        Espresso.pressBack()
        compose.onNodeWithTag("overview_pin_sheet").assertIsDisplayed()
        search.assertTextContains("demo")
        compose.onNodeWithTag("overview_pin_row_password:12").assertIsDisplayed()
        search.performClick().performImeAction()
        compose.onNodeWithTag("overview_pin_sheet").assertIsDisplayed()
        search.assertTextContains("demo")
        chooseDatabase(context.getString(R.string.category_selection_menu_local_database))
        search.assertTextContains("demo")
        compose.onNodeWithTag("overview_pin_row_password:10").assertIsDisplayed()
        compose.onNodeWithTag("overview_pin_row_password:12").assertDoesNotExist()
        compose.runOnIdle { assertEquals("local", config.scope) }
    }

    @Test fun bankSearchLockedAndEmptyDatabasesHaveDistinctStates() {
        showPicker(wallet = true)
        val search = compose.onNodeWithTag("overview_pin_search")
        search.performTextInput("bank 2")
        search.performImeAction()
        compose.onNodeWithTag("overview_pin_row_bank_card:2").assertIsDisplayed()
        compose.onNodeWithTag("overview_pin_row_bank_card:1").assertDoesNotExist()
        compose.onNodeWithTag("overview_pin_clear").performClick()
        chooseDatabase("Locked")
        compose.onNodeWithText(context.getString(R.string.vault_overview_locked_hint)).assertIsDisplayed()
        compose.onNodeWithTag("overview_pin_row_bank_card:1").assertDoesNotExist()
        chooseDatabase("Project MDBX")
        compose.onNodeWithText(context.getString(R.string.vault_overview_empty_pins)).assertIsDisplayed()
        chooseDatabase(context.getString(R.string.category_all))
        compose.onNodeWithTag("overview_pin_row_bank_card:1").assertIsDisplayed()
    }

    @Test fun aSingleDatabaseStaysScopedAndThePinLimitNeverSilentlyDropsASelection() {
        config = config.copy(pinnedCards = listOf(cards[0].overviewIdentity()) + (1..198).map { "previous:$it" })
        showPicker(wallet = true, scope = "local")
        compose.onNodeWithTag("overview_pin_scope").assertDoesNotExist()
        compose.onNodeWithTag("overview_pin_row_bank_card:3").assertDoesNotExist()
        compose.onNodeWithTag("overview_pin_row_bank_card:2").performClick().assertIsOn()
        compose.runOnIdle { assertEquals(VAULT_OVERVIEW_MAX_PINS, config.pinnedCards.size) }
        compose.onNodeWithText(context.getString(R.string.vault_overview_picker_limit, VAULT_OVERVIEW_MAX_PINS)).assertIsDisplayed()
        compose.onNodeWithTag("overview_pin_row_bank_card:1").performClick().assertIsOff().assertIsEnabled()
        compose.onNodeWithTag("overview_pin_row_bank_card:2").assertIsOn()
        compose.runOnIdle { assertEquals(VAULT_OVERVIEW_MAX_PINS - 1, config.pinnedCards.size) }
    }

    private fun chooseDatabase(label: String) {
        compose.onNodeWithTag("overview_pin_scope").performClick()
        compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("overview_pin_database_menu"))).performClick()
    }

    private fun assertSheetMeetsKeyboard() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val info = automation.serviceInfo
        val originalFlags = info.flags
        try {
            info.flags = originalFlags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = info
            val ime = automation.windows.firstOrNull { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            assertNotNull("A real keyboard must be visible for this check", ime)
            val bounds = android.graphics.Rect()
            ime!!.getBoundsInScreen(bounds)
            // Measure a child after the sheet's placement modifier. The outer
            // sheet semantics are attached before its draggable offset.
            val footer = compose.onNodeWithTag("overview_pin_footer").fetchSemanticsNode().boundsInWindow
            assertTrue("Footer bottom ${footer.bottom} must meet IME top ${bounds.top}", kotlin.math.abs(footer.bottom - bounds.top) <= 4f)
        } finally {
            info.flags = originalFlags
            automation.serviceInfo = info
        }
    }

    private fun capturePicker(name: String) {
        compose.waitForIdle()
        File(context.getExternalFilesDir("overview-verification"), name).outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
