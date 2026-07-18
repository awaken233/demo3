package com.java.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import okhttp3.Headers;
import okhttp3.Response;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/**
 * 微信支付 APIv3 工具类。
 *
 * 当前示例仅保留 JSAPI 下单直接依赖的能力：
 * JSON 序列化、密钥加载、请求签名、响应读取、响应验签和 API 异常解析。
 */
public final class WXPayUtility {

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    private static final char[] SYMBOLS =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private static final SecureRandom RANDOM = new SecureRandom();

    private WXPayUtility() {
    }

    public static String toJson(Object object) {
        return GSON.toJson(object);
    }

    public static <T> T fromJson(String json, Class<T> classOfT) throws JsonSyntaxException {
        return GSON.fromJson(json, classOfT);
    }

    private static String readKeyStringFromPath(String keyPath) {
        try {
            return new String(Files.readAllBytes(Paths.get(keyPath)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Read key file failed: " + keyPath, e);
        }
    }

    public static PrivateKey loadPrivateKeyFromPath(String keyPath) {
        return loadPrivateKeyFromString(readKeyStringFromPath(keyPath));
    }

    public static PrivateKey loadPrivateKeyFromString(String keyString) {
        String normalized = keyString
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");

        try {
            return KeyFactory.getInstance("RSA").generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(normalized)));
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedOperationException("RSA algorithm is unavailable", e);
        } catch (InvalidKeySpecException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid PKCS#8 private key", e);
        }
    }

    public static PublicKey loadPublicKeyFromPath(String keyPath) {
        return loadPublicKeyFromString(readKeyStringFromPath(keyPath));
    }

    public static PublicKey loadPublicKeyFromString(String keyString) {
        String normalized = keyString
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");

        try {
            return KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(normalized)));
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedOperationException("RSA algorithm is unavailable", e);
        } catch (InvalidKeySpecException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid X.509 public key", e);
        }
    }

    public static String createNonce(int length) {
        char[] buffer = new char[length];
        for (int i = 0; i < length; i++) {
            buffer[i] = SYMBOLS[RANDOM.nextInt(SYMBOLS.length)];
        }
        return new String(buffer);
    }

    public static String sign(String message, String algorithm, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance(algorithm);
            signature.initSign(privateKey);
            signature.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedOperationException(
                    "The current Java environment does not support " + algorithm, e);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException(
                    algorithm + " signature uses an illegal private key", e);
        } catch (SignatureException e) {
            throw new IllegalStateException("Sign message failed", e);
        }
    }

    public static boolean verify(
            String message,
            String signatureValue,
            String algorithm,
            PublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance(algorithm);
            signature.initVerify(publicKey);
            signature.update(message.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signatureValue));
        } catch (SignatureException | IllegalArgumentException e) {
            return false;
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("Verify uses an illegal public key", e);
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedOperationException(
                    "The current Java environment does not support " + algorithm, e);
        }
    }

    public static String buildAuthorization(
            String mchid,
            String certificateSerialNo,
            PrivateKey privateKey,
            String method,
            String uri,
            String body) {
        String nonce = createNonce(32);
        long timestamp = Instant.now().getEpochSecond();
        String message = String.format(
                "%s\n%s\n%d\n%s\n%s\n",
                method,
                uri,
                timestamp,
                nonce,
                body == null ? "" : body);

        String signature = sign(message, "SHA256withRSA", privateKey);

        return String.format(
                "WECHATPAY2-SHA256-RSA2048 mchid=\"%s\",nonce_str=\"%s\",signature=\"%s\",timestamp=\"%d\",serial_no=\"%s\"",
                mchid,
                nonce,
                signature,
                timestamp,
                certificateSerialNo);
    }

    public static String extractBody(Response response) {
        if (response.body() == null) {
            return "";
        }
        try {
            return response.body().string();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Read response body failed, status: " + response.code(), e);
        }
    }

    public static void validateResponse(
            String wechatPayPublicKeyId,
            PublicKey wechatPayPublicKey,
            Headers headers,
            String body) {
        String timestamp = headers.get("Wechatpay-Timestamp");
        String requestId = headers.get("Request-ID");

        try {
            Instant responseTime = Instant.ofEpochSecond(Long.parseLong(timestamp));
            if (Duration.between(responseTime, Instant.now()).abs().toMinutes() >= 5) {
                throw new IllegalArgumentException(String.format(
                        "Validate response failed, timestamp[%s] is expired, request-id[%s]",
                        timestamp,
                        requestId));
            }
        } catch (DateTimeException | NumberFormatException | NullPointerException e) {
            throw new IllegalArgumentException(String.format(
                    "Validate response failed, timestamp[%s] is invalid, request-id[%s]",
                    timestamp,
                    requestId), e);
        }

        String remoteSerial = headers.get("Wechatpay-Serial");
        if (!Objects.equals(remoteSerial, wechatPayPublicKeyId)) {
            throw new IllegalArgumentException(String.format(
                    "Validate response failed, invalid Wechatpay-Serial, local[%s], remote[%s]",
                    wechatPayPublicKeyId,
                    remoteSerial));
        }

        String signature = headers.get("Wechatpay-Signature");
        String nonce = headers.get("Wechatpay-Nonce");
        String message = String.format(
                "%s\n%s\n%s\n",
                timestamp,
                nonce,
                body == null ? "" : body);

        if (!verify(message, signature, "SHA256withRSA", wechatPayPublicKey)) {
            throw new IllegalArgumentException(String.format(
                    "Validate response failed, WeChat Pay signature is incorrect, request-id[%s]",
                    requestId));
        }
    }

    public static final class ApiException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final int statusCode;
        private final String body;
        private final Headers headers;
        private final String errorCode;
        private final String errorMessage;

        public ApiException(int statusCode, String body, Headers headers) {
            super(String.format(
                    "微信支付 API 访问失败，statusCode=[%s], body=[%s], headers=[%s]",
                    statusCode,
                    body,
                    headers));
            this.statusCode = statusCode;
            this.body = body;
            this.headers = headers;

            String parsedCode = null;
            String parsedMessage = null;
            if (body != null && !body.isEmpty()) {
                try {
                    JsonObject jsonObject = GSON.fromJson(body, JsonObject.class);
                    JsonElement code = jsonObject.get("code");
                    JsonElement message = jsonObject.get("message");
                    parsedCode = code == null ? null : code.getAsString();
                    parsedMessage = message == null ? null : message.getAsString();
                } catch (JsonSyntaxException ignored) {
                    // 非 JSON 错误应答保留原始 body。
                }
            }
            this.errorCode = parsedCode;
            this.errorMessage = parsedMessage;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getBody() {
            return body;
        }

        public Headers getHeaders() {
            return headers;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
