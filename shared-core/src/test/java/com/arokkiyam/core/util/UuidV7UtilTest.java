package com.arokkiyam.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UuidV7Util}.
 *
 * <p>Tagged {@code unit} — runs without Docker or Spring context.
 * Completes in milliseconds.
 */
@Tag("unit")
@DisplayName("UuidV7Util")
class UuidV7UtilTest {

    // ── Generation ────────────────────────────────────────────

    @Nested
    @DisplayName("generate()")
    class Generate {

        @Test
        @DisplayName("returns a non-null UUID")
        void returnsNonNull() {
            UUID uuid = UuidV7Util.generate();
            assertThat(uuid).isNotNull();
        }

        @Test
        @DisplayName("returns UUID version 7")
        void isVersion7() {
            UUID uuid = UuidV7Util.generate();
            assertThat(uuid.version()).isEqualTo(7);
        }

        @Test
        @DisplayName("returns RFC 4122 variant (variant == 2)")
        void hasCorrectVariant() {
            UUID uuid = UuidV7Util.generate();
            assertThat(uuid.variant()).isEqualTo(2);
        }

        @RepeatedTest(100)
        @DisplayName("generates globally unique values across 100 calls")
        void isUnique() {
            Set<UUID> generated = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                generated.add(UuidV7Util.generate());
            }
            assertThat(generated).hasSize(1000);
        }

        @Test
        @DisplayName("generates UUIDs that sort in creation order")
        void isSortedByCreationTime() throws InterruptedException {
            List<UUID> uuids = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                uuids.add(UuidV7Util.generate());
                Thread.sleep(1); // ensure different millisecond timestamps
            }

            // Each UUID should be greater than the previous
            for (int i = 1; i < uuids.size(); i++) {
                UUID prev = uuids.get(i - 1);
                UUID curr = uuids.get(i);
                assertThat(curr.getMostSignificantBits())
                    .as("UUID %d MSB should be greater than UUID %d MSB", i, i - 1)
                    .isGreaterThan(prev.getMostSignificantBits());
            }
        }

        @Test
        @DisplayName("embedded timestamp is close to current time")
        void embeddedTimestampIsAccurate() {
            long beforeMs = Instant.now().toEpochMilli();
            UUID uuid = UuidV7Util.generate();
            long afterMs = Instant.now().toEpochMilli();

            long embeddedMs = UuidV7Util.extractTimestampMs(uuid);

            assertThat(embeddedMs)
                .isGreaterThanOrEqualTo(beforeMs)
                .isLessThanOrEqualTo(afterMs + 5); // 5ms tolerance
        }
    }

    // ── Byte conversion ───────────────────────────────────────

    @Nested
    @DisplayName("toBytes() and fromBytes()")
    class ByteConversion {

        @Test
        @DisplayName("round-trips UUID through byte array without data loss")
        void roundTrip() {
            UUID original = UuidV7Util.generate();
            byte[] bytes = UuidV7Util.toBytes(original);
            UUID restored = UuidV7Util.fromBytes(bytes);

            assertThat(restored).isEqualTo(original);
        }

        @Test
        @DisplayName("byte array is exactly 16 bytes")
        void produces16Bytes() {
            byte[] bytes = UuidV7Util.toBytes(UuidV7Util.generate());
            assertThat(bytes).hasSize(16);
        }

        @Test
        @DisplayName("toBytes() throws on null input")
        void toBytesRejectsNull() {
            assertThatThrownBy(() -> UuidV7Util.toBytes(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UUID must not be null");
        }

        @Test
        @DisplayName("fromBytes() throws on null input")
        void fromBytesRejectsNull() {
            assertThatThrownBy(() -> UuidV7Util.fromBytes(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
        }

        @Test
        @DisplayName("fromBytes() throws when byte array is not 16 bytes")
        void fromBytesRejectsWrongLength() {
            assertThatThrownBy(() -> UuidV7Util.fromBytes(new byte[8]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("16 bytes");
        }

        @Test
        @DisplayName("byte array preserves sort order — critical for BINARY(16) index")
        void preservesSortOrder() throws InterruptedException {
            UUID first  = UuidV7Util.generate();
            Thread.sleep(2);
            UUID second = UuidV7Util.generate();

            byte[] firstBytes  = UuidV7Util.toBytes(first);
            byte[] secondBytes = UuidV7Util.toBytes(second);

            // Compare byte arrays lexicographically — second must be > first
            // This is the essential property for sequential MySQL index insertion
            int comparison = 0;
            for (int i = 0; i < 16; i++) {
                comparison = Byte.compareUnsigned(secondBytes[i], firstBytes[i]);
                if (comparison != 0) break;
            }
            assertThat(comparison)
                .as("Second UUID byte array must be greater than first")
                .isGreaterThan(0);
        }
    }

    // ── String conversion ─────────────────────────────────────

    @Nested
    @DisplayName("toString() and fromString()")
    class StringConversion {

        @Test
        @DisplayName("round-trips UUID through string representation")
        void roundTrip() {
            UUID original = UuidV7Util.generate();
            String str    = UuidV7Util.toString(original);
            UUID restored = UuidV7Util.fromString(str);

            assertThat(restored).isEqualTo(original);
        }

        @Test
        @DisplayName("string format matches UUID canonical pattern")
        void hasCanonicalFormat() {
            String str = UuidV7Util.toString(UuidV7Util.generate());
            assertThat(str).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
            );
        }

        @Test
        @DisplayName("toString() throws on null input")
        void toStringRejectsNull() {
            assertThatThrownBy(() -> UuidV7Util.toString(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("fromString() throws on null input")
        void fromStringRejectsNull() {
            assertThatThrownBy(() -> UuidV7Util.fromString(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("fromString() throws on blank input")
        void fromStringRejectsBlank() {
            assertThatThrownBy(() -> UuidV7Util.fromString("   "))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("fromString() throws on invalid UUID format")
        void fromStringRejectsInvalidFormat() {
            assertThatThrownBy(() -> UuidV7Util.fromString("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid UUID format");
        }
    }

    // ── Validation ────────────────────────────────────────────

    @Nested
    @DisplayName("isVersion7()")
    class Validation {

        @Test
        @DisplayName("returns true for generated UUID v7")
        void trueForV7() {
            assertThat(UuidV7Util.isVersion7(UuidV7Util.generate())).isTrue();
        }

        @Test
        @DisplayName("returns false for UUID v4")
        void falseForV4() {
            assertThat(UuidV7Util.isVersion7(UUID.randomUUID())).isFalse();
        }

        @Test
        @DisplayName("returns false for null")
        void falseForNull() {
            assertThat(UuidV7Util.isVersion7(null)).isFalse();
        }
    }

    // ── Timestamp extraction ──────────────────────────────────

    @Nested
    @DisplayName("extractTimestampMs() and extractInstant()")
    class TimestampExtraction {

        @Test
        @DisplayName("extractInstant() returns correct approximate time")
        void extractInstantIsAccurate() {
            Instant before = Instant.now();
            UUID uuid = UuidV7Util.generate();
            Instant after = Instant.now();

            Instant extracted = UuidV7Util.extractInstant(uuid);

            assertThat(extracted)
                .isAfterOrEqualTo(before.minusMillis(5))
                .isBeforeOrEqualTo(after.plusMillis(5));
        }

        @Test
        @DisplayName("extractTimestampMs() throws on null")
        void throwsOnNull() {
            assertThatThrownBy(() -> UuidV7Util.extractTimestampMs(null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── generateAsBytes() ─────────────────────────────────────

    @Test
    @DisplayName("generateAsBytes() returns 16 bytes representing a valid UUID v7")
    void generateAsBytes() {
        byte[] bytes = UuidV7Util.generateAsBytes();
        assertThat(bytes).hasSize(16);

        UUID uuid = UuidV7Util.fromBytes(bytes);
        assertThat(UuidV7Util.isVersion7(uuid)).isTrue();
    }
}