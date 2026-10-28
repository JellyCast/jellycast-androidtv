package nl.jellycast.mobile.core.remote.androidtv

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One TLS connection to an Android TV with this phone's certificate, carrying length-prefixed
 * protobuf messages. The TV's own certificate is self-signed, so any is accepted and kept:
 * pairing hashes it.
 */
class TlsChannel private constructor(
    private val plain: Socket,
    private val socket: SSLSocket,
    private val io: CoroutineDispatcher,
    val serverCertificate: X509Certificate?,
) {
    private val input: InputStream = socket.inputStream
    private val output: OutputStream = socket.outputStream
    private val sendLock = Mutex()

    suspend fun send(message: ProtoWriter) = sendLock.withLock {
        withContext(io) {
            output.write(message.framed())
            output.flush()
        }
    }

    suspend fun receive(): ProtoMessage = withContext(io) { input.readFramedMessage() }

    /** Closes only the socket underneath: no TLS goodbye to write, so it never blocks. */
    fun close() {
        try {
            plain.close()
        } catch (_: IOException) {
            // Already closed.
        }
    }

    companion object {
        /**
         * Runs [block] on an open channel. Cancelling closes the socket, which ends a read that
         * is waiting for the TV.
         */
        suspend fun <T> connect(
            host: String,
            port: Int,
            identity: ClientIdentity,
            io: CoroutineDispatcher,
            block: suspend (TlsChannel) -> T,
        ): T {
            val channel = open(host, port, identity, io)
            try {
                return coroutineScope {
                    val closer =
                        launch(io, start = CoroutineStart.UNDISPATCHED) {
                            try {
                                awaitCancellation()
                            } finally {
                                channel.close()
                            }
                        }
                    try {
                        block(channel)
                    } finally {
                        closer.cancel()
                    }
                }
            } finally {
                withContext(NonCancellable + io) { channel.close() }
            }
        }

        private suspend fun open(
            host: String,
            port: Int,
            identity: ClientIdentity,
            io: CoroutineDispatcher,
        ): TlsChannel = withContext(io) {
            val trust = RecordingTrustManager()
            val context = SSLContext.getInstance("TLS")
            context.init(arrayOf(ClientKeyManager(identity)), arrayOf(trust), null)
            val plain = Socket()
            try {
                plain.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                plain.soTimeout = CONNECT_TIMEOUT_MS
                val socket =
                    context.socketFactory.createSocket(plain, host, port, true) as SSLSocket
                socket.startHandshake()
                // The TV may stay quiet for a long time (pairing waits for the code).
                plain.soTimeout = 0
                TlsChannel(plain, socket, io, trust.serverCertificate)
            } catch (e: IOException) {
                plain.close()
                throw e
            }
        }

        private const val CONNECT_TIMEOUT_MS = 3_000
    }
}

private class RecordingTrustManager : X509TrustManager {
    @Volatile
    var serverCertificate: X509Certificate? = null
        private set

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        serverCertificate = chain?.firstOrNull() ?: throw CertificateException("No certificate")
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        throw CertificateException("This side is the client")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

private class ClientKeyManager(private val identity: ClientIdentity) : X509ExtendedKeyManager() {
    override fun chooseClientAlias(
        keyType: Array<out String>?,
        issuers: Array<out Principal>?,
        socket: Socket?,
    ): String = ALIAS

    override fun chooseEngineClientAlias(
        keyType: Array<out String>?,
        issuers: Array<out Principal>?,
        engine: SSLEngine?,
    ): String = ALIAS

    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) =
        arrayOf(ALIAS)

    override fun getCertificateChain(alias: String?): Array<X509Certificate> =
        arrayOf(identity.certificate)

    override fun getPrivateKey(alias: String?): PrivateKey = identity.privateKey

    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?) = null

    override fun chooseServerAlias(
        keyType: String?,
        issuers: Array<out Principal>?,
        socket: Socket?,
    ): String? = null

    private companion object {
        const val ALIAS = "jellycast"
    }
}
