package chegaai.service;

import chegaai.controller.dto.UsuarioResponseDTO;
import chegaai.domain.model.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

/** A API calcula os mesmos limites e selos usados na interface. */
@Service
public class RegrasEventoService {
    private final BigDecimal honraEmbaixador;
    private final BigDecimal honraMinimaCriacao;
    private final int limiteVagasPadrao;
    private final int limiteVagasEmbaixador;

    public RegrasEventoService(
            @Value("${chegaai.regras.honra-embaixador:4.8}") BigDecimal honraEmbaixador,
            @Value("${chegaai.regras.honra-minima-criacao:2.0}") BigDecimal honraMinimaCriacao,
            @Value("${chegaai.regras.limite-vagas-padrao:20}") int limiteVagasPadrao,
            @Value("${chegaai.regras.limite-vagas-embaixador:50}") int limiteVagasEmbaixador) {
        if (honraEmbaixador.compareTo(BigDecimal.ZERO) < 0
                || honraEmbaixador.compareTo(BigDecimal.valueOf(5)) > 0
                || honraMinimaCriacao.compareTo(BigDecimal.ZERO) < 0
                || honraMinimaCriacao.compareTo(BigDecimal.valueOf(5)) > 0
                || limiteVagasPadrao < 3 || limiteVagasEmbaixador < 3) {
            throw new IllegalArgumentException("Os limites de honra devem estar entre 0 e 5, e as capacidades devem ser no mínimo 3.");
        }
        this.honraEmbaixador = honraEmbaixador;
        this.honraMinimaCriacao = honraMinimaCriacao;
        this.limiteVagasPadrao = limiteVagasPadrao;
        this.limiteVagasEmbaixador = limiteVagasEmbaixador;
    }

    public BigDecimal honraMinimaCriacao() {
        return honraMinimaCriacao;
    }

    public boolean ehEmbaixador(Usuario usuario) {
        return usuario.qualificaEmbaixador(honraEmbaixador);
    }

    public int limiteVagas(Usuario usuario) {
        return ehEmbaixador(usuario) ? limiteVagasEmbaixador : limiteVagasPadrao;
    }

    public UsuarioResponseDTO respostaDoUsuario(Usuario usuario) {
        return UsuarioResponseDTO.de(usuario, ehEmbaixador(usuario), limiteVagas(usuario),
            usuario.podeCriarEvento(honraMinimaCriacao));
    }
}
