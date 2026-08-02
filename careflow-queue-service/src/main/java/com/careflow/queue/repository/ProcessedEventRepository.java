package com.careflow.queue.repository;

import com.careflow.queue.domain.ProcessedEvent;
import com.careflow.queue.domain.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {
}
