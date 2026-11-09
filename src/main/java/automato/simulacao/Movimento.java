package automato.simulacao;

/** Uma transição efetivamente percorrida: de --símbolo--> para (símbolo pode ser "ε"). */
public record Movimento(String de, String simbolo, String para) { }
