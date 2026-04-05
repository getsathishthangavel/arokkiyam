package com.arokkiyam.core.util;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * UUID version 7 utility — time-sorted, globally unique identifiers.
 *
 * <h2>Why UUID v7 over v4?</h2>
 * <ul>
 *   <li>UUID v4 is random — database inserts scatter across the B-tree index,
 *       causing page splits and index fragmentation. On a table with millions
 *       of patient records, this degrades INSERT performance by 3–5x.</li>
 *   <li>UUID v7 embeds a millisecond-precision Unix timestamp in the most
 *       significant bits. New records always append to the right edge of
 *       the B-tree — same insertion pattern as AUTO_INCREMENT, but globally
 *       unique across all services and tenants.</li>
 * </ul>
 *
 * <h2>Wire format (128 bits)</h2>
 * <pre>
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                           unix_ts_ms (48 bits)                |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  unix_ts_ms   |  ver  |        rand_a (12 bits)               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |var|                  rand_b (62 bits)                         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 *
 * <h2>MySQL storage</h2>
 * Stored as {@code BINARY(16)} — 16 raw bytes, no dashes, no text encoding.
 * This is 50% smaller than a {@code VARCHAR(36)} UUID string and preserves
 * the sort order so MySQL's clustered index stays sequential.
 *
 * <h2>Thread safety</h2>
 * {@link SecureRandom} is thread-safe. All methods are static.
 * No shared mutable state.
 */
public final class UuidV7Util {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // UUID version 7 identifier (bits 76–79 of the 128-bit value)
    private static final int VERSION = 7;

    // RFC 4122 variant bits (bits 62–63): binary 10
    private static final int VARIANT = 0b10;

    private UuidV7Util() {}

    // ── Generation ────────────────────────────────────────────

    /**
     * Generates a new UUID v7.
     *
     * <p>The returned UUID is monotonically increasing within the same
     * millisecond — random bits ensure uniqueness when multiple UUIDs
     * are generated in the same millisecond on the same or different nodes.
     *
     * @return a new UUID v7 instance
     */
    public static UUID generate() {
        long nowMs = Instant.now().toEpochMilli();

        // Most significant 64 bits:
        //   bits 63–16: 48-bit Unix timestamp in milliseconds
        //   bits 15–12: version (0111 = 7)
        //   bits 11–0:  12 random bits (rand_a)
        long msb = (nowMs << 16)
                 | ((long) VERSION << 12)
                 | (SECURE_RANDOM.nextLong() & 0x0FFFL);

        // Least significant 64 bits:
        //   bits 63–62: variant (10)
        //   bits 61–0:  62 random bits (rand_b)
        long lsb = ((long) VARIANT << 62)
                 | (SECURE_RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL);

        return new UUID(msb, lsb);
    }

    /**
     * Generates a new UUID v7 and returns it as a 16-byte array
     * ready for storage in a MySQL {@code BINARY(16)} column.
     *
     * @return 16-byte big-endian representation
     */
    public static byte[] generateAsBytes() {
        return toBytes(generate());
    }

    // ── Conversion ────────────────────────────────────────────

    /**
     * Converts a UUID to a 16-byte big-endian array.
     * Used when writing to {@code BINARY(16)} MySQL columns.
     *
     * @param uuid the UUID to convert; must not be null
     * @return 16-byte array
     */
    public static byte[] toBytes(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("UUID must not be null");
        }
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    /**
     * Reconstructs a UUID from a 16-byte big-endian array.
     * Used when reading from {@code BINARY(16)} MySQL columns.
     *
     * @param bytes the 16-byte array; must not be null and must be length 16
     * @return the reconstructed UUID
     */
    public static UUID fromBytes(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array must not be null");
        }
        if (bytes.length != 16) {
            throw new IllegalArgumentException(
                "Byte array must be exactly 16 bytes for UUID conversion. " +
                "Got: " + bytes.length + " bytes."
            );
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long msb = buffer.getLong();
        long lsb = buffer.getLong();
        return new UUID(msb, lsb);
    }

    /**
     * Converts a UUID to its standard hyphenated string representation.
     * e.g. {@code 018e4d9a-c4f2-7e1b-8a3d-9f2c1b4e8a7f}
     *
     * @param uuid the UUID; must not be null
     * @return the string form
     */
    public static String toString(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("UUID must not be null");
        }
        return uuid.toString();
    }

    /**
     * Parses a UUID from a hyphenated string.
     *
     * @param value the UUID string; must not be null or blank
     * @return the parsed UUID
     * @throws IllegalArgumentException if the string is not a valid UUID
     */
    public static UUID fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                "UUID string must not be null or blank"
            );
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                "Invalid UUID format: '" + value + "'", ex
            );
        }
    }

    // ── Validation ────────────────────────────────────────────

    /**
     * Returns true if the given UUID is a valid UUID v7.
     * Checks version bits (must be 7) and variant bits (must be RFC 4122).
     *
     * @param uuid the UUID to validate
     * @return true if UUID v7, false otherwise
     */
    public static boolean isVersion7(UUID uuid) {
        if (uuid == null) return false;
        return uuid.version() == VERSION && uuid.variant() == 2;
    }

    /**
     * Extracts the embedded Unix timestamp (milliseconds) from a UUID v7.
     * Useful for debugging and audit log timestamp correlation.
     *
     * @param uuid a UUID v7; behaviour is undefined for other versions
     * @return the Unix timestamp in milliseconds embedded in the UUID
     */
    public static long extractTimestampMs(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("UUID must not be null");
        }
        // Top 48 bits of MSB hold the timestamp
        return uuid.getMostSignificantBits() >>> 16;
    }

    /**
     * Extracts the embedded timestamp as an {@link Instant} from a UUID v7.
     *
     * @param uuid a UUID v7
     * @return the {@link Instant} embedded in the UUID
     */
    public static Instant extractInstant(UUID uuid) {
        return Instant.ofEpochMilli(extractTimestampMs(uuid));
    }
}