package com.careflow.queue.service;
import com.careflow.common.exception.BusinessException;
import com.careflow.queue.client.DirectoryClient;
import com.careflow.queue.dto.CheckInRequest;
import com.careflow.queue.repository.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ReceptionCheckInBoundaryTest {
    @Test void staffAssignmentIsCheckedBeforeLookingUpQrOrTicket(){var entries=mock(QueueEntryRepository.class);var directory=mock(DirectoryClient.class);var service=new QueueManagementService(mock(QueueConfigRepository.class),null,null,entries,null,mock(QueueEventService.class),mock(QrTokenService.class),"Asia/Ho_Chi_Minh",directory);assertThatThrownBy(()->service.checkIn(new CheckInRequest(null,"OTHER-001","ROOM-02",null,null),UUID.randomUUID(),"STAFF",null)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(403));verifyNoInteractions(entries);}
    @Test void missingAssignmentAuthorityDoesNotEnableReception(){var entries=mock(QueueEntryRepository.class);var service=new QueueManagementService(mock(QueueConfigRepository.class),null,null,entries,null,mock(QueueEventService.class),mock(QrTokenService.class),"Asia/Ho_Chi_Minh");assertThatThrownBy(()->service.checkIn(new CheckInRequest(null,"OTHER-001","ROOM-02",null,null),UUID.randomUUID(),"STAFF",null)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(503));verifyNoInteractions(entries);}
}
