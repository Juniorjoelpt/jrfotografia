package br.com.jrfotografia.servico;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.Instant;

/**
 * Códigos de 6 dígitos que mudam a cada 30 segundos (RFC 6238), os mesmos do Google Authenticator,
 * Microsoft Authenticator e similares.
 */
public final class Totp {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    public static final int PERIODO = 30;

    private Totp() {}

    public static long passo(Instant agora) {
        return agora.getEpochSecond() / PERIODO;
    }

    public static String codigo(byte[] segredo, long passo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(segredo, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(passo).array());
            int o = h[h.length - 1] & 0x0F;
            int bin = ((h[o] & 0x7F) << 24) | ((h[o + 1] & 0xFF) << 16) | ((h[o + 2] & 0xFF) << 8) | (h[o + 3] & 0xFF);
            return String.format("%06d", bin % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Confere o código aceitando um intervalo antes e um depois (relógio do celular adiantado ou atrasado).
     * Devolve o passo usado, ou -1 se não confere ou se o código já foi usado (passo ≤ último aceito).
     */
    public static long verificar(byte[] segredo, String digitado, Instant agora, Long ultimoPasso) {
        if (digitado == null || !digitado.matches("\\d{6}")) return -1;
        long atual = passo(agora);
        for (long p = atual - 1; p <= atual + 1; p++) {
            if (ultimoPasso != null && p <= ultimoPasso) continue;
            if (java.security.MessageDigest.isEqual(codigo(segredo, p).getBytes(), digitado.getBytes())) return p;
        }
        return -1;
    }

    public static String base32(byte[] dados) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : dados) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) sb.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    public static byte[] deBase32(String texto) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0, bits = 0;
        for (char c : texto.toUpperCase().replaceAll("[^A-Z2-7]", "").toCharArray()) {
            buffer = (buffer << 5) | BASE32.indexOf(c);
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
