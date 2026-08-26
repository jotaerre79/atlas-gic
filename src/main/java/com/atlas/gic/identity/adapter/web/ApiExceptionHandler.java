package com.atlas.gic.identity.adapter.web;

import com.atlas.gic.identity.application.DuplicatePersonIdentifierException;
import com.atlas.gic.identity.application.PersonNotFoundException;
import com.atlas.gic.identity.application.TenantContextRequiredException;
import com.atlas.gic.roles.application.DuplicateActiveBusinessRoleException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({
            IllegalArgumentException.class,
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class
    })
    ProblemDetail badRequest(Exception exception) {
        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid request");
        problem.setDetail("Request validation failed");
        return problem;
    }

    @ExceptionHandler(TenantContextRequiredException.class)
    ProblemDetail forbidden(TenantContextRequiredException exception) {
        var problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setTitle("Forbidden");
        problem.setDetail("Authorized tenant context is required");
        return problem;
    }

    @ExceptionHandler(DuplicatePersonIdentifierException.class)
    ProblemDetail conflict(DuplicatePersonIdentifierException exception) {
        var problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Identifier conflict");
        problem.setDetail("A person with the same identifier already exists for this tenant");
        return problem;
    }

    @ExceptionHandler(DuplicateActiveBusinessRoleException.class)
    ProblemDetail duplicateRole(DuplicateActiveBusinessRoleException exception) {
        var problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Business role conflict");
        problem.setDetail("The person already has an active assignment for this business role");
        return problem;
    }

    @ExceptionHandler(PersonNotFoundException.class)
    ProblemDetail notFound(PersonNotFoundException exception) {
        var problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Person not found");
        problem.setDetail("Person was not found");
        return problem;
    }
}
