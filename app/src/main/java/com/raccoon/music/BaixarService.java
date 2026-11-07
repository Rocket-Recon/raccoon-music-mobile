package com.raccoon.music;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Mantem o download rodando com a tela apagada e avisa o resultado por notificacao. */
public class BaixarService extends Service {
    public static final String EXTRA_PEDIDO = "pedido";
    public static final String EXTRA_ARVORE = "arvore";
    public static final String ACAO_FIM = "com.raccoon.music.BAIXOU";
    public static final String EXTRA_OK = "ok";
    public static final String EXTRA_MENSAGEM = "mensagem";
    private static final String CANAL = "baixar";
    private static final int ID_ANDAMENTO = 4001;
    private static final int ID_FIM = 4002;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, final int idDaTarefa) {
        final String pedido = intent == null ? null : intent.getStringExtra(EXTRA_PEDIDO);
        final String arvore = intent == null ? null : intent.getStringExtra(EXTRA_ARVORE);
        if (pedido == null || arvore == null) {
            stopSelf(idDaTarefa);
            return START_NOT_STICKY;
        }
        criarCanal();
        Notification andamento = montar("Baixando…", pedido, true);
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID_ANDAMENTO, andamento, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(ID_ANDAMENTO, andamento);
        new Thread(() -> {
            Baixador.Resultado r = Baixador.baixar(this, pedido, arvore);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(ID_FIM, montar(r.ok ? "Música baixada" : "Não foi possível baixar", r.mensagem, false));
            sendBroadcast(new Intent(ACAO_FIM).setPackage(getPackageName()).putExtra(EXTRA_OK, r.ok).putExtra(EXTRA_MENSAGEM, r.mensagem));
            stopForeground(true);
            stopSelf(idDaTarefa);
        }).start();
        return START_NOT_STICKY;
    }

    private void criarCanal() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.createNotificationChannel(new NotificationChannel(CANAL, "Downloads", NotificationManager.IMPORTANCE_LOW));
    }

    private Notification montar(String titulo, String texto, boolean emAndamento) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CANAL) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setOngoing(emAndamento)
                .setAutoCancel(!emAndamento);
        if (emAndamento) b.setProgress(0, 0, true);
        return b.build();
    }
}
