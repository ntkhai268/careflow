package com.careflow.analytics;
import com.careflow.common.analytics.*;
import com.careflow.common.analytics.AnalyticsSnapshot.Source;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.security.InternalTlsMaterial;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
@Component
public class SourceClient {
    private final HttpClient http;private final ObjectMapper json;private final String key;private final Map<Source,URI> urls;
    public SourceClient(@Value("${analytics.sources.appointment}") String a,@Value("${analytics.sources.queue}") String q,@Value("${analytics.sources.consultation}") String c,@Value("${security.caller.key}") String key,@Value("${security.audit.allow-loopback-http:false}") boolean audit,ObjectMapper json,ObjectProvider<InternalTlsMaterial> tls){
        this.json=json;this.key=key;urls=Map.of(Source.APPOINTMENT,uri(a,audit),Source.QUEUE,uri(q,audit),Source.CONSULTATION,uri(c,audit));
        var builder=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER);var material=tls.getIfAvailable();if(material!=null)builder.sslContext(material.context());http=builder.build();
    }
    private static URI uri(String input,boolean audit){var u=URI.create(input);if(u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||u.getHost()==null||(!"https".equals(u.getScheme())&&!(audit&&"http".equals(u.getScheme())&&Set.of("localhost","127.0.0.1","::1").contains(u.getHost()))))throw new IllegalArgumentException("Analytics source must use trusted HTTPS");return u;}
    public Optional<AnalyticsSnapshot> one(Source source,UUID id){var result=fetch(source,"?id="+id+"&limit=1",true);if(result==null)return Optional.empty();if(result.items().size()!=1||!result.items().getFirst().id().equals(id))throw unavailable();return Optional.of(result.items().getFirst());}
    public AnalyticsExportPage page(Source source,UUID after){return fetch(source,"?limit=200"+(after==null?"":"&after="+after),false);}
    private AnalyticsExportPage fetch(Source source,String query,boolean optional){
        try{
            var request=HttpRequest.newBuilder(urls.get(source).resolve("/internal/analytics/records"+query)).timeout(Duration.ofSeconds(5)).header("X-Careflow-Caller","analytics-service").header("X-Careflow-Caller-Key",key).header("Accept","application/json").GET().build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());if(optional&&response.statusCode()==404)return null;if(response.statusCode()!=200)throw unavailable();
            var body=json.readTree(response.body());if(body.path("status").asInt()!=200)throw unavailable();var result=json.treeToValue(body.path("data"),AnalyticsExportPage.class);
            if(result==null||result.items()==null||result.items().size()>500||result.capturedAt()==null||result.items().stream().anyMatch(v->v.source()!=source))throw unavailable();return result;
        }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();throw unavailable();}
    }
    private BusinessException unavailable(){return new BusinessException(503,"Chưa lấy được snapshot từ service nguồn.");}
}
