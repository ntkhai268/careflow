package com.careflow.queue.config;
import com.careflow.common.exception.BusinessException;
import com.careflow.queue.client.PatientIntakeClient;
import com.careflow.queue.client.ReceptionRoomClient;
import feign.RequestTemplate;
import feign.Target;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class QueueAnonymousCatalogTest {
    RequestTemplate template(Class<?> type,String path){var t=new RequestTemplate();t.method("GET");t.uri(path);t.feignTarget(new Target.HardCodedTarget(type,"hospital-directory-service","https://localhost:18090/api/directory"));return t;}
    @Test void backgroundEventCanResolveOnlyThePublicDirectoryCatalog(){var filter=new QueueSecurityConfiguration().authenticatedFeignRequests();assertThatCode(()->filter.apply(template(ReceptionRoomClient.class,"/rooms"))).doesNotThrowAnyException();assertThatCode(()->filter.apply(template(ReceptionRoomClient.class,"/departments/CO_XUONG_KHOP"))).doesNotThrowAnyException();}
    @Test void samePathOnPrivateClientAndPrivateServiceCatalogStillRequireAnAuthenticatedRequest(){var filter=new QueueSecurityConfiguration().authenticatedFeignRequests();assertThatThrownBy(()->filter.apply(template(PatientIntakeClient.class,"/rooms"))).isInstanceOf(BusinessException.class);assertThatThrownBy(()->filter.apply(template(ReceptionRoomClient.class,"/services/GENERAL_CONSULTATION"))).isInstanceOf(BusinessException.class);}
}
