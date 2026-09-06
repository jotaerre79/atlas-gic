package com.atlas.gic.identity.adapter.web;

import com.atlas.gic.identity.application.RegisterOrganizationCommand;
import com.atlas.gic.identity.application.RegisterOrganizationUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/organizations")
@ConditionalOnBean(RegisterOrganizationUseCase.class)
public class RegisterOrganizationController {

    private final RegisterOrganizationUseCase registerOrganization;

    public RegisterOrganizationController(RegisterOrganizationUseCase registerOrganization) {
        this.registerOrganization = registerOrganization;
    }

    @PostMapping
    ResponseEntity<RegisterOrganizationResponse> register(
            @Valid @RequestBody RegisterOrganizationRequest request) {
        var result = registerOrganization.register(new RegisterOrganizationCommand(
                request.legalName(),
                request.tradeName(),
                new RegisterOrganizationCommand.IdentifierCommand(
                        request.identifier().type(),
                        request.identifier().value(),
                        request.identifier().countryCode()),
                request.correlationId()));

        return ResponseEntity
                .created(URI.create("/api/v1/organizations/" + result.organizationId()))
                .body(new RegisterOrganizationResponse(
                        result.organizationId().toString(),
                        result.legalName(),
                        result.tradeName(),
                        result.status().name(),
                        new IdentifierResponse(
                                result.identifier().type(),
                                result.identifier().countryCode(),
                                result.identifier().maskedValue()),
                        result.createdAt()));
    }

    public record RegisterOrganizationRequest(
            @NotBlank @Size(max = 240) String legalName,
            @Size(max = 240) String tradeName,
            @Valid @NotNull IdentifierRequest identifier,
            @NotBlank @Size(max = 160) String correlationId) {
    }

    public record IdentifierRequest(
            @NotBlank String type,
            @NotBlank @Size(max = 160) String value,
            @NotBlank @Size(min = 2, max = 2) String countryCode) {
    }

    public record RegisterOrganizationResponse(
            String organizationId,
            String legalName,
            String tradeName,
            String status,
            IdentifierResponse identifier,
            Instant createdAt) {
    }

    public record IdentifierResponse(String type, String countryCode, String maskedValue) {
    }
}
