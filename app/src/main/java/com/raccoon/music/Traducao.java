package com.raccoon.music;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Tradução da letra (serviço gratuito do Google Tradutor, sem chave). Chamar fora da thread da tela. */
public final class Traducao {
    private Traducao() {}

    /** (nome mostrado, código do idioma) */
    public static final String[][] IDIOMAS = {
            {"Português", "pt"}, {"Inglês", "en"}, {"Espanhol", "es"}, {"Francês", "fr"}, {"Alemão", "de"},
            {"Italiano", "it"}, {"Japonês", "ja"}, {"Coreano", "ko"}, {"Russo", "ru"}};
    static final int LIMITE_POR_PEDIDO = 1500;

    public static String nomeDoIdioma(String codigo) {
        for (String[] i : IDIOMAS) if (i[1].equals(codigo)) return i[0];
        return codigo == null ? "" : codigo;
    }

    /** Divide as linhas em blocos de até {@link #LIMITE_POR_PEDIDO} caracteres (cada pedido leva o texto no endereço). */
    static List<List<String>> blocos(String[] linhas) {
        List<List<String>> r = new ArrayList<>();
        List<String> atual = new ArrayList<>();
        int tamanho = 0;
        for (String l : linhas) {
            if (!atual.isEmpty() && tamanho + l.length() + 1 > LIMITE_POR_PEDIDO) {
                r.add(atual);
                atual = new ArrayList<>();
                tamanho = 0;
            }
            atual.add(l);
            tamanho += l.length() + 1;
        }
        if (!atual.isEmpty()) r.add(atual);
        return r;
    }

    private static String pedir(String texto, String destino) {
        try {
            String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=" + URLEncoder.encode(destino, "UTF-8")
                    + "&dt=t&q=" + URLEncoder.encode(texto, "UTF-8");
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(12000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 RaccoonMusic");
            if (c.getResponseCode() != 200) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) {
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) out.write(b, 0, n);
            }
            JSONArray dados = new JSONArray(new String(out.toByteArray(), StandardCharsets.UTF_8)).getJSONArray(0);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < dados.length(); i++) {
                JSONArray seg = dados.optJSONArray(i);
                if (seg != null) sb.append(seg.optString(0, ""));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** Mesma quantidade de linhas que `linhas`, traduzidas; null se falhar (sem internet ou serviço ocupado). */
    public static String[] traduzir(String[] linhas, String destino) {
        String[] saida = new String[linhas.length];
        int pos = 0;
        for (List<String> bloco : blocos(linhas)) {
            boolean algumTexto = false;
            for (String l : bloco) if (!l.trim().isEmpty()) algumTexto = true;
            String[] partes;
            if (!algumTexto) {
                partes = bloco.toArray(new String[0]);
            } else {
                String r = pedir(String.join("\n", bloco), destino);
                if (r == null) return null;
                partes = r.split("\n", -1);
                if (partes.length != bloco.size()) {  // o serviço juntou ou separou linhas: refaz uma a uma
                    partes = new String[bloco.size()];
                    for (int i = 0; i < partes.length; i++) {
                        String l = bloco.get(i);
                        if (l.trim().isEmpty()) {
                            partes[i] = l;
                            continue;
                        }
                        String t = pedir(l, destino);
                        if (t == null) return null;
                        partes[i] = t.replace("\n", " ");
                    }
                }
            }
            for (String p : partes) saida[pos++] = p.trim();
        }
        return saida;
    }
}
