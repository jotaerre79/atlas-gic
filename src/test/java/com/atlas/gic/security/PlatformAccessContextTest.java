package com.atlas.gic.security;

import com.atlas.gic.shared.audit.application.PlatformAccessAudit;
import com.atlas.gic.shared.audit.application.PlatformAccessAuditEntry;
import com.atlas.gic.identity.application.PersonReadRepository;
import com.atlas.gic.identity.application.PersonRegisteredAuditEntry;
import com.atlas.gic.identity.application.PersonRegistrationAudit;
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
import com.atlas.gic.shared.tenancy.application.TenantContextHolder;
import com.atlas.gic.shared.tenancy.domain.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.atlas.gic.shared.security.application.PlatformAuthorities.PLATFORM_ADMIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
@AutoConfigureMockMvc
class PlatformAccessContextTest {

    private static final String TENANT_A = "11111111-1111-1111-1111-111111111111";
    private static final String TENANT_B = "22222222-2222-2222-2222-222222222222";

    private final MockMvc mockMvc;
    private final RecordingPlatformAccessAudit audit;

    @Autowired
    PlatformAccessContextTest(MockMvc mockMvc, RecordingPlatformAccessAudit audit) {
        this.mockMvc = mockMvc;
        this.audit = audit;
    }

    @Test
    void headerCannotGrantPlatformAccess() throws Exception {
        mockMvc.perform(get("/test/security/context")
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_A)
                        .header("X-Platform-Access", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string("tenant=%s;platform=false".formatted(TENANT_A)));
    }

    @Test
    void normalTenantUserCannotReadAnotherTenantContext() throws Exception {
        mockMvc.perform(get("/test/security/tenant-scoped")
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_B))
                .andExpect(status().isForbidden());
    }

    @Test
    void normalTenantUserCannotWriteAnotherTenantContext() throws Exception {
        mockMvc.perform(get("/test/security/tenant-scoped-write")
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A)))
                        .header("X-Tenant-Id", TENANT_B))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingTenantContextIsDeniedForTenantScopedOperation() throws Exception {
        mockMvc.perform(get("/test/security/tenant-scoped")
                        .with(user("tenant-user").authorities(new SimpleGrantedAuthority("TENANT_" + TENANT_A))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousRequestDoesNotGetPlatformAccess() throws Exception {
        mockMvc.perform(get("/test/security/context")
                        .with(anonymous())
                        .header("X-Platform-Access", "true"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void platformAdminAuthorityGrantsPlatformAccess() throws Exception {
        mockMvc.perform(get("/test/security/context")
                        .with(user("platform-admin").authorities(new SimpleGrantedAuthority(PLATFORM_ADMIN))))
                .andExpect(status().isOk())
                .andExpect(content().string("tenant=none;platform=true"));
    }

    @Test
    void platformAdminOperationRecordsAuditTrail() throws Exception {
        audit.clear();

        mockMvc.perform(get("/test/security/platform-operation")
                        .with(user("platform-admin").authorities(new SimpleGrantedAuthority(PLATFORM_ADMIN)))
                        .header("X-Correlation-Id", "corr-1"))
                .andExpect(status().isOk());

        assertThat(audit.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.actor()).isEqualTo("platform-admin");
            assertThat(entry.reason()).isEqualTo("test platform operation");
            assertThat(entry.operation()).isEqualTo("test.platform-operation");
            assertThat(entry.correlationId()).isEqualTo("corr-1");
        });
    }

    @TestConfiguration
    static class SecurityTestConfiguration {

        @Bean
        @Primary
        RecordingPlatformAccessAudit recordingPlatformAccessAudit() {
            return new RecordingPlatformAccessAudit();
        }

        @Bean
        PersonRepository personRepository() {
            return person -> {
            };
        }

        @Bean
        PersonRegistrationAudit personRegistrationAudit() {
            return entry -> {
            };
        }

        @Bean
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
        BusinessRoleAssignedAudit businessRoleAssignedAudit() {
            return entry -> {
            };
        }

        @Bean
        BusinessRoleEndedAudit businessRoleEndedAudit() {
            return entry -> {
            };
        }

        @RestController
        static class SecurityTestController {

            private final TenantContextHolder tenantContext = new TenantContextHolder();
            private final PlatformAccessAudit audit;

            SecurityTestController(PlatformAccessAudit audit) {
                this.audit = audit;
            }

            @GetMapping(value = "/test/security/context", produces = MediaType.TEXT_PLAIN_VALUE)
            String context() {
                return "tenant=%s;platform=%s".formatted(
                        tenantContext.currentTenant().map(Object::toString).orElse("none"),
                        tenantContext.platformAccess());
            }

            @GetMapping("/test/security/tenant-scoped")
            void tenantScoped() {
                if (tenantContext.currentTenant().isEmpty()) {
                    throw new ForbiddenOperationException();
                }
            }

            @GetMapping("/test/security/tenant-scoped-write")
            void tenantScopedWrite() {
                if (tenantContext.currentTenant().isEmpty()) {
                    throw new ForbiddenOperationException();
                }
            }

            @GetMapping("/test/security/platform-operation")
            void platformOperation(org.springframework.security.core.Authentication authentication,
                                   jakarta.servlet.http.HttpServletRequest request) {
                if (!tenantContext.platformAccess()) {
                    throw new ForbiddenOperationException();
                }
                audit.record(new PlatformAccessAuditEntry(
                        authentication.getName(),
                        "test platform operation",
                        "test.platform-operation",
                        request.getHeader("X-Correlation-Id"),
                        Instant.now()));
            }
        }
    }

    static class RecordingPlatformAccessAudit implements PlatformAccessAudit {

        private final List<PlatformAccessAuditEntry> entries = new ArrayList<>();

        @Override
        public void record(PlatformAccessAuditEntry entry) {
            entries.add(entry);
        }

        List<PlatformAccessAuditEntry> entries() {
            return List.copyOf(entries);
        }

        void clear() {
            entries.clear();
        }
    }

    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.FORBIDDEN)
    static class ForbiddenOperationException extends RuntimeException {
    }
}
