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

internal val TabItems = Tabs.map { it.item }

/**
 * The round accessory beside the bar: search, which opens over the current tab. New message lives
 * in the inbox's top bar (it's rare in India, so it stays modest).
 */
internal val SearchItem = BrNavItem(R.drawable.ic_search, "Search")
