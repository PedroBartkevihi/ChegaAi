package chegaai.service.external;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.time.Duration;
import java.util.Optional;
@Service
@Slf4j
public class ViaCEPService {
    private final RestClient restClient;
    // Com tempo limite: sem ele, um ViaCEP lento prenderia a requisição (e a thread) indefinidamente.
    public ViaCEPService(@Value("${chegaai.viacep.url}") String viaCepBaseUrl,
                         @Value("${chegaai.viacep.tempo-conexao:2s}") Duration tempoConexao,
                         @Value("${chegaai.viacep.tempo-resposta:3s}") Duration tempoResposta) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(tempoConexao);
        fabrica.setReadTimeout(tempoResposta);
        this.restClient = RestClient.builder()
            .baseUrl(viaCepBaseUrl.replaceAll("/+$", ""))
            .requestFactory(fabrica)
            .build();
    }
    // O ViaCEP manda outros campos (unidade, estado, regiao...) que não interessam aqui.
    @JsonIgnoreProperties(ignoreUnknown = true)
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
        // CEP inexistente: o ViaCEP responde {"erro": "true"} (antes, {"erro": true}).
        public String erro;
        public boolean cepInvalido() {
            return "true".equalsIgnoreCase(erro);
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
            ViaCEPResponse resposta = restClient.get()
                .uri("/{cep}/json/", cepLimpo)
                .retrieve()
                .body(ViaCEPResponse.class);
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
