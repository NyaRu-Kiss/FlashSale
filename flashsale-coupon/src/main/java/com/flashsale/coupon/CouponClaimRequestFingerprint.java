package com.flashsale.coupon;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Builds the stable request identity used by coupon claim idempotency. */
final class CouponClaimRequestFingerprint {
    private CouponClaimRequestFingerprint() { }

    static String sha256(long userId, long templateId) {
        String normalized = "user_id=" + userId + "&coupon_template_id=" + templateId;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA_256_UNAVAILABLE", impossible);
        }
    }
}
