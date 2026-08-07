package com.careflow.lab.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LabOrderControllerContractTest {

    @Test
    void exposesTheDocumentedLabOrderResource() throws Exception {
        Class<?> controller = Class.forName("com.careflow.lab.controller.LabOrderController");

        RequestMapping base = controller.getAnnotation(RequestMapping.class);
        assertThat(base).as("lab order controller base mapping").isNotNull();
        assertThat(List.of(base.value())).containsExactly("/api/labs/orders");

        List<String> mappings = Arrays.stream(controller.getDeclaredMethods())
                .flatMap(method -> mappingsFor(method).stream())
                .toList();

        assertThat(mappings).contains(
                "POST ",
                "GET /{id}",
                "GET /consultation/{consultationId}",
                "GET /patient/{patientId}",
                "POST /{id}/start",
                "PUT /{id}/items/{itemId}/result",
                "POST /{id}/finalize",
                "POST /{id}/mark-reviewed",
                "POST /{id}/cancel");
    }

    private static List<String> mappingsFor(Method method) {
        if (method.isAnnotationPresent(PostMapping.class)) {
            return prefixed("POST", method.getAnnotation(PostMapping.class).value());
        }
        if (method.isAnnotationPresent(GetMapping.class)) {
            return prefixed("GET", method.getAnnotation(GetMapping.class).value());
        }
        if (method.isAnnotationPresent(PutMapping.class)) {
            return prefixed("PUT", method.getAnnotation(PutMapping.class).value());
        }
        if (method.isAnnotationPresent(DeleteMapping.class)) {
            return prefixed("DELETE", method.getAnnotation(DeleteMapping.class).value());
        }
        return List.of();
    }

    private static List<String> prefixed(String verb, String[] paths) {
        if (paths.length == 0) {
            return List.of(verb + " ");
        }
        return Arrays.stream(paths).map(path -> verb + " " + path).toList();
    }
}
