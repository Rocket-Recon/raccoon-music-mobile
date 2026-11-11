package com.raccoon.music;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A fila de downloads do app: o que falta baixar, o que está baixando e o resultado de cada um. Sem Android, para poder ser testada. */
public final class FilaDeDownload {
    public static final int PENDENTE = 0, BAIXANDO = 1, OK = 2, ERRO = 3;

    /** Um pedido da fila. `nome` vazio deixa o app escolher o nome (Artista - Título oficial). */
    public static final class Item {
        public final int id;
        public final String entrada;
        public final String nome;
        public final String arvore;
        public final String formato;
        public final int taxa;
        public volatile int estado = PENDENTE;
        public volatile String mensagem = "";

        Item(int id, String entrada, String nome, String arvore, String formato, int taxa) {
            this.id = id;
            this.entrada = entrada;
            this.nome = nome == null ? "" : nome;
            this.arvore = arvore;
            this.formato = formato;
            this.taxa = taxa;
        }

        /** O que aparece na lista: o nome escolhido ou o texto/link pedido. */
        public String rotulo() {
            return nome.isEmpty() ? entrada : nome;
        }
    }

    private static final List<Item> itens = new ArrayList<>();
    private static final List<Runnable> ouvintes = new CopyOnWriteArrayList<>();
    private static int proximoId = 1;

    private FilaDeDownload() {
    }

    public static void ouvir(Runnable ouvinte) {
        if (!ouvintes.contains(ouvinte)) ouvintes.add(ouvinte);
    }

    public static void naoOuvir(Runnable ouvinte) {
        ouvintes.remove(ouvinte);
    }

    private static void avisar() {
        for (Runnable r : ouvintes) r.run();
    }

    /** Põe pedidos no fim da fila; `nomes` (opcional, mesma ordem) traz o nome escolhido para cada um. */
    public static void adicionar(List<String> entradas, List<String> nomes, String arvore, String formato, int taxa) {
        synchronized (itens) {
            for (int i = 0; i < entradas.size(); i++) {
                String nome = nomes != null && i < nomes.size() ? nomes.get(i) : "";
                itens.add(new Item(proximoId++, entradas.get(i), nome, arvore, formato, taxa));
            }
        }
        avisar();
    }

    /** O próximo pedido a baixar (já marcado como baixando); null quando não há mais. */
    public static Item proximo() {
        Item achado = null;
        synchronized (itens) {
            for (Item i : itens) {
                if (i.estado == PENDENTE) {
                    i.estado = BAIXANDO;
                    achado = i;
                    break;
                }
            }
        }
        if (achado != null) avisar();
        return achado;
    }

    public static void concluir(Item item, boolean ok, String mensagem) {
        item.mensagem = mensagem == null ? "" : mensagem;
        item.estado = ok ? OK : ERRO;
        avisar();
    }

    public static List<Item> copia() {
        synchronized (itens) {
            return new ArrayList<>(itens);
        }
    }

    /** {pendentes, baixando, prontos, com erro}. */
    public static int[] contagem() {
        int[] c = new int[4];
        synchronized (itens) {
            for (Item i : itens) c[i.estado]++;
        }
        return c;
    }

    /** Tem algo esperando ou baixando. */
    public static boolean ativa() {
        int[] c = contagem();
        return c[PENDENTE] + c[BAIXANDO] > 0;
    }

    public static void limparConcluidos() {
        synchronized (itens) {
            for (int i = itens.size() - 1; i >= 0; i--) {
                if (itens.get(i).estado == OK) itens.remove(i);
            }
        }
        avisar();
    }

    /** Os que deram erro voltam ao fim da fila; devolve quantos foram. */
    public static int tentarDeNovo() {
        int n = 0;
        synchronized (itens) {
            for (Item i : itens) {
                if (i.estado == ERRO) {
                    i.estado = PENDENTE;
                    i.mensagem = "";
                    n++;
                }
            }
        }
        if (n > 0) avisar();
        return n;
    }

    /** Tira da fila o que ainda não começou (o que está baixando termina). */
    public static void cancelarPendentes() {
        synchronized (itens) {
            for (int i = itens.size() - 1; i >= 0; i--) {
                if (itens.get(i).estado == PENDENTE) itens.remove(i);
            }
        }
        avisar();
    }

    private static String limpo(String t) {
        return t == null ? "" : t.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    /** O que precisa sobreviver ao fechar o app: o que falta baixar, o que estava baixando e os que deram erro. Uma linha por pedido. */
    public static String serializar() {
        StringBuilder sb = new StringBuilder();
        synchronized (itens) {
            for (Item i : itens) {
                if (i.estado == OK) continue;
                int estado = i.estado == ERRO ? ERRO : PENDENTE;
                sb.append(estado).append('\t').append(i.taxa).append('\t').append(limpo(i.formato)).append('\t')
                        .append(limpo(i.arvore)).append('\t').append(limpo(i.nome)).append('\t').append(limpo(i.entrada)).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * Recoloca na fila o que foi guardado por serializar(). Só age com a fila vazia (não duplica). Devolve quantos
     * ficaram esperando para baixar.
     */
    public static int restaurar(String texto) {
        int pendentes = 0;
        if (texto == null || texto.isEmpty()) return 0;
        synchronized (itens) {
            if (!itens.isEmpty()) return 0;
            for (String linha : texto.split("\n")) {
                String[] p = linha.split("\t", -1);
                if (p.length != 6 || p[5].isEmpty()) continue;
                int taxa;
                int estado;
                try {
                    estado = Integer.parseInt(p[0]);
                    taxa = Integer.parseInt(p[1]);
                } catch (NumberFormatException e) {
                    continue;
                }
                Item item = new Item(proximoId++, p[5], p[4], p[3], p[2], taxa);
                item.estado = estado == ERRO ? ERRO : PENDENTE;
                if (item.estado == ERRO) item.mensagem = "Não terminou antes de o app fechar.";
                else pendentes++;
                itens.add(item);
            }
        }
        avisar();
        return pendentes;
    }

    /** Esvazia tudo (uso nos testes). */
    public static void zerar() {
        synchronized (itens) {
            itens.clear();
            proximoId = 1;
        }
    }
}
