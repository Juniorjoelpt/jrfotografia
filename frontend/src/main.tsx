import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import './estilos.css';
import './animacoes.css';
import './painel/fase2.css';
import { Site } from './site/Site';
import { GaleriaCliente } from './galeria/GaleriaCliente';
import { Entrar } from './painel/Entrar';
import { LayoutPainel } from './painel/LayoutPainel';
import { Inicio } from './painel/Inicio';
import { Trabalhos } from './painel/Trabalhos';
import { NovoTrabalho } from './painel/NovoTrabalho';
import { DetalheTrabalho } from './painel/DetalheTrabalho';
import { Clientes } from './painel/Clientes';
import { Agenda } from './painel/Agenda';
import { Vendas } from './painel/Vendas';
import { DetalheOrcamento, EditorOrcamento } from './painel/Orcamentos';
import { Financeiro } from './painel/Financeiro';
import { Relatorios } from './painel/Relatorios';
import { Conta } from './painel/Conta';
import { SoAdmin } from './painel/comum';
import { OrcamentoCliente } from './orcamento/OrcamentoCliente';
import { Ajustes } from './painel/Ajustes';

createRoot(document.getElementById('raiz')!).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Site />} />
        <Route path="/g/:token" element={<GaleriaCliente />} />
        <Route path="/o/:token" element={<OrcamentoCliente />} />
        <Route path="/painel/entrar" element={<Entrar />} />
        <Route path="/painel" element={<LayoutPainel />}>
          <Route index element={<Inicio />} />
          <Route path="trabalhos" element={<Trabalhos />} />
          <Route path="trabalhos/novo" element={<SoAdmin><NovoTrabalho /></SoAdmin>} />
          <Route path="trabalhos/:id" element={<DetalheTrabalho />} />
          <Route path="clientes" element={<Clientes />} />
          <Route path="agenda" element={<Agenda />} />
          <Route path="vendas" element={<Vendas />} />
          <Route path="pedidos" element={<Navigate to="/painel/vendas" replace />} />
          <Route path="orcamentos/novo" element={<SoAdmin><EditorOrcamento /></SoAdmin>} />
          <Route path="orcamentos/:id" element={<SoAdmin><DetalheOrcamento /></SoAdmin>} />
          <Route path="orcamentos/:id/editar" element={<SoAdmin><EditorOrcamento /></SoAdmin>} />
          <Route path="financeiro" element={<SoAdmin><Financeiro /></SoAdmin>} />
          <Route path="recebimentos" element={<Navigate to="/painel/financeiro" replace />} />
          <Route path="relatorios" element={<SoAdmin><Relatorios /></SoAdmin>} />
          <Route path="conta" element={<Conta />} />
          <Route path="ajustes" element={<SoAdmin><Ajustes /></SoAdmin>} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
);
