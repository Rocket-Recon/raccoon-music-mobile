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
import java.lang.reflect.Method;

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

    private static final long INTERVALO_DE_ATUALIZACAO_MS = 10 * 60 * 1000L;
    private static boolean pronto;
    private static long ultimaAtualizacao;

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

    /** Atualiza o yt-dlp embutido (o YouTube muda e versoes velhas param de funcionar). true se a chamada foi feita. */
    public static boolean atualizarAgora(Context contexto) {
        if (!iniciar(contexto)) return false;
        synchronized (Baixador.class) {
            ultimaAtualizacao = System.currentTimeMillis();
        }
        try {
            YoutubeDL ydl = YoutubeDL.getInstance();
            for (Method m : YoutubeDL.class.getMethods()) {
                if (!m.getName().equals("updateYoutubeDL")) continue;
                Class<?>[] tipos = m.getParameterTypes();
                if (tipos.length == 1) {
                    m.invoke(ydl, contexto.getApplicationContext());
                    return true;
                }
                if (tipos.length == 2) {
                    Object canal = canalEstavel(tipos[1]);
                    if (canal == null) continue;
                    m.invoke(ydl, contexto.getApplicationContext(), canal);
                    return true;
                }
            }
        } catch (Throwable e) {
            // sem atualizar: segue com o que esta embutido
        }
        return false;
    }

    private static synchronized boolean atualizarSeDeu(Context contexto) {
        if (System.currentTimeMillis() - ultimaAtualizacao < INTERVALO_DE_ATUALIZACAO_MS) return false;
        return atualizarAgora(contexto);
    }

    /** O canal "estavel" do yt-dlp, seja qual for a forma que a biblioteca o expoe. */
    private static Object canalEstavel(Class<?> tipo) {
        for (String nome : new String[]{"_STABLE", "STABLE"}) {
            try {
                return tipo.getField(nome).get(null);
            } catch (Throwable e) {
                // tenta a proxima forma
            }
        }
        try {
            return Class.forName(tipo.getName() + "$STABLE").getField("INSTANCE").get(null);
        } catch (Throwable e) {
            return null;
        }
    }

    /** Roda o yt-dlp; se falhar, atualiza o yt-dlp e tenta de novo uma vez. Devolve a saida de texto. */
    private static String executar(Context contexto, YoutubeDLRequest pedido) throws Exception {
        Object resposta;
        try {
            resposta = YoutubeDL.getInstance().execute(pedido);
        } catch (Exception primeira) {
            if (!atualizarSeDeu(contexto)) throw primeira;
            resposta = YoutubeDL.getInstance().execute(pedido);
        }
        try {
            Object saida = resposta.getClass().getMethod("getOut").invoke(resposta);
            return saida == null ? "" : saida.toString();
        } catch (Throwable e) {
            return "";
        }
    }

    /** Titulo do video para sugerir o nome do arquivo; "" se nao deu. Chamar fora da thread da tela. */
    public static String titulo(Context contexto, String texto) {
        String entrada = Pedido.entrada(texto);
        if (entrada.isEmpty() || !iniciar(contexto)) return "";
        try {
            YoutubeDLRequest pedido = new YoutubeDLRequest(entrada);
            pedido.addOption("--no-playlist");
            pedido.addOption("--skip-download");
            pedido.addOption("--print", "%(title)s");
            return Pedido.nomeDoArquivo(Pedido.ultimaLinha(executar(contexto, pedido)));
        } catch (Exception e) {
            return "";
        }
    }

    /** Bloqueia ate terminar: chamar fora da thread da tela. `nome` vazio usa o titulo do video. */
    public static Resultado baixar(Context contexto, String texto, String arvore, String nome, String formato, int taxa) {
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
            pedido.addOption("--audio-format", formato);
            pedido.addOption("--audio-quality", taxa + "K");
            pedido.addOption("--embed-metadata");
            pedido.addOption("--windows-filenames");
            pedido.addOption("--trim-filenames", String.valueOf(Pedido.MAX_NOME));
            pedido.addOption("-o", Pedido.modelo(temporaria.getAbsolutePath(), nome));
            executar(contexto, pedido);
            File pronto = null;
            File[] arquivos = temporaria.listFiles();
            if (arquivos != null) {
                for (File f : arquivos) {
                    if (f.getName().toLowerCase().endsWith("." + formato) && (pronto == null || f.length() > pronto.length())) pronto = f;
                }
            }
            if (pronto == null) return new Resultado(false, "Não consegui baixar essa música.");
            String arquivo = pronto.getName();
            if (!copiar(contexto.getContentResolver(), pronto, Uri.parse(arvore), Pedido.mime(formato))) {
                return new Resultado(false, "Baixei, mas não consegui salvar na pasta de músicas.");
            }
            return new Resultado(true, arquivo.substring(0, arquivo.length() - formato.length() - 1));
        } catch (Exception e) {
            return new Resultado(false, "Não consegui baixar: " + Pedido.resumoDoErro(e.getMessage()));
        } finally {
            limpar(temporaria);
        }
    }

    private static boolean copiar(ContentResolver cr, File arquivo, Uri arvore, String mime) {
        try {
            Uri pai = DocumentsContract.buildDocumentUriUsingTree(arvore, DocumentsContract.getTreeDocumentId(arvore));
            Uri novo = DocumentsContract.createDocument(cr, pai, mime, arquivo.getName());
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
