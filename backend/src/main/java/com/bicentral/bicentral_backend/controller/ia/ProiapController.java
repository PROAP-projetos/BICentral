package com.bicentral.bicentral_backend.controller.ia;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.bicentral.bicentral_backend.dto.ia.MensagemHistoricoDTO;
import com.bicentral.bicentral_backend.dto.ia.SessaoCompartilhadaDTO;
import com.bicentral.bicentral_backend.dto.ia.SessaoResumoDTO;
import com.bicentral.bicentral_backend.dto.notificacao.NotificacaoDTO;
import com.bicentral.bicentral_backend.dto.notificacao.PainelAtrasosDTO;
import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.state.EstadoSessao;
import com.bicentral.bicentral_backend.state.StatusExecucaoAgente;

import dev.ai4j.openai4j.chat.ChatCompletionChoice;

import com.bicentral.bicentral_backend.service.admin.AdminService;
import com.bicentral.bicentral_backend.service.auth.UsuarioService;
import com.bicentral.bicentral_backend.service.ia.ChatHistoricoService;
import com.bicentral.bicentral_backend.service.ia.ProiapService;
import com.bicentral.bicentral_backend.service.notificacao.NotificacaoService;


import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@RestController
@RequestMapping("/api/proiap")
public class ProiapController {

    // Compartilhado entre requisições (não por sessão) só pra rodar a pergunta numa thread
    // que dá pra interromper — quem serializa por sessão é o EstadoSessao.aguardarTerminoSeAtivo().
    private static final ExecutorService EXECUTOR_PERGUNTAS = Executors.newCachedThreadPool();

    private final ProiapService proiapService;
    private final EstadoSessao estadoSessao;
    private final StatusExecucaoAgente statusExecucao;
    private final NotificacaoService notificacaoService;
    private final UsuarioService usuarioService;
    private final AdminService adminService;
    private final ChatHistoricoService chatHistoricoService;

    public ProiapController(ProiapService proiapService, EstadoSessao estadoSessao, StatusExecucaoAgente statusExecucao,
        NotificacaoService notificacaoService, UsuarioService usuarioService, AdminService adminService,
        ChatHistoricoService chatHistoricoService) {
        this.proiapService = proiapService;
        this.estadoSessao = estadoSessao;
        this.statusExecucao = statusExecucao;
        this.notificacaoService = notificacaoService;
        this.usuarioService = usuarioService;
        this.adminService = adminService;
        this.chatHistoricoService = chatHistoricoService;
    }

    public record RequisicaoProiap(String texto, Long equipeId, String modelo, String sessaoId) {
    }

    @PostMapping("/perguntar")
    public Object fazerPergunta(@RequestBody RequisicaoProiap requisicao,
    @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão expirada, faça login novamente");
        }

        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        boolean usuarioEhAdmin = adminService.isAdmin(usuario.getId());

        if (requisicao.modelo() != null) {
            estadoSessao.setModelo(requisicao.modelo());
        }

        if (requisicao.equipeId() != null) {
            estadoSessao.setEquipeId(requisicao.equipeId());
        }

        // Se a pergunta anterior dessa sessão acabou de ser cancelada e ainda está desligando,
        // espera aqui — nunca duas threads mexendo no mesmo EstadoSessao ao mesmo tempo.
        estadoSessao.aguardarTerminoSeAtivo();

        // EstadoSessao é @SessionScope: o proxy do Spring só resolve a instância de verdade
        // olhando a requisição "amarrada" na thread atual (RequestContextHolder). Rodar
        // processarPergunta numa thread nova do executor, sem propagar isso, faz QUALQUER
        // acesso ao estadoSessao lá dentro estourar IllegalStateException — precisa copiar
        // manualmente os atributos da requisição pra thread nova.
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();

        // Mesmo motivo do RequestAttributes acima: SecurityContextHolder (usado por ferramentas
        // que precisam saber quem está logado, ex: buscarMinhasTarefas, solicitarGeracaoRelatorio)
        // também é preso à thread original por padrão — sem propagar isso, essas ferramentas
        // falham com "falha de autenticação na sessão" na thread nova do executor.
        SecurityContext securityContext = SecurityContextHolder.getContext();

        // Reseta pro texto padrão antes de começar — sem isso, a etapa da pergunta ANTERIOR
        // ficaria visível por um instante até a primeira ferramenta desta pergunta ser chamada
        // (ou pra sempre, se esta pergunta não chamar ferramenta nenhuma).
        statusExecucao.reiniciar();

        Future<Object> execucao = EXECUTOR_PERGUNTAS.submit(() -> {
            RequestContextHolder.setRequestAttributes(requestAttributes, true);
            SecurityContextHolder.setContext(securityContext);
            try {
                return proiapService.processarPergunta(requisicao.texto(), requisicao.sessaoId(), usuarioEhAdmin, usuario.getId());
            } finally {
                // statusExecucao é @SessionScope — precisa ser tocado ANTES de soltar a requisição
                // da thread (resetRequestAttributes), senão o proxy de sessão não consegue mais
                // resolver a instância e estoura IllegalStateException aqui dentro do finally,
                // mascarando o resultado de verdade com um erro genérico.
                statusExecucao.reiniciar();
                RequestContextHolder.resetRequestAttributes();
                SecurityContextHolder.clearContext();
            }
        });
        estadoSessao.registrarExecucao(execucao);

        try {
            return execucao.get();
        } catch (CancellationException | InterruptedException e) {
            Thread.currentThread().interrupt();
            // Ninguém lê essa resposta — o frontend já desistiu da conexão original quando
            // clicou em parar — mas a requisição em si precisa devolver algo.
            return Map.of("cancelado", true);
        } catch (ExecutionException e) {
            Throwable causa = e.getCause();
            if (causa instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao processar pergunta", causa);
        }
    }

    // Chamado pelo botão de parar do chat: cancela a pergunta em andamento nessa sessão
    // (best-effort — interrompe a thread, mas nem todo cliente HTTP aborta a chamada em
    // andamento por interrupção) e espera ela desligar antes de responder.
    @PostMapping("/cancelar")
    public void cancelarPergunta() {
        estadoSessao.cancelarExecucaoAtual();
        statusExecucao.reiniciar();
    }

    public record StatusExecucaoResponse(String etapa) {}

    // O frontend faz polling aqui enquanto "carregando" — dá o feedback de qual ferramenta
    // está rodando agora em vez de um "Pensando" parado (ver StatusExecucaoAgente).
    @GetMapping("/status-execucao")
    public StatusExecucaoResponse obterStatusExecucao() {
        return new StatusExecucaoResponse(statusExecucao.getEtapaAtual());
    }

    // Endpoint de debug (deixa ver a notificação de qualquer usuário, não só a sua) —
    // por isso exige admin, mesmo já exigindo login por padrão em todo /api/proiap/**.
    @GetMapping("/testar-notificacoes/{usuarioId}")
    public List<NotificacaoDTO> testarNotificacoes(@PathVariable Long usuarioId, @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuarioLogado = usuarioService.buscarPorEmail(userDetails.getUsername());
        adminService.exigirAdmin(usuarioLogado.getId());
        return notificacaoService.gerarNotificacoes(usuarioId);
    }

    // O botão "ver painel completo" na notificação de tarefas atrasadas chamava essa rota,
    // mas ela nunca tinha sido exposta aqui — o DTO e o service (NotificacaoService.
    // gerarPainelAtrasos) já existiam prontos, só faltava esse @GetMapping.
    @GetMapping("/painel-atrasos")
    public PainelAtrasosDTO painelAtrasos(@RequestParam String departamento) {
        return notificacaoService.gerarPainelAtrasos(departamento);
    }

    @GetMapping("/sessoes")
    public List<SessaoResumoDTO> listarSessoes(@AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        return chatHistoricoService.listarSessoes(usuario.getId());
    }

    @GetMapping("/sessoes/{sessaoId}/mensagens")
    public List<MensagemHistoricoDTO> listarMensagens(@PathVariable String sessaoId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        return chatHistoricoService.listarMensagens(sessaoId, usuario.getId());
    }

    @DeleteMapping("/sessoes/{sessaoId}")
    public void excluirSessao(@PathVariable String sessaoId, @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        chatHistoricoService.excluirSessao(sessaoId, usuario.getId());
    }

    public record TituloRequestDTO(String titulo) {}

    @PatchMapping("/sessoes/{sessaoId}/titulo")
    public void renomearSessao(@PathVariable String sessaoId, @RequestBody TituloRequestDTO requisicao,
            @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        chatHistoricoService.renomear(sessaoId, usuario.getId(), requisicao.titulo());
    }

    public record FlagRequestDTO(boolean valor) {}

    @PatchMapping("/sessoes/{sessaoId}/fixar")
    public void fixarSessao(@PathVariable String sessaoId, @RequestBody FlagRequestDTO requisicao,
            @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        chatHistoricoService.fixar(sessaoId, usuario.getId(), requisicao.valor());
    }

    public record TokenResponseDTO(String token) {}

    @PostMapping("/sessoes/{sessaoId}/compartilhar")
    public TokenResponseDTO compartilharSessao(@PathVariable String sessaoId, @AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
        return new TokenResponseDTO(chatHistoricoService.compartilhar(sessaoId, usuario.getId()));
    }

    // Rota pública (ver "/api/proiap/compartilhado/**" no SecurityConfig) — quem abre o link
    // não precisa ter conta nem estar logado no BICentral.
    @GetMapping("/compartilhado/{token}")
    public SessaoCompartilhadaDTO buscarCompartilhada(@PathVariable String token) {
        return chatHistoricoService.buscarCompartilhada(token);
    }
}