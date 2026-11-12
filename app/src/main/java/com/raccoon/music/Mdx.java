package com.raccoon.music;

import java.util.ArrayList;
import java.util.List;

/**
 * Separação de voz com modelo MDX-Net (mesmo algoritmo do Raccoon Music do PC), sem nada do Android:
 * STFT, execução do modelo por blocos e STFT inversa. A execução do modelo entra por {@link Inferencia}.
 */
public final class Mdx {
    private Mdx() {}

    /** Parâmetros do modelo. */
    public static final class Modelo {
        public final int dimF, dimT, nFft, hop;
        public final float compensacao;

        public Modelo(int dimF, int dimT, int nFft, int hop, float compensacao) {
            this.dimF = dimF;
            this.dimT = dimT;
            this.nFft = nFft;
            this.hop = hop;
            this.compensacao = compensacao;
        }
    }

    /** Kim_Vocal_2 (o mesmo modelo do PC). */
    public static final Modelo KIM_VOCAL_2 = new Modelo(3072, 256, 7680, 1024, 1.009f);
    public static final int TAXA = 44100;

    /** Executa o modelo: entrada (1, 4, dimF, dimT) achatada em ordem de linhas; devolve o mesmo formato. */
    public interface Inferencia {
        float[] executar(float[] entrada) throws Exception;
    }

    /** Recebe cada pedaço de voz estimada: `inicio` é a amostra onde o pedaço começa; pedaco[canal][amostra]. */
    public interface Pedaco {
        void receber(int inicio, float[][] pedaco) throws Exception;
    }

    public interface Progresso {
        void fracao(float f);
    }

    public static class Cancelado extends Exception {
        public Cancelado() {
            super("cancelado");
        }
    }

    // ------------------------------------------------------------------ FFT de tamanho qualquer (fatores 2, 3, 5, ...)
    /** Transformada de Fourier complexa para um tamanho fixo (decomposição em fatores primos). */
    public static final class Fft {
        private final int n;
        private final int[] fatores;
        private final double[] cos, sen;
        private final double[] tmpRe, tmpIm, resRe, resIm;

        public Fft(int n) {
            this.n = n;
            List<Integer> f = new ArrayList<>();
            int resto = n;
            for (int p = 2; p * p <= resto; p += (p == 2 ? 1 : 2)) {
                while (resto % p == 0) {
                    f.add(p);
                    resto /= p;
                }
            }
            if (resto > 1) f.add(resto);
            fatores = new int[f.size()];
            for (int i = 0; i < fatores.length; i++) fatores[i] = f.get(i);
            cos = new double[n];
            sen = new double[n];
            for (int k = 0; k < n; k++) {
                cos[k] = Math.cos(2 * Math.PI * k / n);
                sen[k] = -Math.sin(2 * Math.PI * k / n);  // e^(-2πik/n)
            }
            int maior = 1;
            for (int p : fatores) maior = Math.max(maior, p);
            tmpRe = new double[maior];
            tmpIm = new double[maior];
            resRe = new double[maior];
            resIm = new double[maior];
        }

        /** Transformada direta: saída em (outRe, outIm); a entrada não muda. */
        public void direta(double[] inRe, double[] inIm, double[] outRe, double[] outIm) {
            rec(inRe, inIm, 0, 1, outRe, outIm, 0, n, 0);
        }

        /** Inversa (já dividida por n). */
        public void inversa(double[] inRe, double[] inIm, double[] outRe, double[] outIm) {
            double[] cim = new double[n];
            for (int i = 0; i < n; i++) cim[i] = -inIm[i];
            rec(inRe, cim, 0, 1, outRe, outIm, 0, n, 0);
            for (int i = 0; i < n; i++) {
                outRe[i] /= n;
                outIm[i] = -outIm[i] / n;
            }
        }

        private void rec(double[] inRe, double[] inIm, int ini, int passo, double[] outRe, double[] outIm, int outIni, int tam, int nivel) {
            if (tam == 1) {
                outRe[outIni] = inRe[ini];
                outIm[outIni] = inIm[ini];
                return;
            }
            int p = fatores[nivel];
            int m = tam / p;
            for (int r = 0; r < p; r++) rec(inRe, inIm, ini + r * passo, passo * p, outRe, outIm, outIni + r * m, m, nivel + 1);
            int salto = n / tam;  // W_tam^t = W_n^(t*salto)
            for (int k = 0; k < m; k++) {
                for (int r = 0; r < p; r++) {
                    int t = (r * k * salto) % n;
                    double re = outRe[outIni + r * m + k], im = outIm[outIni + r * m + k];
                    tmpRe[r] = re * cos[t] - im * sen[t];
                    tmpIm[r] = re * sen[t] + im * cos[t];
                }
                for (int q = 0; q < p; q++) {
                    double sr = 0, si = 0;
                    for (int r = 0; r < p; r++) {
                        int t = ((r * q * m) % tam) * salto;
                        sr += tmpRe[r] * cos[t] - tmpIm[r] * sen[t];
                        si += tmpRe[r] * sen[t] + tmpIm[r] * cos[t];
                    }
                    resRe[q] = sr;
                    resIm[q] = si;
                }
                for (int q = 0; q < p; q++) {
                    outRe[outIni + q * m + k] = resRe[q];
                    outIm[outIni + q * m + k] = resIm[q];
                }
            }
        }
    }

    // ------------------------------------------------------------------ STFT
    private static float[] janela(int nFft) {
        float[] j = new float[nFft];
        for (int i = 0; i < nFft; i++) j[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / nFft));  // Hann periódica
        return j;
    }

    private static float amostra(float[] x, int idx) {
        if (idx < 0) idx = -idx;  // reflexão nas pontas, como o center=True do torch
        if (idx >= x.length) idx = 2 * (x.length - 1) - idx;
        return x[Math.max(0, Math.min(x.length - 1, idx))];
    }

    /**
     * sinal[2][amostra] (estéreo) -> {re, im}[canal][frequência][quadro] com `bins` frequências (como torch.stft com
     * center=True). Os dois canais vão juntos numa única FFT complexa (um como parte real, o outro como imaginária).
     */
    public static float[][][][] stft(float[][] sinal, int nFft, int hop, int bins) {
        float[] w = janela(nFft);
        Fft fft = new Fft(nFft);
        int pad = nFft / 2;
        int comp = sinal[0].length;
        int quadros = 1 + (comp + 2 * pad - nFft) / hop;
        float[][][][] saida = new float[2][2][bins][quadros];
        double[] re = new double[nFft], im = new double[nFft], oRe = new double[nFft], oIm = new double[nFft];
        for (int q = 0; q < quadros; q++) {
            int ini = q * hop - pad;
            for (int i = 0; i < nFft; i++) {
                re[i] = amostra(sinal[0], ini + i) * w[i];
                im[i] = amostra(sinal[1], ini + i) * w[i];
            }
            fft.direta(re, im, oRe, oIm);
            for (int f = 0; f < bins; f++) {
                int g = (nFft - f) % nFft;
                double zr = oRe[f], zi = oIm[f], cr = oRe[g], ci = -oIm[g];  // Z[f] e conj(Z[N-f])
                saida[0][0][f][q] = (float) ((zr + cr) / 2);
                saida[1][0][f][q] = (float) ((zi + ci) / 2);
                saida[0][1][f][q] = (float) ((zi - ci) / 2);
                saida[1][1][f][q] = (float) (-(zr - cr) / 2);
            }
        }
        return saida;
    }

    /** Inversa de {@link #stft}: espectro {re, im}[2][frequência][quadro] (frequências além de `bins` são zero) -> sinal[2][amostras]. */
    public static float[][] istft(float[][][][] esp, int nFft, int hop, int amostras) {
        float[] w = janela(nFft);
        Fft fft = new Fft(nFft);
        int bins = esp[0][0].length;
        int quadros = esp[0][0][0].length;
        int total = nFft + hop * (quadros - 1);
        float[] peso = new float[total];
        for (int q = 0; q < quadros; q++) for (int i = 0; i < nFft; i++) peso[q * hop + i] += w[i] * w[i];
        float[][] acum = new float[2][total];
        double[] re = new double[nFft], im = new double[nFft], oRe = new double[nFft], oIm = new double[nFft];
        int metade = nFft / 2;
        for (int q = 0; q < quadros; q++) {
            java.util.Arrays.fill(re, 0);
            java.util.Arrays.fill(im, 0);
            for (int f = 0; f < bins && f <= metade; f++) {
                double ar = esp[0][0][f][q], ai = esp[1][0][f][q], br = esp[0][1][f][q], bi = esp[1][1][f][q];
                if (f == 0 || f == metade) {  // como irfft: a parte imaginária de DC e Nyquist não conta
                    ai = 0;
                    bi = 0;
                }
                re[f] = ar - bi;  // S = A + iB
                im[f] = ai + br;
                if (f != 0 && f != metade) {  // espelho hermitiano: S[N-f] = conj(A[f]) + i conj(B[f])
                    re[nFft - f] = ar + bi;
                    im[nFft - f] = -ai + br;
                }
            }
            fft.inversa(re, im, oRe, oIm);
            for (int i = 0; i < nFft; i++) {
                acum[0][q * hop + i] += (float) (oRe[i] * w[i]);
                acum[1][q * hop + i] += (float) (oIm[i] * w[i]);
            }
        }
        float[][] saida = new float[2][amostras];
        for (int c = 0; c < 2; c++) {
            for (int i = 0; i < amostras; i++) {
                int idx = metade + i;
                saida[c][i] = idx < total ? acum[c][idx] / Math.max(peso[idx], 1e-8f) : 0f;
            }
        }
        return saida;
    }

    // ------------------------------------------------------------------ inferência por blocos
    /** Duas amostras por canal viram estéreo: mistura[2][n]. Estima a voz bloco a bloco e entrega cada pedaço em ordem. */
    public static void estimarVoz(float[][] mistura, Modelo m, Inferencia inf, Progresso progresso, java.util.function.BooleanSupplier cancelado, Pedaco saida)
            throws Exception {
        int nFft = m.nFft, hop = m.hop, dimF = m.dimF, dimT = m.dimT;
        int corte = nFft / 2;
        int bloco = hop * (dimT - 1);
        int util = bloco - 2 * corte;
        int n = mistura[0].length;
        int sobra = util + corte - (n % util);
        int tamMix = corte + n + sobra;
        int quantos = 0;
        for (int ini = 0; ini < tamMix - corte - 1; ini += util) quantos++;
        int feitos = 0;
        float[][] janela = new float[2][bloco];
        for (int ini = 0; ini < tamMix - corte - 1; ini += util) {
            if (cancelado != null && cancelado.getAsBoolean()) throw new Cancelado();
            for (int c = 0; c < 2; c++) {
                for (int i = 0; i < bloco; i++) {
                    int pos = ini + i - corte;  // índice na mistura original (antes dela: zeros)
                    janela[c][i] = pos >= 0 && pos < n ? mistura[c][pos] : 0f;
                }
            }
            float[][][][] esp = stft(janela, nFft, hop, dimF);  // {re,im}[2][dimF][dimT]
            float[] entrada = new float[4 * dimF * dimT];
            for (int c = 0; c < 2; c++) {
                for (int f = 0; f < dimF; f++) {
                    System.arraycopy(esp[0][c][f], 0, entrada, ((2 * c) * dimF + f) * dimT, dimT);
                    System.arraycopy(esp[1][c][f], 0, entrada, ((2 * c + 1) * dimF + f) * dimT, dimT);
                }
            }
            float[] resposta = inf.executar(entrada);
            float[][][][] estimado = new float[2][2][dimF][dimT];
            for (int c = 0; c < 2; c++) {
                for (int f = 0; f < dimF; f++) {
                    for (int t = 0; t < dimT; t++) {
                        estimado[0][c][f][t] = resposta[((2 * c) * dimF + f) * dimT + t] * m.compensacao;
                        estimado[1][c][f][t] = resposta[((2 * c + 1) * dimF + f) * dimT + t] * m.compensacao;
                    }
                }
            }
            float[][] voz = istft(estimado, nFft, hop, bloco);
            int inicioSaida = ini;  // posição (em amostras da mistura) do primeiro item útil: ini - corte + corte
            int ate = Math.min(util, n - inicioSaida);
            if (ate > 0) {
                float[][] pedaco = new float[2][ate];
                for (int c = 0; c < 2; c++) System.arraycopy(voz[c], corte, pedaco[c], 0, ate);
                saida.receber(inicioSaida, pedaco);
            }
            feitos++;
            if (progresso != null) progresso.fracao(feitos / (float) quantos);
        }
    }

    /** Reamostragem linear de `de` Hz para `para` Hz (usada só quando a música não está em 44,1 kHz). */
    public static float[] reamostrar(float[] x, int de, int para) {
        if (de == para || x.length == 0) return x;
        int n = (int) ((long) x.length * para / de);
        float[] r = new float[n];
        double passo = (double) de / para;
        for (int i = 0; i < n; i++) {
            double pos = i * passo;
            int a = (int) pos;
            int b = Math.min(a + 1, x.length - 1);
            double fr = pos - a;
            r[i] = (float) (x[Math.min(a, x.length - 1)] * (1 - fr) + x[b] * fr);
        }
        return r;
    }
}
