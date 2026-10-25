package com.careflow.notification.config;
import org.springframework.context.annotation.*;
import org.springframework.amqp.core.*;
import java.util.ArrayList;
import java.util.List;
@Configuration
public class RealtimeTopology {
    public static final String QUEUE="notification.sync.events";
    @Bean("notificationRealtimeBindings") Declarables realtimeTopology(){
        var queue=QueueBuilder.durable(QUEUE).withArgument("x-dead-letter-exchange","careflow.dlx").withArgument("x-dead-letter-routing-key",QUEUE+".dlq").build();
        var dlq=QueueBuilder.durable(QUEUE+".dlq").build();
        List<Declarable> all=new ArrayList<>(List.of(queue,dlq,BindingBuilder.bind(dlq).to(new DirectExchange("careflow.dlx")).with(QUEUE+".dlq")));
        for(String service:List.of("appointment","queue","lab","prescription","consultation")){
            var exchange=new TopicExchange(service+".exchange",true,false);all.add(exchange);all.add(BindingBuilder.bind(queue).to(exchange).with("#"));
        }
        return new Declarables(all);
    }
}
