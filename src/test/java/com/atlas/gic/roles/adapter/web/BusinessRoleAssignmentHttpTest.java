package com.atlas.gic.roles.adapter.web;

import com.atlas.gic.identity.application.PersonReadRepository;
import com.atlas.gic.identity.application.PersonRepository;
import com.atlas.gic.identity.application.PersonSearchPage;
import com.atlas.gic.identity.domain.Person;
import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.roles.application.BusinessRoleAssignedAudit;
import com.atlas.gic.roles.application.BusinessRoleAssignedAuditEntry;
import com.atlas.gic.roles.application.BusinessRoleAssignmentRepository;
import com.atlas.gic.roles.application.BusinessRoleAssignmentView;
import com.atlas.gic.roles.application.DuplicateActiveBusinessRoleException;
import com.atlas.gic.roles.domain.BusinessRoleAssignment;
import com.atlas.gic.roles.domain.BusinessRoleAssignmentStatus;
import com.atlas.gic.roles.domain.BusinessRoleType;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
@AutoConfigureMockMvc
class BusinessRoleAssignmentHttpTest {

    private static final String TENANT_A = "11111111-1111-1111-1111-111111111111";
    private static final String TENANT_B = "22222222-2222-2222-2222-222222222222";
    private static final String PERSON_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    private final MockMvc mockMvc;
    private final RecordingBusinessRoleAssignmentRepository repository;
    private final RecordingBusinessRoleAssignedAudit audit;

    @Autowired
    BusinessRoleAssignmentHttpTest(
            MockMvc mockMvc,
            RecordingBusinessRoleAssignmentRepository repository,
            RecordingBusinessRoleAssignedAudit audit) {
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
    void returnsCreatedForValidAssignment() throws Exception {
        repository.personExists = true;

        mockMvc.perform(post("/api/v1/persons/{personId}/roles", PERSON_ID)
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A)
                        .header("X-Correlation-Id", "corr-role-http")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "SOCIO",
                                  "validFrom": "2026-08-26"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.personId").value(PERSON_ID))
                .andExpect(jsonPath("$.role").value("SOCIO"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(repository.saved).singleElement()
                .satisfies(assignment -> assertThat(assignment.tenantId().toString()).isEqualTo(TENANT_A));
        assertThat(repository.lastActor).isEqualTo("tenant-user");
        assertThat(audit.entries).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.actor()).isEqualTo("tenant-user");
                    assertThat(entry.correlationId()).isEqualTo("corr-role-http");
                    assertThat(entry.role()).isEqualTo(BusinessRoleType.SOCIO);
                });
    }

    @Test
    void returnsBadRequestForInvalidValidityPeriod() throws Exception {
        repository.personExists = true;

        mockMvc.perform(post("/api/v1/persons/{personId}/roles", PERSON_ID)
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "CLIENTE",
                                  "validFrom": "2026-08-26",
                                  "validTo": "2026-08-25"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsUnauthorizedForAnonymousAssignment() throws Exception {
        mockMvc.perform(post("/api/v1/persons/{personId}/roles", PERSON_ID)
                        .with(anonymous())
                        .header("X-Tenant-Id", TENANT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsForbiddenWhenTenantIsNotAuthorized() throws Exception {
        mockMvc.perform(post("/api/v1/persons/{personId}/roles", PERSON_ID)
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsNotFoundWhenPersonDoesNotExistInTenant() throws Exception {
        repository.personExists = false;

        mockMvc.perform(post("/api/v1/persons/{personId}/roles", PERSON_ID)
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsConflictForDuplicateActiveAssignment() throws Exception {
        repository.personExists = true;
        repository.duplicate = true;

        mockMvc.perform(post("/api/v1/persons/{personId}/roles", PERSON_ID)
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Business role conflict"));
    }

    @Test
    void getRolesReturnsTenantScopedItems() throws Exception {
        repository.personExists = true;
        repository.views.add(new BusinessRoleAssignmentView(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                UUID.fromString(PERSON_ID),
                BusinessRoleType.CLIENTE,
                BusinessRoleAssignmentStatus.ACTIVE,
                LocalDate.parse("2026-08-26"),
                null));

        mockMvc.perform(get("/api/v1/persons/{personId}/roles", PERSON_ID)
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].personId").value(PERSON_ID))
                .andExpect(jsonPath("$.items[0].role").value("CLIENTE"));

        assertThat(repository.lastTenant.toString()).isEqualTo(TENANT_A);
    }

    private String validPayload() {
        return """
                {
                  "role": "SOCIO",
                  "validFrom": "2026-08-26"
                }
                """;
    }

    @TestConfiguration
    static class HttpTestConfiguration {

        @Bean
        @Primary
        RecordingBusinessRoleAssignmentRepository recordingBusinessRoleAssignmentRepository() {
            return new RecordingBusinessRoleAssignmentRepository();
        }

        @Bean
        @Primary
        RecordingBusinessRoleAssignedAudit recordingBusinessRoleAssignedAudit() {
            return new RecordingBusinessRoleAssignedAudit();
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
    }

    static class RecordingBusinessRoleAssignmentRepository implements BusinessRoleAssignmentRepository {

        private boolean personExists;
        private boolean duplicate;
        private String lastActor;
        private TenantId lastTenant;
        private final List<BusinessRoleAssignment> saved = new ArrayList<>();
        private final List<BusinessRoleAssignmentView> views = new ArrayList<>();

        @Override
        public boolean personExists(TenantId tenantId, PersonId personId) {
            lastTenant = tenantId;
            return personExists;
        }

        @Override
        public void save(BusinessRoleAssignment assignment, String actor) {
            if (duplicate) {
                throw new DuplicateActiveBusinessRoleException();
            }
            lastActor = actor;
            saved.add(assignment);
        }

        @Override
        public List<BusinessRoleAssignmentView> findByPerson(TenantId tenantId, PersonId personId) {
            lastTenant = tenantId;
            return List.copyOf(views);
        }

        void clear() {
            personExists = false;
            duplicate = false;
            lastActor = null;
            lastTenant = null;
            saved.clear();
            views.clear();
        }
    }

    static class RecordingBusinessRoleAssignedAudit implements BusinessRoleAssignedAudit {

        private final List<BusinessRoleAssignedAuditEntry> entries = new ArrayList<>();

        @Override
        public void record(BusinessRoleAssignedAuditEntry entry) {
            entries.add(entry);
        }

        void clear() {
            entries.clear();
        }
    }
}
