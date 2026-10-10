package chegaai.domain.model;
import chegaai.domain.enums.PapelSistema;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.NoArgsConstructor;
@Entity
@DiscriminatorValue("PARTICIPANTE")
@NoArgsConstructor
public class Participante extends Usuario {
    public Participante(String nome, String email, String cpf,
                        String senhaHash, java.time.LocalDate dataNascimento) {
        super();
        setNome(nome);
        setEmail(email);
        setCpf(cpf);
        setSenhaHash(senhaHash);
        setDataNascimento(dataNascimento);
    }
    @Override
    public PapelSistema getPapelSistema() {
        return PapelSistema.PARTICIPANTE;
    }
}