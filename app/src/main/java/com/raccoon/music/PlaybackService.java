package com.raccoon.music;

import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

/**
 * Quem toca de verdade. Fica rodando com a tela apagada e mostra a notificacao com os controles;
 * os botoes do fone, do Bluetooth e do volante do carro tambem chegam por aqui.
 */
public class PlaybackService extends MediaSessionService {
    private MediaSession sessao;

    @Override
    public void onCreate() {
        super.onCreate();
        AudioAttributes atributos = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();
        ExoPlayer player = new ExoPlayer.Builder(this)
                .setAudioAttributes(atributos, true)   // respeita ligacoes e outros apps de som
                .setHandleAudioBecomingNoisy(true)     // pausa se o fone ou o Bluetooth desconectar
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build();
        sessao = new MediaSession.Builder(this, player).build();
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controlador) {
        return sessao;
    }

    @Override
    public void onTaskRemoved(@Nullable Intent rootIntent) {
        Player p = sessao.getPlayer();
        if (!p.getPlayWhenReady() || p.getMediaItemCount() == 0) stopSelf();  // pausado e fechado: encerra
    }

    @Override
    public void onDestroy() {
        if (sessao != null) {
            sessao.getPlayer().release();
            sessao.release();
            sessao = null;
        }
        super.onDestroy();
    }
}
