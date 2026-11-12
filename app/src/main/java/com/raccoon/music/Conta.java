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

    // ------------------------------------------------------------------ criar conta, código do e-mail e senha esquecida
    /** Devolvido por {@link #cadastrar} e {@link #recuperarSenha} quando falta digitar o código que chegou por e-mail. */
    public static final String PEDE_CODIGO = "@codigo";

    public static String validarEmail(String email) {
        String e = email == null ? "" : email.trim();
        return e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$") ? "" : "Digite um e-mail válido.";
    }

    public static String validarUsuario(String usuario) {
        String u = usuario == null ? "" : usuario.trim().toLowerCase(Locale.ROOT);
        return u.matches("^[a-z0-9_.]{3,20}$") ? "" : "Usuário: 3 a 20 caracteres, só letras, números, ponto e sublinhado.";
    }

    public static String validarSenha(String senha) {
        String s = senha == null ? "" : senha;
        if (s.length() < 8) return "A senha precisa ter pelo menos 8 caracteres.";
        if (s.matches("^[0-9]+$") || s.matches("^\\p{L}+$")) return "Misture letras e números na senha.";
        return "";
    }

    private static String erroDeCadastro(JSONObject o, int status) {
        String codigo = o.optString("error_code", o.optString("code", o.optString("error", "")));
        if ("user_already_exists".equals(codigo) || "email_exists".equals(codigo)) return "Já existe uma conta com este e-mail.";
        if ("weak_password".equals(codigo)) return "Senha fraca. Use pelo menos 8 caracteres, com letras e números.";
        if ("over_email_send_rate_limit".equals(codigo)) return "Muitos e-mails enviados. Espere alguns minutos.";
        if ("otp_expired".equals(codigo)) return "Código incorreto ou vencido.";
        if ("signup_disabled".equals(codigo)) return "Novos cadastros estão desligados no momento.";
        if (status == 429) return "Muitas tentativas. Espere alguns minutos.";
        if (status >= 500) return "O servidor está com problema. Tente de novo em instantes.";
        return "Não foi possível concluir. Confira os dados e tente de novo.";
    }

    /** Cria a conta. null = criada e já aberta; {@link #PEDE_CODIGO} = falta o código do e-mail; outro texto = erro. */
    public static String cadastrar(Context c, String email, String usuario, String senha) {
        String erro = validarEmail(email);
        if (erro.isEmpty()) erro = validarUsuario(usuario);
        if (erro.isEmpty()) erro = validarSenha(senha);
        if (!erro.isEmpty()) return erro;
        try {
            Resposta r = http("POST", URL_BASE + "/auth/v1/signup", null, new JSONObject()
                    .put("email", email.trim()).put("password", senha)
                    .put("data", new JSONObject().put("username", usuario.trim().toLowerCase(Locale.ROOT))).toString());
            if (r.status == 0) return "Sem conexão com a internet.";
            JSONObject o = objeto(r.corpo);
            if (r.status >= 400) return erroDeCadastro(o, r.status);
            if (o.has("access_token")) {
                guardar(c, o);
                return null;
            }
            return PEDE_CODIGO;
        } catch (Exception e) {
            return "Não foi possível criar a conta agora.";
        }
    }

    /** Confere o código do e-mail (tipo "signup" ou "recovery") e abre a conta. null = deu certo. */
    public static String confirmarCodigo(Context c, String email, String codigo, String tipo) {
        String cod = codigo == null ? "" : codigo.trim().replace(" ", "");
        if (cod.isEmpty()) return "Digite o código que chegou no seu e-mail.";
        try {
            Resposta r = http("POST", URL_BASE + "/auth/v1/verify", null, new JSONObject()
                    .put("type", tipo).put("email", email == null ? "" : email.trim()).put("token", cod).toString());
            if (r.status == 0) return "Sem conexão com a internet.";
            JSONObject o = objeto(r.corpo);
            if (r.status >= 400 || !o.has("access_token")) return "Código incorreto ou vencido.";
            guardar(c, o);
            return null;
        } catch (Exception e) {
            return "Não foi possível confirmar agora.";
        }
    }

    /** Pede de novo o código de ativação da conta. null = enviado. */
    public static String reenviarCodigo(String email) {
        String erro = validarEmail(email);
        if (!erro.isEmpty()) return erro;
        try {
            Resposta r = http("POST", URL_BASE + "/auth/v1/resend", null,
                    new JSONObject().put("type", "signup").put("email", email.trim()).toString());
            if (r.status == 0) return "Sem conexão com a internet.";
            return r.status >= 400 ? erroDeCadastro(objeto(r.corpo), r.status) : null;
        } catch (Exception e) {
            return "Não foi possível reenviar agora.";
        }
    }

    /** "Esqueci a senha": manda o código para o e-mail (sem revelar se a conta existe). null = enviado. */
    public static String recuperarSenha(String email) {
        String erro = validarEmail(email);
        if (!erro.isEmpty()) return erro;
        try {
            Resposta r = http("POST", URL_BASE + "/auth/v1/recover", null, new JSONObject().put("email", email.trim()).toString());
            if (r.status == 0) return "Sem conexão com a internet.";
            return r.status >= 400 ? erroDeCadastro(objeto(r.corpo), r.status) : null;
        } catch (Exception e) {
            return "Não foi possível enviar agora.";
        }
    }

    /** Confere o código do e-mail e grava a senha nova; a conta já fica aberta. null = deu certo. */
    public static String redefinirSenha(Context c, String email, String codigo, String novaSenha) {
        String erro = validarSenha(novaSenha);
        if (!erro.isEmpty()) return erro;
        erro = confirmarCodigo(c, email, codigo, "recovery");
        if (erro != null) return erro;
        try {
            Resposta r = http("PUT", URL_BASE + "/auth/v1/user", acesso, new JSONObject().put("password", novaSenha).toString());
            if (r.status == 0) return "Sem conexão com a internet.";
            if (r.status >= 400) return erroDeCadastro(objeto(r.corpo), r.status);
            return null;
        } catch (Exception e) {
            return "Não foi possível trocar a senha agora.";
        }
    }

    // ------------------------------------------------------------------ licença: ativar depois
    /** Liga uma chave de licença à conta aberta. null = ativada. */
    public static String ativarChave(Context c, String chave) {
        String k = chave == null ? "" : chave.trim();
        if (k.isEmpty()) return "Digite a chave da licença.";
        String t = token(c);
        if (t == null) return "Entre na conta para ativar a licença.";
        try {
            String id = android.provider.Settings.Secure.getString(c.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
            Resposta r = http("POST", URL_BASE + "/rest/v1/rpc/resgatar_licenca", t,
                    new JSONObject().put("p_chave", k).put("p_hwid", "android-" + (id == null ? "" : id)).toString());
            if (r.status == 0) return "Sem conexão com a internet.";
            if (r.status >= 400) return "Não foi possível ativar agora. Tente de novo.";
            JSONObject o = objeto(r.corpo);
            if (o.optBoolean("ok", false)) return null;
            return o.optString("motivo", "Chave inválida.");
        } catch (Exception e) {
            return "Não foi possível ativar agora.";
        }
    }

    /** O app foi aberto sem licença ("colocar depois"): segue no modo grátis até a licença ser ativada. */
    public static boolean semLicenca(Context c) {
        return prefs(c).getBoolean("sem_licenca", false);
    }

    public static void usarSemLicenca(Context c, boolean usar) {
        prefs(c).edit().putBoolean("sem_licenca", usar).apply();
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
        return logado(c) && (semLicenca(c) || prefs(c).getLong("valida_ate", 0L) > System.currentTimeMillis());
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
                prefs(c).edit().putString("plano", plano).putLong("valida_ate", valida).putBoolean("sem_licenca", false).apply();
                return "ok";
            }
            prefs(c).edit().putString("plano", "Modo grátis").putLong("valida_ate", 0L).apply();
            return "sem_licenca";
        } catch (Exception e) {
            return "sem_resposta";
        }
    }
}
