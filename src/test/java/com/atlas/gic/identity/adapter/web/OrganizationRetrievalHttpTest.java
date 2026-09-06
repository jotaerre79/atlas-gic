package com.atlas.gic.identity.adapter.web;

import com.atlas.gic.identity.application.DuplicateOrganizationIdentifierException;
import com.atlas.gic.identity.application.OrganizationReadRepository;
import com.atlas.gic.identity.application.OrganizationRegisteredAuditEntry;
import com.atlas.gic.identity.application.OrganizationRegistrationAudit;
import com.atlas.gic.identity.application.OrganizationRepository;
import com.atlas.gic.identity.application.OrganizationSearchItem;
import com.atlas.gic.identity.application.OrganizationSearchPage;
import com.atlas.gic.identity.application.OrganizationView;
import com.atlas.gic.identity.application.PersonReadRepository;
import com.atlas.gic.identity.application.PersonRegisteredAuditEntry;
import com.atlas.gic.identity.application.PersonRegistrationAudit;
import com.atlas.gic.identity.application.PersonRepository;
import com.atlas.gic.identity.application.PersonSearchPage;
import com.atlas.gic.identity.domain.Organization;
import com.atlas.gic.identity.domain.OrganizationId;
import com.atlas.gic.identity.domain.Person;
import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.roles.application.BusinessRoleAssignedAudit;
import com.atlas.gic.roles.application.BusinessRoleAssignmentRepository;
import com.atlas.gic.roles.application.BusinessRoleAssignmentView;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
@AutoConfigureMockMvc
class OrganizationRetrievalHttpTest {

    private static final String TENANT_A = "11111111-1111-1111-1111-111111111111";
    private static final String TENANT_B = "22222222-2222-2222-2222-222222222222";
    private static final String ORGANIZATION_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    private final MockMvc mockMvc;
    private final RecordingOrganizationReadRepository readRepository;
    private final RecordingOrganizationRepository writeRepository;

    @Autowired
    OrganizationRetrievalHttpTest(
            MockMvc mockMvc,
            RecordingOrganizationReadRepository readRepository,
            RecordingOrganizationRepository writeRepository) {
        this.mockMvc = mockMvc;
        this.readRepository = readRepository;
        this.writeRepository = writeRepository;
    }

    @BeforeEach
    void reset() {
        readRepository.clear();
        writeRepository.clear();
    }

    @Test
    void getOrganizationReturnsCurrentTenantOrganizationWithoutTenantIdOrRawRuc() throws Exception {
        readRepository.organization = Optional.of(organizationView(ORGANIZATION_ID));

        mockMvc.perform(get("/api/v1/organizations/{organizationId}", ORGANIZATION_ID)
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(ORGANIZATION_ID))
                .andExpect(jsonPath("$.legalName").value("Atlas Cooperativa"))
                .andExpect(jsonPath("$.tradeName").value("Atlas"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.identifiers[0].type").value("RUC"))
                .andExpect(jsonPath("$.identifiers[0].countryCode").value("PY"))
                .andExpect(jsonPath("$.identifiers[0].maskedValue").value("****3456"))
                .andExpect(jsonPath("$.tenantId").doesNotExist())
                .andExpect(content().string(not(containsString("800123456"))))
                .andExpect(content().string(not(containsString("80012345-6"))));

        assertThat(readRepository.lastTenant.toString()).isEqualTo(TENANT_A);
    }

    @Test
    void rejectsMalformedOrganizationId() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/{organizationId}", "not-a-uuid")
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOrganizationDoesNotRevealCrossTenantExistence() throws Exception {
        readRepository.organization = Optional.empty();

        mockMvc.perform(get("/api/v1/organizations/{organizationId}", ORGANIZATION_ID)
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Organization not found"));
    }

    @Test
    void returnsUnauthorizedForAnonymousSearch() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .with(anonymous())
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsForbiddenWhenTenantIsNotAuthorized() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_B))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchUsesDefaultPaginationAndDoesNotExposeRucValues() throws Exception {
        readRepository.searchItems.add(new OrganizationSearchItem(
                UUID.fromString(ORGANIZATION_ID),
                "Atlas Cooperativa",
                "Atlas",
                "ACTIVE",
                "RUC",
                "PY"));
        readRepository.total = 1;

        mockMvc.perform(get("/api/v1/organizations")
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].organizationId").value(ORGANIZATION_ID))
                .andExpect(jsonPath("$.items[0].legalName").value("Atlas Cooperativa"))
                .andExpect(jsonPath("$.items[0].identifierType").value("RUC"))
                .andExpect(jsonPath("$.items[0].identifierCountryCode").value("PY"))
                .andExpect(jsonPath("$.items[0].maskedValue").doesNotExist())
                .andExpect(jsonPath("$.tenantId").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(content().string(not(containsString("800123456"))));
    }

    @Test
    void searchRejectsInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/organizations")
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A)
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchPassesTrimmedQueryToUseCase() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A)
                        .param("query", "  atlas  ")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk());

        assertThat(readRepository.lastTenant.toString()).isEqualTo(TENANT_A);
        assertThat(readRepository.lastQuery).isEqualTo("atlas");
        assertThat(readRepository.lastPage).isEqualTo(1);
        assertThat(readRepository.lastSize).isEqualTo(10);
    }

    @Test
    void locationEmittedByRegisterOrganizationCanBeFetched() throws Exception {
        var result = mockMvc.perform(post("/api/v1/organizations")
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/organizations/")))
                .andReturn();

        var location = result.getResponse().getHeader("Location");
        assertThat(location).isNotBlank();
        var saved = writeRepository.saved().getFirst();
        readRepository.organization = Optional.of(organizationView(saved.organizationId().toString()));

        mockMvc.perform(get(location)
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(saved.organizationId().toString()));
    }

    private static OrganizationView organizationView(String organizationId) {
        return new OrganizationView(
                UUID.fromString(organizationId),
                "Atlas Cooperativa",
                "Atlas",
                "ACTIVE",
                List.of(new OrganizationView.IdentifierView("RUC", "PY", "****3456")),
                Instant.parse("2026-09-06T00:00:00Z"));
    }

    private String validPayload() {
        return """
                {
                  "legalName": "Atlas Cooperativa",
                  "tradeName": "Atlas",
                  "identifier": {
                    "type": "RUC",
                    "value": "80012345-6",
                    "countryCode": "PY"
                  },
                  "correlationId": "corr-org-http"
                }
                """;
    }

    @TestConfiguration
    static class HttpTestConfiguration {

        @Bean
        @Primary
        RecordingOrganizationReadRepository recordingOrganizationReadRepository() {
            return new RecordingOrganizationReadRepository();
        }

        @Bean
        @Primary
        RecordingOrganizationRepository recordingOrganizationRepository() {
            return new RecordingOrganizationRepository();
        }

        @Bean
        @Primary
        OrganizationRegistrationAudit organizationRegistrationAudit() {
            return entry -> {
            };
        }

        @Bean
        @Primary
        PersonRepository personRepository() {
            return person -> {
            };
        }

        @Bean
        @Primary
        PersonRegistrationAudit personRegistrationAudit() {
            return entry -> {
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

    static class RecordingOrganizationReadRepository implements OrganizationReadRepository {

        private Optional<OrganizationView> organization = Optional.empty();
        private final List<OrganizationSearchItem> searchItems = new ArrayList<>();
        private TenantId lastTenant;
        private String lastQuery;
        private int lastPage = -1;
        private int lastSize = -1;
        private long total;

        @Override
        public Optional<OrganizationView> findById(TenantId tenantId, OrganizationId organizationId) {
            lastTenant = tenantId;
            return organization;
        }

        @Override
        public OrganizationSearchPage search(TenantId tenantId, String query, int page, int size) {
            lastTenant = tenantId;
            lastQuery = query;
            lastPage = page;
            lastSize = size;
            return new OrganizationSearchPage(searchItems, page, size, total);
        }

        void clear() {
            organization = Optional.empty();
            searchItems.clear();
            lastTenant = null;
            lastQuery = null;
            lastPage = -1;
            lastSize = -1;
            total = 0;
        }
    }

    static class RecordingOrganizationRepository implements OrganizationRepository {

        private final List<Organization> saved = new ArrayList<>();

        @Override
        public void save(Organization organization) {
            saved.add(organization);
        }

        List<Organization> saved() {
            return List.copyOf(saved);
        }

        void clear() {
            saved.clear();
        }
    }
}
