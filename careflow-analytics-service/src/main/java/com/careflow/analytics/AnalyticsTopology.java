package com.careflow.analytics;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.*;
import org.springframework.context.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
@Configuration
public class AnalyticsTopology {
    @Bean public Declarables analyticsBindings(){
        List<Declarable> all=new ArrayList<>();var dlx=new DirectExchange("careflow.dlx",true,false);all.add(dlx);
        for(String source:List.of("appointment","queue","consultation")){
            String name="analytics."+source+".events";var exchange=new TopicExchange(source+".exchange",true,false);
            var queue=QueueBuilder.durable(name).withArgument("x-dead-letter-exchange","careflow.dlx").withArgument("x-dead-letter-routing-key",name+".dlq").build();
            var dead=QueueBuilder.durable(name+".dlq").build();all.addAll(List.of(exchange,queue,dead,BindingBuilder.bind(queue).to(exchange).with("#"),BindingBuilder.bind(dead).to(dlx).with(name+".dlq")));
        }return new Declarables(all);
    }
}
