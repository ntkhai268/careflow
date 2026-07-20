package com.careflow.common.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BaseEntityTest {

    @Test
    void transientEntitiesAreNotEqualAndHashCodeRemainsStableAfterPersistence() {
        TestEntity first = new TestEntity();
        TestEntity second = new TestEntity();
        var entities = new HashSet<TestEntity>();
        entities.add(first);
        int originalHashCode = first.hashCode();

        assertThat(first).isNotEqualTo(second);

        first.setId(UUID.randomUUID());

        assertThat(first.hashCode()).isEqualTo(originalHashCode);
        assertThat(entities).contains(first);
    }

    @Test
    void lifecycleCallbacksPopulateAuditTimestamps() {
        TestEntity entity = new TestEntity();

        entity.create();
        Instant createdAt = entity.getCreatedAt();
        entity.update();

        assertThat(createdAt).isNotNull();
        assertThat(entity.getUpdatedAt()).isAfterOrEqualTo(createdAt);
    }

    private static final class TestEntity extends BaseEntity {
        void create() {
            onCreate();
        }

        void update() {
            onUpdate();
        }
    }
}
