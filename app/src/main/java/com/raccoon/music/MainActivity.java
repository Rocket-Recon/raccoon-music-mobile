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
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewOutlineProvider;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
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
    private static final int SECAO_INICIO = 0, SECAO_MUSICAS = 1, SECAO_ARTISTAS = 2, SECAO_ALBUNS = 3, SECAO_GENEROS = 4, SECAO_PLAYLISTS = 5,
            SECAO_CURTIDAS = 6, SECAO_RECENTES = 7, SECAO_MAIS = 8, SECAO_COLECAO = 9;
    private static final String[] NOMES_SECAO = {"Início", "Biblioteca", "Artistas", "Álbuns", "Gêneros", "Playlists", "Curtidas", "Tocadas recentemente", "Mais tocadas", "Lista"};
    private static final int SECOES_NO_MENU = 9;
    private int secao = SECAO_INICIO;
    private int secaoAnterior = SECAO_INICIO;
    private String planoConta = "";
    private String estadoLicenca = "";
    private long ultimaVerificacao;
    private View telaBloqueio;
    private LinearLayout blocoEntrada, blocoSemLicenca;
    private EditText bloqueioUsuario, bloqueioSenha;
    private TextView lblBloqueio;
    private TextView lblConta, itemConta;
    private ScrollView inicio;
    private LinearLayout cartaoBaixar;
    private LinearLayout conteudoInicio;
    private FrameLayout caixaDaBusca;
    private String colecaoTitulo = "";
    private List<Faixa> colecaoFaixas = new ArrayList<>();
    private String colecaoPlaylist = null;  // nome da playlist do celular que esta aberta (para poder tirar musicas dela)
    private View cortina, gaveta;
    private boolean gavetaAberta = false;
    private float toqueX = -1;
    private final View[] linhasSecao = new View[SECOES_NO_MENU];
    private TextView itemTemporizador, itemVersao;
    private boolean pararNoFim, pedirAleatorioAoAbrir;
    private static final String ATALHO_ALEATORIO = "com.raccoon.music.TOCAR_ALEATORIO";
    private long desligarEm;
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
    private long ajusteLetraMs = 0;
    private static final int TAMANHO_LETRA_PADRAO = 22, TAMANHO_LETRA_MIN = 14, TAMANHO_LETRA_MAX = 40;
    private static final long PASSO_AJUSTE_LETRA_MS = 250;
    private volatile String uriAtual = "";
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

    private TextView lblContagem, lblVazio, lblTitulo, lblArtista, lblPos, lblDur, lblSecao, lblTituloMini, lblArtistaMini, lblProxima, lblTamanhoLetra, lblAjusteLetra;
    private LinearLayout mini;
    private ProgressBar barraMini;
    private ImageView capaMini;
    private IconeView notaMini, notaGrande, icPlayMini, icProximaMini, icMenu, icLetra;
    private FrameLayout celulaMais, celulaAleatorio;
    private LinearLayout linhaControles;
    private TextView bSeta;
    private TextView btnVazio, bOrdem, bFiltro, lblGaveta;
    private int filtroMusicas = 0;  // 0 todas, 1 curtidas, 2 nunca tocadas, 3 já tocadas
    private FrameLayout limparBusca;
    private View telaTocando;
    private LetraView letraView;
    private LinearLayout blocoLetra, linhaAjusteLetra, linhaDoTitulo;
    private TextView tituloTopo;
    private IconeView icCurtirTopo;
    private FrameLayout celulaCurtirTopo;
    private FrameLayout caixaDaCapa;
    private EditText edtBusca;
    private ListView lista;
    private GridView grade;
    private Adaptador adaptador;
    private AdaptadorGrade adaptadorGrade;
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

    /** Albuns, generos e playlists aparecem como grade de capas; artistas ficam em lista. */
    private boolean modoGrade() {
        return secao == SECAO_ALBUNS || secao == SECAO_GENEROS || secao == SECAO_PLAYLISTS;
    }

    private boolean modoGrupos() {
        return secao == SECAO_ARTISTAS || secao == SECAO_ALBUNS || secao == SECAO_GENEROS || secao == SECAO_PLAYLISTS;
    }

    // ------------------------------------------------------------------ ciclo de vida
    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);
        androidx.core.content.ContextCompat.registerReceiver(this, aoBaixar, new android.content.IntentFilter(BaixarService.ACAO_FIM), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        FilaDeDownload.ouvir(aoMudarFila);
        prefs = new Preferencias(this);
        aplicarPaleta();
        historico = Historico.ler(prefs.historico());
        minhas = Playlists.ler(prefs.playlists());
        etiquetas = Etiquetas.ler(Arquivos.ler(this, "etiquetas.tsv"));
        capas = new Capas(this);
        curtidas = prefs.curtidas();
        setContentView(montarTela());
        aplicarConta();
        atualizarConta();

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
            executarAtalho();
        }, command -> ui.post(command));
        tratarAtalho(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intencao) {
        super.onNewIntent(intencao);
        setIntent(intencao);
        tratarAtalho(intencao);
    }

    /** O atalho do icone do app ("Tocar em aleatorio"): guarda o pedido e toca assim que a biblioteca e o player estiverem prontos. */
    private void tratarAtalho(Intent intencao) {
        if (intencao != null && ATALHO_ALEATORIO.equals(intencao.getAction())) {
            pedirAleatorioAoAbrir = true;
            setIntent(new Intent(Intent.ACTION_MAIN));  // uma troca de cor (recreate) nao pode sortear de novo
            executarAtalho();
        }
    }

    private void executarAtalho() {
        if (!pedirAleatorioAoAbrir || player == null || todas.isEmpty()) return;
        pedirAleatorioAoAbrir = false;
        prefs.embaralhar(true);
        atualizarIcones();
        tocarFila(Catalogo.embaralhar(todas, -1, sorteio), 0);
    }

    @Override
    protected void onStart() {
        super.onStart();
        ui.post(relogio);
        if (Conta.logado(this) && System.currentTimeMillis() - ultimaVerificacao > 10 * 60 * 1000L) atualizarConta();
    }

    @Override
    protected void onStop() {
        ui.removeCallbacks(relogio);
        salvarUltimo();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        PlaybackService.aoAlternarAleatorio = null;
        FilaDeDownload.naoOuvir(aoMudarFila);
        try {
            unregisterReceiver(aoBaixar);
        } catch (Exception jaSolto) {
            // o receptor ja nao estava registrado
        }
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
        tirarDaFilaOQueSumiu();
        aplicarFiltro();
        atualizarCabecalho();
        if (lblGaveta != null) lblGaveta.setText(faixas.size() == 1 ? "1 música" : faixas.size() + " músicas");
        restaurar();
        executarAtalho();
        atualizarPlayer();
        iniciarIndexacao();
    }

    /** Músicas apagadas ou que sumiram da pasta saem da fila do player (o arquivo aberto continuaria tocando). */
    private void tirarDaFilaOQueSumiu() {
        if (player == null || !player.isConnected()) return;
        for (int i = player.getMediaItemCount() - 1; i >= 0; i--) {
            if (!porUri.containsKey(player.getMediaItemAt(i).mediaId)) player.removeMediaItem(i);
        }
        if (player.getMediaItemCount() == 0) {
            player.stop();
            player.clearMediaItems();
        }
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
            if (secao == SECAO_MUSICAS && filtroMusicas != 0) {
                List<Faixa> passam = new ArrayList<>();
                for (Faixa f : visiveis) {
                    boolean curtida = curtidas.contains(f.chave);
                    int vezes = historico.vezes(f.chave);
                    if ((filtroMusicas == 1 && curtida) || (filtroMusicas == 2 && vezes == 0) || (filtroMusicas == 3 && vezes > 0)) passam.add(f);
                }
                visiveis = passam;
            }
            if ((secao == SECAO_MUSICAS || secao == SECAO_CURTIDAS) && (prefs.ordem() != 0 || prefs.ordemInvertida())) {
                visiveis = Catalogo.ordenarPor(visiveis, MODO_DA_ORDEM[Math.max(0, Math.min(prefs.ordem(), MODO_DA_ORDEM.length - 1))], f -> etiquetas.duracao(f.uri));
                if (prefs.ordemInvertida()) java.util.Collections.reverse(visiveis);
            }
        }
        if (adaptador != null) adaptador.notifyDataSetChanged();
        if (adaptadorGrade != null) adaptadorGrade.notifyDataSetChanged();
        boolean naInicio = secao == SECAO_INICIO && texto.isEmpty();
        if (grade != null && lista != null) {
            grade.setVisibility(!naInicio && modoGrade() ? View.VISIBLE : View.GONE);
            lista.setVisibility(!naInicio && !modoGrade() ? View.VISIBLE : View.GONE);
        }
        if (inicio != null) {
            inicio.setVisibility(naInicio ? View.VISIBLE : View.GONE);
            if (naInicio) montarConteudoInicio();
        }
        atualizarContagem();
        atualizarVazio();
        rotularBarra();
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
        if (secao == SECAO_INICIO && Conta.logado(this) && !planoConta.isEmpty()) s = planoConta + "  ·  " + s;
        if (indexando && aIndexar > 0) s += "  ·  lendo informações " + Math.min(indexadas, aIndexar) + "/" + aIndexar;
        if (varrendo) s += "  ·  procurando músicas…";
        lblContagem.setText(s);
    }

    private void atualizarVazio() {
        if (lblVazio == null) return;
        boolean semBusca = edtBusca == null || edtBusca.getText().length() == 0;
        if (secao == SECAO_INICIO && semBusca) {
            // a página inicial tem o próprio aviso de biblioteca vazia e continua igual com ou sem músicas
            lblVazio.setVisibility(View.GONE);
            if (btnVazio != null) btnVazio.setVisibility(View.GONE);
            if (todas.isEmpty() && inicio != null && inicio.getVisibility() == View.VISIBLE) montarConteudoInicio();
            return;
        }
        if (btnVazio != null) btnVazio.setVisibility(todas.isEmpty() && !varrendo ? View.VISIBLE : View.GONE);
        boolean vazia = modoGrupos() ? grupos.isEmpty() : visiveis.isEmpty();
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
        PlaybackService.aoAlternarAleatorio = this::alternarAleatorio;
        player.addListener(new Player.Listener() {
            @Override
            public void onRepeatModeChanged(int modo) {
                prefs.repetir(modo);
                atualizarIcones();
            }
        });
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

    /** Modo de Catalogo.ordenarPor para cada ordem do menu: banda, título e adicionadas recentemente. */
    private static final int[] MODO_DA_ORDEM = {0, 1, 3};
    private static final String[] ORDENS_CURTAS = {"banda", "título", "recentes"};
    private static final String[] NOMES_FILTRO = {"Todas", "Curtidas", "Nunca tocadas", "Já tocadas"};

    /** Escreve nos dois botões da barra a ordem e o filtro de agora. */
    private void rotularBarra() {
        if (bOrdem != null) bOrdem.setText("Ordem: " + ORDENS_CURTAS[Math.max(0, Math.min(prefs.ordem(), ORDENS_CURTAS.length - 1))]);
        if (bSeta != null) {
            boolean naturalDecrescente = prefs.ordem() == 2;  // adicionadas recentemente: as mais novas primeiro
            boolean decrescente = naturalDecrescente != prefs.ordemInvertida();
            bSeta.setText(decrescente ? "↓" : "↑");
        }
        if (bFiltro != null) {
            bFiltro.setText(filtroMusicas == 0 ? "Filtrar" : NOMES_FILTRO[filtroMusicas]);
            bFiltro.setBackground(toque(forma(filtroMusicas == 0 ? CARTAO : ACENTO, 24, TEXTO), forma(0xFF000000, 24, 0)));
            bFiltro.setTextColor(filtroMusicas == 0 ? TEXTO : 0xFFFFFFFF);
        }
    }

    private void menuFiltrar() {
        new Construtor()
                .setTitle("Mostrar")
                .setSingleChoiceItems(NOMES_FILTRO, filtroMusicas, (d, qual) -> {
                    filtroMusicas = qual;
                    d.dismiss();
                    aplicarFiltro();
                    if (lista != null) lista.setSelection(0);
                })
                .show();
    }

    private void menuOrdenar() {
        String[] nomes = {"Por banda", "Por título", "Adicionadas recentemente"};
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
        PlaybackService.atualizarBotoes();
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
            ajustarTopoDoPlayer();
            icCurtir.setVisibility(View.INVISIBLE);
            atualizarCapas(null, null);
            return;
        }
        uriAtual = mi.mediaId;
        Faixa f = porUri.get(mi.mediaId);
        CharSequence titulo = f != null ? f.titulo : player.getMediaMetadata().title;
        CharSequence artista = f != null ? f.artista : player.getMediaMetadata().artist;
        lblTitulo.setText(titulo == null ? "" : titulo);
        String linhaArtista = artista == null ? "" : artista.toString();
        lblArtista.setText(linhaArtista);
        lblProxima.setVisibility(View.GONE);
        lblTituloMini.setText(titulo == null ? "" : titulo);
        lblArtistaMini.setText(artista == null ? "" : artista);
        lblArtistaMini.setVisibility(artista == null || artista.length() == 0 ? View.GONE : View.VISIBLE);
        if (f != null) {
            icCurtir.setVisibility(View.VISIBLE);
            icCurtir.definir(curtidas.contains(f.chave) ? IconeView.CORACAO_CHEIO : IconeView.CORACAO, curtidas.contains(f.chave) ? ACENTO : ICONE);
        }
        atualizarCapas(f, player.getMediaMetadata().artworkData);
        ajustarTopoDoPlayer();
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

    /** Enquanto a letra está aberta, acompanha a música em passos curtos para a linha cantada trocar na hora. */
    private final Runnable seguirLetra = new Runnable() {
        @Override
        public void run() {
            if (!letraAberta) return;
            if (player != null && tocandoAberto) desenharLrc(player.getCurrentPosition());
            ui.postDelayed(this, 120);
        }
    };

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
                long agora = System.currentTimeMillis();
                if (desligarEm > 0 && desligarEm - agora <= 15_000 && desligarEm - agora > 0) {
                    player.setVolume((desligarEm - agora) / 15_000f);  // a musica some aos poucos antes de pausar
                } else if (desligarEm == 0 && player.getVolume() < 1f) {
                    player.setVolume(1f);  // o volume nunca fica baixo depois de um temporizador cancelado
                }
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
        nomes.add("Selecionar");
        acoes.add(() -> iniciarSelecao(f));
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
        nomes.add("Excluir");
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

    /** Grava na pasta das musicas as curtidas e as playlists feitas aqui; o PC soma isso em "Celular > Trazer do celular". */
    private void enviarAoPc() {
        final String arvore = prefs.arvore();
        if (arvore == null) {
            Toast.makeText(this, "Escolha primeiro a pasta das músicas.", Toast.LENGTH_SHORT).show();
            return;
        }
        final Map<String, List<String>> locais = new java.util.LinkedHashMap<>();
        for (String nome : minhas.nomes()) locais.put(nome, minhas.chaves(nome));
        final Set<String> likes = new java.util.LinkedHashSet<>(curtidas);
        final String texto = Sincronizacao.montar(likes, locais);
        if (texto.isEmpty()) {
            Toast.makeText(this, "Você ainda não curtiu nem criou playlists por aqui.", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            final boolean deu = Biblioteca.gravarNaRaiz(getContentResolver(), Uri.parse(arvore), Sincronizacao.ARQUIVO_PARA_O_PC, texto);
            ui.post(() -> Toast.makeText(this, deu
                    ? "Enviado: " + plural(likes.size(), "curtida", "curtidas") + " e " + plural(locais.size(), "playlist", "playlists") + ". No PC: Celular > Trazer do celular."
                    : "Não consegui gravar na pasta. Se o pendrive estiver protegido, escolha a pasta de novo no menu.", Toast.LENGTH_LONG).show());
        }).start();
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
            linhaDeInfo(col, "Ano", Etiquetas.anoLegivel(inf.ano));
            linhaDeInfo(col, "Duração", inf.duracaoMs > 0 ? formatar(inf.duracaoMs) : "");
        }
        linhaDeInfo(col, "Reproduções", vezes == 0 ? "Ainda não tocou" : plural(vezes, "vez", "vezes"));
        ScrollView rolagem = new ScrollView(this);
        rolagem.addView(col);
        new Construtor().setTitle("Informações").setView(rolagem).setPositiveButton("Fechar", null).show();
    }

    /** Com a letra aberta, "Banda - Título" vai para o topo (e o coração junto) e o espaço da linha do título volta para a letra. */
    private void ajustarTopoDoPlayer() {
        if (tituloTopo == null) return;
        Faixa f = faixaAtual();
        String nome = "Tocando agora";
        if (letraAberta && f != null) nome = f.artista.isEmpty() ? f.titulo : f.artista + " - " + f.titulo;
        tituloTopo.setText(nome);
        tituloTopo.setTextColor(letraAberta ? TEXTO : SUAVE);
        tituloTopo.setSelected(true);
        linhaDoTitulo.setVisibility(letraAberta ? View.GONE : View.VISIBLE);
        celulaCurtirTopo.setVisibility(letraAberta ? View.VISIBLE : View.GONE);
        icCurtirTopo.setVisibility(letraAberta ? View.VISIBLE : View.GONE);
        if (f != null) icCurtirTopo.definir(curtidas.contains(f.chave) ? IconeView.CORACAO_CHEIO : IconeView.CORACAO, curtidas.contains(f.chave) ? ACENTO : ICONE);
    }

    private void alternarLetra() {
        letraAberta = !letraAberta;
        blocoLetra.setVisibility(letraAberta ? View.VISIBLE : View.GONE);
        caixaDaCapa.setVisibility(letraAberta ? View.GONE : View.VISIBLE);
        if (linhaControles != null) linhaControles.setVisibility(letraAberta ? View.GONE : View.VISIBLE);  // com a letra aberta, o espaço dos botões vai para o texto
        icLetra.definir(IconeView.LETRA, letraAberta ? ACENTO : ICONE);
        telaTocando.setKeepScreenOn(letraAberta);  // lendo a letra, a tela nao apaga
        ajustarTopoDoPlayer();
        ui.removeCallbacks(seguirLetra);
        if (letraAberta) {
            carregarLetraAtual();
            ui.post(seguirLetra);
        }
    }

    private void carregarLetraAtual() {
        final Faixa f = faixaAtual();
        if (f == null) return;
        letraDe = f.uri;
        lrcAtual = null;
        ajusteLetraMs = 0;
        pintarOpcoesDaLetra();
        String guardada = Letras.guardada(this, f);
        if (guardada != null) {
            exibirLetra(guardada, Letras.sincronizada(this, f));
            if (!Letras.jaTentouSincronizada(this, f)) {
                // letra guardada de antes, sem a versao com tempo: tenta uma vez buscar essa versao
                new Thread(() -> {
                    Letras.buscar(this, f, duracaoDa(f));
                    final String sinc = Letras.sincronizada(this, f);
                    if (sinc == null) return;
                    ui.post(() -> {
                        if (letraAberta && f.uri.equals(uriAtual)) exibirLetra(Letras.guardada(this, f), sinc);
                    });
                }).start();
            }
            return;
        }
        letraView.mostrarMensagem("Buscando a letra…");
        new Thread(() -> {
            final String letra = Letras.buscar(this, f, duracaoDa(f));
            final String sinc = Letras.sincronizada(this, f);
            ui.post(() -> {
                if (!letraAberta || !f.uri.equals(uriAtual)) return;
                exibirLetra(letra, sinc);
            });
        }).start();
    }

    /** Mostra a letra: com tempo (a linha cantada fica em destaque e a tela acompanha) ou so o texto. */
    private void exibirLetra(String simples, String sinc) {
        Lrc l = sinc == null ? null : Lrc.ler(sinc);
        lrcAtual = l;
        if (l != null) {
            letraView.definir(l.linhas, true, "");
            desenharLrc(player == null ? 0L : player.getCurrentPosition());
        } else if (simples != null && !simples.trim().isEmpty()) {
            letraView.definir(simples.trim().split("\r?\n"), false, "");
        } else {
            letraView.mostrarMensagem("Não achei a letra dessa música.");
        }
        pintarOpcoesDaLetra();
    }

    /** Tocar numa linha da letra com tempo leva a musica ate aquele trecho. */
    private void irParaLinhaDaLetra(int indice) {
        Lrc l = lrcAtual;
        if (l == null || player == null || indice < 0 || indice >= l.quantas()) return;
        player.seekTo(Math.max(0L, l.tempos[indice] - ajusteLetraMs));
        desenharLrc(l.tempos[indice] - ajusteLetraMs);
    }

    /** Duração da música em ms (do índice ou, se ainda não leu, do player quando é a que toca); 0 se não souber. */
    private long duracaoDa(Faixa f) {
        long d = etiquetas.duracao(f.uri);
        if (d > 0) return d;
        if (player != null && f.uri.equals(uriAtual)) {
            long p = player.getDuration();
            if (p > 0) return p;
        }
        return 0L;
    }

    private void desenharLrc(long posicaoMs) {
        final Lrc l = lrcAtual;
        if (l == null) return;
        letraView.definirAtual(l.indiceEm(posicaoMs + 450 + ajusteLetraMs));
    }

    private void mudarTamanhoDaLetra(int delta) {
        int novo = delta == 0 ? TAMANHO_LETRA_PADRAO : Math.max(TAMANHO_LETRA_MIN, Math.min(TAMANHO_LETRA_MAX, prefs.tamanhoLetra() + delta));
        prefs.tamanhoLetra(novo);
        letraView.definirTamanho(novo);
        pintarOpcoesDaLetra();
    }

    /** "Velocidade" da letra: adianta ou atrasa a linha cantada em relação à música (0 volta ao padrão). */
    private void mudarSincroniaDaLetra(long deltaMs) {
        ajusteLetraMs = deltaMs == 0 ? 0 : ajusteLetraMs + deltaMs;
        pintarOpcoesDaLetra();
        if (player != null) desenharLrc(player.getCurrentPosition());
    }

    private void pintarOpcoesDaLetra() {
        if (lblTamanhoLetra == null) return;
        int t = prefs.tamanhoLetra();
        lblTamanhoLetra.setText(t == TAMANHO_LETRA_PADRAO ? "Padrão" : String.valueOf(t));
        lblAjusteLetra.setText(Lrc.rotuloDoAjuste(ajusteLetraMs));
        linhaAjusteLetra.setVisibility(lrcAtual != null ? View.VISIBLE : View.GONE);  // letra sem tempo não tem o que ajustar
    }

    private TextView chipDaLetra(String rotulo, Runnable acao) {
        TextView t = texto(16, TEXTO, true);
        t.setText(rotulo);
        t.setGravity(Gravity.CENTER);
        t.setBackground(toque(forma(CARTAO, 18, 0), forma(0xFF000000, 18, 0)));
        t.setOnClickListener(v -> acao.run());
        return t;
    }

    /** Uma linha de opção da letra: rótulo à esquerda e [ - ] [ estado ] [ + ] à direita (o estado volta ao padrão ao tocar). */
    private LinearLayout linhaDeOpcaoDaLetra(String rotulo, TextView estado, Runnable menos, Runnable padrao, Runnable mais) {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER_VERTICAL);
        TextView r = texto(14, SUAVE, false);
        r.setText(rotulo);
        l.addView(r, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(44), dp(36));
        p.setMargins(dp(4), dp(3), dp(4), dp(3));
        l.addView(chipDaLetra("\u2212", menos), p);
        estado.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        estado.setGravity(Gravity.CENTER);
        estado.setBackground(toque(forma(CARTAO, 18, 0), forma(0xFF000000, 18, 0)));
        estado.setOnClickListener(v -> padrao.run());
        LinearLayout.LayoutParams pe = new LinearLayout.LayoutParams(dp(92), dp(36));
        pe.setMargins(dp(4), dp(3), dp(4), dp(3));
        l.addView(estado, pe);
        LinearLayout.LayoutParams pm = new LinearLayout.LayoutParams(dp(44), dp(36));
        pm.setMargins(dp(4), dp(3), 0, dp(3));
        l.addView(chipDaLetra("+", mais), pm);
        return l;
    }

    private void mostrarLetra(final Faixa f) {
        String guardada = Letras.guardada(this, f);
        if (guardada != null) {
            dialogoDaLetra(f, guardada);
            return;
        }
        Toast.makeText(this, "Buscando a letra…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final String letra = Letras.buscar(this, f, duracaoDa(f));
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
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        l.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView d = texto(13, SUAVE, false);
        d.setText(detalhe);
        d.setSingleLine(true);
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

    // ------------------------------------------------------------------ selecao de varias musicas
    private boolean selecionando;
    private final Set<String> marcadas = new java.util.LinkedHashSet<>();
    private LinearLayout barraSelecao;
    private LinearLayout pilulasDaBarra;
    private TextView lblSelecao;

    private void iniciarSelecao(Faixa f) {
        selecionando = true;
        marcadas.clear();
        marcadas.add(f.uri);
        atualizarSelecao();
    }

    private void alternarMarca(Faixa f) {
        if (!marcadas.remove(f.uri)) marcadas.add(f.uri);
        if (marcadas.isEmpty()) terminarSelecao();
        else atualizarSelecao();
    }

    private void terminarSelecao() {
        selecionando = false;
        marcadas.clear();
        atualizarSelecao();
    }

    private void atualizarSelecao() {
        if (barraSelecao != null) barraSelecao.setVisibility(selecionando ? View.VISIBLE : View.GONE);
        if (pilulasDaBarra != null) pilulasDaBarra.setVisibility(selecionando ? View.GONE : View.VISIBLE);
        if (lblSelecao != null) lblSelecao.setText(marcadas.size() + (marcadas.size() == 1 ? " selecionada" : " selecionadas"));
        if (adaptador != null) adaptador.notifyDataSetChanged();
    }

    /** As músicas marcadas, na ordem da biblioteca. */
    private List<Faixa> selecionadas() {
        List<Faixa> r = new ArrayList<>();
        for (Faixa f : todas) if (marcadas.contains(f.uri)) r.add(f);
        return r;
    }

    private LinearLayout montarBarraDeSelecao() {
        LinearLayout barra = new LinearLayout(this);
        barra.setOrientation(LinearLayout.VERTICAL);
        LinearLayout topo = new LinearLayout(this);
        topo.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout fechar = new FrameLayout(this);
        IconeView x = new IconeView(this, IconeView.FECHAR);
        x.definir(IconeView.FECHAR, TEXTO);
        fechar.addView(x, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        fechar.setOnClickListener(v -> terminarSelecao());
        topo.addView(fechar, new LinearLayout.LayoutParams(dp(40), dp(40)));
        lblSelecao = texto(15, TEXTO, true);
        topo.addView(lblSelecao, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView todasMarcadas = texto(14, ACENTO, true);
        todasMarcadas.setText("Todas");
        todasMarcadas.setPadding(dp(12), dp(8), dp(4), dp(8));
        todasMarcadas.setOnClickListener(v -> {
            for (Faixa f : visiveis) marcadas.add(f.uri);
            atualizarSelecao();
        });
        topo.addView(todasMarcadas);
        barra.addView(topo);
        LinearLayout acoes = new LinearLayout(this);
        String[] nomes = {"Renomear", "Playlist", "Curtir", "Excluir"};
        Runnable[] fazer = {this::renomearSelecao, this::playlistDaSelecao, this::curtirSelecao, this::excluirSelecao};
        for (int i = 0; i < nomes.length; i++) {
            TextView b = botao(nomes[i], false, fazer[i]);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            b.setSingleLine(true);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(40), 1f);
            p.setMargins(i == 0 ? 0 : dp(4), dp(2), i == nomes.length - 1 ? 0 : dp(4), 0);
            acoes.addView(b, p);
        }
        barra.addView(acoes);
        barra.setVisibility(View.GONE);
        return barra;
    }

    private void playlistDaSelecao() {
        final List<Faixa> fs = selecionadas();
        if (fs.isEmpty()) return;
        final List<String> nomes = minhas.nomes();
        String[] itens = new String[nomes.size() + 1];
        itens[0] = "+  Nova playlist";
        for (int i = 0; i < nomes.size(); i++) itens[i + 1] = nomes.get(i);
        new Construtor()
                .setTitle("Adicionar " + fs.size() + " à playlist")
                .setItems(itens, (d, qual) -> {
                    if (qual == 0) novaPlaylistDaSelecao(fs);
                    else adicionarVariasNaPlaylist(nomes.get(qual - 1), fs);
                })
                .show();
    }

    private void novaPlaylistDaSelecao(final List<Faixa> fs) {
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
                    adicionarVariasNaPlaylist(nome, fs);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void adicionarVariasNaPlaylist(String nome, List<Faixa> fs) {
        int entraram = 0;
        for (Faixa f : fs) if (minhas.adicionar(nome, f.chave)) entraram++;
        salvarPlaylists();
        terminarSelecao();
        if (secao == SECAO_PLAYLISTS || secao == SECAO_COLECAO) aplicarFiltro();
        Toast.makeText(this, entraram + (entraram == 1 ? " adicionada a " : " adicionadas a ") + nome, Toast.LENGTH_SHORT).show();
    }

    /** Se todas as marcadas já são curtidas, descurte todas; senão, curte todas. */
    private void curtirSelecao() {
        List<Faixa> fs = selecionadas();
        if (fs.isEmpty()) return;
        boolean todasCurtidas = true;
        for (Faixa f : fs) if (!curtidas.contains(f.chave)) todasCurtidas = false;
        boolean alvo = !todasCurtidas;
        for (Faixa f : fs) if (curtidas.contains(f.chave) != alvo) prefs.alternarCurtida(f.chave);
        curtidas = prefs.curtidas();
        terminarSelecao();
        if (secao == SECAO_CURTIDAS) aplicarFiltro();
        atualizarPlayer();
        Toast.makeText(this, alvo ? "Curtidas" : "Descurtidas", Toast.LENGTH_SHORT).show();
    }

    private void excluirSelecao() {
        final List<Faixa> fs = selecionadas();
        if (fs.isEmpty()) return;
        new Construtor()
                .setTitle("Excluir " + fs.size() + (fs.size() == 1 ? " música?" : " músicas?"))
                .setMessage("Os arquivos serão apagados e não dá para desfazer.")
                .setPositiveButton("Excluir", (d, q) -> new Thread(() -> {
                    final Set<String> apagadas = new java.util.HashSet<>();
                    for (Faixa f : fs) {
                        try {
                            if (android.provider.DocumentsContract.deleteDocument(getContentResolver(), Uri.parse(f.uri))) apagadas.add(f.uri);
                        } catch (Exception e) {
                            // essa fica de fora e entra na contagem das que falharam
                        }
                    }
                    final int falhas = fs.size() - apagadas.size();
                    ui.post(() -> {
                        if (isFinishing()) return;
                        if (!apagadas.isEmpty()) {
                            List<Faixa> lista = new ArrayList<>(todas);
                            for (int i = lista.size() - 1; i >= 0; i--) if (apagadas.contains(lista.get(i).uri)) lista.remove(i);
                            Biblioteca.guardar(this, lista);
                            definirTodas(lista);
                        }
                        terminarSelecao();
                        String msg = apagadas.size() + (apagadas.size() == 1 ? " excluída" : " excluídas");
                        if (falhas > 0) msg += " · " + falhas + " não deu para excluir (pendrive protegido? escolha a pasta de novo no menu)";
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    });
                }).start())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Renomear as marcadas de uma vez: escolhe o jeito, vê como ficam as primeiras e confirma. */
    private void renomearSelecao() {
        final List<Faixa> fs = selecionadas();
        if (fs.isEmpty()) return;
        final List<String> bases = new ArrayList<>();
        for (Faixa f : fs) bases.add(Faixa.semExtensao(f.nome));
        final List<String> biblioteca = new ArrayList<>();
        for (Faixa f : todas) biblioteca.add(Faixa.semExtensao(f.nome));

        final int[] modo = {Renomeacao.PADRONIZAR};
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(22), dp(8), dp(22), 0);
        final TextView[] opcoes = new TextView[Renomeacao.NOMES.length];
        final EditText procurar = campoDeTexto("");
        procurar.setHint("Procurar");
        final EditText trocarPor = campoDeTexto("");
        trocarPor.setHint("Trocar por (vazio = apagar)");
        final TextView previa = texto(13, SUAVE, false);
        previa.setSingleLine(false);
        previa.setPadding(0, dp(10), 0, dp(4));
        final Runnable[] recalcular = new Runnable[1];
        for (int i = 0; i < opcoes.length; i++) {
            final int este = i;
            opcoes[i] = texto(15, TEXTO, false);
            opcoes[i].setPadding(0, dp(8), 0, dp(8));
            opcoes[i].setOnClickListener(v -> {
                modo[0] = este;
                recalcular[0].run();
            });
            col.addView(opcoes[i]);
        }
        col.addView(procurar);
        col.addView(trocarPor);
        col.addView(previa);
        recalcular[0] = () -> {
            for (int i = 0; i < opcoes.length; i++) {
                opcoes[i].setText((modo[0] == i ? "●  " : "○  ") + Renomeacao.NOMES[i]);
                opcoes[i].setTextColor(modo[0] == i ? ACENTO : TEXTO);
            }
            int vis = modo[0] == Renomeacao.TROCAR ? View.VISIBLE : View.GONE;
            procurar.setVisibility(vis);
            trocarPor.setVisibility(vis);
            List<String> novos = Renomeacao.planejar(bases, biblioteca, modo[0], procurar.getText().toString(), trocarPor.getText().toString());
            int mudam = 0;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < novos.size(); i++) {
                if (novos.get(i).isEmpty()) continue;
                mudam++;
                if (mudam <= 4) sb.append(bases.get(i)).append("\n→ ").append(novos.get(i)).append("\n\n");
            }
            String resumo = mudam == 0 ? "Nenhuma muda com esse jeito." : mudam + (mudam == 1 ? " será renomeada" : " serão renomeadas") + (mudam < fs.size() ? " (as outras já estão assim ou o nome já existe)" : "") + ".";
            previa.setText(sb.toString() + resumo);
        };
        android.text.TextWatcher observador = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable e) { recalcular[0].run(); }
        };
        procurar.addTextChangedListener(observador);
        trocarPor.addTextChangedListener(observador);
        recalcular[0].run();
        ScrollView rolagem = new ScrollView(this);
        rolagem.addView(col);
        new Construtor()
                .setTitle("Renomear " + fs.size() + (fs.size() == 1 ? " música" : " músicas"))
                .setView(rolagem)
                .setPositiveButton("Renomear", (d, q) -> {
                    final List<String> novos = Renomeacao.planejar(bases, biblioteca, modo[0], procurar.getText().toString(), trocarPor.getText().toString());
                    executarRenomeacao(fs, novos);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void executarRenomeacao(final List<Faixa> fs, final List<String> novos) {
        new Thread(() -> {
            final List<Faixa[]> feitas = new ArrayList<>();
            int tentadas = 0;
            for (int i = 0; i < fs.size(); i++) {
                if (novos.get(i).isEmpty()) continue;
                tentadas++;
                Faixa f = fs.get(i);
                String extensao = f.nome.substring(Faixa.semExtensao(f.nome).length());
                try {
                    Uri novo = android.provider.DocumentsContract.renameDocument(getContentResolver(), Uri.parse(f.uri), novos.get(i) + extensao);
                    if (novo != null) feitas.add(new Faixa[]{f, new Faixa(novo.toString(), novos.get(i) + extensao)});
                } catch (Exception e) {
                    // essa fica com o nome de antes
                }
            }
            final int falhas = tentadas - feitas.size();
            ui.post(() -> {
                if (isFinishing()) return;
                if (!feitas.isEmpty()) {
                    Map<String, Faixa> porUriAntiga = new HashMap<>();
                    for (Faixa[] par : feitas) {
                        porUriAntiga.put(par[0].uri, par[1]);
                        prefs.renomearCurtida(par[0].chave, par[1].chave);
                        historico.renomear(par[0].chave, par[1].chave);
                        minhas.renomearChave(par[0].chave, par[1].chave);
                    }
                    List<Faixa> lista = new ArrayList<>(todas);
                    for (int i = 0; i < lista.size(); i++) {
                        Faixa nova = porUriAntiga.get(lista.get(i).uri);
                        if (nova != null) lista.set(i, nova);
                    }
                    curtidas = prefs.curtidas();
                    salvarPlaylists();
                    guardarHistorico();
                    lista = Catalogo.ordenar(lista);
                    Biblioteca.guardar(this, lista);
                    definirTodas(lista);
                }
                terminarSelecao();
                String msg = feitas.size() + (feitas.size() == 1 ? " renomeada" : " renomeadas");
                if (falhas > 0) msg += " · " + falhas + " não deu para renomear";
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            });
        }).start();
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
                .setTitle("Excluir música?")
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
        if (selecionando) terminarSelecao();
        if (nova == SECAO_COLECAO && secao != SECAO_COLECAO) secaoAnterior = secao;
        secao = nova;
        lblSecao.setText(nova == SECAO_COLECAO ? colecaoTitulo : NOMES_SECAO[nova]);
        marcarSecao();
        atualizarCabecalho();
        edtBusca.setText("");
        aplicarFiltro();
        if (lista != null) lista.setSelection(0);
        if (grade != null) grade.setSelection(0);
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

    /** Mostra a conta no cabeçalho do menu e na página inicial, e a tela de bloqueio quando falta licença. */
    private void aplicarConta() {
        boolean logado = Conta.logado(this);
        if (lblConta != null) {
            lblConta.setText(logado ? "Olá, " + Conta.usuario(this) : "Entrar na conta");
        }
        if (itemConta != null) itemConta.setText(logado ? "Sair da conta" : "Entrar na conta");
        if (lblSecao != null && lblContagem != null) {
            atualizarCabecalho();
            atualizarContagem();
        }
        aplicarBloqueio();
    }

    /** Confere a licença da conta em segundo plano. Sem licença, o app fica bloqueado (como no PC). */
    private void atualizarConta() {
        if (!Conta.logado(this)) {
            planoConta = "";
            estadoLicenca = "sem_conta";
            aplicarBloqueio();
            return;
        }
        ultimaVerificacao = System.currentTimeMillis();
        new Thread(() -> {
            final String estado = Conta.verificar(this);
            ui.post(() -> {
                if (isDestroyed()) return;
                estadoLicenca = estado;
                planoConta = Conta.logado(this) ? Conta.planoGuardado(this) : "";
                if ("sem_resposta".equals(estado)) ultimaVerificacao = 0L;  // tenta de novo na próxima abertura
                aplicarConta();
            });
        }).start();
    }

    private void aplicarBloqueio() {
        if (telaBloqueio == null) return;
        boolean liberado = Conta.liberado(this);
        telaBloqueio.setVisibility(liberado ? View.GONE : View.VISIBLE);
        if (liberado) return;
        if (player != null && player.isConnected() && player.isPlaying()) player.pause();
        boolean logado = Conta.logado(this);
        blocoEntrada.setVisibility(logado ? View.GONE : View.VISIBLE);
        blocoSemLicenca.setVisibility(logado ? View.VISIBLE : View.GONE);
        if (!logado) {
            lblBloqueio.setText("Entre com a sua conta do Raccoon Music para usar o app.");
        } else if ("sem_licenca".equals(estadoLicenca)) {
            lblBloqueio.setText("Olá, " + Conta.usuario(this) + ". Esta conta não tem uma licença ativa. Ative a licença no Raccoon Music do PC ou peça uma chave.");
        } else if (estadoLicenca.isEmpty()) {
            lblBloqueio.setText("Olá, " + Conta.usuario(this) + ". Confirmando a licença…");
        } else {
            lblBloqueio.setText("Olá, " + Conta.usuario(this) + ". Não consegui confirmar a licença. Conecte-se à internet e tente de novo.");
        }
    }

    /** Tela que cobre o app inteiro enquanto não houver conta com licença ativa. */
    private View montarBloqueio() {
        ScrollView rolagem = new ScrollView(this);
        rolagem.setBackgroundColor(FUNDO);
        rolagem.setClickable(true);  // nada passa para o app de trás
        rolagem.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(28), dp(56), dp(28), dp(28));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        col.addView(logo, new LinearLayout.LayoutParams(dp(96), dp(96)));
        TextView nome = texto(26, TEXTO, true);
        nome.setText("Raccoon Music");
        nome.setGravity(Gravity.CENTER);
        nome.setPadding(0, dp(14), 0, dp(6));
        col.addView(nome);
        lblBloqueio = texto(14, SUAVE, false);
        lblBloqueio.setSingleLine(false);
        lblBloqueio.setGravity(Gravity.CENTER);
        lblBloqueio.setPadding(0, 0, 0, dp(22));
        col.addView(lblBloqueio, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        blocoEntrada = new LinearLayout(this);
        blocoEntrada.setOrientation(LinearLayout.VERTICAL);
        bloqueioUsuario = campoDaConta("Usuário ou e-mail", false);
        bloqueioSenha = campoDaConta("Senha", true);
        blocoEntrada.addView(bloqueioUsuario, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        LinearLayout.LayoutParams ps = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        ps.setMargins(0, dp(10), 0, 0);
        blocoEntrada.addView(bloqueioSenha, ps);
        TextView entrar = botao("Entrar", true, this::entrarPelaTelaDeBloqueio);
        entrar.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams pe = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        pe.setMargins(0, dp(16), 0, 0);
        blocoEntrada.addView(entrar, pe);
        TextView dica = texto(12, SUAVE, false);
        dica.setSingleLine(false);
        dica.setGravity(Gravity.CENTER);
        dica.setText("É a mesma conta do Raccoon Music no PC. Para criar a conta ou recuperar a senha, use o app do PC.");
        dica.setPadding(0, dp(14), 0, 0);
        blocoEntrada.addView(dica);
        col.addView(blocoEntrada, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        blocoSemLicenca = new LinearLayout(this);
        blocoSemLicenca.setOrientation(LinearLayout.VERTICAL);
        TextView verificar = botao("Verificar de novo", true, () -> {
            Toast.makeText(this, "Verificando…", Toast.LENGTH_SHORT).show();
            atualizarConta();
        });
        verificar.setTextColor(0xFFFFFFFF);
        blocoSemLicenca.addView(verificar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        TextView sair = botao("Sair da conta", false, () -> {
            Conta.sair(this);
            planoConta = "";
            estadoLicenca = "sem_conta";
            aplicarConta();
        });
        LinearLayout.LayoutParams pq = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        pq.setMargins(0, dp(10), 0, 0);
        blocoSemLicenca.addView(sair, pq);
        col.addView(blocoSemLicenca, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rolagem.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return rolagem;
    }

    private EditText campoDaConta(String dica, boolean senha) {
        EditText c = new EditText(this);
        c.setHint(dica);
        c.setHintTextColor(SUAVE);
        c.setTextColor(TEXTO);
        c.setSingleLine(true);
        c.setBackground(forma(CARTAO, 14, LINHA));
        c.setPadding(dp(16), 0, dp(16), 0);
        c.setInputType(senha ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        if (senha) {
            c.setImeOptions(EditorInfo.IME_ACTION_GO);
            c.setOnEditorActionListener((v, acao, ev) -> {
                entrarPelaTelaDeBloqueio();
                return true;
            });
        }
        return c;
    }

    private void entrarPelaTelaDeBloqueio() {
        final String login = bloqueioUsuario.getText().toString();
        final String chave = bloqueioSenha.getText().toString();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(bloqueioSenha.getWindowToken(), 0);
        Toast.makeText(this, "Entrando…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final String erro = Conta.entrar(this, login, chave);
            ui.post(() -> {
                if (isDestroyed()) return;
                if (erro != null) {
                    Toast.makeText(this, erro, Toast.LENGTH_LONG).show();
                    return;
                }
                bloqueioSenha.setText("");
                estadoLicenca = "";
                aplicarConta();
                atualizarConta();
            });
        }).start();
    }

    private void menuDaConta() {
        if (!Conta.logado(this)) {
            dialogoDeEntrada("");
            return;
        }
        String plano = planoConta.isEmpty() ? "consultando…" : planoConta;
        new Construtor()
                .setTitle("Olá, " + Conta.usuario(this))
                .setMessage("Plano: " + plano)
                .setPositiveButton("Fechar", null)
                .setNegativeButton("Sair da conta", (d, q) -> {
                    Conta.sair(this);
                    planoConta = "";
                    aplicarConta();
                    Toast.makeText(this, "Você saiu da conta", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void dialogoDeEntrada(String loginInicial) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(22), dp(8), dp(22), 0);
        final EditText usuario = new EditText(this);
        usuario.setHint("Usuário ou e-mail");
        usuario.setSingleLine(true);
        usuario.setText(loginInicial);
        usuario.setTextColor(TEXTO);
        usuario.setHintTextColor(SUAVE);
        usuario.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        final EditText senha = new EditText(this);
        senha.setHint("Senha");
        senha.setSingleLine(true);
        senha.setTextColor(TEXTO);
        senha.setHintTextColor(SUAVE);
        senha.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TextView aviso = texto(12, SUAVE, false);
        aviso.setSingleLine(false);
        aviso.setText("A mesma conta do Raccoon Music no PC. Para criar a conta ou recuperar a senha, use o app do PC.");
        aviso.setPadding(0, dp(10), 0, 0);
        col.addView(usuario);
        col.addView(senha);
        col.addView(aviso);
        new Construtor()
                .setTitle("Entrar")
                .setView(col)
                .setPositiveButton("Entrar", (d, q) -> {
                    final String login = usuario.getText().toString();
                    final String chave = senha.getText().toString();
                    Toast.makeText(this, "Entrando…", Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        final String erro = Conta.entrar(this, login, chave);
                        ui.post(() -> {
                            if (isDestroyed()) return;
                            if (erro != null) {
                                Toast.makeText(this, erro, Toast.LENGTH_LONG).show();
                                dialogoDeEntrada(login);
                                return;
                            }
                            aplicarConta();
                            atualizarConta();
                            Toast.makeText(this, "Olá, " + Conta.usuario(this), Toast.LENGTH_SHORT).show();
                        });
                    }).start();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private String saudacao() {
        int hora = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (hora >= 5 && hora < 12) return "Bom dia";
        if (hora >= 12 && hora < 18) return "Boa tarde";
        return "Boa noite";
    }

    /** A página inicial: baixar música, um resumo da biblioteca e carrosséis de capas, sem repetir a mesma música em dois lugares. */
    private void montarConteudoInicio() {
        LinearLayout c = conteudoInicio;
        c.removeAllViews();
        c.setPadding(dp(16), dp(2), dp(16), dp(28));
        if (cartaoBaixar == null) cartaoBaixar = montarCartaoBaixar();
        LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pb.setMargins(0, 0, 0, dp(12));
        TextView principais = texto(18, TEXTO, true);
        principais.setText("Principais recursos");
        principais.setPadding(dp(2), dp(8), 0, dp(10));
        c.addView(principais);
        c.addView(cartaoBaixar, pb);

        LinearLayout resumo = new LinearLayout(this);
        Set<String> artistas = new java.util.HashSet<>();
        for (Faixa f : todas) if (!f.artista.isEmpty()) artistas.add(Faixa.normalizar(f.artista));
        String[][] dados = {
                {String.valueOf(todas.size()), "músicas"},
                {String.valueOf(artistas.size()), "artistas"},
                {String.valueOf(historico.totalDeVezes()), "reproduções"},
        };
        for (int i = 0; i < dados.length; i++) {
            LinearLayout cartao = cartaoDeNumero(dados[i][0], dados[i][1]);
            ((TextView) cartao.getChildAt(0)).setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            cartao.setBackground(toque(forma(CARTAO, 14, LINHA), forma(0xFF000000, 14, 0)));
            cartao.setOnClickListener(v -> mostrarEstatisticas());
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            p.setMargins(i == 0 ? 0 : dp(5), 0, i == dados.length - 1 ? 0 : dp(5), 0);
            resumo.addView(cartao, p);
        }
        c.addView(resumo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (todas.isEmpty()) {
            TextView aviso = texto(14, SUAVE, false);
            aviso.setSingleLine(false);
            aviso.setGravity(Gravity.CENTER);
            aviso.setText(varrendo ? (achadasAgora > 0 ? "Procurando músicas… " + achadasAgora : "Procurando músicas…")
                    : prefs.arvore() == null ? "Escolha a pasta onde ficam as suas músicas." : "Nenhuma música encontrada na pasta.");
            aviso.setPadding(0, dp(26), 0, dp(10));
            c.addView(aviso, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (!varrendo) {
                TextView escolher = botao("Escolher a pasta das músicas", true, this::escolherPasta);
                escolher.setTextColor(0xFFFFFFFF);
                c.addView(escolher, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
            }
        }
        if (!todas.isEmpty()) {
            LinearLayout atalhoBiblioteca = new LinearLayout(this);
            atalhoBiblioteca.setGravity(Gravity.CENTER_VERTICAL);
            atalhoBiblioteca.setPadding(dp(14), 0, dp(14), 0);
            atalhoBiblioteca.setBackground(toque(forma(CARTAO, 14, LINHA), forma(0xFF000000, 14, 0)));
            IconeView icBiblioteca = new IconeView(this, IconeView.NOTA);
            icBiblioteca.definir(IconeView.NOTA, ACENTO);
            atalhoBiblioteca.addView(icBiblioteca, new LinearLayout.LayoutParams(dp(22), dp(22)));
            TextView nomeBiblioteca = texto(16, TEXTO, true);
            nomeBiblioteca.setText("Biblioteca");
            nomeBiblioteca.setPadding(dp(12), 0, 0, 0);
            atalhoBiblioteca.addView(nomeBiblioteca, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView quantas = texto(13, SUAVE, false);
            quantas.setText(plural(todas.size(), "música", "músicas") + "  ›");
            atalhoBiblioteca.addView(quantas);
            atalhoBiblioteca.setOnClickListener(v -> irPara(SECAO_MUSICAS));
            LinearLayout.LayoutParams pab = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
            pab.setMargins(0, dp(14), 0, 0);
            c.addView(atalhoBiblioteca, pab);
        }
        Set<String> mostradas = new java.util.HashSet<>();
        carrosselDoInicio(c, "Tocadas recentemente", semRepetidas(historico.recentes(todas, 15), mostradas), SECAO_RECENTES);
        carrosselDoInicio(c, "Mais tocadas", semRepetidas(historico.maisTocadas(todas, 30), mostradas), SECAO_MAIS);
        carrosselDoInicio(c, "Adicionadas recentemente", semRepetidas(Catalogo.ordenarPor(todas, 3, f -> etiquetas.duracao(f.uri)), mostradas), SECAO_MUSICAS);
    }

    /** O campo de baixar da página inicial (como no app do PC): link ou nome da música; várias linhas baixam em lote. */
    private LinearLayout montarCartaoBaixar() {
        LinearLayout cartao = new LinearLayout(this);
        cartao.setOrientation(LinearLayout.VERTICAL);
        cartao.setBackground(forma(CARTAO, 16, LINHA));
        cartao.setPadding(dp(14), dp(12), dp(14), dp(14));
        LinearLayout topo = new LinearLayout(this);
        topo.setGravity(Gravity.CENTER_VERTICAL);
        IconeView ic = new IconeView(this, IconeView.DOWNLOAD);
        ic.definir(IconeView.DOWNLOAD, ACENTO);
        topo.addView(ic, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView titulo = texto(15, TEXTO, true);
        titulo.setText("Baixar músicas");
        titulo.setPadding(dp(8), 0, 0, 0);
        topo.addView(titulo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        lblFilaInicio = texto(13, ACENTO, true);
        lblFilaInicio.setPadding(dp(10), dp(4), 0, dp(4));
        lblFilaInicio.setOnClickListener(v -> abrirFila());
        lblFilaInicio.setVisibility(View.GONE);
        topo.addView(lblFilaInicio);
        cartao.addView(topo);

        FrameLayout moldura = new FrameLayout(this);
        final EditText campo = new EditText(this);
        campo.setHint("Link ou nome da música");
        campo.setHintTextColor(SUAVE);
        campo.setTextColor(TEXTO);
        campo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        campo.setMaxLines(4);
        campo.setMinHeight(dp(52));
        campo.setGravity(Gravity.CENTER_VERTICAL);
        campo.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        campo.setBackground(forma(FUNDO, 26, LINHA));
        campo.setPadding(dp(18), dp(10), dp(62), dp(10));
        moldura.addView(campo, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout botao = new FrameLayout(this);
        botao.setBackground(toque(forma(ACENTO, 20, 0), forma(0xFFFFFFFF, 20, 0)));
        IconeView seta = new IconeView(this, IconeView.DOWNLOAD);
        seta.definir(IconeView.DOWNLOAD, 0xFFFFFFFF);
        botao.addView(seta, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        FrameLayout.LayoutParams pb = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.END | Gravity.CENTER_VERTICAL);
        pb.setMargins(0, 0, dp(6), 0);
        moldura.addView(botao, pb);
        botao.setOnClickListener(v -> {
            String texto = campo.getText().toString().trim();
            if (texto.isEmpty()) {
                Toast.makeText(this, "Escreva um link ou o nome da música.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (prefs.arvore() == null) {
                Toast.makeText(this, "Escolha primeiro a pasta de músicas.", Toast.LENGTH_LONG).show();
                escolherPasta();
                return;
            }
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(campo.getWindowToken(), 0);
            campo.setText("");
            baixarTexto(texto);
        });
        LinearLayout.LayoutParams pm = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pm.setMargins(0, dp(10), 0, 0);
        cartao.addView(moldura, pm);
        TextView dica = texto(12, SUAVE, false);
        dica.setSingleLine(false);
        dica.setText("Para baixar várias, cole um link ou nome por linha, ou o link de uma playlist.");
        dica.setPadding(dp(4), dp(8), 0, 0);
        cartao.addView(dica);
        return cartao;
    }

    /** As primeiras 15 que ainda não apareceram em outro carrossel. */
    private List<Faixa> semRepetidas(List<Faixa> base, Set<String> mostradas) {
        List<Faixa> r = new ArrayList<>();
        for (Faixa f : base) {
            if (r.size() >= 15) break;
            if (mostradas.add(f.uri)) r.add(f);
        }
        return r;
    }

    private void carrosselDoInicio(LinearLayout pai, String titulo, final List<Faixa> faixas, final int destino) {
        if (faixas.isEmpty()) return;
        LinearLayout cab = new LinearLayout(this);
        cab.setGravity(Gravity.CENTER_VERTICAL);
        cab.setPadding(0, dp(14), 0, dp(8));
        TextView t = texto(18, TEXTO, true);
        t.setText(titulo);
        cab.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView ver = texto(13, ACENTO, true);
        ver.setText("Ver tudo");
        ver.setPadding(dp(10), dp(6), 0, dp(6));
        ver.setOnClickListener(v -> irPara(destino));
        cab.addView(ver);
        pai.addView(cab);

        HorizontalScrollView rolagem = new HorizontalScrollView(this);
        rolagem.setHorizontalScrollBarEnabled(false);
        rolagem.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout fila = new LinearLayout(this);
        for (int i = 0; i < faixas.size(); i++) {
            final int indice = i;
            final Faixa f = faixas.get(i);
            LinearLayout cartao = new LinearLayout(this);
            cartao.setOrientation(LinearLayout.VERTICAL);
            Linha l = new Linha();
            l.caixaCapa = new FrameLayout(this);
            l.caixaCapa.setBackground(forma(CARTAO, 14, LINHA));
            arredondar(l.caixaCapa, 14);
            l.capa = new ImageView(this);
            l.capa.setScaleType(ImageView.ScaleType.CENTER_CROP);
            l.caixaCapa.addView(l.capa, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            l.nota = new IconeView(this, IconeView.NOTA);
            l.nota.definir(IconeView.NOTA, SUAVE);
            l.caixaCapa.addView(l.nota, new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER));
            cartao.addView(l.caixaCapa, new LinearLayout.LayoutParams(dp(132), dp(132)));
            TextView nome = rolante(14, TEXTO, true);
            nome.setText(f.titulo);
            nome.setPadding(dp(2), dp(8), dp(2), 0);
            TextView quem = rolante(12, SUAVE, false);
            quem.setText(f.artista);
            quem.setPadding(dp(2), dp(1), dp(2), 0);
            cartao.addView(nome);
            cartao.addView(quem);
            ligarCapa(l, f);
            cartao.setOnClickListener(v -> {
                if (prefs.embaralhar()) tocarFila(Catalogo.embaralhar(faixas, indice, sorteio), 0);
                else tocarFila(new ArrayList<>(faixas), indice);
            });
            cartao.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                opcoesDaFaixa(f);
                return true;
            });
            LinearLayout.LayoutParams pc = new LinearLayout.LayoutParams(dp(132), ViewGroup.LayoutParams.WRAP_CONTENT);
            pc.setMargins(0, 0, dp(12), 0);
            fila.addView(cartao, pc);
        }
        rolagem.addView(fila);
        pai.addView(rolagem, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void atualizarCabecalho() {
        icMenu.definir(secao == SECAO_COLECAO ? IconeView.SETA : IconeView.MENU, TEXTO);
        celulaMais.setVisibility(secao == SECAO_PLAYLISTS ? View.VISIBLE : View.GONE);
        if (caixaDaBusca != null) caixaDaBusca.setVisibility(secao == SECAO_INICIO ? View.GONE : View.VISIBLE);
        celulaAleatorio.setVisibility(todas.isEmpty() || (secao != SECAO_MUSICAS && secao != SECAO_CURTIDAS) ? View.GONE : View.VISIBLE);
        if (bFiltro != null) bFiltro.setVisibility(secao == SECAO_MUSICAS ? View.VISIBLE : View.GONE);
        if (secao == SECAO_INICIO) lblSecao.setText(Conta.logado(this) && !Conta.usuario(this).isEmpty() ? "Olá, " + Conta.usuario(this) : saudacao());
        rotularBarra();
    }

    private void menuDoTemporizador() {
        if (desligar != null || pararNoFim) {
            if (desligar != null) ui.removeCallbacks(desligar);
            desligar = null;
            desligarEm = 0;
            pararNoFim = false;
            if (player != null && player.isConnected()) player.setVolume(1f);
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
                        desligarEm = 0;
                        if (itemTemporizador != null) itemTemporizador.setText("Temporizador");
                        try {
                            if (player != null && player.isConnected()) {
                                player.pause();
                                player.setVolume(1f);
                            }
                        } catch (Exception e) {
                            // o app ja foi fechado: nada a pausar daqui
                        }
                    };
                    ui.postDelayed(desligar, minutos[qual] * 60_000L);
                    desligarEm = System.currentTimeMillis() + minutos[qual] * 60_000L;
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

    /** Padrões prontos (os do PC) e as combinações salvas pela pessoa, como botões que aplicam fundo e destaque juntos. */
    private void adicionarCombinacoes(LinearLayout col, final AlertDialog dialogo) {
        TextView rotulo = texto(14, SUAVE, true);
        rotulo.setText("Padrões prontos");
        rotulo.setPadding(0, dp(4), 0, dp(6));
        col.addView(rotulo);
        linhasDeCombinacoes(col, dialogo, java.util.Arrays.asList(Combinacoes.PRONTAS), false);
        List<Combinacoes.Item> minhasCores = Combinacoes.ler(prefs.combinacoes());
        if (!minhasCores.isEmpty()) {
            TextView meus = texto(14, SUAVE, true);
            meus.setText("Minhas combinações (segure para apagar)");
            meus.setPadding(0, dp(14), 0, dp(6));
            col.addView(meus);
            linhasDeCombinacoes(col, dialogo, minhasCores, true);
        }
        TextView separador = texto(14, SUAVE, true);
        separador.setText("Ajuste fino");
        separador.setPadding(0, dp(18), 0, dp(2));
        col.addView(separador);
    }

    private void linhasDeCombinacoes(LinearLayout col, final AlertDialog dialogo, List<Combinacoes.Item> itens, final boolean apagaveis) {
        int fundoAtual = (prefs.corFundo() == 0 ? Cores.PADRAO_FUNDO : prefs.corFundo()) & 0xFFFFFF;
        int acentoAtual = (prefs.corAcento() == 0 ? Cores.PADRAO_ACENTO : prefs.corAcento()) & 0xFFFFFF;
        LinearLayout linha = null;
        for (int i = 0; i < itens.size(); i++) {
            if (i % 3 == 0) {
                linha = new LinearLayout(this);
                col.addView(linha);
            }
            final Combinacoes.Item c = itens.get(i);
            LinearLayout chip = new LinearLayout(this);
            chip.setGravity(Gravity.CENTER);
            boolean marcada = (c.fundo & 0xFFFFFF) == fundoAtual && (c.acento & 0xFFFFFF) == acentoAtual;
            chip.setBackground(forma(c.fundo, 14, marcada ? TEXTO : LINHA));
            View ponto = new View(this);
            ponto.setBackground(bolinha(c.acento, false));
            chip.addView(ponto, new LinearLayout.LayoutParams(dp(14), dp(14)));
            TextView nome = texto(13, Cores.luminancia(c.fundo) > 0.5 ? 0xFF18181B : 0xFFFFFFFF, marcada);
            nome.setText(c.nome);
            nome.setSingleLine(true);
            nome.setEllipsize(TextUtils.TruncateAt.END);
            nome.setPadding(dp(6), 0, 0, 0);
            chip.addView(nome);
            chip.setOnClickListener(v -> {
                prefs.cores(c.fundo, c.acento);
                dialogo.dismiss();
                recreate();
            });
            if (apagaveis) {
                chip.setOnLongClickListener(v -> {
                    List<Combinacoes.Item> todasSalvas = Combinacoes.ler(prefs.combinacoes());
                    Combinacoes.remover(todasSalvas, c.nome);
                    prefs.combinacoes(Combinacoes.serializar(todasSalvas));
                    dialogo.dismiss();
                    escolherCores();
                    return true;
                });
            }
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(42), 1f);
            p.setMargins(dp(3), dp(3), dp(3), dp(3));
            linha.addView(chip, p);
        }
        int resto = itens.size() % 3;
        for (int i = 0; resto != 0 && i < 3 - resto; i++) linha.addView(new View(this), new LinearLayout.LayoutParams(0, dp(42), 1f));
    }

    /** Guarda o fundo e o destaque de agora com um nome, para voltar a eles depois. */
    private void salvarCombinacaoAtual() {
        final EditText campo = campoDeTexto("");
        campo.setHint("Nome da combinação");
        new Construtor()
                .setTitle("Salvar combinação")
                .setView(caixaDoCampo(campo))
                .setPositiveButton("Salvar", (d, q) -> {
                    String nome = Combinacoes.limparNome(campo.getText().toString());
                    if (nome.isEmpty()) {
                        Toast.makeText(this, "Escreva um nome.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    List<Combinacoes.Item> salvas = Combinacoes.ler(prefs.combinacoes());
                    Combinacoes.salvar(salvas, nome, prefs.corFundo() == 0 ? Cores.PADRAO_FUNDO : prefs.corFundo(), prefs.corAcento() == 0 ? Cores.PADRAO_ACENTO : prefs.corAcento());
                    prefs.combinacoes(Combinacoes.serializar(salvas));
                    Toast.makeText(this, "Combinação salva", Toast.LENGTH_SHORT).show();
                    escolherCores();
                })
                .setNegativeButton("Cancelar", (d, q) -> escolherCores())
                .show();
    }

    private void escolherCores() {
        final ScrollView rolagem = new ScrollView(this);
        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(8), dp(20), dp(8));
        rolagem.addView(col);
        final AlertDialog dialogo = new Construtor().setTitle("Aparência").setView(rolagem)
                .setNegativeButton("Restaurar", (d, q) -> {
                    prefs.cores(0, 0);
                    recreate();
                })
                .setNeutralButton("Salvar", (d, q) -> salvarCombinacaoAtual())
                .setPositiveButton("Fechar", null)
                .create();
        adicionarCombinacoes(col, dialogo);
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

    /** TextView que sempre se considera em foco: só assim o texto longo rola sozinho dentro de listas (que mexem na seleção das linhas). */
    private static final class Rolante extends TextView {
        Rolante(android.content.Context c) {
            super(c);
        }

        @Override
        public boolean isFocused() {
            return true;
        }
    }

    /** Texto de uma linha que, se não couber, rola de um lado ao outro (títulos e artistas). */
    private TextView rolante(float sp, int cor, boolean negrito) {
        TextView t = new Rolante(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(cor);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        t.setMarqueeRepeatLimit(-1);
        t.setHorizontallyScrolling(true);
        if (negrito) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
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
        b.setBackground(toque(forma(destaque ? ACENTO : CARTAO, 24, TEXTO), forma(0xFF000000, 24, 0)));  // contorno na cor do texto (branco no tema escuro), como no app do PC
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
        caixaDaBusca = caixaBusca;
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

        // ordem e filtro da lista de músicas
        celulaAleatorio = new FrameLayout(this);
        LinearLayout pilulas = new LinearLayout(this);
        pilulasDaBarra = pilulas;
        pilulas.setGravity(Gravity.CENTER_VERTICAL);
        bOrdem = botao("Ordem", false, this::menuOrdenar);
        bSeta = botao("↑", false, () -> {
            prefs.ordemInvertida(!prefs.ordemInvertida());
            aplicarFiltro();
            if (lista != null) lista.setSelection(0);
        });
        bFiltro = botao("Filtrar", false, this::menuFiltrar);
        LinearLayout.LayoutParams po = new LinearLayout.LayoutParams(0, dp(42), 1f);
        po.setMargins(0, 0, dp(5), 0);
        LinearLayout.LayoutParams pf = new LinearLayout.LayoutParams(0, dp(42), 1f);
        pf.setMargins(dp(5), 0, 0, 0);
        ps.setMargins(dp(2), 0, dp(2), 0);
        pilulas.addView(bOrdem, po);
        LinearLayout.LayoutParams ps = new LinearLayout.LayoutParams(dp(46), dp(42));
        pilulas.addView(bSeta, ps);
        pilulas.addView(bFiltro, pf);
        celulaAleatorio.addView(pilulas, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        barraSelecao = montarBarraDeSelecao();
        celulaAleatorio.addView(barraSelecao, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
            } else if (selecionando) {
                if (pos >= 0 && pos < visiveis.size()) alternarMarca(visiveis.get(pos));
            } else {
                tocarDaLista(pos);
            }
        });
        lista.setOnItemLongClickListener((p, v, pos, id) -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (modoGrupos()) {
                if (pos >= 0 && pos < grupos.size()) opcoesDoGrupo(grupos.get(pos));
            } else if (pos >= 0 && pos < visiveis.size()) {
                if (selecionando) alternarMarca(visiveis.get(pos));
                else opcoesDaFaixa(visiveis.get(pos));
            }
            return true;
        });
        meio.addView(lista, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        grade = new GridView(this);
        grade.setNumColumns(2);
        grade.setHorizontalSpacing(dp(14));
        grade.setVerticalSpacing(dp(18));
        grade.setPadding(dp(16), dp(8), dp(16), dp(16));
        grade.setClipToPadding(false);
        grade.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grade.setSelector(android.R.color.transparent);
        grade.setVerticalScrollBarEnabled(false);
        adaptadorGrade = new AdaptadorGrade();
        grade.setAdapter(adaptadorGrade);
        grade.setOnItemClickListener((p, v, pos, id) -> {
            if (pos >= 0 && pos < grupos.size()) abrirColecao(grupos.get(pos));
        });
        grade.setOnItemLongClickListener((p, v, pos, id) -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (pos >= 0 && pos < grupos.size()) opcoesDoGrupo(grupos.get(pos));
            return true;
        });
        grade.setVisibility(View.GONE);
        meio.addView(grade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        inicio = new ScrollView(this);
        inicio.setVerticalScrollBarEnabled(false);
        inicio.setOverScrollMode(View.OVER_SCROLL_NEVER);
        conteudoInicio = new LinearLayout(this);
        conteudoInicio.setOrientation(LinearLayout.VERTICAL);
        inicio.addView(conteudoInicio, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        meio.addView(inicio, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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
        telaBloqueio = montarBloqueio();
        tudo.addView(telaBloqueio, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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
        lblTituloMini = rolante(15, TEXTO, true);
        lblArtistaMini = rolante(12, SUAVE, false);
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
        IconeView ic = new IconeView(this, tipo);
        ic.definir(tipo, ICONE);
        c.addView(ic, new LinearLayout.LayoutParams(dp(26), dp(26)));
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
        tituloTopo = rolante(15, SUAVE, true);
        tituloTopo.setText("Tocando agora");
        tituloTopo.setGravity(Gravity.CENTER);
        topo.addView(tituloTopo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        FrameLayout cCurtirTopo = new FrameLayout(this);
        icCurtirTopo = icone(IconeView.CORACAO, 24, this::curtirAtual, cCurtirTopo);
        icCurtirTopo.setVisibility(View.GONE);
        cCurtirTopo.setVisibility(View.GONE);
        celulaCurtirTopo = cCurtirTopo;
        topo.addView(cCurtirTopo, new LinearLayout.LayoutParams(dp(44), dp(48)));
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
        caixaDaCapa = caixaCapa;
        gestos(caixaCapa, true, true, true);
        areaCapa.addView(caixaCapa, new FrameLayout.LayoutParams(lado, lado, Gravity.CENTER));

        // a letra ocupa o lugar da capa: linha cantada em destaque, rolagem suave e as opcoes de tamanho e sincronia embaixo
        blocoLetra = new LinearLayout(this);
        blocoLetra.setOrientation(LinearLayout.VERTICAL);
        blocoLetra.setVisibility(View.GONE);
        letraView = new LetraView(this);
        letraView.definirCores(TEXTO, TEXTO);
        letraView.definirTamanho(prefs.tamanhoLetra());
        letraView.aoTocarLinha(this::irParaLinhaDaLetra);
        blocoLetra.addView(letraView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        lblTamanhoLetra = texto(13, TEXTO, true);
        lblAjusteLetra = texto(13, TEXTO, true);
        LinearLayout opcoesLetra = new LinearLayout(this);
        opcoesLetra.setOrientation(LinearLayout.VERTICAL);
        opcoesLetra.setPadding(dp(4), dp(4), dp(4), 0);
        opcoesLetra.addView(linhaDeOpcaoDaLetra("Tamanho", lblTamanhoLetra,
                () -> mudarTamanhoDaLetra(-2), () -> mudarTamanhoDaLetra(0), () -> mudarTamanhoDaLetra(2)));
        linhaAjusteLetra = linhaDeOpcaoDaLetra("Velocidade", lblAjusteLetra,
                () -> mudarSincroniaDaLetra(-PASSO_AJUSTE_LETRA_MS), () -> mudarSincroniaDaLetra(0), () -> mudarSincroniaDaLetra(PASSO_AJUSTE_LETRA_MS));
        opcoesLetra.addView(linhaAjusteLetra);
        blocoLetra.addView(opcoesLetra, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        areaCapa.addView(blocoLetra, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pintarOpcoesDaLetra();
        col.addView(areaCapa, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout info = new LinearLayout(this);
        linhaDoTitulo = info;
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(0, dp(12), 0, 0);
        LinearLayout nomes = new LinearLayout(this);
        nomes.setOrientation(LinearLayout.VERTICAL);
        lblTitulo = rolante(22, TEXTO, true);
        lblTitulo.setText("Nada tocando");
        lblArtista = rolante(16, SUAVE, false);
        nomes.addView(lblTitulo);
        nomes.addView(lblArtista);
        lblProxima = texto(12, SUAVE, false);
        lblProxima.setPadding(0, dp(3), 0, 0);
        nomes.addView(lblProxima);
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
        linhaControles = controles;
        col.addView(controles);

        LinearLayout acoes = new LinearLayout(this);
        acoes.setPadding(0, dp(4), 0, 0);
        LinearLayout aLetra = acaoRapida(IconeView.LETRA, "Letra", this::alternarLetra);
        icLetra = (IconeView) aLetra.getChildAt(0);
        acoes.addView(aLetra, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        acoes.addView(acaoRapida(IconeView.FILA, "Fila", this::mostrarFila), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
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
    private static final int[] ICONES_SECAO = {IconeView.CASA, IconeView.NOTA, IconeView.ARTISTA, IconeView.DISCO, IconeView.ETIQUETA, IconeView.PLAYLIST, IconeView.CORACAO, IconeView.RELOGIO, IconeView.ESTRELA, IconeView.LISTA};

    /** Uma linha do menu: icone e nome. O nome e o segundo filho (veja rotuloDoItem). */
    private LinearLayout itemDoMenu(String rotulo, int tipoIcone, Runnable acao) {
        LinearLayout linha = new LinearLayout(this);
        linha.setGravity(Gravity.CENTER_VERTICAL);
        linha.setPadding(dp(14), 0, dp(14), 0);
        IconeView ic = new IconeView(this, tipoIcone);
        ic.definir(tipoIcone, ICONE);
        linha.addView(ic, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView t = texto(15, TEXTO, false);
        t.setText(rotulo);
        t.setPadding(dp(16), 0, 0, 0);
        linha.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        linha.setBackground(toque(null, forma(0xFF000000, 12, 0)));
        linha.setOnClickListener(v -> {
            fecharGaveta();
            acao.run();
        });
        return linha;
    }

    private TextView rotuloDoItem(View item) {
        return (TextView) ((LinearLayout) item).getChildAt(1);
    }

    private void adicionarItem(LinearLayout pai, View item) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        p.setMargins(dp(10), dp(1), dp(10), dp(1));
        pai.addView(item, p);
    }

    private void adicionarTitulo(LinearLayout pai, String rotulo) {
        TextView t = texto(11, SUAVE, true);
        t.setText(rotulo.toUpperCase(Locale.ROOT));
        t.setLetterSpacing(0.1f);
        t.setPadding(dp(26), dp(16), dp(20), dp(4));
        pai.addView(t);
    }

    private View montarGaveta() {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setBackgroundColor(CARTAO);
        g.setClickable(true);  // nao deixa o toque passar para a lista de tras

        LinearLayout marca = new LinearLayout(this);
        marca.setGravity(Gravity.CENTER_VERTICAL);
        marca.setPadding(dp(20), dp(32), dp(20), dp(22));
        marca.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Cores.misturar(CARTAO, ACENTO, 0.28), CARTAO}));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        marca.addView(logo, new LinearLayout.LayoutParams(dp(56), dp(56)));
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
        lblConta = texto(13, ACENTO, true);
        lblConta.setPadding(dp(14), dp(4), 0, 0);
        nomes.addView(lblConta);
        marca.addView(nomes, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        marca.setOnClickListener(v -> menuDaConta());
        g.addView(marca);

        ScrollView rolagem = new ScrollView(this);
        LinearLayout itens = new LinearLayout(this);
        itens.setOrientation(LinearLayout.VERTICAL);
        adicionarTitulo(itens, "Explorar");
        for (int i = 0; i < SECOES_NO_MENU; i++) {
            final int destino = i;
            if (i == SECAO_ARTISTAS) adicionarTitulo(itens, "Listas");
            linhasSecao[i] = itemDoMenu(NOMES_SECAO[i], ICONES_SECAO[i], () -> irPara(destino));
            adicionarItem(itens, linhasSecao[i]);
        }
        adicionarTitulo(itens, "Músicas");
        adicionarItem(itens, itemDoMenu("Baixar música", IconeView.DOWNLOAD, this::baixarNoCelular));
        adicionarItem(itens, itemDoMenu("Fila de downloads", IconeView.DOWNLOAD, this::abrirFila));
        adicionarItem(itens, itemDoMenu("Baixar no PC", IconeView.COMPUTADOR, this::baixarNoPc));
        adicionarItem(itens, itemDoMenu("Enviar ao PC", IconeView.EXPORTAR, this::enviarAoPc));
        adicionarItem(itens, itemDoMenu("Exportar músicas", IconeView.PASTA, this::escolherOrigemDaExportacao));
        adicionarTitulo(itens, "Ferramentas");
        adicionarItem(itens, itemDoMenu("Estatísticas", IconeView.ESTATISTICA, this::mostrarEstatisticas));
        LinearLayout linhaTemporizador = itemDoMenu("Temporizador", IconeView.RELOGIO, this::menuDoTemporizador);
        itemTemporizador = rotuloDoItem(linhaTemporizador);
        adicionarItem(itens, linhaTemporizador);
        adicionarTitulo(itens, "Aplicativo");
        LinearLayout linhaConta = itemDoMenu("Entrar na conta", IconeView.ARTISTA, this::menuDaConta);
        itemConta = rotuloDoItem(linhaConta);
        adicionarItem(itens, linhaConta);
        adicionarItem(itens, itemDoMenu("Ajustes", IconeView.AJUSTES, this::menuDeAjustes));
        adicionarItem(itens, itemDoMenu("Atualizar a biblioteca", IconeView.ATUALIZAR, this::atualizarBiblioteca));
        adicionarItem(itens, itemDoMenu("Escolher outra pasta", IconeView.PASTA, this::escolherPasta));
        if (Atualizador.disponivel()) {
            LinearLayout linhaVersao = itemDoMenu("Procurar atualização", IconeView.DOWNLOAD, () -> procurarAtualizacao(true));
            itemVersao = rotuloDoItem(linhaVersao);
            adicionarItem(itens, linhaVersao);
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
        for (int i = 0; i < linhasSecao.length; i++) {
            if (linhasSecao[i] == null) continue;
            boolean atual = i == (secao == SECAO_COLECAO ? secaoAnterior : secao);
            TextView rotulo = rotuloDoItem(linhasSecao[i]);
            rotulo.setTextColor(atual ? ACENTO : TEXTO);
            rotulo.setTypeface(null, atual ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            ((IconeView) ((LinearLayout) linhasSecao[i]).getChildAt(0)).definir(ICONES_SECAO[i], atual ? ACENTO : ICONE);
            linhasSecao[i].setBackground(toque(atual ? forma(LINHA, 12, 0) : null, forma(0xFF000000, 12, 0)));
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
        if (selecionando) terminarSelecao();
        else if (gavetaAberta) fecharGaveta();
        else if (tocandoAberto) fecharTocando();
        else if (secao == SECAO_COLECAO) irPara(secaoAnterior);
        else if (secao != SECAO_INICIO) irPara(SECAO_INICIO);
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

    // ------------------------------------------------------------------ baixar no proprio celular
    private TextView lblFilaInicio;
    private Runnable redesenharFila;

    /** Avisa o fim de cada download da fila: atualiza a biblioteca e, no fim, mostra o resumo. */
    private final android.content.BroadcastReceiver aoBaixar = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context contexto, Intent dados) {
            if (isFinishing()) return;
            boolean ok = dados.getBooleanExtra(BaixarService.EXTRA_OK, false);
            boolean fim = dados.getBooleanExtra(BaixarService.EXTRA_FIM_DA_FILA, true);
            int[] n = FilaDeDownload.contagem();
            int total = FilaDeDownload.copia().size();
            if (total <= 1) {
                Toast.makeText(MainActivity.this, dados.getStringExtra(BaixarService.EXTRA_MENSAGEM), Toast.LENGTH_LONG).show();
            } else if (fim) {
                String resumo = n[FilaDeDownload.OK] + " de " + total + " baixadas";
                if (n[FilaDeDownload.ERRO] > 0) resumo += " · " + n[FilaDeDownload.ERRO] + " com erro (veja a fila)";
                Toast.makeText(MainActivity.this, resumo, Toast.LENGTH_LONG).show();
            }
            if (ok && (total <= 1 || fim)) atualizarBiblioteca();
        }
    };

    /** Mantém o aviso "Fila · feitos/total" do cartão de download e a tela da fila em dia. */
    private final Runnable aoMudarFila = () -> ui.post(() -> {
        atualizarFilaInicio();
        if (redesenharFila != null) redesenharFila.run();
    });

    private void atualizarFilaInicio() {
        if (lblFilaInicio == null) return;
        int[] n = FilaDeDownload.contagem();
        int total = FilaDeDownload.copia().size();
        boolean ativa = FilaDeDownload.ativa();
        if (total == 0 || (!ativa && n[FilaDeDownload.ERRO] == 0)) {
            lblFilaInicio.setVisibility(View.GONE);
            return;
        }
        String t = "Fila · " + (n[FilaDeDownload.OK] + n[FilaDeDownload.ERRO]) + "/" + total;
        if (n[FilaDeDownload.ERRO] > 0) t += " · " + n[FilaDeDownload.ERRO] + " erro(s)";
        lblFilaInicio.setText(t);
        lblFilaInicio.setVisibility(View.VISIBLE);
    }

    private String pedidoPendente = "";

    /** Baixa links ou nomes (um ou vários, um por linha) direto no celular, para a pasta de músicas. */
    private void baixarNoCelular() {
        if (prefs.arvore() == null) {
            Toast.makeText(this, "Escolha primeiro a pasta de músicas.", Toast.LENGTH_LONG).show();
            escolherPasta();
            return;
        }
        final EditText campo = campoDeTexto(pedidoPendente);
        campo.setSingleLine(false);
        campo.setMaxLines(8);
        campo.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        campo.setHint("Link ou nome da música (um por linha, ou o link de uma playlist)");
        new Construtor()
                .setTitle("Baixar músicas")
                .setView(caixaDoCampo(campo))
                .setPositiveButton("Baixar", (d, q) -> {
                    pedidoPendente = "";
                    baixarTexto(campo.getText().toString().trim());
                })
                .setNegativeButton("Cancelar", (d, q) -> pedidoPendente = "")
                .show();
    }

    /** Ponto único de entrada: um pedido simples segue o caminho antigo; vários (ou playlist) vão para o diálogo do lote. */
    private void baixarTexto(final String texto) {
        final List<String> pedidos = Pedido.separar(texto);
        if (pedidos.isEmpty()) {
            Toast.makeText(this, "Escreva um link ou o nome da música.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (pedidos.size() == 1 && !Pedido.ehPlaylist(pedidos.get(0))) {
            iniciarDownload(pedidos.get(0));
            return;
        }
        boolean temPlaylist = false;
        for (String p : pedidos) if (Pedido.ehPlaylist(p)) temPlaylist = true;
        if (!temPlaylist) {
            dialogoDoLote(pedidos, new ArrayList<String>());
            return;
        }
        final boolean[] cancelado = {false};
        final AlertDialog espera = new Construtor()
                .setMessage("Lendo a playlist…")
                .setNegativeButton("Cancelar", (d, q) -> cancelado[0] = true)
                .setOnCancelListener(d -> cancelado[0] = true)
                .show();
        new Thread(() -> {
            final List<String> entradas = new ArrayList<>();
            final List<String> titulos = new ArrayList<>();
            for (String p : pedidos) {
                if (cancelado[0]) return;
                if (Pedido.ehPlaylist(p)) {
                    for (Baixador.Entrada e : Baixador.expandirPlaylist(this, p)) {
                        entradas.add(e.url);
                        titulos.add(e.titulo);
                    }
                } else {
                    entradas.add(p);
                    titulos.add("");
                }
            }
            ui.post(() -> {
                if (cancelado[0] || isFinishing()) return;
                espera.dismiss();
                if (entradas.isEmpty()) {
                    Toast.makeText(this, "Não consegui ler a playlist.", Toast.LENGTH_LONG).show();
                    return;
                }
                dialogoDoLote(entradas, titulos);
            });
        }).start();
    }

    /** Uma linha do diálogo do lote. */
    private static final class LinhaDoLote {
        String entrada;
        String titulo;
        EditText campo;
        View vista;
        boolean removida;
    }

    /** Mostra o lote: cada música com um nome editável (vazio = automático) e um ✕ para tirar da lista. */
    private void dialogoDoLote(final List<String> entradas, final List<String> titulos) {
        final int total = entradas.size();
        if (total > 80) {
            new Construtor()
                    .setTitle("Baixar " + total + " músicas?")
                    .setMessage("Os nomes ficam automáticos (Artista - Título). Elas entram na fila e baixam duas por vez.")
                    .setPositiveButton("Baixar tudo", (d, q) -> colocarNaFila(entradas, null))
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }
        final List<LinhaDoLote> linhas = new ArrayList<>();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(8), dp(18), 0);

        LinearLayout acoes = new LinearLayout(this);
        TextView preencher = texto(13, ACENTO, true);
        preencher.setText("Preencher com os títulos");
        preencher.setPadding(0, dp(6), dp(16), dp(10));
        TextView automatico = texto(13, ACENTO, true);
        automatico.setText("Deixar automático");
        automatico.setPadding(0, dp(6), 0, dp(10));
        acoes.addView(preencher);
        acoes.addView(automatico);
        col.addView(acoes);

        final LinearLayout lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < total; i++) {
            final LinhaDoLote l = new LinhaDoLote();
            l.entrada = entradas.get(i);
            l.titulo = i < titulos.size() ? titulos.get(i) : "";
            LinearLayout linha = new LinearLayout(this);
            linha.setGravity(Gravity.CENTER_VERTICAL);
            l.campo = new EditText(this);
            l.campo.setSingleLine(true);
            l.campo.setTextColor(temaClaro ? 0xFF18181B : 0xFFFFFFFF);
            l.campo.setHintTextColor(SUAVE);
            l.campo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            l.campo.setHint(l.titulo.isEmpty() ? l.entrada : l.titulo);
            linha.addView(l.campo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView tirar = texto(18, SUAVE, true);
            tirar.setText("✕");
            tirar.setGravity(Gravity.CENTER);
            tirar.setPadding(dp(12), dp(8), dp(4), dp(8));
            tirar.setOnClickListener(v -> {
                l.removida = true;
                l.vista.setVisibility(View.GONE);
            });
            linha.addView(tirar);
            l.vista = linha;
            lista.addView(linha);
            linhas.add(l);
        }
        col.addView(lista);
        preencher.setOnClickListener(v -> {
            for (LinhaDoLote l : linhas) {
                if (!l.titulo.isEmpty()) l.campo.setText(Limpeza.nomeSugerido("NA", "NA", l.titulo));
            }
        });
        automatico.setOnClickListener(v -> {
            for (LinhaDoLote l : linhas) l.campo.setText("");
        });
        ScrollView rolagem = new ScrollView(this);
        rolagem.addView(col);
        new Construtor()
                .setTitle(total + " músicas")
                .setView(rolagem)
                .setPositiveButton("Baixar tudo", (d, q) -> {
                    List<String> e = new ArrayList<>();
                    List<String> nomes = new ArrayList<>();
                    for (LinhaDoLote l : linhas) {
                        if (l.removida) continue;
                        e.add(l.entrada);
                        nomes.add(Pedido.nomeDoArquivo(l.campo.getText().toString()));
                    }
                    if (e.isEmpty()) return;
                    colocarNaFila(e, nomes);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Com a pergunta ligada, busca o titulo e deixa a pessoa renomear antes de baixar. */
    private void iniciarDownload(final String texto) {
        if (Pedido.entrada(texto).isEmpty()) {
            Toast.makeText(this, "Escreva um link ou o nome da música.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!prefs.baixarPerguntarNome()) {
            enviarDownload(texto, "");
            return;
        }
        final boolean[] cancelado = {false};
        final AlertDialog espera = new Construtor()
                .setMessage("Buscando a música…")
                .setNegativeButton("Cancelar", (d, q) -> cancelado[0] = true)
                .setOnCancelListener(d -> cancelado[0] = true)
                .show();
        new Thread(() -> {
            final String titulo = Baixador.titulo(this, texto);
            ui.post(() -> {
                if (cancelado[0] || isFinishing()) return;
                espera.dismiss();
                perguntarONome(texto, titulo);
            });
        }).start();
    }

    private void perguntarONome(final String texto, String sugestao) {
        final EditText campo = campoDeTexto(sugestao);
        campo.setHint("Nome do arquivo");
        new Construtor()
                .setTitle("Nome do arquivo")
                .setView(caixaDoCampo(campo))
                .setPositiveButton("Baixar", (d, q) -> enviarDownload(texto, campo.getText().toString()))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void enviarDownload(String texto, String nome) {
        List<String> e = new ArrayList<>();
        List<String> n = new ArrayList<>();
        e.add(texto);
        n.add(Pedido.nomeDoArquivo(nome));
        colocarNaFila(e, n);
    }

    /** Põe os pedidos na fila e acorda o serviço, que baixa duas músicas por vez. */
    private void colocarNaFila(List<String> entradas, List<String> nomes) {
        if (prefs.arvore() == null) {
            Toast.makeText(this, "Escolha primeiro a pasta de músicas.", Toast.LENGTH_LONG).show();
            escolherPasta();
            return;
        }
        if (!FilaDeDownload.ativa()) FilaDeDownload.limparConcluidos();
        FilaDeDownload.adicionar(entradas, nomes, prefs.arvore(), prefs.baixarFormato(), prefs.baixarTaxa());
        Intent servico = new Intent(this, BaixarService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(servico);
        else startService(servico);
        Toast.makeText(this, entradas.size() == 1 ? "Baixando… o aviso aparece quando terminar." : entradas.size() + " músicas na fila.", Toast.LENGTH_LONG).show();
        atualizarFilaInicio();
    }

    /** Tela da fila: andamento de cada música, com cancelar os que faltam, limpar os prontos e tentar de novo os que falharam. */
    private void abrirFila() {
        final LinearLayout lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        lista.setPadding(dp(22), dp(8), dp(22), 0);
        ScrollView rolagem = new ScrollView(this);
        rolagem.addView(lista);
        final AlertDialog d = new Construtor()
                .setTitle("Fila de downloads")
                .setView(rolagem)
                .setPositiveButton("Fechar", null)
                .setNegativeButton("Cancelar os que faltam", null)
                .setNeutralButton("Limpar prontos", null)
                .create();
        redesenharFila = () -> {
            lista.removeAllViews();
            List<FilaDeDownload.Item> itens = FilaDeDownload.copia();
            if (itens.isEmpty()) {
                TextView vazio = texto(14, SUAVE, false);
                vazio.setText("A fila está vazia.");
                lista.addView(vazio);
            }
            String[] glifo = {"…", "↓", "✓", "✕"};
            for (FilaDeDownload.Item it : itens) {
                TextView t = texto(14, it.estado == FilaDeDownload.ERRO ? 0xFFE5484D : (it.estado == FilaDeDownload.OK ? ACENTO : TEXTO), false);
                t.setSingleLine(true);
                t.setEllipsize(TextUtils.TruncateAt.END);
                t.setText(glifo[Math.max(0, Math.min(3, it.estado))] + "  " + it.rotulo());
                t.setPadding(0, dp(6), 0, dp(6));
                lista.addView(t);
                if (it.estado == FilaDeDownload.ERRO && !it.mensagem.isEmpty()) {
                    TextView m = texto(12, SUAVE, false);
                    m.setText(it.mensagem);
                    lista.addView(m);
                }
            }
        };
        d.setOnDismissListener(x -> redesenharFila = null);
        d.show();
        try {
            if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(new android.graphics.drawable.InsetDrawable(forma(CARTAO, 22, LINHA), dp(6)));
        } catch (Exception e) {
            // o visual padrao do dialogo continua valendo
        }
        d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ACENTO);
        d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(SUAVE);
        d.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(SUAVE);
        d.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> FilaDeDownload.cancelarPendentes());
        d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            if (FilaDeDownload.contagem()[FilaDeDownload.ERRO] > 0 && !FilaDeDownload.ativa()) {
                // com erros e nada rodando, o botão serve para tentar de novo
                int n = FilaDeDownload.tentarDeNovo();
                if (n > 0) startServicoDeDownload();
            } else {
                FilaDeDownload.limparConcluidos();
            }
        });
        redesenharFila.run();
    }

    private void startServicoDeDownload() {
        Intent servico = new Intent(this, BaixarService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(servico);
        else startService(servico);
    }

    /** Categorias de ajustes, como no app do PC. */
    private void menuDeAjustes() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(22), dp(8), dp(22), 0);
        final AlertDialog[] aberto = new AlertDialog[1];
        String[] nomes = {"Aparência", "Download"};
        Runnable[] acoes = {this::escolherCores, this::ajustesDoDownload};
        for (int i = 0; i < nomes.length; i++) {
            final Runnable acao = acoes[i];
            TextView linha = linhaDeAjuste();
            linha.setText(nomes[i]);
            linha.setOnClickListener(v -> {
                aberto[0].dismiss();
                acao.run();
            });
            col.addView(linha);
        }
        aberto[0] = new Construtor().setTitle("Ajustes").setView(col).setNegativeButton("Fechar", null).show();
    }

    /** Formato, taxa de bits e se pergunta o nome do arquivo antes de baixar (como nos ajustes do PC). */
    private void ajustesDoDownload() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(22), dp(8), dp(22), 0);
        final TextView formato = linhaDeAjuste();
        final TextView taxa = linhaDeAjuste();
        final Runnable atualizar = () -> {
            formato.setText("Formato do áudio:  " + prefs.baixarFormato().toUpperCase());
            taxa.setText("Taxa de bits:  " + prefs.baixarTaxa() + " kbps");
        };
        atualizar.run();
        formato.setOnClickListener(v -> {
            prefs.baixarFormato(Pedido.proximoFormato(prefs.baixarFormato()));
            atualizar.run();
        });
        taxa.setOnClickListener(v -> {
            prefs.baixarTaxa(Pedido.proximaTaxa(prefs.baixarTaxa()));
            atualizar.run();
        });
        final android.widget.CheckBox perguntar = new android.widget.CheckBox(this);
        perguntar.setText("Perguntar o nome antes de baixar");
        perguntar.setTextColor(TEXTO);
        perguntar.setButtonTintList(ColorStateList.valueOf(ACENTO));
        perguntar.setChecked(prefs.baixarPerguntarNome());
        perguntar.setOnCheckedChangeListener((v, marcado) -> prefs.baixarPerguntarNome(marcado));
        final TextView atualizarBaixador = linhaDeAjuste();
        atualizarBaixador.setText("Atualizar o download");
        atualizarBaixador.setOnClickListener(v -> {
            Toast.makeText(this, "Atualizando o baixador…", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                final boolean deu = Baixador.atualizarAgora(this);
                ui.post(() -> {
                    if (!isFinishing()) Toast.makeText(this, deu ? "Download atualizado." : "Não consegui atualizar agora. Confira a internet.", Toast.LENGTH_LONG).show();
                });
            }).start();
        });
        col.addView(formato);
        col.addView(taxa);
        col.addView(perguntar);
        col.addView(atualizarBaixador);
        new Construtor()
                .setTitle("Download")
                .setView(col)
                .setPositiveButton("Pronto", (d, q) -> menuDeAjustes())
                .show();
    }

    private TextView linhaDeAjuste() {
        TextView t = texto(15, TEXTO, false);
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        t.setBackground(toque(forma(CARTAO, 12, LINHA), forma(0xFF000000, 12, 0)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        t.setLayoutParams(lp);
        return t;
    }

    // ------------------------------------------------------------------ baixar no PC
    /** Pede ao Raccoon Music do PC (mesma rede) para baixar um link ou nome de musica. */
    private void baixarNoPc() {
        if (PcRemoto.endereco(prefs.pcEndereco()).isEmpty() || PcRemoto.codigo(prefs.pcCodigo()).isEmpty()) {
            conectarAoPc(this::baixarNoPc);
            return;
        }
        final EditText campo = campoDeTexto("");
        campo.setHint("Link ou nome da música");
        campo.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
        new Construtor()
                .setTitle("Baixar no PC")
                .setView(caixaDoCampo(campo))
                .setPositiveButton("Enviar", (d, q) -> enviarPedido(campo.getText().toString()))
                .setNeutralButton("PC", (d, q) -> conectarAoPc(this::baixarNoPc))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void enviarPedido(String texto) {
        final String pedido = PcRemoto.pedido(texto);
        if (pedido.isEmpty()) {
            Toast.makeText(this, PcRemoto.mensagem(400), Toast.LENGTH_SHORT).show();
            return;
        }
        final String endereco = PcRemoto.endereco(prefs.pcEndereco());
        final String codigo = PcRemoto.codigo(prefs.pcCodigo());
        Toast.makeText(this, "Enviando ao PC…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final int status = PcRemoto.enviar(endereco, codigo, pedido);
            ui.post(() -> {
                if (!isFinishing()) Toast.makeText(this, PcRemoto.mensagem(status), Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    /** Endereco e codigo do PC (os mesmos da janela "Celular" do Raccoon Music no computador). */
    private void conectarAoPc(final Runnable depois) {
        final EditText endereco = campoDeTexto(prefs.pcEndereco());
        endereco.setHint("Endereço do PC (ex.: 192.168.0.5:8765)");
        final EditText codigo = campoDeTexto(prefs.pcCodigo());
        codigo.setHint("Código");
        codigo.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(caixaDoCampo(endereco));
        col.addView(caixaDoCampo(codigo));
        new Construtor()
                .setTitle("PC")
                .setView(col)
                .setPositiveButton("Salvar", (d, q) -> {
                    String e = PcRemoto.endereco(endereco.getText().toString());
                    String c = PcRemoto.codigo(codigo.getText().toString());
                    if (c.isEmpty()) c = PcRemoto.codigo(endereco.getText().toString());  // link completo colado no endereco
                    if (e.isEmpty() || c.isEmpty()) {
                        Toast.makeText(this, "Confira o endereço e o código mostrados no PC.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    prefs.pc(e, c);
                    if (depois != null) depois.run();
                })
                .setNegativeButton("Cancelar", null)
                .show();
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

    /** Capas em duas colunas (albuns, generos e playlists): a capa da primeira musica e o nome embaixo. */
    private final class AdaptadorGrade extends BaseAdapter {
        @Override public int getCount() { return grupos.size(); }
        @Override public Object getItem(int i) { return grupos.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View v, ViewGroup pai) {
            final Linha l;
            int lado = (getResources().getDisplayMetrics().widthPixels - dp(16 + 16 + 14)) / 2;
            if (v == null) {
                final Linha nova = new Linha();
                LinearLayout cel = new LinearLayout(MainActivity.this);
                cel.setOrientation(LinearLayout.VERTICAL);
                nova.caixaCapa = new FrameLayout(MainActivity.this);
                nova.caixaCapa.setBackground(forma(CARTAO, 16, LINHA));
                arredondar(nova.caixaCapa, 16);
                nova.capa = new ImageView(MainActivity.this);
                nova.capa.setScaleType(ImageView.ScaleType.CENTER_CROP);
                nova.caixaCapa.addView(nova.capa, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                nova.nota = new IconeView(MainActivity.this, IconeView.NOTA);
                nova.nota.definir(IconeView.NOTA, SUAVE);
                nova.caixaCapa.addView(nova.nota, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
                cel.addView(nova.caixaCapa, new LinearLayout.LayoutParams(lado, lado));
                nova.titulo = rolante(15, TEXTO, true);
                nova.titulo.setPadding(dp(2), dp(8), dp(2), 0);
                nova.artista = rolante(12, SUAVE, false);
                nova.artista.setPadding(dp(2), dp(1), dp(2), 0);
                cel.addView(nova.titulo);
                cel.addView(nova.artista);
                cel.setTag(nova);
                v = cel;
                l = nova;
            } else {
                l = (Linha) v.getTag();
                ViewGroup.LayoutParams pc = l.caixaCapa.getLayoutParams();
                if (pc.height != lado) {
                    pc.width = lado;
                    pc.height = lado;
                    l.caixaCapa.setLayoutParams(pc);
                }
            }
            if (i < grupos.size()) {
                Grupo g = grupos.get(i);
                l.titulo.setText(g.titulo);
                l.artista.setText(g.sub);
                ligarCapa(l, g.faixas.isEmpty() ? null : g.faixas.get(0));
            }
            return v;
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
                nova.titulo = rolante(16, TEXTO, false);
                nova.artista = rolante(13, SUAVE, false);
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
                        if (selecionando) alternarMarca(visiveis.get(nova.pos));
                        else opcoesDaFaixa(visiveis.get(nova.pos));
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
                v.setBackground(toque(null, new android.graphics.drawable.ColorDrawable(0xFF000000)));
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
            boolean marcada = selecionando && marcadas.contains(f.uri);
            l.titulo.setTextColor(tocando || marcada ? ACENTO : TEXTO);
            v.setBackground(marcada ? forma((ACENTO & 0x00FFFFFF) | 0x33000000, 0, 0) : toque(null, new android.graphics.drawable.ColorDrawable(0xFF000000)));
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
