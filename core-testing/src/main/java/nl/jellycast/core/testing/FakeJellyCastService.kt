package nl.jellycast.core.testing

import java.io.IOException
import nl.jellycast.core.network.JellyCastService

class FakeJellyCastService(var placementsJson: String = NO_PLACEMENTS) : JellyCastService {
    val pings = mutableListOf<String>()
    var placementRequests = 0
        private set
    var failNext = false

    override suspend fun ping(installId: String) {
        failIfAsked()
        pings += installId
    }

    override suspend fun placements(): String {
        failIfAsked()
        placementRequests++
        return placementsJson
    }

    private fun failIfAsked() {
        if (failNext) {
            failNext = false
            throw IOException("Service unreachable")
        }
    }

    companion object {
        const val NO_PLACEMENTS = """{"version":1,"country":"NL","home":null,"splash":null}"""
    }
}
