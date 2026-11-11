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

    /** Esvazia tudo (uso nos testes). */
    public static void zerar() {
        synchronized (itens) {
            itens.clear();
            proximoId = 1;
        }
    }
}
