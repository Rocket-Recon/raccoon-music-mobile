package com.raccoon.music;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Grava áudio PCM de 16 bits em arquivo WAV, aos poucos (o cabeçalho é completado ao fechar). Sem Android. */
public final class Wav {
    private Wav() {}

    public static final class Escritor implements java.io.Closeable {
        private final RandomAccessFile arq;
        private final int canais;
        private final int taxa;
        private long bytes;

        public Escritor(File destino, int taxa, int canais) throws IOException {
            this.taxa = taxa;
            this.canais = canais;
            arq = new RandomAccessFile(destino, "rw");
            arq.setLength(0);
            arq.write(new byte[44]);
        }

        /** pedaco[canal][amostra], valores de -1 a 1. */
        public void escrever(float[][] pedaco) throws IOException {
            int n = pedaco[0].length;
            byte[] buf = new byte[n * canais * 2];
            int p = 0;
            for (int i = 0; i < n; i++) {
                for (int c = 0; c < canais; c++) {
                    float v = Math.max(-1f, Math.min(1f, pedaco[c][i]));
                    int s = Math.round(v * 32767f);
                    buf[p++] = (byte) (s & 0xFF);
                    buf[p++] = (byte) ((s >> 8) & 0xFF);
                }
            }
            arq.write(buf);
            bytes += buf.length;
        }

        @Override
        public void close() throws IOException {
            arq.seek(0);
            byte[] h = new byte[44];
            escreverTexto(h, 0, "RIFF");
            inteiro(h, 4, (int) (36 + bytes));
            escreverTexto(h, 8, "WAVE");
            escreverTexto(h, 12, "fmt ");
            inteiro(h, 16, 16);
            curto(h, 20, 1);
            curto(h, 22, canais);
            inteiro(h, 24, taxa);
            inteiro(h, 28, taxa * canais * 2);
            curto(h, 32, canais * 2);
            curto(h, 34, 16);
            escreverTexto(h, 36, "data");
            inteiro(h, 40, (int) bytes);
            arq.write(h);
            arq.close();
        }
    }

    private static void escreverTexto(byte[] b, int pos, String t) {
        for (int i = 0; i < t.length(); i++) b[pos + i] = (byte) t.charAt(i);
    }

    private static void inteiro(byte[] b, int pos, int v) {
        b[pos] = (byte) v;
        b[pos + 1] = (byte) (v >> 8);
        b[pos + 2] = (byte) (v >> 16);
        b[pos + 3] = (byte) (v >> 24);
    }

    private static void curto(byte[] b, int pos, int v) {
        b[pos] = (byte) v;
        b[pos + 1] = (byte) (v >> 8);
    }
}
