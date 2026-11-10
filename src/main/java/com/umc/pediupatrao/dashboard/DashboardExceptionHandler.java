package com.umc.pediupatrao.dashboard;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Padroniza os erros da API do dashboard: {@code {"erro","status","caminho","timestamp"}}.
 * Nunca devolve stack trace ao cliente (RNF de segurança).
 */
@RestControllerAdvice(assignableTypes = DashboardController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DashboardExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DashboardExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> parametroInvalido(IllegalArgumentException e, HttpServletRequest req) {
        return corpo(HttpStatus.BAD_REQUEST, e.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> tipoInvalido(MethodArgumentTypeMismatchException e,
            HttpServletRequest req) {
        return corpo(HttpStatus.BAD_REQUEST,
                "Parâmetro '" + e.getName() + "' inválido: '" + e.getValue() + "'.", req);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> bancoIndisponivel(DataAccessException e, HttpServletRequest req) {
        log.error("Falha ao consultar o MongoDB para o dashboard", e);
        return corpo(HttpStatus.SERVICE_UNAVAILABLE,
                "Não foi possível consultar o banco de dados no momento. Tente novamente em instantes.", req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> erroInesperado(Exception e, HttpServletRequest req) {
        log.error("Erro inesperado no dashboard", e);
        return corpo(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao gerar os dados do dashboard.", req);
    }

    static ResponseEntity<Map<String, Object>> corpo(HttpStatus status, String mensagem, HttpServletRequest req) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("erro", mensagem == null ? "Requisição inválida." : mensagem);
        m.put("status", status.value());
        m.put("caminho", req.getRequestURI());
        m.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(m);
    }
}
