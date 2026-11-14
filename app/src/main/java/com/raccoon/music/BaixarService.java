package com.raccoon.music;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Mantém a fila de downloads rodando com a tela apagada (dois por vez) e mostra o andamento e o resultado por notificação. */
public class BaixarService extends Service {
    public static final String ACAO_FIM = "com.raccoon.music.BAIXOU";
    public static final String EXTRA_OK = "ok";
    public static final String EXTRA_MENSAGEM = "mensagem";
    public static final String EXTRA_FIM_DA_FILA = "fim";
    private static final String CANAL = "baixar";
    private static final int ID_ANDAMENTO = 4001;
    private static final int ID_FIM = 4002;
    private static final int TRABALHADORES = 2;

    private final Object trava = new Object();
    private int rodando;
    private int prontosNestaRodada;
    private int falhasNestaRodada;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, final int idDaTarefa) {
        criarCanal();
        // o sistema pode ter encerrado o app no meio da fila: recoloca o que faltava
        FilaDeDownload.restaurar(new Preferencias(this).filaDeDownload());
        Notification andamento = montar(tituloDoAndamento(), "Preparando…", true, 0, 0);
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID_ANDAMENTO, andamento, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(ID_ANDAMENTO, andamento);
        synchronized (trava) {
            while (rodando < TRABALHADORES && FilaDeDownload.contagem()[FilaDeDownload.PENDENTE] > 0) {
                rodando++;
                new Thread(this::trabalhar).start();
            }
            if (rodando == 0) {
                stopForeground(true);
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }

    private void trabalhar() {
        FilaDeDownload.Item item;
        while ((item = FilaDeDownload.proximo()) != null) {
            atualizarAndamento(item.rotulo());
            Baixador.Resultado r;
            try {
                final FilaDeDownload.Item atual = item;
                r = Baixador.baixar(this, item.entrada, item.arvore, item.nome, item.formato, item.taxa, (e, t) -> FilaDeDownload.etapa(atual, e, t));
            } catch (Throwable e) {
                r = new Baixador.Resultado(false, "Não consegui baixar essa música.");
            }
            FilaDeDownload.concluir(item, r.ok, r.mensagem);
            new Preferencias(this).filaDeDownload(FilaDeDownload.serializar());  // com o app fechado, só o serviço sabe o que já terminou
            synchronized (trava) {
                if (r.ok) prontosNestaRodada++;
                else falhasNestaRodada++;
            }
            boolean fim = !FilaDeDownload.ativa();
            sendBroadcast(new Intent(ACAO_FIM).setPackage(getPackageName())
                    .putExtra(EXTRA_OK, r.ok).putExtra(EXTRA_MENSAGEM, r.mensagem).putExtra(EXTRA_FIM_DA_FILA, fim));
        }
        boolean ultimo;
        synchronized (trava) {
            rodando--;
            ultimo = rodando == 0;
        }
        if (ultimo) terminar();
    }

    private void terminar() {
        int prontos;
        int falhas;
        synchronized (trava) {
            prontos = prontosNestaRodada;
            falhas = falhasNestaRodada;
            prontosNestaRodada = 0;
            falhasNestaRodada = 0;
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null && prontos + falhas > 0) {
            String titulo = prontos == 1 && falhas == 0 ? "Música baixada" : prontos + (prontos == 1 ? " música baixada" : " músicas baixadas");
            String texto = falhas == 0 ? "Pronto." : falhas + (falhas == 1 ? " não pôde ser baixada. Abra a fila no app para tentar de novo." : " não puderam ser baixadas. Abra a fila no app para tentar de novo.");
            nm.notify(ID_FIM, montar(titulo, texto, false, 0, 0));
        }
        stopForeground(true);
        stopSelf();
    }

    private String tituloDoAndamento() {
        int[] c = FilaDeDownload.contagem();
        int total = c[0] + c[1] + c[2] + c[3];
        int feitos = c[2] + c[3];
        return total > 1 ? "Baixando " + Math.min(total, feitos + 1) + " de " + total : "Baixando…";
    }

    private void atualizarAndamento(String texto) {
        int[] c = FilaDeDownload.contagem();
        int total = c[0] + c[1] + c[2] + c[3];
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(ID_ANDAMENTO, montar(tituloDoAndamento(), texto, true, total, c[2] + c[3]));
    }

    private void criarCanal() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.createNotificationChannel(new NotificationChannel(CANAL, "Downloads", NotificationManager.IMPORTANCE_LOW));
    }

    private Notification montar(String titulo, String texto, boolean emAndamento, int total, int feitos) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CANAL) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setOngoing(emAndamento)
                .setAutoCancel(!emAndamento);
        b.setStyle(new Notification.BigTextStyle().bigText(texto));
        if (emAndamento) {
            if (total > 1) b.setProgress(total, feitos, false);
            else b.setProgress(0, 0, true);
        }
        return b.build();
    }
}
