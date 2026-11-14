package com.riwi.skillbridge.payment.infrastructure.adapter.in.rest.filter;

import com.riwi.skillbridge.payment.application.common.CorrelationIdHolder;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/** Propaga {@code X-Correlation-Id} (cabecera → MDC → envelope de eventos → respuesta). */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements Filter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String CORRELATION_ID_MDC = "correlationId";
    private static final int MAX_LENGTH = 128;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String correlationId = ((HttpServletRequest) request).getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank() || correlationId.length() > MAX_LENGTH
                || !correlationId.matches("[A-Za-z0-9._:-]+")) {
            correlationId = UUID.randomUUID().toString();
        }
        CorrelationIdHolder.set(correlationId);
        MDC.put(CORRELATION_ID_MDC, correlationId);
        try {
            if (response instanceof HttpServletResponse httpResponse) {
                httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);
            }
            chain.doFilter(request, response);
        } finally {
            CorrelationIdHolder.clear();
            MDC.remove(CORRELATION_ID_MDC);
        }
    }
}
