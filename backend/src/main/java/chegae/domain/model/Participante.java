package chegae.domain.model;

import chegae.domain.enums.PapelSistema;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.NoArgsConstructor;

/**
 * Especialização de {@link Usuario} que representa o papel padrão na plataforma.
 *
 * <p>Um Participante pode:</p>
 * <ul>
 *   <li>Entrar em fila de espera, confirmar presença e cancelar inscrição (UC11, RF002)</li>
 *   <li>Avaliar outros participantes de eventos concluídos (UC06)</li>
 *   <li>Publicar comentários em eventos cujos quais está inscrito ou na fila (RN006)</li>
 *   <li>Criar eventos, desde que honraMedia ≥ 2.0 (RN004) e sem avaliações pendentes (RN007)</li>
 *   <li>Tornar-se Organizador no contexto dos eventos que cria (papel contextual)</li>
 * </ul>
 *
 * <p>Quando a honraMedia atingir ≥ 4.8 (RN003), o serviço promove o usuário
 * para {@link Embaixador} atualizando o valor da coluna discriminadora
 * {@code papel_sistema}.</p>
 */
@Entity
@DiscriminatorValue("PARTICIPANTE")
@NoArgsConstructor
public class Participante extends Usuario {

    /**
     * Construtor completo delegado ao construtor da superclasse.
     * Utilizado pelos testes e pela camada de serviço.
     */
    public Participante(String nome, String email, String cpf,
                        String senhaHash, java.time.LocalDate dataNascimento) {
        super();
        setNome(nome);
        setEmail(email);
        setCpf(cpf);
        setSenhaHash(senhaHash);
        setDataNascimento(dataNascimento);
    }

    /**
     * Retorna {@link PapelSistema#PARTICIPANTE}.
     *
     * @return papel PARTICIPANTE
     */
    @Override
    public PapelSistema getPapelSistema() {
        return PapelSistema.PARTICIPANTE;
    }
}