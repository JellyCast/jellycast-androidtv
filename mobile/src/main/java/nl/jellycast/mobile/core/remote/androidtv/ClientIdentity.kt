package nl.jellycast.mobile.core.remote.androidtv

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.jellycast.core.common.AppDispatchers

/** This phone's certificate for the Android TV remote protocol: the TV pairs with it once. */
class ClientIdentity(val privateKey: PrivateKey, val certificate: X509Certificate)

fun interface ClientIdentitySource {
    suspend fun load(): ClientIdentity
}

/** Seals the private key at rest; on the phone with a key that never leaves the Keystore. */
interface KeySealer {
    fun seal(plain: ByteArray): ByteArray

    fun open(sealed: ByteArray): ByteArray
}

/**
 * Made once and kept in app-private storage: the certificate as is, the private key sealed. A
 * key that can't be opened any more (the Keystore was reset) means a new identity and pairing
 * again.
 */
class FileClientIdentitySource(
    private val directory: File,
    private val sealer: KeySealer,
    private val dispatchers: AppDispatchers,
) : ClientIdentitySource {
    private val mutex = Mutex()
    private var cached: ClientIdentity? = null

    override suspend fun load(): ClientIdentity = mutex.withLock {
        cached ?: withContext(dispatchers.io) { stored() ?: create() }.also { cached = it }
    }

    private fun stored(): ClientIdentity? {
        val certificateFile = File(directory, CERTIFICATE_FILE)
        val keyFile = File(directory, KEY_FILE)
        if (!certificateFile.exists() || !keyFile.exists()) return null
        return try {
            val certificate = parseCertificate(certificateFile.readBytes())
            val key =
                KeyFactory.getInstance(RSA).generatePrivate(
                    PKCS8EncodedKeySpec(sealer.open(keyFile.readBytes())),
                )
            ClientIdentity(key, certificate)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    private fun create(): ClientIdentity {
        val identity = generateClientIdentity(Date())
        directory.mkdirs()
        File(directory, KEY_FILE).writeBytes(sealer.seal(identity.privateKey.encoded))
        File(directory, CERTIFICATE_FILE).writeBytes(identity.certificate.encoded)
        return identity
    }

    private companion object {
        const val CERTIFICATE_FILE = "androidtv-certificate.der"
        const val KEY_FILE = "androidtv-key.sealed"
    }
}

fun generateClientIdentity(now: Date): ClientIdentity {
    val keys = KeyPairGenerator.getInstance(RSA).apply { initialize(KEY_BITS) }.generateKeyPair()
    val validUntil = Date(now.time + VALIDITY_MS)
    val tbs = Der.tbsCertificate(COMMON_NAME, keys.public.encoded, now, validUntil)
    val signature =
        Signature.getInstance("SHA256withRSA").run {
            initSign(keys.private)
            update(tbs)
            sign()
        }
    return ClientIdentity(keys.private, parseCertificate(Der.certificate(tbs, signature)))
}

private fun parseCertificate(der: ByteArray): X509Certificate =
    CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der))
        as X509Certificate

/** Modulus and exponent as unsigned big-endian bytes, as the pairing secret hashes them. */
fun X509Certificate.rsaComponents(): Pair<ByteArray, ByteArray>? {
    val key = publicKey as? RSAPublicKey ?: return null
    return key.modulus.unsignedBytes() to key.publicExponent.unsignedBytes()
}

private fun BigInteger.unsignedBytes(): ByteArray = toByteArray().dropWhile { it == 0.toByte() }
    .toByteArray()

/** AES-GCM with a key generated inside the Android Keystore. */
class AndroidKeystoreSealer : KeySealer {
    override fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun open(sealed: ByteArray): ByteArray {
        if (sealed.size <= IV_BYTES) throw GeneralSecurityException("Sealed key too short")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES),
        )
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec
                    .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(AES_BITS)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "nl.jellycast.mobile.androidtv"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val AES_BITS = 256
    }
}

private const val RSA = "RSA"
private const val KEY_BITS = 2048
private const val COMMON_NAME = "JellyCast"
private const val VALIDITY_MS = 10L * 365 * 24 * 60 * 60 * 1000
