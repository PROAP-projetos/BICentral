package com.bicentral.bicentral_backend.service.equipe;

import com.bicentral.bicentral_backend.dto.painel.PainelDTO;
import com.bicentral.bicentral_backend.exception.AutenticacaoException;
import com.bicentral.bicentral_backend.model.Equipe;
import com.bicentral.bicentral_backend.model.Painel;
import com.bicentral.bicentral_backend.model.Usuario;
import com.bicentral.bicentral_backend.repository.EquipeRepository;
import com.bicentral.bicentral_backend.repository.MembroEquipeRepository;
import com.bicentral.bicentral_backend.repository.PainelRepository;
import com.bicentral.bicentral_backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class PainelService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.bucket}")
    private String supabaseBucket;

    private final PainelRepository painelRepository;
    private final UsuarioRepository usuarioRepository;
    private final PowerBIScraperService scraperService;
    private final EquipeRepository equipeRepository;
    private final MembroEquipeRepository membroEquipeRepository;

    public PainelService(PainelRepository painelRepository,
                        UsuarioRepository usuarioRepository,
                        PowerBIScraperService scraperService,
                        EquipeRepository equipeRepository,
                        MembroEquipeRepository membroEquipeRepository) {
        this.painelRepository = painelRepository;
        this.usuarioRepository = usuarioRepository;
        this.scraperService = scraperService;
        this.equipeRepository = equipeRepository;
        this.membroEquipeRepository = membroEquipeRepository;
    }

    private PainelDTO toDTO(Painel painel) {
        PainelDTO dto = new PainelDTO();
        dto.setId(painel.getId());
        dto.setNome(painel.getNome());
        dto.setLinkPowerBi(painel.getLinkPowerBi());
        dto.setStatusCaptura(painel.getStatusCaptura());

        String path = painel.getImagemCapaUrl();
        if (path != null && !path.isEmpty()) {
            // Garante que a URL não termine com barra para não duplicar no link
            String urlBase = supabaseUrl.replaceAll("/$", "");

            if (path.startsWith("http")) {
                dto.setImagemCapaUrl(path);
            } else {
                // Monta o link público: URL + API Storage + Bucket + Caminho do arquivo
                String urlFinal = String.format("%s/storage/v1/object/public/%s/%s",
                        urlBase, supabaseBucket, path);
                dto.setImagemCapaUrl(urlFinal);
            }
        }
        return dto;
    }

    private Usuario getUsuarioLogado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || "anonymousUser".equals(auth.getName())) {
            throw new AutenticacaoException("Usuário não autenticado");
        }
        return usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AutenticacaoException("Usuário não encontrado"));
    }

    private Equipe buscarEquipeDoUsuario(Long equipeId, Usuario usuario) {
        Equipe equipe = equipeRepository.findById(equipeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipe não encontrada"));

        boolean membro = membroEquipeRepository.existsByEquipeIdAndUsuarioId(equipeId, usuario.getId());
        if (!membro) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário sem acesso à equipe informada");
        }

        return equipe;
    }

    private void validarLinkPowerBi(String link) {
        String prefixo = "https://app.powerbi.com/view?r=";
        if (link == null || !link.startsWith(prefixo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Link do Power BI inválido.");
        }
    }

    @Transactional
    public PainelDTO criarPainel(Long equipeId, Painel painel) {
        Usuario usuario = getUsuarioLogado();
        String link = painel.getLinkPowerBi().trim();
        validarLinkPowerBi(link);

        Equipe equipe = buscarEquipeDoUsuario(equipeId, usuario);

        if (painelRepository.existsByLinkPowerBiAndEquipeId(link, equipeId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Painel já cadastrado nesta equipe.");
        }

        painel.setEquipe(equipe);
        painel.setLinkPowerBi(link);
        painel.setStatusCaptura(Painel.StatusCaptura.PENDENTE);
        return toDTO(painelRepository.save(painel));
    }

    public List<PainelDTO> listarPorEquipe(Long equipeId) {
        Usuario usuario = getUsuarioLogado();

        return painelRepository.findByEquipeIdForMember(equipeId, usuario.getEmail())
                .stream().map(this::toDTO).toList();
    }

    public PainelDTO buscarPorIdAndEquipe(Long id, Long equipeId) {
        Usuario usuario = getUsuarioLogado();

        return painelRepository.findByIdAndEquipeIdForMember(id, equipeId, usuario.getEmail())
                .map(this::toDTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Painel não encontrado nesta equipe"));
    }

    @Transactional
    public PainelDTO atualizarPainel(Long id, Long equipeId, PainelDTO dto) {
        Usuario usuario = getUsuarioLogado();

        Painel painel = painelRepository.findByIdAndEquipeIdForMember(id, equipeId, usuario.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Painel não encontrado nesta equipe"));

        if (dto.getNome() != null) painel.setNome(dto.getNome().trim());

        if (dto.getLinkPowerBi() != null) {
            String novoLink = dto.getLinkPowerBi().trim();
            if (!novoLink.equals(painel.getLinkPowerBi())) {
                validarLinkPowerBi(novoLink);

                if (painelRepository.existsByLinkPowerBiAndEquipeIdAndIdNot(novoLink, equipeId, id)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Painel já cadastrado nesta equipe.");
                }

                painel.setLinkPowerBi(novoLink);
                painel.setStatusCaptura(Painel.StatusCaptura.PENDENTE);
                painel.setImagemCapaUrl(null);
                scraperService.capturaCapaAsync(id);
            }
        }
        return toDTO(painelRepository.save(painel));
    }

    @Transactional
    public void deletarPainel(Long id, Long equipeId) {
        Usuario usuario = getUsuarioLogado();

        Painel painel = painelRepository.findByIdAndEquipeIdForMember(id, equipeId, usuario.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Painel não encontrado nesta equipe"));
        painelRepository.delete(painel);
    }
}
