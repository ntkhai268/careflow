package com.careflow.queue.dto;

import com.careflow.queue.domain.*;

import java.time.LocalDate;
import java.util.UUID;

public record QueueConfigResponse(UUID id, UUID departmentId, String departmentName, String queuePrefix,
                                  String roomCode, int priorityRatioN, int normalRatioM,
                                  int avgConsultationMinutes, int nearTurnThreshold,
                                  MissedPolicy missedPolicy, LocalDate schedulerDate,
                                  CyclePhase cyclePhase, int servedInPhase, NormalCursor normalCursor,
                                  boolean active) {
    public static QueueConfigResponse from(QueueConfig config) {
        return new QueueConfigResponse(config.getId(), config.getDepartmentId(), config.getDepartmentNameSnapshot(),
                config.getQueuePrefix(), config.getRoomCode(), config.getPriorityRatioN(), config.getNormalRatioM(),
                config.getAvgConsultationMinutes(), config.getNearTurnThreshold(), config.getMissedPolicy(),
                config.getSchedulerDate(), config.getCyclePhase(), config.getServedInPhase(),
                config.getNormalCursor(), config.isActive());
    }
}
