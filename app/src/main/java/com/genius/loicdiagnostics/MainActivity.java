package com.genius.loicdiagnostics;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.net.*;
import java.io.IOException;

public class MainActivity extends Activity {
    private final int bg = Color.rgb(8, 14, 27), panel = Color.rgb(17, 28, 48), blue = Color.rgb(34, 151, 255), white = Color.rgb(232, 241, 252), muted = Color.rgb(143, 164, 190);
    private EditText hostInput, portInput;
    private TextView output, status;
    private Button dnsButton, pingButton, tcpButton;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(bg);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(22), dp(26), dp(22), dp(24)); scroll.addView(root);
        TextView brand = text("LOIC", 32, blue, true); root.addView(brand);
        TextView subtitle = text("DIAGNOSTICS  /  NETWORK TOOLS", 12, muted, true); root.addView(subtitle);
        addSpace(root, 24);
        TextView title = text("Network diagnostics", 23, white, true); root.addView(title);
        TextView desc = text("Comprobaciones individuales para redes y equipos autorizados.", 14, muted, false); root.addView(desc);
        addSpace(root, 22);
        root.addView(label("HOST / IP ADDRESS"));
        hostInput = field("example.com o 192.0.2.1"); root.addView(hostInput);
        addSpace(root, 14);
        root.addView(label("TCP PORT"));
        portInput = field("443"); root.addView(portInput);
        addSpace(root, 18);
        dnsButton = button("LOOKUP DNS"); root.addView(dnsButton);
        pingButton = button("CHECK LATENCY"); root.addView(pingButton);
        tcpButton = button("TEST TCP CONNECTION"); root.addView(tcpButton);
        addSpace(root, 18);
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16),dp(16),dp(16),dp(16)); card.setBackgroundColor(panel); root.addView(card);
        status = text("●  READY", 13, blue, true); card.addView(status);
        addSpace(card, 10);
        output = text("Los resultados aparecerán aquí. Cada prueba realiza una sola consulta o conexión.", 14, white, false); card.addView(output);
        addSpace(root, 18);
        TextView foot = text("LOIC DIAGNOSTICS  •  SAFE MODE", 11, muted, true); foot.setGravity(Gravity.CENTER); root.addView(foot);
        setContentView(scroll);
        dnsButton.setOnClickListener(v -> runTask("DNS lookup", () -> {
            InetAddress[] addresses = InetAddress.getAllByName(getHost());
            StringBuilder b = new StringBuilder("DNS resuelto correctamente:\n");
            for (InetAddress a : addresses) b.append("• ").append(a.getHostAddress()).append("\n");
            return b.toString();
        }));
        pingButton.setOnClickListener(v -> runTask("Latency check", () -> {
            String h = getHost(); long start = System.currentTimeMillis();
            InetAddress address = InetAddress.getByName(h);
            boolean reachable = address.isReachable(3000);
            long elapsed = System.currentTimeMillis() - start;
            return "Destino: " + address.getHostAddress() + "\nTiempo de comprobación: " + elapsed + " ms\nEstado: " + (reachable ? "reachable" : "sin respuesta ICMP (puede estar bloqueado)");
        }));
        tcpButton.setOnClickListener(v -> runTask("TCP connection", () -> {
            String h = getHost(); int p = getPort(); long start = System.currentTimeMillis();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(h, p), 4000);
                return "Conexión TCP establecida.\nDestino: " + h + ":" + p + "\nTiempo: " + (System.currentTimeMillis()-start) + " ms";
            }
        }));
    }
    private interface Job { String run() throws Exception; }
    private void runTask(String name, Job job) {
        String host = hostInput.getText().toString().trim();
        if (host.isEmpty()) { output.setText("Introduce un host o una dirección IP."); return; }
        setBusy(true, name + "…");
        new Thread(() -> {
            String result;
            try { result = job.run(); } catch (Exception e) { result = "No se pudo completar la prueba: " + e.getClass().getSimpleName() + (e.getMessage()==null ? "" : "\n"+e.getMessage()); }
            final String finalResult = result;
            runOnUiThread(() -> { output.setText(finalResult); setBusy(false, "READY"); });
        }).start();
    }
    private String getHost() { return hostInput.getText().toString().trim(); }
    private int getPort() {
        try { int p = Integer.parseInt(portInput.getText().toString().trim()); if (p < 1 || p > 65535) throw new Exception(); return p; }
        catch (Exception e) { throw new IllegalArgumentException("El puerto debe estar entre 1 y 65535."); }
    }
    private void setBusy(boolean busy, String s) { status.setText("●  " + s.toUpperCase()); dnsButton.setEnabled(!busy); pingButton.setEnabled(!busy); tcpButton.setEnabled(!busy); }
    private TextView text(String s, int size, int color, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t; }
    private TextView label(String s) { TextView t = text(s,11,muted,true); return t; }
    private EditText field(String hint) { EditText e = new EditText(this); e.setSingleLine(true); e.setTextSize(15); e.setTextColor(white); e.setHintTextColor(muted); e.setHint(hint); e.setPadding(dp(12),dp(8),dp(12),dp(8)); e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(blue)); return e; }
    private Button button(String s) { Button b = new Button(this); b.setText(s); b.setTextColor(white); b.setTextSize(13); b.setAllCaps(false); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(blue)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,dp(50)); lp.bottomMargin=dp(10); b.setLayoutParams(lp); return b; }
    private void addSpace(LinearLayout l,int h) { View v=new View(this); l.addView(v,new LinearLayout.LayoutParams(1,dp(h))); }
    private int dp(int n) { return (int)(n*getResources().getDisplayMetrics().density+0.5f); }
}
