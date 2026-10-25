package com.careflow.analytics;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.security.ActiveSessionClient.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AnalyticsSecurityTest {
    final AnalyticsReportService reports=mock(AnalyticsReportService.class);final ReconciliationService sync=mock(ReconciliationService.class);final SnapshotStore store=mock(SnapshotStore.class);
    final AnalyticsController controller=new AnalyticsController(reports,sync,store,false);
    Principal principal(String role){return new Principal(true,UUID.randomUUID(),role,UUID.randomUUID(),Instant.now().plusSeconds(60));}
    @Test void allNonAdminRolesAreDeniedBeforeAnyDataReadOrMutation(){for(String role:List.of("PATIENT","DOCTOR","STAFF","LAB_TECHNICIAN")){assertThatThrownBy(()->controller.report(principal(role),LocalDate.now(),LocalDate.now(),null,null)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(403));assertThatThrownBy(()->controller.reconcile(principal(role))).isInstanceOf(BusinessException.class);assertThatThrownBy(()->controller.rebuild(principal(role))).isInstanceOf(BusinessException.class);}verifyNoInteractions(reports,sync,store);}
    @Test void adminReportsAreNonCacheable(){var p=principal("ADMIN");var day=LocalDate.now();assertThat(controller.report(p,day,day,null,null).getHeaders().getCacheControl()).contains("no-store");verify(reports).report(day,day,null,null);}
    @Test void invalidDateRangesAndRoomIdentifiersAreRejected(){var service=new AnalyticsReportService(store,false);var d=LocalDate.now();assertThatThrownBy(()->service.report(d,d.minusDays(1),null,null)).isInstanceOf(BusinessException.class);assertThatThrownBy(()->service.report(d,d.plusDays(93),null,null)).isInstanceOf(BusinessException.class);assertThatThrownBy(()->service.report(d,d,null,"../private")).isInstanceOf(BusinessException.class);verifyNoInteractions(store);}
    @Test void missingAndMalformedReportFiltersReturn400()throws Exception{
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new AnalyticsController(new AnalyticsReportService(store,false),sync,store,false)).setControllerAdvice(new com.careflow.common.exception.GlobalExceptionHandler()).build();
        String attribute=com.careflow.common.security.ActiveSessionFilter.VERIFIED_PRINCIPAL_ATTRIBUTE;
        for(String path:List.of("/api/analytics/report","/api/analytics/report?from=not-a-date&to=2026-10-21","/api/analytics/report?from=2026-10-21&to=2026-10-21&departmentId=bad"))mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).requestAttr(attribute,principal("ADMIN"))).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        verifyNoInteractions(store);
    }
    @Test void productionCannotEnableSyntheticReporting(){var e=new MockEnvironment();e.setActiveProfiles("production","secure");assertThatThrownBy(()->new AnalyticsModeGuard(e,true)).isInstanceOf(IllegalStateException.class);assertThatCode(()->new AnalyticsModeGuard(e,false)).doesNotThrowAnyException();}
}
