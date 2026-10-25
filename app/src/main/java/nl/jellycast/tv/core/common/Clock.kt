package nl.jellycast.tv.core.common

fun interface Clock {
    fun nowMs(): Long
}
