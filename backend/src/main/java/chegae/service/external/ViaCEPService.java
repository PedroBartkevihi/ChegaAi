package chegae.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

/**
 * Serviço de integração com a API pública ViaCEP.
 *
 * <p>Consulta o endpoint {@code https://viacep.com.br/ws/{cep}/json/} para
 * obter dados de logradouro, bairro, cidade e estado a partir de um CEP
 * informado pelo organizador durante a criação do evento (UC03, ator externo
 * "API de Localização" referenciado na seção 3 do documento de requisitos).</p>
 *
 * <h3>Fluxo de exceção (FE1 – UC03)</h3>
 * <p>Se a API estiver indisponível ou o CEP for inválido, o método retorna
 * {@link Optional#empty()}, permitindo que o {@link EventoService} aplique
 * o fluxo alternativo: preenchimento manual pelo usuário.</p>
 *
 * <h3>Exemplo de resposta ViaCEP</h3>
 * <pre>
 * {
 *   "cep":        "22210-030",
 *   "logradouro": "Aterro do Flamengo",
 *   "complemento":"",
 *   "bairro":     "Flamengo",
 *   "localidade": "Rio de Janeiro",
 *   "uf":         "RJ",
 *   "ibge":       "3304557",
 *   "gia":        "",
 *   "ddd":        "21",
 *   "siafi":      "6001"
 * }
 * </pre>
 */
@Service
@Slf4j
public class ViaCEPService {

    private static final String VIACEP_URL = "https://viacep.com.br/ws/{cep}/json/";

    private final RestTemplate restTemplate;

    public ViaCEPService() {
        this.restTemplate = new RestTemplate();
    }

    // -------------------------------------------------------------------------
    // DTO interno para deserialização da resposta da ViaCEP
    // -------------------------------------------------------------------------

    /**
     * Representação plana da resposta JSON retornada pela API ViaCEP.
     * Todos os campos são Strings para refletir fielmente o contrato da API.
     */
    public static class ViaCEPResponse {
        public String cep;
        public String logradouro;
        public String complemento;
        public String bairro;
        public String localidade;   // Cidade
        public String uf;           // Estado (sigla)
        public String ibge;
        public String gia;
        public String ddd;
        public String siafi;
        public Boolean erro;        // Presente e true quando o CEP não existe

        /** @return {@code true} se a ViaCEP sinalizou que o CEP não foi encontrado. */
        public boolean cepInvalido() {
            return Boolean.TRUE.equals(erro);
        }
    }

    // -------------------------------------------------------------------------
    // Método principal
    // -------------------------------------------------------------------------

    /**
     * Consulta os dados de endereço associados ao {@code cep} fornecido.
     *
     * <p>O CEP deve conter exatamente 8 dígitos numéricos (sem hífen).
     * A validação de formato é feita antes da chamada HTTP.</p>
     *
     * @param cep CEP no formato {@code "XXXXXXXX"} (8 dígitos, sem hífen)
     * @return {@link Optional} com os dados do endereço, ou {@code empty} se:
     *         <ul>
     *           <li>o CEP tiver formato inválido;</li>
     *           <li>a API retornar {@code {"erro": true}};</li>
     *           <li>a API estiver indisponível (timeout, rede, etc.).</li>
     *         </ul>
     */
    public Optional<ViaCEPResponse> buscarEnderecoPorCep(String cep) {
        // 1. Limpar e validar formato
        if (cep == null) {
            log.warn("ViaCEP: CEP nulo recebido.");
            return Optional.empty();
        }

        String cepLimpo = cep.replaceAll("[^0-9]", "");

        if (cepLimpo.length() != 8) {
            log.warn("ViaCEP: CEP '{}' inválido (deve ter 8 dígitos).", cep);
            return Optional.empty();
        }

        // 2. Consultar a API
        try {
            log.info("ViaCEP: consultando CEP '{}'...", cepLimpo);
            ViaCEPResponse resposta = restTemplate.getForObject(VIACEP_URL, ViaCEPResponse.class, cepLimpo);

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
            // FE1 (UC03): API indisponível — permite preenchimento manual
            log.error("ViaCEP: falha ao consultar CEP '{}': {}", cepLimpo, e.getMessage());
            return Optional.empty();
        }
    }
}