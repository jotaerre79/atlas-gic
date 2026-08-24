package com.atlas.gic.shared.security.adapter.spring;

import com.atlas.gic.shared.security.application.PlatformAccessAuthorization;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import static com.atlas.gic.shared.security.application.PlatformAuthorities.PLATFORM_ADMIN;

@Component
public class SecurityContextPlatformAccessAuthorization implements PlatformAccessAuthorization {

    @Override
    public boolean hasPlatformAccess(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> PLATFORM_ADMIN.equals(authority.getAuthority()));
    }
}
