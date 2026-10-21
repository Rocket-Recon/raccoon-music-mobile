package com.raccoon.music;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** As playlists feitas no proprio celular (nome e as musicas, pela chave). Sem Android, para poder ser testado. */
public final class Playlists {
    private final Map<String, List<String>> mapa = new LinkedHashMap<>();

    public List<String> nomes() { return new ArrayList<>(mapa.keySet()); }

    public boolean existe(String nome) { return mapa.containsKey(limpar(nome)); }

    public List<String> chaves(String nome) {
        List<String> l = mapa.get(nome);
        return l == null ? new ArrayList<String>() : new ArrayList<>(l);
    }

    public static String limpar(String nome) {
        return nome == null ? "" : nome.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim().replaceAll("\\s+", " ");
    }

    /** false se o nome esta vazio ou ja existe. */
    public boolean criar(String nome) {
        String n = limpar(nome);
        if (n.isEmpty() || mapa.containsKey(n)) return false;
        mapa.put(n, new ArrayList<String>());
        return true;
    }

    public boolean renomear(String antigo, String novo) {
        String n = limpar(novo);
        if (n.isEmpty() || !mapa.containsKey(antigo) || (!n.equals(antigo) && mapa.containsKey(n))) return false;
        if (n.equals(antigo)) return true;
        Map<String, List<String>> copia = new LinkedHashMap<>(mapa);
        mapa.clear();
        for (Map.Entry<String, List<String>> e : copia.entrySet()) mapa.put(e.getKey().equals(antigo) ? n : e.getKey(), e.getValue());
        return true;
    }

    public void excluir(String nome) { mapa.remove(nome); }

    /** false se a musica ja estava na playlist (ou a playlist nao existe). */
    public boolean adicionar(String nome, String chave) {
        List<String> l = mapa.get(nome);
        if (l == null || l.contains(chave)) return false;
        l.add(chave);
        return true;
    }

    /** Sobe (delta -1) ou desce (delta +1) a musica na playlist. false se nao deu para mexer. */
    public boolean mover(String nome, String chave, int delta) {
        List<String> l = mapa.get(nome);
        if (l == null) return false;
        int i = l.indexOf(chave);
        int j = i + delta;
        if (i < 0 || j < 0 || j >= l.size() || delta == 0) return false;
        l.remove(i);
        l.add(j, chave);
        return true;
    }

    public void remover(String nome, String chave) {
        List<String> l = mapa.get(nome);
        if (l != null) l.remove(chave);
    }

    /** A musica foi renomeada: ela continua nas playlists. */
    public void renomearChave(String antiga, String nova) {
        for (List<String> l : mapa.values()) {
            int i = l.indexOf(antiga);
            if (i >= 0) {
                if (l.contains(nova)) l.remove(i); else l.set(i, nova);
            }
        }
    }

    public String serializar() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> e : mapa.entrySet()) {
            sb.append(e.getKey());
            for (String k : e.getValue()) sb.append('\t').append(k);
            sb.append('\n');
        }
        return sb.toString();
    }

    public static Playlists ler(String texto) {
        Playlists p = new Playlists();
        if (texto == null) return p;
        for (String linha : texto.split("\n")) {
            String[] partes = linha.split("\t");
            if (partes.length == 0 || limpar(partes[0]).isEmpty()) continue;
            String nome = limpar(partes[0]);
            p.criar(nome);
            for (int i = 1; i < partes.length; i++) if (!partes[i].isEmpty()) p.adicionar(nome, partes[i]);
        }
        return p;
    }
}
