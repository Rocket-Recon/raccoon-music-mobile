package com.raccoon.music;

import java.util.ArrayList;
import java.util.List;

/** Escolhe, entre os resultados de uma busca por nome, o video que mais provavelmente e a musica pedida (sem Android). */
public final class Escolha {
    /** Um resultado da busca. `duracaoS` 0 e `visualizacoes` 0 quando nao se sabe. */
    public static final class Candidato {
        public final String id;
        public final String titulo;
        public final String canal;
        public final long duracaoS;
        public final long visualizacoes;

        public Candidato(String id, String titulo, String canal, long duracaoS, long visualizacoes) {
            this.id = id == null ? "" : id;
            this.titulo = titulo == null ? "" : titulo;
            this.canal = canal == null ? "" : canal;
            this.duracaoS = duracaoS;
            this.visualizacoes = visualizacoes;
        }
    }

    /** Termos que indicam outra coisa que nao a gravacao original; so contam contra quando o pedido nao os traz. */
    private static final String[] INDESEJADOS = {
            "live", "ao vivo", "cover", "karaoke", "reaction", "reacao", "remix", "slowed", "sped up", "speed up", "nightcore",
            "8d", "bass boosted", "tutorial", "full album", "album completo", "playlist", "compilation", "1 hour", "10 hours",
    };

    private Escolha() {
    }

    /** Pontuacao do candidato para o pedido; maior e melhor. */
    public static double pontuar(String pedido, Candidato c) {
        String q = Faixa.normalizar(pedido).replaceAll("[^\\p{L}\\p{N} ]", " ").replaceAll("\\s+", " ").trim();
        String t = Faixa.normalizar(c.titulo).replaceAll("[^\\p{L}\\p{N} ]", " ").replaceAll("\\s+", " ").trim();
        String canal = Faixa.normalizar(c.canal);
        String alvo = " " + t + " " + canal.replaceAll("[^\\p{L}\\p{N} ]", " ") + " ";
        double pontos = 0;
        String[] palavras = q.isEmpty() ? new String[0] : q.split(" ");
        int achadas = 0;
        for (String p : palavras) if (alvo.contains(" " + p + " ") || alvo.contains(p)) achadas++;
        double cobertura = palavras.length == 0 ? 1 : (double) achadas / palavras.length;
        pontos += 40 * cobertura;
        if (cobertura < 0.6) pontos -= 50;
        if (!q.isEmpty() && (" " + t + " ").contains(" " + q + " ")) pontos += 15;
        if (canal.endsWith("- topic")) pontos += 25;
        if (canal.contains("vevo")) pontos += 6;
        String tb = " " + t + " ";
        if (tb.contains(" official audio ") || tb.contains(" audio oficial ")) pontos += 8;
        else if (tb.contains(" official video ") || tb.contains(" official music video ") || tb.contains(" clipe oficial ")) pontos += 4;
        String qb = " " + q + " ";
        for (String ruim : INDESEJADOS) {
            if (tb.contains(" " + ruim + " ") && !qb.contains(" " + ruim + " ")) pontos -= 30;
        }
        if (c.duracaoS > 0) {
            if (c.duracaoS < 60) pontos -= 40;
            else if (c.duracaoS > 720) pontos -= 35;
            else if (c.duracaoS >= 90 && c.duracaoS <= 480) pontos += 5;
        }
        if (c.visualizacoes > 0) pontos += Math.min(5.0, Math.log10(c.visualizacoes + 1));
        return pontos;
    }

    /** Posicao do melhor candidato; em empate vence o que veio antes na busca. -1 se a lista esta vazia. */
    public static int melhor(String pedido, List<Candidato> lista) {
        int melhor = -1;
        double maior = -1e9;
        for (int i = 0; i < lista.size(); i++) {
            double p = pontuar(pedido, lista.get(i)) - i * 0.5;
            if (p > maior) {
                maior = p;
                melhor = i;
            }
        }
        return melhor;
    }

    /** Endereco da pagina do video. */
    public static String endereco(Candidato c) {
        return "https://www.youtube.com/watch?v=" + c.id;
    }

    static List<Candidato> vazia() {
        return new ArrayList<>();
    }
}
