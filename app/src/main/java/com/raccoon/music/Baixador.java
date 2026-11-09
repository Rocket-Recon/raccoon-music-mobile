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

    private static final String SEP = "|||";

    /** Dados do video {artista, faixa, canal, titulo}; vazios se nao deu. Chamar fora da thread da tela. */
    private static String[] dados(Context contexto, String entrada) {
        try {
            YoutubeDLRequest pedido = new YoutubeDLRequest(entrada);
            pedido.addOption("--no-playlist");
            pedido.addOption("--skip-download");
            pedido.addOption("--print", "%(artist)s" + SEP + "%(track)s" + SEP + "%(uploader)s" + SEP + "%(title)s");
            String linha = Pedido.ultimaLinha(executar(contexto, pedido));
            String[] p = linha.split(java.util.regex.Pattern.quote(SEP), -1);
            if (p.length == 4) return p;
        } catch (Exception e) {
            // segue sem os dados
        }
        return new String[] {"", "", "", ""};
    }

    private static String artistaDe(String[] d) {
        String a = Limpeza.artistaDoCanal(d[0]);
        return a.isEmpty() ? Limpeza.artistaDoCanal(d[2]) : a;
    }

    /** Nome sugerido no padrao do PC ("Artista - Titulo", sem lixo); "" se nao deu. Chamar fora da thread da tela. */
    public static String titulo(Context contexto, String texto) {
        String entrada = Pedido.entrada(texto);
        if (entrada.isEmpty() || !iniciar(contexto)) return "";
        String[] d = dados(contexto, entrada);
        return Limpeza.nomeSugerido(artistaDe(d), d[1], d[3]);
    }

    private static String aspas(String t) {
        return t.replace("\"", "").replace("\\", "");
    }

    /** Bloqueia ate terminar: chamar fora da thread da tela. `nome` vazio usa o nome limpo no padrao do PC. */
    public static Resultado baixar(Context contexto, String texto, String arvore, String nome, String formato, int taxa) {
        String entrada = Pedido.entrada(texto);
        if (entrada.isEmpty()) return new Resultado(false, "Escreva um link ou o nome da música.");
        if (!iniciar(contexto)) return new Resultado(false, "O baixador não iniciou neste aparelho.");
        File temporaria = new File(contexto.getCacheDir(), "baixando");
        limpar(temporaria);
        if (!temporaria.mkdirs() && !temporaria.isDirectory()) return new Resultado(false, "Sem espaço para baixar.");
        try {
            String[] d = dados(contexto, entrada);
            String artista = artistaDe(d);
            String faixa = d[1].equals("NA") ? "" : d[1];
            String limpo = Limpeza.formatarTitulo(Limpeza.limparLixo(faixa.isEmpty() ? d[3] : faixa));
            String nomeFinal = nome == null || nome.trim().isEmpty() ? Limpeza.nomeSugerido(artista, d[1], d[3]) : nome;
            File pronto = null;
            Exception erro = null;
            for (int tentativa = 0; tentativa < 2 && pronto == null; tentativa++) {
                limpar(temporaria);
                temporaria.mkdirs();
                try {
                    YoutubeDLRequest pedido = new YoutubeDLRequest(entrada);
                    pedido.addOption("--no-playlist");
                    pedido.addOption("-x");
                    pedido.addOption("--audio-format", formato);
                    pedido.addOption("--audio-quality", taxa + "K");
                    pedido.addOption("--embed-metadata");
                    if (!limpo.isEmpty()) {
                        String tags = "-metadata title=\"" + aspas(limpo) + "\"";
                        if (!artista.isEmpty()) tags += " -metadata artist=\"" + aspas(Limpeza.formatarArtista(artista)) + "\"";
                        pedido.addOption("--postprocessor-args", "ffmpeg:" + tags);
                    }
                    // a imagem fica também numa pasta à parte: se não entrar no arquivo, o app guarda a capa por conta própria
                    pedido.addOption("--write-thumbnail");
                    pedido.addOption("--convert-thumbnails", "jpg");
                    if (tentativa == 0) {
                        // capa quadrada no arquivo, como no PC; se falhar, tenta de novo sem embutir
                        pedido.addOption("--embed-thumbnail");
                        pedido.addOption("--ppa", "ThumbnailsConvertor+ffmpeg_o:-vf crop='min(iw,ih)':'min(iw,ih)'");
                    }
                    pedido.addOption("--windows-filenames");
                    pedido.addOption("--trim-filenames", String.valueOf(Pedido.MAX_NOME));
                    pedido.addOption("-o", Pedido.modelo(temporaria.getAbsolutePath(), nomeFinal));
                    executar(contexto, pedido);
                } catch (Exception e) {
                    erro = e;
                }
                File[] arquivos = temporaria.listFiles();
                if (arquivos != null) {
                    for (File f : arquivos) {
                        if (f.getName().toLowerCase().endsWith("." + formato) && (pronto == null || f.length() > pronto.length())) pronto = f;
                    }
                }
            }
            if (pronto == null) {
                if (erro != null) return new Resultado(false, "Não consegui baixar: " + Pedido.resumoDoErro(erro.getMessage()));
                return new Resultado(false, "Não consegui baixar essa música.");
            }
            String arquivo = pronto.getName();
            byte[] imagem = imagemBaixada(temporaria);
            Uri novo = copiar(contexto.getContentResolver(), pronto, Uri.parse(arvore), Pedido.mime(formato));
            if (novo == null) {
                return new Resultado(false, "Baixei, mas não consegui salvar na pasta de músicas.");
            }
            complementar(contexto, novo.toString(), arquivo, imagem);
            return new Resultado(true, arquivo.substring(0, arquivo.length() - formato.length() - 1));
        } finally {
            limpar(temporaria);
        }
    }

    /** A imagem de capa que o yt-dlp deixou ao lado do áudio (null quando não há). */
    private static byte[] imagemBaixada(File pasta) {
        File[] itens = pasta.listFiles();
        if (itens == null) return null;
        File melhor = null;
        for (File f : itens) {
            String n = f.getName().toLowerCase(java.util.Locale.ROOT);
            if ((n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")) && (melhor == null || f.length() > melhor.length())) melhor = f;
        }
        if (melhor == null) return null;
        try (InputStream in = new java.io.FileInputStream(melhor); java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] bloco = new byte[65536];
            int n;
            while ((n = in.read(bloco)) > 0) out.write(bloco, 0, n);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /** Depois de salvar: guarda a capa e já busca a letra, para a música nova aparecer completa. Nada aqui invalida o download. */
    private static void complementar(Context contexto, String uri, String arquivo, byte[] imagem) {
        try {
            Capas.guardar(contexto, uri, imagem);
        } catch (Throwable e) {
            // sem capa guardada: o app tenta ler a do arquivo
        }
        try {
            Letras.buscar(contexto, new Faixa(uri, arquivo));
        } catch (Throwable e) {
            // sem letra agora: o player busca quando for aberta
        }
    }

    private static Uri copiar(ContentResolver cr, File arquivo, Uri arvore, String mime) {
        try {
            Uri pai = DocumentsContract.buildDocumentUriUsingTree(arvore, DocumentsContract.getTreeDocumentId(arvore));
            Uri novo = DocumentsContract.createDocument(cr, pai, mime, arquivo.getName());
            if (novo == null) return null;
            try (InputStream in = new java.io.FileInputStream(arquivo); OutputStream out = cr.openOutputStream(novo)) {
                if (out == null) return null;
                byte[] bloco = new byte[65536];
                int n;
                while ((n = in.read(bloco)) > 0) out.write(bloco, 0, n);
            }
            return novo;
        } catch (Exception e) {
            return null;
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
