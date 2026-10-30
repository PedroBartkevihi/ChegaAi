package chegaai.limite;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;

class IdentidadeClienteTest {
    @Test
    void ipv6AgrupaPor64ECabecalhosNaoEscolhemIdentidade() {
        String prefixo = IdentidadeCliente.normalizarIp("2001:db8:1:2::1");
        assertThat(prefixo).endsWith("/64");
        assertThat(IdentidadeCliente.normalizarIp("2001:0db8:0001:0002:abcd::77")).isEqualTo(prefixo);
        assertThat(IdentidadeCliente.normalizarIp("2001:db8:1:3::1")).isNotEqualTo(prefixo);
        assertThat(IdentidadeCliente.normalizarIp("::ffff:192.0.2.1")).isEqualTo("192.0.2.1");
        assertThat(IdentidadeCliente.normalizarIp("192.000.002.001")).isEqualTo("192.0.2.1");
        var req = new MockHttpServletRequest(); req.setRemoteAddr("192.0.2.1");
        req.addHeader("Forwarded", "for=198.51.100.1"); req.addHeader("X-Forwarded-For", "198.51.100.2");
        assertThat(new IdentidadeCliente().ip(req)).isEqualTo("192.0.2.1");
        assertThat(IdentidadeCliente.normalizarIp("qualquer-host.invalid")).isEqualTo("transporte-desconhecido");
        assertThat(IdentidadeCliente.email("  ALGUEM@EXAMPLE.INVALID  ")).isEqualTo("alguem@example.invalid");
    }
    @Test
    void cloudRunExigeConfiguracaoExplicitaSemConfiancaAmpla() {
        ConfiguracaoLimitacao.validarProxy(null, "native", "(?!)");
        assertThatThrownBy(() -> ConfiguracaoLimitacao.validarProxy("servico", "native", "(?!)")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ConfiguracaoLimitacao.validarProxy("servico", "framework", "127\\.0\\.0\\.1")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ConfiguracaoLimitacao.validarProxy("servico", "native", ".*")).isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> ConfiguracaoLimitacao.validarProxy("servico", "native", "127\\.0\\.0\\.1")).doesNotThrowAnyException();
    }
}
