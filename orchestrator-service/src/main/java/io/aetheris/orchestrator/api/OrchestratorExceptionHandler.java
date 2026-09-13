package io.aetheris.orchestrator.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class OrchestratorExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail handleMissingAgent(NoSuchElementException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Agent not found");
        return problem;
    }
}
