package com.example.scheduleddemo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "jdjk.forward-sync",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class ConditionalForwardSyncJob {

    @Scheduled(cron = "${demo.forward-sync-cron}")
    public void syncJdPendingForwardTables() {
        // No-op: this demo only verifies whether the bean and scheduled task are registered.
    }
}
