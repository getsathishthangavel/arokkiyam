package com.arokkiyam.core.util;

import java.util.UUID;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA {@link AttributeConverter} that transparently converts between
 * {@link UUID} (Java) and {@code byte[]} ({@code BINARY(16)} in MySQL).
 *
 * <p>Annotated with {@code autoApply = true} — every JPA entity field
 * declared as {@code UUID} type automatically uses this converter
 * without needing {@code @Convert} on each field.
 *
 * <p>This is the critical bridge that makes UUID v7's performance
 * benefits work in practice:
 * <ul>
 *   <li>Java layer: uses readable {@code UUID} objects</li>
 *   <li>JDBC layer: sends 16 raw bytes — no string parsing overhead</li>
 *   <li>MySQL layer: stores 16 bytes in {@code BINARY(16)} — clustered
 *       index stays sequential, no B-tree fragmentation</li>
 * </ul>
 */
@Converter(autoApply = true)
public class UuidV7AttributeConverter
        implements AttributeConverter<UUID, byte[]> {

    /**
     * Called before INSERT/UPDATE — converts UUID to 16 bytes.
     *
     * @param uuid the Java UUID; null is allowed (nullable FK columns)
     * @return 16-byte big-endian representation, or null
     */
    @Override
    public byte[] convertToDatabaseColumn(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return UuidV7Util.toBytes(uuid);
    }

    /**
     * Called after SELECT — converts 16 bytes back to UUID.
     *
     * @param bytes the raw bytes from MySQL; null for nullable columns
     * @return the reconstructed UUID, or null
     */
    @Override
    public UUID convertToEntityAttribute(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return UuidV7Util.fromBytes(bytes);
    }
}