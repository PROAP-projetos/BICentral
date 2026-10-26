package com.bicentral.bicentral_backend.service.ia.lgpd;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Pseudonimizador {

    private static final Set<String> PARTICULAS = Set.of("da", "de", "do", "das", "dos", "e");

    // Primeiros nomes que são palavras comuns ("mais clara a descrição"): sozinhos, não são trocados.
    private static final Set<String> PALAVRAS_COMUNS = Set.of(
            "clara", "luz", "gloria", "vitoria", "rosa", "graca", "flor", "celeste", "socorro", "dores",
            "pura", "aurora", "estrela", "paz", "mar", "sol", "cruz", "lima", "rocha", "franco", "justo",
            "bento", "leal", "nobre", "branca", "serena", "constante", "amado", "inocencio", "bela");

    private static final int MAX_PALAVRAS_POR_TERMO = 10;

    private static final Pattern PALAVRA = Pattern.compile("\\p{L}+(?:['’-]\\p{L}+)*");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");
    private static final Pattern CPF = Pattern.compile("(?<!\\d)\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}(?!\\d)");
    // Aceita o código com ou sem colchetes, porque o modelo às vezes os perde ao reescrever.
    private static final Pattern CODIGO = Pattern.compile("\\[?(PESSOA|NOME)_([0-9a-f]{6,16})\\]?");

    private final Map<String, String> codigoPorTermo = new HashMap<>();
    private final Map<String, String> textoPorCodigo = new HashMap<>();

    public Pseudonimizador(Collection<String> nomes) {
        Map<String, Set<String>> pessoasPorTermo = new LinkedHashMap<>();
        Map<String, String> nomeOriginalPorChave = new LinkedHashMap<>();
        Map<String, String> grafiaPorTermo = new HashMap<>();

        for (String nome : nomes) {
            if (nome == null || nome.isBlank()) {
                continue;
            }
            List<String> originais = palavras(nome.trim());
            if (originais.isEmpty()) {
                continue;
            }
            List<String> normalizadas = originais.stream().map(Pseudonimizador::normalizar).toList();
            String chave = String.join(" ", normalizadas);
            nomeOriginalPorChave.putIfAbsent(chave, nome.trim());

            for (Map.Entry<String, String> termo : termosDaPessoa(originais, normalizadas).entrySet()) {
                pessoasPorTermo.computeIfAbsent(termo.getKey(), k -> new HashSet<>()).add(chave);
                grafiaPorTermo.putIfAbsent(termo.getKey(), termo.getValue());
            }
        }

        int tamanho = tamanhoSemColisao(new ArrayList<>(pessoasPorTermo.keySet()));
        for (Map.Entry<String, Set<String>> entrada : pessoasPorTermo.entrySet()) {
            String termo = entrada.getKey();
            Set<String> pessoas = entrada.getValue();
            String codigo;
            String texto;
            // Hash do nome, não posição na lista: o código não muda ao recarregar o dicionário.
            if (pessoas.size() == 1) {
                String chave = pessoas.iterator().next();
                codigo = "[PESSOA_" + hash(chave, tamanho) + "]";
                texto = nomeOriginalPorChave.get(chave);
            } else {
                // Ambíguo: volta como foi escrito e a tool faz a busca aproximada.
                codigo = "[NOME_" + hash(termo, tamanho) + "]";
                texto = grafiaPorTermo.get(termo);
            }
            codigoPorTermo.put(termo, codigo);
            textoPorCodigo.put(codigo, texto);
        }
    }

    // E-mail e CPF são apagados sem volta: o agente não precisa deles.
    public String mascarar(String texto) {
        if (texto == null || texto.isEmpty() || codigoPorTermo.isEmpty()) {
            return apagarContatos(texto);
        }
        String semContatos = apagarContatos(texto);
        String normalizado = normalizar(semContatos);

        List<int[]> posicoes = new ArrayList<>();
        Matcher m = PALAVRA.matcher(normalizado);
        while (m.find()) {
            posicoes.add(new int[]{m.start(), m.end()});
        }

        StringBuilder saida = new StringBuilder(semContatos.length());
        int copiadoAte = 0;
        int i = 0;
        while (i < posicoes.size()) {
            int achouAte = -1;
            String codigo = null;
            StringBuilder termo = new StringBuilder();
            for (int j = i; j < Math.min(posicoes.size(), i + MAX_PALAVRAS_POR_TERMO); j++) {
                if (j > i) {
                    // Só junta palavras separadas por espaço; "Silva, Maria" são dois termos, não um.
                    String entre = normalizado.substring(posicoes.get(j - 1)[1], posicoes.get(j)[0]);
                    if (!entre.isBlank()) {
                        break;
                    }
                    termo.append(' ');
                }
                termo.append(normalizado, posicoes.get(j)[0], posicoes.get(j)[1]);
                String encontrado = codigoPorTermo.get(termo.toString());
                if (encontrado != null && (j > i || pareceNomeProprio(semContatos, posicoes.get(i)[0]))) {
                    achouAte = j;
                    codigo = encontrado;
                }
            }
            if (codigo != null) {
                saida.append(semContatos, copiadoAte, posicoes.get(i)[0]).append(codigo);
                copiadoAte = posicoes.get(achouAte)[1];
                i = achouAte + 1;
            } else {
                i++;
            }
        }
        saida.append(semContatos, copiadoAte, semContatos.length());
        return saida.toString();
    }

    public String desmascarar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto;
        }
        Matcher m = CODIGO.matcher(texto);
        StringBuilder saida = new StringBuilder(texto.length());
        while (m.find()) {
            String nome = textoPorCodigo.get("[" + m.group(1) + "_" + m.group(2) + "]");
            m.appendReplacement(saida, Matcher.quoteReplacement(nome != null ? nome : m.group()));
        }
        m.appendTail(saida);
        return saida.toString();
    }

    public int totalTermos() {
        return codigoPorTermo.size();
    }

    private static Map<String, String> termosDaPessoa(List<String> originais, List<String> normalizadas) {
        Map<String, String> termos = new LinkedHashMap<>();
        termos.put(String.join(" ", normalizadas), String.join(" ", originais));

        List<Integer> nomesProprios = new ArrayList<>();
        for (int k = 0; k < normalizadas.size(); k++) {
            if (!PARTICULAS.contains(normalizadas.get(k))) {
                nomesProprios.add(k);
            }
        }
        if (nomesProprios.isEmpty()) {
            return termos;
        }
        // Primeiro nome + cada outro nome, não só o último: cobre quem cita pelo nome do meio.
        int primeiro = nomesProprios.get(0);
        for (int k : nomesProprios.subList(1, nomesProprios.size())) {
            termos.putIfAbsent(normalizadas.get(primeiro) + " " + normalizadas.get(k),
                    originais.get(primeiro) + " " + originais.get(k));
        }
        String primeiroNome = normalizadas.get(primeiro);
        if (primeiroNome.length() >= 3 && !PALAVRAS_COMUNS.contains(primeiroNome)) {
            termos.putIfAbsent(primeiroNome, originais.get(primeiro));
        }
        return termos;
    }

    // Palavra sozinha só é nome com maiúscula no meio da frase; senão "Leia o título" do prompt vira código.
    private static boolean pareceNomeProprio(String texto, int inicio) {
        if (!Character.isUpperCase(texto.charAt(inicio))) {
            return false;
        }
        int k = inicio - 1;
        while (k >= 0 && (texto.charAt(k) == ' ' || texto.charAt(k) == '\t')) {
            k--;
        }
        return k >= 0 && ".!?:\n\r-*•\"'(".indexOf(texto.charAt(k)) < 0;
    }

    private static String apagarContatos(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto;
        }
        return CPF.matcher(EMAIL.matcher(texto).replaceAll("[EMAIL]")).replaceAll("[CPF]");
    }

    private static List<String> palavras(String texto) {
        List<String> lista = new ArrayList<>();
        Matcher m = PALAVRA.matcher(texto);
        while (m.find()) {
            lista.add(m.group());
        }
        return lista;
    }

    // Caractere por caractere, mantendo o tamanho: as posições achadas aqui valem para o texto original.
    static String normalizar(String texto) {
        StringBuilder sb = new StringBuilder(texto.length());
        for (int k = 0; k < texto.length(); k++) {
            char c = texto.charAt(k);
            char base = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD).charAt(0);
            sb.append(Character.toLowerCase(base));
        }
        return sb.toString();
    }

    // Menor tamanho de hash (em dígitos hexadecimais) em que nenhum termo colide com outro.
    private static int tamanhoSemColisao(List<String> termos) {
        for (int tamanho = 6; tamanho < 16; tamanho += 2) {
            Set<String> vistos = new HashSet<>();
            boolean colidiu = false;
            for (String termo : termos) {
                if (!vistos.add(hash(termo, tamanho))) {
                    colidiu = true;
                    break;
                }
            }
            if (!colidiu) {
                return tamanho;
            }
        }
        return 16;
    }

    private static String hash(String texto, int tamanho) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, tamanho);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}
