package com.raccoon.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.ToLongFunction;

/** As contas da biblioteca (ordem, pesquisa, aleatorio e o arquivo guardado). Sem nada do Android, para poder ser testado. */
public final class Catalogo {
    private Catalogo() {}

    /** Por banda e depois por musica (o nome sem acento e sem maiuscula). */
    public static List<Faixa> ordenar(List<Faixa> faixas) {
        List<Faixa> r = new ArrayList<>(faixas);
        Collections.sort(r, (a, b) -> a.chave.compareTo(b.chave));
        return r;
    }

    /** modo 0 = por banda (como veio), 1 = por titulo, 2 = as mais longas primeiro. */
    public static List<Faixa> ordenarPor(List<Faixa> base, int modo, final ToLongFunction<Faixa> duracao) {
        List<Faixa> r = new ArrayList<>(base);
        if (modo == 1) {
            Collections.sort(r, (a, b) -> Faixa.normalizar(a.titulo).compareTo(Faixa.normalizar(b.titulo)));
        } else if (modo == 2) {
            Collections.sort(r, (a, b) -> Long.compare(duracao.applyAsLong(b), duracao.applyAsLong(a)));
        }
        return r;
    }

    /** Todas as palavras digitadas precisam aparecer (em qualquer ordem, sem ligar para acento nem maiuscula). */
    public static List<Faixa> filtrar(List<Faixa> todas, String texto, Set<String> curtidas, boolean soCurtidas) {
        String[] palavras = Faixa.normalizar(texto).split(" ");
        List<Faixa> r = new ArrayList<>();
        for (Faixa f : todas) {
            if (soCurtidas && !curtidas.contains(f.chave)) continue;
            boolean ok = true;
            for (String p : palavras) {
                if (!p.isEmpty() && !f.busca.contains(p)) {
                    ok = false;
                    break;
                }
            }
            if (ok) r.add(f);
        }
        return r;
    }

    /** As bandas da lista (nome e quantas musicas), em ordem alfabetica; musicas sem "Banda - " ficam de fora. */
    public static List<String[]> artistas(List<Faixa> faixas, String texto) {
        String busca = Faixa.normalizar(texto);
        Map<String, String[]> por = new java.util.TreeMap<>();
        for (Faixa f : faixas) {
            if (f.artista.isEmpty()) continue;
            String chave = Faixa.normalizar(f.artista);
            if (!busca.isEmpty() && !chave.contains(busca)) continue;
            String[] atual = por.get(chave);
            if (atual == null) {
                por.put(chave, new String[]{f.artista, "1"});
            } else {
                atual[1] = String.valueOf(Integer.parseInt(atual[1]) + 1);
            }
        }
        return new ArrayList<>(por.values());
    }

    /** A lista toda em ordem aleatoria. Com `inicio` valido, essa musica toca primeiro e o resto vem sorteado. */
    public static List<Faixa> embaralhar(List<Faixa> lista, int inicio, Random r) {
        List<Faixa> copia = new ArrayList<>(lista);
        Faixa primeira = (inicio >= 0 && inicio < copia.size()) ? copia.remove(inicio) : null;
        Collections.shuffle(copia, r);
        if (primeira != null) copia.add(0, primeira);
        return copia;
    }

    /** O que ainda vai tocar, na nova ordem: sorteado (aleatorio ligado) ou na ordem da biblioteca (desligado). */
    public static List<String> reordenar(List<String> restantes, Map<String, Integer> posicao, boolean embaralhar, Random r) {
        List<String> saida = new ArrayList<>(restantes);
        if (embaralhar) {
            Collections.shuffle(saida, r);
        } else {
            Collections.sort(saida, (a, b) -> Integer.compare(
                    posicao.containsKey(a) ? posicao.get(a) : Integer.MAX_VALUE,
                    posicao.containsKey(b) ? posicao.get(b) : Integer.MAX_VALUE));
        }
        return saida;
    }

    /** O arquivo onde a biblioteca fica guardada (uma musica por linha: endereco, TAB, nome do arquivo). */
    public static String serializar(List<Faixa> faixas) {
        StringBuilder sb = new StringBuilder();
        for (Faixa f : faixas) {
            sb.append(f.uri.replace('\t', ' ').replace('\n', ' ')).append('\t').append(f.nome.replace('\t', ' ').replace('\n', ' ')).append('\n');
        }
        return sb.toString();
    }

    public static List<Faixa> ler(String texto) {
        List<Faixa> r = new ArrayList<>();
        if (texto == null) return r;
        for (String linha : texto.split("\n")) {
            String[] p = linha.split("\t", 2);
            if (p.length == 2 && !p[0].isEmpty() && !p[1].isEmpty()) r.add(new Faixa(p[0], p[1]));
        }
        return r;
    }
}
