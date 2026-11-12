package com.raccoon.music;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.CommandButton;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionCommands;
import androidx.media3.session.SessionResult;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Quem toca de verdade. Fica rodando com a tela apagada e mostra a notificacao com os controles
 * (inclusive aleatorio e repetir); os botoes do fone, do Bluetooth e do volante do carro tambem chegam por aqui.
 */
public class PlaybackService extends MediaSessionService {
    private static final String ACAO_ALEATORIO = "com.raccoon.music.ALEATORIO";
    private static final String ACAO_REPETIR = "com.raccoon.music.REPETIR";
    public static final String ACAO_WIDGET_ANTERIOR = "com.raccoon.music.W_ANTERIOR";
    public static final String ACAO_WIDGET_TOCAR = "com.raccoon.music.W_TOCAR";
    public static final String ACAO_WIDGET_PROXIMA = "com.raccoon.music.W_PROXIMA";

    /** Definido pela tela principal enquanto ela existe: o aleatorio é feito lá (reordena a fila pela ordem da biblioteca). */
    public static volatile Runnable aoAlternarAleatorio;
    private static volatile PlaybackService instancia;

    private MediaSession sessao;
    private Preferencias prefs;
    private final Handler principal = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        instancia = this;
        prefs = new Preferencias(this);
        AudioAttributes atributos = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();
        ExoPlayer player = new ExoPlayer.Builder(this)
                .setAudioAttributes(atributos, true)   // respeita ligacoes e outros apps de som
                .setHandleAudioBecomingNoisy(true)     // pausa se o fone ou o Bluetooth desconectar
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build();
        player.setRepeatMode(prefs.modoDoPlayer());
        player.addListener(new Player.Listener() {
            @Override
            public void onRepeatModeChanged(int modo) {
                prefs.repetir(modo);
                redesenharBotoes();
            }

            @Override
            public void onMediaMetadataChanged(androidx.media3.common.MediaMetadata metadados) {
                atualizarWidget();
            }

            @Override
            public void onIsPlayingChanged(boolean tocando) {
                atualizarWidget();
            }
        });
        sessao = new MediaSession.Builder(this, new PlayerComBotoesFixos(player, this))
                .setCallback(new Retorno())
                .setCustomLayout(botoes())
                .build();
    }

    /**
     * Mantém "anterior" e "próxima" sempre disponíveis. Sem a próxima (última da fila) o sistema remaneja os botões da
     * notificação e a ordem de aleatório/repetir muda; aqui a próxima da última volta ao começo da fila.
     */
    private static final class PlayerComBotoesFixos extends ForwardingPlayer {
        private final android.content.Context contexto;

        PlayerComBotoesFixos(Player p, android.content.Context contexto) {
            super(p);
            this.contexto = contexto;
        }

        /** Sem conta liberada (login e licença ou modo grátis) o app não toca, nem pela notificação, fone ou widget. */
        private boolean podeTocar() {
            return Conta.liberado(contexto);
        }

        @Override
        public void play() {
            if (podeTocar()) super.play();
        }

        @Override
        public void setPlayWhenReady(boolean tocar) {
            if (!tocar || podeTocar()) super.setPlayWhenReady(tocar);
        }

        @Override
        public Player.Commands getAvailableCommands() {
            Player.Commands base = super.getAvailableCommands();
            if (getMediaItemCount() == 0) return base;
            return base.buildUpon().addAll(COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM).build();
        }

        @Override
        public boolean isCommandAvailable(int comando) {
            return getAvailableCommands().contains(comando);
        }

        @Override
        public boolean hasNextMediaItem() {
            return getMediaItemCount() > 0;
        }

        @Override
        public void seekToNext() {
            if (super.hasNextMediaItem()) super.seekToNext();
            else if (getMediaItemCount() > 0) seekTo(0, C.TIME_UNSET);
        }

        @Override
        public void seekToNextMediaItem() {
            if (super.hasNextMediaItem()) super.seekToNextMediaItem();
            else if (getMediaItemCount() > 0) seekTo(0, C.TIME_UNSET);
        }
    }

    /** Passa a música de agora e o estado (tocando ou pausado) ao widget da tela inicial. */
    private void atualizarWidget() {
        if (sessao == null) return;
        Player p = sessao.getPlayer();
        androidx.media3.common.MediaMetadata md = p.getMediaMetadata();
        MusicaWidget.guardar(this, md.title == null ? "" : md.title.toString(), md.artist == null ? "" : md.artist.toString(), p.isPlaying());
    }

    /** Botões do widget: chegam como Intent de serviço, já que ele não tem como falar com a sessão de mídia. */
    @Override
    public int onStartCommand(Intent intent, int flags, int idDaTarefa) {
        String acao = intent == null ? null : intent.getAction();
        if (acao != null && acao.startsWith("com.raccoon.music.W_") && sessao != null) {
            Player p = sessao.getPlayer();
            if (p.getMediaItemCount() == 0) {
                stopSelf();  // nada na fila: o app fechado não tem o que tocar
                return START_NOT_STICKY;
            }
            if (ACAO_WIDGET_TOCAR.equals(acao)) {
                if (p.isPlaying()) p.pause();
                else if (Conta.liberado(this)) p.play();
            } else if (ACAO_WIDGET_PROXIMA.equals(acao)) {
                p.seekToNextMediaItem();
            } else if (ACAO_WIDGET_ANTERIOR.equals(acao)) {
                p.seekToPreviousMediaItem();
            }
        }
        return super.onStartCommand(intent, flags, idDaTarefa);
    }

    private ImmutableList<CommandButton> botoes() {
        int iconeRepetir = prefs.repetindo() ? R.drawable.ic_notif_repetir_all : R.drawable.ic_notif_repetir_off;
        CommandButton aleatorio = new CommandButton.Builder()
                .setDisplayName("Aleatório")
                .setIconResId(prefs.embaralhar() ? R.drawable.ic_notif_aleatorio_on : R.drawable.ic_notif_aleatorio_off)
                .setSessionCommand(new SessionCommand(ACAO_ALEATORIO, Bundle.EMPTY))
                .build();
        CommandButton repetirBotao = new CommandButton.Builder()
                .setDisplayName("Repetir")
                .setIconResId(iconeRepetir)
                .setSessionCommand(new SessionCommand(ACAO_REPETIR, Bundle.EMPTY))
                .build();
        return ImmutableList.of(aleatorio, repetirBotao);
    }

    private void redesenharBotoes() {
        if (sessao != null) sessao.setCustomLayout(botoes());
    }

    /** Chamado pela tela quando o aleatorio ou o repetir mudam, para a notificacao acompanhar. */
    public static void atualizarBotoes() {
        final PlaybackService s = instancia;
        if (s != null) s.principal.post(s::redesenharBotoes);
    }

    private void alternarAleatorio() {
        Runnable tela = aoAlternarAleatorio;
        if (tela != null) {
            tela.run();
            return;
        }
        // sem a tela aberta: alterna e, ao ligar, embaralha o que ainda vai tocar
        boolean novo = !prefs.embaralhar();
        prefs.embaralhar(novo);
        Player p = sessao.getPlayer();
        int n = p.getMediaItemCount();
        int atual = p.getCurrentMediaItemIndex();
        if (novo && n > 0 && atual >= 0 && atual + 1 < n) {
            List<MediaItem> resto = new ArrayList<>();
            for (int i = atual + 1; i < n; i++) resto.add(p.getMediaItemAt(i));
            Collections.shuffle(resto);
            p.replaceMediaItems(atual + 1, n, resto);
        }
        redesenharBotoes();
    }

    private void alternarRepetir() {
        Player p = sessao.getPlayer();
        prefs.repetindo(!prefs.repetindo());
        p.setRepeatMode(prefs.modoDoPlayer());
    }

    private final class Retorno implements MediaSession.Callback {
        @Override
        public MediaSession.ConnectionResult onConnect(MediaSession s, MediaSession.ControllerInfo controlador) {
            SessionCommands comandos = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(new SessionCommand(ACAO_ALEATORIO, Bundle.EMPTY))
                    .add(new SessionCommand(ACAO_REPETIR, Bundle.EMPTY))
                    .build();
            return MediaSession.ConnectionResult.accept(comandos, MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS);
        }

        @Override
        public ListenableFuture<SessionResult> onCustomCommand(MediaSession s, MediaSession.ControllerInfo controlador, SessionCommand comando, Bundle extras) {
            if (ACAO_ALEATORIO.equals(comando.customAction)) alternarAleatorio();
            else if (ACAO_REPETIR.equals(comando.customAction)) alternarRepetir();
            return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
        }
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controlador) {
        return sessao;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Player p = sessao.getPlayer();
        if (!p.getPlayWhenReady() || p.getMediaItemCount() == 0) stopSelf();  // pausado e fechado: encerra
    }

    @Override
    public void onDestroy() {
        instancia = null;
        if (sessao != null) {
            sessao.getPlayer().release();
            sessao.release();
            sessao = null;
        }
        super.onDestroy();
    }
}
