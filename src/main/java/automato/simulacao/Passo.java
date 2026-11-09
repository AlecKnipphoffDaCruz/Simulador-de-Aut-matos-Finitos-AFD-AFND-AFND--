package automato.simulacao;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registro de um passo da simulação.
 *
 * @param indice            0 = configuração inicial; k = após ler o k-ésimo símbolo
 * @param lido              símbolo lido neste passo (null no passo 0)
 * @param movimentos        para cada estado ativo antes do passo, o conjunto δ(estado, lido)
 * @param transicoesEpsilon transições ε usadas no fecho-ε deste passo (ex.: "q1 --ε--> q2")
 * @param ativos            estados ativos ao fim do passo (já com fecho-ε)
 */
public record Passo(int indice,
                    Character lido,
                    Map<String, Set<String>> movimentos,
                    List<String> transicoesEpsilon,
                    Set<String> ativos) { }
