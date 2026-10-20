package com.raccoon.music;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** O que o Raccoon Music do PC manda para o celular: as curtidas e as playlists (arquivo raccoon-celular.tsv na pasta das musicas). */
public final class Sincronizacao {
    public static final String ARQUIVO = "raccoon-celular.tsv";

    public final Set<String> curtidas = new LinkedHashSet<>();
    public final Map<String, List<String>> playlists = new LinkedHashMap<>();

    /** Linhas separadas por TAB: "C chave" (curtida) e "P nome chave chave ..." (playlist). Linha estranha e ignorada. */
    public static Sincronizacao ler(String texto) {
        Sincronizacao s = new Sincronizacao();
        if (texto == null) return s;
        for (String linha : texto.split("\n")) {
            String[] p = linha.replace("\r", "").split("\t");
            if (p.length >= 2 && p[0].equals("C") && !p[1].isEmpty()) {
                s.curtidas.add(p[1]);
            } else if (p.length >= 3 && p[0].equals("P") && !p[1].isEmpty()) {
                List<String> chaves = new ArrayList<>();
                for (int i = 2; i < p.length; i++) if (!p[i].isEmpty()) chaves.add(p[i]);
                if (!chaves.isEmpty()) s.playlists.put(p[1], chaves);
            }
        }
        return s;
    }

    /** As musicas da playlist que existem na biblioteca, na ordem da playlist. */
    public static List<Faixa> faixasDaPlaylist(List<String> chaves, Map<String, Faixa> porChave) {
        List<Faixa> r = new ArrayList<>();
        for (String k : chaves) {
            Faixa f = porChave.get(k);
            if (f != null) r.add(f);
        }
        return r;
    }
}
