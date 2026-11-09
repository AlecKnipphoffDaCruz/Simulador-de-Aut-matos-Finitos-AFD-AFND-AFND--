package automato.simulacao;

import automato.modelo.Automato;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Simula um {@link Automato} sobre uma palavra de entrada.
 *
 * <p>Funciona para AFD e AFND (com ou sem transições ε) com o mesmo algoritmo:
 * mantém o <b>conjunto de estados ativos</b>. A cada símbolo lido, move todos os
 * estados ativos por δ e depois aplica o <b>fecho-ε</b> (estados alcançáveis
 * sem consumir símbolo). A palavra é aceita se, ao fim da fita, algum estado
 * ativo é final. Num AFD o conjunto ativo tem sempre no máximo 1 estado, então
 * a execução coincide com o caminho único do autômato.
 *
 * <p>Para cada estado ativo guardamos de onde ele veio ({@code Origem}); assim
 * dá para reconstruir, no fim, o caminho de estados percorrido.
 */
public final class Simulador {

    /** Como um estado chegou a ser ativo: por qual estado/símbolo, e se na mesma "coluna" (ε). */
    private record Origem(String de, String simbolo, boolean mesmaColuna) { }

    private static final Origem INICIO = new Origem(null, null, true);

    public Resultado simular(Automato automato, String palavra) {
        // 1) Entrada inválida: símbolo fora do alfabeto → nem começa a simular.
        for (int i = 0; i < palavra.length(); i++) {
            char c = palavra.charAt(i);
            if (!automato.alfabeto().contains(c)) {
                return Resultado.entradaInvalida(palavra, "o símbolo '" + c + "' (posição " + (i + 1)
                        + " da palavra) não pertence ao alfabeto " + conjunto(automato.alfabeto()));
            }
        }

        Comparator<String> ordem = automato.ordemDeclaracao();
        List<Passo> passos = new ArrayList<>();
        List<Map<String, Origem>> colunas = new ArrayList<>(); // uma por passo, para reconstruir o caminho

        // 2) Passo 0: estado inicial + fecho-ε
        Map<String, Origem> atual = new TreeMap<>(ordem);
        atual.put(automato.inicial(), INICIO);
        List<String> eps = fechoEpsilon(automato, atual);
        passos.add(new Passo(0, null, Map.of(), eps, ativos(atual, ordem)));
        colunas.add(atual);

        // 3) Um passo por símbolo; para se o conjunto ativo esvaziar
        int lidos = 0;
        for (int i = 0; i < palavra.length() && !atual.isEmpty(); i++) {
            char c = palavra.charAt(i);
            Map<String, Origem> proxima = new TreeMap<>(ordem);
            Map<String, Set<String>> movimentos = new LinkedHashMap<>();
            for (String q : atual.keySet()) {
                Set<String> destinos = automato.destinos(q, c);
                movimentos.put(q, destinos);
                for (String d : destinos) {
                    proxima.putIfAbsent(d, new Origem(q, String.valueOf(c), false));
                }
            }
            List<String> epsilons = fechoEpsilon(automato, proxima);
            passos.add(new Passo(i + 1, c, movimentos, epsilons, ativos(proxima, ordem)));
            colunas.add(proxima);
            atual = proxima;
            lidos++;
        }

        // 4) Veredito
        boolean consumiuTudo = lidos == palavra.length();
        Set<String> finaisAtingidos = new TreeSet<>(ordem);
        if (consumiuTudo) {
            for (String q : atual.keySet()) {
                if (automato.ehFinal(q)) {
                    finaisAtingidos.add(q);
                }
            }
        }
        boolean aceita = consumiuTudo && !finaisAtingidos.isEmpty();

        String mensagem = null;
        if (atual.isEmpty()) {
            mensagem = "nenhum estado ativo após ler " + lidos + " símbolo(s)"
                    + (consumiuTudo ? "" : "; o restante da palavra não foi lido")
                    + " — não há transição possível";
        } else if (!aceita) {
            mensagem = "a palavra foi lida por inteiro, mas nenhum estado ativo é final";
        }

        // 5) Caminho: o aceitador; ou, num AFD, o único caminho percorrido
        List<Movimento> caminho = List.of();
        if (aceita) {
            caminho = reconstruir(colunas, colunas.size() - 1, finaisAtingidos.iterator().next());
        } else if (automato.ehDeterministico()) {
            int ultima = colunas.size() - 1;
            while (ultima > 0 && colunas.get(ultima).isEmpty()) {
                ultima--;
            }
            caminho = reconstruir(colunas, ultima, colunas.get(ultima).keySet().iterator().next());
        }

        return new Resultado(aceita ? Resultado.Status.ACEITA : Resultado.Status.REJEITADA,
                palavra, passos, ativos(atual, ordem), finaisAtingidos, caminho, mensagem, lidos);
    }

    /** Fecho-ε: acrescenta a {@code coluna} todo estado alcançável por transições ε. */
    private List<String> fechoEpsilon(Automato automato, Map<String, Origem> coluna) {
        List<String> usadas = new ArrayList<>();
        Deque<String> fila = new ArrayDeque<>(coluna.keySet());
        while (!fila.isEmpty()) {
            String q = fila.poll();
            for (String d : automato.destinosEpsilon(q)) {
                if (!coluna.containsKey(d)) {
                    coluna.put(d, new Origem(q, Automato.EPSILON, true));
                    fila.add(d);
                    usadas.add(q + " --" + Automato.EPSILON + "--> " + d);
                }
            }
        }
        return usadas;
    }

    /** Caminha para trás pelas origens até o estado inicial, montando o caminho em ordem. */
    private List<Movimento> reconstruir(List<Map<String, Origem>> colunas, int coluna, String estado) {
        Deque<Movimento> caminho = new ArrayDeque<>();
        Origem o = colunas.get(coluna).get(estado);
        while (o.de() != null) {
            caminho.addFirst(new Movimento(o.de(), o.simbolo(), estado));
            if (!o.mesmaColuna()) {
                coluna--;
            }
            estado = o.de();
            o = colunas.get(coluna).get(estado);
        }
        return new ArrayList<>(caminho);
    }

    private static Set<String> ativos(Map<String, Origem> coluna, Comparator<String> ordem) {
        Set<String> conjunto = new TreeSet<>(ordem);
        conjunto.addAll(coluna.keySet());
        return conjunto;
    }

    private static String conjunto(Set<Character> simbolos) {
        List<String> partes = new ArrayList<>();
        simbolos.forEach(s -> partes.add(String.valueOf(s)));
        return "{" + String.join(", ", partes) + "}";
    }
}
