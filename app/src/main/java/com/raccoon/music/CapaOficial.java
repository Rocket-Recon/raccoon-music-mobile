package com.raccoon.music;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** A capa oficial do álbum (MusicBrainz + Cover Art Archive), em vez da imagem do vídeo do YouTube. Chamar fora da thread da tela. */
public final class CapaOficial {
    private static final String AGENTE = "RaccoonMusic/1.0 (https://github.com/raccoon-music)";
    private static final int TEMPO_MS = 12000;
    private static final int MAX_BYTES = 6 * 1024 * 1024;

    private CapaOficial() {
    }

    /** Bytes da capa oficial, ou null quando não achou (quem chama usa a imagem do vídeo). */
    public static byte[] buscar(String artista, String titulo) {
        try {
            if (titulo == null || titulo.trim().isEmpty()) return null;
            for (String grupo : gruposDeAlbum(artista == null ? "" : artista.trim(), titulo.trim())) {
                byte[] imagem = baixar("https://coverartarchive.org/release-group/" + grupo + "/front-500");
                if (imagem != null) return imagem;
            }
        } catch (Throwable e) {
            // sem rede ou sem resposta: fica a imagem do vídeo
        }
        return null;
    }

    /** Álbuns oficiais em que a gravação aparece: álbuns primeiro, depois singles e o resto. */
    private static List<String> gruposDeAlbum(String artista, String titulo) throws Exception {
        String consulta = "recording:\"" + aspas(titulo) + "\"";
        if (!artista.isEmpty()) consulta += " AND artist:\"" + aspas(artista) + "\"";
        String url = "https://musicbrainz.org/ws/2/recording?fmt=json&limit=8&query=" + URLEncoder.encode(consulta, "UTF-8");
        byte[] corpo = baixar(url);
        List<String> albuns = new ArrayList<>();
        List<String> outros = new ArrayList<>();
        if (corpo == null) return albuns;
        JSONArray gravacoes = new JSONObject(new String(corpo, StandardCharsets.UTF_8)).optJSONArray("recordings");
        if (gravacoes == null) return albuns;
        for (int i = 0; i < gravacoes.length(); i++) {
            JSONObject g = gravacoes.getJSONObject(i);
            if (g.optInt("score", 0) < 85) continue;
            JSONArray lancamentos = g.optJSONArray("releases");
            if (lancamentos == null) continue;
            for (int j = 0; j < lancamentos.length(); j++) {
                JSONObject l = lancamentos.getJSONObject(j);
                if (!"Official".equalsIgnoreCase(l.optString("status", "Official"))) continue;
                JSONObject rg = l.optJSONObject("release-group");
                if (rg == null) continue;
                String id = rg.optString("id", "");
                if (id.isEmpty() || albuns.contains(id) || outros.contains(id)) continue;
                String tipo = rg.optString("primary-type", "");
                JSONArray secundarios = rg.optJSONArray("secondary-types");
                boolean coletanea = secundarios != null && secundarios.length() > 0;
                if ("Album".equalsIgnoreCase(tipo) && !coletanea) albuns.add(id);
                else if (!coletanea) outros.add(id);
            }
        }
        albuns.addAll(outros);
        return albuns.size() > 4 ? new ArrayList<>(albuns.subList(0, 4)) : albuns;
    }

    private static String aspas(String t) {
        return t.replace("\\", " ").replace("\"", " ");
    }

    /** GET com redirecionamentos; null em qualquer falha (404 = álbum sem capa). */
    private static byte[] baixar(String endereco) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(endereco).openConnection();
            c.setConnectTimeout(TEMPO_MS);
            c.setReadTimeout(TEMPO_MS);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", AGENTE);
            c.setRequestProperty("Accept", "application/json, image/*");
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] bloco = new byte[32768];
                int n;
                while ((n = in.read(bloco)) > 0) {
                    out.write(bloco, 0, n);
                    if (out.size() > MAX_BYTES) return null;
                }
                return out.size() == 0 ? null : out.toByteArray();
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
