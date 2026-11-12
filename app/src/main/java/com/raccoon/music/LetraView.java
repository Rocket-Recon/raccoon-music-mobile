package com.raccoon.music;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/** A letra rolando sozinha: a linha cantada fica grande e em destaque, as outras ficam apagadas e a rolagem acompanha a música. */
public final class LetraView extends View {
    /** Chamado ao tocar numa linha de uma letra com tempo. */
    public interface AoTocarLinha {
        void linha(int indice);
    }

    private static final long PAUSA_MANUAL_MS = 4000L;
    private static final float SUAVIDADE = 0.14f;
    private static final float AUMENTO_DA_LINHA_CANTADA = 1.18f;

    private final float densidade;
    private final float limiteDoToque;
    private final TextPaint tintaComum = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tintaDestaque = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tintaMensagem = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private String[] linhas = new String[0];
    private boolean sincronizada;
    private String mensagem = "";
    private int atual = -1;
    private float tamanho;
    private float y;
    private float alvo;
    private float fim;
    private long livreAte;
    private int larguraCalculada = -1;
    private float[] topos = new float[0];
    private float[] alturas = new float[0];
    private StaticLayout[] comuns = new StaticLayout[0];
    private StaticLayout[] destaques = new StaticLayout[0];
    private StaticLayout aviso;
    private String[] traduzidas;
    private StaticLayout[] traducoes = new StaticLayout[0];
    private final TextPaint tintaTraducao = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private int corTexto = 0xFFFFFFFF;
    private int corDestaque = 0xFFFFFFFF;
    private AoTocarLinha aoTocar;
    private float toqueInicialY;
    private float toqueAnteriorY;
    private boolean arrastou;
    private boolean animando;

    private final Runnable passo = new Runnable() {
        @Override
        public void run() {
            long agora = SystemClock.uptimeMillis();
            if (agora < livreAte) {
                postDelayed(this, Math.max(16L, livreAte - agora));
                return;
            }
            float diferenca = alvo - y;
            if (Math.abs(diferenca) < 0.5f) {
                y = alvo;
                animando = false;
                invalidate();
                return;
            }
            y += diferenca * SUAVIDADE;
            invalidate();
            postOnAnimation(this);
        }
    };

    public LetraView(Context contexto) {
        super(contexto);
        densidade = contexto.getResources().getDisplayMetrics().density;
        limiteDoToque = ViewConfiguration.get(contexto).getScaledTouchSlop();
        tintaComum.setTypeface(Typeface.DEFAULT_BOLD);
        tintaDestaque.setTypeface(Typeface.DEFAULT_BOLD);
        tintaMensagem.setTypeface(Typeface.DEFAULT);
        tintaTraducao.setTypeface(Typeface.DEFAULT);
        tintaMensagem.setColor(0xCCFFFFFF);
        tintaMensagem.setTextSize(16f * densidade);
        definirTamanho(22f);
    }

    /** Tamanho da linha comum em sp; a linha cantada é um pouco maior. */
    public void definirTamanho(float sp) {
        float px = sp * getResources().getDisplayMetrics().scaledDensity;
        tamanho = px;
        tintaComum.setTextSize(px);
        tintaDestaque.setTextSize(px * AUMENTO_DA_LINHA_CANTADA);
        tintaTraducao.setTextSize(px * 0.8f);
        larguraCalculada = -1;
        int guardada = atual;
        atual = -2;
        definirAtual(guardada);
        if (!sincronizada) atual = guardada;
        invalidate();
    }

    /** Cor das linhas comuns e da linha cantada. */
    public void definirCores(int texto, int destaque) {
        corTexto = texto;
        corDestaque = destaque;
        tintaMensagem.setColor(texto);
        tintaMensagem.setAlpha(210);
        invalidate();
    }

    /** Tradução mostrada abaixo de cada linha (mesma quantidade de linhas da letra); null volta à letra original. */
    public void definirTraducao(String[] t) {
        traduzidas = t;
        larguraCalculada = -1;
        aviso = null;
        invalidate();
    }

    public void aoTocarLinha(AoTocarLinha ouvinte) {
        aoTocar = ouvinte;
    }

    /** Troca a letra: `sincronizada` indica que cada linha tem o seu momento (a linha cantada acompanha a música). */
    public void definir(String[] novas, boolean temTempo, String texto) {
        linhas = novas == null ? new String[0] : novas;
        traduzidas = null;
        sincronizada = temTempo && linhas.length > 0;
        mensagem = texto == null ? "" : texto;
        atual = -1;
        y = 0f;
        alvo = 0f;
        larguraCalculada = -1;
        aviso = null;
        invalidate();
    }

    /** Só uma mensagem no centro ("Buscando a letra…", "Não achei a letra dessa música."). */
    public void mostrarMensagem(String texto) {
        definir(null, false, texto);
    }

    public boolean temLetra() {
        return linhas.length > 0;
    }

    public void definirAtual(int indice) {
        if (indice == atual || !sincronizada) return;
        atual = indice;
        calcular();
        if (indice >= 0 && indice < topos.length) {
            alvo = topos[indice] + alturas[indice] / 2f - getHeight() / 2f;
        }
        iniciarAnimacao();
        invalidate();
    }

    private void iniciarAnimacao() {
        if (animando) return;
        animando = true;
        postOnAnimation(passo);
    }

    private StaticLayout montar(String texto, TextPaint tinta, int largura) {
        return StaticLayout.Builder.obtain(texto, 0, texto.length(), tinta, largura)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.1f)
                .setIncludePad(false)
                .build();
    }

    private void calcular() {
        int largura = Math.max(60, getWidth() - Math.round(32 * densidade));
        if (largura == larguraCalculada && comuns.length == linhas.length) return;
        larguraCalculada = largura;
        int n = linhas.length;
        comuns = new StaticLayout[n];
        destaques = new StaticLayout[n];
        traducoes = new StaticLayout[n];
        topos = new float[n];
        alturas = new float[n];
        float topo = 40f * densidade;
        float folga = 10f * densidade;
        for (int i = 0; i < n; i++) {
            String texto = linhas[i].isEmpty() ? " " : linhas[i];
            comuns[i] = montar(texto, tintaComum, largura);
            destaques[i] = montar(texto, tintaDestaque, largura);
            float altura = destaques[i].getHeight() + 2 * folga;  // altura pela fonte maior: a linha cantada nunca é cortada
            if (traduzidas != null && i < traduzidas.length && !traduzidas[i].trim().isEmpty()) {
                traducoes[i] = montar(traduzidas[i], tintaTraducao, largura);
                altura += traducoes[i].getHeight() + 4 * densidade;
            }
            topos[i] = topo;
            alturas[i] = altura;
            topo += altura;
        }
        fim = topo + 60f * densidade;
        if (sincronizada && atual >= 0 && atual < n) alvo = topos[atual] + alturas[atual] / 2f - getHeight() / 2f;
    }

    private float limiteInferior() {
        return Math.max(0f, fim - getHeight());
    }

    @Override
    protected void onSizeChanged(int w, int h, int antigoW, int antigoH) {
        super.onSizeChanged(w, h, antigoW, antigoH);
        larguraCalculada = -1;
        aviso = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(passo);
        animando = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        int margem = Math.round(16 * densidade);
        if (linhas.length == 0) {
            if (mensagem.isEmpty() || getWidth() <= 0) return;
            if (aviso == null) aviso = montar(mensagem, tintaMensagem, Math.max(60, getWidth() - 2 * margem));
            c.save();
            c.translate(margem, (getHeight() - aviso.getHeight()) / 2f);
            aviso.draw(c);
            c.restore();
            return;
        }
        calcular();
        for (int i = 0; i < linhas.length; i++) {
            float topo = topos[i] - y;
            if (topo + alturas[i] < 0 || topo > getHeight()) continue;
            boolean cantada = sincronizada && i == atual;
            StaticLayout layout = cantada ? destaques[i] : comuns[i];
            int alfa;
            if (!sincronizada || cantada) alfa = 255;
            else alfa = Math.abs(i - atual) == 1 ? 150 : 100;
            layout.getPaint().setColor(cantada ? corDestaque : corTexto);
            layout.getPaint().setAlpha(alfa);
            StaticLayout trad = i < traducoes.length ? traducoes[i] : null;
            float extra = trad == null ? 0f : trad.getHeight() + 4 * densidade;
            c.save();
            c.translate(margem, topo + (alturas[i] - layout.getHeight() - extra) / 2f);
            layout.draw(c);
            c.restore();
            if (trad != null) {
                trad.getPaint().setColor(cantada ? corDestaque : corTexto);
                trad.getPaint().setAlpha(Math.round(alfa * 0.8f));
                c.save();
                c.translate(margem, topo + (alturas[i] - layout.getHeight() - extra) / 2f + layout.getHeight() + 4 * densidade);
                trad.draw(c);
                c.restore();
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (linhas.length == 0) return super.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                toqueInicialY = e.getY();
                toqueAnteriorY = e.getY();
                arrastou = false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!arrastou && Math.abs(e.getY() - toqueInicialY) > limiteDoToque) arrastou = true;
                if (arrastou) {
                    calcular();
                    y = Math.min(Math.max(0f, y - (e.getY() - toqueAnteriorY)), limiteInferior());
                    livreAte = SystemClock.uptimeMillis() + PAUSA_MANUAL_MS;
                    if (!sincronizada) alvo = y;  // letra sem tempo: fica onde o dedo deixou
                    iniciarAnimacao();
                    invalidate();
                }
                toqueAnteriorY = e.getY();
                return true;
            case MotionEvent.ACTION_UP:
                if (!arrastou) {
                    performClick();
                    if (sincronizada && aoTocar != null) {
                        calcular();
                        float ponto = toqueInicialY + y;
                        for (int i = 0; i < topos.length; i++) {
                            if (ponto >= topos[i] && ponto < topos[i] + alturas[i]) {
                                aoTocar.linha(i);
                                break;
                            }
                        }
                    }
                }
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
