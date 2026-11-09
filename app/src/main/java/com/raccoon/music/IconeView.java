package com.raccoon.music;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Os icones do player, desenhados no codigo (assim nao dependem de nenhuma imagem nem de fonte de emoji). */
public final class IconeView extends View {
    public static final int PLAY = 0, PAUSE = 1, PROXIMA = 2, ANTERIOR = 3, ALEATORIO = 4, REPETIR = 5, REPETIR_UM = 6, CORACAO = 7, CORACAO_CHEIO = 8, MENU = 9, SETA = 10, BAIXO = 11, FECHAR = 12, MAIS = 13, PONTOS = 14, FILA = 15, LETRA = 16, PLAYLIST_MAIS = 17, NOTA = 18, RELOGIO = 19,
            ARTISTA = 20, DISCO = 21, ETIQUETA = 22, PLAYLIST = 23, ESTRELA = 24, ESTATISTICA = 25, EXPORTAR = 26, COMPUTADOR = 27, DOWNLOAD = 28, AJUSTES = 29, PASTA = 30, ATUALIZAR = 31, LISTA = 32;

    private int tipo;
    private int cor = 0xFFC8C8D0;
    private final Paint tinta = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path caminho = new Path();
    private final RectF retangulo = new RectF();

    public IconeView(Context contexto, int tipo) {
        super(contexto);
        this.tipo = tipo;
        setContentDescription(descricao(tipo));
    }

    /** O que o leitor de tela fala para cada icone. */
    public static String descricao(int tipo) {
        switch (tipo) {
            case PLAY: return "Tocar";
            case PAUSE: return "Pausar";
            case PROXIMA: return "Próxima música";
            case ANTERIOR: return "Música anterior";
            case ALEATORIO: return "Aleatório";
            case REPETIR: return "Repetir";
            case REPETIR_UM: return "Repetir esta música";
            case CORACAO: return "Curtir";
            case CORACAO_CHEIO: return "Descurtir";
            case MENU: return "Menu";
            case SETA: return "Voltar";
            case BAIXO: return "Fechar o player";
            case FECHAR: return "Fechar";
            case MAIS: return "Nova playlist";
            case PONTOS: return "Mais opções";
            case FILA: return "Fila";
            case LETRA: return "Letra";
            case PLAYLIST_MAIS: return "Adicionar à playlist";
            case RELOGIO: return "Temporizador";
            case ARTISTA: return "Artistas";
            case DISCO: return "Álbuns";
            case ETIQUETA: return "Gêneros";
            case PLAYLIST: return "Playlists";
            case ESTRELA: return "Mais tocadas";
            case ESTATISTICA: return "Estatísticas";
            case EXPORTAR: return "Exportar";
            case COMPUTADOR: return "Computador";
            case DOWNLOAD: return "Baixar";
            case AJUSTES: return "Ajustes";
            case PASTA: return "Pasta";
            case ATUALIZAR: return "Atualizar";
            case LISTA: return "Lista";
            default: return null;
        }
    }

    public void definir(int novoTipo, int novaCor) {
        if (novoTipo != tipo || novaCor != cor) {
            if (novoTipo != tipo) setContentDescription(descricao(novoTipo));
            tipo = novoTipo;
            cor = novaCor;
            invalidate();
        }
    }

    private void cheio() { tinta.setStyle(Paint.Style.FILL); }

    private void traco() {
        tinta.setStyle(Paint.Style.STROKE);
        tinta.setStrokeWidth(2f);
        tinta.setStrokeCap(Paint.Cap.ROUND);
        tinta.setStrokeJoin(Paint.Join.ROUND);
    }

    private void triangulo(Canvas c, float x1, float y1, float x2, float y2, float x3, float y3) {
        caminho.reset();
        caminho.moveTo(x1, y1);
        caminho.lineTo(x2, y2);
        caminho.lineTo(x3, y3);
        caminho.close();
        cheio();
        c.drawPath(caminho, tinta);
    }

    private void linha(Canvas c, float x1, float y1, float x2, float y2) {
        traco();
        c.drawLine(x1, y1, x2, y2, tinta);
    }

    private void barra(Canvas c, float e, float t, float d, float b) {
        retangulo.set(e, t, d, b);
        cheio();
        c.drawRoundRect(retangulo, 1f, 1f, tinta);
    }

    @Override
    protected void onDraw(Canvas c) {
        int largura = getWidth() - getPaddingLeft() - getPaddingRight();
        int altura = getHeight() - getPaddingTop() - getPaddingBottom();
        float escala = Math.min(largura, altura) / 24f;
        if (escala <= 0) return;
        c.save();
        c.translate(getPaddingLeft() + (largura - 24 * escala) / 2f, getPaddingTop() + (altura - 24 * escala) / 2f);
        c.scale(escala, escala);
        tinta.setColor(cor);
        switch (tipo) {
            case PLAY:
                triangulo(c, 8, 5, 8, 19, 19, 12);
                break;
            case PAUSE:
                barra(c, 6.5f, 5, 10.5f, 19);
                barra(c, 13.5f, 5, 17.5f, 19);
                break;
            case PROXIMA:
                triangulo(c, 6, 6, 6, 18, 15, 12);
                barra(c, 16, 6, 18.5f, 18);
                break;
            case ANTERIOR:
                triangulo(c, 18, 6, 18, 18, 9, 12);
                barra(c, 5.5f, 6, 8, 18);
                break;
            case ALEATORIO:
                traco();
                caminho.reset();
                caminho.moveTo(3, 17);
                caminho.lineTo(6, 17);
                caminho.cubicTo(10, 17, 11, 7, 15, 7);
                caminho.lineTo(18, 7);
                c.drawPath(caminho, tinta);
                caminho.reset();
                caminho.moveTo(3, 7);
                caminho.lineTo(6, 7);
                caminho.cubicTo(10, 7, 11, 17, 15, 17);
                caminho.lineTo(18, 17);
                c.drawPath(caminho, tinta);
                triangulo(c, 17, 4, 21.5f, 7, 17, 10);
                triangulo(c, 17, 14, 21.5f, 17, 17, 20);
                break;
            case REPETIR:
            case REPETIR_UM:
                traco();
                caminho.reset();
                caminho.moveTo(5, 12);
                caminho.lineTo(5, 10);
                caminho.quadTo(5, 7, 8, 7);
                caminho.lineTo(16, 7);
                c.drawPath(caminho, tinta);
                caminho.reset();
                caminho.moveTo(19, 12);
                caminho.lineTo(19, 14);
                caminho.quadTo(19, 17, 16, 17);
                caminho.lineTo(8, 17);
                c.drawPath(caminho, tinta);
                triangulo(c, 15, 4, 19.5f, 7, 15, 10);
                triangulo(c, 9, 14, 4.5f, 17, 9, 20);
                if (tipo == REPETIR_UM) {
                    tinta.setStyle(Paint.Style.FILL);
                    tinta.setTextAlign(Paint.Align.CENTER);
                    tinta.setTextSize(9f);
                    tinta.setFakeBoldText(true);
                    c.drawText("1", 12, 15.2f, tinta);
                    tinta.setFakeBoldText(false);
                }
                break;
            case MENU:
                barra(c, 3.5f, 5.5f, 20.5f, 7.5f);
                barra(c, 3.5f, 11f, 20.5f, 13f);
                barra(c, 3.5f, 16.5f, 20.5f, 18.5f);
                break;
            case SETA:
                traco();
                caminho.reset();
                caminho.moveTo(15, 5);
                caminho.lineTo(8, 12);
                caminho.lineTo(15, 19);
                c.drawPath(caminho, tinta);
                break;
            case BAIXO:
                traco();
                caminho.reset();
                caminho.moveTo(5, 9);
                caminho.lineTo(12, 16);
                caminho.lineTo(19, 9);
                c.drawPath(caminho, tinta);
                break;
            case FECHAR:
                linha(c, 6, 6, 18, 18);
                linha(c, 18, 6, 6, 18);
                break;
            case MAIS:
                linha(c, 12, 5, 12, 19);
                linha(c, 5, 12, 19, 12);
                break;
            case PONTOS:
                cheio();
                c.drawCircle(12, 5.5f, 1.9f, tinta);
                c.drawCircle(12, 12, 1.9f, tinta);
                c.drawCircle(12, 18.5f, 1.9f, tinta);
                break;
            case FILA:
                linha(c, 4, 6, 15, 6);
                linha(c, 4, 11, 15, 11);
                linha(c, 4, 16, 11, 16);
                triangulo(c, 16, 13, 16, 21, 22, 17);
                break;
            case LETRA:
                linha(c, 5, 7, 19, 7);
                linha(c, 8, 12, 16, 12);
                linha(c, 5, 17, 19, 17);
                break;
            case PLAYLIST_MAIS:
                linha(c, 4, 6, 15, 6);
                linha(c, 4, 11, 15, 11);
                linha(c, 4, 16, 10, 16);
                linha(c, 18, 13, 18, 21);
                linha(c, 14, 17, 22, 17);
                break;
            case NOTA:
                cheio();
                retangulo.set(5.5f, 14.5f, 12.5f, 19.5f);
                c.drawOval(retangulo, tinta);
                barra(c, 11, 4.5f, 13.2f, 17);
                barra(c, 13, 4.5f, 18.5f, 7.5f);
                break;
            case RELOGIO:
                traco();
                c.drawCircle(12, 12, 8, tinta);
                caminho.reset();
                caminho.moveTo(12, 7);
                caminho.lineTo(12, 12);
                caminho.lineTo(15.5f, 14);
                c.drawPath(caminho, tinta);
                break;
            case ARTISTA:
                traco();
                c.drawCircle(12, 8, 3.6f, tinta);
                caminho.reset();
                caminho.moveTo(4.5f, 20);
                caminho.cubicTo(4.5f, 14.5f, 8, 13.5f, 12, 13.5f);
                caminho.cubicTo(16, 13.5f, 19.5f, 14.5f, 19.5f, 20);
                c.drawPath(caminho, tinta);
                break;
            case DISCO:
                traco();
                c.drawCircle(12, 12, 8.5f, tinta);
                c.drawCircle(12, 12, 2.6f, tinta);
                break;
            case ETIQUETA:
                traco();
                caminho.reset();
                caminho.moveTo(4, 12);
                caminho.lineTo(4, 5);
                caminho.lineTo(11, 5);
                caminho.lineTo(20, 14);
                caminho.lineTo(13, 21);
                caminho.close();
                c.drawPath(caminho, tinta);
                cheio();
                c.drawCircle(8, 9, 1.4f, tinta);
                break;
            case PLAYLIST:
                linha(c, 4, 6, 15, 6);
                linha(c, 4, 11, 15, 11);
                linha(c, 4, 16, 10, 16);
                cheio();
                retangulo.set(14, 14.5f, 19, 19);
                c.drawOval(retangulo, tinta);
                barra(c, 18, 7, 20, 17);
                break;
            case ESTRELA:
                traco();
                caminho.reset();
                caminho.moveTo(12, 3.8f);
                caminho.lineTo(14.5f, 9.2f);
                caminho.lineTo(20.4f, 9.9f);
                caminho.lineTo(16, 13.9f);
                caminho.lineTo(17.3f, 19.7f);
                caminho.lineTo(12, 16.7f);
                caminho.lineTo(6.7f, 19.7f);
                caminho.lineTo(8, 13.9f);
                caminho.lineTo(3.6f, 9.9f);
                caminho.lineTo(9.5f, 9.2f);
                caminho.close();
                c.drawPath(caminho, tinta);
                break;
            case ESTATISTICA:
                barra(c, 4.5f, 12, 8.5f, 20);
                barra(c, 10, 6, 14, 20);
                barra(c, 15.5f, 9.5f, 19.5f, 20);
                break;
            case EXPORTAR:
                linha(c, 12, 16, 12, 5);
                traco();
                caminho.reset();
                caminho.moveTo(7.5f, 9.5f);
                caminho.lineTo(12, 5);
                caminho.lineTo(16.5f, 9.5f);
                c.drawPath(caminho, tinta);
                caminho.reset();
                caminho.moveTo(5, 14);
                caminho.lineTo(5, 19);
                caminho.lineTo(19, 19);
                caminho.lineTo(19, 14);
                c.drawPath(caminho, tinta);
                break;
            case COMPUTADOR:
                traco();
                retangulo.set(3.5f, 5, 20.5f, 16);
                c.drawRoundRect(retangulo, 1.5f, 1.5f, tinta);
                linha(c, 9, 20, 15, 20);
                linha(c, 12, 16, 12, 20);
                break;
            case DOWNLOAD:
                linha(c, 12, 4.5f, 12, 15);
                traco();
                caminho.reset();
                caminho.moveTo(7.5f, 10.5f);
                caminho.lineTo(12, 15);
                caminho.lineTo(16.5f, 10.5f);
                c.drawPath(caminho, tinta);
                caminho.reset();
                caminho.moveTo(5, 15);
                caminho.lineTo(5, 19.5f);
                caminho.lineTo(19, 19.5f);
                caminho.lineTo(19, 15);
                c.drawPath(caminho, tinta);
                break;
            case AJUSTES:
                linha(c, 4, 7, 20, 7);
                linha(c, 4, 12, 20, 12);
                linha(c, 4, 17, 20, 17);
                cheio();
                c.drawCircle(9, 7, 2.3f, tinta);
                c.drawCircle(15, 12, 2.3f, tinta);
                c.drawCircle(8, 17, 2.3f, tinta);
                break;
            case PASTA:
                traco();
                caminho.reset();
                caminho.moveTo(3.5f, 7);
                caminho.lineTo(3.5f, 18.5f);
                caminho.lineTo(20.5f, 18.5f);
                caminho.lineTo(20.5f, 8.5f);
                caminho.lineTo(11.5f, 8.5f);
                caminho.lineTo(9.5f, 5.5f);
                caminho.lineTo(3.5f, 5.5f);
                caminho.close();
                c.drawPath(caminho, tinta);
                break;
            case ATUALIZAR:
                traco();
                retangulo.set(5, 5, 19, 19);
                c.drawArc(retangulo, -40, 270, false, tinta);
                triangulo(c, 15.5f, 2.5f, 20.5f, 6.5f, 14.5f, 8.2f);
                break;
            case LISTA:
                cheio();
                c.drawCircle(5.5f, 7, 1.4f, tinta);
                c.drawCircle(5.5f, 12, 1.4f, tinta);
                c.drawCircle(5.5f, 17, 1.4f, tinta);
                linha(c, 9.5f, 7, 20, 7);
                linha(c, 9.5f, 12, 20, 12);
                linha(c, 9.5f, 17, 20, 17);
                break;
            default:  // CORACAO e CORACAO_CHEIO
                caminho.reset();
                caminho.moveTo(12, 20.5f);
                caminho.cubicTo(5, 15, 3, 11.5f, 3, 8.5f);
                caminho.cubicTo(3, 6, 5, 4.5f, 7.2f, 4.5f);
                caminho.cubicTo(9.2f, 4.5f, 11, 5.6f, 12, 7.3f);
                caminho.cubicTo(13, 5.6f, 14.8f, 4.5f, 16.8f, 4.5f);
                caminho.cubicTo(19, 4.5f, 21, 6, 21, 8.5f);
                caminho.cubicTo(21, 11.5f, 19, 15, 12, 20.5f);
                caminho.close();
                if (tipo == CORACAO_CHEIO) cheio(); else traco();
                c.drawPath(caminho, tinta);
                break;
        }
        c.restore();
    }
}
