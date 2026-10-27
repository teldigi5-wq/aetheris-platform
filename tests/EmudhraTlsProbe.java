// CI-only Java 17+ TLS chain probe; never packaged into the application.
// Deliberately fails closed on DNS failures, timeouts, and unrelated I/O errors.
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathValidatorException;
import java.time.Duration;
import java.util.Locale;
import javax.net.ssl.SSLHandshakeException;

public final class EmudhraTlsProbe {
    private static final String CONTROL = "https://www.python.org/";
    private static final String[] COMPROMISED = {
        "https://dv-g3b-emudhra.we-have-a-problem.com/",
        "https://dv-g3b-aaa-emudhra.we-have-a-problem.com/"
    };
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    private static boolean certificateFailure(Throwable original) {
        for (Throwable cause = original; cause != null; cause = cause.getCause()) {
            if (cause instanceof CertPathBuilderException ||
                cause instanceof CertPathValidatorException) {
                return true;
            }
            if (cause instanceof SSLHandshakeException) {
                String message = String.valueOf(cause.getMessage()).toLowerCase(Locale.ROOT);
                if (message.contains("certificate") ||
                    message.contains("pkix") ||
                    message.contains("trust anchor")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void fetch(String url, boolean mustReject) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(14))
            .GET()
            .build();
        try {
            HttpResponse<Void> response = CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            if (mustReject) {
                throw new IllegalStateException(
                    "VULNERABLE: JVM accepted a compromised eMudhra chain: " + url +
                    " (HTTP " + response.statusCode() + ")");
            }
            System.out.println("PASS valid HTTPS control (HTTP " + response.statusCode() + ") " + url);
        } catch (IOException exception) {
            if (mustReject && certificateFailure(exception)) {
                System.out.println("PASS JVM rejected compromised certificate chain: " + url);
                return;
            }
            throw new IllegalStateException(
                "INCONCLUSIVE: unexpected I/O failure; do not count as TLS rejection: " + url,
                exception);
        }
    }

    public static void main(String[] args) throws Exception {
        fetch(CONTROL, false);
        for (String url : COMPROMISED) {
            fetch(url, true);
        }
    }
}
