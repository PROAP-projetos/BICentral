package com.bicentral.bicentral_backend.service.ia;

import com.bicentral.bicentral_backend.dto.relatorio.AcaoAnalisadaDTO;
import com.bicentral.bicentral_backend.dto.relatorio.AcaoRelatorioDTO;
import com.bicentral.bicentral_backend.dto.relatorio.DepartamentoParceiroDTO;
import com.bicentral.bicentral_backend.dto.relatorio.DistribuicaoExecucaoDTO;
import com.bicentral.bicentral_backend.dto.relatorio.IndicadorRelatorioDTO;
import com.bicentral.bicentral_backend.dto.relatorio.PontoAcompanhamentoDTO;
import com.bicentral.bicentral_backend.dto.relatorio.RelatorioEstruturadoDTO;
import com.bicentral.bicentral_backend.dto.relatorio.TarefaResponsavelDTO;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void buscarTendencia_SemSnapshotAnterior_RetornaVazio() throws Exception {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        String resultado = (String) invoke("buscarTendencia", new Class<?>[]{String.class, double.class}, "PROEST", 23.91);
        assertEquals("", resultado);
    }

    @Test
    void buscarTendencia_ComAumento_MostraSinalDeMais() throws Exception {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(
                List.of(Map.of("media_execucao_pct", 21.90, "data_snapshot", Date.valueOf(LocalDate.of(2026, 9, 16)))));
        String resultado = (String) invoke("buscarTendencia", new Class<?>[]{String.class, double.class}, "PROEST", 23.91);
        assertEquals(" (+2,01 pontos percentuais desde 16/09/2026)", resultado);
    }

    @Test
    void buscarTendencia_ComQueda_MostraSinalDeMenos() throws Exception {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(
                List.of(Map.of("media_execucao_pct", 30.0, "data_snapshot", Date.valueOf(LocalDate.of(2026, 9, 16)))));
        String resultado = (String) invoke("buscarTendencia", new Class<?>[]{String.class, double.class}, "PROEST", 23.91);
        assertEquals(" (-6,09 pontos percentuais desde 16/09/2026)", resultado);
    }

    @Test
    void buscarTendencia_SemVariacao_SemSinal() throws Exception {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(
                List.of(Map.of("media_execucao_pct", 23.91, "data_snapshot", Date.valueOf(LocalDate.of(2026, 9, 16)))));
        String resultado = (String) invoke("buscarTendencia", new Class<?>[]{String.class, double.class}, "PROEST", 23.91);
        assertEquals(" (0,00 pontos percentuais desde 16/09/2026)", resultado);
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

    private RelatorioEstruturadoDTO relatorioDeTeste() {
        List<IndicadorRelatorioDTO> indicadores = List.of(
                new IndicadorRelatorioDTO("Total de Ações no PAT", "10"),
                new IndicadorRelatorioDTO("Média Geral de Execução", "23,91% (+2,01 pontos percentuais desde 16/09/2026)"));

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
                menorExecucao, destaques, pontosDeAcompanhamento);
    }
}
