package com.raccoon.music;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Busca a letra da musica (lrclib.net, gratis e sem cadastro) e guarda no celular para abrir depois sem internet. */
public final class Letras {
    private Letras() {}

    private static File arquivo(Context c, Faixa f) {
        File pasta = new File(c.getFilesDir(), "letras");
        pasta.mkdirs();
        String nome = f.chave.replaceAll("[^a-z0-9]+", "_");
        if (nome.length() > 120) nome = nome.substring(0, 120);
        return new File(pasta, nome + ".txt");
    }

    public static String guardada(Context c, Faixa f) {
        File a = arquivo(c, f);
        if (!a.exists()) return null;
        try (InputStream in = new java.io.FileInputStream(a)) {
            return new String(ler(in), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] ler(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
        return out.toByteArray();
    }

    private static String pegar(String endereco) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(endereco).openConnection();
            c.setConnectTimeout(10_000);
            c.setReadTimeout(15_000);
            c.setRequestProperty("User-Agent", "RaccoonMusic/1.0");
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream()) {
                return new String(ler(in), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static String limpar(String titulo) {
        String s = titulo.replaceAll("\\(.*?\\)|\\[.*?\\]", " ").replaceAll("\\s+", " ").trim();
        return s.isEmpty() ? titulo : s;
    }

    private static String q(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static String daResposta(JSONObject o) {
        String t = o.optString("plainLyrics", "").trim();
        return t.isEmpty() ? null : t;
    }

    /** Letra da internet; null se nao achou ou se nao deu para conectar. Guarda no celular quando acha. */
    public static String buscar(Context c, Faixa f) {
        try {
            String titulo = limpar(f.titulo);
            String achada = null;
            if (!f.artista.isEmpty()) {
                String r = pegar("https://lrclib.net/api/get?artist_name=" + q(f.artista) + "&track_name=" + q(titulo));
                if (r != null) achada = daResposta(new JSONObject(r));
            }
            if (achada == null) {
                String r = pegar("https://lrclib.net/api/search?q=" + q((f.artista + " " + titulo).trim()));
                if (r != null) {
                    JSONArray lista = new JSONArray(r);
                    for (int i = 0; i < lista.length() && achada == null; i++) achada = daResposta(lista.getJSONObject(i));
                }
            }
            if (achada != null) {
                try (FileOutputStream out = new FileOutputStream(arquivo(c, f))) {
                    out.write(achada.getBytes(StandardCharsets.UTF_8));
                }
            }
            return achada;
        } catch (Exception e) {
            return null;
        }
    }
}
