package com.raccoon.music;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lê o tempo digitado no temporizador: "45m", "1h30m", "2:00", "3h", "1,5h" ou só "90" (minutos). Sem Android. */
public final class Tempo {
    private Tempo() {}

    private static final Pattern PARTE = Pattern.compile("(\\d+(?:[.,]\\d+)?)([hms])");

    /** Minutos inteiros (mínimo 1); 0 quando o texto é inválido. */
    public static int minutos(String texto) {
        String s = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (s.isEmpty()) return 0;
        if (s.matches("\\d{1,3}:\\d{2}(:\\d{2})?")) {
            String[] p = s.split(":");
            return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
        }
        if (s.matches("\\d{1,4}")) return Integer.parseInt(s);
        s = s.replace("horas", "h").replace("hora", "h").replace("min", "m");
        Matcher m = PARTE.matcher(s);
        double segundos = 0;
        int fim = 0;
        while (m.find()) {
            if (m.start() != fim) return 0;
            fim = m.end();
            double n = Double.parseDouble(m.group(1).replace(',', '.'));
            segundos += n * ("h".equals(m.group(2)) ? 3600 : "m".equals(m.group(2)) ? 60 : 1);
        }
        if (fim != s.length() || fim == 0) return 0;
        return (int) (segundos / 60);
    }

    /** "45 min", "1 h 30 min" ou "2 h". */
    public static String texto(int minutos) {
        int h = minutos / 60, m = minutos % 60;
        if (h == 0) return m + " min";
        return m == 0 ? h + " h" : h + " h " + m + " min";
    }
}
