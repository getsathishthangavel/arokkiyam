package com.arokkiyam.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UuidV7AttributeConverter}.
 *
 * <p>Verifies the JPA ↔ MySQL BINARY(16) conversion is lossless
 * and handles null values correctly (required for nullable FK columns).
 */
@Tag("unit")
@DisplayName("UuidV7AttributeConverter")
class UuidV7AttributeConverterTest {

    private UuidV7AttributeConverter converter;

    @BeforeEach
    void setUp() {
        converter = new UuidV7AttributeConverter();
    }

    @Nested
    @DisplayName("convertToDatabaseColumn()")
    class ToDatabaseColumn {

        @Test
        @DisplayName("converts UUID to 16-byte array")
        void convertsToBytes() {
            UUID uuid  = UuidV7Util.generate();
            byte[] result = converter.convertToDatabaseColumn(uuid);

            assertThat(result).isNotNull().hasSize(16);
        }

        @Test
        @DisplayName("returns null for null input (nullable FK columns)")
        void handlesNull() {
            assertThat(converter.convertToDatabaseColumn(null)).isNull();
        }

        @Test
        @DisplayName("produces same bytes as UuidV7Util.toBytes()")
        void matchesUtilOutput() {
            UUID uuid = UuidV7Util.generate();
            assertThat(converter.convertToDatabaseColumn(uuid))
                .isEqualTo(UuidV7Util.toBytes(uuid));
        }
    }

    @Nested
    @DisplayName("convertToEntityAttribute()")
    class ToEntityAttribute {

        @Test
        @DisplayName("converts 16-byte array back to UUID")
        void convertsFromBytes() {
            UUID original = UuidV7Util.generate();
            byte[] bytes  = UuidV7Util.toBytes(original);

            UUID restored = converter.convertToEntityAttribute(bytes);

            assertThat(restored).isEqualTo(original);
        }

        @Test
        @DisplayName("returns null for null input")
        void handlesNull() {
            assertThat(converter.convertToEntityAttribute(null)).isNull();
        }
    }

    @Nested
    @DisplayName("round-trip")
    class RoundTrip {

        @Test
        @DisplayName("UUID survives full DB round-trip without data loss")
        void fullRoundTrip() {
            UUID original = UuidV7Util.generate();
            byte[] dbValue = converter.convertToDatabaseColumn(original);
            UUID restored  = converter.convertToEntityAttribute(dbValue);

            assertThat(restored)
                .isEqualTo(original)
                .satisfies(uuid -> assertThat(UuidV7Util.isVersion7(uuid)).isTrue());
        }

        @Test
        @DisplayName("null survives round-trip (nullable FK)")
        void nullRoundTrip() {
            byte[] dbValue = converter.convertToDatabaseColumn(null);
            UUID restored  = converter.convertToEntityAttribute(dbValue);
            assertThat(restored).isNull();
        }
    }
}