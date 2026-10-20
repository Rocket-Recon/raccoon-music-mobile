package com.raccoon.music;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Ler e gravar um arquivo de texto na pasta do app. */
public final class Arquivos {
    private Arquivos() {}

    public static String ler(Context c, String nome) {
        File f = new File(c.getFilesDir(), nome);
        if (!f.exists()) return "";
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    public static void gravar(Context c, String nome, String texto) {
        File f = new File(c.getFilesDir(), nome);
        File tmp = new File(c.getFilesDir(), nome + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(texto.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return;
        }
        if (!tmp.renameTo(f)) {
            f.delete();
            tmp.renameTo(f);
        }
    }
}
