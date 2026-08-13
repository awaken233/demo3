package com.example;

import com.sun.net.httpserver.HttpServer;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RestTemplateFutureCancelTest {

    private HttpServer server;
    private RestTemplate restTemplate;
    private TrackingRequestFactory requestFactory;
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
        requestFactory = new TrackingRequestFactory(httpClient);
        requestFactory.setConnectTimeout(2_000);
        requestFactory.setReadTimeout(15_000);
        restTemplate = new RestTemplate(requestFactory);
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
        CountDownLatch workerExited = new CountDownLatch(1);
        AtomicLong workerExitAt = new AtomicLong(-1);

        long begin = System.nanoTime();
        Future<?> future = executor.submit(() -> {
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

        assertTrue(requestFactory.awaitRequestCreated(1, TimeUnit.SECONDS));
        Thread.sleep(1_000);

        long cancelAt = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);
        boolean cancelResult = future.cancel(true);
        System.out.println("future.cancel(true) returned " + cancelResult + " at " + cancelAt + " ms");
        System.out.println("future.isCancelled=" + future.isCancelled() + ", future.isDone=" + future.isDone());

        boolean exitedWithinTwoSeconds = workerExited.await(2, TimeUnit.SECONDS);
        System.out.println("worker exited within 2s after cancel: " + exitedWithinTwoSeconds);

        assertTrue(future.isCancelled());
        assertFalse("HTTP request unexpectedly stopped just because Future.cancel(true) interrupted the worker",
                exitedWithinTwoSeconds);

        assertTrue("worker should eventually return after server response/read completes",
                workerExited.await(10, TimeUnit.SECONDS));
        System.out.println("observed worker total lifetime: " + workerExitAt.get() + " ms");

        executor.shutdownNow();
    }

    @Test
    public void explicitApacheRequestAbortPromptlyStopsRestTemplateRequest() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch workerExited = new CountDownLatch(1);
        AtomicLong workerExitAt = new AtomicLong(-1);

        long begin = System.nanoTime();
        Future<?> future = executor.submit(() -> {
            try {
                restTemplate.getForObject("http://127.0.0.1:" + port + "/slow", String.class);
            } catch (Throwable e) {
                System.out.println("worker exception after abort: " + e.getClass().getName() + ": " + e.getMessage());
            } finally {
                workerExitAt.set(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin));
                System.out.println("worker finally after abort at " + workerExitAt.get() + " ms");
                workerExited.countDown();
            }
        });

        assertTrue(requestFactory.awaitRequestCreated(1, TimeUnit.SECONDS));
        Thread.sleep(1_000);

        long abortAt = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);
        boolean aborted = requestFactory.abortCurrentRequest();
        System.out.println("HttpRequestBase.abort() invoked=" + aborted + " at " + abortAt + " ms");

        boolean exitedWithinTwoSeconds = workerExited.await(2, TimeUnit.SECONDS);
        System.out.println("worker exited within 2s after explicit abort: " + exitedWithinTwoSeconds);

        assertTrue("expected to obtain and abort Apache HttpClient request", aborted);
        assertTrue("explicit Apache request abort should promptly unblock RestTemplate", exitedWithinTwoSeconds);
        assertTrue("worker Future should be done after the HTTP request is aborted", future.isDone());

        executor.shutdownNow();
    }

    private static class TrackingRequestFactory extends HttpComponentsClientHttpRequestFactory {
        private final AtomicReference<HttpUriRequest> currentRequest = new AtomicReference<>();
        private final CountDownLatch requestCreated = new CountDownLatch(1);

        TrackingRequestFactory(CloseableHttpClient httpClient) {
            super(httpClient);
        }

        @Override
        protected HttpUriRequest createHttpUriRequest(HttpMethod httpMethod, URI uri) {
            HttpUriRequest request = super.createHttpUriRequest(httpMethod, uri);
            currentRequest.set(request);
            requestCreated.countDown();
            return request;
        }

        boolean awaitRequestCreated(long timeout, TimeUnit unit) throws InterruptedException {
            return requestCreated.await(timeout, unit);
        }

        boolean abortCurrentRequest() {
            HttpUriRequest request = currentRequest.get();
            if (request instanceof HttpRequestBase) {
                ((HttpRequestBase) request).abort();
                return true;
            }
            return false;
        }
    }
}
