package automato.modelo;

import java.util.List;

/**
 * Indica que a definição de um autômato (ou o arquivo que a contém) é inválida.
 * Carrega a lista completa de problemas encontrados, para que o usuário
 * possa corrigir tudo de uma vez só.
 */
public class AutomatoInvalidoException extends Exception {

    private final List<String> erros;

    public AutomatoInvalidoException(String erro) {
        this(List.of(erro));
    }

    public AutomatoInvalidoException(List<String> erros) {
        super(String.join("; ", erros));
        this.erros = List.copyOf(erros);
    }

    /** Todos os problemas encontrados, em linguagem legível. */
    public List<String> getErros() {
        return erros;
    }
}
