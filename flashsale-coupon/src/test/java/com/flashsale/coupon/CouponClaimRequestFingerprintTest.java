package com.flashsale.coupon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CouponClaimRequestFingerprintTest {
    @Test void normalizedRequestUsesStableSha256() {
        String fingerprint = CouponClaimRequestFingerprint.sha256(7, 11);
        assertEquals(64, fingerprint.length());
        assertEquals(fingerprint, CouponClaimRequestFingerprint.sha256(7, 11));
        assertNotEquals(fingerprint, CouponClaimRequestFingerprint.sha256(8, 11));
        assertNotEquals(fingerprint, CouponClaimRequestFingerprint.sha256(7, 12));
    }
}
