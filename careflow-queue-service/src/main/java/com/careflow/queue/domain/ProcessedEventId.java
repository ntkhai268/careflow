package com.careflow.queue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record ProcessedEventId(@Column(name = "event_id") UUID eventId,
                               @Column(name = "consumer_name", length = 100) String consumerName) implements Serializable {
}
