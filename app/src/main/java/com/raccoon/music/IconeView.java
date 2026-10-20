package com.raccoon.music;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Os icones do player, desenhados no codigo (assim nao dependem de nenhuma imagem nem de fonte de emoji). */
public final class IconeView extends View {
    public static final int PLAY = 0, PAUSE = 1, PROXIMA = 2, ANTERIOR = 3, ALEATORIO = 4, REPETIR = 5, REPETIR_UM = 6, CORACAO = 7, CORACAO_CHEIO = 8, MENU = 9;

    private int tipo;
    private int cor = 0xFFC8C8D0;
    private final Paint tinta = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path caminho = new Path();
    private final RectF retangulo = new RectF();

    public IconeView(Context contexto, int tipo) {
        super(contexto);
        this.tipo = tipo;
    }

    public void definir(int novoTipo, int novaCor) {
        if (novoTipo != tipo || novaCor != cor) {
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
