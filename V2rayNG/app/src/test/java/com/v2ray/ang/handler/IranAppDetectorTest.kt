package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IranAppDetectorTest {

    @Test
    fun `Blu bank is matched by package even with an English name`() {
        assertTrue(IranAppDetector.isIranian("com.samanpr.blu", "Blu"))
        assertTrue(IranAppDetector.isIranian("com.samanpr.blu", null))
    }

    @Test
    fun `Iranian package patterns are matched`() {
        assertTrue(IranAppDetector.isIranian("ir.divar", null))
        assertTrue(IranAppDetector.isIranian("com.example.ir", null))
        assertTrue(IranAppDetector.isIranian("com.bank.ir.mobile", null))
        assertTrue(IranAppDetector.isIranian("cab.snapp.passenger", null))
    }

    @Test
    fun `Persian names without Persian-only letters are matched`() {
        assertTrue(IranAppDetector.isIranian("com.example.bank", "بلو"))
        assertTrue(IranAppDetector.isIranian("com.example.mellat", "همراه بانک ملت"))
        assertTrue(IranAppDetector.isIranian("com.example.app", "اپ پرداخت"))
    }

    @Test
    fun `Foreign apps with Persian names stay on the VPN`() {
        assertFalse(IranAppDetector.isIranian("org.telegram.messenger", "تلگرام"))
        assertFalse(IranAppDetector.isIranian("com.instagram.android", "اینستاگرام"))
    }

    @Test
    fun `Other apps are not matched`() {
        assertFalse(IranAppDetector.isIranian("com.example.app", "Example"))
        assertFalse(IranAppDetector.isIranian("com.example.app", ""))
        assertFalse(IranAppDetector.isIranian("com.example.app", null))
    }
}
