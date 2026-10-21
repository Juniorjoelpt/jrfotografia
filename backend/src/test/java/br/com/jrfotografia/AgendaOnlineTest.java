package br.com.jrfotografia;

import br.com.jrfotografia.dominio.Repositorios.ReservaRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Agendamento online: horários livres, pré-reserva, comprovante, confirmação, remarcação e cancelamento pela cliente. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgendaOnlineTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ReservaRepo reservas;

    String painel;

    @BeforeEach
    void entrar() throws Exception {
        painel = ler(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@teste.local\",\"senha\":\"senha-de-teste-123\"}"))
                .andExpect(status().isOk()).andReturn()).get("token").asText();
        // Atende todos os dias, 8h às 18h: o teste não depende do dia da semana.
        StringBuilder hs = new StringBuilder();
        for (int d = 1; d <= 7; d++) hs.append(d > 1 ? "," : "").append("{\"diaSemana\":").append(d).append(",\"inicio\":\"08:00\",\"fim\":\"18:00\"}");
        mvc.perform(auth(put("/api/estudio/agenda-online")).contentType(MediaType.APPLICATION_JSON).content("""
                {"ativo":true,"antecedenciaHoras":24,"horizonteDias":60,"intervaloMin":30,"passoMin":60,"sinalPercentual":30,
                 "reservaHoras":24,"remarcacaoHoras":48,"remarcacoesMax":1,"reembolsoDias":7,"horarios":[%s]}""".formatted(hs)))
                .andExpect(status().isOk());
    }

    @Test
    void preReservaConfirmacaoRemarcacaoECancelamento() throws Exception {
        LocalDate dia = LocalDate.now().plusDays(25);
        JsonNode agenda = ler(mvc.perform(get("/api/publico/agenda-online")).andExpect(status().isOk()).andReturn());
        assertThat(agenda.get("ativo").asBoolean()).isTrue();
        List<String> nomes = agenda.get("pacotes").findValuesAsText("nome");
        assertThat(nomes).contains("Sessão 10").doesNotContain("Eventos"); // eventos não entram na agenda online
        long pacote = 0;
        for (JsonNode p : agenda.get("pacotes")) if (p.get("nome").asText().equals("Sessão 10")) pacote = p.get("id").asLong();

        assertThat(horas(pacote, dia)).contains("08:00", "09:00", "16:00").doesNotContain("17:00"); // 2 h de ensaio até as 18 h

        // Pré-reserva às 8h: segura 8h-10h + 30 min de folga
        JsonNode r = ler(mvc.perform(reserva(pacote, dia.atTime(8, 0), "Bianca Teste", "10.1.0.1"))
                .andExpect(status().isCreated()).andReturn());
        assertThat(r.get("status").asText()).isEqualTo("PENDENTE");
        assertThat(r.get("valorSinal").decimalValue()).isEqualByComparingTo("60.00"); // 30% de R$ 200
        String token = token(r);
        assertThat(horas(pacote, dia)).doesNotContain("08:00", "09:00", "10:00").contains("11:00");
        mvc.perform(reserva(pacote, dia.atTime(9, 0), "Outra", "10.1.0.2")).andExpect(status().isUnprocessableEntity());

        // Comprovante: arquivo falso é recusado; PNG de verdade é aceito
        mvc.perform(multipart("/api/publico/reservas/" + token + "/comprovante")
                        .file(new MockMultipartFile("arquivo", "x.jpg", "image/jpeg", "nao e imagem".getBytes())))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(multipart("/api/publico/reservas/" + token + "/comprovante").file(png()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.comprovanteEnviado").value(true));

        // Painel: aparece na lista, na agenda e o comprovante abre
        JsonNode lista = ler(mvc.perform(auth(get("/api/reservas"))).andExpect(status().isOk()).andReturn());
        long id = 0;
        for (JsonNode x : lista) if (x.get("token").asText().equals(token)) id = x.get("id").asLong();
        assertThat(id).isPositive();
        mvc.perform(auth(get("/api/reservas/" + id + "/comprovante"))).andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
        JsonNode eventos = ler(mvc.perform(auth(get("/api/agenda").param("de", dia.toString()).param("ate", dia.toString()))).andReturn());
        assertThat(eventos.findValuesAsText("tipo")).contains("RESERVA");

        // Confirma: vira cliente + trabalho, com o sinal pago e o restante no dia do ensaio
        JsonNode conf = ler(mvc.perform(auth(post("/api/reservas/" + id + "/confirmacao"))).andExpect(status().isOk()).andReturn());
        long trabalho = conf.get("trabalhoId").asLong();
        JsonNode t = ler(mvc.perform(auth(get("/api/trabalhos/" + trabalho))).andReturn());
        assertThat(t.get("cliente").get("nome").asText()).isEqualTo("Bianca Teste");
        assertThat(t.get("cliente").get("telefone").asText()).matches("989\\d{8}"); // só dígitos, sem máscara
        assertThat(t.get("parcelas")).hasSize(2);
        assertThat(t.get("parcelas").get(0).get("pagoEm").asText()).isEqualTo(LocalDate.now().toString());
        assertThat(t.get("parcelas").get(1).get("valor").decimalValue()).isEqualByComparingTo("140.00");
        assertThat(t.get("parcelas").get(1).get("vencimento").asText()).isEqualTo(dia.toString());
        assertThat(t.get("duracaoMin").asInt()).isEqualTo(120);
        String area = t.get("areaCliente").asText();
        assertThat(ler(mvc.perform(get("/api/publico/reservas/" + token)).andReturn()).get("areaCliente").asText()).isEqualTo(area);
        mvc.perform(auth(post("/api/reservas/" + id + "/confirmacao"))).andExpect(status().isOk()); // confirmar de novo não duplica

        // Área da cliente: pode remarcar (faltam 25 dias), sinal volta se cancelar (mais de 7 dias)
        JsonNode ac = ler(mvc.perform(get("/api/publico/cliente/" + area)).andExpect(status().isOk()).andReturn());
        assertThat(ac.get("regras").get("podeRemarcar").asBoolean()).isTrue();
        assertThat(ac.get("regras").get("reembolsaSinal").asBoolean()).isTrue();
        // Remarcar: o próprio horário conta como livre; vai para o dia seguinte às 10h
        JsonNode vagasRem = ler(mvc.perform(get("/api/publico/cliente/" + area + "/vagas").param("de", dia.toString()).param("ate", dia.toString())).andReturn());
        assertThat(vagasRem.toString()).contains("08:00");
        LocalDateTime novo = dia.plusDays(1).atTime(10, 0);
        JsonNode rem = ler(mvc.perform(post("/api/publico/cliente/" + area + "/remarcar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"inicio\":\"" + novo + "\"}")).andExpect(status().isOk()).andReturn());
        assertThat(rem.get("data").asText()).startsWith(novo.toString().substring(0, 16));
        assertThat(rem.get("regras").get("podeRemarcar").asBoolean()).isFalse(); // só uma remarcação sem custo
        t = ler(mvc.perform(auth(get("/api/trabalhos/" + trabalho))).andReturn());
        assertThat(t.get("parcelas").get(1).get("vencimento").asText()).isEqualTo(novo.toLocalDate().toString());
        mvc.perform(post("/api/publico/cliente/" + area + "/remarcar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"inicio\":\"" + dia.plusDays(2).atTime(10, 0) + "\"}")).andExpect(status().isUnprocessableEntity());

        // Cancela com mais de 7 dias: sinal entra em Contas a pagar como devolução
        JsonNode can = ler(mvc.perform(post("/api/publico/cliente/" + area + "/cancelar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Mudança de planos\"}")).andExpect(status().isOk()).andReturn());
        assertThat(can.get("status").asText()).isEqualTo("CANCELADO");
        JsonNode desp = ler(mvc.perform(auth(get("/api/despesas"))).andReturn());
        assertThat(desp.toString()).contains("Devolver sinal · Bianca Teste");
        t = ler(mvc.perform(auth(get("/api/trabalhos/" + trabalho))).andReturn());
        assertThat(t.get("canceladoPor").asText()).isEqualTo("CLIENTE");
        // Horário liberado de novo
        assertThat(horas(pacote, novo.toLocalDate())).contains("10:00");
    }

    @Test
    void cancelarPertoDoEnsaioNaoDevolveSinalERemarcarPrecisaDeAntecedencia() throws Exception {
        long pacote = pacote("Sessão 10");
        LocalDate dia = LocalDate.now().plusDays(3);
        String token = token(ler(mvc.perform(reserva(pacote, dia.atTime(14, 0), "Carla Perto", "10.1.0.3"))
                .andExpect(status().isCreated()).andReturn()));
        long id = idDa(token);
        long trabalho = ler(mvc.perform(auth(post("/api/reservas/" + id + "/confirmacao"))).andReturn()).get("trabalhoId").asLong();
        String area = ler(mvc.perform(auth(get("/api/trabalhos/" + trabalho))).andReturn()).get("areaCliente").asText();
        JsonNode ac = ler(mvc.perform(get("/api/publico/cliente/" + area)).andReturn());
        assertThat(ac.get("regras").get("reembolsaSinal").asBoolean()).isFalse();
        assertThat(ac.get("regras").get("textoCancelamento").asText()).contains("não é devolvido");
        int antes = ler(mvc.perform(auth(get("/api/despesas"))).andReturn()).size();
        mvc.perform(post("/api/publico/cliente/" + area + "/cancelar").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        assertThat(ler(mvc.perform(auth(get("/api/despesas"))).andReturn()).size()).isEqualTo(antes);
    }

    @Test
    void preReservaVencidaLiberaOHorarioERecusaTambem() throws Exception {
        long pacote = pacote("Sessão 20");
        LocalDate dia = LocalDate.now().plusDays(30);
        String token = token(ler(mvc.perform(reserva(pacote, dia.atTime(8, 0), "Dani Vence", "10.1.0.4")).andReturn()));
        assertThat(horas(pacote, dia)).doesNotContain("08:00");
        var r = reservas.findByToken(token).orElseThrow();
        r.setExpiraEm(LocalDateTime.now().minusMinutes(1));
        reservas.save(r);
        assertThat(horas(pacote, dia)).contains("08:00");
        assertThat(ler(mvc.perform(get("/api/publico/reservas/" + token)).andReturn()).get("status").asText()).isEqualTo("EXPIRADA");
        mvc.perform(multipart("/api/publico/reservas/" + token + "/comprovante").file(png())).andExpect(status().isUnprocessableEntity());

        String outro = token(ler(mvc.perform(reserva(pacote, dia.atTime(14, 0), "Eva Recusa", "10.1.0.5")).andReturn()));
        mvc.perform(auth(post("/api/reservas/" + idDa(outro) + "/recusa")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Data reservada para casamento\"}")).andExpect(status().isOk());
        JsonNode pub = ler(mvc.perform(get("/api/publico/reservas/" + outro)).andReturn());
        assertThat(pub.get("status").asText()).isEqualTo("RECUSADA");
        assertThat(pub.get("motivoRecusa").asText()).contains("casamento");
        assertThat(horas(pacote, dia)).contains("14:00");
    }

    @Test
    void limiteDePreReservasPorAparelhoEAgendaDesligada() throws Exception {
        long pacote = pacote("Sessão 10");
        LocalDate dia = LocalDate.now().plusDays(40);
        for (int i = 0; i < 4; i++) {
            mvc.perform(reserva(pacote, dia.plusDays(i).atTime(8, 0), "Spam " + i, "10.9.9.9")).andExpect(status().isCreated());
        }
        mvc.perform(reserva(pacote, dia.plusDays(5).atTime(8, 0), "Spam 5", "10.9.9.9")).andExpect(status().isTooManyRequests());

        mvc.perform(auth(put("/api/estudio/agenda-online")).contentType(MediaType.APPLICATION_JSON).content("""
                {"ativo":false,"antecedenciaHoras":24,"horizonteDias":60,"intervaloMin":30,"passoMin":60,"sinalPercentual":30,
                 "reservaHoras":24,"remarcacaoHoras":48,"remarcacoesMax":1,"reembolsoDias":7,"horarios":[]}""")).andExpect(status().isOk());
        assertThat(ler(mvc.perform(get("/api/publico/agenda-online")).andReturn()).get("ativo").asBoolean()).isFalse();
        mvc.perform(reserva(pacote, dia.plusDays(6).atTime(8, 0), "Fora", "10.1.0.6")).andExpect(status().isUnprocessableEntity());
        mvc.perform(auth(put("/api/estudio/agenda-online")).contentType(MediaType.APPLICATION_JSON).content("""
                {"ativo":true,"antecedenciaHoras":24,"horizonteDias":60,"intervaloMin":30,"passoMin":60,"sinalPercentual":30,
                 "reservaHoras":24,"remarcacaoHoras":48,"remarcacoesMax":1,"reembolsoDias":7,"horarios":[]}"""))
                .andExpect(status().isUnprocessableEntity()); // ligar sem horários não pode
    }

    // ------------------------------------------------------------------ apoio
    private List<String> horas(long pacote, LocalDate dia) throws Exception {
        JsonNode v = ler(mvc.perform(get("/api/publico/agenda-online/vagas").param("pacote", String.valueOf(pacote))
                .param("de", dia.toString()).param("ate", dia.toString())).andExpect(status().isOk()).andReturn());
        List<String> out = new ArrayList<>();
        if (v.size() > 0) for (JsonNode h : v.get(0).get("horarios")) out.add(h.asText().substring(0, 5));
        return out;
    }

    private MockHttpServletRequestBuilder reserva(long pacote, LocalDateTime inicio, String nome, String ip) {
        return post("/api/publico/reservas").header("X-Real-IP", ip).contentType(MediaType.APPLICATION_JSON).content("""
                {"pacoteId":%d,"inicio":"%s","nome":"%s","telefone":"(98) 9%04d-%04d","tipo":"Ensaio feminino","consentimento":true}"""
                .formatted(pacote, inicio, nome, Math.abs(nome.hashCode()) % 10000, Math.abs(ip.hashCode()) % 10000));
    }

    private String token(JsonNode publica) throws Exception {
        // A resposta pública não traz o token (ele está só no link); busca pelo nome na lista do painel.
        String nome = publica.get("nome").asText();
        for (JsonNode x : ler(mvc.perform(auth(get("/api/reservas"))).andReturn())) if (x.get("nome").asText().equals(nome)) return x.get("token").asText();
        throw new AssertionError("reserva não encontrada: " + nome);
    }

    private long idDa(String token) throws Exception {
        for (JsonNode x : ler(mvc.perform(auth(get("/api/reservas"))).andReturn())) if (x.get("token").asText().equals(token)) return x.get("id").asLong();
        throw new AssertionError(token);
    }

    private long pacote(String nome) throws Exception {
        for (JsonNode p : ler(mvc.perform(auth(get("/api/pacotes"))).andReturn())) if (p.get("nome").asText().equals(nome)) return p.get("id").asLong();
        throw new AssertionError(nome);
    }

    private static MockMultipartFile png() throws Exception {
        var img = new java.awt.image.BufferedImage(40, 40, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", out);
        return new MockMultipartFile("arquivo", "comprovante-" + UUID.randomUUID() + ".png", "image/png", out.toByteArray());
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", "Bearer " + painel);
    }

    private JsonNode ler(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsByteArray());
    }
}
