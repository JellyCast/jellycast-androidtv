package nl.jellycast.core.common

fun interface Clock {
    fun nowMs(): Long
}
