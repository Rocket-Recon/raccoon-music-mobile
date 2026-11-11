package com.raccoon.music;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Renomear várias músicas de uma vez: calcula os novos nomes (sem extensão), sem Android, para poder ser testado. */
public final class Renomeacao {
    public static final int LIMPAR = 0, PADRONIZAR = 1, TROCAR = 2, INVERTER = 3;
    public static final String[] NOMES = {"Tirar lixo do nome", "Artista - Título", "Trocar um trecho", "Inverter Artista e Título"};

    private Renomeacao() {
    }

    /** Novo nome de uma música (sem extensão) pelo modo escolhido; `a` e `b` só importam em TROCAR (procurar / trocar por). */
    public static String aplicar(String base, int modo, String a, String b) {
        String nome = base == null ? "" : base;
        switch (modo) {
            case LIMPAR:
                return Pedido.nomeDoArquivo(Limpeza.limparLixo(nome));
            case PADRONIZAR: {
                int i = nome.indexOf(" - ");
                if (i < 0) return Pedido.nomeDoArquivo(Limpeza.formatarTitulo(Limpeza.limparLixo(nome)));
                String artista = Limpeza.formatarArtista(Limpeza.limparLixo(nome.substring(0, i)));
                String titulo = Limpeza.formatarTitulo(Limpeza.limparLixo(nome.substring(i + 3)));
                if (artista.isEmpty()) return Pedido.nomeDoArquivo(titulo);
                if (titulo.isEmpty()) return Pedido.nomeDoArquivo(artista);
                return Pedido.nomeDoArquivo(artista + " - " + titulo);
            }
            case TROCAR: {
                if (a == null || a.isEmpty()) return nome;
                return Pedido.nomeDoArquivo(nome.replace(a, b == null ? "" : b));
            }
            case INVERTER: {
                int i = nome.indexOf(" - ");
                if (i < 0) return nome;
                String esquerda = nome.substring(0, i).trim();
                String direita = nome.substring(i + 3).trim();
                if (esquerda.isEmpty() || direita.isEmpty()) return nome;
                return Pedido.nomeDoArquivo(direita + " - " + esquerda);
            }
            default:
                return nome;
        }
    }

    /**
     * Novos nomes para uma lista de nomes (mesma ordem). "" quer dizer "não mexer": nada mudou, ficou vazio
     * ou o nome já existe (na biblioteca ou em outra música do mesmo lote).
     */
    public static List<String> planejar(List<String> bases, List<String> biblioteca, int modo, String a, String b) {
        Set<String> usados = new HashSet<>();
        for (String x : biblioteca) usados.add(x.toLowerCase(Locale.ROOT));
        List<String> r = new ArrayList<>();
        for (String antigo : bases) {
            String novo = aplicar(antigo, modo, a, b);
            String chaveAntiga = antigo.toLowerCase(Locale.ROOT);
            String chaveNova = novo.toLowerCase(Locale.ROOT);
            if (novo.isEmpty() || novo.equals(antigo) || (usados.contains(chaveNova) && !chaveNova.equals(chaveAntiga))) {
                r.add("");
                continue;
            }
            usados.remove(chaveAntiga);
            usados.add(chaveNova);
            r.add(novo);
        }
        return r;
    }
}
