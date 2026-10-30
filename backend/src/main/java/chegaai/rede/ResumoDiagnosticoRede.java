package chegaai.rede;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Só tipos controlados saem deste resumo. Cabeçalhos, hosts e IPs públicos nunca são campos. */
record ResumoDiagnosticoRede(String estrategia, Classe baseClasse, String basePeerInterno,
        String esquema, boolean resolvidoMarcador, boolean esperadoValido, boolean esperadoResolvido,
        Cadeia xff, Cadeia forwarded) {
    enum Classe { LOOPBACK, LINKLOCAL, PRIVADO, PUBLICO, INVALIDO }
    record Cadeia(int componentes, List<Integer> marcadores, List<Classe> classes,
                  List<Integer> esperadoPosicoes, boolean truncado) {}
    private static final int MAX_COMPONENTES = 16;
    private static final int MAX_CARACTERES = 2048;

    static ResumoDiagnosticoRede observar(HttpServletRequest base, HttpServletRequest resolvido, String estrategia) {
        Ip peer = ip(base.getRemoteAddr());
        Ip destino = ip(resolvido.getRemoteAddr());
        Ip esperado = ip(base.getHeader("X-Chegaai-Cliente-Esperado"));
        if (esperado == null || esperado.classe() != Classe.PUBLICO || esperado.marcador()) esperado = null;
        String esquema = switch (resolvido.getScheme()) { case "http", "https" -> resolvido.getScheme(); default -> "outro"; };
        return new ResumoDiagnosticoRede(estrategia, classe(peer),
            peer != null && peer.classe() != Classe.PUBLICO ? peer.endereco().getHostAddress() : "omitido",
            esquema, destino != null && destino.marcador(), esperado != null, iguais(esperado, destino),
            cadeia(base, "X-Forwarded-For", false, esperado), cadeia(base, "Forwarded", true, esperado));
    }

    private static Cadeia cadeia(HttpServletRequest request, String nome, boolean forwarded, Ip esperado) {
        var marcadores = new ArrayList<Integer>();
        var classes = new ArrayList<Classe>();
        var esperadoPosicoes = new ArrayList<Integer>();
        var headers = request.getHeaders(nome);
        int caracteres = 0;
        boolean truncado = false;
        // Limita tanto os valores repetidos quanto os componentes e o trabalho de parsing.
        while (headers != null && headers.hasMoreElements()) {
            String valor = headers.nextElement();
            if (valor == null) continue;
            int tamanho = Math.min(valor.length(), MAX_CARACTERES - caracteres);
            if (tamanho < valor.length()) truncado = true;
            int inicio = 0;
            boolean aspas = false;
            for (int i = 0; i <= tamanho; i++) {
                if (i < tamanho && valor.charAt(i) == '"') aspas = !aspas;
                if (i == tamanho || valor.charAt(i) == ',' && !aspas) {
                    if (classes.size() == MAX_COMPONENTES) { truncado = true; break; }
                    // Não interpretar o fragmento cortado pelo limite como componente completo.
                    if (i == tamanho && tamanho < valor.length()) break;
                    String componente = valor.substring(inicio, i).trim();
                    Ip endereco = ip(forwarded ? extrairFor(componente) : componente);
                    classes.add(classe(endereco));
                    if (endereco != null && endereco.marcador()) marcadores.add(classes.size());
                    if (iguais(esperado, endereco)) esperadoPosicoes.add(classes.size());
                    inicio = i + 1;
                }
            }
            caracteres += tamanho;
            if (truncado || caracteres >= MAX_CARACTERES || classes.size() >= MAX_COMPONENTES) {
                truncado |= headers.hasMoreElements();
                break;
            }
        }
        return new Cadeia(classes.size(), List.copyOf(marcadores), List.copyOf(classes),
            List.copyOf(esperadoPosicoes), truncado);
    }

    private static String extrairFor(String componente) {
        for (String parametro : componente.split(";", -1)) {
            int igual = parametro.indexOf('=');
            if (igual > 0 && "for".equalsIgnoreCase(parametro.substring(0, igual).trim())) {
                String valor = parametro.substring(igual + 1).trim();
                if (valor.startsWith("\"") && valor.endsWith("\"") && valor.length() >= 2)
                    valor = valor.substring(1, valor.length() - 1);
                if (valor.startsWith("[")) {
                    int fim = valor.indexOf(']');
                    if (fim < 0) return null;
                    String resto = valor.substring(fim + 1);
                    if (!resto.isEmpty() && !resto.matches(":[0-9]{1,5}")) return null;
                    return valor.substring(1, fim);
                }
                // Porta opcional só para IPv4; IPv6 sem colchetes permanece literal.
                if (valor.indexOf(':') == valor.lastIndexOf(':') && valor.indexOf('.') >= 0 && valor.indexOf(':') > 0) {
                    int porta = valor.indexOf(':');
                    if (!valor.substring(porta).matches(":[0-9]{1,5}")) return null;
                    return valor.substring(0, porta);
                }
                return valor;
            }
        }
        return null;
    }

    private record Ip(InetAddress endereco, Classe classe, boolean marcador) {}
    private static Classe classe(Ip ip) { return ip == null ? Classe.INVALIDO : ip.classe(); }
    private static boolean iguais(Ip a, Ip b) {
        return a != null && b != null && Arrays.equals(a.endereco().getAddress(), b.endereco().getAddress());
    }

    private static Ip ip(String literal) {
        if (literal == null || literal.length() > 64 || literal.isEmpty()) return null;
        // Antes de InetAddress, só aceita literais numéricos. Não há resolução de hostname ou zona.
        byte[] v4 = ipv4(literal);
        InetAddress endereco;
        try {
            if (v4 != null) endereco = InetAddress.getByAddress(v4);
            // No JDK 17, um ponto inicial pode encaminhar getByName ao resolvedor de nomes.
            else if (literal.indexOf(':') >= 0 && literal.matches("[0-9a-fA-F:][0-9a-fA-F:.]*")) endereco = InetAddress.getByName(literal);
            else return null;
        } catch (UnknownHostException | IllegalArgumentException e) { return null; }
        byte[] bytes = endereco.getAddress();
        Classe classe;
        if (endereco.isLoopbackAddress()) classe = Classe.LOOPBACK;
        else if (endereco.isLinkLocalAddress()) classe = Classe.LINKLOCAL;
        else if (endereco.isSiteLocalAddress() || bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc) classe = Classe.PRIVADO;
        else classe = Classe.PUBLICO;
        boolean marcador = bytes.length == 4 && ((bytes[0] & 255) == 192 && (bytes[1] & 255) == 0 && (bytes[2] & 255) == 2
            || (bytes[0] & 255) == 198 && (bytes[1] & 255) == 51 && (bytes[2] & 255) == 100
            || (bytes[0] & 255) == 203 && (bytes[1] & 255) == 0 && (bytes[2] & 255) == 113)
            || bytes.length == 16 && (bytes[0] & 255) == 0x20 && (bytes[1] & 255) == 1 && (bytes[2] & 255) == 0x0d && (bytes[3] & 255) == 0xb8;
        return new Ip(endereco, classe, marcador);
    }

    private static byte[] ipv4(String literal) {
        String[] partes = literal.split("\\.", -1);
        if (partes.length != 4) return null;
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            if (!partes[i].matches("[0-9]{1,3}")) return null;
            int valor = Integer.parseInt(partes[i]);
            if (valor > 255) return null;
            bytes[i] = (byte) valor;
        }
        return bytes;
    }
}
