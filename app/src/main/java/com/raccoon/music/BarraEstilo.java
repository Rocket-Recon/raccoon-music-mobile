package com.raccoon.music;

/** Aparência das três barras de progresso do download (preparando, baixando, concluído), como na tela de barras do app do PC. Sem Android, para poder ser testada. */
public final class BarraEstilo {
    public static final String[] CHAVES = {"preparando", "baixando", "concluido"};
    public static final String[] NOMES = {"Preparando", "Baixando", "Concluído"};

    public static final String[][] EFEITOS_COMUNS = {
            {"brilho", "Brilho"}, {"listras", "Listras"}, {"pulso", "Pulso"}, {"onda", "Onda"}, {"degrade", "Degradê"},
    };
    private static final String[] SOLIDA = {"nenhum", "Sólida"};

    public static final String[][] VELOCIDADES = {{"lenta", "Lenta"}, {"normal", "Normal"}, {"rapida", "Rápida"}};
    public static final String[][] FORMATOS = {{"reta", "Quadrado"}, {"arredondada", "Arredondado"}, {"pilula", "Pílula"}, {"segmentada", "Segmentado"}};
    /** Nome, cor ARGB (0 = automática: a cor principal do app). */
    public static final Object[][] CORES = {
            {"Automática", 0}, {"Vermelho", 0xFFE53935}, {"Laranja", 0xFFFF8A1F}, {"Amarelo", 0xFFFACC15}, {"Verde", 0xFF2ECC71},
            {"Ciano", 0xFF22D3EE}, {"Azul", 0xFF3B82F6}, {"Violeta", 0xFF8B5CF6}, {"Rosa", 0xFFEC4899}, {"Prata", 0xFF8A8F9C}, {"Branco", 0xFFF1F5F9},
    };

    public final String[] efeito = new String[3];
    public final String[] velocidade = new String[3];
    public final int[] cor = new int[3];
    public String formato = "reta";

    public BarraEstilo() {
        for (int i = 0; i < 3; i++) {
            efeito[i] = efeitos(i)[0][0];
            velocidade[i] = "normal";
        }
    }

    /** Efeitos aceitos por cada barra; o primeiro é o padrão. */
    public static String[][] efeitos(int barra) {
        String[][] exclusivos;
        if (barra == 0) exclusivos = new String[][]{{"varredura", "Varredura"}, {"corrida", "Corrida"}};
        else if (barra == 1) exclusivos = new String[][]{};
        else exclusivos = new String[][]{{"arco-iris", "Arco-íris"}};
        String[][] r = new String[exclusivos.length + EFEITOS_COMUNS.length + 1][];
        System.arraycopy(exclusivos, 0, r, 0, exclusivos.length);
        System.arraycopy(EFEITOS_COMUNS, 0, r, exclusivos.length, EFEITOS_COMUNS.length);
        r[r.length - 1] = SOLIDA;
        return r;
    }

    public static boolean efeitoValido(int barra, String chave) {
        for (String[] e : efeitos(barra)) if (e[0].equals(chave)) return true;
        return false;
    }

    public static boolean velocidadeValida(String chave) {
        for (String[] v : VELOCIDADES) if (v[0].equals(chave)) return true;
        return false;
    }

    public static boolean formatoValido(String chave) {
        for (String[] f : FORMATOS) if (f[0].equals(chave)) return true;
        return false;
    }

    /** Multiplicador do ritmo do efeito. */
    public static double fatorDaVelocidade(String chave) {
        return "lenta".equals(chave) ? 0.5 : ("rapida".equals(chave) ? 1.8 : 1.0);
    }

    public void efeito(int barra, String chave) {
        if (efeitoValido(barra, chave)) efeito[barra] = chave;
    }

    public void velocidade(int barra, String chave) {
        if (velocidadeValida(chave)) velocidade[barra] = chave;
    }

    public void formato(String chave) {
        if (formatoValido(chave)) formato = chave;
    }

    /** Barra 0..2, ou -1 para as três de uma vez. */
    public void cor(int barra, int argb) {
        for (int i = 0; i < 3; i++) if (barra < 0 || barra == i) cor[i] = argb;
    }

    public String serializar() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 3; i++) b.append(efeito[i]).append(',').append(velocidade[i]).append(',').append(cor[i]).append(';');
        return b.append(formato).toString();
    }

    /** Lê o texto salvo; qualquer parte inválida volta ao padrão. */
    public static BarraEstilo ler(String texto) {
        BarraEstilo e = new BarraEstilo();
        if (texto == null || texto.isEmpty()) return e;
        String[] partes = texto.split(";", -1);
        for (int i = 0; i < 3 && i < partes.length; i++) {
            String[] c = partes[i].split(",", -1);
            if (c.length != 3) continue;
            e.efeito(i, c[0]);
            e.velocidade(i, c[1]);
            try {
                e.cor[i] = Integer.parseInt(c[2]);
            } catch (NumberFormatException erro) {
                e.cor[i] = 0;
            }
        }
        if (partes.length > 3) e.formato(partes[3]);
        return e;
    }

    public BarraEstilo copia() {
        return ler(serializar());
    }
}
