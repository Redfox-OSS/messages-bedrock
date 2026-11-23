package dev.redfox.messages.navigation

import androidx.navigation3.runtime.NavKey
import dev.redfox.bedrock.navigation.BrNavItem
import dev.redfox.messages.core.ui.R
import dev.redfox.messages.feature.conversations.ConversationsKey
import dev.redfox.messages.saved.SavedKey

/** A top-level destination in the nav bar. */
internal data class Tab(val key: NavKey, val item: BrNavItem)

/** The bar's tabs, in order: messages first, then everything kept for later. */
internal val Tabs = listOf(
    Tab(ConversationsKey, BrNavItem(R.drawable.ic_chat, "Messages")),
    Tab(SavedKey, BrNavItem(R.drawable.ic_bookmark, "Saved")),
)

/**
 * The bar's last slot, after the tabs: New message. It's an action, not a tab: tapping it opens
 * the composer and the pill stays on the current tab.
 */
internal val NewMessageItem = BrNavItem(R.drawable.ic_edit_square, "New message")

/** What the bar shows: the tabs, then New message. */
internal val BarItems = Tabs.map { it.item } + NewMessageItem

/** The round accessory beside the bar: search, which opens over the current tab. */
internal val SearchItem = BrNavItem(R.drawable.ic_search, "Search")
