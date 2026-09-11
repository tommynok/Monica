package takagi.ru.monica.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val VAULT_OVERVIEW_MAX_PINS = 200

enum class VaultOverviewModule {
    CARDS, ITEMS, FAVORITES, TYPES, FOLDERS, DATABASES, ARCHIVE, TRASH;

    companion object {
        val defaultOrder: List<String> = entries.map { it.name }
    }
}

/** Display preferences only; opening the overview never changes the saved list layout. */
@Serializable
data class VaultOverviewConfig(
    val order: List<String> = VaultOverviewModule.defaultOrder,
    val hidden: Set<String> = emptySet(),
    val collapsed: Set<String> = setOf(VaultOverviewModule.DATABASES.name),
    val pinnedCards: List<String> = emptyList(),
    val pinnedItems: List<String> = emptyList(),
    val recommendCards: Boolean = true,
    val recommendItems: Boolean = true,
    val scope: String = "local",
) {
    fun normalized(): VaultOverviewConfig = copy(
        order = (order + VaultOverviewModule.defaultOrder).distinct()
            .filter { it in VaultOverviewModule.defaultOrder },
        hidden = hidden.intersect(VaultOverviewModule.defaultOrder.toSet()),
        collapsed = collapsed.intersect(VaultOverviewModule.defaultOrder.toSet()),
        pinnedCards = pinnedCards.filter(String::isNotBlank).distinct().take(VAULT_OVERVIEW_MAX_PINS),
        pinnedItems = pinnedItems.filter(String::isNotBlank).distinct().take(VAULT_OVERVIEW_MAX_PINS),
        scope = scope.takeIf {
            it == "all" || it == "local" || (DATABASE_SCOPE.matches(it) && it.substringAfter(':').toLongOrNull() != null)
        } ?: "local",
    )

    fun encode(): String = json.encodeToString(normalized())

    companion object {
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        private val DATABASE_SCOPE = Regex("(keepass|bitwarden|mdbx):[1-9][0-9]*")

        fun decode(value: String?): VaultOverviewConfig =
            value?.let { runCatching { json.decodeFromString<VaultOverviewConfig>(it).normalized() }.getOrNull() }
                ?: VaultOverviewConfig()
    }
}
