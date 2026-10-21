package com.raccoon.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Letra com tempo (formato .lrc do lrclib): cada linha sabe em que momento da musica ela e cantada. Sem nada do Android. */
public final class Lrc {
    private static final Pattern TEMPO = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");

    public final long[] tempos;
    public final String[] linhas;

    private Lrc(long[] tempos, String[] linhas) {
        this.tempos = tempos;
        this.linhas = linhas;
    }

    public int quantas() { return linhas.length; }

    private static long milissegundos(Matcher m) {
        long ms = (Long.parseLong(m.group(1)) * 60 + Long.parseLong(m.group(2))) * 1000;
        String f = m.group(3);
        if (f != null) {
            long v = Long.parseLong(f);
            ms += f.length() == 1 ? v * 100 : f.length() == 2 ? v * 10 : v;
        }
        return ms;
    }

    /** Le o texto .lrc; devolve null quando nao tem nenhuma linha com tempo. */
    public static Lrc ler(String texto) {
        if (texto == null) return null;
        final List<long[]> pares = new ArrayList<>();
        final List<String> falas = new ArrayList<>();
        for (String linha : texto.split("\r?\n")) {
            Matcher m = TEMPO.matcher(linha);
            List<Long> marcas = new ArrayList<>();
            int pos = 0;
            while (true) {
                m.region(pos, linha.length());
                if (!m.lookingAt()) break;
                marcas.add(milissegundos(m));
                pos = m.end();
            }
            if (marcas.isEmpty()) continue;
            String fala = linha.substring(pos).trim();
            for (Long t : marcas) {
                pares.add(new long[]{t, falas.size()});
                falas.add(fala);
            }
        }
        if (pares.isEmpty()) return null;
        Collections.sort(pares, (a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
        long[] tempos = new long[pares.size()];
        String[] linhas = new String[pares.size()];
        for (int i = 0; i < pares.size(); i++) {
            tempos[i] = pares.get(i)[0];
            linhas[i] = falas.get((int) pares.get(i)[1]);
        }
        return new Lrc(tempos, linhas);
    }

    /** A linha que esta sendo cantada em "ms" (-1 antes da primeira). */
    public int indiceEm(long ms) {
        int ini = 0, fim = tempos.length - 1, r = -1;
        while (ini <= fim) {
            int meio = (ini + fim) >>> 1;
            if (tempos[meio] <= ms) {
                r = meio;
                ini = meio + 1;
            } else {
                fim = meio - 1;
            }
        }
        return r;
    }

    /** A letra sem os tempos, para quem so quer ler. */
    public static String semTempos(String texto) {
        return texto.replaceAll("(?m)^(\\[[^\\]]*\\])+[ \\t]*", "").trim();
    }
}
