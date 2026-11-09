package automato.cli;

import automato.modelo.Automato;
import automato.simulacao.Movimento;
import automato.simulacao.Passo;
import automato.simulacao.Resultado;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Transforma autômatos e resultados em texto legível para o terminal. */
final class Formatador {

    private static final String LINHA = "=".repeat(60);

    private Formatador() { }

    /** "{q0, q1}" ou "∅" quando vazio. */
    static String conjunto(Collection<?> itens) {
        if (itens.isEmpty()) {
            return "∅";
        }
        List<String> partes = new ArrayList<>();
        itens.forEach(i -> partes.add(String.valueOf(i)));
        return "{" + String.join(", ", partes) + "}";
    }

    static String descreverAutomato(Automato a, String nomeArquivo) {
        StringBuilder sb = new StringBuilder();
        sb.append(LINHA).append('\n');
        sb.append(" Autômato : ").append(nomeArquivo).append('\n');
        sb.append(" Tipo     : ").append(tipo(a)).append('\n');
        sb.append(" Estados  : ").append(conjunto(a.estados())).append('\n');
        sb.append(" Alfabeto : ").append(conjunto(a.alfabeto())).append('\n');
        sb.append(" Inicial  : ").append(a.inicial()).append('\n');
        sb.append(" Finais   : ").append(conjunto(a.finais())).append('\n');
        sb.append(" Transições (δ):\n");
        for (String q : a.estados()) {
            for (Character c : a.alfabeto()) {
                Set<String> destinos = a.destinos(q, c);
                if (!destinos.isEmpty()) {
                    sb.append("   δ(").append(q).append(", ").append(c).append(") = ")
                            .append(conjunto(destinos)).append('\n');
                }
            }
            Set<String> eps = a.destinosEpsilon(q);
            if (!eps.isEmpty()) {
                sb.append("   δ(").append(q).append(", ").append(Automato.EPSILON).append(") = ")
                        .append(conjunto(eps)).append('\n');
            }
        }
        sb.append(LINHA);
        return sb.toString();
    }

    private static String tipo(Automato a) {
        if (a.ehDeterministico()) {
            return "AFD (determinístico)";
        }
        return a.temTransicaoEpsilon() ? "AFND com transições ε" : "AFND (não determinístico)";
    }

    /** Descreve uma execução. Com {@code passoAPasso}, mostra cada passo da fita. */
    static String descreverExecucao(Resultado r, boolean passoAPasso) {
        StringBuilder sb = new StringBuilder();
        sb.append("Palavra: ").append(rotuloPalavra(r.palavra())).append('\n');

        if (r.status() == Resultado.Status.ENTRADA_INVALIDA) {
            sb.append("  Erro: ").append(r.mensagem()).append('\n');
            sb.append("Resultado: ENTRADA INVÁLIDA");
            return sb.toString();
        }

        if (passoAPasso) {
            for (Passo p : r.passos()) {
                sb.append(passo(p, r.palavra()));
            }
        }
        if (r.mensagem() != null) {
            sb.append("  Obs.: ").append(r.mensagem()).append('\n');
        }
        sb.append("  Estados ativos ao final  : ").append(conjunto(r.ativosFinais())).append('\n');
        sb.append("  Estados finais atingidos : ").append(conjunto(r.finaisAtingidos())).append('\n');
        if (!r.caminho().isEmpty() || r.aceita()) {
            sb.append(String.format("  %-25s: ", r.aceita() ? "Caminho aceitador" : "Caminho percorrido"))
                    .append(caminho(r)).append('\n');
        }
        sb.append("Resultado: ").append(r.aceita() ? "ACEITA" : "REJEITADA");
        return sb.toString();
    }

    private static String passo(Passo p, String palavra) {
        StringBuilder sb = new StringBuilder();
        if (p.indice() == 0) {
            sb.append("  Passo 0 — estado inicial\n");
        } else {
            sb.append("  Passo ").append(p.indice()).append(" — lê '").append(p.lido()).append("'")
                    .append("   (fita: ").append(fita(palavra, p.indice())).append(")\n");
            p.movimentos().forEach((estado, destinos) ->
                    sb.append("     δ(").append(estado).append(", ").append(p.lido()).append(") = ")
                            .append(conjunto(destinos)).append('\n'));
        }
        if (!p.transicoesEpsilon().isEmpty()) {
            sb.append("     fecho-ε: ").append(String.join(", ", p.transicoesEpsilon())).append('\n');
        }
        sb.append("     ativos: ").append(conjunto(p.ativos())).append('\n');
        return sb.toString();
    }

    /** Mostra a fita com um marcador: lidos | restantes. */
    private static String fita(String palavra, int lidos) {
        return palavra.substring(0, lidos) + "|" + palavra.substring(lidos);
    }

    private static String caminho(Resultado r) {
        List<Movimento> mov = r.caminho();
        if (mov.isEmpty()) {
            return r.ativosFinais().isEmpty() ? "(nenhum)" : "(permaneceu no estado inicial)";
        }
        StringBuilder sb = new StringBuilder(mov.get(0).de());
        for (Movimento m : mov) {
            sb.append(" --").append(m.simbolo()).append("--> ").append(m.para());
        }
        return sb.toString();
    }

    static String rotuloPalavra(String palavra) {
        return palavra.isEmpty() ? "ε (palavra vazia)"
                : "\"" + palavra + "\" (" + palavra.length() + " símbolo" + (palavra.length() == 1 ? "" : "s") + ")";
    }

    /** Linha única, usada no modo silencioso e no resumo. */
    static String umaLinha(Resultado r) {
        String palavra = r.palavra().isEmpty() ? "ε" : "\"" + r.palavra() + "\"";
        return switch (r.status()) {
            case ACEITA -> palavra + ": ACEITA";
            case REJEITADA -> palavra + ": REJEITADA";
            case ENTRADA_INVALIDA -> palavra + ": ENTRADA INVÁLIDA — " + r.mensagem();
        };
    }
}
