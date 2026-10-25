package com.careflow.common.analytics;
import com.careflow.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import javax.security.auth.x500.X500Principal;
import java.security.cert.X509Certificate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AnalyticsExportSecurityTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);final String key="synthetic_analytics_key_"+"x".repeat(32);
    MockHttpServletRequest request(String cn,String secret){var request=new MockHttpServletRequest();request.setSecure(true);request.addHeader("X-Careflow-Caller","analytics-service");request.addHeader("X-Careflow-Caller-Key",secret);var cert=mock(X509Certificate.class);when(cert.getSubjectX500Principal()).thenReturn(new X500Principal("CN="+cn));request.setAttribute("jakarta.servlet.request.X509Certificate",new X509Certificate[]{cert});return request;}
    @Test void forgedHeaderWithoutMatchingClientCertificateCannotReadSource(){var support=new AnalyticsExportSupport(jdbc,key,false);assertThatThrownBy(()->support.read(request("api-gateway",key),null,null,200,AnalyticsSnapshot.Source.QUEUE,"SELECT synthetic")).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(401));verifyNoInteractions(jdbc);}
    @Test void missingOrWrongSharedCapabilityCannotReadSource(){for(String configured:new String[]{key,""}){var support=new AnalyticsExportSupport(jdbc,configured,false);assertThatThrownBy(()->support.read(request("analytics-service","incorrect"),null,null,200,AnalyticsSnapshot.Source.QUEUE,"SELECT synthetic")).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(403));}verifyNoInteractions(jdbc);}
    @Test void exportCannotAskForUnboundedPage(){var support=new AnalyticsExportSupport(jdbc,key,false);assertThatThrownBy(()->support.read(request("analytics-service",key),null,null,501,AnalyticsSnapshot.Source.QUEUE,"SELECT synthetic")).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(400));verifyNoInteractions(jdbc);}
}
