package chegaai;

import chegaai.limite.RelogioLimitesTeste;
import chegaai.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import java.time.Duration;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "chegaai.limites.login-ip.capacidade=3", "chegaai.limites.login-par.capacidade=2",
    "chegaai.limites.cadastro-ip.capacidade=2"
})
@Import(RelogioLimitesTeste.Config.class)
class LimiteAutenticacaoIT extends IntegracaoBase {
    @Autowired RelogioLimitesTeste relogio;
    @Autowired UsuarioRepository usuarios;
    @BeforeEach void avancar() { relogio.avancar(Duration.ofDays(2)); }

    @Test
    void tentativasDeLoginErradasEEmailNormalizadoGastamCotaAntesDeCredencialValida() throws Exception {
        var pessoa = cadastrar("CotaLogin");
        for (String email : new String[]{pessoa.email().toUpperCase(java.util.Locale.ROOT), pessoa.email()}) {
            mockMvc.perform(post("/auth/login").with(r -> { r.setRemoteAddr("192.0.2.10"); return r; })
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("email", email, "password", "senha-errada"))))
                .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/auth/login").with(r -> { r.setRemoteAddr("192.0.2.10"); return r; })
            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("email", pessoa.email(), "password", SENHA))))
            .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "30"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.status").value(429)).andExpect(jsonPath("$.message", not(containsString(pessoa.email()))));
        relogio.avancar(Duration.ofSeconds(30));
        mockMvc.perform(post("/auth/login").with(r -> { r.setRemoteAddr("192.0.2.10"); return r; })
            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("email", pessoa.email(), "password", SENHA))))
            .andExpect(status().isOk());
    }

    @Test
    void ipContaJsonEDtoInvalidosECors429ContinuaLegivelNoNavegador() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(options("/auth/login").header("Origin", "http://127.0.0.1:3000")
                .header("Access-Control-Request-Method", "POST")).andExpect(status().isOk());
        }
        for (String body : new String[]{"{", "{}", "{\"email\":\"invalido\",\"password\":\"\"}"}) {
            mockMvc.perform(post("/auth/login").with(r -> { r.setRemoteAddr("192.0.2.11"); return r; })
                .header("Origin", "http://127.0.0.1:3000").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/auth/login").with(r -> { r.setRemoteAddr("192.0.2.11"); return r; })
            .header("Origin", "http://127.0.0.1:3000").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isTooManyRequests()).andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:3000"))
            .andExpect(header().string("Access-Control-Expose-Headers", containsString("Retry-After")));
    }

    @Test
    void cadastroContaTentativasInvalidasSemPersistirContas() throws Exception {
        long antes = usuarios.count();
        for (int i = 0; i < 2; i++) mockMvc.perform(post("/auth/cadastro")
            .with(r -> { r.setRemoteAddr("192.0.2.12"); return r; }).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/auth/cadastro").with(r -> { r.setRemoteAddr("192.0.2.12"); return r; })
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "1800"));
        assertThat(usuarios.count()).isEqualTo(antes);
    }

    @Test
    void origensSemPermissaoNaoGastamCotaENaoLeemResposta() throws Exception {
        for (int i = 0; i < 5; i++) mockMvc.perform(post("/auth/login")
            .with(r -> { r.setRemoteAddr("192.0.2.13"); return r; }).header("Origin", "https://origem.invalid")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mockMvc.perform(post("/auth/login").with(r -> { r.setRemoteAddr("192.0.2.13"); return r; })
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void ipv6DaMesma64CompartilhaCotaSemAfetarOutraRedeOuLeitura() throws Exception {
        for (int i = 0; i < 3; i++) mockMvc.perform(post("/auth/login")
            .with(r -> { r.setRemoteAddr("2001:db8:1:2::1"); return r; }).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/auth/login").with(r -> { r.setRemoteAddr("2001:db8:1:2::abcd"); return r; })
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isTooManyRequests());
        mockMvc.perform(post("/auth/login").with(r -> { r.setRemoteAddr("2001:db8:1:3::1"); return r; })
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        for (int i = 0; i < 5; i++) mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
