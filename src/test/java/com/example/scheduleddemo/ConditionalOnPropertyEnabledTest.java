package com.example.scheduleddemo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "jdjk.forward-sync.enabled=true",
        "demo.forward-sync-cron=0 0 0 1 1 *"
})
class ConditionalOnPropertyEnabledTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ScheduledAnnotationBeanPostProcessor scheduledAnnotationBeanPostProcessor;

    @Test
    void registersBeanAndScheduledTaskWhenPropertyIsTrue() {
        assertThat(applicationContext.getBeansOfType(ConditionalForwardSyncJob.class)).hasSize(1);
        assertThat(scheduledAnnotationBeanPostProcessor.getScheduledTasks()).hasSize(1);
    }
}
