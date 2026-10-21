package br.com.jrfotografia.servico;

import br.com.jrfotografia.config.AppProps;
import br.com.jrfotografia.config.Erros.RegraNegocio;
import br.com.jrfotografia.dominio.EmailEnviado;
import br.com.jrfotografia.dominio.Estudio;
import br.com.jrfotografia.dominio.Repositorios.EmailEnviadoRepo;
import jakarta.annotation.PreDestroy;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Envia e-mails pelo SMTP configurado no estúdio (Gmail com senha de app, Brevo, Outlook…).
 * O envio acontece em segundo plano depois que a operação foi gravada: um e-mail que falha
 * não desfaz nada e fica no histórico com o motivo.
 */
@Service
public class EmailServico {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public record Anexo(String nome, String tipo, byte[] dados) {}

    /** Mensagem pronta (no modo memória fica guardada aqui, para os testes). */
    public record Mensagem(Long estudioId, String para, String assunto, String html, List<Anexo> anexos, String tipo) {}

    private record Smtp(String host, int porta, String usuario, String senha, String seguranca, String remetente, String nomeRemetente,
                        String responderPara) {}

    private final EmailEnviadoRepo historico;
    /** O status do histórico é gravado numa transação própria (o envio roda depois do commit da operação). */
    private final org.springframework.transaction.support.TransactionTemplate novaTransacao;
    private final Cifra cifra;
    private final boolean memoria;
    private final List<Mensagem> caixa = new CopyOnWriteArrayList<>();
    private final ExecutorService fila = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "envio-email");
        t.setDaemon(true);
        return t;
    });

    public EmailServico(EmailEnviadoRepo historico, Cifra cifra, AppProps props, org.springframework.transaction.PlatformTransactionManager tx) {
        this.novaTransacao = new org.springframework.transaction.support.TransactionTemplate(tx);
        this.novaTransacao.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.historico = historico;
        this.cifra = cifra;
        this.memoria = props.email() != null && "memoria".equalsIgnoreCase(props.email().modo());
    }

    @PreDestroy
    void parar() {
        fila.shutdown();
    }

    public static boolean emailValido(String e) {
        return e != null && e.length() <= 200 && EMAIL.matcher(e.trim()).matches();
    }

    public static boolean configurado(Estudio e) {
        return e.getSmtpHost() != null && !e.getSmtpHost().isBlank() && e.getSmtpUsuario() != null && e.getSmtpSenha() != null;
    }

    /** E-mails guardados no modo memória (testes). */
    public List<Mensagem> caixa() {
        return caixa;
    }

    /**
     * Agenda o envio. Não faz nada se os e-mails estiverem desligados, se o endereço for inválido ou se este
     * mesmo e-mail (tipo + referência + destinatário) já foi enviado antes.
     */
    public boolean enviar(Estudio e, String para, String assunto, String html, String tipo, String referencia, List<Anexo> anexos) {
        if (!e.isEmailAtivo() || !configurado(e) || !emailValido(para)) return false;
        String destino = para.trim().toLowerCase();
        if (referencia != null && (historico.existsByTipoAndReferenciaAndParaAndStatus(tipo, referencia, destino, "ENVIADO")
                || historico.existsByTipoAndReferenciaAndParaAndStatus(tipo, referencia, destino, "PENDENTE"))) return false;
        EmailEnviado reg = new EmailEnviado();
        reg.setEstudioId(e.getId());
        reg.setPara(destino);
        reg.setAssunto(assunto.length() > 200 ? assunto.substring(0, 200) : assunto);
        reg.setTipo(tipo);
        reg.setReferencia(referencia);
        reg.setStatus("PENDENTE");
        Long id = historico.save(reg).getId();
        Smtp smtp = smtp(e);
        Mensagem m = new Mensagem(e.getId(), destino, assunto, html, anexos == null ? List.of() : anexos, tipo);
        Runnable tarefa = () -> despachar(id, smtp, m);
        // Só envia depois que a operação foi gravada (se der erro e desfizer, nada é enviado).
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (memoria) tarefa.run(); else fila.submit(tarefa);
                }
            });
        } else if (memoria) {
            tarefa.run();
        } else {
            fila.submit(tarefa);
        }
        return true;
    }

    /** Teste da configuração: envia na hora e devolve o erro, se houver (não precisa estar ligado). */
    public void testar(Estudio e, String para, String assunto, String html) {
        if (!configurado(e)) throw new RegraNegocio("Preencha servidor, usuário e senha antes de testar.");
        if (!emailValido(para)) throw new RegraNegocio("Informe um e-mail válido para receber o teste.");
        Mensagem m = new Mensagem(e.getId(), para.trim(), assunto, html, List.of(), "TESTE");
        try {
            entregar(smtp(e), m);
        } catch (Exception ex) {
            throw new RegraNegocio("Não foi possível enviar: " + explicar(ex));
        }
        registrarDireto(e.getId(), m, "ENVIADO", null);
    }

    private void despachar(Long id, Smtp smtp, Mensagem m) {
        String erro = null;
        try {
            entregar(smtp, m);
        } catch (Exception ex) {
            erro = explicar(ex);
        }
        String falha = erro;
        try {
            novaTransacao.executeWithoutResult(st -> historico.findById(id).ifPresent(reg -> {
                reg.setStatus(falha == null ? "ENVIADO" : "FALHOU");
                reg.setErro(falha);
                reg.setEnviadoEm(falha == null ? LocalDateTime.now() : null);
                historico.save(reg);
            }));
        } catch (RuntimeException ignorado) {
            // histórico é complementar
        }
    }

    private void entregar(Smtp s, Mensagem m) throws Exception {
        if (memoria) {
            caixa.add(m);
            return;
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(s.host());
        sender.setPort(s.porta());
        sender.setUsername(s.usuario());
        sender.setPassword(s.senha());
        sender.setDefaultEncoding("UTF-8");
        Properties p = sender.getJavaMailProperties();
        p.put("mail.smtp.auth", "true");
        p.put("mail.smtp.connectiontimeout", "15000");
        p.put("mail.smtp.timeout", "20000");
        p.put("mail.smtp.writetimeout", "20000");
        if ("SSL".equals(s.seguranca())) {
            p.put("mail.smtp.ssl.enable", "true");
        } else if (!"NENHUMA".equals(s.seguranca())) {
            p.put("mail.smtp.starttls.enable", "true");
            p.put("mail.smtp.starttls.required", "true");
        }
        MimeMessage msg = sender.createMimeMessage();
        // Sempre multipart: versão texto + HTML (e anexos, quando houver).
        MimeMessageHelper h = new MimeMessageHelper(msg, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
        h.setFrom(new InternetAddress(s.remetente(), s.nomeRemetente(), StandardCharsets.UTF_8.name()));
        if (s.responderPara() != null && emailValido(s.responderPara())) h.setReplyTo(s.responderPara());
        h.setTo(m.para());
        h.setSubject(m.assunto());
        h.setText(EmailModelos.textoSimples(m.html()), m.html());
        for (Anexo a : m.anexos()) h.addAttachment(a.nome(), new ByteArrayResource(a.dados()), a.tipo());
        sender.send(msg);
    }

    private Smtp smtp(Estudio e) {
        String remetente = e.getEmailRemetente() != null && !e.getEmailRemetente().isBlank() ? e.getEmailRemetente() : e.getSmtpUsuario();
        int porta = e.getSmtpPorta() != null ? e.getSmtpPorta() : "SSL".equals(e.getSmtpSeguranca()) ? 465 : 587;
        return new Smtp(e.getSmtpHost().trim(), porta, e.getSmtpUsuario().trim(), cifra.decifrar(e.getSmtpSenha()),
                e.getSmtpSeguranca() == null ? "STARTTLS" : e.getSmtpSeguranca(), remetente.trim(), e.getNome(), e.getEmail());
    }

    private void registrarDireto(Long estudioId, Mensagem m, String status, String erro) {
        try {
            EmailEnviado reg = new EmailEnviado();
            reg.setEstudioId(estudioId);
            reg.setPara(m.para());
            reg.setAssunto(m.assunto());
            reg.setTipo(m.tipo());
            reg.setStatus(status);
            reg.setErro(erro);
            reg.setEnviadoEm(LocalDateTime.now());
            historico.save(reg);
        } catch (RuntimeException ignorado) {
            // histórico é complementar
        }
    }

    /** Erro do servidor de e-mail em português, com a dica mais comum. */
    static String explicar(Exception ex) {
        String msg = String.valueOf(ex.getMessage());
        Throwable causa = ex;
        while (causa.getCause() != null) causa = causa.getCause();
        String raiz = String.valueOf(causa.getMessage());
        String tudo = (msg + " " + raiz).toLowerCase();
        String dica;
        if (tudo.contains("authentication") || tudo.contains("535") || tudo.contains("username and password")) {
            dica = "usuário ou senha recusados. No Gmail, use uma senha de app (não a senha normal da conta).";
        } else if (tudo.contains("unknownhost") || tudo.contains("unknown host") || causa instanceof java.net.UnknownHostException) {
            dica = "servidor não encontrado. Confira o endereço do servidor SMTP.";
        } else if (tudo.contains("timed out") || tudo.contains("connect") ) {
            dica = "não conectou ao servidor. Confira servidor, porta e segurança (587 com STARTTLS ou 465 com SSL).";
        } else if (tudo.contains("sender address rejected") || tudo.contains("553") || tudo.contains("550")) {
            dica = "o servidor recusou o remetente. Use o mesmo e-mail da conta ou um remetente verificado no serviço.";
        } else {
            dica = msg.length() > 200 ? msg.substring(0, 200) : msg;
        }
        return dica.length() > 380 ? dica.substring(0, 380) : dica;
    }
}
