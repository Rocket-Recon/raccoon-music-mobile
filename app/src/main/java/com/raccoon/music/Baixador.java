package com.raccoon.music;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
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

    /** Dados do video, lidos uma vez só: o mesmo texto serve para sugerir o nome, buscar a capa oficial e baixar sem extrair de novo. */
    private static final class Info {
        String entrada = "";
        String json = "";
        /** {artista, faixa, canal, titulo}; "NA" quando o yt-dlp não sabe. */
        String[] d = {"NA", "NA", "NA", "NA"};
        String miniatura = "";
        long duracaoMs;
        long quando;
    }

    private static final java.util.Map<String, Info> infos = new java.util.concurrent.ConcurrentHashMap<>();

    private static String campo(JSONObject o, String chave) {
        if (o.isNull(chave)) return "NA";
        String v = o.optString(chave, "NA").trim();
        return v.isEmpty() ? "NA" : v;
    }

    private static Info obterInfo(Context contexto, String entrada) {
        long agora = System.currentTimeMillis();
        Info guardada = infos.get(entrada);
        if (guardada != null && agora - guardada.quando < 15 * 60 * 1000L && !guardada.json.isEmpty()) return guardada;
        Info info = new Info();
        info.entrada = entrada;
        info.quando = agora;
        try {
            YoutubeDLRequest pedido = new YoutubeDLRequest(entrada);
            pedido.addOption("--no-playlist");
            pedido.addOption("--skip-download");
            pedido.addOption("--dump-json");
            String saida = executar(contexto, pedido);
            String[] linhas = saida.split("\\r?\\n");
            for (int i = linhas.length - 1; i >= 0; i--) {
                String l = linhas[i].trim();
                if (!l.startsWith("{")) continue;
                JSONObject o = new JSONObject(l);
                info.json = l;
                info.d = new String[] {campo(o, "artist"), campo(o, "track"), campo(o, "uploader"), campo(o, "title")};
                info.miniatura = o.isNull("thumbnail") ? "" : o.optString("thumbnail", "");
                info.duracaoMs = Math.round(o.optDouble("duration", 0d) * 1000d);
                break;
            }
        } catch (Exception e) {
            info.json = "";
        }
        if (!info.json.isEmpty()) {
            if (infos.size() > 40) infos.clear();
            infos.put(entrada, info);
        }
        return info;
    }

    /** Uma música de uma playlist: o endereço e o título que o YouTube mostra. */
    public static final class Entrada {
        public final String url;
        public final String titulo;

        Entrada(String url, String titulo) {
            this.url = url;
            this.titulo = titulo;
        }
    }

    /** As músicas de um link de playlist (até 300), sem baixar nada; lista vazia se não deu. Chamar fora da thread da tela. */
    public static List<Entrada> expandirPlaylist(Context contexto, String link) {
        List<Entrada> r = new ArrayList<>();
        if (!iniciar(contexto)) return r;
        try {
            YoutubeDLRequest pedido = new YoutubeDLRequest(link);
            pedido.addOption("--flat-playlist");
            pedido.addOption("--dump-json");
            pedido.addOption("--playlist-end", "300");
            for (String linha : executar(contexto, pedido).split("\\r?\\n")) {
                String l = linha.trim();
                if (!l.startsWith("{")) continue;
                JSONObject o = new JSONObject(l);
                String url = o.isNull("url") ? "" : o.optString("url", "");
                if (url.isEmpty() && !o.isNull("id")) url = "https://www.youtube.com/watch?v=" + o.optString("id", "");
                if (url.isEmpty()) continue;
                String titulo = o.isNull("title") ? "" : o.optString("title", "");
                if (titulo.equals("[Private video]") || titulo.equals("[Deleted video]")) continue;
                r.add(new Entrada(url, titulo));
            }
        } catch (Exception e) {
            // sem a lista: quem chamou avisa
        }
        return r;
    }

    private static String artistaDe(String[] d) {
        String a = Limpeza.artistaDoCanal(d[0]);
        return a.isEmpty() ? Limpeza.artistaDoCanal(d[2]) : a;
    }

    /** Nome sugerido no padrao do PC ("Artista - Titulo", sem lixo); "" se nao deu. Chamar fora da thread da tela. */
    public static String titulo(Context contexto, String texto) {
        String entrada = Pedido.entrada(texto);
        if (entrada.isEmpty() || !iniciar(contexto)) return "";
        String[] d = obterInfo(contexto, entrada).d;
        return Limpeza.nomeSugerido(artistaDe(d), d[1], d[3]);
    }

    private static String aspas(String t) {
        return t.replace("\"", "").replace("\\", "");
    }

    private static String etiqueta(String chave, String valor) {
        return valor == null || valor.trim().isEmpty() ? "" : " -metadata " + chave + "=\"" + aspas(valor.trim()) + "\"";
    }

    private static void escrever(File arquivo, String texto) throws java.io.IOException {
        try (OutputStream out = new java.io.FileOutputStream(arquivo)) {
            out.write(texto.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /** Bloqueia ate terminar: chamar fora da thread da tela. `nome` vazio usa o nome limpo no padrao do PC. */
    public static Resultado baixar(Context contexto, String texto, String arvore, String nome, String formato, int taxa) {
        String entrada = Pedido.entrada(texto);
        if (entrada.isEmpty()) return new Resultado(false, "Escreva um link ou o nome da música.");
        if (!iniciar(contexto)) return new Resultado(false, "O baixador não iniciou neste aparelho.");
        File temporaria = new File(contexto.getCacheDir(), "baixando/" + System.nanoTime());  // uma pasta por download: dois podem rodar juntos
        limpar(temporaria);
        if (!temporaria.mkdirs() && !temporaria.isDirectory()) return new Resultado(false, "Sem espaço para baixar.");
        try {
            Info info = obterInfo(contexto, entrada);
            String[] d = info.d;
            String artista = artistaDe(d);
            String faixa = d[1].equals("NA") ? "" : d[1];
            String limpo = Limpeza.formatarTitulo(Limpeza.limparLixo(faixa.isEmpty() ? d[3] : faixa));
            CapaOficial.Oficial oficial = CapaOficial.buscar(artista, limpo, (artista + " " + d[2] + " " + d[3]));
            String artistaTag = oficial != null && !oficial.artista.isEmpty() ? oficial.artista : Limpeza.formatarArtista(artista);
            String tituloTag = oficial != null && !oficial.titulo.isEmpty() ? oficial.titulo : limpo;
            String nomeFinal;
            if (nome != null && !nome.trim().isEmpty()) nomeFinal = nome;
            else if (oficial != null && !oficial.titulo.isEmpty()) nomeFinal = Pedido.nomeDoArquivo(artistaTag.isEmpty() ? tituloTag : Limpeza.formatarArtista(artistaTag) + " - " + tituloTag);
            else nomeFinal = Limpeza.nomeSugerido(artista, d[1], d[3]);

            // a imagem é buscada em paralelo ao download: a oficial do álbum e, sem ela, a miniatura do vídeo
            final String enderecoDaCapa = oficial != null && !oficial.capa.isEmpty() ? oficial.capa : info.miniatura;
            final byte[][] capaBaixada = new byte[1][];
            Thread buscaDaCapa = new Thread(() -> capaBaixada[0] = CapaOficial.imagem(enderecoDaCapa));
            buscaDaCapa.start();

            File pronto = null;
            Exception erro = null;
            for (int tentativa = 0; tentativa < 2 && pronto == null; tentativa++) {
                limpar(temporaria);
                temporaria.mkdirs();
                try {
                    boolean reaproveita = tentativa == 0 && !info.json.isEmpty();
                    YoutubeDLRequest pedido = new YoutubeDLRequest(entrada);
                    if (reaproveita) {
                        // os dados já extraídos são reaproveitados (com a capa oficial no lugar da miniatura do vídeo)
                        JSONObject o = new JSONObject(info.json);
                        // data de envio, categoria ("Music"), tags e descrição do vídeo não são dados da música
                        for (String chave : new String[] {"upload_date", "release_date", "release_year", "release_timestamp", "timestamp", "categories", "genres", "genre", "tags", "description"}) {
                            o.remove(chave);
                        }
                        if (oficial != null && !oficial.capa.isEmpty()) {
                            o.put("thumbnail", oficial.capa);
                            o.put("thumbnails", new JSONArray().put(new JSONObject().put("url", oficial.capa).put("id", "0")));
                        }
                        File arquivoDeDados = new File(temporaria, "dados.info.json");
                        escrever(arquivoDeDados, o.toString());
                        pedido.addOption("--load-info-json", arquivoDeDados.getAbsolutePath());
                    } else {
                        pedido.addOption("--no-playlist");
                    }
                    pedido.addOption("-x");
                    pedido.addOption("--audio-format", formato);
                    pedido.addOption("--audio-quality", taxa + "K");
                    pedido.addOption("--embed-metadata");
                    pedido.addOption("--concurrent-fragments", "4");
                    pedido.addOption("--no-mtime");
                    String tags = etiqueta("title", tituloTag) + etiqueta("artist", artistaTag);
                    if (oficial == null) tags += " -metadata genre= -metadata date=";
                    if (oficial != null) tags += etiqueta("album", oficial.album) + etiqueta("album_artist", artistaTag) + etiqueta("genre", oficial.genero) + etiqueta("date", oficial.ano);
                    if (!tags.isEmpty()) pedido.addOption("--postprocessor-args", "ffmpeg:" + tags.trim());
                    if (reaproveita) {
                        // capa dentro do arquivo; se falhar, a segunda tentativa baixa sem embutir
                        pedido.addOption("--embed-thumbnail");
                        pedido.addOption("--convert-thumbnails", "jpg");
                        if (oficial == null) pedido.addOption("--ppa", "ThumbnailsConvertor+ffmpeg_o:-vf crop='min(iw,ih)':'min(iw,ih)'");
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
            try {
                buscaDaCapa.join(6000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] imagem = capaBaixada[0];
            Uri novo = copiar(contexto.getContentResolver(), pronto, Uri.parse(arvore), Pedido.mime(formato));
            if (novo == null) {
                return new Resultado(false, "Baixei, mas não consegui salvar na pasta de músicas.");
            }
            complementar(contexto, novo.toString(), arquivo, imagem, info.duracaoMs);
            return new Resultado(true, arquivo.substring(0, arquivo.length() - formato.length() - 1));
        } finally {
            limpar(temporaria);
        }
    }

    /** Depois de salvar: guarda a capa e já busca a letra, para a música nova aparecer completa. Nada aqui invalida o download. */
    private static void complementar(final Context contexto, String uri, String arquivo, byte[] imagem, final long duracaoMs) {
        try {
            Capas.guardar(contexto, uri, imagem);
        } catch (Throwable e) {
            // sem capa guardada: o app tenta ler a do arquivo
        }
        final Faixa nova = new Faixa(uri, arquivo);
        // a letra vem em segundo plano: o download termina sem esperar por ela (o player busca de novo se faltar)
        Thread letra = new Thread(() -> {
            try {
                Letras.buscar(contexto, nova, duracaoMs);
            } catch (Throwable e) {
                // sem letra agora
            }
        });
        letra.setDaemon(false);
        letra.start();
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
