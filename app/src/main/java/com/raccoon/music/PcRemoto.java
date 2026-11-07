package com.raccoon.music;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

/** Conversa com o Raccoon Music do PC pela rede local: manda um link ou nome de musica para ele baixar. */
public final class PcRemoto {
    public static final int PORTA_PADRAO = 8765;
    public static final int MAX_PEDIDO = 300;
    public static final int TAMANHO_CODIGO = 8;

    private PcRemoto() {
    }

    /** "http://192.168.0.5:8765/?c=ABCD" -> "192.168.0.5:8765" (sem porta, usa a padrao); "" se nao for um endereco. */
    public static String endereco(String texto) {
        if (texto == null) return "";
        String t = texto.trim();
        if (t.toLowerCase().startsWith("http://")) t = t.substring(7);
        int corte = t.length();
        for (char c : new char[]{'/', '?', '#'}) {
            int i = t.indexOf(c);
            if (i >= 0 && i < corte) corte = i;
        }
        t = t.substring(0, corte);
        String host = t;
        int porta = PORTA_PADRAO;
        int pontos = t.lastIndexOf(':');
        if (pontos >= 0) {
            host = t.substring(0, pontos);
            String p = t.substring(pontos + 1);
            if (p.isEmpty() || p.length() > 5 || !p.matches("[0-9]+")) return "";
            porta = Integer.parseInt(p);
            if (porta < 1 || porta > 65535) return "";
        }
        if (host.isEmpty() || host.length() > 100 || !host.matches("[A-Za-z0-9.-]+") || host.startsWith(".") || host.startsWith("-")) return "";
        return host + ":" + porta;
    }

    /** O codigo do PC: so letras e numeros, em maiusculas. Se vier o link inteiro, pega o que esta em "?c=". */
    public static String codigo(String texto) {
        if (texto == null) return "";
        String t = texto.trim();
        int i = t.indexOf("?c=");
        if (i >= 0) t = t.substring(i + 3);
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < t.length(); k++) {
            char c = t.charAt(k);
            if (Character.isLetterOrDigit(c) && c < 128) sb.append(Character.toUpperCase(c));
        }
        return sb.length() == TAMANHO_CODIGO ? sb.toString() : "";
    }

    /** O pedido sem espacos repetidos nem caracteres de controle; "" se for curto ou longo demais. */
    public static String pedido(String texto) {
        if (texto == null) return "";
        String t = texto.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
        return t.length() < 2 || t.length() > MAX_PEDIDO ? "" : t;
    }

    public static String url(String endereco, String codigo) {
        return "http://" + endereco + "/api/baixar?c=" + codigo;
    }

    public static String corpo(String pedido) {
        StringBuilder sb = new StringBuilder("{\"pedido\":\"");
        for (int i = 0; i < pedido.length(); i++) {
            char c = pedido.charAt(i);
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c < 32) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.append("\"}").toString();
    }

    /** Envia o pedido; devolve o codigo HTTP, ou -1 se nao chegou ao PC. Chamar fora da thread da tela. */
    public static int enviar(String endereco, String codigo, String pedido) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) java.net.URI.create(url(endereco, codigo)).toURL().openConnection();
            con.setConnectTimeout(4000);
            con.setReadTimeout(8000);
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] dados = corpo(pedido).getBytes(StandardCharsets.UTF_8);
            con.setFixedLengthStreamingMode(dados.length);
            try (OutputStream out = con.getOutputStream()) {
                out.write(dados);
            }
            return con.getResponseCode();
        } catch (Exception e) {
            return -1;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    /** O que mostrar ao usuario para cada resposta. */
    public static String mensagem(int status) {
        switch (status) {
            case 200: return "Pedido enviado ao PC.";
            case 400: return "Pedido inválido. Escreva um link ou o nome da música.";
            case 401: return "Código incorreto. Confira o código mostrado no PC.";
            case 429: return "Muitas tentativas erradas. Aguarde um pouco.";
            case 503: return "O PC não está aceitando downloads agora.";
            case -1: return "Não encontrei o PC. Confira o endereço e se os dois estão na mesma rede.";
            default: return "O PC respondeu com erro (" + status + ").";
        }
    }
}
