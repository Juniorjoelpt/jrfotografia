package br.com.jrfotografia.api;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

/** Respostas de arquivo: imagens para exibir e fotos para baixar. */
final class Respostas {

    private Respostas() {}

    static ResponseEntity<Resource> imagem(Path caminho, boolean publica) {
        CacheControl cache = publica
                ? CacheControl.maxAge(Duration.ofDays(7)).cachePublic()
                : CacheControl.maxAge(Duration.ofHours(1)).cachePrivate();
        return ResponseEntity.ok()
                .cacheControl(cache)
                .contentType(tipo(caminho))
                .body(new FileSystemResource(caminho));
    }

    static ResponseEntity<Resource> download(Path caminho, String nome) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nome, StandardCharsets.UTF_8).build().toString())
                .contentType(tipo(caminho))
                .body(new FileSystemResource(caminho));
    }

    private static MediaType tipo(Path p) {
        return p.toString().toLowerCase().endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
    }
}
