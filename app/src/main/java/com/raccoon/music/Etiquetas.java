package com.raccoon.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** O que esta escrito dentro de cada arquivo (album, genero, ano, duracao). Lido aos poucos em segundo plano e guardado. Sem Android, para poder ser testado. */
public final class Etiquetas {
    public static final class Info {
        public final String album, genero, ano;
        public final long duracaoMs;

        public Info(String album, String genero, String ano, long duracaoMs) {
            this.album = limpo(album);
            this.genero = limpo(genero);
            this.ano = limpo(ano);
            this.duracaoMs = Math.max(0, duracaoMs);
        }
    }

    private final Map<String, Info> por = new java.util.concurrent.ConcurrentHashMap<>();

    private static String limpo(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }

    public boolean tem(String uri) { return por.containsKey(uri); }

    public Info de(String uri) { return por.get(uri); }

    public void definir(String uri, Info info) { por.put(uri, info); }

    public int quantas() { return por.size(); }

    public long duracao(String uri) {
        Info i = por.get(uri);
        return i == null ? 0 : i.duracaoMs;
    }

    /** Genero que o arquivo traz so como numero, como "(13)" ou "13", nao serve para nada: vira vazio. */
    public static String generoLegivel(String g) {
        String s = limpo(g);
        return s.matches("[()\\d\\s]*") ? "" : s;
    }

    /** Agrupa as musicas pelo album (ou genero), em ordem alfabetica. Chave do mapa: nome normalizado; valor: musicas. */
    public Map<String, List<Faixa>> agrupar(List<Faixa> faixas, boolean porGenero) {
        Map<String, List<Faixa>> m = new HashMap<>();
        Map<String, String> nomes = new HashMap<>();
        for (Faixa f : faixas) {
            Info i = por.get(f.uri);
            if (i == null) continue;
            String nome = porGenero ? generoLegivel(i.genero) : i.album;
            if (nome.isEmpty()) continue;
            String k = Faixa.normalizar(nome);
            if (!m.containsKey(k)) {
                m.put(k, new ArrayList<Faixa>());
                nomes.put(k, nome);
            }
            m.get(k).add(f);
        }
        List<String> ordem = new ArrayList<>(m.keySet());
        Collections.sort(ordem);
        Map<String, List<Faixa>> r = new LinkedHashMap<>();
        for (String k : ordem) r.put(nomes.get(k), m.get(k));
        return r;
    }

    public String serializar() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Info> e : por.entrySet()) {
            Info i = e.getValue();
            sb.append(e.getKey().replace('\t', ' ').replace('\n', ' ')).append('\t').append(i.album).append('\t').append(i.genero).append('\t')
                    .append(i.ano).append('\t').append(i.duracaoMs).append('\n');
        }
        return sb.toString();
    }

    public static Etiquetas ler(String texto) {
        Etiquetas e = new Etiquetas();
        if (texto == null) return e;
        for (String linha : texto.split("\n")) {
            String[] p = linha.split("\t", -1);
            if (p.length != 5 || p[0].isEmpty()) continue;
            try {
                e.por.put(p[0], new Info(p[1], p[2], p[3], Long.parseLong(p[4].trim())));
            } catch (NumberFormatException ignorar) {
                // linha estragada
            }
        }
        return e;
    }
}
