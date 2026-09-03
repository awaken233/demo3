package com.example.scheduleddemo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "demo.forward-sync-cron=0/10 * * * * *")
class ForwardSyncJobRegistrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ScheduledAnnotationBeanPostProcessor scheduledAnnotationBeanPostProcessor;

    @Test
    void scheduledMethodIsNotRegisteredWhenDeclaringClassIsNotASpringBean() {
        assertThat(applicationContext.getBeansOfType(ForwardSyncJob.class)).isEmpty();
        assertThatThrownBy(() -> applicationContext.getBean(ForwardSyncJob.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);

        assertThat(scheduledAnnotationBeanPostProcessor.getScheduledTasks()).isEmpty();
    }
}
