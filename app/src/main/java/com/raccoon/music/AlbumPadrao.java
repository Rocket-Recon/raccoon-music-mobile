package com.raccoon.music;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Nome padrão do álbum: remove marcas de edição (deluxe, expandida, remasterizada, ao vivo) para as edições do mesmo álbum ficarem juntas. Sem Android. */
public final class AlbumPadrao {
    private AlbumPadrao() {}

    private static final String PALAVRAS = "deluxe|expanded|edition|edicao|remaster(?:ed)?|anniversary|bonus|special|collector'?s?|re-?issue|limited|"
            + "explicit|clean|import|japan(?:ese)?|tour|platinum|live|ao vivo|super|digital|standard";
    private static final Pattern TEM_PALAVRA = Pattern.compile("(?<![\\p{L}\\d])(?:" + PALAVRAS + ")(?![\\p{L}\\d])", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARENTESES = Pattern.compile("\\s*[(\\[]([^()\\[\\]]*)[)\\]]");
    private static final Pattern SUFIXO = Pattern.compile("\\s+[-–/:]\\s+(.+)$");

    /** "One-X (Deluxe Edition)" e "One-X / Live at the Palace 2006" viram "One-X"; se sobrar nada, devolve o nome original. */
    public static String limpar(String album) {
        if (album == null) return "";
        String original = album.trim();
        String s = original;
        Matcher m = PARENTESES.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String dentro = Faixa.normalizar(m.group(1));
            m.appendReplacement(sb, TEM_PALAVRA.matcher(dentro).find() ? "" : Matcher.quoteReplacement(m.group()));
        }
        m.appendTail(sb);
        s = sb.toString().trim();
        boolean mudou = true;
        while (mudou) {  // "Álbum - Deluxe / Live": tira de trás para frente
            mudou = false;
            Matcher x = SUFIXO.matcher(s);
            if (x.find() && x.start() > 0 && TEM_PALAVRA.matcher(Faixa.normalizar(x.group(1))).find()) {
                s = s.substring(0, x.start()).trim();
                mudou = true;
            }
        }
        s = s.replaceAll("\\s+", " ").replaceAll("[\\s\\-–/:]+$", "").trim();
        return s.isEmpty() ? original : s;
    }
}
