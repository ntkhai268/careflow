package com.careflow.notification.messaging;
import com.careflow.common.event.EventEnvelope;
import com.careflow.common.security.PatientOwnershipClient;
import com.careflow.notification.config.RealtimeTopology;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import java.util.*;
/** Only an invalidation hint. Clients refetch current state instead of applying event snapshots. */
@Component
public class RealtimeInvalidationConsumer {
    private final PatientOwnershipClient owners;private final SimpMessagingTemplate messaging;
    public RealtimeInvalidationConsumer(PatientOwnershipClient owners,SimpMessagingTemplate messaging){this.owners=owners;this.messaging=messaging;}
    @RabbitListener(queues=RealtimeTopology.QUEUE)
    public void consume(EventEnvelope event){
        if(event==null || event.eventId()==null || event.payload()==null || !event.payload().hasNonNull("patientId"))return;
        UUID user=owners.owner(UUID.fromString(event.payload().get("patientId").asText()));if(user==null)return;
        messaging.convertAndSendToUser(user.toString(),"/queue/notifications",Map.of("id",event.eventId(),"invalidate",true));
    }
}
