package automato;

import automato.entrada.CarregadorAutomato;
import automato.modelo.Automato;
import automato.modelo.AutomatoInvalidoException;
import automato.simulacao.Movimento;
import automato.simulacao.Resultado;
import automato.simulacao.Simulador;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Bateria de testes sem dependências externas (não precisa de JUnit).
 * Execute com ./testar.sh na raiz do projeto.
 */
public final class TestesAutomato {

    private static final Path VALIDOS = Path.of("exemplos", "validos");
    private static final Path INVALIDOS = Path.of("exemplos", "invalidos");

    private static int total = 0;
    private static int falhas = 0;
    private static Path tmp;

    public static void main(String[] args) throws Exception {
        tmp = Files.createTempDirectory("testes-automato");

        secao("Simulação — AFD");
        testesAfd();
        secao("Simulação — AFND");
        testesAfnd();
        secao("Simulação — AFND com transições ε");
        testesEpsilon();
        secao("Caminho percorrido");
        testesCaminho();
        secao("Entrada inválida na palavra");
        testesPalavraInvalida();
        secao("Carregamento — arquivos válidos (JSON e TXT)");
        testesCarregamentoValido();
        secao("Carregamento — arquivos inválidos / malformados");
        testesCarregamentoInvalido();
        secao("CLI — códigos de saída e saída de texto");
        testesCli();

        System.out.println();
        System.out.println(total - falhas + "/" + total + " testes passaram"
                + (falhas == 0 ? " — OK" : " — " + falhas + " FALHA(S)"));
        System.exit(falhas == 0 ? 0 : 1);
    }

    // ---------------------------------------------------------------- simulação

    private static void testesAfd() throws Exception {
        Automato pares = carregar(VALIDOS.resolve("afd_pares_de_a.json"));
        verificar("pares_de_a é AFD", pares.ehDeterministico());
        aceita(pares, "", true);
        aceita(pares, "b", true);
        aceita(pares, "aa", true);
        aceita(pares, "abab", true);
        aceita(pares, "a", false);
        aceita(pares, "aaa", false);
        aceita(pares, "bab", false);

        Automato mult3 = carregar(VALIDOS.resolve("afd_binario_multiplo_de_3.json"));
        for (int n = 0; n <= 200; n++) {
            String binario = Integer.toBinaryString(n);
            aceitaSilencioso(mult3, binario, n % 3 == 0, "binário de " + n + " múltiplo de 3");
        }
        total++; // contabiliza o laço acima como um grupo de testes
        System.out.println("  ok   200 números binários conferidos contra n % 3");
    }

    private static void testesAfnd() throws Exception {
        Automato ab = carregar(VALIDOS.resolve("afnd_termina_em_ab.json"));
        verificar("termina_em_ab é AFND sem ε", !ab.ehDeterministico() && !ab.temTransicaoEpsilon());
        aceita(ab, "ab", true);
        aceita(ab, "aab", true);
        aceita(ab, "bbab", true);
        aceita(ab, "abab", true);
        aceita(ab, "", false);
        aceita(ab, "a", false);
        aceita(ab, "ba", false);
        aceita(ab, "abb", false);
    }

    private static void testesEpsilon() throws Exception {
        Automato eps = carregar(VALIDOS.resolve("afnd_epsilon_a_mais_ou_b_mais.json"));
        verificar("a+ ou b+ tem transições ε", eps.temTransicaoEpsilon());
        aceita(eps, "a", true);
        aceita(eps, "aaaa", true);
        aceita(eps, "b", true);
        aceita(eps, "bbb", true);
        aceita(eps, "", false);
        aceita(eps, "ab", false);
        aceita(eps, "ba", false);

        // ε na palavra vazia: estado inicial chega a um final só por ε
        Automato vazia = montar("""
                estados: q0 q1
                alfabeto: a
                inicial: q0
                finais: q1
                transicoes:
                q0 ε q1
                """);
        aceita(vazia, "", true);
        aceita(vazia, "a", false);

        // ciclo de ε não pode travar o simulador
        Automato ciclo = montar("""
                estados: q0 q1 q2
                alfabeto: a
                inicial: q0
                finais: q2
                transicoes:
                q0 ε q1
                q1 ε q0
                q1 a q2
                q2 ε q1
                """);
        aceita(ciclo, "a", true);
        aceita(ciclo, "", false);
        aceita(ciclo, "aa", true);
    }

    private static void testesCaminho() throws Exception {
        Automato eps = carregar(VALIDOS.resolve("afnd_epsilon_a_mais_ou_b_mais.json"));
        Resultado r = new Simulador().simular(eps, "aa");
        verificar("caminho aceitador de 'aa' = q0 -ε-> q1 -a-> q2 -a-> q2",
                texto(r.caminho()).equals("q0 -ε-> q1 -a-> q2 -a-> q2"));

        Automato pares = carregar(VALIDOS.resolve("afd_pares_de_a.json"));
        r = new Simulador().simular(pares, "ab");
        verificar("caminho percorrido (AFD rejeitado) de 'ab' = q0 -a-> q1 -b-> q1",
                texto(r.caminho()).equals("q0 -a-> q1 -b-> q1"));

        // AFD parcial que trava: caminho vai só até onde existiu transição
        Automato parcial = montar("""
                estados: q0 q1
                alfabeto: a b
                inicial: q0
                finais: q1
                transicoes:
                q0 a q1
                """);
        r = new Simulador().simular(parcial, "aab");
        verificar("AFD parcial trava depois do 2º símbolo e é REJEITADA",
                r.status() == Resultado.Status.REJEITADA && r.simbolosLidos() == 2 && r.mensagem() != null);
        verificar("caminho até travar = q0 -a-> q1", texto(r.caminho()).equals("q0 -a-> q1"));

        Automato ab = carregar(VALIDOS.resolve("afnd_termina_em_ab.json"));
        r = new Simulador().simular(ab, "aab");
        verificar("AFND aceitador de 'aab' termina em q2", r.aceita() && r.caminho().get(r.caminho().size() - 1).para().equals("q2"));
        verificar("caminho aceitador tem 3 transições (uma por símbolo)", r.caminho().size() == 3);
    }

    private static void testesPalavraInvalida() throws Exception {
        Automato pares = carregar(VALIDOS.resolve("afd_pares_de_a.json"));
        Resultado r = new Simulador().simular(pares, "abc");
        verificar("símbolo 'c' fora do alfabeto → ENTRADA_INVALIDA", r.status() == Resultado.Status.ENTRADA_INVALIDA);
        verificar("mensagem cita o símbolo e a posição", r.mensagem().contains("'c'") && r.mensagem().contains("posição 3"));
        r = new Simulador().simular(pares, "a b");
        verificar("espaço na palavra → ENTRADA_INVALIDA", r.status() == Resultado.Status.ENTRADA_INVALIDA);
    }

    // ------------------------------------------------------------- carregamento

    private static void testesCarregamentoValido() throws Exception {
        Automato json = carregar(VALIDOS.resolve("afnd_termina_em_ab.json"));
        Automato txt = carregar(VALIDOS.resolve("afnd_termina_em_ab.txt"));
        verificar("JSON e TXT equivalentes têm os mesmos estados", json.estados().equals(txt.estados()));
        boolean igual = true;
        for (String w : List.of("", "a", "ab", "aab", "bab", "abb", "bbbab")) {
            igual &= new Simulador().simular(json, w).status() == new Simulador().simular(txt, w).status();
        }
        verificar("JSON e TXT equivalentes dão o mesmo veredito", igual);

        Automato arrow = montar("""
                estados: q0 q1
                alfabeto: a
                inicial: q0
                finais: q1
                transicoes:
                q0 a -> q1
                """);
        aceita(arrow, "a", true);

        Automato bom = montar("""
                # comentário no topo

                Estados:   q0   q1
                ALFABETO: a
                inicial: q0
                finais:
                transições:
                q0 a q1
                """);
        aceita(bom, "a", false); // sem finais: nada é aceito, mas carrega
        verificar("diretivas case-insensitive, 'transições' com acento e 'finais:' vazio carregam", true);

        Path bom8 = tmp.resolve("bom_bom.json");
        Files.writeString(bom8, "\uFEFF{\"estados\":[\"s\"],\"alfabeto\":[\"x\"],\"inicial\":\"s\",\"finais\":[\"s\"],\"transicoes\":[]}");
        aceita(carregar(bom8), "", true);
        verificar("JSON com BOM e sem transições carrega", true);
    }

    private static void testesCarregamentoInvalido() throws Exception {
        esperaErro("json_malformado.json", "linha 4");
        esperaErro("sem_campo_inicial.json", "campo obrigatório ausente: \"inicial\"");
        esperaErro("inicial_inexistente.json", "estado inicial 'q9'");
        esperaErro("transicao_simbolo_fora_do_alfabeto.json", "símbolo 'b' não pertence ao alfabeto");
        esperaErro("transicao_estado_inexistente.json", "estado de destino 'q5' não existe");
        esperaErro("transicao_estado_inexistente.json", "estado final 'q7'"); // reporta vários erros juntos
        esperaErro("alfabeto_numeros_sem_aspas.json", "use \"0\" com aspas");
        esperaErro("alfabeto_multicaractere.json", "exatamente 1 caractere");
        esperaErro("arquivo_vazio.json", "vazio");
        esperaErro("txt_linha_invalida.txt", "linha 7");
        // texto solto na seção de transições vira uma transição; o erro aparece como "estado inexistente"
        esperaErroTextoTxt("estados: q0\nalfabeto: a\ninicial: q0\nfinais:\ntransicoes:\nisto nao e uma\n",
                "linha 6: estado de origem 'isto' não existe");

        esperaErroCaminho(tmp.resolve("nao_existe.json"), "não encontrado");
        esperaErroCaminho(tmp, "diretório");
        Path docx = Files.writeString(tmp.resolve("a.docx"), "x");
        esperaErroCaminho(docx, "extensão não suportada");
        Path latin1 = tmp.resolve("latin1.txt");
        Files.write(latin1, new byte[]{'e', 's', 't', 'a', 'd', 'o', 's', ':', ' ', (byte) 0xE7, (byte) 0xE3});
        esperaErroCaminho(latin1, "UTF-8");
        Path branco = Files.writeString(tmp.resolve("branco.txt"), "   \n\n  ");
        esperaErroCaminho(branco, "vazio");

        esperaErroTexto("{\"estados\": [\"q0\"], \"estados\": [\"q1\"]}", "campo repetido");
        esperaErroTexto("[1, 2]", "raiz do JSON deve ser um objeto");
        esperaErroTexto("{\"estados\": [\"q0\"]} lixo", "conteúdo inesperado");
        esperaErroTexto("{\"estados\": [\"q0\"", "não fechada");
        esperaErroTexto("{\"estados\": [\"q0\"],}", "nome de um campo");
        esperaErroTexto("{\"estados\": \"q0\"}", "deve ser uma lista");
        esperaErroTexto("{\"estados\": [\"q0\"], \"extra\": 1}", "campo desconhecido");
        esperaErroTexto("{\"estados\": [\"q0\", \"q0\"], \"alfabeto\": [\"a\"], \"inicial\": \"q0\", \"finais\": [], \"transicoes\": []}",
                "declarado mais de uma vez");
        esperaErroTexto("{\"estados\": [\"q0\"], \"alfabeto\": [\"ε\"], \"inicial\": \"q0\", \"finais\": [], \"transicoes\": []}",
                "reservado");
        esperaErroTexto("{\"estados\": [\"q0\"], \"alfabeto\": [], \"inicial\": \"q0\", \"finais\": [], \"transicoes\": []}",
                "alfabeto está vazio");
        esperaErroTexto("{\"estados\": [\"q0\"], \"alfabeto\": [\"a\"], \"inicial\": \"q0\", \"finais\": [], "
                + "\"transicoes\": [{\"de\": \"q0\", \"simbolo\": \"a\"}]}", "campo obrigatório ausente: \"para\"");
        esperaErroTexto("{\"estados\": [\"q0\"], \"alfabeto\": [\"a\"], \"inicial\": \"q0\", \"finais\": [], "
                + "\"transicoes\": [{\"de\": \"q0\", \"simbolo\": \"a\", \"para\": []}]}", "não tem estado de destino");
        esperaErroTexto("{\"estados\": [\"q0\"], \"alfabeto\": [\"a\"], \"inicial\": \"q0\", \"finais\": [], "
                + "\"transicoes\": [{\"de\": \"q0\", \"simbolo\": \"a\", \"para\": [\"q0\"]},]}", "caractere inesperado ']'");
        esperaErroTexto("{\"estados\": \"\\q\"}", "escape inválido");

        // texto .txt
        esperaErroTextoTxt("estados: q0\nalfabeto: a\ninicial: q0 q1\nfinais:\ntransicoes:\n", "exatamente um estado");
        esperaErroTextoTxt("estados: q0\nalfabeto: a\ninicial: q0\nfinais:\n", "diretiva obrigatória ausente: 'transicoes:'");
        esperaErroTextoTxt("estados: q0\nestados: q1\nalfabeto: a\ninicial: q0\nfinais:\ntransicoes:\n", "mais de uma vez");
        esperaErroTextoTxt("estados: q0\nalfabeto: a\ninicial: q0\nfinais:\nbanana: x\ntransicoes:\n", "diretiva desconhecida");
        esperaErroTextoTxt("lixo qualquer\n", "linha não reconhecida");
    }

    // --------------------------------------------------------------------- CLI

    private static void testesCli() throws Exception {
        String afd = VALIDOS.resolve("afd_pares_de_a.json").toString();
        String afnd = VALIDOS.resolve("afnd_termina_em_ab.json").toString();

        Saida s = cli(afnd, "aab");
        verificar("CLI: palavra aceita → código 0", s.codigo == 0);
        verificar("CLI: mostra 'Passo 1', 'Caminho aceitador' e 'ACEITA'",
                s.out.contains("Passo 1") && s.out.contains("Caminho aceitador") && s.out.contains("Resultado: ACEITA"));

        s = cli(afnd, "ba");
        verificar("CLI: palavra rejeitada → código 1", s.codigo == 1 && s.out.contains("Resultado: REJEITADA"));

        s = cli(afnd, "abx");
        verificar("CLI: símbolo inválido → código 2 e mensagem", s.codigo == 2 && s.out.contains("ENTRADA INVÁLIDA"));

        s = cli(afd, "-q", "abab", "a", "");
        verificar("CLI -q: uma linha por palavra e resumo",
                s.out.contains("\"abab\": ACEITA") && s.out.contains("\"a\": REJEITADA") && s.out.contains("ε: ACEITA")
                        && s.out.contains("Resumo: 2 aceita(s), 1 rejeitada(s)") && !s.out.contains("Passo"));
        verificar("CLI -q: rejeitada presente → código 1", s.codigo == 1);

        s = cli(afnd, "-q", "-f", VALIDOS.resolve("palavras_afnd.txt").toString());
        verificar("CLI -f: lê palavras do arquivo (inclui ε e inválida) → código 2",
                s.out.contains("\"aab\": ACEITA") && s.out.contains("ε: REJEITADA") && s.out.contains("\"abx\": ENTRADA INVÁLIDA")
                        && s.codigo == 2);

        s = cliComEntrada("ab\n\n:info\nε\nzzz\n:q\n", afnd);
        verificar("CLI interativo: processa palavras, ε, comandos e inválidas",
                s.out.contains("Resultado: ACEITA") && s.out.contains("Tipo     :") && s.out.contains("palavra vazia")
                        && s.out.contains("ENTRADA INVÁLIDA") && s.codigo == 0);

        s = cliComEntrada("", afnd); // EOF imediato no modo interativo
        verificar("CLI interativo: fim de entrada encerra sem erro", s.codigo == 0);

        s = cli(VALIDOS.resolve("inexistente.json").toString(), "a");
        verificar("CLI: arquivo inexistente → código 2 e mensagem no stderr", s.codigo == 2 && s.err.contains("não encontrado"));

        s = cli(INVALIDOS.resolve("transicao_estado_inexistente.json").toString(), "a");
        verificar("CLI: autômato inválido lista todos os erros", s.codigo == 2 && s.err.contains("q5") && s.err.contains("q7"));

        s = cli();
        verificar("CLI: sem argumentos → uso, código 2", s.codigo == 2 && s.err.contains("Uso:"));
        s = cli("--opcao-que-nao-existe", afnd);
        verificar("CLI: opção desconhecida → código 2", s.codigo == 2 && s.err.contains("opção desconhecida"));
        s = cli(afnd, "-f");
        verificar("CLI: -f sem arquivo → código 2", s.codigo == 2);
        s = cli("--ajuda");
        verificar("CLI: --ajuda → código 0", s.codigo == 0 && s.out.contains("Uso:"));

        s = cli(afnd, "--", "-a");
        verificar("CLI: '--' permite palavra começando com '-' (aqui, símbolo inválido)", s.codigo == 2 && s.out.contains("'-'"));
    }

    // ----------------------------------------------------------------- helpers

    private record Saida(int codigo, String out, String err) { }

    private static Saida cli(String... args) throws Exception {
        return cliComEntrada("", args);
    }

    private static Saida cliComEntrada(String entrada, String... args) throws Exception {
        Method executar = Class.forName("automato.cli.Main")
                .getDeclaredMethod("executar", String[].class, java.io.InputStream.class, PrintStream.class, PrintStream.class);
        executar.setAccessible(true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int codigo = (int) executar.invoke(null, args,
                new ByteArrayInputStream(entrada.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Saida(codigo, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static Automato carregar(Path caminho) throws AutomatoInvalidoException {
        return CarregadorAutomato.carregar(caminho);
    }

    private static Automato montar(String txt) throws IOException, AutomatoInvalidoException {
        Path arq = Files.createTempFile(tmp, "auto", ".txt");
        Files.writeString(arq, txt);
        return carregar(arq);
    }

    private static void aceita(Automato a, String palavra, boolean esperado) {
        Resultado r = new Simulador().simular(a, palavra);
        verificar("\"" + palavra + "\" " + (esperado ? "aceita" : "rejeitada"),
                r.aceita() == esperado && r.status() != Resultado.Status.ENTRADA_INVALIDA);
    }

    /** Igual a aceita(), mas só imprime se falhar (para laços grandes). */
    private static void aceitaSilencioso(Automato a, String palavra, boolean esperado, String descricao) {
        Resultado r = new Simulador().simular(a, palavra);
        if (r.aceita() != esperado) {
            total++;
            falhas++;
            System.out.println("  FALHA " + descricao);
        }
    }

    private static void esperaErro(String arquivo, String trecho) {
        Path p = arquivo.endsWith(".txt") || arquivo.endsWith(".json") ? INVALIDOS.resolve(arquivo) : Path.of(arquivo);
        esperaErroCaminho(p, trecho);
    }

    private static void esperaErroCaminho(Path caminho, String trecho) {
        try {
            carregar(caminho);
            verificar(caminho.getFileName() + " deveria falhar (" + trecho + ")", false);
        } catch (AutomatoInvalidoException e) {
            String todas = String.join(" | ", e.getErros());
            verificar(caminho.getFileName() + " → erro contém \"" + trecho + "\"", todas.contains(trecho));
            if (!todas.contains(trecho)) {
                System.out.println("       mensagens: " + todas);
            }
        }
    }

    private static void esperaErroTexto(String json, String trecho) throws IOException {
        esperaErroConteudo(".json", json, trecho);
    }

    private static void esperaErroTextoTxt(String txt, String trecho) throws IOException {
        esperaErroConteudo(".txt", txt, trecho);
    }

    private static void esperaErroConteudo(String extensao, String conteudo, String trecho) throws IOException {
        Path arq = Files.createTempFile(tmp, "inv", extensao);
        Files.writeString(arq, conteudo);
        try {
            carregar(arq);
            verificar("conteúdo deveria falhar (" + trecho + "): " + resumir(conteudo), false);
        } catch (AutomatoInvalidoException e) {
            String todas = String.join(" | ", e.getErros());
            verificar("erro contém \"" + trecho + "\" para " + resumir(conteudo), todas.contains(trecho));
            if (!todas.contains(trecho)) {
                System.out.println("       mensagens: " + todas);
            }
        }
    }

    private static String resumir(String s) {
        String uma = s.replace("\n", "\\n");
        return uma.length() > 40 ? uma.substring(0, 40) + "…" : uma;
    }

    private static String texto(List<Movimento> caminho) {
        if (caminho.isEmpty()) {
            return "";
        }
        return caminho.get(0).de() + caminho.stream()
                .map(m -> " -" + m.simbolo() + "-> " + m.para())
                .collect(Collectors.joining());
    }

    private static void verificar(String descricao, boolean condicao) {
        total++;
        if (condicao) {
            System.out.println("  ok   " + descricao);
        } else {
            falhas++;
            System.out.println("  FALHA " + descricao);
        }
    }

    private static void secao(String titulo) {
        System.out.println();
        System.out.println("== " + titulo);
    }
}
