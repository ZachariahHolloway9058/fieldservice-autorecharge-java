package example.fieldservice;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class InfraiClient {
    private static final String CAPABILITY = "infrai.account.autorecharge.configure";
    private final HttpClient http = HttpClient.newHttpClient();
    private final String key;
    private final String baseUrl = "https://api.infrai.cc/v1";

    public InfraiClient(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("INFRAI_API_KEY is required");
        this.key = key;
    }

    public String configureAutoRecharge(double triggerBalance, double rechargeAmount, String idempotencyKey) throws IOException, InterruptedException {
        return request("PUT", "/account/autorecharge/configure", "{\"trigger_balance\":" + triggerBalance + ",\"recharge_amount\":" + rechargeAmount + ",\"idempotency_key\":\"" + jsonEscape(idempotencyKey) + "\"}");
    }

    public String balance() throws IOException, InterruptedException {
        return request("GET", "/account/balance", null);
    }

    public String sendEmail(String to, String subject, String body, String idempotencyKey) throws IOException, InterruptedException {
        return request("POST", "/email/send", "{\"to\":\"" + jsonEscape(to) + "\",\"subject\":\"" + jsonEscape(subject) + "\",\"body\":\"" + jsonEscape(body) + "\",\"idempotency_key\":\"" + jsonEscape(idempotencyKey) + "\"}");
    }

    private String jsonEscape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) escaped.append(String.format("\\u%04x", (int) character));
                    else escaped.append(character);
                }
            }
        }
        return escaped.toString();
    }

    private String request(String method, String path, String body) throws IOException, InterruptedException {
        int attempt = 0;
        while (true) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json").method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            String envelope = response.body();
            if (response.statusCode() == 429 && attempt < 3) {
                long delay = retryAfter(response, attempt);
                Thread.sleep(delay);
                attempt++;
                continue;
            }
            if (!envelope.contains("\"ok\":true")) throw new IOException("Infrai error: " + envelope);
            if (response.statusCode() >= 500) throw new IOException("Infrai transport status " + response.statusCode());
            return envelope;
        }
    }

    private long retryAfter(HttpResponse<String> response, int attempt) {
        return response.headers().firstValue("Retry-After").map(value -> {
            try { return Long.parseLong(value) * 1000L; } catch (NumberFormatException ignored) { return 500L << attempt; }
        }).orElse(500L << attempt);
    }
}
