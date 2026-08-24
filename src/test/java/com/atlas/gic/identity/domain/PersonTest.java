package com.atlas.gic.identity.domain;

import com.atlas.gic.shared.tenancy.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonTest {

    @Test
    void createsValidPerson() {
        var tenantId = TenantId.of(UUID.randomUUID());
        var person = Person.register(
                tenantId,
                new PersonName("Juan", "Carlos", "Perez"),
                PersonIdentifier.of("ci", "1.234.567", "py"));

        assertThat(person.personId()).isNotNull();
        assertThat(person.tenantId()).isEqualTo(tenantId);
        assertThat(person.status()).isEqualTo(IdentityStatus.ACTIVE);
        assertThat(person.displayName()).isEqualTo("Juan Carlos Perez");
        assertThat(person.identifier().type()).isEqualTo("CI");
        assertThat(person.identifier().normalizedValue()).isEqualTo("1234567");
    }

    @Test
    void rejectsMissingRequiredName() {
        assertThatThrownBy(() -> new PersonName(" ", null, "Perez"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("givenName");

        assertThatThrownBy(() -> new PersonName("Juan", null, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("familyName");
    }

    @Test
    void rejectsInvalidIdentifier() {
        assertThatThrownBy(() -> PersonIdentifier.of("CI", " - ", "PY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identifier.value");
    }
}
