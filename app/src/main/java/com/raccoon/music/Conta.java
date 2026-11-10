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
import java.util.Date;
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

    /** Situação da licença da conta ("Premium", "Premium até 31/12/2026" ou "Plano gratuito"); null sem conta ou sem internet. */
    public static String plano(Context c) {
        String t = token(c);
        if (t == null) return null;
        try {
            String base = URL_BASE + "/rest/v1/licencas?select=tipo,expira_em&status=eq.ativo";
            Resposta r = http("GET", base + "&app=in.(global,music_desktop,music_mobile)", t, null);
            if (r.status == 400) r = http("GET", base, t, null);
            if (r.status == 0 || r.status >= 400) return null;
            JSONArray lista = new JSONArray(r.corpo);
            String hoje = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
            String melhor = "";
            for (int i = 0; i < lista.length(); i++) {
                JSONObject l = lista.getJSONObject(i);
                String tipo = l.optString("tipo", "");
                String expira = l.isNull("expira_em") ? "" : l.optString("expira_em", "");
                if ("premium_vitalicio".equals(tipo) && expira.isEmpty()) return "Premium";
                if (("premium_periodico".equals(tipo) || "premium_vitalicio".equals(tipo)) && expira.length() >= 10 && expira.substring(0, 10).compareTo(hoje) >= 0) {
                    String d = expira.substring(8, 10) + "/" + expira.substring(5, 7) + "/" + expira.substring(0, 4);
                    if (melhor.isEmpty()) melhor = "Premium até " + d;
                }
            }
            return melhor.isEmpty() ? "Plano gratuito" : melhor;
        } catch (Exception e) {
            return null;
        }
    }
}
