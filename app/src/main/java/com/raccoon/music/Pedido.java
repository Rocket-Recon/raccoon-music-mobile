package com.raccoon.music;

import java.util.ArrayList;
import java.util.List;

/** Transforma o texto digitado (link ou nome de musica) na entrada que o yt-dlp entende. */
public final class Pedido {
    private Pedido() {
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
