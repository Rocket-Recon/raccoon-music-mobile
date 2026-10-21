package com.raccoon.music;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ViewOutlineProvider;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** A tela unica do app: biblioteca com pesquisa em cima e o player embaixo. */
public class MainActivity extends Activity {
    private int ACENTO, FUNDO, CARTAO, LINHA, TEXTO, SUAVE, ICONE;
    private boolean temaClaro = false;
    private static final int PEDIDO_PASTA = 11, PEDIDO_NOTIFICACAO = 12, PEDIDO_EXPORTAR_TEXTO = 13, PEDIDO_EXPORTAR_PASTA = 14;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Random sorteio = new Random();
    private Preferencias prefs;

    private List<Faixa> todas = new ArrayList<>();
    private List<Faixa> visiveis = new ArrayList<>();
    private final Map<String, Faixa> porUri = new HashMap<>();
    private final Map<String, Integer> posicao = new HashMap<>();
    private Set<String> curtidas;
    private static final int SECAO_MUSICAS = 0, SECAO_ARTISTAS = 1, SECAO_ALBUNS = 2, SECAO_GENEROS = 3, SECAO_PLAYLISTS = 4,
            SECAO_CURTIDAS = 5, SECAO_RECENTES = 6, SECAO_MAIS = 7, SECAO_COLECAO = 8;
    private static final String[] NOMES_SECAO = {"Músicas", "Artistas", "Álbuns", "Gêneros", "Playlists", "Curtidas", "Tocadas recentemente", "Mais tocadas", "Lista"};
    private static final int SECOES_NO_MENU = 8;
    private int secao = SECAO_MUSICAS;
    private int secaoAnterior = SECAO_MUSICAS;
    private String colecaoTitulo = "";
    private List<Faixa> colecaoFaixas = new ArrayList<>();
    private String colecaoPlaylist = null;  // nome da playlist do celular que esta aberta (para poder tirar musicas dela)
    private View cortina, gaveta;
    private boolean gavetaAberta = false;
    private float toqueX = -1;
    private final TextView[] itensSecao = new TextView[SECOES_NO_MENU];
    private TextView itemTemporizador, itemVersao;
    private boolean pararNoFim;
    private List<Faixa> exportando = new ArrayList<>();
    private String exportandoNome = "", exportandoTipo = "";
    private boolean avisouAtualizacao = false;
    private boolean varrendo = false;
    private List<Grupo> grupos = new ArrayList<>();
    private Playlists minhas = new Playlists();
    private Etiquetas etiquetas = new Etiquetas();
    private Capas capas;
    private boolean indexando = false;
    private int indexadas = 0, aIndexar = 0;
    private boolean tocandoAberto = false, letraAberta = false;
    private String letraDe = "", capaAtualChave = "";
    private Lrc lrcAtual;
    private int[] inicioLinhasLrc = new int[0];
    private float letraToqueY;
    private int linhaLrc = -2;
    private volatile String uriAtual = "";
    private float velocidade = 1f;
    private int errosSeguidos = 0;
    private Historico historico = new Historico();
    private long tocadoMs = 0;
    private boolean contouEssa = false;
    private Sincronizacao sincronizacao = new Sincronizacao();
    private final Map<String, Faixa> porChave = new HashMap<>();
    private Runnable desligar;
    private int achadasAgora = 0;

    private MediaController player;
    private ListenableFuture<MediaController> futuro;
    private boolean arrastando = false;
    private byte[] ultimaArte;
    private long ultimoSalvo = 0;

    private TextView lblContagem, lblVazio, lblTitulo, lblArtista, lblPos, lblDur, lblSecao, lblTituloMini, lblArtistaMini, lblLetra, lblVelocidade;
    private LinearLayout mini;
    private ProgressBar barraMini;
    private ImageView capaMini;
    private IconeView notaMini, notaGrande, icPlayMini, icProximaMini, icMenu, icLetra;
    private FrameLayout celulaMais, celulaAleatorio;
    private TextView btnVazio, bOrdem, lblGaveta;
    private FrameLayout limparBusca;
    private View telaTocando;
    private ScrollView painelLetra;
    private EditText edtBusca;
    private ListView lista;
    private Adaptador adaptador;
    private ImageView capa;
    private SeekBar barra;
    private IconeView icCurtir, icAleatorio, icAnterior, icPlay, icProxima, icRepetir;

    /** Uma linha das listas de artistas, albuns, generos e playlists. */
    private static final class Grupo {
        final String titulo;
        final String origem;  // "meu" (playlist feita aqui), "pc" (playlist do PC) ou null
        final List<Faixa> faixas;
        String sub;

        Grupo(String titulo, String origem, List<Faixa> faixas) {
            this.titulo = titulo;
            this.origem = origem;
            this.faixas = faixas;
            int n = faixas.size();
            this.sub = (n == 1 ? "1 música" : n + " músicas") + ("pc".equals(origem) ? "  ·  do PC" : "");
        }
    }

    private boolean modoGrupos() {
        return secao == SECAO_ARTISTAS || secao == SECAO_ALBUNS || secao == SECAO_GENEROS || secao == SECAO_PLAYLISTS;
    }

    // ------------------------------------------------------------------ ciclo de vida
    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);
        prefs = new Preferencias(this);
        aplicarPaleta();
        historico = Historico.ler(prefs.historico());
        minhas = Playlists.ler(prefs.playlists());
        etiquetas = Etiquetas.ler(Arquivos.ler(this, "etiquetas.tsv"));
        capas = new Capas(this);
        curtidas = prefs.curtidas();
        setContentView(montarTela());

        sincronizacao = Sincronizacao.ler(Biblioteca.sincronizacaoGuardada(this));
        definirTodas(Biblioteca.carregar(this));
        if (prefs.arvore() == null) {
            escolherPasta();
        } else if (todas.isEmpty()) {
            atualizarBiblioteca();
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, PEDIDO_NOTIFICACAO);
        }

        if (estado == null && !avisouAtualizacao) {
            avisouAtualizacao = true;
            procurarAtualizacao(false);
        }

        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        futuro = new MediaController.Builder(this, token).buildAsync();
        futuro.addListener(() -> {
            try {
                player = futuro.get();
            } catch (Exception e) {
                Toast.makeText(this, "Não consegui ligar o player.", Toast.LENGTH_LONG).show();
                return;
            }
            ligarPlayer();
        }, command -> ui.post(command));
    }

    @Override
    protected void onStart() {
        super.onStart();
        ui.post(relogio);
    }

    @Override
    protected void onStop() {
        ui.removeCallbacks(relogio);
        salvarUltimo();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (futuro != null) MediaController.releaseFuture(futuro);  // so solta a conexao: a musica continua tocando
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int pedido, int resultado, Intent dados) {
        super.onActivityResult(pedido, resultado, dados);
        if (pedido == PEDIDO_PASTA && resultado == RESULT_OK && dados != null && dados.getData() != null) {
            Uri arvore = dados.getData();
            try {
                try {
                    getContentResolver().takePersistableUriPermission(arvore, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (Exception semEscrita) {
                    getContentResolver().takePersistableUriPermission(arvore, Intent.FLAG_GRANT_READ_URI_PERMISSION);  // pendrive protegido: so ler
                }
            } catch (Exception e) {
                // alguns seletores nao deixam guardar a permissao: vale so ate fechar o app
            }
            prefs.arvore(arvore.toString());
            atualizarBiblioteca();
        }
        if (pedido == PEDIDO_EXPORTAR_TEXTO && resultado == RESULT_OK && dados != null && dados.getData() != null) {
            gravarListaExportada(dados.getData());
        }
        if (pedido == PEDIDO_EXPORTAR_PASTA && resultado == RESULT_OK && dados != null && dados.getData() != null) {
            copiarParaPasta(dados.getData());
        }
    }

    // ------------------------------------------------------------------ biblioteca
    private void escolherPasta() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            startActivityForResult(i, PEDIDO_PASTA);
        } catch (Exception e) {
            Toast.makeText(this, "Este celular não abre o seletor de pastas.", Toast.LENGTH_LONG).show();
        }
    }

    private void atualizarBiblioteca() {
        final String arvore = prefs.arvore();
        if (arvore == null) {
            escolherPasta();
            return;
        }
        if (varrendo) return;
        varrendo = true;
        achadasAgora = 0;
        atualizarVazio();
        new Thread(() -> {
            final Biblioteca.Resultado resultado = Biblioteca.varrer(getContentResolver(), Uri.parse(arvore), n -> ui.post(() -> {
                achadasAgora = n;
                atualizarVazio();
            }));
            final List<Faixa> achadas = resultado.faixas;
            if (!achadas.isEmpty()) Biblioteca.guardar(this, achadas);
            if (resultado.sincronizacao != null) Biblioteca.guardarSincronizacao(this, resultado.sincronizacao);
            ui.post(() -> {
                varrendo = false;
                if (achadas.isEmpty()) {
                    Toast.makeText(this, "Não encontrei músicas nessa pasta. Confira se o pendrive está conectado.", Toast.LENGTH_LONG).show();
                } else {
                    definirTodas(achadas);
                }
                if (resultado.sincronizacao != null) importarDoPc(resultado.sincronizacao);
                atualizarVazio();
            });
        }).start();
    }

    private void definirTodas(List<Faixa> faixas) {
        todas = faixas;
        porUri.clear();
        porChave.clear();
        posicao.clear();
        for (int i = 0; i < faixas.size(); i++) {
            porUri.put(faixas.get(i).uri, faixas.get(i));
            if (!porChave.containsKey(faixas.get(i).chave)) porChave.put(faixas.get(i).chave, faixas.get(i));
            posicao.put(faixas.get(i).uri, i);
        }
        aplicarFiltro();
        atualizarCabecalho();
        if (lblGaveta != null) lblGaveta.setText(faixas.size() == 1 ? "1 música" : faixas.size() + " músicas");
        restaurar();
        atualizarPlayer();
        iniciarIndexacao();
    }

    private List<Faixa> faixasBase() {
        switch (secao) {
            case SECAO_COLECAO: return colecaoFaixas;
            case SECAO_RECENTES: return historico.recentes(todas, 300);
            case SECAO_MAIS: return historico.maisTocadas(todas, 300);
            default: return todas;
        }
    }

    private List<Faixa> faixasPorChaves(List<String> chaves) {
        List<Faixa> r = new ArrayList<>();
        for (String k : chaves) {
            Faixa f = porChave.get(k);
            if (f != null) r.add(f);
        }
        return r;
    }

    private List<Grupo> montarGrupos(String texto) {
        String busca = Faixa.normalizar(texto);
        List<Grupo> todos = new ArrayList<>();
        if (secao == SECAO_ARTISTAS) {
            Map<String, Grupo> por = new java.util.TreeMap<>();
            for (Faixa f : todas) {
                if (f.artista.isEmpty()) continue;
                String k = Faixa.normalizar(f.artista);
                Grupo g = por.get(k);
                if (g == null) {
                    g = new Grupo(f.artista, null, new ArrayList<Faixa>());
                    por.put(k, g);
                }
                g.faixas.add(f);
            }
            for (Grupo g : por.values()) {
                int n = g.faixas.size();
                g.sub = n == 1 ? "1 música" : n + " músicas";
                todos.add(g);
            }
        } else if (secao == SECAO_ALBUNS || secao == SECAO_GENEROS) {
            for (Map.Entry<String, List<Faixa>> e : etiquetas.agrupar(todas, secao == SECAO_GENEROS).entrySet()) {
                Grupo g = new Grupo(e.getKey(), null, e.getValue());
                if (secao == SECAO_ALBUNS && !g.faixas.get(0).artista.isEmpty()) g.sub = g.faixas.get(0).artista + "  ·  " + g.sub;
                todos.add(g);
            }
        } else {
            for (String nome : minhas.nomes()) todos.add(new Grupo(nome, "meu", faixasPorChaves(minhas.chaves(nome))));
            for (Map.Entry<String, List<String>> e : sincronizacao.playlists.entrySet()) {
                todos.add(new Grupo(e.getKey(), "pc", Sincronizacao.faixasDaPlaylist(e.getValue(), porChave)));
            }
        }
        if (busca.isEmpty()) return todos;
        List<Grupo> r = new ArrayList<>();
        for (Grupo g : todos) if (Faixa.normalizar(g.titulo).contains(busca)) r.add(g);
        return r;
    }

    private void aplicarFiltro() {
        String texto = edtBusca == null ? "" : edtBusca.getText().toString();
        if (modoGrupos()) {
            grupos = montarGrupos(texto);
            visiveis = new ArrayList<>();
        } else {
            grupos = new ArrayList<>();
            visiveis = Catalogo.filtrar(faixasBase(), texto, curtidas, secao == SECAO_CURTIDAS);
            if ((secao == SECAO_MUSICAS || secao == SECAO_CURTIDAS) && prefs.ordem() != 0) {
                visiveis = Catalogo.ordenarPor(visiveis, prefs.ordem(), f -> etiquetas.duracao(f.uri));
            }
        }
        if (adaptador != null) adaptador.notifyDataSetChanged();
        atualizarContagem();
        atualizarVazio();
    }

    private static String plural(int n, String um, String varios) {
        return n == 1 ? "1 " + um : n + " " + varios;
    }

    private void atualizarContagem() {
        if (lblContagem == null) return;
        String s;
        if (modoGrupos()) {
            int n = grupos.size();
            s = secao == SECAO_ARTISTAS ? plural(n, "artista", "artistas") : secao == SECAO_ALBUNS ? plural(n, "álbum", "álbuns")
                    : secao == SECAO_GENEROS ? plural(n, "gênero", "gêneros") : plural(n, "playlist", "playlists");
        } else {
            int total = faixasBase().size();
            s = visiveis.size() == total || secao == SECAO_CURTIDAS ? plural(visiveis.size(), "música", "músicas") : visiveis.size() + " de " + plural(total, "música", "músicas");
        }
        if (!modoGrupos() && !indexando && !visiveis.isEmpty()) {
            long soma = 0;
            for (Faixa f : visiveis) soma += etiquetas.duracao(f.uri);
            if (soma > 0) s += "  ·  " + Historico.duracao(soma);
        }
        if (indexando && aIndexar > 0) s += "  ·  lendo informações " + Math.min(indexadas, aIndexar) + "/" + aIndexar;
        if (varrendo) s += "  ·  procurando músicas…";
        lblContagem.setText(s);
    }

    private void atualizarVazio() {
        if (lblVazio == null) return;
        if (btnVazio != null) btnVazio.setVisibility(todas.isEmpty() && !varrendo ? View.VISIBLE : View.GONE);
        boolean vazia = modoGrupos() ? grupos.isEmpty() : visiveis.isEmpty();
        boolean semBusca = edtBusca == null || edtBusca.getText().length() == 0;
        if (varrendo && todas.isEmpty()) {
            lblVazio.setText(achadasAgora > 0 ? "Procurando músicas… " + achadasAgora : "Procurando músicas…");
            lblVazio.setVisibility(View.VISIBLE);
        } else if (todas.isEmpty()) {
            lblVazio.setText(prefs.arvore() == null ? "" : "Nenhuma música encontrada");
            lblVazio.setVisibility(View.VISIBLE);
        } else if (vazia) {
            String m = "Nada encontrado";
            if (semBusca) {
                if (secao == SECAO_CURTIDAS) m = "Você ainda não curtiu nenhuma música";
                else if (secao == SECAO_RECENTES || secao == SECAO_MAIS) m = "Você ainda não ouviu nenhuma música por aqui";
                else if (secao == SECAO_PLAYLISTS) m = "Nenhuma playlist ainda.\nToque em + para criar a primeira.";
                else if (secao == SECAO_COLECAO) m = "Esta lista está vazia";
                else if (secao == SECAO_ALBUNS || secao == SECAO_GENEROS) {
                    m = indexando ? "Lendo as informações das músicas…" : "Suas músicas não trazem essa informação";
                }
            }
            lblVazio.setText(m);
            lblVazio.setVisibility(View.VISIBLE);
        } else {
            lblVazio.setVisibility(View.GONE);
        }
    }

    /** Le album, genero e duracao de dentro dos arquivos, aos poucos e em segundo plano (so do que ainda nao foi lido). */
    private void iniciarIndexacao() {
        if (indexando) return;
        final List<Faixa> pendentes = new ArrayList<>();
        for (Faixa f : todas) if (!etiquetas.tem(f.uri)) pendentes.add(f);
        if (pendentes.isEmpty()) return;
        indexando = true;
        indexadas = 0;
        aIndexar = pendentes.size();
        final Etiquetas alvo = etiquetas;
        new Thread(() -> {
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            int feitas = 0;
            for (Faixa f : pendentes) {
                if (isDestroyed()) break;
                String album = "", genero = "", ano = "";
                long duracao = 0;
                try {
                    r.setDataSource(this, Uri.parse(f.uri));
                    album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                    genero = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
                    ano = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR);
                    String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    duracao = d == null ? 0 : Long.parseLong(d.trim());
                } catch (Exception e) {
                    // arquivo ilegivel: fica guardado vazio para nao tentar de novo toda vez
                }
                alvo.definir(f.uri, new Etiquetas.Info(album, genero, ano, duracao));
                feitas++;
                if (feitas % 40 == 0) {
                    final int n = feitas;
                    ui.post(() -> {
                        indexadas = n;
                        atualizarContagem();
                        if (adaptador != null) adaptador.notifyDataSetChanged();
                    });
                }
                if (feitas % 200 == 0) Arquivos.gravar(this, "etiquetas.tsv", alvo.serializar());
            }
            try {
                r.release();
            } catch (Exception e) {
                // nada a fazer
            }
            Arquivos.gravar(this, "etiquetas.tsv", alvo.serializar());
            ui.post(() -> {
                indexando = false;
                aplicarFiltro();
            });
        }).start();
    }

    // ------------------------------------------------------------------ player
    private void ligarPlayer() {
        player.setShuffleModeEnabled(false);  // o sorteio e feito aqui (assim "tocar a seguir" fica de fato a seguir)
        player.setRepeatMode(prefs.repetir());
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException erro) {
                // arquivo sumiu ou o pendrive foi desconectado: pula para a proxima (e desiste se vierem varios erros seguidos)
                errosSeguidos++;
                if (errosSeguidos > 5) {
                    Toast.makeText(MainActivity.this, "Não consegui tocar. O pendrive está conectado?", Toast.LENGTH_LONG).show();
                    return;
                }
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem();
                    player.prepare();
                    player.play();
                }
            }

            @Override
            public void onEvents(Player p, Player.Events eventos) {
                if (p.isPlaying()) errosSeguidos = 0;
                atualizarPlayer();
                if (eventos.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                    if (pararNoFim) {
                        pararNoFim = false;
                        p.pause();
                        if (itemTemporizador != null) itemTemporizador.setText("Temporizador");
                    }
                    tocadoMs = 0;
                    contouEssa = false;
                    salvarUltimo();
                }
            }
        });
        restaurar();
        atualizarPlayer();
        atualizarIcones();
    }

    private MediaItem item(Faixa f) {
        Uri uri = Uri.parse(f.uri);
        MediaMetadata.Builder m = new MediaMetadata.Builder().setTitle(f.titulo);
        if (!f.artista.isEmpty()) m.setArtist(f.artista);
        return new MediaItem.Builder()
                .setMediaId(f.uri)
                .setUri(uri)
                .setMediaMetadata(m.build())
                .setRequestMetadata(new MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
                .build();
    }

    private void tocarFila(List<Faixa> fila, int inicio) {
        if (player == null) {
            Toast.makeText(this, "Aguarde um instante…", Toast.LENGTH_SHORT).show();
            return;
        }
        List<MediaItem> itens = new ArrayList<>(fila.size());
        for (Faixa f : fila) itens.add(item(f));
        player.setMediaItems(itens, inicio, 0L);
        player.prepare();
        player.play();
    }

    private void tocarDaLista(int pos) {
        if (pos < 0 || pos >= visiveis.size()) return;
        if (prefs.embaralhar()) {
            tocarFila(Catalogo.embaralhar(visiveis, pos, sorteio), 0);
        } else {
            tocarFila(new ArrayList<>(visiveis), pos);
        }
    }

    private void menuOrdenar() {
        String[] nomes = {"Por banda", "Por título", "Mais longas primeiro", "Adicionadas recentemente"};
        new Construtor()
                .setTitle("Ordenar por")
                .setSingleChoiceItems(nomes, prefs.ordem(), (d, qual) -> {
                    prefs.ordem(qual);
                    d.dismiss();
                    aplicarFiltro();
                    if (lista != null) lista.setSelection(0);
                })
                .show();
    }

    /** Deslizar para os lados troca de musica; toque duplo curte; deslizar para baixo fecha o player grande. */
    private void gestos(View alvo, final boolean duploCurte, final boolean descerFecha, final boolean tomarToque) {
        final GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return tomarToque;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (!duploCurte) return false;
                float terco = e.getX() / Math.max(1, alvo.getWidth());
                if (player != null && player.getMediaItemCount() > 0 && terco < 0.33f) {
                    player.seekTo(Math.max(0, player.getCurrentPosition() - 10_000));
                } else if (player != null && player.getMediaItemCount() > 0 && terco > 0.67f) {
                    long fim = player.getDuration();
                    long novo = player.getCurrentPosition() + 10_000;
                    player.seekTo(fim > 0 ? Math.min(novo, Math.max(0, fim - 500)) : novo);
                } else {
                    curtirAtual();
                }
                return true;
            }

            @Override
            public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (a == null || b == null || player == null || player.getMediaItemCount() == 0) return false;
                float dx = b.getX() - a.getX();
                float dy = b.getY() - a.getY();
                if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > dp(70) && Math.abs(vx) > 600) {
                    if (dx < 0) player.seekToNext(); else player.seekToPrevious();
                    return true;
                }
                if (descerFecha && dy > dp(90) && Math.abs(dy) > Math.abs(dx) && Math.abs(vy) > 600) {
                    fecharTocando();
                    return true;
                }
                return false;
            }
        });
        alvo.setOnTouchListener((v, e) -> gd.onTouchEvent(e));
    }

    private void tocarTudo() {
        if (visiveis.isEmpty()) {
            Toast.makeText(this, "Não há músicas para tocar.", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.embaralhar(false);
        atualizarIcones();
        tocarFila(new ArrayList<>(visiveis), 0);
    }

    private void tocarAleatorio() {
        if (visiveis.isEmpty()) {
            Toast.makeText(this, "Não há músicas para tocar.", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.embaralhar(true);
        atualizarIcones();
        tocarFila(Catalogo.embaralhar(visiveis, -1, sorteio), 0);
    }

    /** Volta a tocar de onde parou (pausado), com a biblioteca toda na fila. */
    private void restaurar() {
        if (player == null || todas.isEmpty() || player.getMediaItemCount() > 0) return;
        String ultimo = prefs.ultimoUri();
        Integer indice = ultimo == null ? null : posicao.get(ultimo);
        if (indice == null) return;
        List<MediaItem> itens = new ArrayList<>();
        int inicio = indice;
        if (prefs.embaralhar()) {
            for (Faixa f : Catalogo.embaralhar(todas, indice, sorteio)) itens.add(item(f));
            inicio = 0;
        } else {
            for (Faixa f : todas) itens.add(item(f));
        }
        player.setMediaItems(itens, inicio, prefs.ultimaPosicao());
        player.prepare();
    }

    private void alternarAleatorio() {
        boolean novo = !prefs.embaralhar();
        prefs.embaralhar(novo);
        atualizarIcones();
        if (player == null) return;
        int n = player.getMediaItemCount();
        int atual = player.getCurrentMediaItemIndex();
        if (n == 0 || atual < 0 || atual + 1 >= n) return;
        List<String> restantes = new ArrayList<>();
        for (int i = atual + 1; i < n; i++) restantes.add(player.getMediaItemAt(i).mediaId);
        List<MediaItem> itens = new ArrayList<>();
        for (String id : Catalogo.reordenar(restantes, posicao, novo, sorteio)) {
            Faixa f = porUri.get(id);
            if (f != null) itens.add(item(f));
        }
        player.replaceMediaItems(atual + 1, n, itens);  // so o que ainda vai tocar muda; a musica de agora nao para
    }

    private void alternarRepetir() {
        int novo = prefs.repetir() == Player.REPEAT_MODE_OFF ? Player.REPEAT_MODE_ALL
                : prefs.repetir() == Player.REPEAT_MODE_ALL ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF;
        prefs.repetir(novo);
        if (player != null) player.setRepeatMode(novo);
        atualizarIcones();
    }

    private void tocarPausar() {
        if (player == null) return;
        if (player.getMediaItemCount() == 0) {
            if (!todas.isEmpty()) tocarFila(Catalogo.embaralhar(todas, -1, sorteio), 0);
            return;
        }
        if (player.getPlaybackState() == Player.STATE_ENDED) {
            player.seekToDefaultPosition(0);
            player.play();
        } else if (player.getPlayWhenReady()) {
            player.pause();
        } else {
            player.play();
        }
    }

    private Faixa faixaAtual() {
        if (player == null) return null;
        MediaItem mi = player.getCurrentMediaItem();
        return mi == null ? null : porUri.get(mi.mediaId);
    }

    private void curtirAtual() {
        Faixa f = faixaAtual();
        if (f != null) alternarCurtida(f);
    }

    private void alternarCurtida(Faixa f) {
        prefs.alternarCurtida(f.chave);
        curtidas = prefs.curtidas();
        if (secao == SECAO_CURTIDAS) aplicarFiltro(); else adaptador.notifyDataSetChanged();
        atualizarPlayer();
    }

    private void atualizarIcones() {
        icAleatorio.definir(IconeView.ALEATORIO, prefs.embaralhar() ? ACENTO : ICONE);
        int r = prefs.repetir();
        icRepetir.definir(r == Player.REPEAT_MODE_ONE ? IconeView.REPETIR_UM : IconeView.REPETIR, r == Player.REPEAT_MODE_OFF ? ICONE : ACENTO);
    }

    private void atualizarPlayer() {
        if (player == null) return;
        MediaItem mi = player.getCurrentMediaItem();
        boolean tocando = player.getPlayWhenReady() && player.getPlaybackState() != Player.STATE_ENDED && player.getPlaybackState() != Player.STATE_IDLE;
        icPlay.definir(tocando ? IconeView.PAUSE : IconeView.PLAY, 0xFFFFFFFF);
        icPlayMini.definir(tocando ? IconeView.PAUSE : IconeView.PLAY, TEXTO);
        if (mi == null) {
            lblTituloMini.setText("Nada tocando");
            lblArtistaMini.setText(todas.isEmpty() ? "Escolha a pasta das músicas" : "Toque em uma música ou no play");
            lblTitulo.setText("Nada tocando");
            lblArtista.setText("");
            uriAtual = "";
            icCurtir.setVisibility(View.INVISIBLE);
            atualizarCapas(null, null);
            return;
        }
        uriAtual = mi.mediaId;
        Faixa f = porUri.get(mi.mediaId);
        CharSequence titulo = f != null ? f.titulo : player.getMediaMetadata().title;
        CharSequence artista = f != null ? f.artista : player.getMediaMetadata().artist;
        lblTitulo.setText(titulo == null ? "" : titulo);
        Etiquetas.Info inf = f == null ? null : etiquetas.de(f.uri);
        String album = inf == null || inf.album == null ? "" : inf.album;
        String linhaArtista = artista == null ? "" : artista.toString();
        if (!album.isEmpty()) linhaArtista = linhaArtista.isEmpty() ? album : linhaArtista + "  ·  " + album;
        lblArtista.setText(linhaArtista);
        lblTituloMini.setText(titulo == null ? "" : titulo);
        lblArtistaMini.setText(artista == null ? "" : artista);
        lblArtistaMini.setVisibility(artista == null || artista.length() == 0 ? View.GONE : View.VISIBLE);
        if (f != null) {
            icCurtir.setVisibility(View.VISIBLE);
            icCurtir.definir(curtidas.contains(f.chave) ? IconeView.CORACAO_CHEIO : IconeView.CORACAO, curtidas.contains(f.chave) ? ACENTO : ICONE);
        }
        atualizarCapas(f, player.getMediaMetadata().artworkData);
        if (letraAberta && !mi.mediaId.equals(letraDe)) carregarLetraAtual();
        adaptador.notifyDataSetChanged();  // destaca a musica que toca na lista
    }

    /** O fundo do player grande ganha a cor da capa, de cima para baixo. */
    private void tingirTocando(Bitmap b) {
        if (telaTocando == null) return;
        int base = FUNDO;
        if (b != null) {
            try {
                Bitmap p = Bitmap.createScaledBitmap(b, 8, 8, true);
                long r = 0, g = 0, az = 0;
                for (int x = 0; x < 8; x++) {
                    for (int y = 0; y < 8; y++) {
                        int c = p.getPixel(x, y);
                        r += Color.red(c);
                        g += Color.green(c);
                        az += Color.blue(c);
                    }
                }
                base = Cores.misturar(FUNDO, Color.rgb((int) (r / 64), (int) (g / 64), (int) (az / 64)), 0.45);
            } catch (Exception e) {
                base = FUNDO;
            }
        }
        telaTocando.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{base, FUNDO}));
    }

    private void definirCapaAtual(Bitmap b) {
        tingirTocando(b);
        capa.setImageBitmap(b);
        capaMini.setImageBitmap(b);
        notaGrande.setVisibility(b == null ? View.VISIBLE : View.GONE);
        notaMini.setVisibility(b == null ? View.VISIBLE : View.GONE);
    }

    /** A capa da musica que toca: a que o player achou no arquivo ou, na falta dela, a das listas. */
    private void atualizarCapas(Faixa f, byte[] arte) {
        String chave = (f == null ? "" : f.uri) + (arte == null ? "-" : "+");
        if (chave.equals(capaAtualChave)) return;
        capaAtualChave = chave;
        Bitmap b = arte != null ? decodificar(arte) : (f != null ? capas.naMemoria(f.uri) : null);
        definirCapaAtual(b);
        if (b == null && f != null && arte == null && !capas.sabeQueNaoTem(f.uri)) {
            final String uri = f.uri;
            capas.pedir(f, () -> uri.equals(uriAtual), bm -> {
                if (bm != null) definirCapaAtual(bm);
            });
        }
    }

    private Bitmap decodificar(byte[] dados) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(dados, 0, dados.length, o);
            int lado = Math.max(1, Math.min(o.outWidth, o.outHeight));
            o.inSampleSize = Math.max(1, lado / 640);
            o.inJustDecodeBounds = false;
            return BitmapFactory.decodeByteArray(dados, 0, dados.length, o);
        } catch (Exception e) {
            return null;
        }
    }

    private void guardarHistorico() {
        if (historico.precisaGuardar()) {
            prefs.historico(historico.serializar());
            historico.guardado();
        }
    }

    private void salvarUltimo() {
        guardarHistorico();
        if (player == null) return;
        MediaItem mi = player.getCurrentMediaItem();
        if (mi != null) prefs.ultimo(mi.mediaId, Math.max(0, player.getCurrentPosition()));
    }

    private final Runnable relogio = new Runnable() {
        @Override
        public void run() {
            if (player != null) {
                long dur = player.getDuration();
                long pos = player.getCurrentPosition();
                if (dur > 0 && !arrastando) {
                    barra.setProgress((int) (1000L * Math.min(pos, dur) / dur));
                    lblPos.setText(formatar(pos));
                    lblDur.setText(formatar(dur));
                    barraMini.setProgress((int) (1000L * Math.min(pos, dur) / dur));
                } else if (dur <= 0 && !arrastando) {
                    barra.setProgress(0);
                    barraMini.setProgress(0);
                    lblPos.setText("0:00");
                    lblDur.setText("0:00");
                }
                if (letraAberta && lrcAtual != null && tocandoAberto) desenharLrc(pos);
                long agora = System.currentTimeMillis();
                if (player.isPlaying()) {
                    historico.somarTempo(500);
                    tocadoMs += 500;
                    Faixa atual = faixaAtual();
                    if (!contouEssa && atual != null && (tocadoMs >= 20_000 || (dur > 0 && dur < 40_000 && tocadoMs >= dur / 2))) {
                        contouEssa = true;
                        historico.registrar(atual.chave, agora);
                        if (secao == SECAO_RECENTES || secao == SECAO_MAIS) aplicarFiltro();
                    }
                }
                if (agora - ultimoSalvo > 5000) {
                    ultimoSalvo = agora;
                    salvarUltimo();
                }
            }
            ui.postDelayed(this, 500);
        }
    };

    private static String formatar(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    // ------------------------------------------------------------------ menus
    private void opcoesDaFaixa(final Faixa f) {
        final boolean curtida = curtidas.contains(f.chave);
        final List<String> nomes = new ArrayList<>();
        final List<Runnable> acoes = new ArrayList<>();
        nomes.add("Letra");
        acoes.add(() -> mostrarLetra(f));
        nomes.add("Mostrar na lista");
        acoes.add(() -> mostrarNaLista(f));
        nomes.add("Tocar a seguir");
        acoes.add(() -> enfileirar(f, true));
        nomes.add("Adicionar à fila");
        acoes.add(() -> enfileirar(f, false));
        nomes.add("Adicionar à playlist");
        acoes.add(() -> escolherPlaylist(f));
        if (secao == SECAO_COLECAO && colecaoPlaylist != null) {
            nomes.add("Subir na playlist");
            acoes.add(() -> moverNaPlaylist(f, -1));
            nomes.add("Descer na playlist");
            acoes.add(() -> moverNaPlaylist(f, 1));
            nomes.add("Remover desta playlist");
            acoes.add(() -> {
                minhas.remover(colecaoPlaylist, f.chave);
                salvarPlaylists();
                colecaoFaixas = faixasPorChaves(minhas.chaves(colecaoPlaylist));
                aplicarFiltro();
            });
        }
        nomes.add(curtida ? "Descurtir" : "Curtir");
        acoes.add(() -> alternarCurtida(f));
        nomes.add("Informações");
        acoes.add(() -> mostrarInformacoes(f));
        nomes.add("Compartilhar");
        acoes.add(() -> compartilhar(f));
        nomes.add("Renomear");
        acoes.add(() -> renomear(f));
        nomes.add("Excluir do pendrive");
        acoes.add(() -> excluir(f));
        new Construtor()
                .setTitle(f.titulo)
                .setItems(nomes.toArray(new String[0]), (d, qual) -> acoes.get(qual).run())
                .show();
    }

    private void enfileirar(Faixa f, boolean aSeguir) {
        if (player == null || player.getMediaItemCount() == 0) {
            List<Faixa> so = new ArrayList<>();
            so.add(f);
            tocarFila(so, 0);
        } else if (aSeguir) {
            player.addMediaItem(player.getCurrentMediaItemIndex() + 1, item(f));
            Toast.makeText(this, "Vai tocar a seguir", Toast.LENGTH_SHORT).show();
        } else {
            player.addMediaItem(item(f));
            Toast.makeText(this, "Adicionada à fila", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ playlists, fila e velocidade
    private void salvarPlaylists() {
        prefs.playlists(minhas.serializar());
    }

    private EditText campoDeTexto(String inicial) {
        EditText campo = new EditText(this);
        campo.setSingleLine(true);
        campo.setText(inicial);
        campo.setSelection(campo.getText().length());
        campo.setTextColor(temaClaro ? 0xFF18181B : 0xFFFFFFFF);
        return campo;
    }

    private FrameLayout caixaDoCampo(EditText campo) {
        FrameLayout caixa = new FrameLayout(this);
        caixa.setPadding(dp(22), dp(8), dp(22), 0);
        caixa.addView(campo);
        return caixa;
    }

    /** Cria uma playlist (e ja poe a musica nela, se veio de uma musica). */
    private void novaPlaylist(final Faixa adicionar) {
        final EditText campo = campoDeTexto("");
        campo.setHint("Nome da playlist");
        new Construtor()
                .setTitle("Nova playlist")
                .setView(caixaDoCampo(campo))
                .setPositiveButton("Criar", (d, q) -> {
                    String nome = Playlists.limpar(campo.getText().toString());
                    if (!minhas.criar(nome)) {
                        Toast.makeText(this, nome.isEmpty() ? "Escreva um nome." : "Já existe uma playlist com esse nome.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (adicionar != null) minhas.adicionar(nome, adicionar.chave);
                    salvarPlaylists();
                    aplicarFiltro();
                    Toast.makeText(this, adicionar != null ? "Adicionada a " + nome : "Playlist criada", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void escolherPlaylist(final Faixa f) {
        final List<String> nomes = minhas.nomes();
        String[] itens = new String[nomes.size() + 1];
        itens[0] = "+  Nova playlist";
        for (int i = 0; i < nomes.size(); i++) itens[i + 1] = nomes.get(i);
        new Construtor()
                .setTitle("Adicionar à playlist")
                .setItems(itens, (d, qual) -> {
                    if (qual == 0) {
                        novaPlaylist(f);
                        return;
                    }
                    String nome = nomes.get(qual - 1);
                    boolean entrou = minhas.adicionar(nome, f.chave);
                    salvarPlaylists();
                    if (secao == SECAO_PLAYLISTS) aplicarFiltro();
                    Toast.makeText(this, entrou ? "Adicionada a " + nome : "Já está em " + nome, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void opcoesDoGrupo(final Grupo g) {
        final List<String> nomes = new ArrayList<>();
        final List<Runnable> acoes = new ArrayList<>();
        nomes.add("Tocar");
        acoes.add(() -> tocarGrupo(g, false));
        nomes.add("Tocar embaralhado");
        acoes.add(() -> tocarGrupo(g, true));
        nomes.add("Exportar");
        acoes.add(() -> escolherFormatoDaExportacao(g.titulo, g.faixas));
        if ("meu".equals(g.origem)) {
            nomes.add("Renomear");
            acoes.add(() -> renomearPlaylist(g.titulo));
            nomes.add("Excluir");
            acoes.add(() -> excluirPlaylist(g.titulo));
        }
        new Construtor()
                .setTitle(g.titulo)
                .setItems(nomes.toArray(new String[0]), (d, qual) -> acoes.get(qual).run())
                .show();
    }

    private void tocarGrupo(Grupo g, boolean embaralhado) {
        if (g.faixas.isEmpty()) {
            Toast.makeText(this, "Não há músicas para tocar.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (embaralhado) {
            prefs.embaralhar(true);
            atualizarIcones();
            tocarFila(Catalogo.embaralhar(g.faixas, -1, sorteio), 0);
        } else {
            tocarFila(new ArrayList<>(g.faixas), 0);
        }
    }

    private void renomearPlaylist(final String antigo) {
        final EditText campo = campoDeTexto(antigo);
        new Construtor()
                .setTitle("Renomear playlist")
                .setView(caixaDoCampo(campo))
                .setPositiveButton("Salvar", (d, q) -> {
                    if (!minhas.renomear(antigo, campo.getText().toString())) {
                        Toast.makeText(this, "Esse nome não serve ou já existe.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    salvarPlaylists();
                    aplicarFiltro();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void excluirPlaylist(final String nome) {
        new Construtor()
                .setTitle("Excluir a playlist " + nome + "?")
                .setMessage("As músicas continuam no pendrive.")
                .setPositiveButton("Excluir", (d, q) -> {
                    minhas.excluir(nome);
                    salvarPlaylists();
                    aplicarFiltro();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void mostrarFila() {
        if (player == null || player.getMediaItemCount() == 0) return;
        final ListView lv = new ListView(this);
        lv.setDivider(null);
        final BaseAdapter ad = new BaseAdapter() {
            @Override public int getCount() { return player == null ? 0 : Math.min(player.getMediaItemCount(), 1000); }
            @Override public Object getItem(int i) { return i; }
            @Override public long getItemId(int i) { return i; }

            @Override
            public View getView(final int i, View v, ViewGroup pai) {
                TextView[] textos;
                FrameLayout[] celula;
                if (v == null) {
                    LinearLayout linha = new LinearLayout(MainActivity.this);
                    linha.setGravity(Gravity.CENTER_VERTICAL);
                    linha.setPadding(dp(20), dp(6), dp(4), dp(6));
                    LinearLayout col = new LinearLayout(MainActivity.this);
                    col.setOrientation(LinearLayout.VERTICAL);
                    TextView t1 = texto(15, TEXTO, false);
                    TextView t2 = texto(12, SUAVE, false);
                    col.addView(t1);
                    col.addView(t2);
                    linha.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    FrameLayout x = new FrameLayout(MainActivity.this);
                    IconeView ic = new IconeView(MainActivity.this, IconeView.FECHAR);
                    ic.definir(IconeView.FECHAR, SUAVE);
                    x.addView(ic, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
                    linha.addView(x, new LinearLayout.LayoutParams(dp(44), dp(44)));
                    linha.setTag(new Object[]{t1, t2, x});
                    v = linha;
                }
                Object[] partes = (Object[]) v.getTag();
                textos = new TextView[]{(TextView) partes[0], (TextView) partes[1]};
                celula = new FrameLayout[]{(FrameLayout) partes[2]};
                MediaItem m = player.getMediaItemAt(i);
                Faixa f = porUri.get(m.mediaId);
                CharSequence titulo = f != null ? f.titulo : m.mediaMetadata.title;
                textos[0].setText(titulo == null ? "" : titulo);
                textos[0].setTextColor(i == player.getCurrentMediaItemIndex() ? ACENTO : TEXTO);
                String artista = f != null ? f.artista : "";
                textos[1].setText(artista);
                textos[1].setVisibility(artista.isEmpty() ? View.GONE : View.VISIBLE);
                celula[0].setOnClickListener(x -> {
                    if (player != null && i < player.getMediaItemCount()) player.removeMediaItem(i);
                    notifyDataSetChanged();
                });
                return v;
            }
        };
        lv.setAdapter(ad);
        lv.setOnItemClickListener((p, v, pos, id) -> {
            if (player != null && pos < player.getMediaItemCount()) {
                player.seekTo(pos, 0L);
                player.play();
                ad.notifyDataSetChanged();
            }
        });
        lv.setSelection(Math.max(0, player.getCurrentMediaItemIndex() - 2));
        new Construtor().setTitle("Fila de reprodução").setView(lv).setPositiveButton("Fechar", null)
                .setNeutralButton("Salvar como playlist", (d, q) -> salvarFilaComoPlaylist()).show();
    }

    private void moverNaPlaylist(Faixa f, int delta) {
        if (colecaoPlaylist == null || !minhas.mover(colecaoPlaylist, f.chave, delta)) {
            Toast.makeText(this, delta < 0 ? "Já é a primeira da playlist." : "Já é a última da playlist.", Toast.LENGTH_SHORT).show();
            return;
        }
        salvarPlaylists();
        colecaoFaixas = faixasPorChaves(minhas.chaves(colecaoPlaylist));
        aplicarFiltro();
    }

    /** Leva a lista ate a musica (usado pelo player grande e pelo menu da musica). */
    private void mostrarNaLista(final Faixa f) {
        fecharTocando();
        if (secao != SECAO_MUSICAS) irPara(SECAO_MUSICAS); else edtBusca.setText("");
        lista.post(() -> {
            for (int i = 0; i < visiveis.size(); i++) {
                if (visiveis.get(i).uri.equals(f.uri)) {
                    lista.setSelection(Math.max(0, i - 2));
                    return;
                }
            }
        });
    }

    // ------------------------------------------------------------------ exportar
    /** Primeiro passo: de onde vem as musicas (todas, curtidas ou uma playlist). */
    private void escolherOrigemDaExportacao() {
        if (todas.isEmpty()) {
            Toast.makeText(this, "Não há músicas para exportar.", Toast.LENGTH_SHORT).show();
            return;
        }
        final List<String> nomes = new ArrayList<>();
        final List<String> titulos = new ArrayList<>();
        final List<List<Faixa>> listas = new ArrayList<>();
        nomes.add("Todas as músicas  ·  " + todas.size());
        titulos.add("Todas as músicas");
        listas.add(new ArrayList<>(todas));
        List<Faixa> curtidasLista = new ArrayList<>();
        for (Faixa f : todas) if (curtidas.contains(f.chave)) curtidasLista.add(f);
        nomes.add("Curtidas  ·  " + curtidasLista.size());
        titulos.add("Curtidas");
        listas.add(curtidasLista);
        for (String nome : minhas.nomes()) {
            List<Faixa> lista = faixasPorChaves(minhas.chaves(nome));
            nomes.add("Playlist " + nome + "  ·  " + lista.size());
            titulos.add(nome);
            listas.add(lista);
        }
        new Construtor()
                .setTitle("Exportar músicas de")
                .setItems(nomes.toArray(new String[0]), (d, qual) -> escolherFormatoDaExportacao(titulos.get(qual), listas.get(qual)))
                .show();
    }

    /** Segundo passo: como exportar (copiar os arquivos, lista de nomes ou playlist .m3u). */
    private void escolherFormatoDaExportacao(final String nome, final List<Faixa> lista) {
        if (lista.isEmpty()) {
            Toast.makeText(this, "Nenhuma música para exportar.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] formas = {"Copiar os arquivos para uma pasta", "Lista de nomes (.txt)", "Playlist (.m3u)"};
        new Construtor()
                .setTitle(nome + "  ·  " + plural(lista.size(), "música", "músicas"))
                .setItems(formas, (d, qual) -> {
                    exportando = new ArrayList<>(lista);
                    exportandoNome = nome;
                    if (qual == 0) {
                        exportandoTipo = "pasta";
                        try {
                            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), PEDIDO_EXPORTAR_PASTA);
                        } catch (Exception e) {
                            Toast.makeText(this, "Este celular não abre o seletor de pastas.", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        exportandoTipo = qual == 1 ? "txt" : "m3u";
                        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType(qual == 1 ? "text/plain" : "audio/x-mpegurl");
                        i.putExtra(Intent.EXTRA_TITLE, Exportar.nomeSeguro(nome) + (qual == 1 ? ".txt" : ".m3u"));
                        try {
                            startActivityForResult(i, PEDIDO_EXPORTAR_TEXTO);
                        } catch (Exception e) {
                            Toast.makeText(this, "Este celular não consegue salvar arquivos aqui.", Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .show();
    }

    private void gravarListaExportada(final Uri destino) {
        final List<Faixa> lista = new ArrayList<>(exportando);
        final boolean m3u = "m3u".equals(exportandoTipo);
        new Thread(() -> {
            boolean gravou = false;
            try (java.io.OutputStream out = getContentResolver().openOutputStream(destino, "wt")) {
                if (out != null) {
                    out.write((m3u ? Exportar.m3u(lista) : Exportar.lista(lista)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    gravou = true;
                }
            } catch (Exception e) {
                gravou = false;
            }
            final boolean deu = gravou;
            ui.post(() -> Toast.makeText(this, deu ? (m3u ? "Playlist exportada" : "Lista exportada") + " com " + plural(lista.size(), "música", "músicas")
                    : "Não consegui gravar o arquivo.", Toast.LENGTH_LONG).show());
        }).start();
    }

    private void copiarParaPasta(final Uri arvore) {
        final List<Faixa> lista = new ArrayList<>(exportando);
        final boolean[] cancelado = {false};
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(24), dp(12), dp(24), dp(8));
        final TextView andamento = texto(14, TEXTO, false);
        andamento.setText("Preparando…");
        final TextView atual = texto(12, SUAVE, false);
        final ProgressBar barraCopia = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        barraCopia.setMax(lista.size());
        barraCopia.setProgressTintList(ColorStateList.valueOf(ACENTO));
        barraCopia.setProgressBackgroundTintList(ColorStateList.valueOf(LINHA));
        col.addView(andamento);
        LinearLayout.LayoutParams pbc = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        pbc.setMargins(0, dp(12), 0, dp(8));
        col.addView(barraCopia, pbc);
        col.addView(atual);
        final AlertDialog dialogo = new Construtor()
                .setTitle("Exportando")
                .setView(col)
                .setCancelable(false)
                .setNegativeButton("Cancelar", (d, q) -> cancelado[0] = true)
                .show();
        new Thread(() -> {
            final android.content.ContentResolver cr = getContentResolver();
            int copiadas = 0;
            String nomePasta = "";
            String erro = null;
            try {
                Uri pai = android.provider.DocumentsContract.buildDocumentUriUsingTree(arvore, android.provider.DocumentsContract.getTreeDocumentId(arvore));
                nomePasta = Exportar.nomeDaPasta(lista.size(), System.currentTimeMillis());
                Uri pasta = android.provider.DocumentsContract.createDocument(cr, pai, android.provider.DocumentsContract.Document.MIME_TYPE_DIR, nomePasta);
                if (pasta == null) throw new IllegalStateException("sem pasta");
                for (int i = 0; i < lista.size() && !cancelado[0]; i++) {
                    final Faixa f = lista.get(i);
                    final int numero = i + 1;
                    ui.post(() -> {
                        andamento.setText(numero + " de " + lista.size());
                        atual.setText(f.nome);
                        barraCopia.setProgress(numero - 1);
                    });
                    try {
                        Uri origem = Uri.parse(f.uri);
                        String mime = cr.getType(origem);
                        if (mime == null) mime = "audio/mpeg";
                        Uri novo = android.provider.DocumentsContract.createDocument(cr, pasta, mime, f.nome);
                        if (novo == null) continue;
                        try (java.io.InputStream in = cr.openInputStream(origem); java.io.OutputStream out = cr.openOutputStream(novo)) {
                            if (in == null || out == null) continue;
                            byte[] bloco = new byte[65536];
                            int n;
                            while ((n = in.read(bloco)) > 0) out.write(bloco, 0, n);
                        }
                        copiadas++;
                    } catch (Exception umaFalhou) {
                        // uma musica que nao copia nao derruba as outras
                    }
                }
            } catch (Exception e) {
                erro = "Não consegui criar a pasta nesse local. Escolha outra pasta.";
            }
            final int feitas = copiadas;
            final String pastaFinal = nomePasta;
            final String erroFinal = erro;
            ui.post(() -> {
                try {
                    dialogo.dismiss();
                } catch (Exception e) {
                    // a tela ja fechou
                }
                if (isFinishing()) return;
                new Construtor()
                        .setTitle(erroFinal != null ? "Não deu para exportar" : (cancelado[0] ? "Exportação cancelada" : "Exportação concluída"))
                        .setMessage(erroFinal != null ? erroFinal : feitas + " de " + lista.size() + " músicas copiadas\n" + pastaFinal)
                        .setPositiveButton("Fechar", null)
                        .show();
            });
        }).start();
    }

    private void salvarFilaComoPlaylist() {
        if (player == null) return;
        final List<String> chaves = new ArrayList<>();
        for (int i = 0; i < player.getMediaItemCount(); i++) {
            Faixa f = porUri.get(player.getMediaItemAt(i).mediaId);
            if (f != null) chaves.add(f.chave);
        }
        if (chaves.isEmpty()) return;
        final EditText campo = campoDeTexto("");
        campo.setHint("Nome da playlist");
        new Construtor()
                .setTitle("Salvar a fila")
                .setView(caixaDoCampo(campo))
                .setPositiveButton("Salvar", (d, q) -> {
                    String nome = Playlists.limpar(campo.getText().toString());
                    if (!minhas.criar(nome)) {
                        Toast.makeText(this, nome.isEmpty() ? "Escreva um nome." : "Já existe uma playlist com esse nome.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    for (String c : chaves) minhas.adicionar(nome, c);
                    salvarPlaylists();
                    aplicarFiltro();
                    Toast.makeText(this, "Playlist criada com " + plural(chaves.size(), "música", "músicas"), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void compartilhar(Faixa f) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("audio/*");
            i.putExtra(Intent.EXTRA_STREAM, Uri.parse(f.uri));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Compartilhar música"));
        } catch (Exception e) {
            Toast.makeText(this, "Não consegui compartilhar esta música.", Toast.LENGTH_SHORT).show();
        }
    }

    private void linhaDeInfo(LinearLayout pai, String rotulo, String valor) {
        if (valor == null || valor.isEmpty()) return;
        TextView r = texto(12, SUAVE, false);
        r.setText(rotulo);
        TextView v = texto(15, TEXTO, false);
        v.setSingleLine(false);
        v.setText(valor);
        v.setPadding(0, 0, 0, dp(10));
        pai.addView(r);
        pai.addView(v);
    }

    private void mostrarInformacoes(Faixa f) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(22), dp(8), dp(22), dp(8));
        Etiquetas.Info inf = etiquetas.de(f.uri);
        int vezes = historico.vezes(f.chave);
        linhaDeInfo(col, "Arquivo", f.nome);
        linhaDeInfo(col, "Artista", f.artista);
        linhaDeInfo(col, "Título", f.titulo);
        if (inf != null) {
            linhaDeInfo(col, "Álbum", inf.album);
            linhaDeInfo(col, "Gênero", Etiquetas.generoLegivel(inf.genero == null ? "" : inf.genero));
            linhaDeInfo(col, "Ano", inf.ano);
            linhaDeInfo(col, "Duração", inf.duracaoMs > 0 ? formatar(inf.duracaoMs) : "");
        }
        linhaDeInfo(col, "Reproduções", vezes == 0 ? "Ainda não tocou" : plural(vezes, "vez", "vezes"));
        ScrollView rolagem = new ScrollView(this);
        rolagem.addView(col);
        new Construtor().setTitle("Informações").setView(rolagem).setPositiveButton("Fechar", null).show();
    }

    private void menuVelocidade() {
        final float[] valores = {0.75f, 1f, 1.25f, 1.5f, 2f};
        final String[] nomes = {"0,75x", "1x (normal)", "1,25x", "1,5x", "2x"};
        new Construtor()
                .setTitle("Velocidade")
                .setItems(nomes, (d, qual) -> {
                    velocidade = valores[qual];
                    if (player != null) player.setPlaybackParameters(new PlaybackParameters(velocidade));
                    lblVelocidade.setText(valores[qual] == 1f ? "1x" : nomes[qual]);
                })
                .show();
    }

    private void alternarLetra() {
        letraAberta = !letraAberta;
        painelLetra.setVisibility(letraAberta ? View.VISIBLE : View.GONE);
        icLetra.definir(IconeView.LETRA, letraAberta ? ACENTO : ICONE);
        telaTocando.setKeepScreenOn(letraAberta);  // lendo a letra, a tela nao apaga
        if (letraAberta) carregarLetraAtual();
    }

    private void carregarLetraAtual() {
        final Faixa f = faixaAtual();
        if (f == null) return;
        letraDe = f.uri;
        painelLetra.scrollTo(0, 0);
        lrcAtual = null;
        linhaLrc = -2;
        String guardada = Letras.guardada(this, f);
        if (guardada != null) {
            exibirLetra(guardada, Letras.sincronizada(this, f));
            if (!Letras.jaTentouSincronizada(this, f)) {
                // letra guardada de antes, sem a versao com tempo: tenta uma vez buscar essa versao
                new Thread(() -> {
                    Letras.buscar(this, f);
                    final String sinc = Letras.sincronizada(this, f);
                    if (sinc == null) return;
                    ui.post(() -> {
                        if (letraAberta && f.uri.equals(uriAtual)) exibirLetra(Letras.guardada(this, f), sinc);
                    });
                }).start();
            }
            return;
        }
        lblLetra.setTextColor(TEXTO);
        lblLetra.setText("Buscando a letra…");
        new Thread(() -> {
            final String letra = Letras.buscar(this, f);
            final String sinc = Letras.sincronizada(this, f);
            ui.post(() -> {
                if (!letraAberta || !f.uri.equals(uriAtual)) return;
                if (letra == null && sinc == null) {
                    lblLetra.setTextColor(TEXTO);
                    lblLetra.setText("Não achei a letra dessa música.");
                } else {
                    exibirLetra(letra, sinc);
                }
            });
        }).start();
    }

    /** Mostra a letra: com tempo (a linha cantada fica em destaque e a tela acompanha) ou so o texto. */
    private void exibirLetra(String simples, String sinc) {
        Lrc l = sinc == null ? null : Lrc.ler(sinc);
        lrcAtual = l;
        linhaLrc = -2;
        if (l != null) {
            desenharLrc(player == null ? 0L : player.getCurrentPosition());
        } else {
            lblLetra.setTextColor(TEXTO);
            lblLetra.setText(simples == null ? "" : simples);
        }
    }

    /** Tocar numa linha da letra com tempo leva a musica ate aquele trecho. */
    private void irParaLinhaDaLetra() {
        Lrc l = lrcAtual;
        android.text.Layout lay = lblLetra.getLayout();
        if (l == null || lay == null || player == null || inicioLinhasLrc.length != l.quantas() + 1) return;
        int linha = lay.getLineForVertical(Math.max(0, Math.round(letraToqueY) - lblLetra.getPaddingTop()));
        int deslocamento = lay.getLineStart(linha);
        for (int i = 0; i < l.quantas(); i++) {
            if (deslocamento >= inicioLinhasLrc[i] && deslocamento < inicioLinhasLrc[i + 1]) {
                player.seekTo(l.tempos[i]);
                linhaLrc = -2;
                desenharLrc(l.tempos[i]);
                return;
            }
        }
    }

    private void desenharLrc(long posicaoMs) {
        final Lrc l = lrcAtual;
        if (l == null) return;
        final int idx = l.indiceEm(posicaoMs + 250);
        if (idx == linhaLrc) return;
        linhaLrc = idx;
        StringBuilder sb = new StringBuilder();
        final int[] ini = new int[l.quantas() + 1];
        for (int i = 0; i < l.quantas(); i++) {
            ini[i] = sb.length();
            sb.append(l.linhas[i].isEmpty() ? " " : l.linhas[i]).append('\n');
        }
        ini[l.quantas()] = sb.length();
        inicioLinhasLrc = ini;
        SpannableString sp = new SpannableString(sb.toString());
        if (idx >= 0) {
            sp.setSpan(new ForegroundColorSpan(ACENTO), ini[idx], ini[idx + 1] - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sp.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), ini[idx], ini[idx + 1] - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sp.setSpan(new RelativeSizeSpan(1.18f), ini[idx], ini[idx + 1] - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        lblLetra.setTextColor(SUAVE);
        lblLetra.setText(sp);
        lblLetra.post(() -> {
            android.text.Layout lay = lblLetra.getLayout();
            if (lay == null || idx < 0) return;
            int linha = lay.getLineForOffset(Math.min(ini[idx], Math.max(0, lblLetra.length() - 1)));
            painelLetra.smoothScrollTo(0, Math.max(0, lay.getLineTop(linha) - painelLetra.getHeight() / 3));
        });
    }

    private void mostrarLetra(final Faixa f) {
        String guardada = Letras.guardada(this, f);
        if (guardada != null) {
            dialogoDaLetra(f, guardada);
            return;
        }
        Toast.makeText(this, "Buscando a letra…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final String letra = Letras.buscar(this, f);
            ui.post(() -> {
                if (isFinishing()) return;
                if (letra == null) {
                    Toast.makeText(this, "Não achei a letra dessa música.", Toast.LENGTH_LONG).show();
                } else {
                    dialogoDaLetra(f, letra);
                }
            });
        }).start();
    }

    private void dialogoDaLetra(Faixa f, String letra) {
        ScrollView rolagem = new ScrollView(this);
        TextView t = new TextView(this);
        t.setText(letra);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTextColor(temaClaro ? 0xFF18181B : 0xFFFFFFFF);
        t.setLineSpacing(0, 1.15f);
        t.setPadding(dp(22), dp(8), dp(22), dp(16));
        t.setTextIsSelectable(true);
        rolagem.addView(t);
        new Construtor()
                .setTitle(f.artista.isEmpty() ? f.titulo : f.artista + " – " + f.titulo)
                .setView(rolagem)
                .setPositiveButton("Fechar", null)
                .setNeutralButton("Copiar", (dlg, q) -> {
                    android.content.ClipboardManager area = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (area != null) {
                        area.setPrimaryClip(android.content.ClipData.newPlainText("Letra", letra));
                        Toast.makeText(this, "Letra copiada", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private LinearLayout cartaoDeNumero(String numero, String rotulo) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(forma(FUNDO, 14, LINHA));
        c.setPadding(dp(14), dp(10), dp(14), dp(10));
        TextView n = texto(24, ACENTO, true);
        n.setText(numero);
        TextView r = texto(12, SUAVE, false);
        r.setText(rotulo);
        c.addView(n);
        c.addView(r);
        return c;
    }

    private void linhaDeCartoes(LinearLayout pai, LinearLayout a, LinearLayout b) {
        LinearLayout l = new LinearLayout(this);
        LinearLayout.LayoutParams pa = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        pa.setMargins(0, 0, dp(5), 0);
        LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        pb.setMargins(dp(5), 0, 0, 0);
        l.addView(a, pa);
        l.addView(b, pb);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pl.setMargins(0, 0, 0, dp(10));
        pai.addView(l, pl);
    }

    private void linhaDeRanking(LinearLayout pai, int posicao, String nome, String detalhe) {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(0, dp(5), 0, dp(5));
        TextView p = texto(14, ACENTO, true);
        p.setText(String.valueOf(posicao));
        l.addView(p, new LinearLayout.LayoutParams(dp(22), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView n = texto(15, TEXTO, false);
        n.setText(nome);
        l.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView d = texto(13, SUAVE, false);
        d.setText(detalhe);
        d.setPadding(dp(10), 0, 0, 0);
        l.addView(d);
        pai.addView(l);
    }

    private void tituloDeLista(LinearLayout pai, String rotulo) {
        TextView t = texto(13, SUAVE, true);
        t.setText(rotulo.toUpperCase());
        t.setPadding(0, dp(8), 0, dp(4));
        pai.addView(t);
    }

    private void mostrarEstatisticas() {
        Set<String> artistas = new java.util.HashSet<>();
        for (Faixa f : todas) if (!f.artista.isEmpty()) artistas.add(Faixa.normalizar(f.artista));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(8), dp(20), dp(8));
        linhaDeCartoes(col, cartaoDeNumero(String.valueOf(todas.size()), "músicas"), cartaoDeNumero(String.valueOf(artistas.size()), "artistas"));
        linhaDeCartoes(col, cartaoDeNumero(String.valueOf(curtidas.size()), "curtidas"), cartaoDeNumero(String.valueOf(historico.totalDeVezes()), "reproduções"));
        linhaDeCartoes(col, cartaoDeNumero(Historico.duracao(historico.tempoTotalMs()), "tempo ouvido"), cartaoDeNumero(String.valueOf(minhas.nomes().size()), "playlists"));
        List<Faixa> top = historico.maisTocadas(todas, 5);
        if (!top.isEmpty()) {
            tituloDeLista(col, "Mais tocadas");
            for (int i = 0; i < top.size(); i++) {
                int v = historico.vezes(top.get(i).chave);
                linhaDeRanking(col, i + 1, top.get(i).titulo, v + (v == 1 ? " vez" : " vezes"));
            }
        }
        List<String[]> bandas = historico.bandasMaisOuvidas(todas, 5);
        if (!bandas.isEmpty()) {
            tituloDeLista(col, "Artistas mais ouvidos");
            for (int i = 0; i < bandas.size(); i++) linhaDeRanking(col, i + 1, bandas.get(i)[0], bandas.get(i)[1]);
        }
        ScrollView rolagem = new ScrollView(this);
        rolagem.addView(col);
        new Construtor().setTitle("Estatísticas").setView(rolagem).setPositiveButton("Fechar", null).show();
    }

    private void renomear(final Faixa f) {
        final EditText campo = new EditText(this);
        campo.setSingleLine(true);
        campo.setText(Faixa.semExtensao(f.nome));
        campo.setSelection(campo.getText().length());
        campo.setTextColor(temaClaro ? 0xFF18181B : 0xFFFFFFFF);
        FrameLayout caixa = new FrameLayout(this);
        caixa.setPadding(dp(22), dp(8), dp(22), 0);
        caixa.addView(campo);
        new Construtor()
                .setTitle("Renomear")
                .setView(caixa)
                .setPositiveButton("Salvar", (d, q) -> aplicarNome(f, campo.getText().toString()))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void aplicarNome(final Faixa f, String digitado) {
        final String novoBase = digitado.replaceAll("[\\\\/:*?\"<>|]", "").trim();
        if (novoBase.isEmpty() || novoBase.equals(Faixa.semExtensao(f.nome))) return;
        final String extensao = f.nome.substring(Faixa.semExtensao(f.nome).length());
        new Thread(() -> {
            Uri novo = null;
            try {
                novo = android.provider.DocumentsContract.renameDocument(getContentResolver(), Uri.parse(f.uri), novoBase + extensao);
            } catch (Exception e) {
                novo = null;
            }
            final Uri resultado = novo;
            ui.post(() -> {
                if (resultado == null) {
                    Toast.makeText(this, "Não consegui renomear. Se o pendrive estiver protegido, escolha a pasta de novo no menu.", Toast.LENGTH_LONG).show();
                    return;
                }
                Faixa nova = new Faixa(resultado.toString(), novoBase + extensao);
                List<Faixa> lista = new ArrayList<>(todas);
                for (int i = 0; i < lista.size(); i++) if (lista.get(i).uri.equals(f.uri)) lista.set(i, nova);
                prefs.renomearCurtida(f.chave, nova.chave);
                curtidas = prefs.curtidas();
                historico.renomear(f.chave, nova.chave);
                minhas.renomearChave(f.chave, nova.chave);
                salvarPlaylists();
                guardarHistorico();
                lista = Catalogo.ordenar(lista);
                Biblioteca.guardar(this, lista);
                definirTodas(lista);
                Toast.makeText(this, "Renomeada", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void excluir(final Faixa f) {
        new Construtor()
                .setTitle("Excluir do pendrive?")
                .setMessage(f.nome + "\n\nO arquivo será apagado e não dá para desfazer.")
                .setPositiveButton("Excluir", (d, q) -> new Thread(() -> {
                    boolean apagou;
                    try {
                        apagou = android.provider.DocumentsContract.deleteDocument(getContentResolver(), Uri.parse(f.uri));
                    } catch (Exception e) {
                        apagou = false;
                    }
                    final boolean ok = apagou;
                    ui.post(() -> {
                        if (!ok) {
                            Toast.makeText(this, "Não consegui excluir. Se o pendrive estiver protegido, escolha a pasta de novo no menu.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        List<Faixa> lista = new ArrayList<>(todas);
                        for (int i = lista.size() - 1; i >= 0; i--) if (lista.get(i).uri.equals(f.uri)) lista.remove(i);
                        Biblioteca.guardar(this, lista);
                        definirTodas(lista);
                        Toast.makeText(this, "Excluída", Toast.LENGTH_SHORT).show();
                    });
                }).start())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Curtidas e playlists que vieram do PC: as curtidas se juntam as daqui; as playlists ficam no menu "Pasta". */
    private void importarDoPc(String texto) {
        sincronizacao = Sincronizacao.ler(texto);
        prefs.adicionarCurtidas(sincronizacao.curtidas);
        curtidas = prefs.curtidas();
        aplicarFiltro();
        atualizarPlayer();
        Toast.makeText(this, "Curtidas e playlists do PC importadas", Toast.LENGTH_SHORT).show();
    }

    /** Troca a secao do menu lateral. */
    private void irPara(int nova) {
        if (nova == SECAO_COLECAO && secao != SECAO_COLECAO) secaoAnterior = secao;
        secao = nova;
        lblSecao.setText(nova == SECAO_COLECAO ? colecaoTitulo : NOMES_SECAO[nova]);
        marcarSecao();
        atualizarCabecalho();
        edtBusca.setText("");
        aplicarFiltro();
        if (lista != null) lista.setSelection(0);
    }

    /** Abre uma lista de dentro de outra: as musicas de uma banda, de um album, de um genero ou de uma playlist. */
    private void abrirColecao(Grupo g) {
        colecaoTitulo = g.titulo;
        colecaoFaixas = g.faixas;
        colecaoPlaylist = "meu".equals(g.origem) ? g.titulo : null;
        irPara(SECAO_COLECAO);
    }

    private void menuOuVoltar() {
        if (secao == SECAO_COLECAO) irPara(secaoAnterior); else abrirGaveta();
    }

    private void atualizarCabecalho() {
        icMenu.definir(secao == SECAO_COLECAO ? IconeView.SETA : IconeView.MENU, TEXTO);
        celulaMais.setVisibility(secao == SECAO_PLAYLISTS ? View.VISIBLE : View.GONE);
        celulaAleatorio.setVisibility(modoGrupos() || todas.isEmpty() ? View.GONE : View.VISIBLE);
        if (bOrdem != null) bOrdem.setVisibility(secao == SECAO_MUSICAS || secao == SECAO_CURTIDAS ? View.VISIBLE : View.GONE);
    }

    private void menuDoTemporizador() {
        if (desligar != null || pararNoFim) {
            if (desligar != null) ui.removeCallbacks(desligar);
            desligar = null;
            pararNoFim = false;
            if (itemTemporizador != null) itemTemporizador.setText("Temporizador");
            Toast.makeText(this, "Temporizador cancelado", Toast.LENGTH_SHORT).show();
            return;
        }
        final int[] minutos = {15, 30, 45, 60, 90};
        String[] nomes = new String[minutos.length + 1];
        for (int i = 0; i < minutos.length; i++) nomes[i] = minutos[i] + " minutos";
        nomes[minutos.length] = "Quando esta música acabar";
        new Construtor()
                .setTitle("Pausar a música em")
                .setItems(nomes, (d, qual) -> {
                    if (qual == minutos.length) {
                        pararNoFim = true;
                        if (itemTemporizador != null) itemTemporizador.setText("Temporizador  ·  fim da música");
                        Toast.makeText(this, "A música pausa quando esta acabar", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    desligar = () -> {
                        desligar = null;
                        if (itemTemporizador != null) itemTemporizador.setText("Temporizador");
                        try {
                            if (player != null && player.isConnected()) player.pause();
                        } catch (Exception e) {
                            // o app ja foi fechado: nada a pausar daqui
                        }
                    };
                    ui.postDelayed(desligar, minutos[qual] * 60_000L);
                    if (itemTemporizador != null) itemTemporizador.setText("Temporizador  ·  " + minutos[qual] + " min");
                    Toast.makeText(this, "A música pausa em " + minutos[qual] + " minutos", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    // ------------------------------------------------------------------ cores
    private void aplicarPaleta() {
        Cores.Paleta c = Cores.montar(prefs.corFundo(), prefs.corAcento());
        FUNDO = c.fundo;
        CARTAO = c.cartao;
        LINHA = c.linha;
        TEXTO = c.texto;
        SUAVE = c.suave;
        ICONE = c.icone;
        ACENTO = c.acento;
        temaClaro = c.claro;
        getWindow().setStatusBarColor(FUNDO);
        getWindow().setNavigationBarColor(CARTAO);
        int flags = 0;
        if (temaClaro) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    /** Dialogos no visual do app: fundo arredondado na cor do tema e botoes na cor de destaque. */
    private final class Construtor extends AlertDialog.Builder {
        Construtor() {
            super(MainActivity.this, estiloDialogo());
        }

        @Override
        public AlertDialog show() {
            AlertDialog d = create();
            d.show();
            try {
                if (d.getWindow() != null) {
                    d.getWindow().setBackgroundDrawable(new android.graphics.drawable.InsetDrawable(forma(CARTAO, 22, LINHA), dp(6)));
                }
                int[] botoes = {AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL};
                for (int b : botoes) {
                    TextView t = d.getButton(b);
                    if (t != null) t.setTextColor(b == AlertDialog.BUTTON_POSITIVE ? ACENTO : SUAVE);
                }
                int idTitulo = getResources().getIdentifier("alertTitle", "id", "android");
                View titulo = idTitulo == 0 ? null : d.findViewById(idTitulo);
                if (titulo instanceof TextView) ((TextView) titulo).setTextColor(TEXTO);
            } catch (Exception e) {
                // o visual padrao do dialogo continua valendo
            }
            return d;
        }
    }

    private int estiloDialogo() {
        return temaClaro ? android.R.style.Theme_Material_Light_Dialog_Alert : android.R.style.Theme_Material_Dialog_Alert;
    }

    private GradientDrawable bolinha(int cor, boolean marcada) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(cor);
        g.setStroke(dp(marcada ? 3 : 1), marcada ? TEXTO : LINHA);
        return g;
    }

    private void escolherCores() {
        final ScrollView rolagem = new ScrollView(this);
        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(8), dp(20), dp(8));
        rolagem.addView(col);
        final AlertDialog dialogo = new Construtor().setTitle("Aparência").setView(rolagem)
                .setNegativeButton("Padrão", (d, q) -> {
                    prefs.cores(0, 0);
                    recreate();
                })
                .setPositiveButton("Fechar", null)
                .create();
        for (int bloco = 0; bloco < 2; bloco++) {
            final boolean fundo = bloco == 0;
            TextView rotulo = texto(14, SUAVE, true);
            rotulo.setText(fundo ? "Fundo" : "Cor de destaque");
            rotulo.setPadding(0, dp(bloco == 0 ? 4 : 18), 0, dp(8));
            col.addView(rotulo);
            final int[] opcoes = fundo ? Cores.FUNDOS : Cores.ACENTOS;
            final int atual = fundo ? (prefs.corFundo() == 0 ? Cores.PADRAO_FUNDO : prefs.corFundo()) : (prefs.corAcento() == 0 ? Cores.PADRAO_ACENTO : prefs.corAcento());
            LinearLayout linha = null;
            for (int i = 0; i <= opcoes.length; i++) {
                if (i % 6 == 0) {
                    linha = new LinearLayout(this);
                    col.addView(linha);
                }
                View b = new View(this);
                LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(dp(40), dp(40));
                pb.setMargins(dp(4), dp(4), dp(4), dp(4));
                if (i == opcoes.length) {  // a ultima bolinha abre o seletor de uma cor so sua
                    b.setBackground(bolinha(0xFF55555F, false));
                    TextView mais = texto(20, 0xFFFFFFFF, true);
                    mais.setText("+");
                    mais.setGravity(Gravity.CENTER);
                    mais.setBackground(bolinha(0xFF55555F, false));
                    mais.setOnClickListener(v -> {
                        dialogo.dismiss();
                        corPersonalizada(fundo, atual);
                    });
                    linha.addView(mais, pb);
                    continue;
                }
                final int cor = opcoes[i];
                b.setBackground(bolinha(cor, (cor & 0xFFFFFF) == (atual & 0xFFFFFF)));
                b.setOnClickListener(v -> {
                    if (fundo) prefs.cores(cor, prefs.corAcento()); else prefs.cores(prefs.corFundo(), cor);
                    dialogo.dismiss();
                    recreate();
                });
                linha.addView(b, pb);
            }
        }
        dialogo.show();
    }

    /** Escolha livre de uma cor: matiz, saturacao e brilho, com a amostra ao vivo. */
    private void corPersonalizada(final boolean fundo, int inicial) {
        final float[] hsv = new float[3];
        Color.colorToHSV(inicial, hsv);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(12), dp(20), dp(4));
        final View amostra = new View(this);
        col.addView(amostra, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        final int[] atual = {inicial};
        final Runnable pintar = () -> {
            atual[0] = Color.HSVToColor(hsv);
            amostra.setBackground(forma(atual[0], 14, LINHA));
        };
        pintar.run();
        String[] nomes = {"Cor", "Intensidade", "Brilho"};
        int[] maximos = {360, 100, 100};
        int[] valores = {Math.round(hsv[0]), Math.round(hsv[1] * 100), Math.round(hsv[2] * 100)};
        for (int i = 0; i < 3; i++) {
            final int indice = i;
            TextView r = texto(13, SUAVE, false);
            r.setText(nomes[i]);
            r.setPadding(0, dp(12), 0, 0);
            col.addView(r);
            SeekBar s = new SeekBar(this);
            s.setMax(maximos[i]);
            s.setProgress(valores[i]);
            s.setProgressTintList(ColorStateList.valueOf(ACENTO));
            s.setThumbTintList(ColorStateList.valueOf(ACENTO));
            s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar sb, int v, boolean doUsuario) {
                    hsv[indice] = indice == 0 ? v : v / 100f;
                    pintar.run();
                }

                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {}
            });
            col.addView(s);
        }
        new Construtor().setTitle(fundo ? "Cor do fundo" : "Cor de destaque").setView(col)
                .setPositiveButton("Usar", (d, q) -> {
                    if (fundo) prefs.cores(atual[0], prefs.corAcento()); else prefs.cores(prefs.corFundo(), atual[0]);
                    recreate();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ------------------------------------------------------------------ montagem da tela
    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private GradientDrawable forma(int cor, float raioDp, int traco) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(cor);
        g.setCornerRadius(dp(raioDp));
        if (traco != 0) g.setStroke(dp(1), traco);
        return g;
    }

    private TextView texto(float sp, int cor, boolean negrito) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(cor);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        if (negrito) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    /** Fundo com a onda de toque: o brilho que acompanha o dedo. */
    private android.graphics.drawable.Drawable toque(android.graphics.drawable.Drawable fundo, android.graphics.drawable.Drawable mascara) {
        return new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf((TEXTO & 0x00FFFFFF) | 0x26000000), fundo, mascara);
    }

    private TextView botao(String rotulo, boolean destaque, Runnable acao) {
        TextView b = texto(15, TEXTO, true);
        b.setText(rotulo);
        b.setGravity(Gravity.CENTER);
        b.setBackground(toque(destaque ? forma(ACENTO, 24, 0) : forma(CARTAO, 24, LINHA), forma(0xFF000000, 24, 0)));
        b.setOnClickListener(v -> acao.run());
        return b;
    }

    private IconeView icone(int tipo, int ladoDp, Runnable acao, FrameLayout celula) {
        IconeView v = new IconeView(this, tipo);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(ladoDp), dp(ladoDp), Gravity.CENTER);
        celula.addView(v, p);
        celula.setOnClickListener(x -> acao.run());
        return v;
    }

    private void arredondar(View v, final float raioDp) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline o) {
                float raio = raioDp < 0 ? Math.min(view.getWidth(), view.getHeight()) / 2f : dp(raioDp);
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), raio);
            }
        });
        v.setClipToOutline(true);
        v.invalidateOutline();
    }

    private View montarTela() {
        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundColor(FUNDO);

        // cabecalho: menu (ou voltar), nome da secao, contagem e as acoes da secao
        LinearLayout cab = new LinearLayout(this);
        cab.setGravity(Gravity.CENTER_VERTICAL);
        cab.setPadding(dp(4), dp(8), dp(8), dp(4));
        FrameLayout celulaMenu = new FrameLayout(this);
        icMenu = icone(IconeView.MENU, 26, this::menuOuVoltar, celulaMenu);
        icMenu.definir(IconeView.MENU, TEXTO);
        cab.addView(celulaMenu, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout titulos = new LinearLayout(this);
        titulos.setOrientation(LinearLayout.VERTICAL);
        lblSecao = texto(22, TEXTO, true);
        lblSecao.setText(NOMES_SECAO[secao]);
        lblContagem = texto(13, SUAVE, false);
        titulos.addView(lblSecao);
        titulos.addView(lblContagem);
        cab.addView(titulos, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        celulaMais = new FrameLayout(this);
        icone(IconeView.MAIS, 26, () -> novaPlaylist(null), celulaMais).definir(IconeView.MAIS, TEXTO);
        celulaMais.setVisibility(View.GONE);
        cab.addView(celulaMais, new LinearLayout.LayoutParams(dp(48), dp(48)));
        raiz.addView(cab);

        // pesquisa
        edtBusca = new EditText(this);
        edtBusca.setHint("Pesquisar");
        edtBusca.setHintTextColor(SUAVE);
        edtBusca.setTextColor(TEXTO);
        edtBusca.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        edtBusca.setSingleLine(true);
        edtBusca.setInputType(InputType.TYPE_CLASS_TEXT);
        edtBusca.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        edtBusca.setBackground(forma(CARTAO, 14, LINHA));
        edtBusca.setPadding(dp(16), 0, dp(16), 0);
        edtBusca.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (limparBusca != null) limparBusca.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                aplicarFiltro();
            }
        });
        edtBusca.setOnEditorActionListener((v, acao, ev) -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            return true;
        });
        edtBusca.setPadding(dp(16), 0, dp(48), 0);
        FrameLayout caixaBusca = new FrameLayout(this);
        caixaBusca.addView(edtBusca, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        limparBusca = new FrameLayout(this);
        IconeView xBusca = new IconeView(this, IconeView.FECHAR);
        xBusca.definir(IconeView.FECHAR, SUAVE);
        limparBusca.addView(xBusca, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        limparBusca.setVisibility(View.GONE);
        limparBusca.setOnClickListener(v -> edtBusca.setText(""));
        caixaBusca.addView(limparBusca, new FrameLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END));
        LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        pb.setMargins(dp(16), dp(6), dp(16), dp(8));
        raiz.addView(caixaBusca, pb);

        // atalhos para tocar a lista inteira (somem nas listas de bandas, albuns, generos e playlists)
        celulaAleatorio = new FrameLayout(this);
        LinearLayout pilulas = new LinearLayout(this);
        pilulas.setGravity(Gravity.CENTER_VERTICAL);
        TextView bTocar = botao("Tocar tudo", true, this::tocarTudo);
        bTocar.setTextColor(0xFFFFFFFF);
        TextView bAleatorio = botao("Aleatório", false, this::tocarAleatorio);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        pp.setMargins(0, 0, dp(5), 0);
        LinearLayout.LayoutParams pa = new LinearLayout.LayoutParams(0, dp(42), 1f);
        pa.setMargins(dp(5), 0, 0, 0);
        pilulas.addView(bTocar, pp);
        pilulas.addView(bAleatorio, pa);
        bOrdem = botao("Ordem", false, this::menuOrdenar);
        LinearLayout.LayoutParams po = new LinearLayout.LayoutParams(dp(84), dp(42));
        po.setMargins(dp(10), 0, 0, 0);
        pilulas.addView(bOrdem, po);
        celulaAleatorio.addView(pilulas, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams pcel = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pcel.setMargins(dp(16), 0, dp(16), dp(6));
        raiz.addView(celulaAleatorio, pcel);

        // lista
        FrameLayout meio = new FrameLayout(this);
        lista = new ListView(this);
        lista.setDivider(null);
        lista.setSelector(android.R.color.transparent);
        lista.setFastScrollEnabled(true);
        adaptador = new Adaptador();
        lista.setAdapter(adaptador);
        lista.setOnItemClickListener((p, v, pos, id) -> {
            if (modoGrupos()) {
                if (pos >= 0 && pos < grupos.size()) abrirColecao(grupos.get(pos));
            } else {
                tocarDaLista(pos);
            }
        });
        lista.setOnItemLongClickListener((p, v, pos, id) -> {
            if (modoGrupos()) {
                if (pos >= 0 && pos < grupos.size()) opcoesDoGrupo(grupos.get(pos));
            } else if (pos >= 0 && pos < visiveis.size()) {
                opcoesDaFaixa(visiveis.get(pos));
            }
            return true;
        });
        meio.addView(lista, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        lblVazio = texto(16, SUAVE, false);
        lblVazio.setSingleLine(false);
        lblVazio.setGravity(Gravity.CENTER);
        lblVazio.setPadding(dp(32), 0, dp(32), 0);
        LinearLayout caixaVazia = new LinearLayout(this);
        caixaVazia.setOrientation(LinearLayout.VERTICAL);
        caixaVazia.setGravity(Gravity.CENTER_HORIZONTAL);
        caixaVazia.addView(lblVazio, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btnVazio = botao("Escolher a pasta das músicas", true, this::escolherPasta);
        btnVazio.setTextColor(0xFFFFFFFF);
        btnVazio.setPadding(dp(24), 0, dp(24), 0);
        btnVazio.setVisibility(View.GONE);
        LinearLayout.LayoutParams pbv = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        pbv.setMargins(0, dp(16), 0, 0);
        caixaVazia.addView(btnVazio, pbv);
        meio.addView(caixaVazia, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        raiz.addView(meio, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        raiz.addView(montarMini(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout tudo = new FrameLayout(this);
        tudo.setBackgroundColor(FUNDO);
        tudo.addView(raiz, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        telaTocando = montarTocando();
        tudo.addView(telaTocando, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        cortina = new View(this);
        cortina.setBackgroundColor(0xB0000000);
        cortina.setAlpha(0f);
        cortina.setVisibility(View.GONE);
        cortina.setOnClickListener(v -> fecharGaveta());
        tudo.addView(cortina, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        int largura = Math.min(dp(310), Math.round(getResources().getDisplayMetrics().widthPixels * 0.82f));
        gaveta = montarGaveta();
        gaveta.setTranslationX(-largura);
        gaveta.setVisibility(View.GONE);
        tudo.addView(gaveta, new FrameLayout.LayoutParams(largura, ViewGroup.LayoutParams.MATCH_PARENT));
        aplicarIconesIniciais();
        marcarSecao();
        atualizarCabecalho();
        return tudo;
    }

    /** O player pequeno, sempre embaixo da lista: toque nele para abrir o player grande. */
    private View montarMini() {
        mini = new LinearLayout(this);
        mini.setOrientation(LinearLayout.VERTICAL);
        float r = dp(18);
        GradientDrawable fundoMini = new GradientDrawable();
        fundoMini.setColor(CARTAO);
        fundoMini.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        mini.setBackground(fundoMini);

        barraMini = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        barraMini.setMax(1000);
        barraMini.setProgressTintList(ColorStateList.valueOf(ACENTO));
        barraMini.setProgressBackgroundTintList(ColorStateList.valueOf(LINHA));
        LinearLayout.LayoutParams pbm = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        pbm.setMargins(dp(18), dp(6), dp(18), 0);
        mini.addView(barraMini, pbm);

        LinearLayout linha = new LinearLayout(this);
        linha.setGravity(Gravity.CENTER_VERTICAL);
        linha.setPadding(dp(14), dp(8), dp(4), dp(8));
        FrameLayout caixaCapa = new FrameLayout(this);
        caixaCapa.setBackground(forma(LINHA, 10, 0));
        arredondar(caixaCapa, 10);
        capaMini = new ImageView(this);
        capaMini.setScaleType(ImageView.ScaleType.CENTER_CROP);
        caixaCapa.addView(capaMini, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        notaMini = new IconeView(this, IconeView.NOTA);
        notaMini.definir(IconeView.NOTA, SUAVE);
        caixaCapa.addView(notaMini, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        linha.addView(caixaCapa, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout nomes = new LinearLayout(this);
        nomes.setOrientation(LinearLayout.VERTICAL);
        nomes.setPadding(dp(12), 0, dp(8), 0);
        lblTituloMini = texto(15, TEXTO, true);
        lblArtistaMini = texto(12, SUAVE, false);
        nomes.addView(lblTituloMini);
        nomes.addView(lblArtistaMini);
        linha.addView(nomes, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout cAnterior = new FrameLayout(this);
        icone(IconeView.ANTERIOR, 26, () -> {
            if (player != null && player.getMediaItemCount() > 0) player.seekToPrevious();
        }, cAnterior).definir(IconeView.ANTERIOR, TEXTO);
        linha.addView(cAnterior, new LinearLayout.LayoutParams(dp(44), dp(50)));
        FrameLayout cPlay = new FrameLayout(this);
        icPlayMini = icone(IconeView.PLAY, 30, this::tocarPausar, cPlay);
        icPlayMini.definir(IconeView.PLAY, TEXTO);
        linha.addView(cPlay, new LinearLayout.LayoutParams(dp(48), dp(50)));
        FrameLayout cProxima = new FrameLayout(this);
        icProximaMini = icone(IconeView.PROXIMA, 28, () -> {
            if (player != null && player.getMediaItemCount() > 0) player.seekToNext();
        }, cProxima);
        icProximaMini.definir(IconeView.PROXIMA, TEXTO);
        linha.addView(cProxima, new LinearLayout.LayoutParams(dp(44), dp(50)));
        mini.addView(linha);
        mini.setOnClickListener(v -> {
            if (player != null && player.getMediaItemCount() > 0) abrirTocando();
        });
        gestos(mini, false, false, false);
        return mini;
    }

    private LinearLayout acaoRapida(int tipo, String rotulo, Runnable acao) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(0, dp(6), 0, dp(4));
        if (tipo >= 0) {
            IconeView ic = new IconeView(this, tipo);
            ic.definir(tipo, ICONE);
            c.addView(ic, new LinearLayout.LayoutParams(dp(26), dp(26)));
        } else {
            lblVelocidade = texto(15, ICONE, true);
            lblVelocidade.setText("1x");
            lblVelocidade.setGravity(Gravity.CENTER);
            c.addView(lblVelocidade, new LinearLayout.LayoutParams(dp(50), dp(26)));
        }
        TextView r = texto(11, SUAVE, false);
        r.setText(rotulo);
        r.setGravity(Gravity.CENTER);
        r.setPadding(0, dp(2), 0, 0);
        c.addView(r);
        c.setOnClickListener(v -> acao.run());
        return c;
    }

    /** O player grande: capa, letra, barra de tempo, controles e atalhos. Fica escondido ate tocar no player pequeno. */
    private View montarTocando() {
        FrameLayout tela = new FrameLayout(this);
        tela.setBackgroundColor(FUNDO);
        tela.setClickable(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(8), dp(20), dp(12));

        LinearLayout topo = new LinearLayout(this);
        topo.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout cFechar = new FrameLayout(this);
        icone(IconeView.BAIXO, 26, this::fecharTocando, cFechar).definir(IconeView.BAIXO, TEXTO);
        topo.addView(cFechar, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView tituloTopo = texto(15, SUAVE, true);
        tituloTopo.setText("Tocando agora");
        tituloTopo.setGravity(Gravity.CENTER);
        topo.addView(tituloTopo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        FrameLayout cOpcoes = new FrameLayout(this);
        icone(IconeView.PONTOS, 26, () -> {
            Faixa f = faixaAtual();
            if (f != null) opcoesDaFaixa(f);
        }, cOpcoes).definir(IconeView.PONTOS, TEXTO);
        topo.addView(cOpcoes, new LinearLayout.LayoutParams(dp(48), dp(48)));
        col.addView(topo);

        FrameLayout areaCapa = new FrameLayout(this);
        int largura = getResources().getDisplayMetrics().widthPixels;
        int altura = getResources().getDisplayMetrics().heightPixels;
        int lado = Math.max(dp(180), Math.min(largura - dp(56), altura - dp(440)));
        FrameLayout caixaCapa = new FrameLayout(this);
        caixaCapa.setBackground(forma(CARTAO, 22, 0));
        arredondar(caixaCapa, 22);
        capa = new ImageView(this);
        capa.setScaleType(ImageView.ScaleType.CENTER_CROP);
        caixaCapa.addView(capa, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        notaGrande = new IconeView(this, IconeView.NOTA);
        notaGrande.definir(IconeView.NOTA, SUAVE);
        caixaCapa.addView(notaGrande, new FrameLayout.LayoutParams(dp(96), dp(96), Gravity.CENTER));
        painelLetra = new ScrollView(this);
        painelLetra.setBackgroundColor((FUNDO & 0x00FFFFFF) | 0xEE000000);
        painelLetra.setVisibility(View.GONE);
        lblLetra = new TextView(this);
        lblLetra.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        lblLetra.setTextColor(TEXTO);
        lblLetra.setLineSpacing(0, 1.3f);
        lblLetra.setOnTouchListener((v, ev) -> {
            letraToqueY = ev.getY();
            return false;
        });
        lblLetra.setOnClickListener(v -> irParaLinhaDaLetra());
        lblLetra.setPadding(dp(18), dp(16), dp(18), dp(16));
        painelLetra.addView(lblLetra);
        caixaCapa.addView(painelLetra, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        gestos(caixaCapa, true, true, true);
        areaCapa.addView(caixaCapa, new FrameLayout.LayoutParams(lado, lado, Gravity.CENTER));
        col.addView(areaCapa, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout info = new LinearLayout(this);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(0, dp(12), 0, 0);
        LinearLayout nomes = new LinearLayout(this);
        nomes.setOrientation(LinearLayout.VERTICAL);
        lblTitulo = texto(22, TEXTO, true);
        lblTitulo.setText("Nada tocando");
        lblArtista = texto(16, SUAVE, false);
        nomes.addView(lblTitulo);
        nomes.addView(lblArtista);
        info.addView(nomes, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        FrameLayout celulaCurtir = new FrameLayout(this);
        icCurtir = icone(IconeView.CORACAO, 30, this::curtirAtual, celulaCurtir);
        icCurtir.setVisibility(View.INVISIBLE);
        info.addView(celulaCurtir, new LinearLayout.LayoutParams(dp(52), dp(52)));
        col.addView(info);

        LinearLayout tempos = new LinearLayout(this);
        tempos.setGravity(Gravity.CENTER_VERTICAL);
        lblPos = texto(12, SUAVE, false);
        lblPos.setText("0:00");
        lblDur = texto(12, SUAVE, false);
        lblDur.setText("0:00");
        barra = new SeekBar(this);
        barra.setMax(1000);
        barra.setProgressTintList(ColorStateList.valueOf(ACENTO));
        barra.setThumbTintList(ColorStateList.valueOf(ACENTO));
        barra.setProgressBackgroundTintList(ColorStateList.valueOf(LINHA));
        barra.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int valor, boolean doUsuario) {
                if (doUsuario && player != null && player.getDuration() > 0) lblPos.setText(formatar(player.getDuration() * valor / 1000));
            }

            @Override public void onStartTrackingTouch(SeekBar s) { arrastando = true; }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                if (player != null && player.getDuration() > 0) player.seekTo(player.getDuration() * s.getProgress() / 1000);
                arrastando = false;
            }
        });
        tempos.addView(lblPos, new LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT));
        tempos.addView(barra, new LinearLayout.LayoutParams(0, dp(40), 1f));
        lblDur.setGravity(Gravity.END);
        tempos.addView(lblDur, new LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(tempos);

        LinearLayout controles = new LinearLayout(this);
        controles.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout c1 = new FrameLayout(this), c2 = new FrameLayout(this), c3 = new FrameLayout(this), c4 = new FrameLayout(this), c5 = new FrameLayout(this);
        icAleatorio = icone(IconeView.ALEATORIO, 30, this::alternarAleatorio, c1);
        icAnterior = icone(IconeView.ANTERIOR, 38, () -> { if (player != null) player.seekToPrevious(); }, c2);
        icPlay = icone(IconeView.PLAY, 42, this::tocarPausar, c3);
        c3.setBackground(forma(ACENTO, 36, 0));
        icProxima = icone(IconeView.PROXIMA, 38, () -> { if (player != null) player.seekToNext(); }, c4);
        icRepetir = icone(IconeView.REPETIR, 30, this::alternarRepetir, c5);
        icAleatorio.definir(IconeView.ALEATORIO, ICONE);
        for (FrameLayout c : new FrameLayout[]{c1, c2, c3, c4, c5}) {
            boolean central = c == c3;
            LinearLayout.LayoutParams p = central ? new LinearLayout.LayoutParams(dp(72), dp(72)) : new LinearLayout.LayoutParams(0, dp(64), 1f);
            if (central) p.setMargins(dp(8), 0, dp(8), 0);
            controles.addView(c, p);
        }
        col.addView(controles);

        LinearLayout acoes = new LinearLayout(this);
        acoes.setPadding(0, dp(4), 0, 0);
        LinearLayout aLetra = acaoRapida(IconeView.LETRA, "Letra", this::alternarLetra);
        icLetra = (IconeView) aLetra.getChildAt(0);
        acoes.addView(aLetra, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        acoes.addView(acaoRapida(IconeView.FILA, "Fila", this::mostrarFila), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        acoes.addView(acaoRapida(-1, "Velocidade", this::menuVelocidade), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        acoes.addView(acaoRapida(IconeView.PLAYLIST_MAIS, "Playlist", () -> {
            Faixa f = faixaAtual();
            if (f != null) escolherPlaylist(f);
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        acoes.addView(acaoRapida(IconeView.RELOGIO, "Timer", this::menuDoTemporizador), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(acoes);

        tela.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tela.setVisibility(View.GONE);
        return tela;
    }

    private void abrirTocando() {
        if (tocandoAberto || player == null || player.getMediaItemCount() == 0) return;
        tocandoAberto = true;
        telaTocando.setTranslationY(getResources().getDisplayMetrics().heightPixels);
        telaTocando.setVisibility(View.VISIBLE);
        telaTocando.animate().translationY(0).setDuration(240).start();
    }

    private void fecharTocando() {
        if (!tocandoAberto) return;
        tocandoAberto = false;
        telaTocando.animate().translationY(getResources().getDisplayMetrics().heightPixels).setDuration(200).withEndAction(() -> {
            if (!tocandoAberto) telaTocando.setVisibility(View.GONE);
        }).start();
    }

    // ------------------------------------------------------------------ menu lateral
    private TextView itemDoMenu(String rotulo, Runnable acao) {
        TextView t = texto(16, TEXTO, false);
        t.setText(rotulo);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(16), 0, dp(16), 0);
        t.setBackground(toque(null, forma(0xFF000000, 12, 0)));
        t.setOnClickListener(v -> {
            fecharGaveta();
            acao.run();
        });
        return t;
    }

    private void adicionarItem(LinearLayout pai, TextView item) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        p.setMargins(dp(10), dp(2), dp(10), dp(2));
        pai.addView(item, p);
    }

    private void adicionarLinha(LinearLayout pai) {
        View linha = new View(this);
        linha.setBackgroundColor(LINHA);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        p.setMargins(dp(20), dp(8), dp(20), dp(8));
        pai.addView(linha, p);
    }

    private View montarGaveta() {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setBackgroundColor(CARTAO);
        g.setClickable(true);  // nao deixa o toque passar para a lista de tras

        LinearLayout marca = new LinearLayout(this);
        marca.setGravity(Gravity.CENTER_VERTICAL);
        marca.setPadding(dp(20), dp(28), dp(20), dp(20));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        marca.addView(logo, new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView nome = texto(20, TEXTO, true);
        nome.setText("Raccoon Music");
        nome.setPadding(dp(14), 0, 0, 0);
        LinearLayout nomes = new LinearLayout(this);
        nomes.setOrientation(LinearLayout.VERTICAL);
        nomes.addView(nome);
        lblGaveta = texto(13, SUAVE, false);
        lblGaveta.setPadding(dp(14), dp(2), 0, 0);
        lblGaveta.setText(todas.size() == 1 ? "1 música" : todas.size() + " músicas");
        nomes.addView(lblGaveta);
        marca.addView(nomes, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        g.addView(marca);

        ScrollView rolagem = new ScrollView(this);
        LinearLayout itens = new LinearLayout(this);
        itens.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < SECOES_NO_MENU; i++) {
            final int destino = i;
            itensSecao[i] = itemDoMenu(NOMES_SECAO[i], () -> irPara(destino));
            adicionarItem(itens, itensSecao[i]);
        }
        adicionarLinha(itens);
        adicionarItem(itens, itemDoMenu("Estatísticas", this::mostrarEstatisticas));
        adicionarItem(itens, itemDoMenu("Exportar músicas", this::escolherOrigemDaExportacao));
        adicionarItem(itens, itemDoMenu("Aparência", this::escolherCores));
        itemTemporizador = itemDoMenu("Temporizador", this::menuDoTemporizador);
        adicionarItem(itens, itemTemporizador);
        adicionarLinha(itens);
        adicionarItem(itens, itemDoMenu("Atualizar a biblioteca", this::atualizarBiblioteca));
        adicionarItem(itens, itemDoMenu("Escolher outra pasta", this::escolherPasta));
        if (Atualizador.disponivel()) {
            itemVersao = itemDoMenu("Procurar atualização", () -> procurarAtualizacao(true));
            adicionarItem(itens, itemVersao);
        }
        rolagem.addView(itens);
        g.addView(rolagem, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView versao = texto(12, SUAVE, false);
        versao.setText("Versão " + Atualizador.instalada(this));
        versao.setPadding(dp(24), dp(8), dp(24), dp(16));
        g.addView(versao);
        return g;
    }

    private void marcarSecao() {
        for (int i = 0; i < itensSecao.length; i++) {
            if (itensSecao[i] == null) continue;
            boolean atual = i == (secao == SECAO_COLECAO ? secaoAnterior : secao);
            itensSecao[i].setTextColor(atual ? ACENTO : TEXTO);
            itensSecao[i].setTypeface(null, atual ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            itensSecao[i].setBackground(toque(atual ? forma(LINHA, 12, 0) : null, forma(0xFF000000, 12, 0)));
        }
    }

    private void abrirGaveta() {
        if (gavetaAberta) return;
        gavetaAberta = true;
        itemTemporizador.setText(desligar == null ? "Temporizador" : "Cancelar o temporizador");
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && edtBusca != null) imm.hideSoftInputFromWindow(edtBusca.getWindowToken(), 0);
        cortina.setVisibility(View.VISIBLE);
        gaveta.setVisibility(View.VISIBLE);
        cortina.animate().alpha(1f).setDuration(180).start();
        gaveta.animate().translationX(0).setDuration(200).start();
    }

    private void fecharGaveta() {
        if (!gavetaAberta) return;
        gavetaAberta = false;
        cortina.animate().alpha(0f).setDuration(160).withEndAction(() -> {
            if (!gavetaAberta) cortina.setVisibility(View.GONE);
        }).start();
        gaveta.animate().translationX(-gaveta.getWidth()).setDuration(180).withEndAction(() -> {
            if (!gavetaAberta) gaveta.setVisibility(View.GONE);
        }).start();
    }

    @Override
    public void onBackPressed() {
        if (gavetaAberta) fecharGaveta();
        else if (tocandoAberto) fecharTocando();
        else if (secao == SECAO_COLECAO) irPara(secaoAnterior);
        else super.onBackPressed();
    }

    /** Arrastar da borda esquerda abre o menu; arrastar o menu para a esquerda fecha. */
    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                toqueX = gavetaAberta || (!tocandoAberto && e.getX() < dp(24)) ? e.getX() : -1;
                break;
            case MotionEvent.ACTION_MOVE:
                if (toqueX >= 0) {
                    float dx = e.getX() - toqueX;
                    if (!gavetaAberta && dx > dp(56)) {
                        toqueX = -1;
                        abrirGaveta();
                        return true;
                    }
                    if (gavetaAberta && dx < -dp(56)) {
                        toqueX = -1;
                        fecharGaveta();
                        return true;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                toqueX = -1;
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(e);
    }

    // ------------------------------------------------------------------ atualizacao do proprio app
    private void procurarAtualizacao(final boolean manual) {
        if (!Atualizador.disponivel()) return;
        new Thread(() -> {
            final int nova = Atualizador.maisNova();
            ui.post(() -> {
                if (isFinishing()) return;
                if (nova > Atualizador.instalada(this)) {
                    perguntarAtualizar();
                } else if (manual) {
                    Toast.makeText(this, nova < 0 ? "Não consegui verificar agora. Confira a internet." : "Você já está com a versão mais recente.", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void perguntarAtualizar() {
        new Construtor()
                .setTitle("Nova versão disponível")
                .setMessage("Atualizar agora? Suas músicas e curtidas continuam como estão.")
                .setPositiveButton("Atualizar", (d, q) -> baixarEInstalar())
                .setNegativeButton("Depois", null)
                .show();
    }

    private void baixarEInstalar() {
        if (!Atualizador.podeInstalar(this)) {
            new Construtor()
                    .setMessage("Para atualizar, permita que o Raccoon Music instale atualizações. Depois de permitir, volte e toque em Atualizar de novo.")
                    .setPositiveButton("Permitir", (d, q) -> Atualizador.pedirPermissao(this))
                    .setNegativeButton("Agora não", null)
                    .show();
            return;
        }
        final AlertDialog espera = new Construtor()
                .setMessage("Baixando a atualização… 0%")
                .setCancelable(false)
                .show();
        new Thread(() -> {
            final java.io.File apk = Atualizador.baixar(this, p -> ui.post(() -> espera.setMessage("Baixando a atualização… " + p + "%")));
            ui.post(() -> {
                espera.dismiss();
                if (apk == null) {
                    Toast.makeText(this, "Não consegui baixar a atualização. Tente de novo.", Toast.LENGTH_LONG).show();
                } else {
                    Atualizador.instalar(this, apk);
                }
            });
        }).start();
    }

    private void aplicarIconesIniciais() {
        icAleatorio.definir(IconeView.ALEATORIO, prefs.embaralhar() ? ACENTO : ICONE);
        int r = prefs.repetir();
        icRepetir.definir(r == Player.REPEAT_MODE_ONE ? IconeView.REPETIR_UM : IconeView.REPETIR, r == Player.REPEAT_MODE_OFF ? ICONE : ACENTO);
        icPlay.definir(IconeView.PLAY, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ a lista
    private static final class Linha {
        FrameLayout caixaCapa, celulaMais;
        ImageView capa;
        IconeView nota, coracao;
        TextView titulo, artista, duracao;
        int pos;
    }

    private void ligarCapa(final Linha l, Faixa f) {
        if (f == null) {
            l.capa.setTag(null);
            l.capa.setImageDrawable(null);
            l.nota.setVisibility(View.VISIBLE);
            return;
        }
        final String uri = f.uri;
        l.capa.setTag(uri);
        Bitmap b = capas.naMemoria(uri);
        l.capa.setImageBitmap(b);
        l.nota.setVisibility(b == null ? View.VISIBLE : View.GONE);
        if (b == null && !capas.sabeQueNaoTem(uri)) {
            capas.pedir(f, () -> uri.equals(l.capa.getTag()), bm -> {
                if (bm != null) {
                    l.capa.setImageBitmap(bm);
                    l.nota.setVisibility(View.GONE);
                }
            });
        }
    }

    private final class Adaptador extends BaseAdapter {
        @Override public int getCount() { return modoGrupos() ? grupos.size() : visiveis.size(); }
        @Override public Object getItem(int i) { return modoGrupos() ? grupos.get(i) : visiveis.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View v, ViewGroup pai) {
            final Linha l;
            if (v == null) {
                final Linha nova = new Linha();
                LinearLayout linha = new LinearLayout(MainActivity.this);
                linha.setGravity(Gravity.CENTER_VERTICAL);
                linha.setPadding(dp(14), dp(6), dp(2), dp(6));
                linha.setBackground(toque(null, new android.graphics.drawable.ColorDrawable(0xFF000000)));
                nova.caixaCapa = new FrameLayout(MainActivity.this);
                nova.caixaCapa.setBackground(forma(LINHA, 10, 0));
                nova.capa = new ImageView(MainActivity.this);
                nova.capa.setScaleType(ImageView.ScaleType.CENTER_CROP);
                nova.caixaCapa.addView(nova.capa, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                nova.nota = new IconeView(MainActivity.this, IconeView.NOTA);
                nova.nota.definir(IconeView.NOTA, SUAVE);
                nova.caixaCapa.addView(nova.nota, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
                linha.addView(nova.caixaCapa, new LinearLayout.LayoutParams(dp(54), dp(54)));
                LinearLayout col = new LinearLayout(MainActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setPadding(dp(12), 0, dp(6), 0);
                nova.titulo = texto(16, TEXTO, false);
                nova.artista = texto(13, SUAVE, false);
                col.addView(nova.titulo);
                col.addView(nova.artista);
                linha.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                nova.coracao = new IconeView(MainActivity.this, IconeView.CORACAO_CHEIO);
                nova.coracao.definir(IconeView.CORACAO_CHEIO, ACENTO);
                LinearLayout.LayoutParams pc = new LinearLayout.LayoutParams(dp(16), dp(16));
                pc.setMargins(dp(4), 0, dp(6), 0);
                linha.addView(nova.coracao, pc);
                nova.duracao = texto(12, SUAVE, false);
                nova.duracao.setGravity(Gravity.END);
                linha.addView(nova.duracao, new LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT));
                nova.celulaMais = new FrameLayout(MainActivity.this);
                IconeView mais = new IconeView(MainActivity.this, IconeView.PONTOS);
                mais.definir(IconeView.PONTOS, SUAVE);
                nova.celulaMais.addView(mais, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
                nova.celulaMais.setOnClickListener(x -> {
                    if (modoGrupos()) {
                        if (nova.pos < grupos.size()) opcoesDoGrupo(grupos.get(nova.pos));
                    } else if (nova.pos < visiveis.size()) {
                        opcoesDaFaixa(visiveis.get(nova.pos));
                    }
                });
                linha.addView(nova.celulaMais, new LinearLayout.LayoutParams(dp(42), dp(48)));
                linha.setTag(nova);
                v = linha;
                l = nova;
            } else {
                l = (Linha) v.getTag();
            }
            l.pos = i;
            if (modoGrupos()) {
                Grupo g = grupos.get(i);
                l.titulo.setText(g.titulo);
                l.titulo.setTextColor(TEXTO);
                l.artista.setText(g.sub);
                l.artista.setVisibility(View.VISIBLE);
                l.coracao.setVisibility(View.GONE);
                l.duracao.setVisibility(View.GONE);
                arredondar(l.caixaCapa, secao == SECAO_ARTISTAS ? -1 : 10);
                ligarCapa(l, g.faixas.isEmpty() ? null : g.faixas.get(0));
                return v;
            }
            Faixa f = visiveis.get(i);
            l.titulo.setText(f.titulo);
            int vezes = historico.vezes(f.chave);
            String detalhe = secao == SECAO_MAIS && vezes > 0 ? (f.artista.isEmpty() ? "" : f.artista + "  ·  ") + vezes + (vezes == 1 ? " vez" : " vezes") : f.artista;
            l.artista.setText(detalhe);
            l.artista.setVisibility(detalhe.isEmpty() ? View.GONE : View.VISIBLE);
            MediaItem mi = player == null ? null : player.getCurrentMediaItem();
            boolean tocando = mi != null && f.uri.equals(mi.mediaId);
            l.titulo.setTextColor(tocando ? ACENTO : TEXTO);
            l.coracao.setVisibility(curtidas.contains(f.chave) ? View.VISIBLE : View.GONE);
            long dur = etiquetas.duracao(f.uri);
            l.duracao.setText(dur > 0 ? formatar(dur) : "");
            l.duracao.setVisibility(View.VISIBLE);
            arredondar(l.caixaCapa, 10);
            ligarCapa(l, f);
            return v;
        }
    }
}
