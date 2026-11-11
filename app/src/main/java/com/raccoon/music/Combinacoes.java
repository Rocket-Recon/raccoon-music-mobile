package com.raccoon.music;

import java.util.ArrayList;
import java.util.List;

/** Combinações de cores prontas (as mesmas do app do PC) e as que a pessoa salva. Sem Android, para poder ser testada. */
public final class Combinacoes {
    private Combinacoes() {
    }

    /** Nome, fundo e destaque. */
    public static final class Item {
        public final String nome;
        public final int fundo;
        public final int acento;

        public Item(String nome, int fundo, int acento) {
            this.nome = nome;
            this.fundo = fundo | 0xFF000000;
            this.acento = acento | 0xFF000000;
        }
    }

    /** Padrões de fábrica: o cinza do ecossistema Raccoon primeiro e depois o círculo de cores, como no PC. */
    public static final Item[] PRONTAS = {
            new Item("Cinza", 0x0B0B0D, 0x676E7F),
            new Item("Vermelho", 0x1A1111, 0xD63A3A),
            new Item("Laranja", 0x18181B, 0xE9532E),
            new Item("Amarelo", 0x17150E, 0xB58A00),
            new Item("Verde", 0x0F1713, 0x1F9D61),
            new Item("Ciano", 0x0E1517, 0x0B8FA8),
            new Item("Azul", 0x111418, 0x3B6FE0),
            new Item("Violeta", 0x14111B, 0x7C4DDB),
            new Item("Rosa", 0x1A1116, 0xD63B82),
            new Item("Claro", 0xF4F4F6, 0x676E7F),
    };

    /** Nome sem quebra de linha nem separador, com no máximo 24 letras. */
    public static String limparNome(String nome) {
        String n = nome == null ? "" : nome.replaceAll("[\\t\\r\\n|]", " ").replaceAll("\\s+", " ").trim();
        return n.length() > 24 ? n.substring(0, 24).trim() : n;
    }

    /** Uma por linha: nome, fundo e destaque em hexadecimal, separados por tabulação. */
    public static String serializar(List<Item> itens) {
        StringBuilder sb = new StringBuilder();
        for (Item i : itens) {
            sb.append(i.nome).append('\t').append(String.format("%06X", i.fundo & 0xFFFFFF)).append('\t')
                    .append(String.format("%06X", i.acento & 0xFFFFFF)).append('\n');
        }
        return sb.toString();
    }

    public static List<Item> ler(String texto) {
        List<Item> r = new ArrayList<>();
        if (texto == null) return r;
        for (String linha : texto.split("\n")) {
            String[] p = linha.split("\t");
            if (p.length != 3 || limparNome(p[0]).isEmpty()) continue;
            try {
                r.add(new Item(limparNome(p[0]), Integer.parseInt(p[1].trim(), 16), Integer.parseInt(p[2].trim(), 16)));
            } catch (NumberFormatException ignorada) {
                // linha estragada: fica de fora
            }
        }
        return r;
    }

    /** Guarda uma combinação; com o mesmo nome (sem ligar para maiúscula), substitui a de antes. */
    public static void salvar(List<Item> itens, String nome, int fundo, int acento) {
        String n = limparNome(nome);
        if (n.isEmpty()) return;
        for (int i = itens.size() - 1; i >= 0; i--) if (itens.get(i).nome.equalsIgnoreCase(n)) itens.remove(i);
        itens.add(new Item(n, fundo, acento));
    }

    public static void remover(List<Item> itens, String nome) {
        for (int i = itens.size() - 1; i >= 0; i--) if (itens.get(i).nome.equalsIgnoreCase(nome)) itens.remove(i);
    }
}
