package br.com.jrfotografia.api;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.text.Normalizer;

/** Resposta de PDF aberta no navegador (inline), com nome de arquivo legível ao salvar. */
final class Pdfs {

    private Pdfs() {}

    static ResponseEntity<byte[]> resposta(byte[] corpo, String nome) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(nome).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_PDF)
                .body(corpo);
    }

    static String nomeArquivo(String texto) {
        if (texto == null) return "arquivo";
        String s = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s.isEmpty() ? "arquivo" : s;
    }
}
