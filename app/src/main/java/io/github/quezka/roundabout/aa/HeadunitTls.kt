package io.github.quezka.roundabout.aa

import android.content.Context
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

// mutual-TLS client identity for AA ; head unit presents the public GAL
// "JVC Kenwood" cert so the phone trusts us. we do not verify the phone cert.
object HeadunitTls {
    private const val CERT = "aa/headunit.crt"
    private const val KEY = "aa/headunit.key"

    fun context(ctx: Context): SSLContext {
        val cert = ctx.assets.open(CERT).use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }
        val key = loadKey(ctx)
        val ks = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry("hu", key, CharArray(0), arrayOf(cert))
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, CharArray(0))
        return SSLContext.getInstance("TLSv1.2").apply {
            init(kmf.keyManagers, arrayOf<TrustManager>(AcceptAll), SecureRandom())
        }
    }

    private fun loadKey(ctx: Context): PrivateKey {
        val pem = ctx.assets.open(KEY).bufferedReader().readText()
        val b64 = pem.replace(Regex("-----[^-]+-----"), "").replace(Regex("\\s"), "")
        val der = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        val pkcs8 = if (pem.contains("BEGIN RSA PRIVATE KEY")) Pkcs8.wrapPkcs1(der) else der
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs8))
    }

    private val AcceptAll = object : X509TrustManager {
        override fun checkClientTrusted(c: Array<X509Certificate>, a: String) {}
        override fun checkServerTrusted(c: Array<X509Certificate>, a: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }
}
