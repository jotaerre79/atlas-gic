package com.atlas.gic.identity.adapter.web;

import com.atlas.gic.identity.application.DuplicatePersonIdentifierException;
import com.atlas.gic.identity.application.PersonRegisteredAuditEntry;
import com.atlas.gic.identity.application.PersonRegistrationAudit;
import com.atlas.gic.identity.application.PersonReadRepository;
import com.atlas.gic.identity.application.PersonRepository;
import com.atlas.gic.identity.application.PersonSearchPage;
import com.atlas.gic.identity.domain.Person;
import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.roles.application.BusinessRoleAssignedAudit;
import com.atlas.gic.roles.application.BusinessRoleEndedAudit;
import com.atlas.gic.roles.application.BusinessRoleAssignmentRepository;
import com.atlas.gic.roles.application.BusinessRoleAssignmentView;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
@AutoConfigureMockMvc
class RegisterPersonHttpTest {

    private static final String TENANT_A = "11111111-1111-1111-1111-111111111111";
    private static final String TENANT_B = "22222222-2222-2222-2222-222222222222";

    private final MockMvc mockMvc;
    private final RecordingPersonRepository repository;
    private final RecordingPersonRegistrationAudit audit;

    @Autowired
    RegisterPersonHttpTest(MockMvc mockMvc, RecordingPersonRepository repository, RecordingPersonRegistrationAudit audit) {
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
    void returnsCreatedForValidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/persons")
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A)
                        .header("X-Correlation-Id", "corr-http")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "givenName": "Juan",
                                  "familyName": "Perez",
                                  "identifier": {
                                    "type": "CI",
                                    "value": "1234567",
                                    "issuer": "PY"
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/persons/")))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.displayName").value("Juan Perez"));

        assertThat(repository.saved()).singleElement()
                .satisfies(person -> assertThat(person.tenantId().toString()).isEqualTo(TENANT_A));
        assertThat(audit.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.actor()).isEqualTo("tenant-user");
            assertThat(entry.tenantId().toString()).isEqualTo(TENANT_A);
            assertThat(entry.correlationId()).isEqualTo("corr-http");
        });
    }

    @Test
    void returnsBadRequestForInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/v1/persons")
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "givenName": "",
                                  "familyName": "Perez",
                                  "identifier": {
                                    "type": "CI",
                                    "value": "1234567"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsUnauthorizedForAnonymousRequest() throws Exception {
        mockMvc.perform(post("/api/v1/persons")
                        .with(anonymous())
                        .header("X-Tenant-Id", TENANT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsForbiddenWhenTenantIsNotAuthorized() throws Exception {
        mockMvc.perform(post("/api/v1/persons")
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsConflictForDuplicateIdentifier() throws Exception {
        repository.failWithDuplicate = true;

        mockMvc.perform(post("/api/v1/persons")
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Identifier conflict"));
    }

    private String validPayload() {
        return """
                {
                  "givenName": "Juan",
                  "familyName": "Perez",
                  "identifier": {
                    "type": "CI",
                    "value": "1234567",
                    "issuer": "PY"
                  }
                }
                """;
    }

    @TestConfiguration
    static class HttpTestConfiguration {

        @Bean
        @Primary
        RecordingPersonRepository recordingPersonRepository() {
            return new RecordingPersonRepository();
        }

        @Bean
        @Primary
        RecordingPersonRegistrationAudit recordingPersonRegistrationAudit() {
            return new RecordingPersonRegistrationAudit();
        }

        @Bean
        @Primary
        PersonReadRepository personReadRepository() {
            return new PersonReadRepository() {
                @Override
                public Optional<com.atlas.gic.identity.application.PersonView> findById(
                        com.atlas.gic.shared.tenancy.domain.TenantId tenantId,
                        com.atlas.gic.identity.domain.PersonId personId) {
                    return Optional.empty();
                }

                @Override
                public PersonSearchPage search(
                        com.atlas.gic.shared.tenancy.domain.TenantId tenantId,
                        String query,
                        int page,
                        int size) {
                    return new PersonSearchPage(List.of(), page, size, 0);
                }
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
                public List<BusinessRoleAssignmentView> findByPerson(TenantId tenantId, PersonId personId) {
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

    static class RecordingPersonRepository implements PersonRepository {

        private final List<Person> saved = new ArrayList<>();
        private boolean failWithDuplicate;

        @Override
        public void save(Person person) {
            if (failWithDuplicate) {
                throw new DuplicatePersonIdentifierException();
            }
            saved.add(person);
        }

        List<Person> saved() {
            return List.copyOf(saved);
        }

        void clear() {
            saved.clear();
            failWithDuplicate = false;
        }
    }

    static class RecordingPersonRegistrationAudit implements PersonRegistrationAudit {

        private final List<PersonRegisteredAuditEntry> entries = new ArrayList<>();

        @Override
        public void record(PersonRegisteredAuditEntry entry) {
            entries.add(entry);
        }

        List<PersonRegisteredAuditEntry> entries() {
            return List.copyOf(entries);
        }

        void clear() {
            entries.clear();
        }
    }
}
