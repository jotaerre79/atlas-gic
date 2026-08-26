package com.atlas.gic.identity.adapter.web;

import com.atlas.gic.identity.application.PersonReadRepository;
import com.atlas.gic.identity.application.PersonSearchItem;
import com.atlas.gic.identity.application.PersonSearchPage;
import com.atlas.gic.identity.application.PersonView;
import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.shared.tenancy.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
@AutoConfigureMockMvc
class PersonRetrievalHttpTest {

    private static final String TENANT_A = "11111111-1111-1111-1111-111111111111";
    private static final String TENANT_B = "22222222-2222-2222-2222-222222222222";
    private static final String PERSON_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    private final MockMvc mockMvc;
    private final RecordingPersonReadRepository repository;

    @Autowired
    PersonRetrievalHttpTest(MockMvc mockMvc, RecordingPersonReadRepository repository) {
        this.mockMvc = mockMvc;
        this.repository = repository;
    }

    @BeforeEach
    void reset() {
        repository.clear();
    }

    @Test
    void getPersonReturnsCurrentTenantPerson() throws Exception {
        repository.person = Optional.of(personView(PERSON_ID));

        mockMvc.perform(get("/api/v1/persons/{personId}", PERSON_ID)
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personId").value(PERSON_ID))
                .andExpect(jsonPath("$.displayName").value("Juan Perez"))
                .andExpect(jsonPath("$.identifiers[0].maskedValue").value("***4567"));

        assertThat(repository.lastTenant.toString()).isEqualTo(TENANT_A);
    }

    @Test
    void getPersonDoesNotRevealCrossTenantExistence() throws Exception {
        repository.person = Optional.empty();

        mockMvc.perform(get("/api/v1/persons/{personId}", PERSON_ID)
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Person not found"));
    }

    @Test
    void searchReturnsOnlyAuthorizedTenantResults() throws Exception {
        repository.searchItems.add(new PersonSearchItem(
                UUID.fromString(PERSON_ID),
                "Juan Perez",
                "ACTIVE",
                "CI",
                "PY"));
        repository.total = 1;

        mockMvc.perform(get("/api/v1/persons")
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A)
                        .param("query", "juan")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].personId").value(PERSON_ID))
                .andExpect(jsonPath("$.items[0].displayName").value("Juan Perez"))
                .andExpect(jsonPath("$.items[0].identifierType").value("CI"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total").value(1));

        assertThat(repository.lastTenant.toString()).isEqualTo(TENANT_A);
        assertThat(repository.lastQuery).isEqualTo("juan");
    }

    @Test
    void searchUsesDefaultPagination() throws Exception {
        mockMvc.perform(get("/api/v1/persons")
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void rejectsInvalidPageAndOversizedPageSize() throws Exception {
        mockMvc.perform(get("/api/v1/persons")
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/persons")
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A)
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMalformedPersonId() throws Exception {
        mockMvc.perform(get("/api/v1/persons/{personId}", "not-a-uuid")
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsUnauthorizedForAnonymousSearch() throws Exception {
        mockMvc.perform(get("/api/v1/persons")
                        .with(anonymous())
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsForbiddenWhenTenantIsNotAuthorized() throws Exception {
        mockMvc.perform(get("/api/v1/persons")
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_B))
                .andExpect(status().isForbidden());
    }

    private static PersonView personView(String personId) {
        return new PersonView(
                UUID.fromString(personId),
                "ACTIVE",
                "Juan",
                null,
                "Perez",
                "Juan Perez",
                List.of(new PersonView.IdentifierView("CI", "PY", "***4567")));
    }

    @TestConfiguration
    static class HttpTestConfiguration {

        @Bean
        @Primary
        RecordingPersonReadRepository recordingPersonReadRepository() {
            return new RecordingPersonReadRepository();
        }

        @Bean
        @Primary
        RegisterPersonHttpTest.RecordingPersonRepository recordingPersonRepository() {
            return new RegisterPersonHttpTest.RecordingPersonRepository();
        }

        @Bean
        @Primary
        RegisterPersonHttpTest.RecordingPersonRegistrationAudit recordingPersonRegistrationAudit() {
            return new RegisterPersonHttpTest.RecordingPersonRegistrationAudit();
        }
    }

    static class RecordingPersonReadRepository implements PersonReadRepository {

        private Optional<PersonView> person = Optional.empty();
        private final List<PersonSearchItem> searchItems = new ArrayList<>();
        private TenantId lastTenant;
        private String lastQuery;
        private long total;

        @Override
        public Optional<PersonView> findById(TenantId tenantId, PersonId personId) {
            lastTenant = tenantId;
            return person;
        }

        @Override
        public PersonSearchPage search(TenantId tenantId, String query, int page, int size) {
            lastTenant = tenantId;
            lastQuery = query;
            return new PersonSearchPage(searchItems, page, size, total);
        }

        void clear() {
            person = Optional.empty();
            searchItems.clear();
            lastTenant = null;
            lastQuery = null;
            total = 0;
        }
    }
}
