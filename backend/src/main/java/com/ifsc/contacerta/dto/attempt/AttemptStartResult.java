package com.ifsc.contacerta.dto.attempt;
import org.springframework.http.HttpStatus; import java.net.URI;
public record AttemptStartResult(HttpStatus status, URI location, AttemptResponse body) {}
