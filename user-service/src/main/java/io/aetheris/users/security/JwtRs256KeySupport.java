package io.aetheris.users.security;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Strict, provider-neutral PKCS#8 private / X.509 public PEM loader.
 * Keys come only from reviewed operator configuration; never log key input.
 * Review-only RS256 rollout: no JWKS fetch, untrusted kid or runtime fallback.
 */
final class JwtRs256KeySupport {
    private JwtRs256KeySupport() {}

    static PrivateKey privateKey(String pem) {
        byte[] bytes = extract(pem, "PRIVATE KEY");
        try {
            PrivateKey key = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(bytes));
            verifyStrength(key);
            return key;
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Invalid RS256 private key configuration");
        } finally {
            java.util.Arrays.fill(bytes, (byte) 0);
        }
    }

    static PublicKey publicKey(String pem) {
        byte[] bytes = extract(pem, "PUBLIC KEY");
        try {
            PublicKey key = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(bytes));
            verifyStrength(key);
            return key;
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Invalid RS256 public key configuration");
        } finally {
            java.util.Arrays.fill(bytes, (byte) 0);
        }
    }

    private static byte[] extract(String pem, String type) {
        if (pem == null || pem.isEmpty() || pem.length() > 20_000) {
            throw new IllegalArgumentException("Invalid RS256 PEM configuration");
        }
        String normalized = pem.replace("\\n", "\n").trim();
        String begin = "-----BEGIN " + type + "-----";
        String end = "-----END " + type + "-----";
        if (!normalized.startsWith(begin) || !normalized.endsWith(end)) {
            throw new IllegalArgumentException("Invalid RS256 PEM configuration");
        }
        String body = normalized.substring(begin.length(), normalized.length() - end.length())
            .replaceAll("\\s+", "");
        if (body.length() < 300 || body.length() > 16_000 || !body.matches("[A-Za-z0-9+/=]+")) {
            throw new IllegalArgumentException("Invalid RS256 PEM configuration");
        }
        try {
            return Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid RS256 PEM configuration");
        }
    }

    private static void verifyStrength(java.security.Key key) {
        if (!(key instanceof RSAKey rsa)
                || rsa.getModulus().bitLength() < 2048
                || rsa.getModulus().bitLength() > 8192) {
            throw new IllegalArgumentException("RS256 requires reviewed 2048-8192 bit RSA key");
        }
    }
}
