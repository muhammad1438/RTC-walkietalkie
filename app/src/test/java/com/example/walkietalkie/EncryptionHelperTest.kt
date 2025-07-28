package com.example.walkietalkie

import org.junit.Assert.assertEquals
import org.junit.Test

class EncryptionHelperTest {

    @Test
    fun testEncryptDecrypt() {
        val key = EncryptionHelper.generateKey("This is a key123")
        val data = "This is a test".toByteArray()
        val encryptedData = EncryptionHelper.encrypt(data, key)
        val decryptedData = EncryptionHelper.decrypt(encryptedData, key)
        assertEquals(String(data), String(decryptedData))
    }
}
