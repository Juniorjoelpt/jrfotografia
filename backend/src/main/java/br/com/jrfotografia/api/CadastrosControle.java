package br.com.jrfotografia.api;

import br.com.jrfotografia.api.Dtos.*;
import br.com.jrfotografia.config.Erros.NaoEncontrado;
import br.com.jrfotografia.config.UsuarioLogado;
import br.com.jrfotografia.dominio.Cliente;
import br.com.jrfotografia.dominio.Enums.Origem;
import br.com.jrfotografia.dominio.Enums.StatusPedido;
import br.com.jrfotografia.dominio.Pacote;
import br.com.jrfotografia.dominio.Repositorios.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Clientes, pacotes, pedidos de orçamento e dados do estúdio. */
@RestController
@RequestMapping("/api")
public class CadastrosControle {

    private final ClienteRepo clientes;
    private final PacoteRepo pacotes;
    private final PedidoRepo pedidos;
    private final EstudioRepo estudios;
    private final TrabalhoRepo trabalhos;
    private final CampanhaRepo campanhas;

    public CadastrosControle(ClienteRepo clientes, PacoteRepo pacotes, PedidoRepo pedidos,
                             EstudioRepo estudios, TrabalhoRepo trabalhos, CampanhaRepo campanhas) {
        this.campanhas = campanhas;
        this.clientes = clientes;
        this.pacotes = pacotes;
        this.pedidos = pedidos;
        this.estudios = estudios;
        this.trabalhos = trabalhos;
    }

    // ---------- clientes ----------
    @GetMapping("/clientes")
    public List<ClienteDto> listarClientes(@RequestParam(required = false) String busca) {
        Long e = UsuarioLogado.estudio();
        var lista = busca == null || busca.isBlank() ? clientes.findByEstudioIdOrderByNome(e) : clientes.buscar(e, busca.trim());
        return lista.stream().map(ClienteDto::de).toList();
    }

    @GetMapping("/clientes/{id}")
    public ClienteDto cliente(@PathVariable Long id) {
        return ClienteDto.de(clienteDoEstudio(id));
    }

    @GetMapping("/clientes/{id}/trabalhos")
    public List<TrabalhoResumo> trabalhosDoCliente(@PathVariable Long id) {
        clienteDoEstudio(id);
        return trabalhos.findByEstudioIdAndClienteIdOrderByDataTrabalhoDesc(UsuarioLogado.estudio(), id)
                .stream().map(TrabalhoResumo::de).toList();
    }

    @PostMapping("/clientes")
    @ResponseStatus(HttpStatus.CREATED)
    public ClienteDto criarCliente(@Valid @RequestBody ClienteForm f) {
        Cliente c = new Cliente();
        c.setEstudioId(UsuarioLogado.estudio());
        preencher(c, f);
        return ClienteDto.de(clientes.save(c));
    }

    @PutMapping("/clientes/{id}")
    @Transactional
    public ClienteDto editarCliente(@PathVariable Long id, @Valid @RequestBody ClienteForm f) {
        Cliente c = clienteDoEstudio(id);
        preencher(c, f);
        return ClienteDto.de(c);
    }

    private void preencher(Cliente c, ClienteForm f) {
        c.setNome(f.nome().trim());
        c.setTelefone(soDigitos(f.telefone()));
        c.setEmail(f.email());
        c.setCpf(f.cpf());
        c.setDataNascimento(f.dataNascimento());
        c.setOrigem(f.origem());
        c.setObservacoes(f.observacoes());
    }

    private Cliente clienteDoEstudio(Long id) {
        return clientes.findByIdAndEstudioId(id, UsuarioLogado.estudio())
                .orElseThrow(() -> new NaoEncontrado("Cliente não encontrado"));
    }

    // ---------- pacotes ----------
    @GetMapping("/pacotes")
    public List<PacoteDto> listarPacotes() {
        // Pacotes internos das mini-sessões ficam fora da lista (são editados pela campanha).
        Long est = UsuarioLogado.estudio();
        var internos = campanhas.findByEstudioIdOrderByIdDesc(est).stream().map(c -> c.getPacoteId()).collect(java.util.stream.Collectors.toSet());
        return pacotes.findByEstudioIdOrderByOrdem(est).stream().filter(p -> !internos.contains(p.getId())).map(PacoteDto::de).toList();
    }

    @PostMapping("/pacotes")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PacoteDto criarPacote(@Valid @RequestBody PacoteForm f) {
        Pacote p = new Pacote();
        p.setEstudioId(UsuarioLogado.estudio());
        preencher(p, f);
        return PacoteDto.de(pacotes.save(p));
    }

    @PutMapping("/pacotes/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public PacoteDto editarPacote(@PathVariable Long id, @Valid @RequestBody PacoteForm f) {
        Pacote p = pacotes.findByIdAndEstudioId(id, UsuarioLogado.estudio())
                .orElseThrow(() -> new NaoEncontrado("Pacote não encontrado"));
        preencher(p, f);
        return PacoteDto.de(p);
    }

    private void preencher(Pacote p, PacoteForm f) {
        p.setNome(f.nome().trim());
        p.setQtdFotos(f.qtdFotos());
        p.setPreco(f.preco());
        p.setPrecoFotoExtra(f.precoFotoExtra());
        p.setPrazoEntregaDias(f.prazoEntregaDias());
        p.setItens(f.itens() == null ? null : String.join("|", f.itens()));
        p.setMaterialEntregue(f.materialEntregue());
        if (f.publico() != null) p.setPublico(f.publico());
        if (f.ativo() != null) p.setAtivo(f.ativo());
        if (f.ordem() != null) p.setOrdem(f.ordem());
        if (f.duracaoMin() != null) p.setDuracaoMin(f.duracaoMin());
        if (f.agendaOnline() != null) p.setAgendaOnline(f.agendaOnline());
    }

    // ---------- pedidos de orçamento ----------
    @GetMapping("/pedidos")
    public List<PedidoDto> listarPedidos() {
        return pedidos.findByEstudioIdOrderByCriadoEmDesc(UsuarioLogado.estudio()).stream().map(PedidoDto::de).toList();
    }

    @PatchMapping("/pedidos/{id}")
    @Transactional
    public PedidoDto mudarStatusPedido(@PathVariable Long id, @Valid @RequestBody StatusPedidoForm f) {
        var p = pedidos.findByIdAndEstudioId(id, UsuarioLogado.estudio())
                .orElseThrow(() -> new NaoEncontrado("Pedido não encontrado"));
        p.setStatus(f.status());
        return PedidoDto.de(p);
    }

    /** Transforma o pedido em cliente (ou reaproveita o cliente já criado) e marca como contatado. */
    @PostMapping("/pedidos/{id}/cliente")
    @Transactional
    public ClienteDto converterPedido(@PathVariable Long id) {
        Long e = UsuarioLogado.estudio();
        var p = pedidos.findByIdAndEstudioId(id, e).orElseThrow(() -> new NaoEncontrado("Pedido não encontrado"));
        if (p.getClienteId() != null) return ClienteDto.de(clienteDoEstudio(p.getClienteId()));
        Cliente c = new Cliente();
        c.setEstudioId(e);
        c.setNome(p.getNome());
        c.setTelefone(p.getTelefone());
        c.setEmail(p.getEmail());
        c.setOrigem(p.getOrigem() != null ? p.getOrigem() : Origem.SITE);
        clientes.save(c);
        p.setClienteId(c.getId());
        if (p.getStatus() == StatusPedido.NOVO) p.setStatus(StatusPedido.CONTATADO);
        return ClienteDto.de(c);
    }

    // ---------- estúdio ----------
    @GetMapping("/estudio")
    public EstudioDto estudio() {
        return EstudioDto.de(estudios.findById(UsuarioLogado.estudio()).orElseThrow(), true);
    }

    @PutMapping("/estudio")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public EstudioDto editarEstudio(@Valid @RequestBody EstudioForm f) {
        var e = estudios.findById(UsuarioLogado.estudio()).orElseThrow();
        e.setNome(f.nome());
        e.setCidade(f.cidade());
        e.setWhatsapp(soDigitos(f.whatsapp()));
        e.setInstagram(f.instagram() == null ? null : f.instagram().replace("@", "").trim());
        e.setEmail(f.email());
        e.setChavePix(f.chavePix());
        e.setApresentacao(f.apresentacao());
        return EstudioDto.de(e, true);
    }

    static String soDigitos(String s) {
        if (s == null) return null;
        String d = s.replaceAll("\\D", "");
        return d.isEmpty() ? null : d;
    }
}
