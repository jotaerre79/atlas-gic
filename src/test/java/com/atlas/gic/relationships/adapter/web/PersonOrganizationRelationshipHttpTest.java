package com.atlas.gic.relationships.adapter.web;

import com.atlas.gic.identity.application.OrganizationReadRepository;
import com.atlas.gic.identity.application.OrganizationRegistrationAudit;
import com.atlas.gic.identity.application.OrganizationRepository;
import com.atlas.gic.identity.application.PersonReadRepository;
import com.atlas.gic.identity.application.PersonRepository;
import com.atlas.gic.identity.application.PersonSearchPage;
import com.atlas.gic.identity.domain.OrganizationId;
import com.atlas.gic.identity.domain.Person;
import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.relationships.application.DuplicateActivePersonOrganizationRelationshipException;
import com.atlas.gic.relationships.application.PersonOrganizationRelationshipCreatedAudit;
import com.atlas.gic.relationships.application.PersonOrganizationRelationshipCreatedAuditEntry;
import com.atlas.gic.relationships.application.PersonOrganizationRelationshipListPage;
import com.atlas.gic.relationships.application.PersonOrganizationRelationshipRepository;
import com.atlas.gic.relationships.application.PersonOrganizationRelationshipView;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationship;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipStatus;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipType;
import com.atlas.gic.roles.application.BusinessRoleAssignedAudit;
import com.atlas.gic.roles.application.BusinessRoleAssignmentList;
import com.atlas.gic.roles.application.BusinessRoleAssignmentRepository;
import com.atlas.gic.roles.application.BusinessRoleEndedAudit;
import com.atlas.gic.roles.domain.BusinessRoleAssignment;
import com.atlas.gic.roles.domain.BusinessRoleAssignmentId;
import com.atlas.gic.shared.tenancy.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
@AutoConfigureMockMvc
class PersonOrganizationRelationshipHttpTest {

    private static final String TENANT_A = "11111111-1111-1111-1111-111111111111";
    private static final String TENANT_B = "22222222-2222-2222-2222-222222222222";
    private static final String PERSON_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String ORGANIZATION_ID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    private final MockMvc mockMvc;
    private final RecordingRelationshipRepository repository;
    private final RecordingRelationshipAudit audit;

    @Autowired
    PersonOrganizationRelationshipHttpTest(
            MockMvc mockMvc,
            RecordingRelationshipRepository repository,
            RecordingRelationshipAudit audit) {
        this.mockMvc = mockMvc;
        this.repository = repository;
        this.audit = audit;
    }

    @BeforeEach
    void reset() {
        repository.clear();
        audit.clear();
    }

    @Test
    void returnsCreatedForValidRelationshipWithoutLocationForUnimplementedDetailResource() throws Exception {
        repository.personExists = true;
        repository.organizationExists = true;

        mockMvc.perform(post("/api/v1/persons/{personId}/organization-relationships", PERSON_ID)
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.personId").value(PERSON_ID))
                .andExpect(jsonPath("$.organizationId").value(ORGANIZATION_ID))
                .andExpect(jsonPath("$.relationshipType").value("REPRESENTATIVE_OF"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.tenantId").doesNotExist())
                .andExpect(jsonPath("$.ruc").doesNotExist());

        assertThat(repository.saved).singleElement()
                .satisfies(relationship -> assertThat(relationship.tenantId().toString()).isEqualTo(TENANT_A));
        assertThat(audit.entries).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.actor()).isEqualTo("tenant-user");
                    assertThat(entry.relationshipType()).isEqualTo(PersonOrganizationRelationshipType.REPRESENTATIVE_OF);
                    assertThat(entry.correlationId()).isEqualTo("corr-rel-http");
                });
    }

    @Test
    void returnsOkForPagedActiveRelationships() throws Exception {
        repository.personExists = true;
        repository.page = new PersonOrganizationRelationshipListPage(List.of(new PersonOrganizationRelationshipView(
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                UUID.fromString(PERSON_ID),
                UUID.fromString(ORGANIZATION_ID),
                "Atlas Cooperativa",
                "Atlas",
                PersonOrganizationRelationshipType.REPRESENTATIVE_OF,
                LocalDate.parse("2026-09-07"),
                null,
                PersonOrganizationRelationshipStatus.ACTIVE,
                Instant.parse("2026-09-07T00:00:00Z"))), 0, 20, 1);

        mockMvc.perform(get("/api/v1/persons/{personId}/organization-relationships", PERSON_ID)
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].personId").value(PERSON_ID))
                .andExpect(jsonPath("$.items[0].organizationId").value(ORGANIZATION_ID))
                .andExpect(jsonPath("$.items[0].organizationLegalName").value("Atlas Cooperativa"))
                .andExpect(jsonPath("$.items[0].relationshipType").value("REPRESENTATIVE_OF"))
                .andExpect(jsonPath("$.items[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.items[0].ruc").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void returnsBadRequestForInvalidPayloadPaginationOrStatus() throws Exception {
        repository.personExists = true;
        repository.organizationExists = true;

        mockMvc.perform(post("/api/v1/persons/{personId}/organization-relationships", PERSON_ID)
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId": "%s",
                                  "relationshipType": "UNSUPPORTED",
                                  "validFrom": "2026-09-07",
                                  "correlationId": "corr-rel-http"
                                }
                                """.formatted(ORGANIZATION_ID)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/persons/{personId}/organization-relationships?page=-1", PERSON_ID)
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/persons/{personId}/organization-relationships?status=ENDED", PERSON_ID)
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsUnauthorizedOrForbiddenForSecurityFailures() throws Exception {
        mockMvc.perform(post("/api/v1/persons/{personId}/organization-relationships", PERSON_ID)
                        .with(anonymous())
                        .header("X-Tenant-Id", TENANT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/persons/{personId}/organization-relationships", PERSON_ID)
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsNotFoundForInvisiblePersonOrOrganization() throws Exception {
        repository.personExists = false;

        mockMvc.perform(post("/api/v1/persons/{personId}/organization-relationships", PERSON_ID)
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isNotFound());

        repository.personExists = true;
        repository.organizationExists = false;
        mockMvc.perform(post("/api/v1/persons/{personId}/organization-relationships", PERSON_ID)
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsConflictForDuplicateActiveRelationship() throws Exception {
        repository.personExists = true;
        repository.organizationExists = true;
        repository.duplicate = true;

        mockMvc.perform(post("/api/v1/persons/{personId}/organization-relationships", PERSON_ID)
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Relationship conflict"));
    }

    @Test
    void tenantIdInBodyDoesNotGrantAuthority() throws Exception {
        repository.personExists = true;
        repository.organizationExists = true;

        mockMvc.perform(post("/api/v1/persons/{personId}/organization-relationships", PERSON_ID)
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId": "%s",
                                  "relationshipType": "REPRESENTATIVE_OF",
                                  "validFrom": "2026-09-07",
                                  "correlationId": "corr-rel-http",
                                  "tenantId": "%s"
                                }
                                """.formatted(ORGANIZATION_ID, TENANT_B)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", not(hasKey("tenantId"))));

        assertThat(repository.saved).singleElement()
                .satisfies(relationship -> assertThat(relationship.tenantId().toString()).isEqualTo(TENANT_A));
    }

    private String validPayload() {
        return """
                {
                  "organizationId": "%s",
                  "relationshipType": "REPRESENTATIVE_OF",
                  "validFrom": "2026-09-07",
                  "correlationId": "corr-rel-http"
                }
                """.formatted(ORGANIZATION_ID);
    }

    @TestConfiguration
    static class HttpTestConfiguration {

        @Bean
        @Primary
        RecordingRelationshipRepository recordingRelationshipRepository() {
            return new RecordingRelationshipRepository();
        }

        @Bean
        @Primary
        RecordingRelationshipAudit recordingRelationshipAudit() {
            return new RecordingRelationshipAudit();
        }

        @Bean
        @Primary
        PersonRepository personRepository() {
            return new PersonRepository() {
                @Override
                public void save(Person person) {
                }
            };
        }

        @Bean
        @Primary
        PersonReadRepository personReadRepository() {
            return new PersonReadRepository() {
                @Override
                public Optional<com.atlas.gic.identity.application.PersonView> findById(
                        TenantId tenantId,
                        PersonId personId) {
                    return Optional.empty();
                }

                @Override
                public PersonSearchPage search(TenantId tenantId, String query, int page, int size) {
                    return new PersonSearchPage(List.of(), page, size, 0);
                }
            };
        }

        @Bean
        @Primary
        com.atlas.gic.identity.application.PersonRegistrationAudit personRegistrationAudit() {
            return entry -> {
            };
        }

        @Bean
        @Primary
        OrganizationRepository organizationRepository() {
            return organization -> {
            };
        }

        @Bean
        @Primary
        OrganizationReadRepository organizationReadRepository() {
            return new OrganizationReadRepository() {
                @Override
                public Optional<com.atlas.gic.identity.application.OrganizationView> findById(
                        TenantId tenantId,
                        OrganizationId organizationId) {
                    return Optional.empty();
                }

                @Override
                public com.atlas.gic.identity.application.OrganizationSearchPage search(
                        TenantId tenantId,
                        String query,
                        int page,
                        int size) {
                    return new com.atlas.gic.identity.application.OrganizationSearchPage(List.of(), page, size, 0);
                }
            };
        }

        @Bean
        @Primary
        OrganizationRegistrationAudit organizationRegistrationAudit() {
            return entry -> {
            };
        }

        @Bean
        @Primary
        BusinessRoleAssignmentRepository businessRoleAssignmentRepository() {
            return new BusinessRoleAssignmentRepository() {
                @Override
                public boolean personExists(TenantId tenantId, PersonId personId) {
                    return false;
                }

                @Override
                public void save(BusinessRoleAssignment assignment, String actor) {
                }

                @Override
                public List<com.atlas.gic.roles.application.BusinessRoleAssignmentView> findByPerson(
                        TenantId tenantId,
                        PersonId personId) {
                    return List.of();
                }

                @Override
                public Optional<BusinessRoleAssignment> findById(
                        TenantId tenantId,
                        PersonId personId,
                        BusinessRoleAssignmentId assignmentId) {
                    return Optional.empty();
                }

                @Override
                public boolean endActive(
                        TenantId tenantId,
                        PersonId personId,
                        BusinessRoleAssignment endedAssignment,
                        String actor,
                        String reason) {
                    return false;
                }
            };
        }

        @Bean
        @Primary
        BusinessRoleAssignedAudit businessRoleAssignedAudit() {
            return entry -> {
            };
        }

        @Bean
        @Primary
        BusinessRoleEndedAudit businessRoleEndedAudit() {
            return entry -> {
            };
        }
    }

    static class RecordingRelationshipRepository implements PersonOrganizationRelationshipRepository {

        private boolean personExists;
        private boolean organizationExists;
        private boolean duplicate;
        private PersonOrganizationRelationshipListPage page =
                new PersonOrganizationRelationshipListPage(List.of(), 0, 20, 0);
        private final List<PersonOrganizationRelationship> saved = new ArrayList<>();

        @Override
        public boolean personExists(TenantId tenantId, PersonId personId) {
            return personExists;
        }

        @Override
        public boolean organizationExists(TenantId tenantId, OrganizationId organizationId) {
            return organizationExists;
        }

        @Override
        public void save(PersonOrganizationRelationship relationship, String actor) {
            if (duplicate) {
                throw new DuplicateActivePersonOrganizationRelationshipException();
            }
            saved.add(relationship);
        }

        @Override
        public PersonOrganizationRelationshipListPage findByPerson(
                TenantId tenantId,
                PersonId personId,
                PersonOrganizationRelationshipStatus status,
                int page,
                int size) {
            return this.page;
        }

        void clear() {
            personExists = false;
            organizationExists = false;
            duplicate = false;
            page = new PersonOrganizationRelationshipListPage(List.of(), 0, 20, 0);
            saved.clear();
        }
    }

    static class RecordingRelationshipAudit implements PersonOrganizationRelationshipCreatedAudit {

        private final List<PersonOrganizationRelationshipCreatedAuditEntry> entries = new ArrayList<>();

        @Override
        public void record(PersonOrganizationRelationshipCreatedAuditEntry entry) {
            entries.add(entry);
        }

        void clear() {
            entries.clear();
        }
    }
}
