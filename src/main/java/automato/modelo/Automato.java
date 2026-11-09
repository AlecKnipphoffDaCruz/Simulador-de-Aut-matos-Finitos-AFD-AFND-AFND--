package automato.modelo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Autômato finito (determinístico ou não, com ou sem transições ε), imutável.
 *
 * <p>Definição formal: M = (Q, Σ, δ, q0, F), onde
 * Q = estados, Σ = alfabeto, δ = função de transição, q0 = estado inicial
 * e F = estados finais. Uma instância só existe se for válida: use
 * {@link Construtor} para montar uma (a validação acontece em
 * {@link Construtor#construir()}).
 */
public final class Automato {

    /** Símbolo usado para representar a transição vazia. */
    public static final String EPSILON = "ε";

    /** Grafias aceitas nos arquivos de entrada para a transição vazia. */
    private static final Set<String> GRAFIAS_EPSILON = Set.of("ε", "λ", "eps", "epsilon", "lambda");

    private final List<String> estados;
    private final Set<Character> alfabeto;
    private final String inicial;
    private final Set<String> finais;
    private final Map<String, Map<Character, Set<String>>> delta;
    private final Map<String, Set<String>> deltaEpsilon;
    private final Comparator<String> ordemDeclaracao;
    private final boolean temEpsilon;
    private final boolean deterministico;

    private Automato(List<String> estados, List<Character> alfabeto, String inicial,
                     List<String> finais, List<Construtor.TransicaoBruta> transicoes) {
        Map<String, Integer> ordem = new HashMap<>();
        for (int i = 0; i < estados.size(); i++) {
            ordem.put(estados.get(i), i);
        }
        Comparator<String> porDeclaracao = Comparator.comparingInt(ordem::get);

        this.estados = List.copyOf(estados);
        this.alfabeto = Collections.unmodifiableSet(new LinkedHashSet<>(alfabeto));
        this.inicial = inicial;
        this.ordemDeclaracao = porDeclaracao;

        TreeSet<String> conjuntoFinais = new TreeSet<>(porDeclaracao);
        conjuntoFinais.addAll(finais);
        this.finais = Collections.unmodifiableSet(conjuntoFinais);

        this.delta = new LinkedHashMap<>();
        this.deltaEpsilon = new LinkedHashMap<>();
        for (Construtor.TransicaoBruta t : transicoes) {
            if (ehEpsilon(t.simbolo())) {
                deltaEpsilon.computeIfAbsent(t.de(), k -> new TreeSet<>(porDeclaracao)).addAll(t.para());
            } else {
                delta.computeIfAbsent(t.de(), k -> new HashMap<>())
                        .computeIfAbsent(t.simbolo().charAt(0), k -> new TreeSet<>(porDeclaracao))
                        .addAll(t.para());
            }
        }

        this.temEpsilon = !deltaEpsilon.isEmpty();
        boolean det = !temEpsilon;
        for (Map<Character, Set<String>> porSimbolo : delta.values()) {
            for (Set<String> destinos : porSimbolo.values()) {
                if (destinos.size() > 1) {
                    det = false;
                }
            }
        }
        this.deterministico = det;
    }

    /** Diz se o texto é uma das grafias aceitas para a transição vazia (ε). */
    public static boolean ehEpsilon(String simbolo) {
        return simbolo != null && GRAFIAS_EPSILON.contains(simbolo);
    }

    /** Estados na ordem em que foram declarados. */
    public List<String> estados() {
        return estados;
    }

    public Set<Character> alfabeto() {
        return alfabeto;
    }

    public String inicial() {
        return inicial;
    }

    public Set<String> finais() {
        return finais;
    }

    public boolean ehFinal(String estado) {
        return finais.contains(estado);
    }

    /** δ(estado, símbolo): conjunto de destinos (vazio se não há transição). */
    public Set<String> destinos(String estado, char simbolo) {
        Map<Character, Set<String>> porSimbolo = delta.get(estado);
        if (porSimbolo == null) {
            return Set.of();
        }
        Set<String> destinos = porSimbolo.get(simbolo);
        return destinos == null ? Set.of() : Collections.unmodifiableSet(destinos);
    }

    /** δ(estado, ε): destinos alcançáveis sem consumir símbolo. */
    public Set<String> destinosEpsilon(String estado) {
        Set<String> destinos = deltaEpsilon.get(estado);
        return destinos == null ? Set.of() : Collections.unmodifiableSet(destinos);
    }

    public boolean temTransicaoEpsilon() {
        return temEpsilon;
    }

    /**
     * Um AFD não tem transições ε e tem no máximo um destino por par
     * (estado, símbolo). Caso contrário, é um AFND.
     */
    public boolean ehDeterministico() {
        return deterministico;
    }

    /** Comparador que ordena estados pela ordem de declaração (saída previsível). */
    public Comparator<String> ordemDeclaracao() {
        return ordemDeclaracao;
    }

    /**
     * Monta e valida um autômato. Todos os problemas encontrados são
     * reportados juntos em uma única {@link AutomatoInvalidoException}.
     */
    public static final class Construtor {

        record TransicaoBruta(String de, String simbolo, List<String> para, String origem) { }

        private final List<String> estados = new ArrayList<>();
        private final List<String> alfabeto = new ArrayList<>();
        private final List<String> finais = new ArrayList<>();
        private final List<TransicaoBruta> transicoes = new ArrayList<>();
        private String inicial;

        public Construtor estado(String nome) {
            estados.add(nome);
            return this;
        }

        public Construtor simbolo(String simbolo) {
            alfabeto.add(simbolo);
            return this;
        }

        public Construtor inicial(String nome) {
            this.inicial = nome;
            return this;
        }

        public Construtor estadoFinal(String nome) {
            finais.add(nome);
            return this;
        }

        /**
         * @param origem descrição de onde a transição veio (ex.: "linha 7"),
         *               usada apenas nas mensagens de erro
         */
        public Construtor transicao(String de, String simbolo, List<String> para, String origem) {
            transicoes.add(new TransicaoBruta(de, simbolo, List.copyOf(para), origem));
            return this;
        }

        public Automato construir() throws AutomatoInvalidoException {
            List<String> erros = new ArrayList<>();

            // --- Estados ---
            Set<String> conjuntoEstados = new LinkedHashSet<>();
            if (estados.isEmpty()) {
                erros.add("o conjunto de estados está vazio");
            }
            for (String e : estados) {
                if (e == null || e.isBlank()) {
                    erros.add("há um estado com nome vazio");
                } else if (!conjuntoEstados.add(e)) {
                    erros.add("estado declarado mais de uma vez: '" + e + "'");
                }
            }

            // --- Alfabeto (cada símbolo é exatamente 1 caractere) ---
            List<Character> simbolos = new ArrayList<>();
            Set<Character> conjuntoSimbolos = new LinkedHashSet<>();
            if (alfabeto.isEmpty()) {
                erros.add("o alfabeto está vazio");
            }
            for (String s : alfabeto) {
                if (s == null || s.length() != 1) {
                    erros.add("símbolo de alfabeto inválido: '" + s
                            + "' (cada símbolo deve ter exatamente 1 caractere)");
                } else if (Character.isWhitespace(s.charAt(0))) {
                    erros.add("espaço em branco não pode ser símbolo do alfabeto");
                } else if (ehEpsilon(s)) {
                    erros.add("'" + s + "' é reservado para a transição vazia e não pode estar no alfabeto");
                } else if (!conjuntoSimbolos.add(s.charAt(0))) {
                    erros.add("símbolo declarado mais de uma vez no alfabeto: '" + s + "'");
                } else {
                    simbolos.add(s.charAt(0));
                }
            }

            // --- Estado inicial e finais ---
            if (inicial == null || inicial.isBlank()) {
                erros.add("estado inicial não informado");
            } else if (!conjuntoEstados.contains(inicial)) {
                erros.add("o estado inicial '" + inicial + "' não está no conjunto de estados");
            }
            for (String f : finais) {
                if (f == null || !conjuntoEstados.contains(f)) {
                    erros.add("o estado final '" + f + "' não está no conjunto de estados");
                }
            }

            // --- Transições ---
            for (TransicaoBruta t : transicoes) {
                String onde = t.origem() + ": ";
                if (t.de() == null || !conjuntoEstados.contains(t.de())) {
                    erros.add(onde + "estado de origem '" + t.de() + "' não existe");
                }
                if (t.simbolo() == null || t.simbolo().isEmpty()) {
                    erros.add(onde + "símbolo da transição não informado");
                } else if (!ehEpsilon(t.simbolo())
                        && !(t.simbolo().length() == 1 && conjuntoSimbolos.contains(t.simbolo().charAt(0)))) {
                    erros.add(onde + "símbolo '" + t.simbolo() + "' não pertence ao alfabeto " + formatar(conjuntoSimbolos));
                }
                if (t.para().isEmpty()) {
                    erros.add(onde + "a transição não tem estado de destino");
                }
                for (String d : t.para()) {
                    if (d == null || !conjuntoEstados.contains(d)) {
                        erros.add(onde + "estado de destino '" + d + "' não existe");
                    }
                }
            }

            if (!erros.isEmpty()) {
                throw new AutomatoInvalidoException(erros);
            }
            return new Automato(new ArrayList<>(conjuntoEstados), simbolos, inicial, finais, transicoes);
        }

        private static String formatar(Set<Character> simbolos) {
            List<String> partes = new ArrayList<>();
            for (Character c : simbolos) {
                partes.add(String.valueOf(c));
            }
            return "{" + String.join(", ", partes) + "}";
        }
    }
}
