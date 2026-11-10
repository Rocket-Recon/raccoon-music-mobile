package com.raccoon.music;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
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
        player.addListener(new Player.Listener() {
            @Override
            public void onRepeatModeChanged(int modo) {
                prefs.repetir(modo);
                redesenharBotoes();
            }
        });
        sessao = new MediaSession.Builder(this, player)
                .setCallback(new Retorno())
                .setCustomLayout(botoes())
                .build();
    }

    private ImmutableList<CommandButton> botoes() {
        int repetir = prefs.repetir();
        int iconeRepetir = repetir == Player.REPEAT_MODE_ONE ? R.drawable.ic_notif_repetir_um
                : repetir == Player.REPEAT_MODE_ALL ? R.drawable.ic_notif_repetir_all : R.drawable.ic_notif_repetir_off;
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
        int atual = p.getRepeatMode();
        p.setRepeatMode(atual == Player.REPEAT_MODE_OFF ? Player.REPEAT_MODE_ALL
                : atual == Player.REPEAT_MODE_ALL ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
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
