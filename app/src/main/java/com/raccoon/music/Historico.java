package com.raccoon.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** O que a pessoa ouviu: quantas vezes cada musica tocou, quando foi a ultima vez e o tempo total. Sem Android, para poder ser testado. */
public final class Historico {
    private final Map<String, Integer> vezes = new HashMap<>();
    private final Map<String, Long> ultima = new HashMap<>();
    private long tempoMs = 0;
    private boolean mudou = false;

    public void registrar(String chave, long agora) {
        Integer v = vezes.get(chave);
        vezes.put(chave, v == null ? 1 : v + 1);
        ultima.put(chave, agora);
        mudou = true;
    }

    public void somarTempo(long ms) {
        if (ms > 0) {
            tempoMs += ms;
            mudou = true;
        }
    }

    public int vezes(String chave) {
        Integer v = vezes.get(chave);
        return v == null ? 0 : v;
    }

    public long tempoTotalMs() { return tempoMs; }

    public int totalDeVezes() {
        int s = 0;
        for (int v : vezes.values()) s += v;
        return s;
    }

    /** Quando foi guardado pela ultima vez? (para so gravar o arquivo quando ha novidade) */
    public boolean precisaGuardar() { return mudou; }

    public void guardado() { mudou = false; }

    /** A musica foi renomeada: o historico dela vai junto. */
    public void renomear(String antiga, String nova) {
        if (antiga.equals(nova)) return;
        Integer v = vezes.remove(antiga);
        Long u = ultima.remove(antiga);
        if (v != null) vezes.put(nova, v + vezes(nova));
        if (u != null) ultima.put(nova, Math.max(u, ultima.containsKey(nova) ? ultima.get(nova) : 0L));
        mudou = true;
    }

    private static Map<String, Faixa> porChave(List<Faixa> todas) {
        Map<String, Faixa> m = new HashMap<>();
        for (Faixa f : todas) if (!m.containsKey(f.chave)) m.put(f.chave, f);
        return m;
    }

    /** As mais tocadas (so as que existem na biblioteca), da mais para a menos. */
    public List<Faixa> maisTocadas(List<Faixa> todas, int limite) {
        Map<String, Faixa> por = porChave(todas);
        List<String> chaves = new ArrayList<>();
        for (String k : vezes.keySet()) if (por.containsKey(k)) chaves.add(k);
        Collections.sort(chaves, (a, b) -> {
            int c = Integer.compare(vezes(b), vezes(a));
            if (c != 0) return c;
            return Long.compare(ultima.containsKey(b) ? ultima.get(b) : 0L, ultima.containsKey(a) ? ultima.get(a) : 0L);
        });
        List<Faixa> r = new ArrayList<>();
        for (String k : chaves) {
            if (r.size() >= limite) break;
            r.add(por.get(k));
        }
        return r;
    }

    /** As tocadas por ultimo primeiro. */
    public List<Faixa> recentes(List<Faixa> todas, int limite) {
        Map<String, Faixa> por = porChave(todas);
        List<String> chaves = new ArrayList<>();
        for (String k : ultima.keySet()) if (por.containsKey(k)) chaves.add(k);
        Collections.sort(chaves, (a, b) -> Long.compare(ultima.get(b), ultima.get(a)));
        List<Faixa> r = new ArrayList<>();
        for (String k : chaves) {
            if (r.size() >= limite) break;
            r.add(por.get(k));
        }
        return r;
    }

    /** As bandas mais ouvidas: {nome, vezes}, da mais para a menos. */
    public List<String[]> bandasMaisOuvidas(List<Faixa> todas, int limite) {
        Map<String, Faixa> por = porChave(todas);
        Map<String, Integer> soma = new HashMap<>();
        Map<String, String> nome = new HashMap<>();
        for (Map.Entry<String, Integer> e : vezes.entrySet()) {
            Faixa f = por.get(e.getKey());
            if (f == null || f.artista.isEmpty()) continue;
            String k = Faixa.normalizar(f.artista);
            nome.put(k, f.artista);
            soma.put(k, (soma.containsKey(k) ? soma.get(k) : 0) + e.getValue());
        }
        List<String> ks = new ArrayList<>(soma.keySet());
        Collections.sort(ks, (a, b) -> {
            int c = Integer.compare(soma.get(b), soma.get(a));
            return c != 0 ? c : a.compareTo(b);
        });
        List<String[]> r = new ArrayList<>();
        for (String k : ks) {
            if (r.size() >= limite) break;
            r.add(new String[]{nome.get(k), String.valueOf(soma.get(k))});
        }
        return r;
    }

    /** "2 h 05 min", "37 min" ou "0 min". */
    public static String duracao(long ms) {
        long min = Math.max(0, ms / 60_000);
        return min >= 60 ? (min / 60) + " h " + String.format(java.util.Locale.US, "%02d", min % 60) + " min" : min + " min";
    }

    public String serializar() {
        StringBuilder sb = new StringBuilder("T\t").append(tempoMs).append('\n');
        for (Map.Entry<String, Integer> e : vezes.entrySet()) {
            sb.append("P\t").append(e.getKey().replace('\t', ' ').replace('\n', ' ')).append('\t').append(e.getValue()).append('\t')
                    .append(ultima.containsKey(e.getKey()) ? ultima.get(e.getKey()) : 0L).append('\n');
        }
        return sb.toString();
    }

    public static Historico ler(String texto) {
        Historico h = new Historico();
        if (texto == null) return h;
        for (String linha : texto.split("\n")) {
            String[] p = linha.split("\t");
            try {
                if (p.length == 2 && p[0].equals("T")) h.tempoMs = Long.parseLong(p[1].trim());
                else if (p.length == 4 && p[0].equals("P") && !p[1].isEmpty()) {
                    h.vezes.put(p[1], Integer.parseInt(p[2].trim()));
                    h.ultima.put(p[1], Long.parseLong(p[3].trim()));
                }
            } catch (NumberFormatException e) {
                // linha estragada: ignora
            }
        }
        return h;
    }
}
