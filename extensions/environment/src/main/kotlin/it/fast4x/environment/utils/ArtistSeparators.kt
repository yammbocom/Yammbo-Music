package it.fast4x.environment.utils

import it.fast4x.environment.Environment

private val punctuationSeparators = setOf(",", "&", "•", "·")
private val wordSeparators = setOf("y", "and", "e", "et")

/**
 * YouTube splits a credit line such as "A, B & C" into runs, and the separators come
 * back as runs of their own. Kept as authors they show up as a fake artist:
 * "Trap Capos, & , Noriel".
 */
fun String?.isArtistSeparator(): Boolean {
    if (this == null) return true
    val trimmed = trim()
    if (trimmed.isEmpty() || trimmed in punctuationSeparators) return true
    // Word separators only count when padded, so a real artist called "Y" survives.
    return trimmed != this && trimmed.lowercase() in wordSeparators
}

fun <T : Environment.Info<*>> List<T>.withoutArtistSeparators(): List<T> =
    filterNot { it.name.isArtistSeparator() }
