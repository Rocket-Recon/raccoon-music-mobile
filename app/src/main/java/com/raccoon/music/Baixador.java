package com.raccoon.music;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

/** Baixa a musica no proprio celular (yt-dlp + ffmpeg embutidos) e grava na pasta de musicas escolhida. */
public final class Baixador {
    public static final class Resultado {
        public final boolean ok;
        public final String mensagem;

        Resultado(boolean ok, String mensagem) {
            this.ok = ok;
            this.mensagem = mensagem;
        }
    }

    private static boolean pronto;

    private Baixador() {
    }

    private static synchronized boolean iniciar(Context contexto) {
        if (pronto) return true;
        try {
            YoutubeDL.getInstance().init(contexto.getApplicationContext());
            FFmpeg.getInstance().init(contexto.getApplicationContext());
            pronto = true;
        } catch (Exception e) {
            pronto = false;
        }
        return pronto;
    }

    /** Bloqueia ate terminar: chamar fora da thread da tela. */
    public static Resultado baixar(Context contexto, String texto, String arvore) {
        String entrada = Pedido.entrada(texto);
        if (entrada.isEmpty()) return new Resultado(false, "Escreva um link ou o nome da música.");
        if (!iniciar(contexto)) return new Resultado(false, "O baixador não iniciou neste aparelho.");
        File temporaria = new File(contexto.getCacheDir(), "baixando");
        limpar(temporaria);
        if (!temporaria.mkdirs() && !temporaria.isDirectory()) return new Resultado(false, "Sem espaço para baixar.");
        try {
            YoutubeDLRequest pedido = new YoutubeDLRequest(entrada);
            pedido.addOption("--no-playlist");
            pedido.addOption("-x");
            pedido.addOption("--audio-format", "mp3");
            pedido.addOption("--audio-quality", "0");
            pedido.addOption("--embed-metadata");
            pedido.addOption("--windows-filenames");
            pedido.addOption("--trim-filenames", "150");
            pedido.addOption("-o", temporaria.getAbsolutePath() + "/%(title)s.%(ext)s");
            YoutubeDL.getInstance().execute(pedido);
            File mp3 = null;
            File[] arquivos = temporaria.listFiles();
            if (arquivos != null) {
                for (File f : arquivos) {
                    if (f.getName().toLowerCase().endsWith(".mp3") && (mp3 == null || f.length() > mp3.length())) mp3 = f;
                }
            }
            if (mp3 == null) return new Resultado(false, "Não consegui baixar essa música.");
            String nome = mp3.getName();
            if (!copiar(contexto.getContentResolver(), mp3, Uri.parse(arvore))) return new Resultado(false, "Baixei, mas não consegui salvar na pasta de músicas.");
            return new Resultado(true, nome.substring(0, nome.length() - 4));
        } catch (Exception e) {
            return new Resultado(false, "Não consegui baixar. Confira a internet e o link.");
        } finally {
            limpar(temporaria);
        }
    }

    private static boolean copiar(ContentResolver cr, File arquivo, Uri arvore) {
        try {
            Uri pai = DocumentsContract.buildDocumentUriUsingTree(arvore, DocumentsContract.getTreeDocumentId(arvore));
            Uri novo = DocumentsContract.createDocument(cr, pai, "audio/mpeg", arquivo.getName());
            if (novo == null) return false;
            try (InputStream in = new java.io.FileInputStream(arquivo); OutputStream out = cr.openOutputStream(novo)) {
                if (out == null) return false;
                byte[] bloco = new byte[65536];
                int n;
                while ((n = in.read(bloco)) > 0) out.write(bloco, 0, n);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void limpar(File pasta) {
        File[] itens = pasta.listFiles();
        if (itens != null) {
            for (File f : itens) {
                if (f.isDirectory()) limpar(f);
                f.delete();
            }
        }
        pasta.delete();
    }
}
