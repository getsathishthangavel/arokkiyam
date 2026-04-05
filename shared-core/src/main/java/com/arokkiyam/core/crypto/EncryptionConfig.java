package com.arokkiyam.core.crypto;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.arokkiyam.core.config.SharedCoreProperties;

import lombok.RequiredArgsConstructor;

/**
 * Registers encryption beans from shared-core.
 *
 * <p>Full implementation: Phase 0 Step 0.4.
 * This stub exists so the module compiles and auto-configuration
 * loads correctly before the crypto classes are built.
 */
@Configuration
@RequiredArgsConstructor
public class EncryptionConfig {

    private final SharedCoreProperties properties;

    @Bean
    public EncryptionKeyProvider encryptionKeyProvider() {
        return new EncryptionKeyProvider(
            properties.getEncryption().getMasterKey()
        );
    }

    @Bean
    public FieldEncryptor fieldEncryptor(EncryptionKeyProvider keyProvider) {
        return new FieldEncryptor(keyProvider);
    }
}