package com.atlas.gic.shared.tenancy.application;

import com.atlas.gic.shared.tenancy.domain.TenantId;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public final class TenantContextHolder implements TenantContext {

    private static final ThreadLocal<TenantContextSnapshot> CURRENT = new ThreadLocal<>();

    @Override
    public Optional<TenantId> currentTenant() {
        return Optional.ofNullable(CURRENT.get()).flatMap(TenantContextSnapshot::optionalTenantId);
    }

    @Override
    public boolean platformAccess() {
        return Optional.ofNullable(CURRENT.get()).map(TenantContextSnapshot::platformAccess).orElse(false);
    }

    public static Scope open(TenantContextSnapshot snapshot) {
        CURRENT.set(snapshot);
        return CURRENT::remove;
    }

    public static void clear() {
        CURRENT.remove();
    }

    public interface Scope extends AutoCloseable {

        @Override
        void close();
    }
}
