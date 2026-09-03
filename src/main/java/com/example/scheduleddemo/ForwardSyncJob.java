package com.example.scheduleddemo;

import org.springframework.scheduling.annotation.Scheduled;
// import org.springframework.stereotype.Component;

// @Component
public class ForwardSyncJob {

    @Scheduled(cron = "${demo.forward-sync-cron}")
    public void syncJdPendingForwardTables() {
        throw new IllegalStateException("This method should never be scheduled because ForwardSyncJob is not a Spring bean");
    }
}
