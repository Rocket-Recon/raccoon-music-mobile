package com.raccoon.music;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Os textos que o app grava ao exportar musicas (lista de nomes, playlist .m3u, nome da pasta). Sem nada do Android. */
public final class Exportar {
    private Exportar() {}

    /** Um nome por linha, sem extensao (igual ao app do PC). */
    public static String lista(List<Faixa> faixas) {
        StringBuilder sb = new StringBuilder();
        for (Faixa f : faixas) sb.append(Faixa.semExtensao(f.nome)).append('\n');
        return sb.toString();
    }

    /** Playlist .m3u: abre em qualquer player quando fica na mesma pasta dos arquivos. */
    public static String m3u(List<Faixa> faixas) {
        StringBuilder sb = new StringBuilder("#EXTM3U\n");
        for (Faixa f : faixas) sb.append("#EXTINF:-1,").append(Faixa.semExtensao(f.nome)).append('\n').append(f.nome).append('\n');
        return sb.toString();
    }

    public static String nomeDaPasta(int quantas, long agoraMs) {
        String quando = new SimpleDateFormat("yyyy-MM-dd HHmmss", Locale.US).format(new Date(agoraMs));
        return "Raccoon Music - (" + quantas + (quantas == 1 ? " música" : " músicas") + ") - " + quando;
    }

    /** Nome de arquivo sem as letras que os celulares e o Windows nao aceitam. */
    public static String nomeSeguro(String nome) {
        String s = nome == null ? "" : nome.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]+", " ").replaceAll("\\s+", " ").trim();
        return s.isEmpty() ? "Raccoon Music" : s;
    }
}
