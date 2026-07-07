package com.example.demo3;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/async-tasks")
public class AsyncTaskController {

    private final AsyncTaskService asyncTaskService;
    private final ConfigurableApplicationContext applicationContext;

    public AsyncTaskController(AsyncTaskService asyncTaskService, ConfigurableApplicationContext applicationContext) {
        this.asyncTaskService = asyncTaskService;
        this.applicationContext = applicationContext;
    }

    @PostMapping
    public Map<String, String> submit(@RequestBody(required = false) Map<String, String> body) {
        String payload = body == null ? "empty" : body.getOrDefault("payload", "empty");
        return Map.of("taskId", asyncTaskService.submit(payload));
    }

    @GetMapping
    public List<TaskState> list() {
        return asyncTaskService.list();
    }

    @PostMapping("/simulate-context-close")
    public Map<String, String> simulateContextClose() {
        Thread shutdownThread = new Thread(applicationContext::close, "simulate-context-close");
        shutdownThread.setDaemon(false);
        shutdownThread.start();
        return Map.of("message", "Spring ApplicationContext close triggered");
    }

    @PostMapping("/simulate-system-exit")
    public Map<String, String> simulateSystemExit() {
        Thread exitThread = new Thread(() -> System.exit(0), "simulate-system-exit");
        exitThread.setDaemon(false);
        exitThread.start();
        return Map.of("message", "System.exit(0) triggered");
    }
}
