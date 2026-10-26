package com.bicentral.bicentral_backend.service.ia.tools;

import com.bicentral.bicentral_backend.dto.diretrizes.DiretrizesAcaoDTO;
import com.bicentral.bicentral_backend.dto.diretrizes.IndicadorInepDTO;
import com.bicentral.bicentral_backend.dto.diretrizes.SituacaoAlinhamento;
import com.bicentral.bicentral_backend.service.ia.AlinhamentoDiretrizesService;
import com.bicentral.bicentral_backend.service.ia.DiretrizesService;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Agrega os vereditos gravados pelo AlinhamentoDiretrizesJob num texto para o agente. */
final class PanoramaAlinhamento {

    /** {@code situacao} null = tarefa ainda não analisada (ou alterada depois da análise). */
    record Linha(long id, String departamento, String codigoAcao, String titulo, String situacao,
                 String diretrizes, String justificativa, OffsetDateTime analisadoEm) {
    }

    static final int MAX_DEPARTAMENTOS = 15;
    static final int MAX_DIRETRIZES_COBERTAS = 10;
    static final int MAX_DIRETRIZES_SEM_COBERTURA = 30;
    static final int MAX_TAREFAS_SEM_EVIDENCIA = 15;
    static final int MAX_DESTAQUES = 5;
    private static final int MIN_TAREFAS_DEPARTAMENTO = 5;

    private static final String ALINHADA = SituacaoAlinhamento.ALINHADA.name();
    private static final String PARCIAL = SituacaoAlinhamento.PARCIAL.name();
    private static final String SEM_EVIDENCIA = SituacaoAlinhamento.SEM_EVIDENCIA.name();

    private PanoramaAlinhamento() {
    }

    private static final class Contagem {
        int alinhadas;
        int parciais;
        int semEvidencia;

        void somar(String situacao) {
            if (ALINHADA.equals(situacao)) alinhadas++;
            else if (PARCIAL.equals(situacao)) parciais++;
            else if (SEM_EVIDENCIA.equals(situacao)) semEvidencia++;
        }

        int avaliadas() {
            return alinhadas + parciais + semEvidencia;
        }
    }

    private static final class Cobertura {
        final String nome;
        final Set<String> acoes = new LinkedHashSet<>();
        int alinhadas;
        int parciais;

        Cobertura(String nome) {
            this.nome = nome;
        }
    }

    static String montar(String escopo, List<Linha> linhas, DiretrizesService base) {
        List<Linha> analisadas = linhas.stream().filter(l -> l.situacao() != null).toList();
        if (analisadas.isEmpty()) {
            return "PANORAMA AINDA NÃO GERADO para " + escopo + " (" + linhas.size() + " tarefa(s)): a análise tarefa por"
                    + " tarefa ainda não foi processada. Se o pedido tiver recorte (departamento, pessoa ou ação), chame"
                    + " compararTarefasComDiretrizes com o mesmo recorte. Se for o PAT inteiro, diga ao usuário que o"
                    + " panorama geral ainda não foi gerado e ofereça a análise por departamento.";
        }

        Contagem total = new Contagem();
        int semDiretriz = 0;
        Map<String, Contagem> porDepartamento = new TreeMap<>();
        for (Linha linha : analisadas) {
            if (AlinhamentoDiretrizesService.SEM_DIRETRIZ.equals(linha.situacao())) {
                semDiretriz++;
                continue;
            }
            total.somar(linha.situacao());
            porDepartamento.computeIfAbsent(Objects.toString(linha.departamento(), "(sem departamento)"), d -> new Contagem())
                    .somar(linha.situacao());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("PANORAMA DE ALINHAMENTO INEP/IESGo (base ").append(base.versao()).append(") — ").append(escopo)
          .append(": ").append(linhas.size()).append(" tarefa(s), ").append(analisadas.size()).append(" analisada(s)");
        analisadas.stream().map(Linha::analisadoEm).filter(Objects::nonNull).max(Comparator.naturalOrder())
                .ifPresent(d -> sb.append(", análise mais recente em ").append(formatarData(d)));
        sb.append(".\n");
        if (analisadas.size() < linhas.size()) {
            sb.append("COBERTURA PARCIAL: ").append(linhas.size() - analisadas.size())
              .append(" tarefa(s) ainda sem análise (novas ou alteradas). Diga isso ao usuário.\n");
        }
        sb.append("\n");

        int avaliadas = total.avaliadas();
        sb.append("Situação das ").append(avaliadas).append(" tarefa(s) em ações com diretrizes:\n")
          .append("- Alinhadas: ").append(total.alinhadas).append(" (").append(pct(total.alinhadas, avaliadas)).append(")\n")
          .append("- Parciais: ").append(total.parciais).append(" (").append(pct(total.parciais, avaliadas)).append(")\n")
          .append("- Sem evidência: ").append(total.semEvidencia).append(" (").append(pct(total.semEvidencia, avaliadas)).append(")\n");
        if (semDiretriz > 0) {
            sb.append("Fora da conta: ").append(semDiretriz).append(" tarefa(s) em ações sem diretrizes INEP/IESGo na base.\n");
        }
        sb.append("\n");

        anexarDepartamentos(sb, porDepartamento);
        anexarDiretrizes(sb, linhas, analisadas, base, analisadas.size() < linhas.size());
        anexarTarefas(sb, analisadas);

        sb.append("Esta é uma análise do texto das tarefas frente às diretrizes, gravada tarefa por tarefa; não é avaliação")
          .append(" oficial do INEP ou do TCU. Ofereça detalhar um departamento, uma ação ou uma pessoa com")
          .append(" compararTarefasComDiretrizes.\n");
        return sb.toString();
    }

    private static void anexarDepartamentos(StringBuilder sb, Map<String, Contagem> porDepartamento) {
        if (porDepartamento.size() < 2) {
            return;
        }
        List<Map.Entry<String, Contagem>> elegiveis = porDepartamento.entrySet().stream()
                .filter(e -> e.getValue().avaliadas() >= MIN_TAREFAS_DEPARTAMENTO)
                .sorted(Comparator.<Map.Entry<String, Contagem>>comparingDouble(
                        e -> (double) e.getValue().alinhadas / e.getValue().avaliadas()).thenComparing(Map.Entry::getKey))
                .toList();
        if (elegiveis.isEmpty()) {
            return;
        }
        List<Map.Entry<String, Contagem>> mostrados = elegiveis;
        if (elegiveis.size() > MAX_DEPARTAMENTOS) {
            mostrados = new ArrayList<>(elegiveis.subList(0, 10));
            mostrados.addAll(elegiveis.subList(elegiveis.size() - 5, elegiveis.size()));
            sb.append("Por departamento (os 10 com menor e os 5 com maior proporção de tarefas alinhadas, de ")
              .append(elegiveis.size()).append(" com ").append(MIN_TAREFAS_DEPARTAMENTO).append("+ tarefas avaliadas):\n");
        } else {
            sb.append("Por departamento (do menor para o maior % de alinhadas):\n");
        }
        sb.append("| Departamento | Tarefas | Alinhadas | Parciais | Sem evidência |\n|---|---|---|---|---|\n");
        for (Map.Entry<String, Contagem> e : mostrados) {
            Contagem c = e.getValue();
            sb.append("| ").append(e.getKey()).append(" | ").append(c.avaliadas())
              .append(" | ").append(pct(c.alinhadas, c.avaliadas()))
              .append(" | ").append(pct(c.parciais, c.avaliadas()))
              .append(" | ").append(pct(c.semEvidencia, c.avaliadas())).append(" |\n");
        }
        sb.append("\n");
    }

    private static void anexarDiretrizes(StringBuilder sb, List<Linha> linhas, List<Linha> analisadas,
                                         DiretrizesService base, boolean parcial) {
        // Universo: diretrizes ligadas às ações que têm tarefa no recorte.
        Map<String, Cobertura> coberturas = new LinkedHashMap<>();
        for (Linha linha : linhas) {
            DiretrizesAcaoDTO acao = base.buscar(linha.codigoAcao()).orElse(null);
            if (!DiretrizesService.temDiretrizes(acao)) {
                continue;
            }
            Map<String, String> nomesInep = new LinkedHashMap<>();
            if (acao.inepDetalhes() != null) {
                for (IndicadorInepDTO indicador : acao.inepDetalhes()) {
                    nomesInep.put("INEP " + indicador.codigo(), indicador.indicador());
                }
            }
            for (String codigo : DiretrizesService.codigos(acao)) {
                coberturas.computeIfAbsent(codigo, c -> new Cobertura(nomesInep.get(c))).acoes.add(linha.codigoAcao());
            }
        }
        for (Linha linha : analisadas) {
            if (linha.diretrizes() == null || linha.diretrizes().isBlank()) {
                continue;
            }
            for (String codigo : linha.diretrizes().split(";\\s*")) {
                Cobertura cobertura = coberturas.get(codigo.trim());
                if (cobertura == null) continue;
                if (ALINHADA.equals(linha.situacao())) cobertura.alinhadas++;
                else if (PARCIAL.equals(linha.situacao())) cobertura.parciais++;
            }
        }
        if (coberturas.isEmpty()) {
            return;
        }

        List<Map.Entry<String, Cobertura>> cobertas = coberturas.entrySet().stream()
                .filter(e -> e.getValue().alinhadas > 0)
                .sorted(Comparator.<Map.Entry<String, Cobertura>>comparingInt(e -> -e.getValue().alinhadas)
                        .thenComparing(Map.Entry::getKey, DiretrizesTool::compararCodigo))
                .toList();
        List<Map.Entry<String, Cobertura>> descobertas = coberturas.entrySet().stream()
                .filter(e -> e.getValue().alinhadas == 0 && e.getValue().parciais == 0)
                .sorted(Comparator.<Map.Entry<String, Cobertura>>comparingInt(e -> -e.getValue().acoes.size())
                        .thenComparing(Map.Entry::getKey, DiretrizesTool::compararCodigo))
                .toList();

        sb.append("Diretrizes ligadas às ações do recorte: ").append(coberturas.size()).append("; com tarefa alinhada: ")
          .append(cobertas.size()).append("; sem nenhuma tarefa alinhada ou parcial: ").append(descobertas.size()).append(".\n");
        if (!cobertas.isEmpty()) {
            sb.append("Mais cobertas (tarefas alinhadas):\n");
            for (Map.Entry<String, Cobertura> e : cobertas.subList(0, Math.min(MAX_DIRETRIZES_COBERTAS, cobertas.size()))) {
                sb.append("- ").append(rotulo(e)).append(": ").append(e.getValue().alinhadas).append(" alinhada(s), ")
                  .append(e.getValue().parciais).append(" parcial(is)\n");
            }
        }
        if (!descobertas.isEmpty()) {
            sb.append("SEM COBERTURA (nenhuma tarefa alinhada ou parcial; ponto mais útil para o gestor")
              .append(parcial ? ", mas a cobertura da análise é parcial" : "").append("):\n");
            for (Map.Entry<String, Cobertura> e : descobertas.subList(0, Math.min(MAX_DIRETRIZES_SEM_COBERTURA, descobertas.size()))) {
                Set<String> acoes = e.getValue().acoes;
                sb.append("- ").append(rotulo(e)).append(" — ").append(acoes.size()).append(" ação(ões) vinculada(s): ")
                  .append(String.join(", ", acoes.stream().limit(5).toList()))
                  .append(acoes.size() > 5 ? "..." : "").append("\n");
            }
            if (descobertas.size() > MAX_DIRETRIZES_SEM_COBERTURA) {
                sb.append("(e mais ").append(descobertas.size() - MAX_DIRETRIZES_SEM_COBERTURA).append(" diretriz(es) sem cobertura)\n");
            }
        }
        sb.append("\n");
    }

    private static void anexarTarefas(StringBuilder sb, List<Linha> analisadas) {
        List<Linha> semEvidencia = analisadas.stream().filter(l -> SEM_EVIDENCIA.equals(l.situacao())).toList();
        if (!semEvidencia.isEmpty()) {
            sb.append("Tarefas sem evidência (").append(semEvidencia.size()).append(" no total")
              .append(semEvidencia.size() > MAX_TAREFAS_SEM_EVIDENCIA ? ", primeiras " + MAX_TAREFAS_SEM_EVIDENCIA : "")
              .append("):\n");
            for (Linha l : semEvidencia.subList(0, Math.min(MAX_TAREFAS_SEM_EVIDENCIA, semEvidencia.size()))) {
                sb.append("- ").append(l.codigoAcao()).append(" \"").append(l.titulo()).append("\" (").append(l.departamento())
                  .append("): ").append(Objects.toString(l.justificativa(), "")).append("\n");
            }
            sb.append("\n");
        }
        List<Linha> alinhadas = analisadas.stream().filter(l -> ALINHADA.equals(l.situacao())).limit(MAX_DESTAQUES).toList();
        if (!alinhadas.isEmpty()) {
            sb.append("Exemplos de tarefas alinhadas:\n");
            for (Linha l : alinhadas) {
                sb.append("- ").append(l.codigoAcao()).append(" \"").append(l.titulo()).append("\" (").append(l.departamento())
                  .append(") — ").append(l.diretrizes()).append(": ").append(Objects.toString(l.justificativa(), "")).append("\n");
            }
            sb.append("\n");
        }
    }

    private static String rotulo(Map.Entry<String, Cobertura> e) {
        return e.getValue().nome == null ? e.getKey() : e.getKey() + " " + e.getValue().nome;
    }

    private static String pct(int parte, int todo) {
        return todo == 0 ? "0%" : Math.round(100.0 * parte / todo) + "%";
    }

    private static String formatarData(OffsetDateTime data) {
        return data.atZoneSameInstant(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}
