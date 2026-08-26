package com.atlas.gic.identity.adapter.web;

import com.atlas.gic.identity.application.PersonRetrievalUseCase;
import com.atlas.gic.identity.application.PersonSearchItem;
import com.atlas.gic.identity.application.PersonView;
import com.atlas.gic.identity.domain.PersonId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/persons")
public class PersonRetrievalController {

    private final PersonRetrievalUseCase personRetrieval;

    public PersonRetrievalController(PersonRetrievalUseCase personRetrieval) {
        this.personRetrieval = personRetrieval;
    }

    @GetMapping("/{personId}")
    ResponseEntity<PersonResponse> get(@PathVariable String personId) {
        var person = personRetrieval.get(PersonId.of(UUID.fromString(personId)));
        return ResponseEntity.ok(PersonResponse.from(person));
    }

    @GetMapping
    ResponseEntity<PersonSearchResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PersonRetrievalUseCase.DEFAULT_PAGE_SIZE) int size) {
        var result = personRetrieval.search(query, page, size);
        return ResponseEntity.ok(new PersonSearchResponse(
                result.items().stream().map(PersonSearchItemResponse::from).toList(),
                result.page(),
                result.size(),
                result.total()));
    }

    public record PersonResponse(
            String personId,
            String status,
            String givenName,
            String middleName,
            String familyName,
            String displayName,
            List<IdentifierResponse> identifiers) {

        static PersonResponse from(PersonView person) {
            return new PersonResponse(
                    person.personId().toString(),
                    person.status(),
                    person.givenName(),
                    person.middleName(),
                    person.familyName(),
                    person.displayName(),
                    person.identifiers().stream().map(IdentifierResponse::from).toList());
        }
    }

    public record IdentifierResponse(String type, String issuer, String maskedValue) {

        static IdentifierResponse from(PersonView.IdentifierView identifier) {
            return new IdentifierResponse(identifier.type(), identifier.issuer(), identifier.maskedValue());
        }
    }

    public record PersonSearchResponse(List<PersonSearchItemResponse> items, int page, int size, long total) {
    }

    public record PersonSearchItemResponse(
            String personId,
            String displayName,
            String status,
            String identifierType,
            String identifierIssuer) {

        static PersonSearchItemResponse from(PersonSearchItem item) {
            return new PersonSearchItemResponse(
                    item.personId().toString(),
                    item.displayName(),
                    item.status(),
                    item.identifierType(),
                    item.identifierIssuer());
        }
    }
}
