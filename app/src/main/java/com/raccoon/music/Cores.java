package com.raccoon.music;

/** As cores do app. A pessoa escolhe so duas (fundo e destaque); o resto e calculado, para o texto nunca ficar ilegivel. Sem Android, para poder ser testado. */
public final class Cores {
    private Cores() {}

    public static final int PADRAO_FUNDO = 0xFF18181B;
    public static final int PADRAO_ACENTO = 0xFFE9532E;

    public static final int[] FUNDOS = {0xFF18181B, 0xFF000000, 0xFF202124, 0xFF101522, 0xFF0F1A16, 0xFF1C1216, 0xFFF4F4F6};
    public static final int[] ACENTOS = {0xFFE9532E, 0xFFFF8A1F, 0xFFE8B923, 0xFF2EB872, 0xFF1FB5A6, 0xFF2E8BE9, 0xFF6C5CE7, 0xFFB44EE0, 0xFFE9418A, 0xFFE03A3A, 0xFF7A8B99, 0xFF8BC34A};

    public static final class Paleta {
        public final int fundo, cartao, linha, texto, suave, icone, acento;
        public final boolean claro;

        Paleta(int fundo, int cartao, int linha, int texto, int suave, int icone, int acento, boolean claro) {
            this.fundo = fundo;
            this.cartao = cartao;
            this.linha = linha;
            this.texto = texto;
            this.suave = suave;
            this.icone = icone;
            this.acento = acento;
            this.claro = claro;
        }
    }

    private static double canal(int v) {
        double c = v / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /** Luminancia de 0 (preto) a 1 (branco). */
    public static double luminancia(int cor) {
        return 0.2126 * canal((cor >> 16) & 255) + 0.7152 * canal((cor >> 8) & 255) + 0.0722 * canal(cor & 255);
    }

    /** Mistura `a` com `b`: t = 0 fica em `a`, t = 1 vira `b`. */
    public static int misturar(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** Cor 0 (nao escolhida) vira o padrao. */
    public static Paleta montar(int fundo, int acento) {
        if (fundo == 0) fundo = PADRAO_FUNDO;
        if (acento == 0) acento = PADRAO_ACENTO;
        fundo |= 0xFF000000;
        acento |= 0xFF000000;
        for (int i = 0; i < 6 && luminancia(acento) > 0.40; i++) acento = misturar(acento, 0xFF000000, 0.15);  // o texto branco precisa ler em cima
        boolean claro = luminancia(fundo) > 0.5;
        if (claro) {
            return new Paleta(fundo, misturar(fundo, 0xFFFFFFFF, 0.7), misturar(fundo, 0xFF000000, 0.10), 0xFF18181B, 0xFF6B6B76, 0xFF55555F, acento, true);
        }
        return new Paleta(fundo, misturar(fundo, 0xFFFFFFFF, 0.045), misturar(fundo, 0xFFFFFFFF, 0.09), 0xFFFFFFFF, 0xFF9A9AA6, 0xFFC8C8D0, acento, false);
    }
}
