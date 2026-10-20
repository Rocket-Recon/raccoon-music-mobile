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
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
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
    private static final int ACENTO = 0xFFE9532E, FUNDO = 0xFF18181B, CARTAO = 0xFF222228, LINHA = 0xFF2C2C36;
    private static final int TEXTO = 0xFFFFFFFF, SUAVE = 0xFF9A9AA6, ICONE = 0xFFC8C8D0;
    private static final int PEDIDO_PASTA = 11, PEDIDO_NOTIFICACAO = 12;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Random sorteio = new Random();
    private Preferencias prefs;

    private List<Faixa> todas = new ArrayList<>();
    private List<Faixa> visiveis = new ArrayList<>();
    private final Map<String, Faixa> porUri = new HashMap<>();
    private final Map<String, Integer> posicao = new HashMap<>();
    private Set<String> curtidas;
    private boolean soCurtidas = false;
    private boolean varrendo = false;
    private boolean modoArtistas = false;
    private List<String[]> artistasVisiveis = new ArrayList<>();
    private int errosSeguidos = 0;
    private Sincronizacao sincronizacao = new Sincronizacao();
    private final Map<String, Faixa> porChave = new HashMap<>();
    private Runnable desligar;
    private int achadasAgora = 0;

    private MediaController player;
    private ListenableFuture<MediaController> futuro;
    private boolean arrastando = false;
    private byte[] ultimaArte;
    private long ultimoSalvo = 0;

    private TextView lblContagem, lblVazio, lblTitulo, lblArtista, lblPos, lblDur, btnCurtidas, btnPasta, btnAleatorio, btnArtistas;
    private EditText edtBusca;
    private ListView lista;
    private Adaptador adaptador;
    private ImageView capa;
    private SeekBar barra;
    private IconeView icCurtir, icAleatorio, icAnterior, icPlay, icProxima, icRepetir;

    // ------------------------------------------------------------------ ciclo de vida
    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);
        getWindow().setStatusBarColor(FUNDO);
        getWindow().setNavigationBarColor(CARTAO);
        prefs = new Preferencias(this);
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
                getContentResolver().takePersistableUriPermission(arvore, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception e) {
                // alguns seletores nao deixam guardar a permissao: vale so ate fechar o app
            }
            prefs.arvore(arvore.toString());
            atualizarBiblioteca();
        }
    }

    // ------------------------------------------------------------------ biblioteca
    private void escolherPasta() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
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
        restaurar();
    }

    private void aplicarFiltro() {
        String texto = edtBusca == null ? "" : edtBusca.getText().toString();
        visiveis = Catalogo.filtrar(todas, texto, curtidas, soCurtidas);
        artistasVisiveis = Catalogo.artistas(soCurtidas ? visiveis : todas, texto);
        if (adaptador != null) adaptador.notifyDataSetChanged();
        int n = todas.size();
        String total = n == 1 ? "1 música" : n + " músicas";
        lblContagem.setText(visiveis.size() == n ? total : visiveis.size() + " de " + total);
        atualizarVazio();
    }

    private void atualizarVazio() {
        if (lblVazio == null) return;
        if (varrendo) {
            lblVazio.setText(achadasAgora > 0 ? "Procurando músicas… " + achadasAgora : "Procurando músicas…");
            lblVazio.setVisibility(visiveis.isEmpty() ? View.VISIBLE : View.GONE);
        } else if (todas.isEmpty()) {
            lblVazio.setText(prefs.arvore() == null ? "Escolha a pasta das suas músicas" : "Nenhuma música por aqui");
            lblVazio.setVisibility(View.VISIBLE);
        } else if (modoArtistas ? artistasVisiveis.isEmpty() : visiveis.isEmpty()) {
            lblVazio.setText(soCurtidas && edtBusca.getText().length() == 0 ? "Você ainda não curtiu nenhuma música" : "Nada encontrado");
            lblVazio.setVisibility(View.VISIBLE);
        } else {
            lblVazio.setVisibility(View.GONE);
        }
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
                if (eventos.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) salvarUltimo();
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
        if (player == null || player.getMediaItemCount() == 0) return;
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
        if (soCurtidas) aplicarFiltro(); else adaptador.notifyDataSetChanged();
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
        icPlay.definir(tocando ? IconeView.PAUSE : IconeView.PLAY, TEXTO);
        if (mi == null) {
            lblTitulo.setText("Nada tocando");
            lblArtista.setText("");
            capa.setImageBitmap(null);
            ultimaArte = null;
            icCurtir.setVisibility(View.INVISIBLE);
            return;
        }
        Faixa f = porUri.get(mi.mediaId);
        CharSequence titulo = f != null ? f.titulo : player.getMediaMetadata().title;
        CharSequence artista = f != null ? f.artista : player.getMediaMetadata().artist;
        lblTitulo.setText(titulo == null ? "" : titulo);
        lblArtista.setText(artista == null ? "" : artista);
        if (f != null) {
            icCurtir.setVisibility(View.VISIBLE);
            icCurtir.definir(curtidas.contains(f.chave) ? IconeView.CORACAO_CHEIO : IconeView.CORACAO, curtidas.contains(f.chave) ? ACENTO : ICONE);
        }
        byte[] arte = player.getMediaMetadata().artworkData;
        if (arte != ultimaArte) {
            ultimaArte = arte;
            capa.setImageBitmap(arte == null ? null : decodificar(arte));
        }
        adaptador.notifyDataSetChanged();  // destaca a musica que toca na lista
    }

    private Bitmap decodificar(byte[] dados) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(dados, 0, dados.length, o);
            int lado = Math.max(1, Math.min(o.outWidth, o.outHeight));
            o.inSampleSize = Math.max(1, lado / 256);
            o.inJustDecodeBounds = false;
            return BitmapFactory.decodeByteArray(dados, 0, dados.length, o);
        } catch (Exception e) {
            return null;
        }
    }

    private void salvarUltimo() {
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
                } else if (dur <= 0 && !arrastando) {
                    barra.setProgress(0);
                    lblPos.setText("0:00");
                    lblDur.setText("0:00");
                }
                long agora = System.currentTimeMillis();
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
    private void menuDaFaixa(int pos) {
        if (pos < 0 || pos >= visiveis.size()) return;
        final Faixa f = visiveis.get(pos);
        final boolean curtida = curtidas.contains(f.chave);
        String[] opcoes = {"Tocar a seguir", "Adicionar à fila", curtida ? "Descurtir" : "Curtir"};
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(f.titulo)
                .setItems(opcoes, (d, qual) -> {
                    if (qual == 2) {
                        alternarCurtida(f);
                    } else if (player == null || player.getMediaItemCount() == 0) {
                        List<Faixa> so = new ArrayList<>();
                        so.add(f);
                        tocarFila(so, 0);
                    } else if (qual == 0) {
                        player.addMediaItem(player.getCurrentMediaItemIndex() + 1, item(f));
                        Toast.makeText(this, "Vai tocar a seguir", Toast.LENGTH_SHORT).show();
                    } else {
                        player.addMediaItem(item(f));
                        Toast.makeText(this, "Adicionada à fila", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void menuDaPasta() {
        if (prefs.arvore() == null) {
            escolherPasta();
            return;
        }
        final List<String> nomes = new ArrayList<>();
        final List<Runnable> acoes = new ArrayList<>();
        if (!sincronizacao.playlists.isEmpty()) {
            nomes.add("Playlists do PC");
            acoes.add(this::menuDasPlaylists);
        }
        nomes.add("Atualizar a biblioteca");
        acoes.add(this::atualizarBiblioteca);
        nomes.add("Escolher outra pasta");
        acoes.add(this::escolherPasta);
        nomes.add(desligar == null ? "Temporizador" : "Cancelar o temporizador");
        acoes.add(this::menuDoTemporizador);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setItems(nomes.toArray(new String[0]), (d, qual) -> acoes.get(qual).run())
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

    private void menuDasPlaylists() {
        final List<String> nomes = new ArrayList<>(sincronizacao.playlists.keySet());
        String[] rotulos = new String[nomes.size()];
        for (int i = 0; i < rotulos.length; i++) {
            rotulos[i] = nomes.get(i) + " (" + Sincronizacao.faixasDaPlaylist(sincronizacao.playlists.get(nomes.get(i)), porChave).size() + ")";
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Playlists do PC")
                .setItems(rotulos, (d, qual) -> {
                    List<Faixa> faixas = Sincronizacao.faixasDaPlaylist(sincronizacao.playlists.get(nomes.get(qual)), porChave);
                    if (faixas.isEmpty()) {
                        Toast.makeText(this, "Nenhuma música dessa playlist está na pasta.", Toast.LENGTH_LONG).show();
                    } else if (prefs.embaralhar()) {
                        tocarFila(Catalogo.embaralhar(faixas, -1, sorteio), 0);
                    } else {
                        tocarFila(faixas, 0);
                    }
                })
                .show();
    }

    private void alternarArtistas() {
        modoArtistas = !modoArtistas;
        btnArtistas.setText(modoArtistas ? "Músicas" : "Artistas");
        btnArtistas.setBackground(modoArtistas ? forma(ACENTO, 24, 0) : forma(CARTAO, 24, LINHA));
        adaptador.notifyDataSetChanged();
        lista.setSelection(0);
        atualizarVazio();
    }

    /** Tocou numa banda: volta para as musicas, so dela. */
    private void escolherArtista(int pos) {
        if (pos < 0 || pos >= artistasVisiveis.size()) return;
        String nome = artistasVisiveis.get(pos)[0];
        modoArtistas = false;
        btnArtistas.setText("Artistas");
        btnArtistas.setBackground(forma(CARTAO, 24, LINHA));
        edtBusca.setText(nome);  // o texto da pesquisa refaz a lista (aplicarFiltro)
        edtBusca.setSelection(edtBusca.getText().length());
        lista.setSelection(0);
    }

    private void menuDoTemporizador() {
        if (desligar != null) {
            ui.removeCallbacks(desligar);
            desligar = null;
            Toast.makeText(this, "Temporizador cancelado", Toast.LENGTH_SHORT).show();
            return;
        }
        final int[] minutos = {15, 30, 45, 60, 90};
        String[] nomes = new String[minutos.length];
        for (int i = 0; i < minutos.length; i++) nomes[i] = minutos[i] + " minutos";
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Pausar a música em")
                .setItems(nomes, (d, qual) -> {
                    desligar = () -> {
                        desligar = null;
                        try {
                            if (player != null && player.isConnected()) player.pause();
                        } catch (Exception e) {
                            // o app ja foi fechado: nada a pausar daqui
                        }
                    };
                    ui.postDelayed(desligar, minutos[qual] * 60_000L);
                    Toast.makeText(this, "A música pausa em " + minutos[qual] + " minutos", Toast.LENGTH_SHORT).show();
                })
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

    private TextView botao(String rotulo, boolean destaque, Runnable acao) {
        TextView b = texto(15, TEXTO, true);
        b.setText(rotulo);
        b.setGravity(Gravity.CENTER);
        b.setBackground(destaque ? forma(ACENTO, 24, 0) : forma(CARTAO, 24, LINHA));
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

    private View montarTela() {
        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundColor(FUNDO);

        // cabecalho
        LinearLayout cab = new LinearLayout(this);
        cab.setOrientation(LinearLayout.VERTICAL);
        cab.setPadding(dp(16), dp(14), dp(16), dp(6));
        TextView titulo = texto(24, TEXTO, true);
        titulo.setText("Raccoon Music");
        lblContagem = texto(13, SUAVE, false);
        cab.addView(titulo);
        cab.addView(lblContagem);
        raiz.addView(cab);

        // botoes
        LinearLayout botoes = new LinearLayout(this);
        botoes.setPadding(dp(12), dp(6), dp(12), dp(6));
        btnAleatorio = botao("Aleatório", true, this::tocarAleatorio);
        btnCurtidas = botao("Curtidas", false, () -> {
            soCurtidas = !soCurtidas;
            btnCurtidas.setBackground(soCurtidas ? forma(ACENTO, 24, 0) : forma(CARTAO, 24, LINHA));
            aplicarFiltro();
        });
        btnArtistas = botao("Artistas", false, this::alternarArtistas);
        btnPasta = botao("Pasta", false, this::menuDaPasta);
        for (TextView b : new TextView[]{btnAleatorio, btnArtistas, btnCurtidas, btnPasta}) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1f);
            p.setMargins(dp(4), 0, dp(4), 0);
            botoes.addView(b, p);
        }
        raiz.addView(botoes);

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
            @Override public void afterTextChanged(Editable s) { aplicarFiltro(); }
        });
        edtBusca.setOnEditorActionListener((v, acao, ev) -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            return true;
        });
        LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        pb.setMargins(dp(16), dp(4), dp(16), dp(8));
        raiz.addView(edtBusca, pb);

        // lista
        FrameLayout meio = new FrameLayout(this);
        lista = new ListView(this);
        lista.setDivider(null);
        lista.setSelector(android.R.color.transparent);
        lista.setFastScrollEnabled(true);
        adaptador = new Adaptador();
        lista.setAdapter(adaptador);
        lista.setOnItemClickListener((p, v, pos, id) -> {
            if (modoArtistas) escolherArtista(pos); else tocarDaLista(pos);
        });
        lista.setOnItemLongClickListener((p, v, pos, id) -> {
            if (modoArtistas) return false;
            menuDaFaixa(pos);
            return true;
        });
        meio.addView(lista, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        lblVazio = texto(16, SUAVE, false);
        lblVazio.setGravity(Gravity.CENTER);
        meio.addView(lblVazio, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        raiz.addView(meio, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // player
        LinearLayout painel = new LinearLayout(this);
        painel.setOrientation(LinearLayout.VERTICAL);
        float r = dp(20);
        GradientDrawable fundoPainel = new GradientDrawable();
        fundoPainel.setColor(CARTAO);
        fundoPainel.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        painel.setBackground(fundoPainel);
        painel.setPadding(dp(16), dp(14), dp(16), dp(6));

        LinearLayout atual = new LinearLayout(this);
        atual.setGravity(Gravity.CENTER_VERTICAL);
        capa = new ImageView(this);
        capa.setScaleType(ImageView.ScaleType.CENTER_CROP);
        capa.setBackground(forma(LINHA, 10, 0));
        atual.addView(capa, new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout nomes = new LinearLayout(this);
        nomes.setOrientation(LinearLayout.VERTICAL);
        nomes.setPadding(dp(12), 0, dp(8), 0);
        lblTitulo = texto(17, TEXTO, true);
        lblTitulo.setText("Nada tocando");
        lblArtista = texto(14, SUAVE, false);
        nomes.addView(lblTitulo);
        nomes.addView(lblArtista);
        atual.addView(nomes, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        FrameLayout celulaCurtir = new FrameLayout(this);
        icCurtir = icone(IconeView.CORACAO, 28, this::curtirAtual, celulaCurtir);
        icCurtir.setVisibility(View.INVISIBLE);
        atual.addView(celulaCurtir, new LinearLayout.LayoutParams(dp(52), dp(52)));
        painel.addView(atual);

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
        painel.addView(tempos);

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
        painel.addView(controles);
        raiz.addView(painel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        aplicarIconesIniciais();
        return raiz;
    }

    private void aplicarIconesIniciais() {
        icAleatorio.definir(IconeView.ALEATORIO, prefs.embaralhar() ? ACENTO : ICONE);
        int r = prefs.repetir();
        icRepetir.definir(r == Player.REPEAT_MODE_ONE ? IconeView.REPETIR_UM : IconeView.REPETIR, r == Player.REPEAT_MODE_OFF ? ICONE : ACENTO);
        icPlay.definir(IconeView.PLAY, TEXTO);
    }

    // ------------------------------------------------------------------ a lista
    private static final class Linha {
        final TextView titulo, artista;
        final IconeView coracao;

        Linha(TextView titulo, TextView artista, IconeView coracao) {
            this.titulo = titulo;
            this.artista = artista;
            this.coracao = coracao;
        }
    }

    private final class Adaptador extends BaseAdapter {
        @Override public int getCount() { return modoArtistas ? artistasVisiveis.size() : visiveis.size(); }
        @Override public Object getItem(int i) { return modoArtistas ? artistasVisiveis.get(i) : visiveis.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View v, ViewGroup pai) {
            Linha l;
            if (v == null) {
                LinearLayout linha = new LinearLayout(MainActivity.this);
                linha.setGravity(Gravity.CENTER_VERTICAL);
                linha.setPadding(dp(16), dp(8), dp(16), dp(8));
                linha.setMinimumHeight(dp(62));
                LinearLayout col = new LinearLayout(MainActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                TextView t = texto(16, TEXTO, false);
                TextView a = texto(13, SUAVE, false);
                col.addView(t);
                col.addView(a);
                linha.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                IconeView c = new IconeView(MainActivity.this, IconeView.CORACAO_CHEIO);
                LinearLayout.LayoutParams pc = new LinearLayout.LayoutParams(dp(20), dp(20));
                pc.setMargins(dp(8), 0, 0, 0);
                linha.addView(c, pc);
                l = new Linha(t, a, c);
                linha.setTag(l);
                v = linha;
            } else {
                l = (Linha) v.getTag();
            }
            if (modoArtistas) {
                String[] b = artistasVisiveis.get(i);
                l.titulo.setText(b[0]);
                l.titulo.setTextColor(TEXTO);
                l.artista.setText("1".equals(b[1]) ? "1 música" : b[1] + " músicas");
                l.artista.setVisibility(View.VISIBLE);
                l.coracao.setVisibility(View.GONE);
                return v;
            }
            Faixa f = visiveis.get(i);
            l.titulo.setText(f.titulo);
            l.artista.setText(f.artista);
            l.artista.setVisibility(f.artista.isEmpty() ? View.GONE : View.VISIBLE);
            MediaItem mi = player == null ? null : player.getCurrentMediaItem();
            boolean tocando = mi != null && f.uri.equals(mi.mediaId);
            l.titulo.setTextColor(tocando ? ACENTO : TEXTO);
            l.coracao.definir(IconeView.CORACAO_CHEIO, ACENTO);
            l.coracao.setVisibility(curtidas.contains(f.chave) ? View.VISIBLE : View.GONE);
            return v;
        }
    }
}
