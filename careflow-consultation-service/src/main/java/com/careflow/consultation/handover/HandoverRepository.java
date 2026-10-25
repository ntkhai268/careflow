package com.careflow.consultation.handover;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface HandoverRepository extends JpaRepository<Handover, UUID> {
    @org.springframework.data.jpa.repository.Query("select h.consultationId from Handover h where h.id = :id")
    Optional<UUID> findConsultationId(UUID id);
    List<Handover> findByRoomIdAndStatus(String roomId, Handover.Status status);
    boolean existsByConsultationIdAndStatus(UUID id, Handover.Status status);
    List<Handover> findByConsultationIdOrderByCreatedAtDesc(UUID id);
    List<Handover> findByToDoctorIdOrFromDoctorIdOrInitiatedByOrderByCreatedAtDesc(UUID receiver, UUID sender, UUID initiator);
}
