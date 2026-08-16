package com.careflow.lab.service;

import com.careflow.common.event.EventEnvelope;

public interface LabEventPublisher {
    void publishReadyForExecution(EventEnvelope envelope);
    void publishAllRequiredResultsAvailable(EventEnvelope envelope);
}
