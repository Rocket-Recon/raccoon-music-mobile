package com.raccoon.music;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.media3.common.Player;

import java.util.HashSet;
import java.util.Set;

/** O que o app lembra entre uma abertura e outra: a pasta, as curtidas, aleatorio/repetir e onde parou. */
public final class Preferencias {
    private final SharedPreferences sp;

    public Preferencias(Context c) {
        sp = c.getSharedPreferences("raccoon", Context.MODE_PRIVATE);
    }

    public String arvore() { return sp.getString("arvore", null); }
    public void arvore(String valor) { sp.edit().putString("arvore", valor).apply(); }

    public Set<String> curtidas() { return new HashSet<>(sp.getStringSet("curtidas", new HashSet<String>())); }

    /** Curte ou descurte; devolve true se agora esta curtida. */
    public boolean alternarCurtida(String chave) {
        Set<String> s = curtidas();
        boolean agora = !s.contains(chave);
        if (agora) s.add(chave); else s.remove(chave);
        sp.edit().putStringSet("curtidas", s).apply();
        return agora;
    }

    public void adicionarCurtidas(Set<String> novas) {
        Set<String> s = curtidas();
        if (s.addAll(novas)) sp.edit().putStringSet("curtidas", s).apply();
    }

    /** A musica foi renomeada: a curtida acompanha o novo nome. */
    public void renomearCurtida(String antiga, String nova) {
        Set<String> s = curtidas();
        if (s.remove(antiga)) {
            s.add(nova);
            sp.edit().putStringSet("curtidas", s).apply();
        }
    }

    /** Cores escolhidas (0 = padrao). */
    public int corFundo() { return sp.getInt("cor_fundo", 0); }
    public int corAcento() { return sp.getInt("cor_acento", 0); }
    public void cores(int fundo, int acento) { sp.edit().putInt("cor_fundo", fundo).putInt("cor_acento", acento).apply(); }

    public String playlists() { return sp.getString("playlists", ""); }
    public void playlists(String texto) { sp.edit().putString("playlists", texto).apply(); }

    public String historico() { return sp.getString("historico", ""); }
    public void historico(String texto) { sp.edit().putString("historico", texto).apply(); }

    public int ordem() { return sp.getInt("ordem", 0); }
    public void ordem(int v) { sp.edit().putInt("ordem", v).apply(); }

    public boolean embaralhar() { return sp.getBoolean("embaralhar", false); }
    public void embaralhar(boolean v) { sp.edit().putBoolean("embaralhar", v).apply(); }

    public int repetir() { return sp.getInt("repetir", Player.REPEAT_MODE_ALL); }
    public void repetir(int v) { sp.edit().putInt("repetir", v).apply(); }

    /** O PC que baixa as musicas: endereco ("192.168.0.5:8765") e codigo mostrado na janela Celular. */
    public String pcEndereco() { return sp.getString("pc_endereco", ""); }
    public String pcCodigo() { return sp.getString("pc_codigo", ""); }
    public void pc(String endereco, String codigo) { sp.edit().putString("pc_endereco", endereco).putString("pc_codigo", codigo).apply(); }

    /** Ajustes do download no proprio celular: formato, taxa de bits e se pergunta o nome antes de baixar. */
    public String baixarFormato() { return sp.getString("baixar_formato", "mp3"); }
    public void baixarFormato(String v) { sp.edit().putString("baixar_formato", v).apply(); }
    public int baixarTaxa() { return sp.getInt("baixar_taxa", 320); }
    public void baixarTaxa(int v) { sp.edit().putInt("baixar_taxa", v).apply(); }
    public boolean baixarPerguntarNome() { return sp.getBoolean("baixar_perguntar_nome", true); }
    public void baixarPerguntarNome(boolean v) { sp.edit().putBoolean("baixar_perguntar_nome", v).apply(); }

    /** Tamanho da letra no player, em sp. */
    public int tamanhoLetra() { return sp.getInt("tamanho_letra", 22); }
    public void tamanhoLetra(int sp_) { sp.edit().putInt("tamanho_letra", sp_).apply(); }

    public String ultimoUri() { return sp.getString("ultimo_uri", null); }
    public long ultimaPosicao() { return sp.getLong("ultima_pos", 0L); }
    public void ultimo(String uri, long posicaoMs) { sp.edit().putString("ultimo_uri", uri).putLong("ultima_pos", posicaoMs).apply(); }
}
