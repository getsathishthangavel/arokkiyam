package com.arokkiyam.core.crypto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AES-256-GCM field-level encryption engine.
 *
 * <p>Full implementation: Phase 0 Step 0.4.
 * This stub defines the encrypt/decrypt interface so that
 * EncryptedConverter and other classes can reference it
 * before the full implementation is complete.
 *
 * <p>Wire format stored in DB: {@code GCM:<base64-iv>:<base64-tag>:<base64-ciphertext>}
 */
@Slf4j
@RequiredArgsConstructor
public class FieldEncryptor {

    private final EncryptionKeyProvider keyProvider;

    /**
     * Encrypts a plaintext string using AES-256-GCM.
     *
     * @param plaintext the value to encrypt; null returns null
     * @return GCM wire format string, or null if input is null
     */
    public String encrypt(String plaintext) {
        // TODO: implement in Phase 0 Step 0.4
        throw new UnsupportedOperationException(
            "FieldEncryptor.encrypt() not yet implemented. " +
            "Implementation scheduled for Phase 0 Step 0.4."
        );
    }

    /**
     * Decrypts a GCM wire format string back to plaintext.
     *
     * @param ciphertext the GCM wire format string; null returns null
     * @return decrypted plaintext, or null if input is null
     */
    public String decrypt(String ciphertext) {
        // TODO: implement in Phase 0 Step 0.4
        throw new UnsupportedOperationException(
            "FieldEncryptor.decrypt() not yet implemented. " +
            "Implementation scheduled for Phase 0 Step 0.4."
        );
    }
}