package chegaai.service.external;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import chegaai.service.EventoService;
import java.util.Optional;
@Service
@Slf4j
public class ViaCEPService {
    private final RestTemplate restTemplate;
    @Value("${chegaai.viacep.url}")
    private String viaCepBaseUrl;
    public ViaCEPService() {
        this.restTemplate = new RestTemplate();
    }
    public static class ViaCEPResponse {
        public String cep;
        public String logradouro;
        public String complemento;
        public String bairro;
        public String localidade;   
        public String uf;           
        public String ibge;
        public String gia;
        public String ddd;
        public String siafi;
        public Boolean erro;        
        public boolean cepInvalido() {
            return Boolean.TRUE.equals(erro);
        }
    }
    public Optional<ViaCEPResponse> buscarEnderecoPorCep(String cep) {
        if (cep == null) {
            log.warn("ViaCEP: CEP nulo recebido.");
            return Optional.empty();
        }
        String cepLimpo = cep.replaceAll("[^0-9]", "");
        if (cepLimpo.length() != 8) {
            log.warn("ViaCEP: CEP '{}' inválido (deve ter 8 dígitos).", cep);
            return Optional.empty();
        }
        try {
            log.info("ViaCEP: consultando CEP '{}'...", cepLimpo);
            String url = viaCepBaseUrl.replaceAll("/+$", "") + "/{cep}/json/";
            ViaCEPResponse resposta = restTemplate.getForObject(url, ViaCEPResponse.class, cepLimpo);
            if (resposta == null) {
                log.warn("ViaCEP: resposta nula para o CEP '{}'.", cepLimpo);
                return Optional.empty();
            }
            if (resposta.cepInvalido()) {
                log.warn("ViaCEP: CEP '{}' não encontrado (erro: true).", cepLimpo);
                return Optional.empty();
            }
            log.info("ViaCEP: endereço encontrado para CEP '{}': {}, {}/{}",
                    cepLimpo, resposta.logradouro, resposta.localidade, resposta.uf);
            return Optional.of(resposta);
        } catch (RestClientException e) {
            log.error("ViaCEP: falha ao consultar CEP '{}': {}", cepLimpo, e.getMessage());
            return Optional.empty();
        }
    }
}
