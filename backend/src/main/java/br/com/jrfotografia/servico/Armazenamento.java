package br.com.jrfotografia.servico;

import br.com.jrfotografia.config.AppProps;
import br.com.jrfotografia.config.Erros.NaoEncontrado;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Guarda os arquivos numa pasta local, em chaves como "1/trabalhos/7/abc.jpg".
 * Mantém a mesma interface que um armazenamento de objetos (Cloudflare R2, Backblaze B2)
 * terá depois: trocar a implementação não muda o resto do sistema.
 */
@Service
public class Armazenamento {

    private final Path raiz;

    public Armazenamento(AppProps props) {
        this.raiz = Path.of(props.arquivos().pasta()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(raiz);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível criar a pasta de arquivos " + raiz, e);
        }
    }

    public void gravar(String chave, byte[] conteudo) {
        Path destino = resolver(chave);
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, conteudo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void gravar(String chave, InputStream conteudo) {
        Path destino = resolver(chave);
        try {
            Files.createDirectories(destino.getParent());
            Files.copy(conteudo, destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path caminho(String chave) {
        Path p = resolver(chave);
        if (!Files.exists(p)) throw new NaoEncontrado("Arquivo não encontrado");
        return p;
    }

    public void apagar(String chave) {
        if (chave == null) return;
        try {
            Files.deleteIfExists(resolver(chave));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path resolver(String chave) {
        Path p = raiz.resolve(chave).normalize();
        if (!p.startsWith(raiz)) throw new IllegalArgumentException("Chave de arquivo inválida");
        return p;
    }
}
