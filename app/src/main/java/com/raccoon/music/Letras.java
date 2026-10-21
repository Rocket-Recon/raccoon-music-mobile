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

    private static File arquivoSinc(Context c, Faixa f) {
        File a = arquivo(c, f);
        return new File(a.getParentFile(), a.getName().replace(".txt", ".lrc"));
    }

    private static File marcaSemSinc(Context c, Faixa f) {
        File a = arquivo(c, f);
        return new File(a.getParentFile(), a.getName().replace(".txt", ".sem"));
    }

    /** Ja procurou a versao com tempo (achando ou nao)? Assim nao busca de novo toda vez. */
    public static boolean jaTentouSincronizada(Context c, Faixa f) {
        return arquivoSinc(c, f).exists() || marcaSemSinc(c, f).exists();
    }

    public static String sincronizada(Context c, Faixa f) {
        return lerArquivo(arquivoSinc(c, f));
    }

    public static String guardada(Context c, Faixa f) {
        return lerArquivo(arquivo(c, f));
    }

    private static String lerArquivo(File a) {
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

    private static String daRespostaSinc(JSONObject o) {
        String t = o.optString("syncedLyrics", "").trim();
        return t.isEmpty() || Lrc.ler(t) == null ? null : t;
    }

    private static boolean temLetra(JSONObject o) {
        return daResposta(o) != null || daRespostaSinc(o) != null;
    }

    private static void gravar(File a, String texto) {
        try (FileOutputStream out = new FileOutputStream(a)) {
            out.write(texto.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // sem espaco: busca de novo na proxima vez
        }
    }

    /** Letra da internet; null se nao achou ou se nao deu para conectar. Guarda no celular (a de ler e a com tempo, quando existe). */
    public static String buscar(Context c, Faixa f) {
        try {
            String titulo = limpar(f.titulo);
            JSONObject achado = null;
            boolean conectou = false;
            if (!f.artista.isEmpty()) {
                String r = pegar("https://lrclib.net/api/get?artist_name=" + q(f.artista) + "&track_name=" + q(titulo));
                if (r != null) {
                    conectou = true;
                    JSONObject o = new JSONObject(r);
                    if (temLetra(o)) achado = o;
                }
            }
            if (achado == null) {
                String r = pegar("https://lrclib.net/api/search?q=" + q((f.artista + " " + titulo).trim()));
                if (r != null) {
                    conectou = true;
                    JSONArray lista = new JSONArray(r);
                    for (int i = 0; i < lista.length() && achado == null; i++) {
                        JSONObject o = lista.getJSONObject(i);
                        if (temLetra(o)) achado = o;
                    }
                }
            }
            if (achado == null) return null;
            String sinc = daRespostaSinc(achado);
            String simples = daResposta(achado);
            if (simples == null && sinc != null) simples = Lrc.semTempos(sinc);
            if (simples != null) gravar(arquivo(c, f), simples);
            if (sinc != null) gravar(arquivoSinc(c, f), sinc);
            else if (conectou) gravar(marcaSemSinc(c, f), "-");
            return simples;
        } catch (Exception e) {
            return null;
        }
    }
}
