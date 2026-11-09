package com.raccoon.music;

import java.util.regex.Pattern;

/** As mesmas regras de limpeza de titulos e artistas do Raccoon Music do PC (texto.py). */
public final class Limpeza {
    private static final Pattern LIXO_ENTRE_PARENTESES = Pattern.compile(
            "[\\(\\[]\\s*.*?\\b(official|video|audio|lyrics|lyric|hd|4k|remaster|clip|visualizer|live|hq|prod\\.|produced)\\b.*?[\\)\\]]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LIXO_SOLTO = Pattern.compile(
            "\\b(official\\s+(?:music\\s+)?video|official\\s+audio|lyric\\s+video|music\\s+video|visualizer)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PARTICIPACAO = Pattern.compile(
            "\\b(feat|ft|featuring|with|w/|versus|vs|x|meets)\\b\\.?", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEPARADOR_DE_TITULO = Pattern.compile("\\s*(/|\\\\|\\+)\\s*");

    private Limpeza() {
    }

    /** Tira termos como (Official Audio), [Video] e Lyrics. */
    public static String limparLixo(String texto) {
        if (texto == null || texto.isEmpty()) return "";
        String t = LIXO_ENTRE_PARENTESES.matcher(texto).replaceAll("");
        t = LIXO_SOLTO.matcher(t).replaceAll("");
        return t.replaceAll("\\s+", " ").trim();
    }

    /** Padroniza o titulo: "/", "\" e "+" viram um separador neutro; sem hifen sobrando nas pontas. */
    public static String formatarTitulo(String texto) {
        if (texto == null || texto.trim().isEmpty()) return "";
        String t = SEPARADOR_DE_TITULO.matcher(texto).replaceAll(" _ ");
        t = t.replaceAll("\\s+", " ").trim();
        while (t.startsWith("-") || t.endsWith("-")) t = t.replaceAll("^-+|-+$", "").trim();
        return t;
    }

    /** Padroniza artistas: ",", "/" e conectores (feat, ft, with, vs, x...) viram " & ". */
    public static String formatarArtista(String texto) {
        if (texto == null || texto.trim().isEmpty()) return "";
        String t = PARTICIPACAO.matcher(texto).replaceAll(" & ");
        t = t.replace(",", " & ").replace("/", " & ");
        t = t.replaceAll("\\s*&\\s*", " & ").replaceAll("\\s+", " ").trim();
        while (t.startsWith("&") || t.endsWith("&")) t = t.replaceAll("^&+|&+$", "").trim();
        return t;
    }

    /** Canais "Artista - Topic" do YouTube Music: fica so o artista. "NA" (campo vazio do yt-dlp) vira "". */
    public static String artistaDoCanal(String canal) {
        if (canal == null) return "";
        String t = canal.trim();
        if (t.equals("NA") || t.equals("None")) return "";
        if (t.endsWith(" - Topic")) t = t.substring(0, t.length() - 8).trim();
        return t;
    }

    /**
     * Nome do arquivo no padrao do PC: "Artista - Titulo". Se o titulo do video ja traz o artista ("A - B"), fica como esta,
     * so limpo. Sem artista nem faixa conhecidos, usa o titulo do video.
     */
    public static String nomeSugerido(String artista, String faixa, String tituloDoVideo) {
        String a = formatarArtista(artistaDoCanal(artista));
        String f = faixa == null || faixa.trim().equals("NA") ? "" : faixa;
        String titulo = formatarTitulo(limparLixo(f.isEmpty() ? tituloDoVideo : f));
        if (titulo.isEmpty()) titulo = formatarTitulo(limparLixo(tituloDoVideo));
        if (a.isEmpty() || titulo.contains(" - ") || titulo.toLowerCase().startsWith(a.toLowerCase() + " ")) return Pedido.nomeDoArquivo(titulo);
        return Pedido.nomeDoArquivo(a + " - " + titulo);
    }
}
