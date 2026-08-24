package com.atlas.gic.shared.security.application;

import org.springframework.security.core.Authentication;

public interface PlatformAccessAuthorization {

    boolean hasPlatformAccess(Authentication authentication);
}
