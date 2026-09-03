package com.example.scheduleddemo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionalOnPropertyRegistrationTest {

    @SpringBootTest(properties = {
            "jdjk.forward-sync.enabled=true",
            "demo.forward-sync-cron=0 0 0 1 1 *"
    })
    static class EnabledCase {
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

    @SpringBootTest(properties = {
            "jdjk.forward-sync.enabled=false",
            "demo.forward-sync-cron=0 0 0 1 1 *"
    })
    static class DisabledCase {
        @Autowired
        private ApplicationContext applicationContext;

        @Autowired
        private ScheduledAnnotationBeanPostProcessor scheduledAnnotationBeanPostProcessor;

        @Test
        void doesNotRegisterBeanOrScheduledTaskWhenPropertyIsFalse() {
            assertThat(applicationContext.getBeansOfType(ConditionalForwardSyncJob.class)).isEmpty();
            assertThat(scheduledAnnotationBeanPostProcessor.getScheduledTasks()).isEmpty();
        }
    }

    @SpringBootTest(properties = "demo.forward-sync-cron=0 0 0 1 1 *")
    static class MissingCase {
        @Autowired
        private ApplicationContext applicationContext;

        @Autowired
        private ScheduledAnnotationBeanPostProcessor scheduledAnnotationBeanPostProcessor;

        @Test
        void doesNotRegisterBeanOrScheduledTaskWhenPropertyIsMissing() {
            assertThat(applicationContext.getBeansOfType(ConditionalForwardSyncJob.class)).isEmpty();
            assertThat(scheduledAnnotationBeanPostProcessor.getScheduledTasks()).isEmpty();
        }
    }
}
