package takagi.ru.monica.ui.cardwallet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.data.WalletStack

@Composable
internal fun rememberWalletStackEntries(
    cards: List<WalletListItem>,
    stacks: List<WalletStack>,
    showIndividualCards: Boolean,
    selectionMode: Boolean = false
): State<List<WalletStackListEntry>> = produceState<List<WalletStackListEntry>>(
    initialValue = emptyList(),
    System.identityHashCode(cards), stacks, showIndividualCards, selectionMode
) {
    value = withContext(Dispatchers.Default) {
        projectWalletStacks(cards, stacks, showIndividualCards, selectionMode)
    }
}

internal sealed interface WalletStackListEntry {
    val key: String

    data class Single(
        val card: WalletListItem,
        val selectionStackId: String? = null,
        val isLastInSelectionStack: Boolean = false
    ) : WalletStackListEntry {
        override val key: String = "card:${card.id}"
    }

    data class Stack(val stack: WalletStack, val cards: List<WalletListItem>) : WalletStackListEntry {
        override val key: String = "stack:${stack.id}"
        val cover: WalletListItem get() = cards.firstOrNull { it.id == stack.coverId } ?: cards.first()
    }

    data class SelectionHeader(
        val stack: WalletStack?,
        val cards: List<WalletListItem>
    ) : WalletStackListEntry {
        // Preserve the collapsed stack's scroll anchor when entering selection mode.
        override val key: String = stack?.let { "stack:${it.id}" } ?: "selection:unstacked"
        val cover: WalletListItem? get() = cards.firstOrNull { it.id == stack?.coverId } ?: cards.firstOrNull()
    }
}

/** Pin groups ahead of the sorted singles; filters never rewrite persisted membership. */
internal fun projectWalletStacks(
    visibleCards: List<WalletListItem>,
    stacks: List<WalletStack>,
    showIndividualCards: Boolean = false,
    selectionMode: Boolean = false
): List<WalletStackListEntry> {
    if (stacks.isEmpty() || showIndividualCards && !selectionMode) {
        return visibleCards.map { WalletStackListEntry.Single(it) }
    }
    val visibleById = visibleCards.associateBy(WalletListItem::id)
    val minimumVisibleMembers = if (selectionMode) 1 else 2
    val groupsByMember = buildMap<Long, WalletStackListEntry.Stack> {
        stacks.forEach { stack ->
            val members = stack.memberIds.distinct().mapNotNull(visibleById::get)
            if (members.size >= minimumVisibleMembers) {
                val entry = WalletStackListEntry.Stack(stack, members)
                members.forEach { put(it.id, entry) }
            }
        }
    }
    val emittedGroups = mutableSetOf<String>()
    val singles = ArrayList<WalletStackListEntry.Single>()
    val groupedEntries = buildList<WalletStackListEntry> {
        visibleCards.forEach { card ->
            val group = groupsByMember[card.id]
            if (group == null) singles.add(WalletStackListEntry.Single(card))
            else if (emittedGroups.add(group.stack.id)) add(group)
        }
        addAll(singles)
    }
    if (!selectionMode || emittedGroups.isEmpty()) return groupedEntries

    return buildList {
        groupedEntries.filterIsInstance<WalletStackListEntry.Stack>().forEach { entry ->
            add(WalletStackListEntry.SelectionHeader(entry.stack, entry.cards))
            entry.cards.forEachIndexed { index, card ->
                add(WalletStackListEntry.Single(card, entry.stack.id, index == entry.cards.lastIndex))
            }
        }
        if (singles.isNotEmpty()) {
            add(WalletStackListEntry.SelectionHeader(null, singles.map { it.card }))
            addAll(singles)
        }
    }
}

/** A group action only affects cards visible in the current search and category. */
internal fun toggleWalletSelectionGroup(
    selectedIds: Set<Long>,
    group: WalletStackListEntry.SelectionHeader
): Set<Long> {
    val groupIds = group.cards.mapTo(mutableSetOf(), WalletListItem::id)
    return if (selectedIds.containsAll(groupIds)) selectedIds - groupIds else selectedIds + groupIds
}

/** Section headers and stack members are not destinations for single-card reordering. */
internal fun reorderWalletSingleCards(
    cards: List<WalletListItem>,
    from: WalletStackListEntry?,
    to: WalletStackListEntry?
): List<WalletListItem> {
    if (from !is WalletStackListEntry.Single || to !is WalletStackListEntry.Single ||
        from.selectionStackId != null || to.selectionStackId != null || from.card.id == to.card.id
    ) return cards
    val fromIndex = cards.indexOfFirst { it.id == from.card.id }
    val toIndex = cards.indexOfFirst { it.id == to.card.id }
    if (fromIndex < 0 || toIndex < 0) return cards
    return cards.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
}
