package com.atlas.gic.identity.application;

import com.atlas.gic.identity.domain.Person;
import com.atlas.gic.identity.domain.PersonIdentifier;
import com.atlas.gic.identity.domain.PersonName;
import com.atlas.gic.shared.security.application.CurrentActor;
import com.atlas.gic.shared.tenancy.application.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterPersonUseCase {

    private final TenantContext tenantContext;
    private final PersonRepository personRepository;
    private final PersonRegistrationAudit audit;
    private final CurrentActor currentActor;

    public RegisterPersonUseCase(
            TenantContext tenantContext,
            PersonRepository personRepository,
            PersonRegistrationAudit audit,
            CurrentActor currentActor) {
        this.tenantContext = tenantContext;
        this.personRepository = personRepository;
        this.audit = audit;
        this.currentActor = currentActor;
    }

    @Transactional
    public RegisterPersonResult register(RegisterPersonCommand command) {
        var tenantId = tenantContext.currentTenant().orElseThrow(TenantContextRequiredException::new);
        var identifier = command.identifier();
        if (identifier == null) {
            throw new IllegalArgumentException("identifier is required");
        }

        var person = Person.register(
                tenantId,
                new PersonName(command.givenName(), command.middleName(), command.familyName()),
                PersonIdentifier.of(identifier.type(), identifier.value(), identifier.issuer()));

        personRepository.save(person);
        audit.record(new PersonRegisteredAuditEntry(
                currentActor.actor(),
                tenantId,
                person.personId(),
                command.correlationId(),
                null));

        return new RegisterPersonResult(person.personId(), person.status(), person.displayName());
    }
}
