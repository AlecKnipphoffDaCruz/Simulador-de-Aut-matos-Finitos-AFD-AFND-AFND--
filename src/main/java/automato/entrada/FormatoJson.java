package automato.entrada;

import automato.modelo.Automato;
import automato.modelo.AutomatoInvalidoException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Converte o conteúdo de um arquivo JSON em {@link Automato}.
 *
 * <pre>
 * {
 *   "estados":    ["q0", "q1"],
 *   "alfabeto":   ["a", "b"],
 *   "inicial":    "q0",
 *   "finais":     ["q1"],
 *   "transicoes": [ {"de": "q0", "simbolo": "a", "para": ["q1"]} ]
 * }
 * </pre>
 */
final class FormatoJson {

    private static final List<String> CAMPOS = List.of("estados", "alfabeto", "inicial", "finais", "transicoes");
    private static final List<String> CAMPOS_TRANSICAO = List.of("de", "simbolo", "para");

    private FormatoJson() { }

    static Automato ler(String texto) throws AutomatoInvalidoException {
        Object raiz = LeitorJson.ler(texto);
        if (!(raiz instanceof Map<?, ?> mapa)) {
            throw new AutomatoInvalidoException("a raiz do JSON deve ser um objeto { ... }");
        }

        List<String> erros = new ArrayList<>();
        Automato.Construtor construtor = new Automato.Construtor();

        for (Object chave : mapa.keySet()) {
            if (!CAMPOS.contains(chave)) {
                erros.add("campo desconhecido: \"" + chave + "\" (campos válidos: " + CAMPOS + ")");
            }
        }
        for (String campo : CAMPOS) {
            if (!mapa.containsKey(campo)) {
                erros.add("campo obrigatório ausente: \"" + campo + "\"");
            }
        }

        if (mapa.containsKey("estados")) {
            listaDeTextos(mapa.get("estados"), "estados", erros).forEach(construtor::estado);
        }
        if (mapa.containsKey("alfabeto")) {
            listaDeTextos(mapa.get("alfabeto"), "alfabeto", erros).forEach(construtor::simbolo);
        }
        if (mapa.containsKey("finais")) {
            listaDeTextos(mapa.get("finais"), "finais", erros).forEach(construtor::estadoFinal);
        }
        if (mapa.containsKey("inicial")) {
            Object inicial = mapa.get("inicial");
            if (inicial instanceof String s) {
                construtor.inicial(s);
            } else {
                erros.add("\"inicial\" deve ser um texto entre aspas com o nome do estado" + dica(inicial));
            }
        }
        if (mapa.containsKey("transicoes")) {
            lerTransicoes(mapa.get("transicoes"), construtor, erros);
        }

        if (!erros.isEmpty()) {
            throw new AutomatoInvalidoException(erros);
        }
        return construtor.construir();
    }

    private static void lerTransicoes(Object valor, Automato.Construtor construtor, List<String> erros) {
        if (!(valor instanceof List<?> lista)) {
            erros.add("\"transicoes\" deve ser uma lista de objetos {\"de\", \"simbolo\", \"para\"}");
            return;
        }
        for (int i = 0; i < lista.size(); i++) {
            String origem = "transição #" + (i + 1);
            if (!(lista.get(i) instanceof Map<?, ?> t)) {
                erros.add(origem + ": deve ser um objeto {\"de\", \"simbolo\", \"para\"}");
                continue;
            }
            boolean ok = true;
            for (Object chave : t.keySet()) {
                if (!CAMPOS_TRANSICAO.contains(chave)) {
                    erros.add(origem + ": campo desconhecido \"" + chave + "\" (use " + CAMPOS_TRANSICAO + ")");
                    ok = false;
                }
            }
            for (String campo : CAMPOS_TRANSICAO) {
                if (!t.containsKey(campo)) {
                    erros.add(origem + ": campo obrigatório ausente: \"" + campo + "\"");
                    ok = false;
                }
            }
            if (!ok) {
                continue;
            }
            Object de = t.get("de");
            Object simbolo = t.get("simbolo");
            Object para = t.get("para");
            if (!(de instanceof String) || !(simbolo instanceof String)) {
                erros.add(origem + ": \"de\" e \"simbolo\" devem ser textos entre aspas" + dica(simbolo instanceof String ? de : simbolo));
                continue;
            }
            List<String> destinos;
            if (para instanceof String unico) {
                destinos = List.of(unico);
            } else {
                destinos = listaDeTextos(para, origem + ", campo \"para\"", erros);
            }
            construtor.transicao((String) de, (String) simbolo, destinos, origem);
        }
    }

    private static List<String> listaDeTextos(Object valor, String nomeCampo, List<String> erros) {
        if (!(valor instanceof List<?> lista)) {
            erros.add("\"" + nomeCampo + "\" deve ser uma lista de textos entre aspas");
            return List.of();
        }
        List<String> textos = new ArrayList<>();
        for (int i = 0; i < lista.size(); i++) {
            Object item = lista.get(i);
            if (item instanceof String s) {
                textos.add(s);
            } else {
                erros.add("\"" + nomeCampo + "\" posição " + (i + 1) + ": deve ser texto entre aspas" + dica(item));
            }
        }
        return textos;
    }

    /** Sugestão para o erro mais comum: escrever 0 / 1 sem aspas. */
    private static String dica(Object valor) {
        if (valor instanceof Double d) {
            String texto = d == Math.rint(d) ? String.valueOf(d.longValue()) : String.valueOf(d);
            return " (use \"" + texto + "\" com aspas)";
        }
        return "";
    }
}
