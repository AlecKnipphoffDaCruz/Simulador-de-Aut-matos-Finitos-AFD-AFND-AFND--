package automato.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Argumentos da linha de comando já interpretados.
 *
 * @param arquivo         arquivo do autômato (.json ou .txt)
 * @param palavras        palavras passadas diretamente na linha de comando
 * @param arquivoPalavras arquivo com uma palavra por linha (ou null)
 * @param interativo      força o modo interativo
 * @param silencioso      mostra só o veredito, sem o passo a passo
 * @param ajuda           mostrar o texto de ajuda
 */
record Opcoes(String arquivo, List<String> palavras, String arquivoPalavras,
              boolean interativo, boolean silencioso, boolean ajuda) {

    /** Erro de uso da linha de comando (opção desconhecida, argumento faltando...). */
    static final class UsoInvalidoException extends Exception {
        UsoInvalidoException(String mensagem) {
            super(mensagem);
        }
    }

    static Opcoes interpretar(String[] args) throws UsoInvalidoException {
        String arquivo = null;
        List<String> palavras = new ArrayList<>();
        String arquivoPalavras = null;
        boolean interativo = false;
        boolean silencioso = false;
        boolean ajuda = false;
        boolean soPosicionais = false;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (!soPosicionais && a.equals("--")) {
                soPosicionais = true; // depois de "--", tudo é arquivo/palavra (permite palavras começando com '-')
                continue;
            }
            if (!soPosicionais && a.length() > 1 && a.startsWith("-")) {
                switch (a) {
                    case "-h", "--ajuda" -> ajuda = true;
                    case "-i", "--interativo" -> interativo = true;
                    case "-q", "--silencioso" -> silencioso = true;
                    case "-f", "--arquivo-palavras" -> {
                        if (i + 1 >= args.length) {
                            throw new UsoInvalidoException("a opção " + a + " exige o caminho de um arquivo");
                        }
                        arquivoPalavras = args[++i];
                    }
                    default -> throw new UsoInvalidoException("opção desconhecida: " + a);
                }
            } else if (arquivo == null) {
                arquivo = a;
            } else {
                palavras.add(a);
            }
        }
        if (!ajuda && arquivo == null) {
            throw new UsoInvalidoException("informe o arquivo do autômato (.json ou .txt)");
        }
        return new Opcoes(arquivo, palavras, arquivoPalavras, interativo, silencioso, ajuda);
    }
}
