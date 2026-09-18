package com.bicentral.bicentral_backend.service.ia.tools;

import com.bicentral.bicentral_backend.service.auth.UsuarioService;
import com.bicentral.bicentral_backend.service.ia.MemoriaUsuarioService;
import com.bicentral.bicentral_backend.state.EstadoSessao;
import com.bicentral.bicentral_backend.state.StatusExecucaoAgente;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class MemoriaTool {

    private final MemoriaUsuarioService memoriaUsuarioService;
    private final UsuarioService usuarioService;
    private final EstadoSessao estadoSessao;
    private final StatusExecucaoAgente statusExecucao;

    public MemoriaTool(MemoriaUsuarioService memoriaUsuarioService, UsuarioService usuarioService,
            EstadoSessao estadoSessao, StatusExecucaoAgente statusExecucao) {
        this.memoriaUsuarioService = memoriaUsuarioService;
        this.usuarioService = usuarioService;
        this.estadoSessao = estadoSessao;
        this.statusExecucao = statusExecucao;
    }

    @Tool("Salva uma preferência ou instrução permanente que o usuário logado pediu explicitamente pra você lembrar " +
          "(ex: 'sempre responda resumido', 'quando eu pedir tarefas atrasadas, mostra o responsável junto'). Só chame " +
          "quando o pedido for claramente pra guardar algo pra sempre, não pra essa resposta específica. Se a nova " +
          "preferência contradiz uma que já está listada em MEMÓRIA DO USUÁRIO no contexto, passe o id dela em " +
          "idParaSubstituir — a antiga é desativada automaticamente, nunca fica duplicada.")
    public String salvarPreferenciaUsuario(
            @P("tipo curto da preferência, ex: 'formato_resposta', 'instrucao_consulta', 'preferencia_geral'") String tipo,
            @P("o que deve ser lembrado, escrito de forma clara e reutilizável, em terceira pessoa (ex: 'Prefere respostas resumidas.')") String conteudo,
            @P(value = "id da memória existente que essa nova preferência substitui, se houver conflito com algo que já está em MEMÓRIA DO USUÁRIO no contexto", required = false) Long idParaSubstituir) {
        statusExecucao.definir("Salvando sua preferência...");
        Long usuarioId = usuarioIdAtual();
        memoriaUsuarioService.salvar(usuarioId, tipo, conteudo, idParaSubstituir);
        // Sinaliza pro frontend mostrar o chip animado de "memória atualizada" nessa resposta —
        // mesmo mecanismo do relatorioGerado (ver EstadoSessao/ProiapService/RespostaTextualDTO).
        estadoSessao.setMemoriaAtualizada(true);
        return "Preferência salva: " + conteudo;
    }

    @Tool("Lista as preferências e instruções que o usuário logado já pediu pra você lembrar. Use quando ele perguntar " +
          "'o que você lembra sobre mim', 'quais minhas preferências salvas' ou algo parecido.")
    public String listarMinhasMemorias() {
        statusExecucao.definir("Buscando suas preferências salvas...");
        Long usuarioId = usuarioIdAtual();
        List<Map<String, Object>> memorias = memoriaUsuarioService.listarAtivas(usuarioId);
        if (memorias.isEmpty()) {
            return "Nenhuma preferência salva ainda.";
        }
        StringBuilder texto = new StringBuilder();
        for (Map<String, Object> memoria : memorias) {
            texto.append("- ").append(memoria.get("conteudo")).append("\n");
        }
        return texto.toString();
    }

    private Long usuarioIdAtual() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioService.buscarPorEmail(email).getId();
    }
}
