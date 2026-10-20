package com.raccoon.music;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Atualiza o app por cima, sem desinstalar: confere o numero da versao no GitHub e baixa o APK novo. */
public final class Atualizador {
    private Atualizador() {}

    public interface Progresso {
        void porcento(int valor);
    }

    private static String base() {
        return "https://github.com/" + BuildConfig.REPO + "/releases/download/mobile-latest/";
    }

    public static boolean disponivel() {
        return !BuildConfig.REPO.isEmpty();
    }

    public static int instalada(Context contexto) {
        try {
            PackageInfo info = contexto.getPackageManager().getPackageInfo(contexto.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? (int) info.getLongVersionCode() : info.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    private static HttpURLConnection abrir(String endereco) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(endereco).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(20_000);
        c.setInstanceFollowRedirects(true);
        return c;
    }

    /** Numero da versao mais nova publicada, ou -1 se nao deu para saber (sem internet, por exemplo). */
    public static int maisNova() {
        if (!disponivel()) return -1;
        try {
            HttpURLConnection c = abrir(base() + "versao.txt");
            if (c.getResponseCode() != 200) return -1;
            try (InputStream in = c.getInputStream()) {
                byte[] b = new byte[32];
                int n = in.read(b);
                return n <= 0 ? -1 : Integer.parseInt(new String(b, 0, n, "UTF-8").trim());
            }
        } catch (Exception e) {
            return -1;
        }
    }

    /** Baixa o APK novo para a pasta temporaria do app; devolve o arquivo ou null se falhou. */
    public static File baixar(Context contexto, Progresso progresso) {
        File pasta = new File(contexto.getCacheDir(), "atualizacao");
        pasta.mkdirs();
        File destino = new File(pasta, "Raccoon-Music-Mobile.apk");
        try {
            HttpURLConnection c = abrir(base() + "Raccoon-Music-Mobile.apk");
            if (c.getResponseCode() != 200) return null;
            long total = c.getContentLengthLong();
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(destino)) {
                byte[] buffer = new byte[64 * 1024];
                long lido = 0;
                int ultimo = -1;
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                    lido += n;
                    int p = total > 0 ? (int) (lido * 100 / total) : 0;
                    if (progresso != null && p != ultimo) {
                        ultimo = p;
                        progresso.porcento(p);
                    }
                }
            }
            return destino.length() > 0 ? destino : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean podeInstalar(Context contexto) {
        return Build.VERSION.SDK_INT < 26 || contexto.getPackageManager().canRequestPackageInstalls();
    }

    public static void pedirPermissao(Activity atividade) {
        Intent i = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + atividade.getPackageName()));
        atividade.startActivity(i);
    }

    public static void instalar(Activity atividade, File apk) {
        Uri uri = FileProvider.getUriForFile(atividade, "com.raccoon.music.arquivos", apk);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        atividade.startActivity(i);
    }
}
