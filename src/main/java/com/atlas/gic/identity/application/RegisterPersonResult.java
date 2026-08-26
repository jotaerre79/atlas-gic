package com.atlas.gic.identity.application;

import com.atlas.gic.identity.domain.IdentityStatus;
import com.atlas.gic.identity.domain.PersonId;

public record RegisterPersonResult(PersonId personId, IdentityStatus status, String displayName) {
}
