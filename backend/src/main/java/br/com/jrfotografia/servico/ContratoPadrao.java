package br.com.jrfotografia.servico;

/**
 * Modelo de contrato usado enquanto o estúdio não cadastrar o seu (Ajustes › Contrato).
 * Os campos entre chaves são preenchidos com os dados do trabalho.
 * É um ponto de partida: revise com um advogado antes de usar.
 */
public final class ContratoPadrao {

    private ContratoPadrao() {}

    public static final String CAMPOS = """
            {CONTRATANTE} {CPF} {TELEFONE} {CONTRATADO} {RESPONSAVEL} {DOCUMENTO_CONTRATADO} {CIDADE} \
            {SERVICO} {DATA_ENSAIO} {LOCAL} {MATERIAL} {PRAZO_ENTREGA} {FOTO_EXTRA} {VALOR_TOTAL} {VALOR_EXTENSO} \
            {CONDICOES_PAGAMENTO} {VALIDADE_GALERIA} {DATA_HOJE}""";

    public static final String TEXTO = """
            CONTRATANTE: {CONTRATANTE}, CPF {CPF}, telefone {TELEFONE}.
            CONTRATADO: {CONTRATADO}, representado por {RESPONSAVEL}, {DOCUMENTO_CONTRATADO}, com atuação em {CIDADE}.

            1. OBJETO
            O CONTRATADO prestará ao CONTRATANTE o serviço de {SERVICO}, no dia {DATA_ENSAIO}, em {LOCAL}.

            2. MATERIAL ENTREGUE
            Será entregue: {MATERIAL}. As fotos são entregues em formato digital, editadas, por meio de galeria online privada com senha. Quando o pacote prevê seleção, o CONTRATANTE escolhe as fotos na galeria; cada foto escolhida além do pacote custa {FOTO_EXTRA}.

            3. PRAZO
            O prazo de entrega é de {PRAZO_ENTREGA} dias úteis, contados da data do ensaio ou da conclusão da seleção, o que ocorrer por último. A galeria fica disponível por {VALIDADE_GALERIA} dias após a entrega; depois desse prazo, o CONTRATADO não é obrigado a manter cópia dos arquivos.

            4. VALOR E PAGAMENTO
            O valor total do serviço é de {VALOR_TOTAL} ({VALOR_EXTENSO}), pago da seguinte forma: {CONDICOES_PAGAMENTO}. O download das fotos em alta resolução é liberado conforme a quitação prevista para a forma de pagamento escolhida.

            5. REMARCAÇÃO E CANCELAMENTO
            O ensaio pode ser remarcado uma vez, sem custo, com aviso de pelo menos 48 horas. Em caso de desistência pelo CONTRATANTE com menos de 7 dias da data marcada, o sinal pago não será devolvido. Se o CONTRATADO não puder realizar o serviço, devolverá integralmente os valores pagos ou remarcará em comum acordo. Chuva ou condições que impeçam ensaios externos permitem remarcar sem custo.

            6. DIREITOS AUTORAIS E USO DE IMAGEM
            As fotografias são obras protegidas pela Lei nº 9.610/1998, e os direitos autorais pertencem ao CONTRATADO. O CONTRATANTE pode usar as fotos para fins pessoais e em suas redes sociais, mencionando o CONTRATADO sempre que possível, sendo vedado o uso comercial sem autorização. O CONTRATANTE (   ) autoriza  (   ) não autoriza o uso das imagens no portfólio, site e redes sociais do CONTRATADO.

            7. DADOS PESSOAIS
            Os dados do CONTRATANTE são usados apenas para a execução deste contrato e para contato, conforme a Lei nº 13.709/2018 (LGPD), e não são compartilhados com terceiros.

            8. FORO
            Fica eleito o foro da comarca de {CIDADE} para resolver qualquer questão deste contrato.

            {CIDADE}, {DATA_HOJE}.""";
}
