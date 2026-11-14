package com.raccoon.music;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/** Barra de progresso desenhada à mão, com os efeitos e formatos escolhidos em Aparência (veja BarraEstilo). */
public final class BarraDeProgresso extends View {
    private final Paint pincel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF area = new RectF();
    private final Path recorte = new Path();
    private int barra;  // 0 preparando, 1 baixando, 2 concluído
    private BarraEstilo estilo = new BarraEstilo();
    private int principal = 0xFF676E7F;
    private int trilho = 0xFF22222A;
    private int valor;  // 0..100
    private boolean indeterminada;
    private boolean animando = true;

    public BarraDeProgresso(Context contexto) {
        super(contexto);
    }

    public void definir(int barra, BarraEstilo estilo, int principal, int trilho) {
        this.barra = barra;
        this.estilo = estilo;
        this.principal = principal;
        this.trilho = trilho;
        invalidate();
    }

    /** `valor` de 0 a 100; `indeterminada` faz o preenchimento correr sem porcentagem (etapa de preparo). */
    public void valor(int valor, boolean indeterminada) {
        this.valor = Math.max(0, Math.min(100, valor));
        this.indeterminada = indeterminada;
        invalidate();
    }

    public void animar(boolean ligado) {
        animando = ligado;
        invalidate();
    }

    private int corBase() {
        int c = estilo.cor[barra];
        return c == 0 ? principal : c;
    }

    private static int clarear(int cor, float t) {
        return Cores.misturar(cor, 0xFFFFFFFF, t);
    }

    private double fase() {
        return (SystemClock.uptimeMillis() % 100000L) / 1000.0 * BarraEstilo.fatorDaVelocidade(estilo.velocidade[barra]);
    }

    private float raio(float altura) {
        switch (estilo.formato) {
            case "arredondada": return altura * 0.3f;
            case "pilula": return altura / 2f;
            default: return 0f;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float r = raio(h);
        pincel.setShader(null);
        pincel.setStyle(Paint.Style.FILL);
        pincel.setColor(trilho);
        area.set(0, 0, w, h);
        c.drawRoundRect(area, r, r, pincel);

        String ef = estilo.efeito[barra];
        double f = fase();
        float inicio = 0;
        float fim = w * valor / 100f;
        if (indeterminada) {  // bloco que vai e volta
            float largura = w * 0.35f;
            double t = (Math.sin(f * 2.2) + 1) / 2;
            inicio = (float) (t * (w - largura));
            fim = inicio + largura;
        } else if ("varredura".equals(ef) || "corrida".equals(ef)) {
            float largura = w * 0.3f;
            double t = "varredura".equals(ef) ? (Math.sin(f * 2.2) + 1) / 2 : (f * 0.7) % 1.0;
            inicio = (float) (t * (w - largura));
            fim = inicio + largura;
        }
        if (fim - inicio < 1f) {
            agendar();
            return;
        }
        int base = corBase();
        c.save();
        recorte.reset();
        recorte.addRoundRect(new RectF(inicio, 0, fim, h), r, r, Path.Direction.CW);
        c.clipPath(recorte);
        pincel.setColor(base);
        switch (ef) {
            case "degrade":
                pincel.setShader(new LinearGradient(inicio, 0, fim, 0, Cores.misturar(base, 0xFF000000, 0.35f), clarear(base, 0.35f), Shader.TileMode.CLAMP));
                break;
            case "pulso": {
                float t = (float) ((Math.sin(f * 3) + 1) / 2);
                pincel.setColor(Cores.misturar(base, 0xFFFFFFFF, 0.05f + 0.3f * t));
                break;
            }
            case "arco-iris": {
                int[] cores = {0xFFE53935, 0xFFFF8A1F, 0xFFFACC15, 0xFF2ECC71, 0xFF22D3EE, 0xFF3B82F6, 0xFF8B5CF6, 0xFFE53935};
                float deslocamento = (float) ((f * 60) % w);
                pincel.setShader(new LinearGradient(-deslocamento, 0, w - deslocamento, 0, cores, null, Shader.TileMode.REPEAT));
                break;
            }
            default:
                break;
        }
        c.drawRect(inicio, 0, fim, h, pincel);
        pincel.setShader(null);
        if ("brilho".equals(ef)) {
            float largura = w * 0.25f;
            float x = (float) (((f * 0.5) % 1.0) * (w + largura)) - largura;
            pincel.setShader(new LinearGradient(x, 0, x + largura, 0, 0x00FFFFFF, 0x66FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(x, 0, x + largura, h, pincel);
            pincel.setShader(null);
        } else if ("listras".equals(ef)) {
            pincel.setColor(0x33FFFFFF);
            float passo = h * 1.2f;
            float desloc = (float) ((f * h * 0.8) % (passo * 2));
            Path listra = new Path();
            for (float x = inicio - h * 2 - passo * 2; x < fim + h; x += passo * 2) {
                listra.reset();
                listra.moveTo(x + desloc, h);
                listra.lineTo(x + desloc + passo, h);
                listra.lineTo(x + desloc + passo + h, 0);
                listra.lineTo(x + desloc + h, 0);
                listra.close();
                c.drawPath(listra, pincel);
            }
        } else if ("onda".equals(ef)) {
            pincel.setColor(0x33FFFFFF);
            Path onda = new Path();
            onda.moveTo(inicio, h);
            for (float x = inicio; x <= fim; x += 4) {
                onda.lineTo(x, h * (0.45f + 0.18f * (float) Math.sin(x / (h * 1.4f) + f * 3)));
            }
            onda.lineTo(fim, h);
            onda.close();
            c.drawPath(onda, pincel);
        }
        if ("segmentada".equals(estilo.formato)) {  // frestas na cor do trilho
            pincel.setColor(trilho);
            float bloco = Math.max(6f, h * 0.9f);
            for (float x = bloco; x < w; x += bloco + 3f) c.drawRect(x, 0, x + 3f, h, pincel);
        }
        c.restore();
        agendar();
    }

    private void agendar() {
        if (animando && isShown() && (indeterminada || !"nenhum".equals(estilo.efeito[barra]))) postInvalidateDelayed(33);
    }
}
