package com.raccoon.music;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/** Dados oficiais da faixa (artista, título, álbum, gênero, ano e capa do álbum) vindos do iTunes, com MusicBrainz + Cover Art Archive como reserva. Chamar fora da thread da tela. */
public final class CapaOficial {
    /** O que foi encontrado; campos vazios quando a fonte não informa. */
    public static final class Oficial {
        public String artista = "";
        public String titulo = "";
        public String album = "";
        public String genero = "";
        public String ano = "";
        /** Endereço da imagem do álbum. */
        public String capa = "";
    }

    private static final String AGENTE = "RaccoonMusic/1.0 (https://github.com/raccoon-music)";
    private static final int TEMPO_MS = 8000;
    private static final int MAX_BYTES = 6 * 1024 * 1024;

    private CapaOficial() {
    }

    /** Procura a faixa nas fontes oficiais; null quando nenhuma confirma. */
    public static Oficial buscar(String artista, String titulo, String contexto) {
        String a = artista == null ? "" : artista.trim();
        String t = titulo == null ? "" : titulo.trim();
        if (t.isEmpty()) return null;
        try {
            String extra = contexto == null ? "" : contexto;
            Oficial o = a.isEmpty() ? null : noItunes(a, t, "US", "");
            if (o == null && !a.isEmpty()) o = noItunes(a, t, "BR", "");
            // artista do canal não bate (ex.: canal "AlokVEVO"): vale o artista que aparece no próprio título ou canal
            if (o == null) o = noItunes("", t, "US", extra);
            if (o == null) o = noItunes("", t, "BR", extra);
            if (o != null) return o;
        } catch (Throwable e) {
            // tenta a reserva
        }
        try {
            return noMusicBrainz(a, t);
        } catch (Throwable e) {
            return null;
        }
    }

    /** Bytes de uma imagem; null em qualquer falha. */
    public static byte[] imagem(String endereco) {
        if (endereco == null || endereco.isEmpty()) return null;
        return baixar(endereco, "image/*");
    }

    static String normalizar(String texto) {
        String s = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    /** Título "parecido": igual, ou um contém o outro (ex.: "Faixa (feat. X)" e "Faixa"). */
    static boolean tituloCombina(String pedido, String achado) {
        String p = normalizar(pedido);
        String a = normalizar(achado);
        if (p.isEmpty() || a.isEmpty()) return false;
        return p.equals(a) || (Math.min(p.length(), a.length()) >= 4 && (p.contains(a) || a.contains(p)));
    }

    static boolean artistaCombina(String pedido, String achado) {
        String p = normalizar(pedido);
        String a = normalizar(achado);
        if (p.isEmpty()) return true;
        return !a.isEmpty() && (p.equals(a) || p.contains(a) || a.contains(p));
    }

    private static Oficial noItunes(String artista, String titulo, String pais, String extra) throws Exception {
        String termo = (artista.isEmpty() ? "" : artista + " ") + titulo;
        String url = "https://itunes.apple.com/search?media=music&entity=song&limit=8&country=" + pais + "&term=" + URLEncoder.encode(termo, "UTF-8");
        byte[] corpo = baixar(url, "application/json");
        if (corpo == null) return null;
        JSONArray lista = new JSONObject(new String(corpo, StandardCharsets.UTF_8)).optJSONArray("results");
        if (lista == null) return null;
        Oficial melhor = null;
        for (int i = 0; i < lista.length(); i++) {
            JSONObject r = lista.getJSONObject(i);
            String nome = r.optString("trackName", "");
            String quem = r.optString("artistName", "");
            if (!tituloCombina(titulo, nome)) continue;
            String nq = normalizar(quem);
            boolean artistaOk;
            if (!artista.isEmpty()) artistaOk = artistaCombina(artista, quem);
            else if (!extra.isEmpty()) artistaOk = !nq.isEmpty() && normalizar(extra).contains(nq);
            else artistaOk = normalizar(titulo).equals(normalizar(nome));
            if (!artistaOk) continue;
            String arte = r.optString("artworkUrl100", "");
            if (arte.isEmpty()) continue;
            Oficial o = new Oficial();
            o.artista = quem;
            o.titulo = nome;
            o.album = r.optString("collectionName", "");
            o.genero = r.optString("primaryGenreName", "");
            String data = r.optString("releaseDate", "");
            o.ano = data.length() >= 4 ? data.substring(0, 4) : "";
            o.capa = arte.replaceAll("/\\d+x\\d+(bb)?\\.(jpg|png)$", "/600x600bb.jpg");
            boolean exato = normalizar(titulo).equals(normalizar(nome));
            if (exato) return o;  // o primeiro resultado exato é o mais popular
            if (melhor == null) melhor = o;
        }
        return melhor;
    }

    private static Oficial noMusicBrainz(String artista, String titulo) throws Exception {
        String consulta = "recording:\"" + aspas(titulo) + "\"";
        if (!artista.isEmpty()) consulta += " AND artist:\"" + aspas(artista) + "\"";
        String url = "https://musicbrainz.org/ws/2/recording?fmt=json&limit=8&query=" + URLEncoder.encode(consulta, "UTF-8");
        byte[] corpo = baixar(url, "application/json");
        if (corpo == null) return null;
        JSONArray gravacoes = new JSONObject(new String(corpo, StandardCharsets.UTF_8)).optJSONArray("recordings");
        if (gravacoes == null) return null;
        List<Oficial> albuns = new ArrayList<>();
        List<Oficial> outros = new ArrayList<>();
        List<String> vistos = new ArrayList<>();
        for (int i = 0; i < gravacoes.length(); i++) {
            JSONObject g = gravacoes.getJSONObject(i);
            if (g.optInt("score", 0) < 85) continue;
            JSONArray lancamentos = g.optJSONArray("releases");
            if (lancamentos == null) continue;
            String quem = "";
            JSONArray creditos = g.optJSONArray("artist-credit");
            if (creditos != null && creditos.length() > 0) quem = creditos.getJSONObject(0).optString("name", "");
            for (int j = 0; j < lancamentos.length(); j++) {
                JSONObject l = lancamentos.getJSONObject(j);
                if (!"Official".equalsIgnoreCase(l.optString("status", "Official"))) continue;
                JSONObject rg = l.optJSONObject("release-group");
                if (rg == null) continue;
                String id = rg.optString("id", "");
                JSONArray secundarios = rg.optJSONArray("secondary-types");
                if (id.isEmpty() || vistos.contains(id) || (secundarios != null && secundarios.length() > 0)) continue;
                vistos.add(id);
                Oficial o = new Oficial();
                o.artista = quem;
                o.titulo = g.optString("title", "");
                o.album = l.optString("title", "");
                String data = l.optString("date", "");
                o.ano = data.length() >= 4 ? data.substring(0, 4) : "";
                o.capa = "https://coverartarchive.org/release-group/" + id + "/front-500";
                if ("Album".equalsIgnoreCase(rg.optString("primary-type", ""))) albuns.add(o);
                else outros.add(o);
            }
        }
        albuns.addAll(outros);
        for (int i = 0; i < albuns.size() && i < 4; i++) {
            byte[] teste = baixar(albuns.get(i).capa, "image/*");
            if (teste != null) return albuns.get(i);
        }
        return null;
    }

    private static String aspas(String t) {
        return t.replace("\\", " ").replace("\"", " ");
    }

    /** GET com redirecionamentos; null em qualquer falha (404 = álbum sem capa). */
    private static byte[] baixar(String endereco, String aceita) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(endereco).openConnection();
            c.setConnectTimeout(TEMPO_MS);
            c.setReadTimeout(TEMPO_MS);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", AGENTE);
            c.setRequestProperty("Accept", aceita);
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
