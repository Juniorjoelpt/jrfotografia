package br.com.jrfotografia.servico;

import br.com.jrfotografia.config.AppProps;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Cifra segredos guardados no banco (ex.: senha do e-mail) com AES-GCM e chave derivada do segredo da aplicação. */
@Component
public class Cifra {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private final SecretKeySpec chave;

    public Cifra(AppProps props) {
        try {
            byte[] k = MessageDigest.getInstance("SHA-256").digest(("dados|" + props.jwt().segredo()).getBytes(StandardCharsets.UTF_8));
            this.chave = new SecretKeySpec(k, "AES");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String cifrar(String texto) {
        if (texto == null) return null;
        try {
            byte[] iv = new byte[12];
            ALEATORIO.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(128, iv));
            byte[] cifrado = c.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String decifrar(String guardado) {
        if (guardado == null) return null;
        try {
            byte[] tudo = Base64.getDecoder().decode(guardado);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(128, tudo, 0, 12));
            return new String(c.doFinal(tudo, 12, tudo.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível ler a senha guardada. Digite a senha do e-mail de novo.", e);
        }
    }
}
