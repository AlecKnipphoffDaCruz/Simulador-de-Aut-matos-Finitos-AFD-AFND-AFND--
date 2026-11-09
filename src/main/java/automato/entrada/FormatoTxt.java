package automato.entrada;

import automato.modelo.Automato;
import automato.modelo.AutomatoInvalidoException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converte o conteúdo de um arquivo .txt em {@link Automato}.
 *
 * <pre>
 * # comentário (só vale se o # for o primeiro caractere da linha)
 * estados: q0 q1 q2
 * alfabeto: a b
 * inicial: q0
 * finais: q2
 * transicoes:
 * q0 a q1
 * q1 ε q2 q0        (vários destinos = não determinismo; ε = transição vazia)
 * </pre>
 */
final class FormatoTxt {

    private static final Pattern DIRETIVA = Pattern.compile("^(\\p{L}+)\\s*:\\s*(.*)$");
    private static final List<String> OBRIGATORIAS = List.of("estados", "alfabeto", "inicial", "finais", "transicoes");

    private FormatoTxt() { }

    static Automato ler(String texto) throws AutomatoInvalidoException {
        List<String> erros = new ArrayList<>();
        Automato.Construtor construtor = new Automato.Construtor();
        Set<String> vistas = new HashSet<>();
        boolean emTransicoes = false;

        String[] linhas = texto.split("\\R", -1);
        for (int i = 0; i < linhas.length; i++) {
            String onde = "linha " + (i + 1);
            String linha = linhas[i].strip();
            if (linha.isEmpty() || linha.startsWith("#")) {
                continue;
            }

            Matcher m = DIRETIVA.matcher(linha);
            String chave = m.matches() ? normalizar(m.group(1)) : null;
            boolean diretivaConhecida = chave != null && OBRIGATORIAS.contains(chave);

            if (diretivaConhecida) {
                if (!vistas.add(chave)) {
                    erros.add(onde + ": a diretiva '" + chave + ":' aparece mais de uma vez");
                    continue;
                }
                List<String> valores = tokens(m.group(2));
                switch (chave) {
                    case "estados" -> valores.forEach(construtor::estado);
                    case "alfabeto" -> valores.forEach(construtor::simbolo);
                    case "finais" -> valores.forEach(construtor::estadoFinal);
                    case "inicial" -> {
                        if (valores.size() == 1) {
                            construtor.inicial(valores.get(0));
                        } else {
                            erros.add(onde + ": 'inicial:' deve ter exatamente um estado");
                        }
                    }
                    default -> { // transicoes
                        emTransicoes = true;
                        if (!valores.isEmpty()) {
                            erros.add(onde + ": coloque as transições nas linhas seguintes a 'transicoes:'");
                        }
                    }
                }
            } else if (emTransicoes) {
                List<String> partes = new ArrayList<>(tokens(linha));
                if (partes.size() >= 4 && partes.get(2).equals("->")) {
                    partes.remove(2); // aceita "q0 a -> q1" como alternativa a "q0 a q1"
                }
                if (partes.size() < 3) {
                    erros.add(onde + ": transição inválida — use: <origem> <símbolo> <destino> [<destino> ...]");
                } else {
                    construtor.transicao(partes.get(0), partes.get(1), partes.subList(2, partes.size()), onde);
                }
            } else if (chave != null) {
                erros.add(onde + ": diretiva desconhecida '" + chave + ":' (use " + OBRIGATORIAS + ")");
            } else {
                erros.add(onde + ": linha não reconhecida: \"" + linha + "\"");
            }
        }

        for (String obrigatoria : OBRIGATORIAS) {
            if (!vistas.contains(obrigatoria)) {
                erros.add("diretiva obrigatória ausente: '" + obrigatoria + ":'");
            }
        }
        if (!erros.isEmpty()) {
            throw new AutomatoInvalidoException(erros);
        }
        return construtor.construir();
    }

    private static List<String> tokens(String texto) {
        String limpo = texto.strip();
        return limpo.isEmpty() ? List.of() : Arrays.asList(limpo.split("\\s+"));
    }

    private static String normalizar(String chave) {
        String minuscula = chave.toLowerCase(Locale.ROOT);
        return minuscula.equals("transições") ? "transicoes" : minuscula;
    }
}
