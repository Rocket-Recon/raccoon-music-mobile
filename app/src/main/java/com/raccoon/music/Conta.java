package com.raccoon.music;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Locale;

/** A conta do Raccoon Music (a mesma do app do PC, no Supabase): entrar por usuário ou e-mail, manter a sessão e consultar a licença. Chamar a rede fora da thread da tela. */
public final class Conta {
    // projeto Supabase do app: os dois valores são públicos por desenho (os dados são protegidos pelas regras do banco)
    private static final String URL_BASE = "https://xpzeknemvllsjqekdlyk.supabase.co";
    private static final String CHAVE = "sb_publishable_tSO3JKNhTa3PN-sDmdfjuw_3TjKpqJ5";
    private static final String PREFS = "conta";

    private static String acesso = "";
    private static long expiraEm;

    private Conta() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean logado(Context c) {
        return !prefs(c).getString("renovacao", "").isEmpty();
    }

    public static String usuario(Context c) {
        return prefs(c).getString("usuario", "");
    }

    private static final class Resposta {
        int status;
        String corpo = "";
    }

    private static Resposta http(String metodo, String endereco, String token, String json) {
        return http(metodo, endereco, token, json, null);
    }

    private static Resposta http(String metodo, String endereco, String token, String json, String prefer) {
        Resposta r = new Resposta();
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(endereco).openConnection();
            c.setRequestMethod(metodo);
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestProperty("apikey", CHAVE);
            c.setRequestProperty("Authorization", "Bearer " + (token == null || token.isEmpty() ? CHAVE : token));
            c.setRequestProperty("Content-Type", "application/json");
            if (prefer != null) c.setRequestProperty("Prefer", prefer);
            if (json != null) {
                c.setDoOutput(true);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                }
            }
            r.status = c.getResponseCode();
            InputStream in = r.status >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in != null) {
                try (InputStream i = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] bloco = new byte[8192];
                    int n;
                    while ((n = i.read(bloco)) > 0) out.write(bloco, 0, n);
                    r.corpo = new String(out.toByteArray(), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            r.status = 0;
        } finally {
            if (c != null) c.disconnect();
        }
        return r;
    }

    private static JSONObject objeto(String texto) {
        try {
            return new JSONObject(texto);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static String traduzir(JSONObject o, int status) {
        String codigo = o.optString("error_code", o.optString("code", o.optString("error", "")));
        if ("invalid_credentials".equals(codigo)) return "Usuário ou senha incorretos.";
        if ("email_not_confirmed".equals(codigo)) return "Confirme o e-mail da conta antes de entrar.";
        if ("over_request_rate_limit".equals(codigo) || status == 429) return "Muitas tentativas. Espere alguns minutos.";
        if (status == 404) return "Entrar pelo usuário não está disponível. Use o e-mail.";
        if (status >= 500) return "O servidor está com problema. Tente de novo em instantes.";
        return "Não foi possível entrar. Confira os dados.";
    }

    private static void guardar(Context c, JSONObject o) {
        JSONObject user = o.optJSONObject("user");
        JSONObject meta = user == null ? null : user.optJSONObject("user_metadata");
        String email = user == null ? "" : user.optString("email", "");
        String nome = meta == null ? "" : meta.optString("username", "");
        if (nome.isEmpty()) nome = prefs(c).getString("usuario", "");
        if (nome.isEmpty() && email.contains("@")) nome = email.substring(0, email.indexOf('@'));
        acesso = o.optString("access_token", "");
        expiraEm = System.currentTimeMillis() + o.optLong("expires_in", 3600L) * 1000L;
        prefs(c).edit().putString("usuario", nome).putString("email", email).putString("renovacao", o.optString("refresh_token", "")).apply();
    }

    /** Entra com usuário ou e-mail. Devolve null quando deu certo, ou a mensagem do erro. */
    public static String entrar(Context c, String login, String senha) {
        String l = login == null ? "" : login.trim();
        if (l.isEmpty() || senha == null || senha.isEmpty()) return "Digite o usuário ou e-mail e a senha.";
        try {
            Resposta r;
            if (l.contains("@")) {
                r = http("POST", URL_BASE + "/auth/v1/token?grant_type=password", null,
                        new JSONObject().put("email", l).put("password", senha).toString());
            } else {
                r = http("POST", URL_BASE + "/functions/v1/entrar-usuario", null,
                        new JSONObject().put("usuario", l.toLowerCase(Locale.ROOT)).put("senha", senha).toString());
            }
            if (r.status == 0) return "Sem conexão com a internet.";
            JSONObject o = objeto(r.corpo);
            if (r.status >= 400 || !o.has("access_token")) return traduzir(o, r.status);
            guardar(c, o);
            return null;
        } catch (Exception e) {
            return "Não foi possível entrar agora.";
        }
    }

    public static void sair(Context c) {
        acesso = "";
        expiraEm = 0L;
        prefs(c).edit().clear().apply();
    }

    /** Token de acesso válido (renova sozinho); null sem conta ou sem internet. Uma sessão recusada pelo servidor é encerrada. */
    public static synchronized String token(Context c) {
        if (!logado(c)) return null;
        if (!acesso.isEmpty() && System.currentTimeMillis() < expiraEm - 30000L) return acesso;
        try {
            Resposta r = http("POST", URL_BASE + "/auth/v1/token?grant_type=refresh_token", null,
                    new JSONObject().put("refresh_token", prefs(c).getString("renovacao", "")).toString());
            if (r.status == 0) return null;
            JSONObject o = objeto(r.corpo);
            if (r.status >= 400 || !o.has("access_token")) {
                sair(c);
                return null;
            }
            guardar(c, o);
            return acesso;
        } catch (Exception e) {
            return null;
        }
    }

    private static final String TABELA_SINCRONIZACAO = "/rest/v1/sincronizacao_music";

    /** O texto de curtidas e playlists guardado na conta: "" se ainda não há nada; null se não deu para falar com a conta. */
    public static String baixarSincronizacao(Context c) {
        String t = token(c);
        if (t == null) return null;
        Resposta r = http("GET", URL_BASE + TABELA_SINCRONIZACAO + "?select=dados", t, null);
        if (r.status < 200 || r.status >= 300) return null;
        try {
            JSONArray linhas = new JSONArray(r.corpo);
            return linhas.length() == 0 ? "" : linhas.getJSONObject(0).optString("dados", "");
        } catch (Exception e) {
            return null;
        }
    }

    /** Guarda na conta o texto de curtidas e playlists (soma de PC e celular). */
    public static boolean enviarSincronizacao(Context c, String texto) {
        String t = token(c);
        if (t == null) return false;
        try {
            Resposta r = http("POST", URL_BASE + TABELA_SINCRONIZACAO + "?on_conflict=user_id", t,
                    new JSONObject().put("dados", texto).put("atualizado_em", "now()").toString(),
                    "resolution=merge-duplicates,return=minimal");
            return r.status >= 200 && r.status < 300;
        } catch (Exception e) {
            return false;
        }
    }

    private static final long DIA_MS = 24L * 60L * 60L * 1000L;
    private static final int DIAS_SEM_INTERNET = 3;

    /** O app está liberado: conta aberta e licença confirmada dentro do prazo sem internet (ou até o fim da licença). */
    public static boolean liberado(Context c) {
        return logado(c) && prefs(c).getLong("valida_ate", 0L) > System.currentTimeMillis();
    }

    /** Última descrição do plano confirmada ("Premium vitalício", "Premium até 31/12/2026", "Modo grátis"). */
    public static String planoGuardado(Context c) {
        return prefs(c).getString("plano", "");
    }

    private static long fimDoDia(String data) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
            f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return f.parse(data.substring(0, 19)).getTime();
        } catch (Exception e) {
            try {
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
                f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                return f.parse(data.substring(0, 10)).getTime() + DIA_MS;
            } catch (Exception ruim) {
                return 0L;
            }
        }
    }

    private static Resposta licencas(String token) {
        String[] campos = {"tipo,expira_em,dias_offline", "tipo,expira_em"};
        String[] apps = {"&app=in.(global,music_mobile)", ""};  // a licença só do PC (music_desktop) não vale aqui
        Resposta r = new Resposta();
        for (String app : apps) {
            for (String campo : campos) {
                r = http("GET", URL_BASE + "/rest/v1/licencas?select=" + campo + "&status=eq.ativo" + app, token, null);
                if (r.status != 400) return r;  // 400 = coluna que ainda não existe no banco: tenta sem ela
            }
        }
        return r;
    }

    /**
     * Confere a licença da conta no servidor: "ok" (licença ativa), "sem_licenca" (conta sem licença ativa),
     * "sem_resposta" (sem internet ou servidor fora) ou "sem_conta". Guarda o plano e até quando o app fica liberado sem internet.
     */
    public static String verificar(Context c) {
        if (!logado(c)) return "sem_conta";
        String t = token(c);
        if (t == null) return logado(c) ? "sem_resposta" : "sem_conta";
        try {
            Resposta r = licencas(t);
            if (r.status == 0 || r.status >= 400) return "sem_resposta";
            JSONArray lista = new JSONArray(r.corpo);
            long agora = System.currentTimeMillis();
            String plano = "";
            long valida = 0L;
            long melhorFim = -1L;
            for (int i = 0; i < lista.length(); i++) {
                JSONObject l = lista.getJSONObject(i);
                String tipo = l.optString("tipo", "");
                if (!"premium_vitalicio".equals(tipo) && !"premium_periodico".equals(tipo)) continue;
                String expira = l.isNull("expira_em") ? "" : l.optString("expira_em", "");
                long fim = expira.length() >= 10 ? fimDoDia(expira) : Long.MAX_VALUE;
                if (fim <= agora) continue;  // vencida
                int dias = l.optInt("dias_offline", 0);
                long sem = (dias >= 1 && dias <= 30 ? dias : DIAS_SEM_INTERNET) * DIA_MS;
                if (fim > melhorFim) {
                    melhorFim = fim;
                    valida = Math.min(agora + sem, fim);
                    if (expira.length() < 10) plano = "premium_vitalicio".equals(tipo) ? "Premium vitalício" : "Premium";
                    else plano = "Premium até " + expira.substring(8, 10) + "/" + expira.substring(5, 7) + "/" + expira.substring(0, 4);
                }
            }
            if (valida > 0L) {
                prefs(c).edit().putString("plano", plano).putLong("valida_ate", valida).apply();
                return "ok";
            }
            prefs(c).edit().putString("plano", "Modo grátis").putLong("valida_ate", 0L).apply();
            return "sem_licenca";
        } catch (Exception e) {
            return "sem_resposta";
        }
    }
}
