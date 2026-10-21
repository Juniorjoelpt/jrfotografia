package br.com.jrfotografia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Freio contra adivinhar senhas, troca de senha e cadastro de colaboradores. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContaSegurancaTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    String painel;

    @BeforeEach
    void entrar() throws Exception {
        painel = token("admin@teste.local", "senha-de-teste-123");
    }

    @Test
    void loginBloqueiaDepoisDeCincoErros() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"alguem@teste.local\",\"senha\":\"errada" + i + "\"}")).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alguem@teste.local\",\"senha\":\"qualquer\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.erro").value(org.hamcrest.Matchers.containsString("Aguarde")));
    }

    @Test
    void galeriaBloqueiaDepoisDeCincoSenhasErradas() throws Exception {
        long cliente = ler(mvc.perform(post("/api/clientes").header("Authorization", "Bearer " + painel)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Teste Freio\"}")).andReturn()).get("id").asLong();
        JsonNode t = ler(mvc.perform(post("/api/trabalhos").header("Authorization", "Bearer " + painel)
                .contentType(MediaType.APPLICATION_JSON).content("{\"clienteId\":" + cliente + ",\"valorTotal\":100}")).andReturn());
        String galeria = t.get("galeriaToken").asText();
        // A galeria só abre a partir da edição/seleção.
        mvc.perform(patch("/api/trabalhos/" + t.get("resumo").get("id").asLong() + "/status").header("Authorization", "Bearer " + painel)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"EDICAO\"}")).andExpect(status().isOk());
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/galeria/" + galeria + "/acesso").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"senha\":\"00000" + i + "\"}")).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/galeria/" + galeria + "/acesso").contentType(MediaType.APPLICATION_JSON)
                .content("{\"senha\":\"123456\"}")).andExpect(status().isTooManyRequests());
        // Outro aparelho ainda consegue tentar (o bloqueio é por aparelho, com teto geral maior).
        mvc.perform(post("/api/galeria/" + galeria + "/acesso").header("X-Real-IP", "10.0.0.9").contentType(MediaType.APPLICATION_JSON)
                .content("{\"senha\":\"123456\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void colaboradorTrocaDeSenhaEDesativacao() throws Exception {
        // Administrador cadastra um colaborador com senha provisória.
        mvc.perform(post("/api/usuarios").header("Authorization", "Bearer " + painel).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Ajudante\",\"email\":\"ajudante@teste.local\",\"senha\":\"curta\"}"))
                .andExpect(status().isBadRequest());
        JsonNode u = ler(mvc.perform(post("/api/usuarios").header("Authorization", "Bearer " + painel).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ajudante\",\"email\":\"Ajudante@Teste.local\",\"senha\":\"provisoria-1\"}"))
                .andExpect(status().isCreated()).andReturn());
        assertThat(u.get("perfil").asText()).isEqualTo("COLABORADOR");
        mvc.perform(post("/api/usuarios").header("Authorization", "Bearer " + painel).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Outro\",\"email\":\"ajudante@teste.local\",\"senha\":\"provisoria-2\"}"))
                .andExpect(status().isUnprocessableEntity());

        String colab = token("ajudante@teste.local", "provisoria-1");
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + colab)).andExpect(status().isForbidden());
        mvc.perform(get("/api/despesas").header("Authorization", "Bearer " + colab)).andExpect(status().isForbidden());

        // Troca a própria senha: precisa da atual, mínimo de 8 e não pode ser a de fábrica.
        mvc.perform(put("/api/conta/senha").header("Authorization", "Bearer " + colab).contentType(MediaType.APPLICATION_JSON)
                .content("{\"senhaAtual\":\"errada\",\"novaSenha\":\"nova-senha-1\"}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/conta/senha").header("Authorization", "Bearer " + colab).contentType(MediaType.APPLICATION_JSON)
                .content("{\"senhaAtual\":\"provisoria-1\",\"novaSenha\":\"trocar123\"}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/conta/senha").header("Authorization", "Bearer " + colab).contentType(MediaType.APPLICATION_JSON)
                .content("{\"senhaAtual\":\"provisoria-1\",\"novaSenha\":\"nova-senha-1\"}")).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ajudante@teste.local\",\"senha\":\"provisoria-1\"}")).andExpect(status().isUnauthorized());
        colab = token("ajudante@teste.local", "nova-senha-1");

        // Desativado, perde o acesso na hora (mesmo com o token ainda válido).
        long id = u.get("id").asLong();
        mvc.perform(put("/api/usuarios/" + id).header("Authorization", "Bearer " + painel).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Ajudante\",\"email\":\"ajudante@teste.local\",\"ativo\":false}")).andExpect(status().isOk());
        mvc.perform(get("/api/trabalhos").header("Authorization", "Bearer " + colab)).andExpect(status().isUnauthorized());

        // O administrador não pode se desativar.
        long eu = 0;
        for (JsonNode x : ler(mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + painel)).andReturn())) {
            if (x.get("voce").asBoolean()) eu = x.get("id").asLong();
        }
        mvc.perform(put("/api/usuarios/" + eu).header("Authorization", "Bearer " + painel).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Admin\",\"email\":\"admin@teste.local\",\"ativo\":false}")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void loginAvisaQuandoAindaUsaASenhaDeFabrica() throws Exception {
        mvc.perform(post("/api/usuarios").header("Authorization", "Bearer " + painel).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Fabrica\",\"email\":\"fabrica@teste.local\",\"senha\":\"provisoria-9\"}")).andExpect(status().isCreated());
        JsonNode s = ler(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@teste.local\",\"senha\":\"senha-de-teste-123\"}")).andReturn());
        assertThat(s.get("senhaPadrao").asBoolean()).isFalse();
    }

    private String token(String email, String senha) throws Exception {
        return ler(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isOk()).andReturn()).get("token").asText();
    }

    private JsonNode ler(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsByteArray());
    }
}
