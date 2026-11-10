package com.umc.pediupatrao.config;
import org.springframework.http.*;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestControllerAdvice public class ApiExceptionHandler {
 @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<Map<String,String>> badRequest(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("erro",safe(e.getMessage())));}
 @ExceptionHandler(NoSuchElementException.class) public ResponseEntity<Map<String,String>> notFound(NoSuchElementException e){return ResponseEntity.status(404).body(Map.of("erro",safe(e.getMessage())));}
 @ExceptionHandler(UnsupportedOperationException.class) public ResponseEntity<Map<String,String>> unsupported(UnsupportedOperationException e){return ResponseEntity.status(405).body(Map.of("erro",safe(e.getMessage())));}
 private String safe(String s){return s==null?"Operação inválida.":s;}
}
