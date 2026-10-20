package com.raccoon.music;

import java.text.Normalizer;
import java.util.Locale;

/** Uma musica da biblioteca. O nome do arquivo segue o padrao do Raccoon Music: "Banda - Musica.mp3". */
public final class Faixa {
    public final String uri;      // endereco do arquivo (content://...)
    public final String nome;     // nome do arquivo, com extensao
    public final String artista;  // "" quando o nome nao tem "Banda - "
    public final String titulo;
    public final String chave;    // nome sem extensao, sem acento e em minusculas (curtidas e ordem)
    public final String busca;    // artista + titulo normalizados, para a pesquisa

    public Faixa(String uri, String nome) {
        this.uri = uri;
        this.nome = nome;
        String base = semExtensao(nome).trim();
        String a = "";
        String t = base;
        int i = base.indexOf(" - ");
        if (i > 0 && i + 3 < base.length()) {
            a = base.substring(0, i).trim();
            t = base.substring(i + 3).trim();
        }
        if (t.isEmpty()) t = base;
        this.artista = a;
        this.titulo = t;
        this.chave = normalizar(base);
        this.busca = normalizar(a + " " + t);
    }

    public static String semExtensao(String nome) {
        int p = nome.lastIndexOf('.');
        return (p > 0 && nome.length() - p <= 6) ? nome.substring(0, p) : nome;
    }

    /** Minusculas, sem acento e com espacos simples: "Áudio  Slave" -> "audio slave". */
    public static String normalizar(String texto) {
        if (texto == null) return "";
        String s = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return s.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    /** O arquivo e uma musica? (pelo tipo que o Android informa ou, na falta dele, pela extensao). */
    public static boolean ehAudio(String nome, String mime) {
        String n = nome == null ? "" : nome.toLowerCase(Locale.ROOT);
        if (n.endsWith(".m3u") || n.endsWith(".m3u8") || n.endsWith(".pls") || n.endsWith(".cue")) return false;
        if (mime != null && mime.startsWith("audio/")) return true;
        return n.endsWith(".mp3") || n.endsWith(".m4a") || n.endsWith(".aac") || n.endsWith(".flac") || n.endsWith(".ogg")
                || n.endsWith(".oga") || n.endsWith(".opus") || n.endsWith(".wav") || n.endsWith(".mka");
    }
}
