package com.example.scheduleddemo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"demo.forward-sync-cron=0 0 0 1 1 *"})
class ConditionalOnPropertyMissingTest {

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    ScheduledAnnotationBeanPostProcessor scheduledAnnotationBeanPostProcessor;

    @Test
    void propertyMissingMeansConditionDoesNotMatch() {
        assertThat(applicationContext.getBeansOfType(ConditionalForwardSyncJob.class)).isEmpty();
        assertThat(scheduledAnnotationBeanPostProcessor.getScheduledTasks()).isEmpty();
    }
}
