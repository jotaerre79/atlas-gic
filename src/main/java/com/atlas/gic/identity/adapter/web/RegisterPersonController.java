package com.atlas.gic.identity.adapter.web;

import com.atlas.gic.identity.application.RegisterPersonCommand;
import com.atlas.gic.identity.application.RegisterPersonUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/persons")
public class RegisterPersonController {

    private final RegisterPersonUseCase registerPerson;

    public RegisterPersonController(RegisterPersonUseCase registerPerson) {
        this.registerPerson = registerPerson;
    }

    @PostMapping
    ResponseEntity<RegisterPersonResponse> register(
            @Valid @RequestBody RegisterPersonRequest request,
            HttpServletRequest httpRequest) {
        var result = registerPerson.register(new RegisterPersonCommand(
                request.givenName(),
                request.middleName(),
                request.familyName(),
                new RegisterPersonCommand.IdentifierCommand(
                        request.identifier().type(),
                        request.identifier().value(),
                        request.identifier().issuer()),
                httpRequest.getHeader("X-Correlation-Id")));

        return ResponseEntity
                .created(URI.create("/api/v1/persons/" + result.personId()))
                .body(new RegisterPersonResponse(
                        result.personId().toString(),
                        result.status().name(),
                        result.displayName()));
    }

    public record RegisterPersonRequest(
            @NotBlank String givenName,
            String middleName,
            @NotBlank String familyName,
            String tenantId,
            @Valid @NotNull IdentifierRequest identifier) {
    }

    public record IdentifierRequest(
            @NotBlank String type,
            @NotBlank String value,
            String issuer) {
    }

    public record RegisterPersonResponse(String personId, String status, String displayName) {
    }
}
