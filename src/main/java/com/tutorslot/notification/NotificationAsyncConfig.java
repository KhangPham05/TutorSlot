package com.tutorslot.notification;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// A small, bounded pool so a slow or down notification service can never starve the app's other
// threads -- at most 4 notifications in flight at once, 100 more queued behind them.
// @EnableScheduling lives here too, since it only turns on NotificationResendJob's @Scheduled
// method -- the one scheduled thing in this whole project so far.
@Configuration
@EnableAsync
@EnableScheduling
public class NotificationAsyncConfig {

    @Bean
    public ThreadPoolTaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notify-");
        executor.initialize();
        return executor;
    }
}
