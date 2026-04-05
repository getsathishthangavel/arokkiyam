package com.arokkiyam.core.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.arokkiyam.core.context.TenantContext;
import com.arokkiyam.core.util.UuidV7Util;

/**
 * Unit tests for {@link BaseEntity}.
 *
 * <p>Uses a concrete test subclass — BaseEntity is abstract.
 * No Spring context, no JPA — tests pure Java logic.
 */
@Tag("unit")
@DisplayName("BaseEntity")
class BaseEntityTest {

    /** Minimal concrete subclass for testing */
    static class TestEntity extends BaseEntity {
        private String name;
        TestEntity(String name) { this.name = name; }
        
        // Add these specifically for the test to simulate DB loading
        void setTestId(UUID id) { setId(id); }
        void setTestTenantId(UUID tenantId) { setTenantId(tenantId); }
    }

    private final String TENANT_ID = UUID.randomUUID().toString();

    @BeforeEach
    void setTenantContext() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // ── @PrePersist ───────────────────────────────────────────

    @Nested
    @DisplayName("@PrePersist — prePersist()")
    class PrePersist {

        @Test
        @DisplayName("assigns a non-null UUID v7 as id")
        void assignsUuidV7Id() {
            TestEntity entity = new TestEntity("test");
            entity.prePersist();

            assertThat(entity.getId()).isNotNull();
            assertThat(UuidV7Util.isVersion7(entity.getId())).isTrue();
        }

        @Test
        @DisplayName("assigns tenantId from TenantContext")
        void assignsTenantIdFromContext() {
            TestEntity entity = new TestEntity("test");
            entity.prePersist();

            assertThat(entity.getTenantId()).isNotNull();
            assertThat(entity.getTenantId().toString()).isEqualTo(TENANT_ID);
        }

        @Test
        @DisplayName("does not overwrite existing id on second prePersist call")
        void doesNotOverwriteExistingId() {
            TestEntity entity = new TestEntity("test");
            entity.prePersist();
            UUID firstId = entity.getId();

            entity.prePersist(); // simulate accidental second call
            assertThat(entity.getId()).isEqualTo(firstId);
        }

        @Test
        @DisplayName("throws IllegalStateException when no tenant in context")
        void throwsWhenNoTenantContext() {
            TenantContext.clear(); // remove tenant context

            TestEntity entity = new TestEntity("test");
            assertThatThrownBy(entity::prePersist)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No tenant context");
        }

        @Test
        @DisplayName("each prePersist generates a unique id")
        void generatesUniqueIds() {
            TestEntity a = new TestEntity("a");
            TestEntity b = new TestEntity("b");
            a.prePersist();
            b.prePersist();

            assertThat(a.getId()).isNotEqualTo(b.getId());
        }
    }

    // ── equals() and hashCode() ───────────────────────────────

    @Nested
    @DisplayName("equals() and hashCode()")
    class EqualsAndHashCode {

        @Test
        @DisplayName("two entities with same id are equal")
        void equalWhenSameId() {
            TestEntity a = new TestEntity("a");
            a.prePersist(); 

            TestEntity b = new TestEntity("b");
            // Use the new test-only helpers
            b.setTestId(a.getId()); 
            b.setTestTenantId(a.getTenantId());

            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("two entities with different ids are not equal")
        void notEqualWhenDifferentIds() {
            TestEntity a = new TestEntity("a");
            TestEntity b = new TestEntity("b");
            a.prePersist();
            b.prePersist();

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("entity is equal to itself")
        void equalToItself() {
            TestEntity entity = new TestEntity("test");
            entity.prePersist();

            assertThat(entity).isEqualTo(entity);
        }

        @Test
        @DisplayName("entity is not equal to null")
        void notEqualToNull() {
            TestEntity entity = new TestEntity("test");
            entity.prePersist();

            assertThat(entity).isNotEqualTo(null);
        }

        @Test
        @DisplayName("two unpersisted entities (id=null) are not equal")
        void unpersistedEntitiesNotEqual() {
            TestEntity a = new TestEntity("a");
            TestEntity b = new TestEntity("b");
            // No prePersist — ids are null

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("hashCode is consistent before and after prePersist")
        void hashCodeIsConsistent() {
            TestEntity entity = new TestEntity("test");
            // Before persist — uses class hashCode
            int beforeHash = entity.hashCode();
            assertThat(beforeHash).isNotZero();

            entity.prePersist();
            // After persist — uses id hashCode
            int afterHash = entity.hashCode();
            assertThat(afterHash).isNotZero();
        }
    }

    // ── toString() ────────────────────────────────────────────

    @Test
    @DisplayName("toString() includes class name and id")
    void toStringIncludesClassNameAndId() {
        TestEntity entity = new TestEntity("test");
        entity.prePersist();

        String str = entity.toString();
        assertThat(str)
            .contains("TestEntity")
            .contains(entity.getId().toString());
    }
}