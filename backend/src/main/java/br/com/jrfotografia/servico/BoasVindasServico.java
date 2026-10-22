package br.com.jrfotografia.servico;

import br.com.jrfotografia.dominio.ContratoAssinatura;
import br.com.jrfotografia.dominio.Repositorios.TrabalhoRepo;
import br.com.jrfotografia.dominio.Trabalho;
import org.springframework.stereotype.Service;

/**
 * Todo trabalho novo passa por aqui, venha de onde vier (agenda online, Pix, orçamento aceito, Novo trabalho,
 * pedido fechado): contrato pronto para assinar e a área da cliente com link. Assim as quatro portas fazem o mesmo.
 */
@Service
public class BoasVindasServico {

    private final AssinaturaServico assinaturas;
    private final TrabalhoRepo trabalhos;
    private final TrabalhoServico trabalhoServico;
    private final EmailAvisos avisos;

    public BoasVindasServico(AssinaturaServico assinaturas, TrabalhoRepo trabalhos, TrabalhoServico trabalhoServico, EmailAvisos avisos) {
        this.assinaturas = assinaturas;
        this.trabalhos = trabalhos;
        this.trabalhoServico = trabalhoServico;
        this.avisos = avisos;
    }

    /** Área da cliente e contrato para assinar (null se não houver contrato pendente). */
    public record Links(String area, String contrato) {}

    public Links preparar(Long estudioId, Long trabalhoId) {
        assinaturas.gerarSePossivel(estudioId, trabalhoId);
        ContratoAssinatura c = assinaturas.atual(trabalhoId);
        String area = trabalhos.findById(trabalhoId).map(trabalhoServico::garantirTokenCliente).orElse(null);
        return new Links(area, c != null && c.getStatus() == ContratoAssinatura.Status.PENDENTE ? c.getToken() : null);
    }

    /** Trabalho criado à mão (Novo trabalho ou pedido fechado): prepara e manda as boas-vindas por e-mail. */
    public Links trabalhoCriado(Trabalho t) {
        Links l = preparar(t.getEstudioId(), t.getId());
        avisos.trabalhoCriado(t, l.area(), l.contrato());
        return l;
    }
}
