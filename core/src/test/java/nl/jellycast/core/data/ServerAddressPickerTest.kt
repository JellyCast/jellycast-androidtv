package nl.jellycast.core.data

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerAddressPickerTest {
    private val lan = "http://192.168.1.4:8096"
    private val vpn = "http://10.8.0.1:8096"

    @Test
    fun `the preferred address wins when both answer`() = runTest {
        val picked = pickAddress(listOf(lan, vpn), GRACE_MS) { address ->
            delay(if (address == lan) 200 else 10)
            true
        }
        assertEquals(lan, picked)
    }

    @Test
    fun `a silent preferred address gets the grace and no more`() = runTest {
        val picked = pickAddress(listOf(lan, vpn), GRACE_MS) { address ->
            if (address == lan) awaitCancellation()
            delay(10)
            true
        }
        assertEquals(vpn, picked)
        assertEquals(10 + GRACE_MS, currentTime)
    }

    @Test
    fun `a failing preferred address gives way at once`() = runTest {
        val picked = pickAddress(listOf(lan, vpn), GRACE_MS) { address ->
            delay(if (address == lan) 5 else 50)
            address == vpn
        }
        assertEquals(vpn, picked)
        assertEquals(50, currentTime)
    }

    @Test
    fun `nothing answering picks nothing`() = runTest {
        assertNull(pickAddress(listOf(lan, vpn), GRACE_MS) { false })
    }

    @Test
    fun `no addresses pick nothing`() = runTest {
        assertNull(pickAddress(emptyList(), GRACE_MS) { true })
    }

    private companion object {
        const val GRACE_MS = 1_500L
    }
}
