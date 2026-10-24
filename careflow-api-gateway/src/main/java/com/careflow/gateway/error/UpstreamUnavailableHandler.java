package com.careflow.gateway.error;

import com.careflow.common.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.net.ConnectException;
import java.net.UnknownHostException;

/** Only transport connection failures are translated; all other handlers keep their status. */
@Component
@Order(-2)
public class UpstreamUnavailableHandler implements ErrorWebExceptionHandler {
    private final ObjectMapper json;

    public UpstreamUnavailableHandler(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        if (exchange.getResponse().isCommitted() || !connectionFailure(error)) {
            return Mono.error(error);
        }
        try {
            byte[] body = json.writeValueAsBytes(ApiResponse.error(503,
                    "Dịch vụ tạm thời chưa sẵn sàng. Vui lòng thử lại."));
            var response = exchange.getResponse();
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            response.getHeaders().setCacheControl("no-store");
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
        } catch (Exception serializationError) {
            return Mono.error(error);
        }
    }

    private boolean connectionFailure(Throwable error) {
        for (int depth = 0; error != null && depth < 8; depth++, error = error.getCause()) {
            if (error instanceof ConnectException || error instanceof UnknownHostException) {
                return true;
            }
        }
        return false;
    }
}
