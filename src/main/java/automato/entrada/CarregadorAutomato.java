package automato.entrada;

import automato.modelo.Automato;
import automato.modelo.AutomatoInvalidoException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Ponto de entrada para ler um autômato de um arquivo (.json ou .txt).
 * Trata os problemas de arquivo (inexistente, vazio, codificação errada,
 * extensão não suportada) antes de delegar ao formato correspondente.
 */
public final class CarregadorAutomato {

    private CarregadorAutomato() { }

    public static Automato carregar(Path caminho) throws AutomatoInvalidoException {
        if (!Files.exists(caminho)) {
            throw new AutomatoInvalidoException("arquivo não encontrado: " + caminho);
        }
        if (Files.isDirectory(caminho)) {
            throw new AutomatoInvalidoException("o caminho é um diretório, não um arquivo: " + caminho);
        }

        String nome = caminho.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean json = nome.endsWith(".json");
        if (!json && !nome.endsWith(".txt")) {
            throw new AutomatoInvalidoException("extensão não suportada em '" + caminho.getFileName()
                    + "' (use .json ou .txt)");
        }

        String texto = lerTexto(caminho);
        if (texto.isBlank()) {
            throw new AutomatoInvalidoException("o arquivo está vazio: " + caminho.getFileName());
        }
        return json ? FormatoJson.ler(texto) : FormatoTxt.ler(texto);
    }

    private static String lerTexto(Path caminho) throws AutomatoInvalidoException {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(caminho);
        } catch (IOException e) {
            throw new AutomatoInvalidoException("não foi possível ler o arquivo " + caminho + ": " + e.getMessage());
        }
        try {
            String texto = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return texto.startsWith("﻿") ? texto.substring(1) : texto; // ignora BOM
        } catch (CharacterCodingException e) {
            throw new AutomatoInvalidoException("o arquivo não está em UTF-8 válido: " + caminho.getFileName()
                    + " (salve-o com a codificação UTF-8)");
        }
    }
}
