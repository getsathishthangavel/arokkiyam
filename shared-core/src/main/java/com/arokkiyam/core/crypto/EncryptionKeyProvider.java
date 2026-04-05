package com.arokkiyam.core.crypto;

import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.util.Assert;

import lombok.extern.slf4j.Slf4j;

/**
 * Provides the AES-256 master encryption key decoded from the
 * base64-encoded environment variable ENCRYPTION_MASTER_KEY.
 *
 * <p>Full implementation: Phase 0 Step 0.4.
 * This stub decodes the key and validates length.
 * Key rotation support added in Step 0.4.
 */
@Slf4j
public class EncryptionKeyProvider {

    private final SecretKey masterKey;

    public EncryptionKeyProvider(String base64MasterKey) {
        Assert.hasText(base64MasterKey,
            "ENCRYPTION_MASTER_KEY must not be blank. " +
            "Set environment variable before starting the service.");

        byte[] keyBytes = Base64.getDecoder().decode(base64MasterKey);

        Assert.isTrue(keyBytes.length == 32,
            "ENCRYPTION_MASTER_KEY must decode to exactly 32 bytes (256 bits) " +
            "for AES-256. Got: " + keyBytes.length + " bytes.");

        this.masterKey = new SecretKeySpec(keyBytes, "AES");
        log.info("EncryptionKeyProvider initialised with AES-256 master key");
    }

    public SecretKey getMasterKey() {
        return masterKey;
    }
}