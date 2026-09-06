package com.atlas.gic.identity.adapter.web;

import com.atlas.gic.identity.application.OrganizationRetrievalUseCase;
import com.atlas.gic.identity.application.OrganizationSearchItem;
import com.atlas.gic.identity.application.OrganizationView;
import com.atlas.gic.identity.domain.OrganizationId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationRetrievalController {

    private final OrganizationRetrievalUseCase organizationRetrieval;

    public OrganizationRetrievalController(OrganizationRetrievalUseCase organizationRetrieval) {
        this.organizationRetrieval = organizationRetrieval;
    }

    @GetMapping("/{organizationId}")
    ResponseEntity<OrganizationResponse> get(@PathVariable String organizationId) {
        var organization = organizationRetrieval.get(OrganizationId.of(UUID.fromString(organizationId)));
        return ResponseEntity.ok(OrganizationResponse.from(organization));
    }

    @GetMapping
    ResponseEntity<OrganizationSearchResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + OrganizationRetrievalUseCase.DEFAULT_PAGE_SIZE) int size) {
        var result = organizationRetrieval.search(query, page, size);
        return ResponseEntity.ok(new OrganizationSearchResponse(
                result.items().stream().map(OrganizationSearchItemResponse::from).toList(),
                result.page(),
                result.size(),
                result.total()));
    }

    public record OrganizationResponse(
            String organizationId,
            String legalName,
            String tradeName,
            String status,
            List<IdentifierResponse> identifiers,
            Instant createdAt) {

        static OrganizationResponse from(OrganizationView organization) {
            return new OrganizationResponse(
                    organization.organizationId().toString(),
                    organization.legalName(),
                    organization.tradeName(),
                    organization.status(),
                    organization.identifiers().stream().map(IdentifierResponse::from).toList(),
                    organization.createdAt());
        }
    }

    public record IdentifierResponse(String type, String countryCode, String maskedValue) {

        static IdentifierResponse from(OrganizationView.IdentifierView identifier) {
            return new IdentifierResponse(identifier.type(), identifier.countryCode(), identifier.maskedValue());
        }
    }

    public record OrganizationSearchResponse(List<OrganizationSearchItemResponse> items, int page, int size, long total) {
    }

    public record OrganizationSearchItemResponse(
            String organizationId,
            String legalName,
            String tradeName,
            String status,
            String identifierType,
            String identifierCountryCode) {

        static OrganizationSearchItemResponse from(OrganizationSearchItem item) {
            return new OrganizationSearchItemResponse(
                    item.organizationId().toString(),
                    item.legalName(),
                    item.tradeName(),
                    item.status(),
                    item.identifierType(),
                    item.identifierCountryCode());
        }
    }
}
