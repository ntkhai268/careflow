package com.careflow.lab.repository;

import com.careflow.lab.model.LabOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LabOrderItemRepository extends JpaRepository<LabOrderItem, UUID> {
}
