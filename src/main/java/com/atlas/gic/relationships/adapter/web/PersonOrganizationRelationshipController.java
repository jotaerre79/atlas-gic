package com.atlas.gic.relationships.adapter.web;

import com.atlas.gic.identity.domain.OrganizationId;
import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.relationships.application.AssignPersonOrganizationRelationshipCommand;
import com.atlas.gic.relationships.application.AssignPersonOrganizationRelationshipResult;
import com.atlas.gic.relationships.application.AssignPersonOrganizationRelationshipUseCase;
import com.atlas.gic.relationships.application.GetPersonOrganizationRelationshipsUseCase;
import com.atlas.gic.relationships.application.PersonOrganizationRelationshipView;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipStatus;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/persons/{personId}/organization-relationships")
public class PersonOrganizationRelationshipController {

    private final AssignPersonOrganizationRelationshipUseCase assignRelationship;
    private final GetPersonOrganizationRelationshipsUseCase getRelationships;

    public PersonOrganizationRelationshipController(
            AssignPersonOrganizationRelationshipUseCase assignRelationship,
            GetPersonOrganizationRelationshipsUseCase getRelationships) {
        this.assignRelationship = assignRelationship;
        this.getRelationships = getRelationships;
    }

    @PostMapping
    ResponseEntity<PersonOrganizationRelationshipCreatedResponse> assign(
            @PathVariable UUID personId,
            @Valid @RequestBody AssignPersonOrganizationRelationshipRequest request) {
        var result = assignRelationship.assign(new AssignPersonOrganizationRelationshipCommand(
                PersonId.of(personId),
                OrganizationId.of(request.organizationId()),
                request.relationshipType(),
                request.validFrom(),
                request.correlationId()));

        return ResponseEntity
                .status(201)
                .body(PersonOrganizationRelationshipCreatedResponse.from(result));
    }

    @GetMapping
    ResponseEntity<PersonOrganizationRelationshipListResponse> get(
            @PathVariable UUID personId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + GetPersonOrganizationRelationshipsUseCase.DEFAULT_PAGE_SIZE) int size,
            @RequestParam(defaultValue = "ACTIVE") PersonOrganizationRelationshipStatus status) {
        var result = getRelationships.get(PersonId.of(personId), page, size, status);
        return ResponseEntity.ok(new PersonOrganizationRelationshipListResponse(
                result.items().stream().map(PersonOrganizationRelationshipItemResponse::from).toList(),
                result.page(),
                result.size(),
                result.total()));
    }

    public record AssignPersonOrganizationRelationshipRequest(
            @NotNull UUID organizationId,
            @NotNull PersonOrganizationRelationshipType relationshipType,
            @NotNull LocalDate validFrom,
            @NotBlank @Size(max = 160) String correlationId) {
    }

    public record PersonOrganizationRelationshipCreatedResponse(
            String relationshipId,
            String personId,
            String organizationId,
            String relationshipType,
            LocalDate validFrom,
            LocalDate validTo,
            String status,
            Instant createdAt) {

        static PersonOrganizationRelationshipCreatedResponse from(AssignPersonOrganizationRelationshipResult result) {
            return new PersonOrganizationRelationshipCreatedResponse(
                    result.relationshipId().toString(),
                    result.personId().toString(),
                    result.organizationId().toString(),
                    result.relationshipType().name(),
                    result.validFrom(),
                    result.validTo(),
                    result.status().name(),
                    result.createdAt());
        }
    }

    public record PersonOrganizationRelationshipItemResponse(
            String relationshipId,
            String personId,
            String organizationId,
            String organizationLegalName,
            String organizationTradeName,
            String relationshipType,
            LocalDate validFrom,
            LocalDate validTo,
            String status,
            Instant createdAt) {

        static PersonOrganizationRelationshipItemResponse from(PersonOrganizationRelationshipView view) {
            return new PersonOrganizationRelationshipItemResponse(
                    view.relationshipId().toString(),
                    view.personId().toString(),
                    view.organizationId().toString(),
                    view.organizationLegalName(),
                    view.organizationTradeName(),
                    view.relationshipType().name(),
                    view.validFrom(),
                    view.validTo(),
                    view.status().name(),
                    view.createdAt());
        }
    }

    public record PersonOrganizationRelationshipListResponse(
            List<PersonOrganizationRelationshipItemResponse> items,
            int page,
            int size,
            long total) {
    }
}
