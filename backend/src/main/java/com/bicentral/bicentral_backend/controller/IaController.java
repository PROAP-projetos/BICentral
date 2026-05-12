package com.bicentral.bicentral_backend.controller;

import com.bicentral.bicentral_backend.model.Equipe;
import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.repository.EquipeRepository;
import com.bicentral.bicentral_backend.repository.MembroEquipeRepository;
import com.bicentral.bicentral_backend.service.ConsultaService;
import com.bicentral.bicentral_backend.service.IngestaoService;
import com.bicentral.bicentral_backend.service.UsuarioService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/ia")
public class IaController {

    private final IngestaoService ingestaoService;
    private final EquipeRepository equipeRepository;
    private final MembroEquipeRepository membroEquipeRepository;
    private final UsuarioService usuarioService;
    private final ConsultaService consultaService;
    private final ChatLanguageModel geminiModel;

    public IaController(
            IngestaoService ingestaoService,
            EquipeRepository equipeRepository,
            MembroEquipeRepository membroEquipeRepository,
            UsuarioService usuarioService,
            ConsultaService consultaService,
            @Qualifier("geminiModel") ChatLanguageModel geminiModel) {
        this.ingestaoService = ingestaoService;
        this.equipeRepository = equipeRepository;
        this.membroEquipeRepository = membroEquipeRepository;
        this.usuarioService = usuarioService;
        this.consultaService = consultaService;
        this.geminiModel = geminiModel;
    }

    @PostMapping("/ingestao")
    public ResponseEntity<?> realizarIngestao(
            @RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam("equipe") String nomeEquipe,
            @RequestParam("visibilidade") String visibilidade
    ) {
        Path tempFile = null;

        try {
            if (arquivo == null || arquivo.isEmpty()) {
                return ResponseEntity.badRequest().body("Arquivo vazio ou não enviado.");
            }

            String visibilidadeNormalizada = normalizarVisibilidade(visibilidade);
            String nomeArquivo = arquivo.getOriginalFilename();
            String extensao = obterExtensao(nomeArquivo);

            if (!extensao.equals(".pdf") && !extensao.equals(".xlsx")) {
                return ResponseEntity.badRequest().body("Formato não suportado. Envie PDF ou XLSX.");
            }

            String emailUsuarioLogado = SecurityContextHolder.getContext().getAuthentication().getName();
            Usuario usuarioLogado = usuarioService.buscarPorEmail(emailUsuarioLogado);

            Equipe equipe = equipeRepository.findByNome(nomeEquipe)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Equipe não encontrada: " + nomeEquipe
                    ));

            boolean usuarioPertenceEquipe = membroEquipeRepository
                    .findByUsuarioAndEquipe(usuarioLogado, equipe)
                    .isPresent();

            if (!usuarioPertenceEquipe) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("Usuário não tem permissão para esta equipe.");
            }

            tempFile = Files.createTempFile("ingestao_", extensao);
            arquivo.transferTo(tempFile.toFile());

            String caminhoArquivo = tempFile.toAbsolutePath().toString();
            String textoBruto = extensao.equals(".xlsx")
                    ? ingestaoService.extrairTextoExcel(caminhoArquivo)
                    : ingestaoService.extrairTextoPDF(caminhoArquivo);

            String textoLimpo = ingestaoService.limparTexto(textoBruto);
            List<String> chunks = ingestaoService.fatiarTexto(textoLimpo);

            if (chunks.isEmpty() || chunks.stream().allMatch(String::isBlank)) {
                return ResponseEntity.badRequest().body("Não foi possível extrair texto útil do documento.");
            }

            ingestaoService.salvarNoBanco(
                    chunks,
                    equipe.getNome(),
                    visibilidadeNormalizada,
                    nomeArquivo,
                    equipe.getId()
            );

            return ResponseEntity.ok(Map.of(
                    "mensagem", "Documento enviado para ingestão.",
                    "status", "PROCESSANDO",
                    "totalChunks", chunks.size(),
                    "arquivo", nomeArquivo,
                    "equipe", equipe.getNome(),
                    "visibilidade", visibilidadeNormalizada
            ));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erro interno na ingestão: " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @PostMapping("/consulta")
    public ResponseEntity<?> consultar(@RequestBody Map<String, Object> body) {
        try {
            String pergunta = String.valueOf(body.getOrDefault("pergunta", "")).trim();
            Long equipeId = obterLong(body.get("equipeId"));

            if (pergunta.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("mensagem", "Informe a pergunta."));
            }

            Equipe equipe = validarAcessoEquipe(equipeId);
            List<String> contextos = consultaService.buscar(pergunta, equipe.getId());
            String resposta = gerarResposta(pergunta, contextos, equipe.getNome());

            return ResponseEntity.ok(Map.of(
                    "pergunta", pergunta,
                    "resposta", resposta,
                    "contextos", contextos,
                    "modelo", "gemini-2.5-flash",
                    "equipe", equipe.getNome()
            ));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("mensagem", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("mensagem", "Erro ao consultar agente: " + e.getMessage()));
        }
    }

    @GetMapping("/fontes")
    public ResponseEntity<?> listarFontes(@RequestParam Long equipeId) {
        try {
            Equipe equipe = validarAcessoEquipe(equipeId);
            return ResponseEntity.ok(Map.of(
                    "fontes", consultaService.listarFontes(equipe.getId()),
                    "modelo", Map.of(
                            "nome", "Gemini 2.5 Flash",
                            "detalhes", "via API · gemini-embedding-001"
                    ),
                    "equipe", equipe.getNome()
            ));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("mensagem", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("mensagem", "Erro ao listar fontes: " + e.getMessage()));
        }
    }

    private String normalizarVisibilidade(String visibilidade) {
        if (visibilidade == null || visibilidade.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a visibilidade do documento.");
        }

        String valor = visibilidade.trim().toUpperCase(Locale.ROOT);
        if (!valor.equals("PUBLICO") && !valor.equals("PRIVADO")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Visibilidade deve ser PUBLICO ou PRIVADO.");
        }

        return valor;
    }

    private String obterExtensao(String nomeArquivo) {
        if (nomeArquivo == null || !nomeArquivo.contains(".")) {
            return ".tmp";
        }

        return nomeArquivo.substring(nomeArquivo.lastIndexOf(".")).toLowerCase(Locale.ROOT);
    }

    private Long obterLong(Object valor) {
        if (valor == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a equipe.");
        }

        if (valor instanceof Number numero) {
            return numero.longValue();
        }

        try {
            return Long.parseLong(String.valueOf(valor));
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Equipe inválida.");
        }
    }

    private Equipe validarAcessoEquipe(Long equipeId) {
        String emailUsuarioLogado = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuarioLogado = usuarioService.buscarPorEmail(emailUsuarioLogado);

        Equipe equipe = equipeRepository.findById(equipeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipe não encontrada."));

        boolean usuarioPertenceEquipe = membroEquipeRepository
                .findByUsuarioAndEquipe(usuarioLogado, equipe)
                .isPresent();

        if (!usuarioPertenceEquipe) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário não tem permissão para esta equipe.");
        }

        return equipe;
    }

    private String gerarResposta(String pergunta, List<String> contextos, String equipe) {
        if (contextos == null || contextos.isEmpty()) {
            return "Não encontrei informação suficiente nos documentos disponíveis da equipe " + equipe + " para responder essa pergunta.";
        }

        String contexto = String.join("\n\n---\n\n", contextos);
        String prompt = """
                Você é o agente de IA do BICentral.
                Responda em português do Brasil, de forma objetiva, usando apenas o contexto abaixo.
                Se o contexto não tiver informação suficiente, diga que não encontrou a informação nos documentos disponíveis.

                Equipe: %s
                Pergunta: %s

                Contexto:
                %s
                """.formatted(equipe, pergunta, contexto);

        return geminiModel.chat(prompt);
    }
}
