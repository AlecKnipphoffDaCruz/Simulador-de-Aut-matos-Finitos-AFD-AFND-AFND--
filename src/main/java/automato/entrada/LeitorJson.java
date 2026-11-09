package automato.entrada;

import automato.modelo.AutomatoInvalidoException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitor de JSON mínimo (sem dependências externas).
 *
 * <p>Devolve: {@code Map<String,Object>} para objetos, {@code List<Object>} para
 * listas, {@code String}, {@code Double}, {@code Boolean} ou {@code null}.
 * Em caso de erro, informa a linha e a coluna do problema.
 */
final class LeitorJson {

    private static final int PROFUNDIDADE_MAXIMA = 100;

    private final String t;
    private int p = 0;
    private int profundidade = 0;

    private LeitorJson(String texto) {
        this.t = texto;
    }

    static Object ler(String texto) throws AutomatoInvalidoException {
        LeitorJson leitor = new LeitorJson(texto);
        leitor.pularEspacos();
        if (leitor.fim()) {
            throw leitor.erro("o arquivo está vazio");
        }
        Object valor = leitor.valor();
        leitor.pularEspacos();
        if (!leitor.fim()) {
            throw leitor.erro("conteúdo inesperado depois do fim do JSON");
        }
        return valor;
    }

    private AutomatoInvalidoException erro(String mensagem) {
        int linha = 1;
        int coluna = 1;
        for (int i = 0; i < p && i < t.length(); i++) {
            if (t.charAt(i) == '\n') {
                linha++;
                coluna = 1;
            } else {
                coluna++;
            }
        }
        return new AutomatoInvalidoException(
                "JSON malformado (linha " + linha + ", coluna " + coluna + "): " + mensagem);
    }

    private boolean fim() {
        return p >= t.length();
    }

    private void pularEspacos() {
        while (!fim() && " \t\r\n".indexOf(t.charAt(p)) >= 0) {
            p++;
        }
    }

    private Object valor() throws AutomatoInvalidoException {
        pularEspacos();
        if (fim()) {
            throw erro("fim inesperado do arquivo (era esperado um valor)");
        }
        char c = t.charAt(p);
        return switch (c) {
            case '{' -> objeto();
            case '[' -> lista();
            case '"' -> texto();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> {
                if (c == '-' || Character.isDigit(c)) {
                    yield numero();
                }
                throw erro("caractere inesperado '" + c + "'");
            }
        };
    }

    private void entrar() throws AutomatoInvalidoException {
        if (++profundidade > PROFUNDIDADE_MAXIMA) {
            throw erro("estrutura aninhada demais");
        }
    }

    private Map<String, Object> objeto() throws AutomatoInvalidoException {
        entrar();
        Map<String, Object> mapa = new LinkedHashMap<>();
        p++; // '{'
        pularEspacos();
        if (!fim() && t.charAt(p) == '}') {
            p++;
            profundidade--;
            return mapa;
        }
        while (true) {
            pularEspacos();
            if (fim() || t.charAt(p) != '"') {
                throw erro("era esperado o nome de um campo entre aspas");
            }
            String chave = texto();
            pularEspacos();
            if (fim() || t.charAt(p) != ':') {
                throw erro("era esperado ':' depois do campo \"" + chave + "\"");
            }
            p++;
            Object valor = valor();
            if (mapa.containsKey(chave)) {
                throw erro("campo repetido: \"" + chave + "\"");
            }
            mapa.put(chave, valor);
            pularEspacos();
            if (fim()) {
                throw erro("objeto não fechado (era esperado ',' ou '}')");
            }
            char c = t.charAt(p);
            if (c == ',') {
                p++;
            } else if (c == '}') {
                p++;
                profundidade--;
                return mapa;
            } else {
                throw erro("era esperado ',' ou '}' mas foi encontrado '" + c + "'");
            }
        }
    }

    private List<Object> lista() throws AutomatoInvalidoException {
        entrar();
        List<Object> itens = new ArrayList<>();
        p++; // '['
        pularEspacos();
        if (!fim() && t.charAt(p) == ']') {
            p++;
            profundidade--;
            return itens;
        }
        while (true) {
            itens.add(valor());
            pularEspacos();
            if (fim()) {
                throw erro("lista não fechada (era esperado ',' ou ']')");
            }
            char c = t.charAt(p);
            if (c == ',') {
                p++;
            } else if (c == ']') {
                p++;
                profundidade--;
                return itens;
            } else {
                throw erro("era esperado ',' ou ']' mas foi encontrado '" + c + "'");
            }
        }
    }

    private String texto() throws AutomatoInvalidoException {
        p++; // aspas de abertura
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (fim()) {
                throw erro("texto entre aspas não foi fechado");
            }
            char c = t.charAt(p++);
            if (c == '"') {
                return sb.toString();
            }
            if (c < 0x20) {
                throw erro("quebra de linha ou caractere de controle dentro de um texto (faltou fechar as aspas?)");
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (fim()) {
                throw erro("sequência de escape incompleta");
            }
            char e = t.charAt(p++);
            switch (e) {
                case '"', '\\', '/' -> sb.append(e);
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (p + 4 > t.length()) {
                        throw erro("escape \\u incompleto");
                    }
                    try {
                        sb.append((char) Integer.parseInt(t.substring(p, p + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw erro("escape \\u inválido");
                    }
                    p += 4;
                }
                default -> throw erro("escape inválido: \\" + e);
            }
        }
    }

    private Object numero() throws AutomatoInvalidoException {
        int inicio = p;
        while (!fim() && "+-0123456789.eE".indexOf(t.charAt(p)) >= 0) {
            p++;
        }
        try {
            return Double.parseDouble(t.substring(inicio, p));
        } catch (NumberFormatException ex) {
            p = inicio;
            throw erro("número inválido");
        }
    }

    private Object literal(String esperado, Object valor) throws AutomatoInvalidoException {
        if (t.startsWith(esperado, p)) {
            p += esperado.length();
            return valor;
        }
        throw erro("valor inválido (era esperado " + esperado + ", um texto entre aspas, número, lista ou objeto)");
    }
}
