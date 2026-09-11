package takagi.ru.monica.ui.vaultv2

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import takagi.ru.monica.R
import takagi.ru.monica.data.*
import takagi.ru.monica.repository.*
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.PageAdjustmentSettingsSnapshot
import takagi.ru.monica.utils.SavedCategoryFilterState
import takagi.ru.monica.utils.SettingsManager
import takagi.ru.monica.viewmodel.*

/** Exercise the real pane and its retained/classic-list pipeline with an isolated Room vault. */
@RunWith(AndroidJUnit4::class)
class VaultOverviewPaneTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val settings = SettingsManager(context)
    private lateinit var originalSettings: PageAdjustmentSettingsSnapshot
    private lateinit var originalFilter: SavedCategoryFilterState
    private lateinit var database: PasswordDatabase
    private val models = mutableListOf<ViewModel>()
    private lateinit var state: VaultV2PaneState
    private var appSettings by mutableStateOf(AppSettings())
    private var openedPassword: Long? = null
    private var darkTheme by mutableStateOf(false)

    @Before fun prepareVault(): Unit = runBlocking {
        originalSettings = settings.exportPageAdjustmentSettings()
        originalFilter = settings.categoryFilterStateFlow("vault_v2").first()
        settings.updateCategoryFilterState("vault_v2", SavedCategoryFilterState(type = "all"))
        database = Room.inMemoryDatabaseBuilder(context, PasswordDatabase::class.java).build()
        repeat(24) { index ->
            database.passwordEntryDao().insertPasswordEntry(PasswordEntry(id = index + 1L,
                title = "Overview password ${index + 1}", website = "", username = "demo", password = "", isFavorite = index < 3))
        }
        database.passwordEntryDao().insertPasswordEntry(PasswordEntry(id = 30L, title = "Archived local",
            website = "", username = "", password = "", isArchived = true))
    }

    @After fun restore() {
        models.forEach { it.viewModelScope.cancel() }
        runBlocking {
            settings.importPageAdjustmentSettings(originalSettings)
            settings.updateCategoryFilterState("vault_v2", originalFilter)
        }
        database.close()
    }

    private fun showPane() {
        val security = SecurityManager(context)
        val passwords = PasswordRepository(database.passwordEntryDao(), categoryDao = database.categoryDao(),
            bitwardenFolderDao = database.bitwardenFolderDao(), passwordArchiveSyncMetaDao = database.passwordArchiveSyncMetaDao())
        val items = SecureItemRepository(database.secureItemDao())
        fun <T : ViewModel> keep(model: T): T = model.also { models += it }
        val passwordModel = keep(PasswordViewModel(passwords, security)).also { it.restoreAuthenticatedUiState() }
        val totp = keep(TotpViewModel(items, passwords))
        val cards = keep(BankCardViewModel(items))
        val documents = keep(DocumentViewModel(items))
        val addresses = keep(BillingAddressViewModel(items))
        val notes = keep(NoteViewModel(items))
        val passkeys = keep(PasskeyViewModel(PasskeyRepository(database.passkeyDao())))
        val keepass = keep(LocalKeePassViewModel(context.applicationContext as Application, database.localKeePassDatabaseDao(), security))
        val settingsModel = keep(SettingsViewModel(settings))
        compose.setContent {
            MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
                state = rememberVaultV2PaneState(remember { VaultV2RetainedState() })
                VaultV2Pane(passwordModel, totp, cards, documents, addresses, notes, passkeys,
                    keepassDatabases = emptyList(), mdbxDatabases = emptyList(), bitwardenVaults = emptyList(),
                    localKeePassViewModel = keepass, settingsViewModel = settingsModel, state = state,
                    onOpenPassword = { openedPassword = it }, onOpenTotp = {}, onOpenBankCard = {}, onOpenDocument = {},
                    onOpenBillingAddress = {}, onOpenNote = {}, onOpenPasskey = {}, onOpenMdbxCommitHistory = {},
                    onOpenHistory = {}, onOpenTrashPage = {}, onOpenArchivePage = {}, onOpenCommonAccountTemplates = {},
                    appSettings = appSettings, securityManager = security, biometricEnabled = false,
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }
        }
        compose.waitUntil(15_000) { state.overviewSnapshot?.items?.size == 24 }
        compose.onNodeWithTag("vault_overview_screen").assertIsDisplayed()
    }

    @Test fun typeNavigationUsesTheRealListAndBackRestoresTheOverview() {
        showPane()
        compose.onNodeWithTag("overview_modules").performScrollToNode(hasTestTag("overview_type_PASSWORD"))
        compose.onNodeWithTag("overview_type_PASSWORD").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Overview password 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Overview password 1").performClick()
        compose.runOnIdle { assertEquals(1L, openedPassword); assertEquals("PASSWORD", state.overviewItemType) }
        Espresso.pressBack()
        compose.onNodeWithTag("vault_overview_screen").assertIsDisplayed()
        compose.runOnIdle { assertFalse(state.overviewListOpen); assertEquals("local", state.storageFilterType) }
    }

    @Test fun archiveBackKeepsScrollAndRestoresLiveItems() {
        showPane()
        compose.onNodeWithTag("overview_modules").performScrollToNode(hasTestTag("overview_archive"))
        var position = 0 to 0
        compose.runOnIdle { position = state.overviewScrollIndex to state.overviewScrollOffset }
        compose.onNodeWithTag("overview_archive").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Archived local").fetchSemanticsNodes().isNotEmpty() }
        Espresso.pressBack()
        compose.waitUntil(10_000) { state.overviewSnapshot?.items?.size == 24 }
        compose.onNodeWithTag("vault_overview_screen").assertIsDisplayed()
        compose.runOnIdle {
            assertFalse(state.isArchiveView)
            assertEquals(position, state.overviewScrollIndex to state.overviewScrollOffset)
            assertEquals(1, state.overviewSnapshot?.archiveCount)
        }
    }

    @Test fun disablingOverviewRestoresSavedClassicScopeAndContent() {
        showPane()
        compose.onNodeWithTag("overview_modules").performScrollToNode(hasTestTag("overview_type_PASSWORD"))
        compose.onNodeWithTag("overview_type_PASSWORD").performClick()
        compose.runOnIdle { appSettings = appSettings.copy(vaultOverviewEnabled = false) }
        compose.waitUntil(10_000) { state.overviewScope == null && state.storageFilterType == VAULT_V2_STORAGE_FILTER_ALL }
        compose.onNodeWithTag("vault_overview_screen").assertDoesNotExist()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Overview password 1").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { assertFalse(state.overviewListOpen); assertNull(state.overviewItemType) }
    }

    @Test fun sharedTopBarSearchKeepsResultsAfterImeActionAndRestoresTheOverview() {
        showPane()
        compose.onNodeWithTag("overview_modules").performScrollToNode(hasTestTag("overview_archive"))
        var position = 0 to 0
        compose.runOnIdle { position = state.overviewScrollIndex to state.overviewScrollOffset }
        compose.onNodeWithTag("overview_search").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("password 24")
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Overview password 24").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNode(hasSetTextAction()).assertTextEquals("password 24")
        compose.onNodeWithText("Overview password 24").performClick()
        compose.runOnIdle { assertTrue(state.overviewListOpen); assertEquals(24L, openedPassword) }
        compose.onNodeWithContentDescription(context.getString(R.string.topbar_close_search)).performClick()
        compose.onNodeWithTag("overview_top_bar").assertIsDisplayed()
        compose.onNodeWithTag("overview_customize").assertIsDisplayed()
        compose.runOnIdle {
            assertFalse(state.overviewListOpen)
            assertEquals("local", state.storageFilterType)
            assertEquals(position, state.overviewScrollIndex to state.overviewScrollOffset)
        }
    }

    @Test fun groupedSearchResultsKeepSelectionSwipeAndSingleResultNavigation() {
        showPane()
        compose.onNodeWithTag("overview_search").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("password 2")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("vault_item_password:24").fetchSemanticsNodes().isNotEmpty() }
        val ids = listOf(2, 20, 21, 22, 23, 24)
        ids.zipWithNext { firstId, secondId ->
            val first = compose.onNodeWithTag("vault_item_password:$firstId").fetchSemanticsNode().boundsInRoot
            val second = compose.onNodeWithTag("vault_item_password:$secondId").fetchSemanticsNode().boundsInRoot
            val gap = second.top - first.bottom
            assertTrue("Filtered rows must remain separated", gap >= 1f && gap <= with(compose.density) { 4.dp.toPx() })
        }
        capture("vault-grouped-light.png")
        compose.runOnIdle { darkTheme = true }
        capture("vault-grouped-dark.png")
        compose.onNodeWithTag("vault_item_password:20").performTouchInput { longClick() }
        compose.onNodeWithTag("vault_item_password:24").performClick()
        compose.runOnIdle { assertNull(openedPassword) }
        compose.onNodeWithText("2").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithContentDescription(context.getString(R.string.select_all)).assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertTextEquals("password 2")
        compose.runOnIdle { assertTrue(state.overviewListOpen) }
        compose.onNodeWithTag("vault_item_password:2").performTouchInput { swipeRight() }
        compose.onNodeWithContentDescription(context.getString(R.string.select_all)).assertIsDisplayed()
        Espresso.pressBack()
        compose.onNode(hasSetTextAction()).assertTextEquals("password 2")
        compose.onNodeWithTag("vault_item_password:2").performTouchInput { swipeLeft() }
        compose.onNodeWithText(context.getString(R.string.cancel)).assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("password 24")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("vault_item_password:2").fetchSemanticsNodes().isEmpty() }
        capture("vault-grouped-single.png")
        compose.onNodeWithTag("vault_item_password:24").performClick()
        compose.runOnIdle { assertEquals(24L, openedPassword) }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        File(context.getExternalFilesDir("overview-verification"), name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
