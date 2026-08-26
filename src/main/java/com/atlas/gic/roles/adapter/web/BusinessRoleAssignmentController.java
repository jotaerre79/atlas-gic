package com.atlas.gic.roles.adapter.web;

import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.roles.application.AssignBusinessRoleCommand;
import com.atlas.gic.roles.application.AssignBusinessRoleUseCase;
import com.atlas.gic.roles.application.GetBusinessRolesUseCase;
import com.atlas.gic.roles.domain.BusinessRoleType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/persons/{personId}/roles")
public class BusinessRoleAssignmentController {

    private final AssignBusinessRoleUseCase assignBusinessRole;
    private final GetBusinessRolesUseCase getBusinessRoles;

    public BusinessRoleAssignmentController(
            AssignBusinessRoleUseCase assignBusinessRole,
            GetBusinessRolesUseCase getBusinessRoles) {
        this.assignBusinessRole = assignBusinessRole;
        this.getBusinessRoles = getBusinessRoles;
    }

    @PostMapping
    ResponseEntity<BusinessRoleAssignmentResponse> assign(
            @PathVariable UUID personId,
            @Valid @RequestBody AssignBusinessRoleRequest request,
            HttpServletRequest httpRequest) {
        var result = assignBusinessRole.assign(new AssignBusinessRoleCommand(
                PersonId.of(personId),
                request.role(),
                request.validFrom(),
                request.validTo(),
                httpRequest.getHeader("X-Correlation-Id")));

        return ResponseEntity
                .created(URI.create("/api/v1/persons/" + personId + "/roles/" + result.assignmentId()))
                .body(new BusinessRoleAssignmentResponse(
                        result.assignmentId().toString(),
                        result.personId().toString(),
                        result.role().name(),
                        result.status().name(),
                        result.validFrom(),
                        result.validTo()));
    }

    @GetMapping
    BusinessRoleAssignmentListResponse get(@PathVariable UUID personId) {
        var result = getBusinessRoles.get(PersonId.of(personId));
        return new BusinessRoleAssignmentListResponse(result.items().stream()
                .map(item -> new BusinessRoleAssignmentResponse(
                        item.assignmentId().toString(),
                        item.personId().toString(),
                        item.role().name(),
                        item.status().name(),
                        item.validFrom(),
                        item.validTo()))
                .toList());
    }

    public record AssignBusinessRoleRequest(
            @NotNull BusinessRoleType role,
            @NotNull LocalDate validFrom,
            LocalDate validTo) {
    }

    public record BusinessRoleAssignmentResponse(
            String assignmentId,
            String personId,
            String role,
            String status,
            LocalDate validFrom,
            LocalDate validTo) {
    }

    public record BusinessRoleAssignmentListResponse(List<BusinessRoleAssignmentResponse> items) {
    }
}
