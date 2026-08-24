package com.atlas.gic.identity.application;

public class TenantContextRequiredException extends RuntimeException {

    public TenantContextRequiredException() {
        super("authorized tenant context is required");
    }
}
