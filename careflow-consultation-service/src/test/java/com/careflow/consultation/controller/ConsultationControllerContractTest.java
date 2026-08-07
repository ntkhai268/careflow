package com.careflow.consultation.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConsultationControllerContractTest {

    @Test
    void exposesTheDocumentedClinicalJourneyCommands() throws Exception {
        Class<?> controller = Class.forName("com.careflow.consultation.controller.ConsultationController");
        RequestMapping base = controller.getAnnotation(RequestMapping.class);

        assertThat(base).as("consultation controller base mapping").isNotNull();
        assertThat(List.of(base.value())).containsExactly("/api/consultations");

        List<String> mappings = Arrays.stream(controller.getDeclaredMethods())
                .flatMap(method -> mappingsFor(method).stream())
                .toList();

        assertThat(mappings).contains(
                "POST ",
                "GET /{id}",
                "PUT /{id}/clinical-data",
                "POST /{id}/wait-for-results",
                "POST /{id}/resume",
                "POST /{id}/complete");
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
        return List.of();
    }

    private static List<String> prefixed(String verb, String[] paths) {
        if (paths.length == 0) return List.of(verb + " ");
        return Arrays.stream(paths).map(path -> verb + " " + path).toList();
    }
}
