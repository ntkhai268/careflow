package com.careflow.directory.controller;

import com.careflow.directory.dto.request.ServicePriceRequest;
import com.careflow.directory.service.ServiceCatalogService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServiceCatalogAuthorizationTest {
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"PATIENT","STAFF","DOCTOR","LAB_TECHNICIAN"})
    void onlyAdminCanPublishOrReadAdministration(String role) {
        var catalog=mock(ServiceCatalogService.class);var controller=new ServiceCatalogController(catalog);
        assertThatThrownBy(() -> controller.admin(role)).hasMessageContaining("quản trị viên");
        assertThatThrownBy(() -> controller.publish(new ServicePriceRequest("GENERAL_CONSULTATION","Khám",null,1,15,true,null,0),role,UUID.randomUUID())).hasMessageContaining("quản trị viên");
        verifyNoInteractions(catalog);
    }
}
