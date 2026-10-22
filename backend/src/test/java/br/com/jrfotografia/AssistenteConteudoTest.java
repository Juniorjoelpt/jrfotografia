package br.com.jrfotografia;

import br.com.jrfotografia.dominio.ContratoAssinatura;
import br.com.jrfotografia.dominio.Enums.StatusTrabalho;
import br.com.jrfotografia.dominio.Equipe.PesquisaSatisfacao;
import br.com.jrfotografia.dominio.Repositorios.AssinaturaRepo;
import br.com.jrfotografia.dominio.Repositorios.EstudioRepo;
import br.com.jrfotografia.dominio.Repositorios.PesquisaRepo;
import br.com.jrfotografia.dominio.Repositorios.TrabalhoRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Assistente de conteúdo: conversa no formato OpenAI, só usa o que pode ir para as redes
 * (ensaio com uso de imagem autorizado, depoimento autorizado) e não manda dado pessoal para o modelo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssistenteConteudoTest {

    static final ObjectMapper JSON = new ObjectMapper();
    static final List<JsonNode> PEDIDOS = new CopyOnWriteArrayList<>();
    /** O que o modelo falso responde no próximo pedido. */
    static final AtomicReference<String> RESPOSTA = new AtomicReference<>();
    static HttpServer falso;
    static String endereco;

    static {
        try {
            falso = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        falso.createContext("/v1/chat/completions", troca -> {
            String auth = troca.getRequestHeaders().getFirst("Authorization");
            JsonNode corpo = JSON.readTree(troca.getRequestBody());
            PEDIDOS.add(corpo);
            int status = 200;
            String resposta;
            if (auth != null && !auth.equals("Bearer chave-certa")) {
                status = 401;
                resposta = "{\"error\":{\"message\":\"Invalid API Key\"}}";
            } else {
                String conteudo = RESPOSTA.get() != null ? RESPOSTA.get() : "Olá, fotógrafo!";
                resposta = JSON.writeValueAsString(JSON.createObjectNode().set("choices", JSON.createArrayNode().add(
                        JSON.createObjectNode().set("message", JSON.createObjectNode().put("role", "assistant").put("content", conteudo)))));
            }
            byte[] b = resposta.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(status, b.length);
            troca.getResponseBody().write(b);
            troca.close();
        });
        falso.start();
        endereco = "http://127.0.0.1:" + falso.getAddress().getPort() + "/v1";
    }

    @AfterAll
    static void parar() {
        falso.stop(0);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TrabalhoRepo trabalhos;
    @Autowired AssinaturaRepo assinaturas;
    @Autowired PesquisaRepo pesquisas;
    @Autowired EstudioRepo estudios;
    String painel;

    @BeforeEach
    void entrar() throws Exception {
        painel = ler(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@teste.local\",\"senha\":\"senha-de-teste-123\"}")).andReturn()).get("token").asText();
        PEDIDOS.clear();
        RESPOSTA.set(null);
    }

    @AfterEach
    void desligar() {
        estudios.findAll().forEach(e -> { e.setIaAtivo(false); e.setIaChave(null); estudios.save(e); });
    }

    @Test
    void configuracaoGuardaAChaveSemDevolverETesta() throws Exception {
        mvc.perform(auth(put("/api/estudio/ia")).content("{\"ativo\":true,\"url\":\"\",\"modelo\":\"\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(auth(put("/api/estudio/ia")).content("{\"ativo\":false,\"url\":\"ftp://x\",\"modelo\":\"m\"}"))
                .andExpect(status().isUnprocessableEntity());

        JsonNode c = ler(mvc.perform(auth(put("/api/estudio/ia")).content("""
                {"ativo":true,"url":"%s","modelo":"qwen-teste","chave":"chave-errada"}""".formatted(endereco))).andExpect(status().isOk()).andReturn());
        assertThat(c.get("temChave").asBoolean()).isTrue();
        assertThat(c.toString()).doesNotContain("chave-errada");
        assertThat(estudios.findAll().get(0).getIaChave()).isNotEqualTo("chave-errada"); // cifrada

        JsonNode t = ler(mvc.perform(auth(post("/api/estudio/ia/teste"))).andReturn());
        assertThat(t.get("ok").asBoolean()).isFalse();
        assertThat(t.get("mensagem").asText()).contains("chave");

        mvc.perform(auth(put("/api/estudio/ia")).content("""
                {"ativo":true,"url":"%s","modelo":"qwen-teste","chave":"chave-certa"}""".formatted(endereco))).andExpect(status().isOk());
        t = ler(mvc.perform(auth(post("/api/estudio/ia/teste"))).andReturn());
        assertThat(t.get("ok").asBoolean()).isTrue();
        assertThat(PEDIDOS.get(PEDIDOS.size() - 1).get("model").asText()).isEqualTo("qwen-teste");

        // Ollama local: sem chave nenhuma
        mvc.perform(auth(put("/api/estudio/ia")).content("""
                {"ativo":true,"url":"%s/","modelo":"qwen-teste","apagarChave":true}""".formatted(endereco))).andExpect(status().isOk());
        assertThat(ler(mvc.perform(auth(get("/api/estudio/ia"))).andReturn()).get("temChave").asBoolean()).isFalse();
        assertThat(ler(mvc.perform(auth(post("/api/estudio/ia/teste"))).andReturn()).get("ok").asBoolean()).isTrue();

        // Endereço fora do ar: mensagem clara, sem erro 500
        mvc.perform(auth(put("/api/estudio/ia")).content("{\"ativo\":true,\"url\":\"http://127.0.0.1:9/v1\",\"modelo\":\"x\",\"apagarChave\":true}"));
        t = ler(mvc.perform(auth(post("/api/estudio/ia/teste"))).andReturn());
        assertThat(t.get("ok").asBoolean()).isFalse();
        assertThat(t.get("mensagem").asText()).contains("Não foi possível falar com o assistente");
    }

    @Test
    void ideiasUsamSoOQueFoiAutorizadoESemDadosPessoais() throws Exception {
        // Desligado: pede para ligar
        mvc.perform(auth(post("/api/conteudo/ideias")).content("{\"tipo\":\"SEMANA\"}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(auth(put("/api/estudio/ia")).content("""
                {"ativo":true,"url":"%s","modelo":"qwen-teste","chave":"chave-certa"}""".formatted(endereco))).andExpect(status().isOk());

        long autorizado = trabalhoEntregue("Marina Autorizou Silva", "Ensaio gestante", true);
        long negou = trabalhoEntregue("Paula Negou Souza", "Ensaio sigiloso-xyz", false);
        PesquisaSatisfacao p = new PesquisaSatisfacao();
        p.setEstudioId(estudios.findAll().get(0).getId());
        p.setTrabalhoId(autorizado);
        p.setToken("tok-pesq-" + autorizado);
        p.setNota(10);
        p.setComentario("Amei cada detalhe, me senti linda!");
        p.setAutorizaDepoimento(true);
        p.setRespondidaEm(LocalDateTime.now());
        pesquisas.save(p);

        JsonNode op = ler(mvc.perform(auth(get("/api/conteudo/opcoes"))).andExpect(status().isOk()).andReturn());
        assertThat(op.get("trabalhos").toString()).contains("Ensaio gestante").doesNotContain("sigiloso-xyz").doesNotContain("Marina");
        assertThat(op.get("depoimentos").get(0).get("rotulo").asText()).isEqualTo("Nota 10 · Marina");

        // Ensaio sem autorização de imagem não pode ser pedido
        mvc.perform(auth(post("/api/conteudo/ideias")).content("{\"tipo\":\"TRABALHO\",\"referencia\":" + negou + "}")).andExpect(status().isNotFound());

        // Modelo pequeno que cerca o JSON com texto e ```json
        RESPOSTA.set("""
                Claro! Aqui estão as ideias:
                ```json
                {"ideias":[{"dia":"","formato":"Carrossel","titulo":"A espera mais linda","roteiro":["Foto da barriga","Detalhe das mãos"],
                 "legenda":"Cada detalhe dessa espera merece ser lembrado.","hashtags":"ensaiogestante, #fotografia"}]}
                ```""");
        JsonNode r = ler(mvc.perform(auth(post("/api/conteudo/ideias")).content("{\"tipo\":\"TRABALHO\",\"referencia\":" + autorizado + ",\"pedido\":\"Destacar a luz do fim de tarde\"}"))
                .andExpect(status().isCreated()).andReturn());
        JsonNode ideia = r.get("ideias").get(0);
        assertThat(ideia.get("formato").asText()).isEqualTo("Carrossel");
        assertThat(ideia.get("roteiro").asText()).isEqualTo("Foto da barriga\nDetalhe das mãos");
        assertThat(ideia.get("hashtags").toString()).contains("#ensaiogestante").contains("#fotografia");
        assertThat(r.get("usado").toString()).contains("uso de imagem autorizado");

        JsonNode enviado = PEDIDOS.get(PEDIDOS.size() - 1);
        String tudo = enviado.get("messages").toString();
        assertThat(enviado.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(tudo).contains("Ensaio gestante").contains("Destacar a luz do fim de tarde");
        assertThat(tudo).doesNotContain("Marina").doesNotContain("Silva").doesNotContain("98999").doesNotContain("@cliente");

        // Plano da semana: depoimento só com o primeiro nome, ensaio negado fora, datas comemorativas
        RESPOSTA.set("Não consegui montar o JSON, mas: poste bastidores na segunda.");
        r = ler(mvc.perform(auth(post("/api/conteudo/ideias")).content("{\"tipo\":\"SEMANA\"}")).andExpect(status().isCreated()).andReturn());
        assertThat(r.get("ideias").get(0).get("legenda").asText()).contains("bastidores"); // texto corrido não se perde
        tudo = PEDIDOS.get(PEDIDOS.size() - 1).get("messages").toString();
        assertThat(tudo).contains("Marina (nota 10)").doesNotContain("Marina Autorizou").doesNotContain("sigiloso-xyz").doesNotContain("Paula");
        assertThat(tudo).contains("Pacotes").contains("AGENDA DOS PRÓXIMOS 30 DIAS");

        // Histórico guarda e apaga
        JsonNode h = ler(mvc.perform(auth(get("/api/conteudo/ideias"))).andReturn());
        assertThat(h.size()).isGreaterThanOrEqualTo(2);
        mvc.perform(auth(delete("/api/conteudo/ideias/" + h.get(0).get("id").asLong()))).andExpect(status().isNoContent());
        assertThat(ler(mvc.perform(auth(get("/api/conteudo/ideias"))).andReturn()).size()).isEqualTo(h.size() - 1);

        // Pedido livre precisa de texto
        mvc.perform(auth(post("/api/conteudo/ideias")).content("{\"tipo\":\"LIVRE\",\"pedido\":\"  \"}")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void datasComemorativasEPascoa() {
        assertThat(br.com.jrfotografia.servico.ConteudoServico.pascoa(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(br.com.jrfotografia.servico.ConteudoServico.pascoa(2027)).isEqualTo(LocalDate.of(2027, 3, 28));
        assertThat(br.com.jrfotografia.servico.ConteudoServico.datasComemorativas(LocalDate.of(2026, 4, 25), 20))
                .anyMatch(d -> d.startsWith("Dia das Mães (10/05"));
        assertThat(br.com.jrfotografia.servico.ConteudoServico.datasComemorativas(LocalDate.of(2026, 12, 20), 20))
                .anyMatch(d -> d.startsWith("Natal")).anyMatch(d -> d.startsWith("Dia do Fotógrafo (08/01"));
    }

    private long trabalhoEntregue(String nome, String tipo, boolean autorizaImagem) throws Exception {
        long cliente = ler(mvc.perform(auth(post("/api/clientes")).content("{\"nome\":\"" + nome + "\",\"telefone\":\"98999887766\",\"email\":\"x@cliente.local\"}"))
                .andReturn()).get("id").asLong();
        long pacote = 0;
        for (JsonNode p : ler(mvc.perform(auth(get("/api/pacotes"))).andReturn())) if (p.get("nome").asText().equals("Sessão 10")) pacote = p.get("id").asLong();
        long id = ler(mvc.perform(auth(post("/api/trabalhos")).content("""
                {"clienteId":%d,"pacoteId":%d,"tipo":"%s","formaPagamento":"AVISTA","primeiroVencimento":"%s","dataTrabalho":"%sT09:00:00"}"""
                .formatted(cliente, pacote, tipo, LocalDate.now(), LocalDate.now().minusDays(20)))).andExpect(status().isCreated()).andReturn()).get("resumo").get("id").asLong();
        var t = trabalhos.findById(id).orElseThrow();
        t.setStatus(StatusTrabalho.ENTREGUE);
        t.setEntregueEm(LocalDateTime.now().minusDays(2));
        trabalhos.save(t);
        ContratoAssinatura a = assinaturas.findByTrabalhoIdOrderByCriadoEmDesc(id).get(0);
        a.setStatus(ContratoAssinatura.Status.ASSINADO);
        a.setAssinadoEm(LocalDateTime.now().minusDays(15));
        a.setAutorizaImagem(autorizaImagem);
        assinaturas.save(a);
        return id;
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", "Bearer " + painel).contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode ler(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsByteArray());
    }
}
