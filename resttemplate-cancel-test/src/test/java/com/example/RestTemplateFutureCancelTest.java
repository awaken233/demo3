package com.example;

import com.sun.net.httpserver.HttpServer;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RestTemplateFutureCancelTest {

    private HttpServer server;
    private RestTemplate restTemplate;
    private int port;

    @Before
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(8_000);
                byte[] body = "ok".getBytes("UTF-8");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        CloseableHttpClient httpClient = HttpClients.custom().build();
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(15_000);
        restTemplate = new RestTemplate(factory);
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void futureCancelTrueDoesNotPromptlyAbortRestTemplateRequest() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch requestStarted = new CountDownLatch(1);
        CountDownLatch workerExited = new CountDownLatch(1);
        AtomicLong workerExitAt = new AtomicLong(-1);

        long begin = System.nanoTime();
        Future<?> future = executor.submit(() -> {
            requestStarted.countDown();
            try {
                restTemplate.getForObject("http://127.0.0.1:" + port + "/slow", String.class);
            } catch (Throwable e) {
                System.out.println("worker exception: " + e.getClass().getName() + ": " + e.getMessage());
                System.out.println("worker interrupted flag in catch: " + Thread.currentThread().isInterrupted());
            } finally {
                workerExitAt.set(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin));
                System.out.println("worker finally at " + workerExitAt.get() + " ms");
                workerExited.countDown();
            }
        });

        assertTrue(requestStarted.await(1, TimeUnit.SECONDS));
        Thread.sleep(1_000);

        long cancelAt = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);
        boolean cancelResult = future.cancel(true);
        System.out.println("future.cancel(true) returned " + cancelResult + " at " + cancelAt + " ms");
        System.out.println("future.isCancelled=" + future.isCancelled() + ", future.isDone=" + future.isDone());

        boolean exitedWithinTwoSeconds = workerExited.await(2, TimeUnit.SECONDS);
        System.out.println("worker exited within 2s after cancel: " + exitedWithinTwoSeconds);

        // The experiment's key assertion: Future is logically cancelled immediately,
        // but RestTemplate + Apache HttpClient classic request keeps the worker blocked.
        assertTrue(future.isCancelled());
        assertFalse("HTTP request unexpectedly stopped just because Future.cancel(true) interrupted the worker",
                exitedWithinTwoSeconds);

        // Let the slow server finish, proving the worker eventually returns only when I/O completes.
        assertTrue("worker should eventually return after server response/read completes",
                workerExited.await(10, TimeUnit.SECONDS));
        System.out.println("observed worker total lifetime: " + workerExitAt.get() + " ms");

        executor.shutdownNow();
    }
}
