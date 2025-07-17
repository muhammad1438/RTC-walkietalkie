package com.example.walkietalkie

import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object EncryptionHelper {

    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES"

    fun encrypt(data: ByteArray, key: Key): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(data)
    }

    fun decrypt(data: ByteArray, key: Key): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key)
        return cipher.doFinal(data)
    }

    fun generateKey(key: String): Key {
        return SecretKeySpec(key.toByteArray(), ALGORITHM)
    }
}
