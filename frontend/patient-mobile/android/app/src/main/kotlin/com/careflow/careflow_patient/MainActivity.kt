package com.careflow.careflow_patient

import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import android.util.Base64
import android.graphics.BitmapFactory
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MainActivity : FlutterFragmentActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        DocumentReaderBridge.register(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "careflow/device-key").setMethodCallHandler { call, result ->
            try {
                val namespace = call.argument<String>("namespace") ?: error("Missing namespace")
                require(namespace.matches(Regex("[a-f0-9]{64}")))
                val alias = "careflow_dpop_$namespace"
                val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                if (!store.containsAlias(alias)) {
                    fun generate(strongBox: Boolean) {
                        val options = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                            .setDigests(KeyProperties.DIGEST_SHA256).setUserAuthenticationRequired(false)
                        if (Build.VERSION.SDK_INT >= 28) {
                            options.setIsStrongBoxBacked(strongBox)
                            options.setUnlockedDeviceRequired(true)
                        }
                        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply { initialize(options.build()) }.generateKeyPair()
                    }
                    if (Build.VERSION.SDK_INT >= 28) {
                        try { generate(true) } catch (_: android.security.keystore.StrongBoxUnavailableException) { generate(false) }
                    } else generate(false)
                }
                val key = store.getKey(alias, null) as java.security.PrivateKey
                require(key.encoded == null) // AndroidKeyStore must never expose the private key.
                when (call.method) {
                    "publicKey" -> {
                        val publicKey = store.getCertificate(alias).publicKey as ECPublicKey
                        fun coordinate(value: java.math.BigInteger): String {
                            val bytes = value.toByteArray().takeLast(32).toByteArray()
                            val padded = ByteArray(32); System.arraycopy(bytes, 0, padded, 32 - bytes.size, bytes.size)
                            return Base64.encodeToString(padded, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                        }
                        val info = KeyFactory.getInstance(key.algorithm, "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java)
                        val protection = if (Build.VERSION.SDK_INT >= 31 && info.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX) "strongbox"
                            else if (info.isInsideSecureHardware) "trusted_environment" else "software_keystore"
                        result.success(mapOf("jwk" to mapOf("kty" to "EC", "crv" to "P-256", "x" to coordinate(publicKey.w.affineX), "y" to coordinate(publicKey.w.affineY)), "protection" to protection))
                    }
                    "sign" -> {
                        val message = call.argument<ByteArray>("message") ?: error("Missing message")
                        require(message.size <= 8192)
                        val signature = Signature.getInstance("SHA256withECDSA").apply { initSign(key); update(message) }.sign()
                        result.success(signature)
                    }
                    else -> result.notImplemented()
                }
            } catch (_: Exception) {
                result.error("DEVICE_KEY_UNAVAILABLE", "Không thể sử dụng khóa thiết bị. Hãy mở khóa điện thoại và thử lại.", null)
            }
        }
    }
}

// Bundled Latin OCR model; image recognition does not require a cloud API or model download.
object DocumentReaderBridge {
    fun register(engine: FlutterEngine) {
        MethodChannel(engine.dartExecutor.binaryMessenger, "careflow/document-reader").setMethodCallHandler { call, result ->
            if (call.method != "read") { result.notImplemented(); return@setMethodCallHandler }
            try {
                val bytes = call.argument<ByteArray>("bytes") ?: error("Missing image")
                require(bytes.size <= 5 * 1024 * 1024)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 20000000)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("Invalid image")
                val orientation = ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                val rotation = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(InputImage.fromBitmap(bitmap, rotation))
                    .addOnSuccessListener { text -> result.success(mapOf("text" to text.text, "qr" to "")) }
                    .addOnFailureListener { result.error("DOCUMENT_READ_FAILED", "Không thể đọc ảnh giấy tờ trên máy.", null) }
                    .addOnCompleteListener { recognizer.close(); bitmap.recycle() }
            } catch (_: Exception) {
                result.error("DOCUMENT_READ_FAILED", "Không thể đọc ảnh giấy tờ trên máy.", null)
            }
        }
    }
}
