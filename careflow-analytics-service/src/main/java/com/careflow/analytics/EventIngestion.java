package com.careflow.analytics;
import com.careflow.common.analytics.AnalyticsSnapshot;
import com.careflow.common.analytics.AnalyticsSnapshot.Source;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class EventIngestion {
    private final SnapshotStore store;
    public EventIngestion(SnapshotStore store){this.store=store;}
    @Transactional
    public void commit(Source source,UUID event,UUID aggregate,Optional<AnalyticsSnapshot> snapshot){
        store.lock();if(store.processed(source,event))return;
        String outcome=snapshot.map(value->store.accept(value,false)).orElse("SOURCE_RECORD_MISSING");
        store.receipt(source,event,aggregate,outcome);
    }
}
