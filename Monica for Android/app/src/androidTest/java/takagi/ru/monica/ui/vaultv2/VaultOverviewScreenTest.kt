package takagi.ru.monica.ui.vaultv2

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Date
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.R
import takagi.ru.monica.data.*
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.ui.VaultV2FabMenu
import takagi.ru.monica.ui.VaultV2FabMenuAction
import takagi.ru.monica.ui.cardwallet.WalletStackOverlayHost

@RunWith(AndroidJUnit4::class)
class VaultOverviewScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val cards = (1L..2L).map { id ->
        val secure = SecureItem(id = id, itemType = ItemType.BANK_CARD, title = "Wallet card $id", createdAt = Date(10),
            itemData = Json.encodeToString(BankCardData(cardNumber = "411111111111${id.toString().padStart(4, '0')}",
                cardholderName = "MONICA DEMO", bankName = "Test bank $id", expiryMonth = "09", expiryYear = "2030")))
        VaultV2Item("bank_card:$id", VaultV2ItemType.BANK_CARD, secure.title, "", false, "$id", emptyList(), secureItem = secure)
    }
    private val passwords = (10L..12L).map { id -> buildVaultV2PasswordItems(listOf(PasswordEntry(
        id = id, title = "Password $id", username = "demo", website = "", password = "", createdAt = Date(10),
        bitwardenVaultId = if (id == 12L) 2 else null, isFavorite = true))).single() }
    private val rows = cards + passwords
    private val sources = listOf(VaultOverviewSource("local", "Local", "Monica"),
        VaultOverviewSource("bitwarden:2", "Work", "Bitwarden"), VaultOverviewSource("keepass:3", "Locked", "KeePass", locked = true))
    private var config by mutableStateOf(VaultOverviewConfig(pinnedCards = cards.map { it.overviewIdentity() },
        pinnedItems = passwords.map { it.overviewIdentity() }))
    private var scopeKey by mutableStateOf("local")
    private var selectedCardKey by mutableStateOf<String?>(null)
    private val cardStack = VaultOverviewCardStackState()
    private var route by mutableStateOf<String?>(null)
    private var created: String? = null
    private lateinit var listState: LazyListState

    private fun showOverview(wideDetail: Boolean = false) {
        val manager = SecurityManager(context)
        compose.setContent {
            MaterialTheme {
                WalletStackOverlayHost {
                listState = rememberLazyListState()
                Box(Modifier.fillMaxSize().statusBarsPadding()) {
                    if (route != null) Button(onClick = { route = null }, modifier = Modifier.testTag("return_home")) {
                        Text("Return")
                    }
                    if (route == null || wideDetail) {
                        val snapshot = remember(scopeKey, config.pinnedCards, config.pinnedItems, config.recommendCards, config.recommendItems) {
                            buildVaultOverviewSnapshot(rows, sources, scopeKey, config, emptyMap(), emptyMap(),
                                emptyList(), emptyList(), emptyMap(), emptyList(), aggregate = ::aggregateVaultOverviewKotlin)
                        }
                        VaultOverviewScreen(snapshot, sources, scopeKey, config, listState,
                            selectedCardKey = selectedCardKey, onSelectedCardChange = { selectedCardKey = it },
                            cardStackState = cardStack, isDetailVisible = wideDetail && route != null,
                            securityManager = manager, reduceAnimations = false, trashCount = 0,
                            onConfigChange = { config = it(config).normalized() }, onSelectScope = { scopeKey = it },
                            onOpenSource = { route = "source:$it" }, onOpenItem = { route = it.key },
                            onOpenType = { route = "type:${it.name}:$scopeKey" }, onOpenFolder = { route = "folder:${it.key}" },
                            onFavorites = { route = "favorites:$scopeKey" }, onArchive = { route = "archive:$scopeKey" },
                            onTrash = { route = "trash:$scopeKey" }, onAllItems = { route = "all:$scopeKey" },
                            onSearch = { route = "search:$scopeKey" }, onUnlock = { route = "unlock:$scopeKey" })
                        VaultV2FabMenu(0.dp, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer,
                            onExpandStateChanged = {}, menuActions = listOf(
                                VaultV2FabMenuAction(Icons.Default.Add, R.string.item_type_password) { created = "password:$scopeKey" }))
                    }
                }
                }
            }
        }
        compose.onNodeWithTag("vault_overview_screen").assertIsDisplayed()
    }

    @Test fun cardAndItemPickersAreSeparateAndUseTheExistingCreationFab() {
        showOverview()
        compose.onNodeWithTag("overview_pin_cards").performClick()
        compose.onNodeWithTag("overview_pin_row_bank_card:1").assertIsDisplayed()
        compose.onNodeWithTag("overview_pin_row_password:10").assertDoesNotExist()
        Espresso.pressBack()
        compose.onNodeWithTag("overview_modules").performScrollToNode(hasTestTag("overview_pin_items"))
        compose.onNodeWithTag("overview_pin_items").performClick()
        compose.onNodeWithTag("overview_pin_row_password:10").assertIsDisplayed()
        compose.onNodeWithTag("overview_pin_row_bank_card:1").assertDoesNotExist()
        Espresso.pressBack()
        compose.onNodeWithTag("vault_add_fab").performClick()
        compose.onNodeWithText(context.getString(R.string.item_type_password), useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals("password:local", created) }
    }

    @Test fun moduleVisibilityOrderAndCollapseWorkIndependently() {
        showOverview()
        compose.onNodeWithTag("overview_customize").performClick()
        compose.onNodeWithTag("overview_move_down_CARDS").performClick()
        compose.runOnIdle { assertEquals("ITEMS", config.order.first()) }
        compose.onNodeWithTag("overview_visible_CARDS").performClick()
        Espresso.pressBack()
        compose.onNodeWithTag("overview_cards").assertDoesNotExist()
        compose.onNodeWithTag("overview_modules").performScrollToNode(hasTestTag("overview_toggle_FAVORITES"))
        compose.onNodeWithTag("overview_toggle_FAVORITES").performClick()
        compose.runOnIdle { assertTrue("FAVORITES" in config.collapsed); assertFalse("ITEMS" in config.collapsed) }
    }

    @Test fun returningFromACardAndArchivePreservesTheHomeCardAndScroll() {
        showOverview()
        compose.waitUntil(5000) { cardStack.prepared?.cards?.size == 2 }
        compose.onNodeWithTag("wallet_stack_overview:local").performClick()
        compose.onNodeWithTag("wallet_stack_scroll").performTouchInput { swipeUp() }
        compose.waitUntil(5000) { selectedCardKey == cards[1].overviewIdentity() }
        compose.onNodeWithTag("wallet_stack_card_2").performClick()
        compose.runOnIdle { assertEquals(cards[1].key, route) }
        compose.onNodeWithTag("return_home").performClick()
        compose.runOnIdle { assertEquals(cards[1].overviewIdentity(), selectedCardKey) }
        compose.onNodeWithTag("wallet_stack_card_2").assertIsDisplayed()
        compose.onNodeWithTag("wallet_stack_collapse").performClick()
        compose.onNodeWithTag("wallet_stack_browser").assertDoesNotExist()
        compose.onNodeWithTag("overview_modules").performScrollToNode(hasTestTag("overview_archive"))
        var position = 0 to 0
        compose.runOnIdle { position = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
        compose.onNodeWithTag("overview_archive").performClick()
        compose.runOnIdle { assertEquals("archive:local", route) }
        compose.onNodeWithTag("return_home").performClick()
        compose.runOnIdle { assertEquals(position, listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset) }
    }

    @Test fun closedDeckHasNoHorizontalPagingAndBackCollapsesTheSharedBrowser() {
        showOverview()
        compose.waitUntil(5000) { cardStack.prepared?.cards?.size == 2 }
        compose.runOnIdle { selectedCardKey = cards[0].overviewIdentity() }
        compose.onNodeWithTag("overview_card_deck").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertNull(route); assertEquals(cards[0].overviewIdentity(), selectedCardKey) }
        if (!cardStack.expanded) compose.onNodeWithTag("wallet_stack_overview:local").performClick()
        compose.onNodeWithTag("wallet_stack_browser").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithTag("wallet_stack_browser").assertDoesNotExist()
        compose.onNodeWithTag("overview_card_deck").assertIsDisplayed()
    }

    @Test fun inlineDetailHidesTheOverlayAndReturnsToTheSameCard() {
        showOverview(wideDetail = true)
        compose.waitUntil(5000) { cardStack.prepared?.cards?.size == 2 }
        compose.onNodeWithTag("wallet_stack_overview:local").performClick()
        compose.onNodeWithTag("wallet_stack_card_1").performClick()
        compose.runOnIdle { assertEquals(cards[0].key, route); assertTrue(cardStack.expanded) }
        compose.onNodeWithTag("wallet_stack_browser").assertDoesNotExist()
        compose.runOnIdle { route = null }
        compose.onNodeWithTag("wallet_stack_card_1").assertIsDisplayed()
        compose.onNodeWithTag("wallet_stack_collapse").performClick()
        compose.onNodeWithTag("overview_card_deck").assertIsDisplayed()
    }

    @Test fun restoringTheCardsModulePreparesItsDeckAgain() {
        showOverview()
        compose.waitUntil(5000) { cardStack.prepared?.cards?.size == 2 }
        compose.onNodeWithTag("overview_toggle_CARDS").performClick()
        compose.onNodeWithTag("overview_card_deck").assertDoesNotExist()
        compose.onNodeWithTag("overview_toggle_CARDS").performClick()
        compose.waitUntil(5000) { cardStack.prepared?.cards?.size == 2 }
        compose.onNodeWithTag("wallet_stack_overview:local").performClick()
        compose.onNodeWithTag("wallet_stack_browser").assertIsDisplayed()
    }

    @Test fun databaseScopeReachesTheExistingTypeListAndLockedDatabasesStayHidden() {
        showOverview()
        compose.onNodeWithTag("overview_scope").performClick()
        compose.onNodeWithTag("overview_source_bitwarden:2").performClick()
        compose.onNodeWithTag("overview_modules").performScrollToNode(hasTestTag("overview_type_PASSWORD"))
        compose.onNodeWithTag("overview_type_PASSWORD").performClick()
        compose.runOnIdle { assertEquals("type:PASSWORD:bitwarden:2", route) }
        compose.onNodeWithTag("return_home").performClick()
        compose.onNodeWithTag("overview_scope").performClick()
        compose.onNodeWithTag("overview_source_keepass:3").performClick()
        compose.onNodeWithTag("overview_modules").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.vault_overview_unlock)).assertIsDisplayed()
    }

    @Test fun capturesTheNativeOverview() {
        showOverview()
        compose.waitUntil(5000) { cardStack.prepared?.cards?.size == 2 }
        compose.waitForIdle()
        val file = File(context.getExternalFilesDir("overview-verification"), "native-overview.png")
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(file.length() > 0)
        compose.onNodeWithTag("wallet_stack_overview:local").performClick()
        compose.onNodeWithTag("wallet_stack_browser").assertIsDisplayed()
        File(file.parentFile, "native-overview-expanded.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
