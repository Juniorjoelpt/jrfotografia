import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import './estilos.css';
import { Site } from './site/Site';
import { GaleriaCliente } from './galeria/GaleriaCliente';
import { Entrar } from './painel/Entrar';
import { LayoutPainel } from './painel/LayoutPainel';
import { Inicio } from './painel/Inicio';
import { Trabalhos } from './painel/Trabalhos';
import { NovoTrabalho } from './painel/NovoTrabalho';
import { DetalheTrabalho } from './painel/DetalheTrabalho';
import { Clientes } from './painel/Clientes';
import { Pedidos } from './painel/Pedidos';
import { Recebimentos } from './painel/Recebimentos';
import { Ajustes } from './painel/Ajustes';

createRoot(document.getElementById('raiz')!).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Site />} />
        <Route path="/g/:token" element={<GaleriaCliente />} />
        <Route path="/painel/entrar" element={<Entrar />} />
        <Route path="/painel" element={<LayoutPainel />}>
          <Route index element={<Inicio />} />
          <Route path="trabalhos" element={<Trabalhos />} />
          <Route path="trabalhos/novo" element={<NovoTrabalho />} />
          <Route path="trabalhos/:id" element={<DetalheTrabalho />} />
          <Route path="clientes" element={<Clientes />} />
          <Route path="pedidos" element={<Pedidos />} />
          <Route path="recebimentos" element={<Recebimentos />} />
          <Route path="ajustes" element={<Ajustes />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
);
