package com.bicentral.bicentral_backend.controller;

import com.bicentral.bicentral_backend.dto.ChunkDTO;
import com.bicentral.bicentral_backend.model.Equipe;
import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.repository.EquipeRepository;
import com.bicentral.bicentral_backend.repository.MembroEquipeRepository;
import com.bicentral.bicentral_backend.service.EmbeddingService;
import com.bicentral.bicentral_backend.service.IngestaoService;
import com.bicentral.bicentral_backend.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ia")
public class IaController {

    @Autowired
    private IngestaoService ingestaoService;

    @Autowired
    private EquipeRepository equipeRepository;

    @Autowired
    private MembroEquipeRepository membroEquipeRepository;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private EmbeddingService embeddingService;

    @PostMapping("/ingestao")
    public ResponseEntity<?> realizarIngestao(
            @RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam("equipe") String nomeEquipe,
            @RequestParam("visibilidade") String visibilidade
    ) {
        Path tempFile = null;
        try {
            if (arquivo == null || arquivo.isEmpty()) {
                return ResponseEntity.badRequest().body("Arquivo vazio ou não enviado!");
            }

            // 1. Validação de Segurança e Equipe
            String emailUsuarioLogado = SecurityContextHolder.getContext().getAuthentication().getName();
            Usuario usuarioLogado = usuarioService.buscarPorEmail(emailUsuarioLogado);

            Equipe equipe = equipeRepository.findByNome(nomeEquipe)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipe não encontrada: " + nomeEquipe));

            // Verifica se o usuário pertence à equipe
            boolean isMembro = membroEquipeRepository.findByUsuarioAndEquipe(usuarioLogado, equipe).isPresent();
            if (!isMembro) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Usuário não tem permissão para esta equipe.");
            }

            // 2. Salvar arquivo temporariamente para processar
            String originalFilename = arquivo.getOriginalFilename();
            String extensao = originalFilename != null && originalFilename.contains(".") 
                    ? originalFilename.substring(originalFilename.lastIndexOf(".")) 
                    : ".tmp";
            
            tempFile = Files.createTempFile("ingestao_", extensao);
            arquivo.transferTo(tempFile.toFile());

            String caminhoArquivo = tempFile.toAbsolutePath().toString();
            String textoBruto;

            // 3. Extrair texto baseado na extensão
            if (caminhoArquivo.toLowerCase().endsWith(".xlsx")) {
                textoBruto = ingestaoService.extrairTextoExcel(caminhoArquivo);
            } else {
                textoBruto = ingestaoService.extrairTextoPDF(caminhoArquivo);
            }

            // 4. Limpar e fatiar o texto (Processo de Chunking)
            String textoLimpo = ingestaoService.limparTexto(textoBruto);
            List<String> chunks = ingestaoService.fatiarTexto(textoLimpo);

            // 5. Integração com o Pipeline de IA
            // O IngestaoService agora orquestra a criação de metadados e o salvamento real via EmbeddingService
            ingestaoService.salvarNoBanco(chunks, equipe.getNome(), visibilidade, originalFilename, equipe.getId());

            return ResponseEntity.ok(Map.of(
                    "mensagem", "Documento enviado para ingestão e processado com sucesso.",
                    "status", "PROCESSANDO",
                    "totalChunks", chunks.size(),
                    "arquivo", originalFilename,
                    "equipe", equipe.getNome(),
                    "visibilidade", visibilidade
            ));

        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Erro interno na ingestão: " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {}
            }
        }
    }
}
