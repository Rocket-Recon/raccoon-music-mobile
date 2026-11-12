package com.raccoon.music;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.function.BooleanSupplier;

/**
 * Separa a voz do instrumental de uma música no próprio celular (modelo MDX-Net Kim_Vocal_2 em ONNX, o mesmo do PC).
 * As faixas separadas ficam em cache (temporárias) até a pessoa salvá-las; salvas, ficam na pasta do app.
 * Chamar fora da thread da tela.
 */
public final class Separador {
    private Separador() {}

    public static final String VOZ = "voz", INSTRUMENTAL = "instrumental";
    private static final String URL_MODELO = "https://github.com/TRvlvr/model_repo/releases/download/all_public_uvr_models/Kim_Vocal_2.onnx";
    private static final long TAMANHO_MINIMO_DO_MODELO = 20L * 1024 * 1024;

    public interface Aviso {
        void progresso(float fracao, String texto);
    }

    // ------------------------------------------------------------------ arquivos
    private static String codigo(Faixa f) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(f.chave.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", h[i]));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(f.chave.hashCode());
        }
    }

    private static File pasta(File base, String nome) {
        File d = new File(base, nome);
        d.mkdirs();
        return d;
    }

    private static File temporaria(Context c, Faixa f, String modo) {
        return new File(pasta(c.getCacheDir(), "separadas"), codigo(f) + "_" + modo + ".wav");
    }

    private static File salva(Context c, Faixa f, String modo) {
        return new File(pasta(c.getFilesDir(), "separadas"), codigo(f) + "_" + modo + ".wav");
    }

    public static boolean salvas(Context c, Faixa f) {
        return salva(c, f, VOZ).exists() && salva(c, f, INSTRUMENTAL).exists();
    }

    public static boolean temporarias(Context c, Faixa f) {
        return temporaria(c, f, VOZ).exists() && temporaria(c, f, INSTRUMENTAL).exists();
    }

    /** O arquivo da faixa separada (salvo ou temporário); null quando ainda não foi separada. */
    public static File arquivo(Context c, Faixa f, String modo) {
        if (salvas(c, f)) return salva(c, f, modo);
        if (temporarias(c, f)) return temporaria(c, f, modo);
        return null;
    }

    /** Move as faixas temporárias para a pasta do app: ficam guardadas até serem excluídas. */
    public static boolean guardar(Context c, Faixa f) {
        if (!temporarias(c, f)) return salvas(c, f);
        for (String m : new String[]{VOZ, INSTRUMENTAL}) {
            File de = temporaria(c, f, m), para = salva(c, f, m);
            if (!de.renameTo(para)) {
                try (InputStream in = new java.io.FileInputStream(de); FileOutputStream out = new FileOutputStream(para)) {
                    byte[] b = new byte[1 << 16];
                    int n;
                    while ((n = in.read(b)) > 0) out.write(b, 0, n);
                } catch (Exception e) {
                    return false;
                }
                de.delete();
            }
        }
        return true;
    }

    public static void excluirSalvas(Context c, Faixa f) {
        salva(c, f, VOZ).delete();
        salva(c, f, INSTRUMENTAL).delete();
    }

    /** Apaga as faixas temporárias (todas, ou todas menos as de `exceto`). */
    public static void limparTemporarias(Context c, Faixa exceto) {
        File[] lista = pasta(c.getCacheDir(), "separadas").listFiles();
        if (lista == null) return;
        String manter = exceto == null ? null : codigo(exceto);
        for (File a : lista) if (manter == null || !a.getName().startsWith(manter)) a.delete();
    }

    // ------------------------------------------------------------------ modelo
    private static File arquivoDoModelo(Context c) {
        return new File(pasta(c.getFilesDir(), "modelos"), "Kim_Vocal_2.onnx");
    }

    public static boolean modeloBaixado(Context c) {
        File a = arquivoDoModelo(c);
        return a.exists() && a.length() >= TAMANHO_MINIMO_DO_MODELO;
    }

    private static void baixarModelo(Context c, Aviso aviso, BooleanSupplier cancelado) throws Exception {
        File destino = arquivoDoModelo(c);
        File parcial = new File(destino.getPath() + ".part");
        HttpURLConnection con = (HttpURLConnection) new URL(URL_MODELO).openConnection();
        con.setConnectTimeout(15_000);
        con.setReadTimeout(30_000);
        con.setInstanceFollowRedirects(true);
        if (con.getResponseCode() != 200) throw new Exception("Não consegui baixar o modelo de separação (erro " + con.getResponseCode() + ").");
        long total = con.getContentLengthLong();
        try (InputStream in = con.getInputStream(); FileOutputStream out = new FileOutputStream(parcial)) {
            byte[] b = new byte[1 << 16];
            long lidos = 0;
            int n;
            while ((n = in.read(b)) > 0) {
                if (cancelado.getAsBoolean()) {
                    parcial.delete();
                    throw new Mdx.Cancelado();
                }
                out.write(b, 0, n);
                lidos += n;
                if (total > 0) aviso.progresso(0.15f * lidos / total, "Baixando o modelo (só na primeira vez)… " + (lidos * 100 / total) + "%");
            }
        }
        if (parcial.length() < TAMANHO_MINIMO_DO_MODELO) {
            parcial.delete();
            throw new Exception("O modelo baixado está incompleto. Tente de novo.");
        }
        if (!parcial.renameTo(destino)) throw new Exception("Não consegui guardar o modelo.");
    }

    // ------------------------------------------------------------------ áudio
    /** Decodifica a música para estéreo float em 44,1 kHz. */
    private static float[][] decodificar(Context c, Uri uri, BooleanSupplier cancelado) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        MediaCodec codec = null;
        try {
            ex.setDataSource(c, uri, null);
            int faixa = -1;
            MediaFormat fmt = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    faixa = i;
                    fmt = f;
                    break;
                }
            }
            if (faixa < 0) throw new Exception("Não consegui ler o áudio desta música.");
            ex.selectTrack(faixa);
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
            codec.configure(fmt, null, null, 0);
            codec.start();
            int taxa = fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) : Mdx.TAXA;
            int canais = fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;
            boolean ponto = false;
            long duracaoUs = fmt.containsKey(MediaFormat.KEY_DURATION) ? fmt.getLong(MediaFormat.KEY_DURATION) : 0L;
            int capacidade = (int) Math.max(1 << 20, duracaoUs * taxa / 1_000_000L + taxa);
            float[] esq = new float[capacidade], dir = new float[capacidade];
            int n = 0;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean fimEntrada = false, fimSaida = false;
            while (!fimSaida) {
                if (cancelado.getAsBoolean()) throw new Mdx.Cancelado();
                if (!fimEntrada) {
                    int i = codec.dequeueInputBuffer(10_000);
                    if (i >= 0) {
                        ByteBuffer entrada = codec.getInputBuffer(i);
                        int lidos = ex.readSampleData(entrada, 0);
                        if (lidos < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            fimEntrada = true;
                        } else {
                            codec.queueInputBuffer(i, 0, lidos, ex.getSampleTime(), 0);
                            ex.advance();
                        }
                    }
                }
                int o = codec.dequeueOutputBuffer(info, 10_000);
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat saida = codec.getOutputFormat();
                    if (saida.containsKey(MediaFormat.KEY_SAMPLE_RATE)) taxa = saida.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (saida.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) canais = saida.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    ponto = saida.containsKey("pcm-encoding") && saida.getInteger("pcm-encoding") == android.media.AudioFormat.ENCODING_PCM_FLOAT;
                } else if (o >= 0) {
                    ByteBuffer buf = codec.getOutputBuffer(o);
                    if (buf != null && info.size > 0) {
                        buf.position(info.offset);
                        buf.limit(info.offset + info.size);
                        buf.order(ByteOrder.nativeOrder());
                        int quadros;
                        ShortBuffer sb = null;
                        FloatBuffer fb = null;
                        if (ponto) {
                            fb = buf.asFloatBuffer();
                            quadros = fb.remaining() / canais;
                        } else {
                            sb = buf.asShortBuffer();
                            quadros = sb.remaining() / canais;
                        }
                        if (n + quadros > esq.length) {
                            int nova = Math.max(esq.length * 2, n + quadros);
                            esq = java.util.Arrays.copyOf(esq, nova);
                            dir = java.util.Arrays.copyOf(dir, nova);
                        }
                        for (int q = 0; q < quadros; q++) {
                            float l, r;
                            if (ponto) {
                                l = fb.get();
                                r = canais > 1 ? fb.get() : l;
                                for (int k = 2; k < canais; k++) fb.get();
                            } else {
                                l = sb.get() / 32768f;
                                r = canais > 1 ? sb.get() / 32768f : l;
                                for (int k = 2; k < canais; k++) sb.get();
                            }
                            esq[n] = l;
                            dir[n] = r;
                            n++;
                        }
                    }
                    codec.releaseOutputBuffer(o, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) fimSaida = true;
                }
            }
            if (n == 0) throw new Exception("Não consegui ler o áudio desta música.");
            float[] l = java.util.Arrays.copyOf(esq, n), r = java.util.Arrays.copyOf(dir, n);
            if (taxa != Mdx.TAXA) {
                l = Mdx.reamostrar(l, taxa, Mdx.TAXA);
                r = Mdx.reamostrar(r, taxa, Mdx.TAXA);
            }
            return new float[][]{l, r};
        } finally {
            try {
                if (codec != null) {
                    codec.stop();
                    codec.release();
                }
            } catch (Exception e) {
                // já encerrado
            }
            ex.release();
        }
    }

    // ------------------------------------------------------------------ separação
    /** Separa a música em voz e instrumental (arquivos temporários). Lança Mdx.Cancelado se `cancelado` virar true. */
    public static void separar(Context c, Faixa f, Aviso aviso, BooleanSupplier cancelado) throws Exception {
        if (temporarias(c, f) || salvas(c, f)) return;
        if (!modeloBaixado(c)) baixarModelo(c, aviso, cancelado);
        aviso.progresso(0.15f, "Lendo a música…");
        final float[][] mistura = decodificar(c, Uri.parse(f.uri), cancelado);
        aviso.progresso(0.2f, "Separando a voz…");
        final File vozParcial = new File(temporaria(c, f, VOZ).getPath() + ".part");
        final File instParcial = new File(temporaria(c, f, INSTRUMENTAL).getPath() + ".part");
        ai.onnxruntime.OrtEnvironment env = ai.onnxruntime.OrtEnvironment.getEnvironment();
        ai.onnxruntime.OrtSession.SessionOptions op = new ai.onnxruntime.OrtSession.SessionOptions();
        op.setIntraOpNumThreads(Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)));
        final Mdx.Modelo m = Mdx.KIM_VOCAL_2;
        final long[] forma = {1, 4, m.dimF, m.dimT};
        final ByteBuffer direto = ByteBuffer.allocateDirect(4 * m.dimF * m.dimT * 4).order(ByteOrder.nativeOrder());
        final FloatBuffer entradaDireta = direto.asFloatBuffer();
        boolean ok = false;
        try (ai.onnxruntime.OrtSession sessao = env.createSession(arquivoDoModelo(c).getPath(), op);
             Wav.Escritor voz = new Wav.Escritor(vozParcial, Mdx.TAXA, 2);
             Wav.Escritor inst = new Wav.Escritor(instParcial, Mdx.TAXA, 2)) {
            final String nomeEntrada = sessao.getInputNames().iterator().next();
            Mdx.estimarVoz(mistura, m, entrada -> {
                entradaDireta.clear();
                entradaDireta.put(entrada);
                entradaDireta.rewind();
                try (ai.onnxruntime.OnnxTensor t = ai.onnxruntime.OnnxTensor.createTensor(env, entradaDireta, forma);
                     ai.onnxruntime.OrtSession.Result r = sessao.run(Collections.singletonMap(nomeEntrada, t))) {
                    FloatBuffer saida = ((ai.onnxruntime.OnnxTensor) r.get(0)).getFloatBuffer();
                    float[] resposta = new float[saida.remaining()];
                    saida.get(resposta);
                    return resposta;
                }
            }, fr -> aviso.progresso(0.2f + 0.8f * fr, "Separando a voz… " + Math.round(fr * 100) + "%"), cancelado, (inicio, pedaco) -> {
                int n = pedaco[0].length;
                float[][] resto = new float[2][n];
                for (int cn = 0; cn < 2; cn++) for (int i = 0; i < n; i++) resto[cn][i] = mistura[cn][inicio + i] - pedaco[cn][i];
                voz.escrever(pedaco);
                inst.escrever(resto);
            });
            ok = true;
        } finally {
            op.close();
            if (!ok) {
                vozParcial.delete();
                instParcial.delete();
            }
        }
        if (!vozParcial.renameTo(temporaria(c, f, VOZ)) || !instParcial.renameTo(temporaria(c, f, INSTRUMENTAL))) {
            throw new Exception("Não consegui guardar as faixas separadas.");
        }
    }
}
