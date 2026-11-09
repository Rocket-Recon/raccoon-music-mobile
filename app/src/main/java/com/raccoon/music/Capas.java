package com.raccoon.music;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** As capas pequenas das listas: le a imagem de dentro do arquivo (so das musicas que aparecem na tela) e guarda no celular para abrir rapido. */
public final class Capas {
    public interface Pronta {
        /** Chamado na tela principal; `capa` e null quando a musica nao tem imagem. `ainda` diz se quem pediu continua querendo. */
        void aqui(Bitmap capa);
    }

    public interface Interesse {
        boolean ainda();
    }

    private static final int LADO = 192;
    private final Context contexto;
    private final File pasta;
    private final LruCache<String, Bitmap> memoria = new LruCache<>(150);
    private final java.util.Set<String> semCapa = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());
    private final ExecutorService fila = Executors.newFixedThreadPool(2);
    private final Handler ui = new Handler(Looper.getMainLooper());

    public Capas(Context contexto) {
        this.contexto = contexto.getApplicationContext();
        this.pasta = new File(this.contexto.getCacheDir(), "capas");
        pasta.mkdirs();
    }

    private static String nomeDoArquivo(String uri) {
        return Integer.toHexString(uri.hashCode()) + "_" + uri.length();
    }

    private static final int LADO_GUARDADO = 256;

    /** Guarda a capa de uma música recém-baixada (recortada em quadrado) para as listas e o player acharem sem depender da imagem dentro do arquivo. */
    public static boolean guardar(Context contexto, String uri, byte[] imagem) {
        if (imagem == null || imagem.length == 0) return false;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(imagem, 0, imagem.length, o);
            int menor = Math.max(1, Math.min(o.outWidth, o.outHeight));
            o.inSampleSize = Math.max(1, menor / (LADO_GUARDADO * 2));
            o.inJustDecodeBounds = false;
            Bitmap original = BitmapFactory.decodeByteArray(imagem, 0, imagem.length, o);
            if (original == null) return false;
            int lado = Math.min(original.getWidth(), original.getHeight());
            Bitmap quadrada = Bitmap.createBitmap(original, (original.getWidth() - lado) / 2, (original.getHeight() - lado) / 2, lado, lado);
            Bitmap pronta = Bitmap.createScaledBitmap(quadrada, LADO_GUARDADO, LADO_GUARDADO, true);
            File pasta = new File(contexto.getApplicationContext().getCacheDir(), "capas");
            pasta.mkdirs();
            new File(pasta, nomeDoArquivo(uri) + ".nao").delete();
            try (FileOutputStream out = new FileOutputStream(new File(pasta, nomeDoArquivo(uri) + ".jpg"))) {
                return pronta.compress(Bitmap.CompressFormat.JPEG, 90, out);
            }
        } catch (Exception e) {
            return false;
        }
    }

    /** Ja esta na memoria? (devolve na hora, sem esperar) */
    public Bitmap naMemoria(String uri) {
        return memoria.get(uri);
    }

    public boolean sabeQueNaoTem(String uri) {
        return semCapa.contains(uri);
    }

    public void pedir(final Faixa f, final Interesse interesse, final Pronta pronta) {
        fila.execute(() -> {
            if (!interesse.ainda()) return;  // rolou a lista depressa: essa linha ja saiu da tela
            Bitmap b = memoria.get(f.uri);
            if (b == null && !semCapa.contains(f.uri)) b = ler(f);
            final Bitmap resultado = b;
            if (resultado == null) semCapa.add(f.uri); else memoria.put(f.uri, resultado);
            ui.post(() -> {
                if (interesse.ainda()) pronta.aqui(resultado);
            });
        });
    }

    private Bitmap ler(Faixa f) {
        File guardada = new File(pasta, nomeDoArquivo(f.uri) + ".jpg");
        File sem = new File(pasta, nomeDoArquivo(f.uri) + ".nao");
        if (sem.exists()) return null;
        if (guardada.exists()) {
            Bitmap b = BitmapFactory.decodeFile(guardada.getAbsolutePath());
            if (b != null) return b;
        }
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(contexto, Uri.parse(f.uri));
            byte[] dados = r.getEmbeddedPicture();
            if (dados == null || dados.length == 0) {
                try {
                    sem.createNewFile();
                } catch (Exception e) {
                    // sem cache: tenta de novo na proxima abertura
                }
                return null;
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(dados, 0, dados.length, o);
            int lado = Math.max(1, Math.min(o.outWidth, o.outHeight));
            o.inSampleSize = Math.max(1, lado / LADO);
            o.inJustDecodeBounds = false;
            Bitmap b = BitmapFactory.decodeByteArray(dados, 0, dados.length, o);
            if (b != null) {
                try (FileOutputStream out = new FileOutputStream(guardada)) {
                    b.compress(Bitmap.CompressFormat.JPEG, 85, out);
                } catch (Exception e) {
                    // so nao guardou
                }
            }
            return b;
        } catch (Exception e) {
            return null;  // pendrive fora, arquivo sem leitura...: tenta de novo depois
        } finally {
            try {
                r.release();
            } catch (Exception e) {
                // nada a fazer
            }
        }
    }
}
