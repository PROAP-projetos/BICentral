package com.bicentral.bicentral_backend.service.ia;

import com.bicentral.bicentral_backend.dto.relatorio.AcaoAnalisadaDTO;
import com.bicentral.bicentral_backend.dto.relatorio.AcaoComTarefasDTO;
import com.bicentral.bicentral_backend.dto.relatorio.AcaoRelatorioDTO;
import com.bicentral.bicentral_backend.dto.relatorio.DepartamentoParceiroDTO;
import com.bicentral.bicentral_backend.dto.relatorio.DistribuicaoExecucaoDTO;
import com.bicentral.bicentral_backend.dto.relatorio.IndicadorRelatorioDTO;
import com.bicentral.bicentral_backend.dto.relatorio.PontoAcompanhamentoDTO;
import com.bicentral.bicentral_backend.dto.relatorio.RelatorioEstruturadoDTO;
import com.bicentral.bicentral_backend.dto.relatorio.RelatorioPessoaDTO;
import com.bicentral.bicentral_backend.dto.relatorio.TarefaPessoaDTO;
import com.bicentral.bicentral_backend.dto.relatorio.TarefaResponsavelDTO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RelatorioServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final RelatorioService relatorioService = new RelatorioService(jdbcTemplate, mock(AgenteRelatorio.class));

    private Object invoke(String metodo, Class<?>[] tipos, Object... args) throws Exception {
        Method m = RelatorioService.class.getDeclaredMethod(metodo, tipos);
        m.setAccessible(true);
        return m.invoke(relatorioService, args);
    }

    @Test
    void truncarTitulo_RemoveSufixoDeDepartamento() throws Exception {
        String bruto = "U 3.1.2.13 - Desenvolver o PEQUI-UFT | Pró-Reitoria de Assistência Estudantil - PROEST (UG)";
        String resultado = (String) invoke("truncarTitulo", new Class<?>[]{String.class}, bruto);
        assertEquals("U 3.1.2.13 - Desenvolver o PEQUI-UFT", resultado);
    }

    @Test
    void truncarTitulo_SemSeparador_DevolveOriginal() throws Exception {
        String semSufixo = "U 3.1.2.13 - Desenvolver o PEQUI-UFT";
        assertEquals(semSufixo, invoke("truncarTitulo", new Class<?>[]{String.class}, semSufixo));
    }

    @Test
    void gerarDocx_NaoLancaExcecaoEProduzArquivoNaoVazio() throws Exception {
        byte[] docx = (byte[]) invoke("gerarDocx", new Class<?>[]{RelatorioEstruturadoDTO.class}, relatorioDeTeste());
        assertTrue(docx.length > 0);
    }


    @Test
    void gerarPdf_NaoLancaExcecaoEProduzArquivoNaoVazio() throws Exception {
        byte[] pdf = (byte[]) invoke("gerarPdf", new Class<?>[]{RelatorioEstruturadoDTO.class}, relatorioDeTeste());
        assertTrue(pdf.length > 0);
        assertEquals('%', (char) pdf[0]);
        assertEquals('P', (char) pdf[1]);
        assertEquals('D', (char) pdf[2]);
        assertEquals('F', (char) pdf[3]);
    }


    // .xlsx é um zip — arquivos zip sempre começam com essa assinatura de 4 bytes ("PK\3\4").
    @Test
    void gerarExcel_NaoLancaExcecaoEProduzArquivoNaoVazio() throws Exception {
        byte[] xlsx = (byte[]) invoke("gerarExcel", new Class<?>[]{RelatorioEstruturadoDTO.class}, relatorioDeTeste());
        assertTrue(xlsx.length > 0);
        assertEquals('P', (char) xlsx[0]);
        assertEquals('K', (char) xlsx[1]);
        assertEquals(3, xlsx[2]);
        assertEquals(4, xlsx[3]);
    }

    @Test
    void gerarExcel_ComListaCompletaOrdenada_ProduzArquivoNaoVazio() throws Exception {
        byte[] xlsx = (byte[]) invoke("gerarExcel", new Class<?>[]{RelatorioEstruturadoDTO.class}, relatorioComListaCompleta());
        assertTrue(xlsx.length > 0);
    }

    // Seção "07 · Lista Completa" (DOCX e PDF) só existe quando listaCompletaOrdenada não é
    // vazia — é o caminho novo introduzido junto com a ordenação, cobrir os dois formatos aqui.
    @Test
    void gerarDocx_ComListaCompletaOrdenada_NaoLancaExcecao() throws Exception {
        byte[] docx = (byte[]) invoke("gerarDocx", new Class<?>[]{RelatorioEstruturadoDTO.class}, relatorioComListaCompleta());
        assertTrue(docx.length > 0);
    }

    @Test
    void gerarPdf_ComListaCompletaOrdenada_NaoLancaExcecao() throws Exception {
        byte[] pdf = (byte[]) invoke("gerarPdf", new Class<?>[]{RelatorioEstruturadoDTO.class}, relatorioComListaCompleta());
        assertTrue(pdf.length > 0);
    }

    // Seção "08 · Tarefas por Ação" só existe quando tarefasPorAcao não é vazia (opt-in via
    // RelatorioContextoTool.incluirTarefas) — cobrir os 3 formatos aqui.
    @Test
    void gerarDocx_ComTarefasPorAcao_NaoLancaExcecao() throws Exception {
        byte[] docx = (byte[]) invoke("gerarDocx", new Class<?>[]{RelatorioEstruturadoDTO.class}, relatorioComTarefasPorAcao());
        assertTrue(docx.length > 0);
    }

    @Test
    void gerarPdf_ComTarefasPorAcao_NaoLancaExcecao() throws Exception {
        byte[] pdf = (byte[]) invoke("gerarPdf", new Class<?>[]{RelatorioEstruturadoDTO.class}, relatorioComTarefasPorAcao());
        assertTrue(pdf.length > 0);
    }

    @Test
    void gerarExcel_ComTarefasPorAcao_ProduzArquivoNaoVazio() throws Exception {
        byte[] xlsx = (byte[]) invoke("gerarExcel", new Class<?>[]{RelatorioEstruturadoDTO.class}, relatorioComTarefasPorAcao());
        assertTrue(xlsx.length > 0);
    }

    // secoesIncluidas restringe quais seções entram no relatório (ver RelatorioContextoTool.secoes) —
    // vazia continua sendo "sem restrição", cobrindo os 3 formatos aqui.
    @Test
    void secaoIncluida_VaziaSignificaSemRestricao() {
        RelatorioEstruturadoDTO r = relatorioComSecoesRestritas(List.of());
        assertTrue(r.secaoIncluida("pontos_atencao"));
        assertTrue(r.secaoIncluida("qualquer_chave_desconhecida"));
    }

    @Test
    void secaoIncluida_ComListaSoLiberaAsChavesPedidas() {
        RelatorioEstruturadoDTO r = relatorioComSecoesRestritas(List.of("pontos_atencao", "destaques"));
        assertTrue(r.secaoIncluida("pontos_atencao"));
        assertTrue(r.secaoIncluida("destaques"));
        assertFalse(r.secaoIncluida("leitura_cenario"));
    }

    @Test
    void gerarDocx_ComSecoesRestritas_SoMostraAsSecoesPedidas() throws Exception {
        byte[] docx = (byte[]) invoke("gerarDocx", new Class<?>[]{RelatorioEstruturadoDTO.class}, relatorioComSecoesRestritas(List.of("pontos_atencao")));
        String texto;
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            texto = extractor.getText();
        }
        assertTrue(texto.contains("Pontos de Atenção"));
        assertFalse(texto.contains("Desempenhos de Destaque"));
        assertFalse(texto.contains("Leitura do Cenário"));
        // "Acompanhamento" sozinho também aparece na capa ("Plano de Acompanhamento do
        // Trabalho", sempre presente) — checa o cabeçalho da seção, que é único.
        assertFalse(texto.contains("05 · Acompanhamento"));
    }

    @Test
    void gerarPdf_ComSecoesRestritas_SoMostraAsSecoesPedidas() throws Exception {
        byte[] pdf = (byte[]) invoke("gerarPdf", new Class<?>[]{RelatorioEstruturadoDTO.class}, relatorioComSecoesRestritas(List.of("destaques")));
        String texto;
        try (PDDocument doc = PDDocument.load(pdf)) {
            texto = new PDFTextStripper().getText(doc);
        }
        assertTrue(texto.contains("Desempenhos de Destaque"));
        assertFalse(texto.contains("Pontos de Atenção"));
    }

    // "detalhamento" não tem aba própria no Excel — cai no fallback de pelo menos 1 aba (Resumo),
    // senão o workbook fica sem nenhuma sheet e não abre.
    @Test
    void gerarExcel_ComSecaoSemEquivalentePlanilha_AindaProduzArquivoValido() throws Exception {
        byte[] xlsx = (byte[]) invoke("gerarExcel", new Class<?>[]{RelatorioEstruturadoDTO.class}, relatorioComSecoesRestritas(List.of("detalhamento")));
        assertTrue(xlsx.length > 0);
        assertEquals('P', (char) xlsx[0]);
        assertEquals('K', (char) xlsx[1]);
    }


    @Test
    void gerarDocxPessoa_NaoLancaExcecaoEProduzArquivoNaoVazio() throws Exception {
        byte[] docx = (byte[]) invoke("gerarDocxPessoa", new Class<?>[]{RelatorioPessoaDTO.class}, relatorioPessoaDeTeste());
        assertTrue(docx.length > 0);
    }

    @Test
    void gerarPdfPessoa_NaoLancaExcecaoEProduzArquivoNaoVazio() throws Exception {
        byte[] pdf = (byte[]) invoke("gerarPdfPessoa", new Class<?>[]{RelatorioPessoaDTO.class}, relatorioPessoaDeTeste());
        assertTrue(pdf.length > 0);
        assertEquals('%', (char) pdf[0]);
        assertEquals('P', (char) pdf[1]);
        assertEquals('D', (char) pdf[2]);
        assertEquals('F', (char) pdf[3]);
    }

    @Test
    void gerarExcelPessoa_NaoLancaExcecaoEProduzArquivoNaoVazio() throws Exception {
        byte[] xlsx = (byte[]) invoke("gerarExcelPessoa", new Class<?>[]{RelatorioPessoaDTO.class}, relatorioPessoaDeTeste());
        assertTrue(xlsx.length > 0);
        assertEquals('P', (char) xlsx[0]);
        assertEquals('K', (char) xlsx[1]);
    }

    private RelatorioPessoaDTO relatorioPessoaDeTeste() {
        List<IndicadorRelatorioDTO> indicadores = List.of(
                new IndicadorRelatorioDTO("Total de Tarefas", "5"),
                new IndicadorRelatorioDTO("Concluídas", "2 (40,00%)"),
                new IndicadorRelatorioDTO("Em Andamento", "3 (60,00%)"),
                new IndicadorRelatorioDTO("Atrasadas", "1 (20,00%)"));

        List<TarefaPessoaDTO> tarefas = List.of(
                new TarefaPessoaDTO("U 1.1.5.2 - Elaboração do Plano Anual de Trabalho - PAT", "Pró-Reitoria de Avaliação e Planejamento - PROAP", 90.0, true, 68L, "15/07/2026"),
                new TarefaPessoaDTO("U 1.1.4.2 - Indicadores de Gestão do TCU 2025", "Pró-Reitoria de Avaliação e Planejamento - PROAP", 100.0, false, null, "20/02/2026"),
                new TarefaPessoaDTO("U 2.1.2.40 - Organização e sistematização das informações do RG", "Pró-Reitoria de Avaliação e Planejamento - PROAP", 100.0, false, null, "28/02/2026"),
                new TarefaPessoaDTO("U 2.1.5.1 - Indicadores Institucionais", "Pró-Reitoria de Avaliação e Planejamento - PROAP", 30.0, false, null, "30/03/2026"),
                new TarefaPessoaDTO("U 1.1.4.1 - Acompanhamento do PAT", "Pró-Reitoria de Avaliação e Planejamento - PROAP", 20.0, false, null, "30/12/2026"));

        return new RelatorioPessoaDTO("Idelma de Melo Rodrigues Abreu", "22/09/2026 10:00", indicadores, tarefas);
    }

    private RelatorioEstruturadoDTO relatorioComListaCompleta() {
        RelatorioEstruturadoDTO base = relatorioDeTeste();
        List<AcaoRelatorioDTO> listaCompleta = List.of(
                new AcaoRelatorioDTO("U 1.1.1.3 - Ação de teste 3", 100.0),
                new AcaoRelatorioDTO("U 1.1.1.4 - Ação de teste 4", 20.0));
        return new RelatorioEstruturadoDTO(
                base.departamento(), base.tipo(), base.geradoEm(), base.resumoExecutivo(), base.leituraCenario(),
                base.indicadores(), base.distribuicao(), base.analiseMenorExecucao(), base.destaquesPositivos(),
                base.pontosDeAcompanhamento(), true, listaCompleta, List.of(), List.of());
    }

    private RelatorioEstruturadoDTO relatorioComTarefasPorAcao() {
        RelatorioEstruturadoDTO base = relatorioDeTeste();
        List<TarefaResponsavelDTO> tarefas = List.of(
                new TarefaResponsavelDTO("Revisar minuta da normativa", "Revisar minuta da normativa", "Marcela Cristina Barbosa Garcia", "31/12/2026"));
        List<AcaoComTarefasDTO> tarefasPorAcao = List.of(
                new AcaoComTarefasDTO("U 1.1.1.3 - Ação de teste 3", 100.0, tarefas),
                new AcaoComTarefasDTO("U 3.1.6.11 - Atualizar as normativas da Política de Acessibilidade da UFT e dos Programas de Permanência estudantil", 0.0, tarefas));
        return new RelatorioEstruturadoDTO(
                base.departamento(), base.tipo(), base.geradoEm(), base.resumoExecutivo(), base.leituraCenario(),
                base.indicadores(), base.distribuicao(), base.analiseMenorExecucao(), base.destaquesPositivos(),
                base.pontosDeAcompanhamento(), false, List.of(), tarefasPorAcao, List.of());
    }

    private RelatorioEstruturadoDTO relatorioComSecoesRestritas(List<String> secoes) {
        RelatorioEstruturadoDTO base = relatorioDeTeste();
        return new RelatorioEstruturadoDTO(
                base.departamento(), base.tipo(), base.geradoEm(), base.resumoExecutivo(), base.leituraCenario(),
                base.indicadores(), base.distribuicao(), base.analiseMenorExecucao(), base.destaquesPositivos(),
                base.pontosDeAcompanhamento(), false, List.of(), List.of(), secoes);
    }

    private RelatorioEstruturadoDTO relatorioDeTeste() {
        List<IndicadorRelatorioDTO> indicadores = List.of(
                new IndicadorRelatorioDTO("Total de Ações no PAT", "10"),
                new IndicadorRelatorioDTO("Média Geral de Execução", "23,91%"));

        List<TarefaResponsavelDTO> tarefas = List.of(
                new TarefaResponsavelDTO("Atualizar normativas da Política de Acessibilidade", "Revisar regimentos vinculados à moradia estudantil", "Marcela Cristina Barbosa Garcia", "31/12/2026"),
                new TarefaResponsavelDTO("Atualizar normativas da Política de Acessibilidade", "Sistematizar contribuições dos câmpus", "Kherlley Caxias Batista Barbosa", "31/12/2026"));
        List<DepartamentoParceiroDTO> parceiros = List.of(
                new DepartamentoParceiroDTO("Campus Universitário de Arraias - CUAR", 100.0),
                new DepartamentoParceiroDTO("Campus Universitário de Gurupi - CAUG", 50.0));

        List<AcaoAnalisadaDTO> menorExecucao = List.of(
                new AcaoAnalisadaDTO(
                        "U 3.1.6.11 - Atualizar as normativas da Política de Acessibilidade da UFT e dos Programas de Permanência estudantil",
                        "Acessibilidade e permanência estudantil",
                        0.0, tarefas, parceiros,
                        "O percentual pode refletir uma etapa ainda não iniciada de revisão e articulação normativa. Por envolver atualização de políticas e programas institucionais, a execução tende a depender de diagnóstico, elaboração de propostas, análise jurídica e tramitação formal.",
                        false),
                new AcaoAnalisadaDTO(
                        "U 3.1.6.14 - Consolidar núcleos permanentes de acompanhamento e monitoramento das políticas de ações afirmativas",
                        "Núcleos de acompanhamento",
                        0.0, tarefas, parceiros,
                        "A consolidação de núcleos permanentes pressupõe definição de estrutura, responsabilidades, composição e fluxos de trabalho.",
                        true));

        List<AcaoRelatorioDTO> destaques = List.of(
                new AcaoRelatorioDTO("U 1.1.1.3 - Ação de teste 3", 100.0),
                new AcaoRelatorioDTO("U 1.1.1.4 - Ação de teste 4", 80.0),
                new AcaoRelatorioDTO("U 1.1.1.5 - Ação de teste 5", 78.0),
                new AcaoRelatorioDTO("U 1.1.1.6 - Ação de teste 6", 76.0),
                new AcaoRelatorioDTO("U 1.1.1.7 - Ação de teste 7", 74.0),
                new AcaoRelatorioDTO("U 1.1.1.8 - Ação de teste 8", 72.0),
                new AcaoRelatorioDTO("U 1.1.1.9 - Ação de teste 9", 70.0),
                new AcaoRelatorioDTO("U 1.1.1.10 - Ação de teste 10", 68.0),
                new AcaoRelatorioDTO("U 1.1.1.11 - Ação de teste 11", 66.0),
                new AcaoRelatorioDTO("U 1.1.1.12 - Ação de teste 12", 64.0));

        List<String> leituraCenario = List.of(
                "A execução geral está concentrada em ações de natureza contínua, o que explica parte dos percentuais baixos.",
                "Os pontos de atenção se concentram em temas de acessibilidade e articulação institucional.");

        List<PontoAcompanhamentoDTO> pontosDeAcompanhamento = List.of(
                new PontoAcompanhamentoDTO("Ações sem execução", "Existem etapas preparatórias já realizadas?"),
                new PontoAcompanhamentoDTO("Ações normativas", "Há cronograma e responsáveis definidos?"));

        DistribuicaoExecucaoDTO distribuicao = new DistribuicaoExecucaoDTO(4, 4, 2);

        return new RelatorioEstruturadoDTO(
                "Departamento de Teste", "PAT", "17/09/2026 12:00",
                "Resumo executivo de teste.", leituraCenario, indicadores, distribuicao,
                menorExecucao, destaques, pontosDeAcompanhamento,
                false, List.of(), List.of(), List.of());
    }
}
