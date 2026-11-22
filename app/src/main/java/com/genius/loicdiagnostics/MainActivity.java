package com.genius.loicdiagnostics;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private final int bg=Color.rgb(7,18,29), panel=Color.rgb(19,43,62), blue=Color.rgb(35,76,105),
        white=Color.WHITE, muted=Color.rgb(190,202,213), accent=Color.rgb(255,70,55), fieldBg=Color.rgb(238,240,241);
    private EditText urlField, ip1,ip2,ip3,ip4,timeoutField,portField,messageField,threadsField;
    private Spinner methodSpinner;
    private SeekBar speedBar;
    private TextView selected,status,result,elapsed,rate;
    private Button runButton,stopButton;
    private volatile Thread worker;
    private final AtomicBoolean cancelled=new AtomicBoolean(false);
    private long startedAt;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.BLACK); getWindow().setNavigationBarColor(Color.BLACK);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(bg);
        LinearLayout root=new LinearLayout(this); root.setOrientation(1); root.setPadding(dp(16),dp(8),dp(16),dp(20)); scroll.addView(root);
        TextView logo=txt("☄  LOIC",23,Color.rgb(115,190,40),true); root.addView(logo);
        root.addView(section("1. Select your target"));
        root.addView(label("URL"));
        LinearLayout urlRow=row(); urlField=edit("http://www.example.com"); urlRow.addView(urlField,new LinearLayout.LayoutParams(0,dp(44),1));
        Button lockUrl=smallButton("Lock On"); urlRow.addView(lockUrl,new LinearLayout.LayoutParams(dp(78),dp(44))); root.addView(urlRow);
        addGap(root,5);
        root.addView(label("IP"));
        LinearLayout ipRow=row();
        ip1=ipBox("172"); ip2=ipBox("16"); ip3=ipBox("1"); ip4=ipBox("50");
        for(EditText e:new EditText[]{ip1,ip2,ip3,ip4}) { ipRow.addView(e,new LinearLayout.LayoutParams(0,dp(42),1)); }
        Button lockIp=smallButton("Lock On"); ipRow.addView(lockIp,new LinearLayout.LayoutParams(dp(78),dp(42))); root.addView(ipRow);
        addGap(root,8);
        TextView selectedLabel=txt("Selected Target",12,white,false); selectedLabel.setGravity(Gravity.CENTER); selectedLabel.setBackgroundColor(panel); root.addView(selectedLabel);
        selected=txt("NONE",31,white,false); selected.setGravity(Gravity.CENTER); selected.setPadding(0,dp(14),0,dp(14)); selected.setBackgroundColor(blue); root.addView(selected);
        addGap(root,10);
        root.addView(section("2. Settings"));
        LinearLayout timeoutRow=row(); timeoutRow.addView(label("Timeout (ms)"),new LinearLayout.LayoutParams(dp(90),dp(44)));
        timeoutField=edit("5000"); timeoutRow.addView(timeoutField,new LinearLayout.LayoutParams(0,dp(44),1));
        timeoutRow.addView(label("Port"),new LinearLayout.LayoutParams(dp(48),dp(44)));
        portField=edit("80"); timeoutRow.addView(portField,new LinearLayout.LayoutParams(0,dp(44),1)); root.addView(timeoutRow);
        addGap(root,6);
        root.addView(label("Message / test note")); messageField=edit("LOIC Diagnostics — single test"); root.addView(messageField);
        addGap(root,6);
        LinearLayout methodRow=row(); methodRow.addView(label("Method"),new LinearLayout.LayoutParams(dp(48),dp(44)));
        methodSpinner=new Spinner(this); String[] methods={"TCP","UDP","HTTP"}; methodSpinner.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,methods));
        methodRow.addView(methodSpinner,new LinearLayout.LayoutParams(0,dp(44),1));
        methodRow.addView(label("Checks"),new LinearLayout.LayoutParams(dp(55),dp(44)));
        threadsField=edit("1"); threadsField.setEnabled(false); methodRow.addView(threadsField,new LinearLayout.LayoutParams(dp(58),dp(44))); root.addView(methodRow);
        addGap(root,7);
        speedBar=new SeekBar(this); speedBar.setMax(9); speedBar.setProgress(4); root.addView(speedBar);
        TextView speed=txt("<= slower       Test interval       faster =>",11,muted,false); speed.setGravity(Gravity.CENTER); root.addView(speed);
        addGap(root,12);
        root.addView(section("3. Ready?"));
        runButton=actionButton("RUN TEST",accent); root.addView(runButton);
        stopButton=actionButton("STOP",Color.GRAY); stopButton.setEnabled(false); root.addView(stopButton);
        elapsed=txt("Elapsed Time: —",13,muted,false); elapsed.setGravity(Gravity.CENTER); root.addView(elapsed);
        LinearLayout metrics=new LinearLayout(this); metrics.setOrientation(1); metrics.setPadding(dp(10),dp(10),dp(10),dp(10)); metrics.setBackgroundColor(blue);
        status=txt("READY",15,white,true); status.setGravity(Gravity.CENTER); metrics.addView(status);
        result=txt("No test executed",23,white,true); result.setGravity(Gravity.CENTER); metrics.addView(result);
        rate=txt("One request per run • No traffic flooding",12,muted,false); rate.setGravity(Gravity.CENTER); metrics.addView(rate); root.addView(metrics);
        TextView disclaimer=txt("SAFE DIAGNOSTICS MODE  •  One bounded test at a time",10,muted,false); disclaimer.setGravity(Gravity.CENTER); root.addView(disclaimer);
        setContentView(scroll);
        lockUrl.setOnClickListener(v->{String h=hostFromUrl(urlField.getText().toString().trim()); if(h!=null){selected.setText(h); fillIp(h);} else toast("Introduce una URL válida.");});
        lockIp.setOnClickListener(v->{String h=ipString(); if(validIp(h)) selected.setText(h); else toast("Completa una dirección IPv4 válida.");});
        runButton.setOnClickListener(v->startTest());
        stopButton.setOnClickListener(v->{cancelled.set(true); if(worker!=null)worker.interrupt(); status.setText("STOPPED"); stopButton.setEnabled(false); runButton.setEnabled(true);});
        methodSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){} public void onItemSelected(AdapterView<?> p,View v,int pos,long id){portField.setText(pos==2?"80":pos==1?"7":"443");}});
    }

    private void startTest(){
        String host=selected.getText().toString().trim();
        if(host.equals("NONE")){toast("Primero selecciona y bloquea un objetivo.");return;}
        final int timeout,port;
        try{timeout=Integer.parseInt(timeoutField.getText().toString());port=Integer.parseInt(portField.getText().toString()); if(timeout<500||timeout>15000||port<1||port>65535)throw new Exception();}
        catch(Exception e){toast("Timeout: 500–15000 ms. Puerto: 1–65535.");return;}
        final String method=methodSpinner.getSelectedItem().toString();
        if(method.equals("HTTP") && !(urlField.getText().toString().trim().startsWith("http://")||urlField.getText().toString().trim().startsWith("https://"))){toast("Para HTTP introduce una URL http:// o https://.");return;}
        cancelled.set(false); runButton.setEnabled(false); stopButton.setEnabled(true); status.setText("RUNNING "+method); result.setText("…"); rate.setText("Ejecutando una única prueba"); startedAt=System.currentTimeMillis();
        worker=new Thread(()->{
            String message; boolean ok=false; long t=System.currentTimeMillis();
            try{
                if(method.equals("TCP")){
                    try(Socket s=new Socket()){s.connect(new InetSocketAddress(host,port),timeout); ok=true; message="TCP CONNECTED\n"+host+":"+port;}
                } else if(method.equals("UDP")){
                    try(DatagramSocket s=new DatagramSocket()){
                        s.setSoTimeout(timeout); byte[] data="LOIC-Diagnostics probe".getBytes("UTF-8");
                        InetAddress address=InetAddress.getByName(host);
                        s.send(new DatagramPacket(data,data.length,address,port));
                        message="UDP datagram sent once\n"+host+":"+port+"\nUDP has no connection handshake; delivery cannot be confirmed.";
                        ok=true;
                    }
                } else {
                    String u=urlField.getText().toString().trim();
                    HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();
                    c.setRequestMethod("HEAD"); c.setConnectTimeout(timeout); c.setReadTimeout(timeout); c.setInstanceFollowRedirects(false);
                    int code=c.getResponseCode(); message="HTTP RESPONSE\n"+u+"\nStatus: "+code; ok=code>0; c.disconnect();
                }
            }catch(Exception e){message="TEST FAILED\n"+e.getClass().getSimpleName()+": "+(e.getMessage()==null?"No response":e.getMessage());}
            final String m=message; final boolean success=ok; final long duration=System.currentTimeMillis()-t;
            runOnUiThread(()->{worker=null; runButton.setEnabled(true); stopButton.setEnabled(false); if(cancelled.get()){status.setText("STOPPED");result.setText("Stopped");}else{status.setText(success?"TEST COMPLETE":"NO RESPONSE / ERROR");result.setText(success?"1 CHECK OK":"CHECK FAILED");rate.setText(m);} elapsed.setText("Elapsed Time: "+duration+" ms");});
        }); worker.start();
    }

    private String ipString(){return ip1.getText()+"."+ip2.getText()+"."+ip3.getText()+"."+ip4.getText();}
    private boolean validIp(String s){try{String[] a=s.split("\\.");if(a.length!=4)return false;for(String x:a){int n=Integer.parseInt(x);if(n<0||n>255)return false;}return true;}catch(Exception e){return false;}}
    private void fillIp(String h){if(validIp(h)){String[] a=h.split("\\.");ip1.setText(a[0]);ip2.setText(a[1]);ip3.setText(a[2]);ip4.setText(a[3]);}}
    private String hostFromUrl(String u){try{if(!u.contains("://"))u="http://"+u;return new URL(u).getHost();}catch(Exception e){return null;}}
    private EditText ipBox(String s){EditText e=edit(s);e.setGravity(Gravity.CENTER);return e;}
    private EditText edit(String hint){EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(14);e.setTextColor(Color.BLACK);e.setHintTextColor(Color.DKGRAY);e.setHint(hint);e.setPadding(dp(8),0,dp(8),0);e.setBackground(round(fieldBg,3));return e;}
    private TextView section(String s){TextView t=txt(s,18,white,false);t.setPadding(0,dp(8),0,dp(5));return t;}
    private TextView label(String s){TextView t=txt(s,11,muted,false);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private TextView txt(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private Button smallButton(String s){Button b=new Button(this);b.setText(s);b.setTextSize(11);b.setPadding(0,0,0,0);b.setTextColor(Color.BLACK);b.setBackground(round(Color.LTGRAY,2));return b;}
    private Button actionButton(String s,int color){Button b=new Button(this);b.setText(s);b.setTextColor(color);b.setTextSize(18);b.setBackground(round(Color.LTGRAY,2));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(46));p.bottomMargin=dp(5);b.setLayoutParams(p);return b;}
    private GradientDrawable round(int color,int r){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(r));return d;}
    private void addGap(LinearLayout l,int h){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
