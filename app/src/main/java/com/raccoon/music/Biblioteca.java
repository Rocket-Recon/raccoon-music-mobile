package com.raccoon.music;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Procura as musicas na pasta escolhida (memoria do celular ou pendrive) e guarda a lista para abrir rapido. */
public final class Biblioteca {
    public interface Progresso { void achou(int quantas); }

    private static final String ARQUIVO = "biblioteca.tsv";
    private static final String ARQUIVO_SINCRONIZACAO = "celular.tsv";
    private static final int LIMITE_SINCRONIZACAO = 5_000_000;

    /** O que a busca achou: as musicas e, se a pasta tinha, o texto do arquivo de curtidas/playlists do PC. */
    public static final class Resultado {
        public final List<Faixa> faixas;
        public final String sincronizacao;

        Resultado(List<Faixa> faixas, String sincronizacao) {
            this.faixas = faixas;
            this.sincronizacao = sincronizacao;
        }
    }

    private Biblioteca() {}

    public static List<Faixa> carregar(Context c) {
        File f = new File(c.getFilesDir(), ARQUIVO);
        if (!f.exists()) return new ArrayList<>();
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return Catalogo.ler(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private static String lerTexto(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0 && out.size() < LIMITE_SINCRONIZACAO) out.write(b, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /** A ultima sincronizacao importada do PC (guardada no celular, para as playlists existirem sem o pendrive). */
    public static String sincronizacaoGuardada(Context c) {
        File f = new File(c.getFilesDir(), ARQUIVO_SINCRONIZACAO);
        if (!f.exists()) return "";
        try (FileInputStream in = new FileInputStream(f)) {
            return lerTexto(in);
        } catch (IOException e) {
            return "";
        }
    }

    public static void guardarSincronizacao(Context c, String texto) {
        Arquivos.gravar(c, ARQUIVO_SINCRONIZACAO, texto);
    }

    public static void guardar(Context c, List<Faixa> faixas) {
        Arquivos.gravar(c, ARQUIVO, Catalogo.serializar(faixas));
    }

    /** Grava (ou troca) um arquivo de texto na pasta raiz escolhida. false se o pendrive nao deixa gravar. */
    public static boolean gravarNaRaiz(ContentResolver cr, Uri arvore, String nome, String texto) {
        try {
            String raiz = DocumentsContract.getTreeDocumentId(arvore);
            Uri alvo = null;
            Uri filhos = DocumentsContract.buildChildDocumentsUriUsingTree(arvore, raiz);
            try (Cursor cur = cr.query(filhos, new String[]{Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
                while (cur != null && cur.moveToNext()) {
                    if (nome.equals(cur.getString(1))) {
                        alvo = DocumentsContract.buildDocumentUriUsingTree(arvore, cur.getString(0));
                        break;
                    }
                }
            }
            if (alvo == null) {
                alvo = DocumentsContract.createDocument(cr, DocumentsContract.buildDocumentUriUsingTree(arvore, raiz), "application/octet-stream", nome);
            }
            if (alvo == null) return false;
            try (java.io.OutputStream out = cr.openOutputStream(alvo, "wt")) {
                if (out == null) return false;
                out.write(texto.getBytes(StandardCharsets.UTF_8));
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean pastaIgnorada(String nome) {
        String n = nome.toLowerCase();
        return n.startsWith(".") || n.equals("lost.dir") || n.equals("android") || n.equals("system volume information") || n.startsWith("$recycle");
    }

    /** Entra em todas as pastas da arvore escolhida e devolve as musicas, por banda e musica. */
    public static Resultado varrer(ContentResolver cr, Uri arvore, Progresso progresso) {
        List<Faixa> achadas = new ArrayList<>();
        String sincronizacao = null;
        Set<String> vistas = new HashSet<>();
        ArrayDeque<String> pastas = new ArrayDeque<>();
        pastas.push(DocumentsContract.getTreeDocumentId(arvore));
        String[] colunas = {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_LAST_MODIFIED};
        while (!pastas.isEmpty()) {
            String pai = pastas.pop();
            if (!vistas.add(pai)) continue;
            Uri filhos = DocumentsContract.buildChildDocumentsUriUsingTree(arvore, pai);
            try (Cursor cur = cr.query(filhos, colunas, null, null, null)) {
                if (cur == null) continue;
                while (cur.moveToNext()) {
                    String id = cur.getString(0);
                    String nome = cur.getString(1);
                    String mime = cur.getString(2);
                    long gravada = cur.isNull(3) ? 0L : cur.getLong(3);
                    if (id == null || nome == null) continue;
                    if (Document.MIME_TYPE_DIR.equals(mime)) {
                        if (!pastaIgnorada(nome)) pastas.push(id);
                    } else if (nome.equals(Sincronizacao.ARQUIVO) && sincronizacao == null) {
                        try (InputStream in = cr.openInputStream(DocumentsContract.buildDocumentUriUsingTree(arvore, id))) {
                            if (in != null) sincronizacao = lerTexto(in);
                        } catch (Exception e) {
                            // arquivo ilegivel: so nao importa
                        }
                    } else if (!nome.startsWith("._") && Faixa.ehAudio(nome, mime)) {
                        achadas.add(new Faixa(DocumentsContract.buildDocumentUriUsingTree(arvore, id).toString(), nome, gravada));
                        if (progresso != null && achadas.size() % 25 == 0) progresso.achou(achadas.size());
                    }
                }
            } catch (Exception e) {
                // uma pasta sem acesso nao pode derrubar a busca das outras
            }
        }
        if (progresso != null) progresso.achou(achadas.size());
        return new Resultado(Catalogo.ordenar(achadas), sincronizacao);
    }
}
