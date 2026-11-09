package automato.simulacao;

import java.util.List;
import java.util.Set;

/**
 * Resultado completo de processar uma palavra.
 *
 * @param status          ACEITA, REJEITADA ou ENTRADA_INVALIDA (símbolo fora do alfabeto)
 * @param palavra         palavra processada ("" é a palavra vazia)
 * @param passos          passo a passo da execução
 * @param ativosFinais    estados ativos quando a execução terminou
 * @param finaisAtingidos interseção dos ativos finais com os estados finais do autômato
 * @param caminho         caminho de estados percorrido (aceitador, ou o único caminho no AFD)
 * @param mensagem        explicação adicional (motivo da rejeição / do erro), ou null
 * @param simbolosLidos   quantos símbolos foram consumidos
 */
public record Resultado(Status status,
                        String palavra,
                        List<Passo> passos,
                        Set<String> ativosFinais,
                        Set<String> finaisAtingidos,
                        List<Movimento> caminho,
                        String mensagem,
                        int simbolosLidos) {

    public enum Status { ACEITA, REJEITADA, ENTRADA_INVALIDA }

    public boolean aceita() {
        return status == Status.ACEITA;
    }

    static Resultado entradaInvalida(String palavra, String mensagem) {
        return new Resultado(Status.ENTRADA_INVALIDA, palavra, List.of(), Set.of(), Set.of(), List.of(), mensagem, 0);
    }
}
