package com.atlas.gic.shared.audit.adapter.noop;

import com.atlas.gic.shared.audit.application.PlatformAccessAudit;
import com.atlas.gic.shared.audit.application.PlatformAccessAuditEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NoopPlatformAccessAudit implements PlatformAccessAudit {

    private static final Logger log = LoggerFactory.getLogger(NoopPlatformAccessAudit.class);

    @Override
    public void record(PlatformAccessAuditEntry entry) {
        log.info("platform_access actor={} operation={} reason={} correlationId={}",
                entry.actor(), entry.operation(), entry.reason(), entry.correlationId());
    }
}
