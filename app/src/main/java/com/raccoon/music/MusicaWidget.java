package com.raccoon.music;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

/** Widget da tela inicial: música tocando e os botões anterior, tocar/pausar e próxima. O estado vem do PlaybackService. */
public class MusicaWidget extends AppWidgetProvider {
    private static final String ARQUIVO = "widget";

    @Override
    public void onUpdate(Context contexto, AppWidgetManager gerente, int[] ids) {
        atualizar(contexto);
    }

    /** Guarda o que o widget mostra e o redesenha. */
    public static void guardar(Context contexto, String titulo, String artista, boolean tocando) {
        contexto.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).edit()
                .putString("titulo", titulo == null ? "" : titulo)
                .putString("artista", artista == null ? "" : artista)
                .putBoolean("tocando", tocando)
                .apply();
        atualizar(contexto);
    }

    public static void atualizar(Context contexto) {
        AppWidgetManager gerente = AppWidgetManager.getInstance(contexto);
        int[] ids = gerente.getAppWidgetIds(new ComponentName(contexto, MusicaWidget.class));
        if (ids.length == 0) return;
        SharedPreferences sp = contexto.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE);
        String titulo = sp.getString("titulo", "");
        boolean tocando = sp.getBoolean("tocando", false);
        RemoteViews v = new RemoteViews(contexto.getPackageName(), R.layout.widget_musica);
        v.setTextViewText(R.id.w_titulo, titulo.isEmpty() ? "Raccoon Music" : titulo);
        v.setTextViewText(R.id.w_artista, titulo.isEmpty() ? "Toque para abrir" : sp.getString("artista", ""));
        v.setImageViewResource(R.id.w_tocar, tocando ? R.drawable.ic_widget_pausar : R.drawable.ic_widget_tocar);
        v.setOnClickPendingIntent(R.id.w_anterior, comando(contexto, PlaybackService.ACAO_WIDGET_ANTERIOR, 1));
        v.setOnClickPendingIntent(R.id.w_tocar, comando(contexto, PlaybackService.ACAO_WIDGET_TOCAR, 2));
        v.setOnClickPendingIntent(R.id.w_proxima, comando(contexto, PlaybackService.ACAO_WIDGET_PROXIMA, 3));
        v.setOnClickPendingIntent(R.id.w_raiz, PendingIntent.getActivity(contexto, 4,
                new Intent(contexto, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        gerente.updateAppWidget(ids, v);
    }

    private static PendingIntent comando(Context contexto, String acao, int codigo) {
        Intent i = new Intent(contexto, PlaybackService.class).setAction(acao);
        return PendingIntent.getService(contexto, codigo, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
