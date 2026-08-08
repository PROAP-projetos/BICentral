package com.bicentral.bicentral_backend.controller.ia;

import com.bicentral.bicentral_backend.dto.ia.ChunkDTO;
import com.bicentral.bicentral_backend.model.Equipe;
import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.repository.EquipeRepository;
import com.bicentral.bicentral_backend.repository.MembroEquipeRepository;
import com.bicentral.bicentral_backend.service.auth.UsuarioService;
import com.bicentral.bicentral_backend.service.ia.AgenteProiap;
import com.bicentral.bicentral_backend.service.ia.ConsultaService;
import com.bicentral.bicentral_backend.service.ia.IngestaoService;

import dev.langchain4j.model.chat.ChatLanguageModel;
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
    private final ChatLanguageModel chatModel;
    private final AgenteProiap agenteProiap;

    public IaController(
            IngestaoService ingestaoService,
            EquipeRepository equipeRepository,
            MembroEquipeRepository membroEquipeRepository,
            UsuarioService usuarioService,
            ConsultaService consultaService,
            ChatLanguageModel chatModel,
            AgenteProiap agenteProiap) {
        this.ingestaoService = ingestaoService;
        this.equipeRepository = equipeRepository;
        this.membroEquipeRepository = membroEquipeRepository;
        this.usuarioService = usuarioService;
        this.consultaService = consultaService;
        this.chatModel = chatModel;
        this.agenteProiap = agenteProiap;
    }

    @PostMapping("/ingestao")
    public ResponseEntity<?> realizarIngestao(
            @RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam("equipe") String nomeEquipe,
            @RequestParam("visibilidade") String visibilidade) {
        Path tempFile = null;

        try {
            if (arquivo == null || arquivo.isEmpty()) {
                return ResponseEntity.badRequest().body("Arquivo vazio ou não enviado.");
            }

            String visibilidadeNormalizada = normalizarVisibilidade(visibilidade);
            String nomeArquivo = arquivo.getOriginalFilename();
            String extensao = obterExtensao(nomeArquivo);

            if (!extensao.equals(".pdf") && !extensao.equals(".txt") && !extensao.equals(".xlsx")
                    && !extensao.equals(".xls")) {
                return ResponseEntity.badRequest().body("Formato não suportado. Envie PDF, TXT, XLSX ou XLS.");
            }

            String emailUsuarioLogado = SecurityContextHolder.getContext().getAuthentication().getName();
            Usuario usuarioLogado = usuarioService.buscarPorEmail(emailUsuarioLogado);

            Equipe equipe = equipeRepository.findByNome(nomeEquipe)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Equipe não encontrada: " + nomeEquipe));

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
            List<ChunkDTO> chunksProntos = ingestaoService.executarIngestao(
                    caminhoArquivo,
                    nomeArquivo,
                    equipe.getNome(),
                    visibilidadeNormalizada,
                    equipe.getId());

            if (chunksProntos.isEmpty() || chunksProntos.stream().allMatch(chunk -> chunk.getConteudo().isBlank())) {
                return ResponseEntity.badRequest().body("Não foi possível extrair texto útil do documento.");
            }

            return ResponseEntity.ok(Map.of(
                    "mensagem", "Documento enviado para ingestão.",
                    "status", "PROCESSANDO",
                    "totalChunks", chunksProntos.size(),
                    "arquivo", nomeArquivo,
                    "equipe", equipe.getNome(),
                    "visibilidade", visibilidadeNormalizada));
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
            String pregunta = String.valueOf(body.getOrDefault("pergunta", "")).trim();
            Long equipeId = obterLong(body.get("equipeId"));

            if (pregunta.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("mensagem", "Informe a pergunta."));
            }

            Equipe equipe = validarAcessoEquipe(equipeId);
            List<String> contextos = consultaService.buscar(pregunta, equipe.getId());
            String resposta = gerarResposta(pregunta, contextos, equipe.getNome());

            // --- BLOCO DE FILTRO SIMPLIFICADO E ATUALIZADO ---
            String textoPergunta = pregunta.toLowerCase();

            // Verifica se a mensagem se parece com uma saudação comum de até 30 caracteres
            boolean ehSaudacao = (textoPergunta.contains("oi") || textoPergunta.contains("olá") ||
                    textoPergunta.contains("ola") || textoPergunta.contains("bom dia") ||
                    textoPergunta.contains("boa tarde") || textoPergunta.contains("tudo bem"))
                    && pregunta.length() < 30;

            // Verifica se a resposta disparou a Regra de Ouro (Frase padrão de erro)
            boolean ehRespostaPadrao = resposta.contains("Desculpe, não encontrei");

            // Se for saudação OU se o documento não tinha a resposta, limpa as fontes da
            // tela
            if (ehSaudacao || ehRespostaPadrao) {
                contextos.clear();
            }
            // -------------------------------------------------

            return ResponseEntity.ok(Map.of(
                    "pergunta", pregunta,
                    "resposta", resposta,
                    "contextos", contextos,
                    "modelo", "Llama 3 (via Groq)",
                    "equipe", equipe.getNome()));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("mensagem", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("mensagem", "Erro ao consultar agente: " + e.getMessage()));
        }
    }

    @GetMapping("/fontes")
    public ResponseEntity<?> listarFontes(@RequestParam Long equipeId) {
        try {
            Equipe equipe = validarAcessoEquipe(equipeId);
            return ResponseEntity.ok(Map.of(
                    "fontes", consultaService.listarFontes(equipe.getId()),
                    "modelo", Map.of(
                            "nome", "Llama 3 (Groq)",
                            "detalhes", "via API Groq · local-onnx-embedding"),
                    "equipe", equipe.getNome()));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("mensagem", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("mensagem", "Erro ao listar fontes: " + e.getMessage()));
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
            return "Desculpe, não encontrei essa informação nos documentos institucionais acessíveis no momento.";
        }

        String contextoUnido = String.join("\n\n---\n\n", contextos);

        return agenteProiap.responderDuvida(pergunta, contextoUnido);
    }
}