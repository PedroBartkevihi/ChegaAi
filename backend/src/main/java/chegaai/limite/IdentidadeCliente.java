package chegaai.limite;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;

@Component
public class IdentidadeCliente {
    public String ip(HttpServletRequest request) { return normalizarIp(request.getRemoteAddr()); }
    public static String email(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }

    /** Só aceita literais. Cabeçalhos Forwarded/XFF nunca são lidos aqui; Tomcat valida o proxy. */
    public static String normalizarIp(String value) {
        if (value == null || value.isBlank()) return "transporte-desconhecido";
        if (!value.contains(":")) {
            String[] partes = value.split("\\.", -1);
            if (partes.length != 4) return "transporte-desconhecido";
            int[] numeros = new int[4];
            for (int i = 0; i < partes.length; i++) {
                if (!partes[i].matches("[0-9]{1,3}")) return "transporte-desconhecido";
                numeros[i] = Integer.parseInt(partes[i]);
                if (numeros[i] > 255) return "transporte-desconhecido";
            }
            return numeros[0] + "." + numeros[1] + "." + numeros[2] + "." + numeros[3];
        }
        if (!value.matches("[0-9a-fA-F:.]+")) return "transporte-desconhecido";
        try {
            InetAddress endereco = InetAddress.getByName(value);
            if (endereco instanceof Inet4Address) return endereco.getHostAddress();
            byte[] prefixo = endereco.getAddress();
            Arrays.fill(prefixo, 8, 16, (byte) 0);
            return InetAddress.getByAddress(prefixo).getHostAddress() + "/64";
        } catch (UnknownHostException ex) { return "transporte-desconhecido"; }
    }
}
