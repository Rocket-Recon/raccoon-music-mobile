package com.raccoon.music;

import java.util.ArrayList;
import java.util.List;

/** Transforma o texto digitado (link ou nome de musica) na entrada que o yt-dlp entende. */
public final class Pedido {
    public static final String[] FORMATOS = {"mp3", "m4a", "opus", "flac"};
    public static final int[] TAXAS = {128, 192, 256, 320};
    public static final int MAX_NOME = 150;

    private Pedido() {
    }

    /** Proximo formato da lista (volta ao primeiro depois do ultimo); desconhecido vira o primeiro. */
    public static String proximoFormato(String atual) {
        for (int i = 0; i < FORMATOS.length; i++) {
            if (FORMATOS[i].equals(atual)) return FORMATOS[(i + 1) % FORMATOS.length];
        }
        return FORMATOS[0];
    }

    public static int proximaTaxa(int atual) {
        for (int i = 0; i < TAXAS.length; i++) {
            if (TAXAS[i] == atual) return TAXAS[(i + 1) % TAXAS.length];
        }
        return TAXAS[TAXAS.length - 1];
    }

    public static String mime(String formato) {
        switch (formato) {
            case "m4a": return "audio/mp4";
            case "opus": return "audio/ogg";
            case "flac": return "audio/flac";
            default: return "audio/mpeg";
        }
    }

    /** Nome de arquivo valido: sem os caracteres proibidos, sem espacos repetidos nem ponto no fim; "" se nada sobrar. */
    public static String nomeDoArquivo(String texto) {
        if (texto == null) return "";
        String t = texto.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        if (t.length() > MAX_NOME) t = t.substring(0, MAX_NOME).trim();
        while (t.endsWith(".")) t = t.substring(0, t.length() - 1).trim();
        return t;
    }

    /** Modelo de saida do yt-dlp: com nome, o arquivo sai com ele; sem nome, com o titulo do video. */
    public static String modelo(String pasta, String nome) {
        String limpo = nomeDoArquivo(nome);
        if (limpo.isEmpty()) return pasta + "/%(title)s.%(ext)s";
        return pasta + "/" + limpo.replace("%", "%%") + ".%(ext)s";
    }

    /** Ultima linha nao vazia da saida (o titulo impresso pelo yt-dlp). */
    public static String ultimaLinha(String saida) {
        if (saida == null) return "";
        String[] linhas = saida.split("\\r?\\n");
        for (int i = linhas.length - 1; i >= 0; i--) {
            if (!linhas[i].trim().isEmpty()) return linhas[i].trim();
        }
        return "";
    }

    /** Causa da falha em uma linha curta: a ultima mensagem "ERROR:" do yt-dlp, ou a ultima linha do texto. */
    public static String resumoDoErro(String mensagem) {
        if (mensagem == null || mensagem.trim().isEmpty()) return "motivo desconhecido";
        String escolhida = "";
        for (String linha : mensagem.split("\\r?\\n")) {
            int i = linha.indexOf("ERROR:");
            if (i >= 0) escolhida = linha.substring(i + 6).trim();
        }
        if (escolhida.isEmpty()) escolhida = ultimaLinha(mensagem);
        return escolhida.length() > 220 ? escolhida.substring(0, 220) + "…" : escolhida;
    }

    /** Separa o texto colado em pedidos: um por linha; vários links na mesma linha (separados por espaço, vírgula ou ponto e vírgula) também viram pedidos. Sem repetidos. */
    public static List<String> separar(String texto) {
        List<String> r = new ArrayList<>();
        if (texto == null) return r;
        for (String linha : texto.split("\\r?\\n")) {
            String l = linha.trim();
            if (l.isEmpty()) continue;
            String[] partes = l.split("[\\s,;]+");
            boolean soLinks = partes.length > 1;
            for (String p : partes) if (!ehLink(p)) soLinks = false;
            if (soLinks) {
                for (String p : partes) adicionarSemRepetir(r, p);
            } else {
                adicionarSemRepetir(r, l);
            }
        }
        return r;
    }

    private static void adicionarSemRepetir(List<String> lista, String item) {
        for (String x : lista) if (x.equalsIgnoreCase(item)) return;
        lista.add(item);
    }

    /** Link de uma playlist inteira (e não de uma música que só está dentro de uma). */
    public static boolean ehPlaylist(String texto) {
        String t = texto == null ? "" : texto.trim().toLowerCase();
        return ehLink(t) && t.contains("/playlist?");
    }

    public static boolean ehLink(String texto) {
        String t = texto == null ? "" : texto.trim().toLowerCase();
        return t.startsWith("http://") || t.startsWith("https://");
    }

    /** Link: tira os parametros de playlist (uma musica so); texto: busca com um resultado; vazio: "". */
    public static String entrada(String texto) {
        String t = texto == null ? "" : texto.trim();
        if (t.isEmpty()) return "";
        if (t.toLowerCase().startsWith("ytsearch")) return t;
        if (!ehLink(t)) return "ytsearch1:" + t;
        int interrogacao = t.indexOf('?');
        if (interrogacao < 0 || t.toLowerCase().contains("/playlist?")) return t;
        int cerquilha = t.indexOf('#', interrogacao);
        String base = t.substring(0, interrogacao);
        String consulta = cerquilha < 0 ? t.substring(interrogacao + 1) : t.substring(interrogacao + 1, cerquilha);
        String fim = cerquilha < 0 ? "" : t.substring(cerquilha);
        List<String> mantidos = new ArrayList<>();
        for (String par : consulta.split("&")) {
            String chave = par.contains("=") ? par.substring(0, par.indexOf('=')) : par;
            if (par.isEmpty() || chave.equals("list") || chave.equals("index") || chave.equals("start_radio")) continue;
            mantidos.add(par);
        }
        return mantidos.isEmpty() ? base + fim : base + "?" + String.join("&", mantidos) + fim;
    }
}
