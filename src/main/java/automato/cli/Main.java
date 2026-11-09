package automato.cli;

import automato.entrada.CarregadorAutomato;
import automato.modelo.Automato;
import automato.modelo.AutomatoInvalidoException;
import automato.simulacao.Resultado;
import automato.simulacao.Simulador;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Interface de linha de comando do simulador.
 *
 * <p>Códigos de saída: 0 = todas as palavras aceitas; 1 = alguma rejeitada;
 * 2 = erro (arquivo inválido, uso incorreto ou palavra com símbolo inválido).
 */
public final class Main {

    private static final String USO = """
            Simulador de Autômatos Finitos (AFD / AFND / AFND-ε)

            Uso:
              java -cp out automato.cli.Main <automato.json|txt> [opções] [palavra ...]

            Opções:
              -f, --arquivo-palavras <arq>  lê as palavras de um arquivo (uma por linha)
              -i, --interativo              entra no modo interativo (digite palavras)
              -q, --silencioso              só mostra ACEITA/REJEITADA (sem o passo a passo)
              -h, --ajuda                   mostra esta ajuda
              --                            fim das opções (para palavras que começam com '-')

            Palavra vazia: use "" ou ε.  Sem palavras na linha de comando, entra no modo interativo.
            Códigos de saída: 0 = todas aceitas, 1 = alguma rejeitada, 2 = erro.

            Exemplos:
              java -cp out automato.cli.Main exemplos/validos/afnd_termina_em_ab.json aab
              java -cp out automato.cli.Main exemplos/validos/afd_pares_de_a.json -q abab aa ""
              java -cp out automato.cli.Main exemplos/validos/afnd_termina_em_ab.json""";

    private static final String AJUDA_INTERATIVO = """
            Comandos: :info (mostra o autômato)  :ajuda  :q (sair)
            Digite uma palavra e Enter. Use ε para a palavra vazia.""";

    private Main() { }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(System.err, true, StandardCharsets.UTF_8);
        int codigo = executar(args, System.in, out, err);
        out.flush();
        err.flush();
        System.exit(codigo);
    }

    /** Núcleo da CLI, separado do {@code main} para poder ser testado. */
    static int executar(String[] args, InputStream entrada, PrintStream out, PrintStream err) {
        Opcoes opcoes;
        try {
            opcoes = Opcoes.interpretar(args);
        } catch (Opcoes.UsoInvalidoException e) {
            err.println("Erro: " + e.getMessage());
            err.println();
            err.println(USO);
            return 2;
        }
        if (opcoes.ajuda()) {
            out.println(USO);
            return 0;
        }

        Automato automato;
        try {
            automato = CarregadorAutomato.carregar(Path.of(opcoes.arquivo()));
        } catch (InvalidPathException e) {
            err.println("Erro: caminho inválido: " + opcoes.arquivo());
            return 2;
        } catch (AutomatoInvalidoException e) {
            err.println("Erro ao carregar o autômato '" + opcoes.arquivo() + "':");
            e.getErros().forEach(msg -> err.println("  - " + msg));
            return 2;
        }

        List<String> palavras = new ArrayList<>();
        opcoes.palavras().forEach(p -> palavras.add(normalizar(p)));
        if (opcoes.arquivoPalavras() != null) {
            try {
                palavras.addAll(lerPalavras(Path.of(opcoes.arquivoPalavras())));
            } catch (IOException | InvalidPathException e) {
                err.println("Erro ao ler o arquivo de palavras '" + opcoes.arquivoPalavras() + "': " + e.getMessage());
                return 2;
            }
        }

        if (!opcoes.silencioso()) {
            out.println(Formatador.descreverAutomato(automato, opcoes.arquivo()));
            out.println();
        }

        Simulador simulador = new Simulador();
        int aceitas = 0;
        int rejeitadas = 0;
        int invalidas = 0;
        for (String palavra : palavras) {
            Resultado r = simulador.simular(automato, palavra);
            switch (r.status()) {
                case ACEITA -> aceitas++;
                case REJEITADA -> rejeitadas++;
                case ENTRADA_INVALIDA -> invalidas++;
            }
            imprimir(out, r, opcoes.silencioso());
        }
        if (palavras.size() > 1) {
            out.println("Resumo: " + aceitas + " aceita(s), " + rejeitadas + " rejeitada(s), "
                    + invalidas + " inválida(s) — " + palavras.size() + " palavra(s).");
        }

        boolean semPalavras = palavras.isEmpty() && opcoes.arquivoPalavras() == null;
        if (opcoes.interativo() || semPalavras) {
            modoInterativo(automato, opcoes, simulador, entrada, out);
        } else if (palavras.isEmpty()) {
            out.println("Nenhuma palavra encontrada em '" + opcoes.arquivoPalavras() + "'.");
        }

        if (invalidas > 0) {
            return 2;
        }
        return rejeitadas > 0 ? 1 : 0;
    }

    private static void imprimir(PrintStream out, Resultado r, boolean silencioso) {
        out.println(silencioso ? Formatador.umaLinha(r) : Formatador.descreverExecucao(r, true));
        if (!silencioso) {
            out.println();
        }
    }

    private static void modoInterativo(Automato automato, Opcoes opcoes, Simulador simulador,
                                       InputStream entrada, PrintStream out) {
        out.println(AJUDA_INTERATIVO);
        BufferedReader leitor = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8));
        try {
            while (true) {
                out.print("palavra> ");
                out.flush();
                String linha = leitor.readLine();
                if (linha == null) {
                    out.println();
                    return; // fim da entrada (Ctrl+D)
                }
                linha = linha.strip();
                if (linha.isEmpty()) {
                    continue;
                }
                switch (linha) {
                    case ":q", ":sair" -> {
                        return;
                    }
                    case ":info" -> out.println(Formatador.descreverAutomato(automato, opcoes.arquivo()));
                    case ":ajuda" -> out.println(AJUDA_INTERATIVO);
                    default -> imprimir(out, simulador.simular(automato, normalizar(linha)), opcoes.silencioso());
                }
            }
        } catch (IOException e) {
            out.println("Erro ao ler a entrada: " + e.getMessage());
        }
    }

    /** "ε" digitado pelo usuário representa a palavra vazia. */
    private static String normalizar(String palavra) {
        return palavra.equals(Automato.EPSILON) ? "" : palavra;
    }

    /** Uma palavra por linha; linhas em branco são ignoradas; "ε" é a palavra vazia. */
    private static List<String> lerPalavras(Path arquivo) throws IOException {
        List<String> palavras = new ArrayList<>();
        for (String linha : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
            String limpa = linha.strip();
            if (!limpa.isEmpty()) {
                palavras.add(normalizar(limpa));
            }
        }
        return palavras;
    }
}
