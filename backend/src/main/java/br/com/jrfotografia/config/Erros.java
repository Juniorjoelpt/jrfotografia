package br.com.jrfotografia.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;
import java.util.stream.Collectors;

/** Erros de negócio e respostas de erro em português, no formato {"erro": "..."}. */
@RestControllerAdvice
public class Erros {

    public static class NaoEncontrado extends RuntimeException {
        public NaoEncontrado(String msg) { super(msg); }
    }

    public static class RegraNegocio extends RuntimeException {
        public RegraNegocio(String msg) { super(msg); }
    }

    public static class NaoAutorizado extends RuntimeException {
        public NaoAutorizado(String msg) { super(msg); }
    }

    public static class Proibido extends RuntimeException {
        public Proibido(String msg) { super(msg); }
    }

    public static class MuitasTentativas extends RuntimeException {
        public MuitasTentativas(String msg) { super(msg); }
    }

    @ExceptionHandler(MuitasTentativas.class)
    ResponseEntity<Map<String, Object>> muitas(MuitasTentativas e) {
        return resposta(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler(NaoEncontrado.class)
    ResponseEntity<Map<String, Object>> naoEncontrado(NaoEncontrado e) {
        return resposta(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(RegraNegocio.class)
    ResponseEntity<Map<String, Object>> regra(RegraNegocio e) {
        return resposta(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(NaoAutorizado.class)
    ResponseEntity<Map<String, Object>> naoAutorizado(NaoAutorizado e) {
        return resposta(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler({Proibido.class, AccessDeniedException.class})
    ResponseEntity<Map<String, Object>> proibido(RuntimeException e) {
        String msg = e instanceof Proibido ? e.getMessage() : "Seu perfil não tem acesso a esta área.";
        return resposta(HttpStatus.FORBIDDEN, msg);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validacao(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return resposta(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, Object>> tamanho(MaxUploadSizeExceededException e) {
        return resposta(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo grande demais. Envie fotos de até 40 MB cada.");
    }

    private ResponseEntity<Map<String, Object>> resposta(HttpStatus status, String msg) {
        return ResponseEntity.status(status).body(Map.of("erro", msg));
    }
}
