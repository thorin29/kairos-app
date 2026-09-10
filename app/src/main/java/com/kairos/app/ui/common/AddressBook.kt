package com.kairos.app.ui.common

import com.kairos.app.data.remote.dto.SavedAddressDto

/**
 * Derives a saved place's friendly name for an event location at read time —
 * no name is stored on the event. Matches the location string against the saved
 * book after light normalization (case, punctuation, whitespace), which reliably
 * catches an address that was picked from the book and most that were typed.
 */
object AddressBook {
    private val WS = Regex("\\s+")
    private val PUNCT = Regex("[.,#]")

    private fun norm(s: String): String =
        s.lowercase().replace(PUNCT, " ").replace(WS, " ").trim()

    /** The saved friendly name for [location], or null if nothing matches. */
    fun nameFor(location: String, addresses: List<SavedAddressDto>): String? {
        if (location.isBlank() || addresses.isEmpty()) return null
        val key = norm(location)
        return addresses.firstOrNull { norm(it.address) == key }?.name
    }
}
