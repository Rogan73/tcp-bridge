package com.rikosoft.posbridge;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BridgeApp extends Activity {
    public static TextView logView;
    public static ScrollView scrollView;
    private Button batteryBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 101);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0F172A"));
        root.setPadding(40, 60, 40, 40);

        // Заголовок
        TextView title = new TextView(this);
        title.setText("POS TCP Bridge");
        title.setTextColor(Color.parseColor("#F8FAFC"));
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Локальний проксі-сервер для Web-ПРРО та обладнання");
        subtitle.setTextColor(Color.parseColor("#94A3B8"));
        subtitle.setTextSize(13);
        subtitle.setPadding(0, 6, 0, 28);
        root.addView(subtitle);

        // Картка статусу
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(32, 28, 32, 28);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#1E293B"));
        cardBg.setCornerRadius(20f);
        cardBg.setStroke(2, Color.parseColor("#334155"));
        card.setBackground(cardBg);

        TextView statusLine = new TextView(this);
        statusLine.setText("●  АКТИВНИЙ  |  http://127.0.0.1:8088");
        statusLine.setTextColor(Color.parseColor("#4ADE80"));
        statusLine.setTextSize(15);
        statusLine.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(statusLine);

        TextView endpoints = new TextView(this);
        endpoints.setText("GET  /ping  — перевірка зв'язку\nPOST /tcp   — відправка у TCP-сокет (JSON / Base64)");
        endpoints.setTextColor(Color.parseColor("#CBD5E1"));
        endpoints.setTextSize(12);
        endpoints.setTypeface(Typeface.MONOSPACE);
        endpoints.setPadding(0, 14, 0, 0);
        card.addView(endpoints);

        root.addView(card);

        // Панель кнопок
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, 24, 0, 16);

        Button restartBtn = createStyledButton("Перезапуск", "#2563EB");
        restartBtn.setOnClickListener(v -> {
            BridgeService.restartServer(this);
        });
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp1.rightMargin = 12;
        btnRow.addView(restartBtn, lp1);

        Button clearBtn = createStyledButton("Очистити лог", "#334155");
        clearBtn.setOnClickListener(v -> BridgeService.clearLogs());
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp2.leftMargin = 12;
        btnRow.addView(clearBtn, lp2);

        root.addView(btnRow);

        // Кнопка вимкнення оптимізації батареї
        batteryBtn = createStyledButton("Вимкнути сплячий режим для мосту", "#D97706");
        batteryBtn.setOnClickListener(v -> requestBatteryExemption());
        LinearLayout.LayoutParams lpBat = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpBat.bottomMargin = 20;
        root.addView(batteryBtn, lpBat);

        // Заголовок логу
        TextView logHeader = new TextView(this);
        logHeader.setText("ЖУРНАЛ ПОДІЙ:");
        logHeader.setTextColor(Color.parseColor("#64748B"));
        logHeader.setTextSize(11);
        logHeader.setTypeface(Typeface.DEFAULT_BOLD);
        logHeader.setPadding(0, 4, 0, 8);
        root.addView(logHeader);

        // Вікно логу
        scrollView = new ScrollView(this);
        GradientDrawable logBg = new GradientDrawable();
        logBg.setColor(Color.parseColor("#090D16"));
        logBg.setCornerRadius(16f);
        logBg.setStroke(2, Color.parseColor("#1E293B"));
        scrollView.setBackground(logBg);
        scrollView.setPadding(24, 20, 24, 20);

        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollView.setLayoutParams(scrollLp);

        logView = new TextView(this);
        logView.setTextColor(Color.parseColor("#38BDF8"));
        logView.setTextSize(11);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setText(BridgeService.getLogs());
        scrollView.addView(logView);

        root.addView(scrollView);
        setContentView(root);

        startBridgeService();
        updateBatteryButton();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBatteryButton();
        if (logView != null) logView.setText(BridgeService.getLogs());
    }

    private Button createStyledButton(String text, String hexColor) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(hexColor));
        bg.setCornerRadius(14f);
        b.setBackground(bg);
        b.setPadding(20, 22, 20, 22);
        return b;
    }

    private void updateBatteryButton() {
        if (batteryBtn == null) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
            batteryBtn.setVisibility(View.GONE);
        } else {
            batteryBtn.setVisibility(View.VISIBLE);
        }
    }

    private void requestBatteryExemption() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            BridgeService.log("Не вдалося відкрити налаштування батареї: " + e.getMessage());
        }
    }

    private void startBridgeService() {
        Intent s = new Intent(this, BridgeService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(s);
        } else {
            startService(s);
        }
    }
}

class BridgeService extends Service {
    private static ServerSocket serverSocket;
    private static volatile boolean running = false;
    private static PowerManager.WakeLock wakeLock;
    private static final StringBuilder logs = new StringBuilder();

    public static String getLogs() {
        synchronized (logs) { return logs.toString(); }
    }

    public static void clearLogs() {
        synchronized (logs) { logs.setLength(0); }
        log("Журнал очищено.");
    }

    public static void log(String msg) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        String line = "[" + time + "] " + msg + "\n";
        synchronized (logs) {
            if (logs.length() > 12000) logs.delete(0, 6000);
            logs.append(line);
        }
        if (BridgeApp.logView != null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (BridgeApp.logView != null) {
                    BridgeApp.logView.setText(getLogs());
                    if (BridgeApp.scrollView != null) {
                        BridgeApp.scrollView.fullScroll(View.FOCUS_DOWN);
                    }
                }
            });
        }
    }

    public static void restartServer(Context ctx) {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {}
        Intent s = new Intent(ctx, BridgeService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(s);
        } else {
            ctx.startService(s);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        acquireWakeLock();
        startForegroundNotification();
        startHttpServer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        acquireWakeLock();
        startForegroundNotification();
        if (!running || serverSocket == null || serverSocket.isClosed()) {
            startHttpServer();
        }
        return START_STICKY;
    }

    private void acquireWakeLock() {
        try {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "POSBridge::Lock");
                    wakeLock.setReferenceCounted(false);
                }
            }
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire();
            }
        } catch (Exception ignored) {}
    }

    private void startForegroundNotification() {
        String chId = "pos_bridge_service";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                chId, "POS TCP Bridge", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Фонова робота локального TCP-мосту");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);

            Intent openIntent = new Intent(this, BridgeApp.class);
            int flags = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_IMMUTABLE : 0;
            PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent, flags);

            Notification n = new Notification.Builder(this, chId)
                .setContentTitle("POS TCP Bridge працює")
                .setContentText("Проксі 127.0.0.1:8088 -> TCP активний")
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
            startForeground(1, n);
        }
    }

    private synchronized void startHttpServer() {
        if (running && serverSocket != null && !serverSocket.isClosed()) return;
        running = true;
        new Thread(() -> {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 8088), 50);
                log("Сервер запущено на 127.0.0.1:8088");
                while (running) {
                    Socket client = serverSocket.accept();
                    new Thread(() -> handleHttpClient(client)).start();
                }
            } catch (Exception e) {
                if (running) log("Зупинка сокета: " + e.getMessage());
                running = false;
            }
        }).start();
    }

    private void handleHttpClient(Socket client) {
        try (Socket c = client) {
            c.setSoTimeout(15000);
            InputStream in = c.getInputStream();
            OutputStream out = c.getOutputStream();

            ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
            int b, state = 0;
            while ((b = in.read()) != -1) {
                headerBuf.write(b);
                if ((state == 0 || state == 2) && b == '\r') state++;
                else if ((state == 1 || state == 3) && b == '\n') state++;
                else state = 0;
                if (state == 4) break;
                if (headerBuf.size() > 16384) break;
            }

            String headers = new String(headerBuf.toByteArray(), StandardCharsets.UTF_8);
            if (headers.isEmpty()) return;

            String firstLine = headers.split("\r\n")[0];
            String[] parts = firstLine.split(" ");
            String method = parts.length > 0 ? parts[0].toUpperCase(Locale.US) : "";
            String path = parts.length > 1 ? parts[1] : "/";

            if ("OPTIONS".equals(method)) {
                sendHttp(out, 204, "");
                return;
            }

            if ("/ping".equals(path) || ("GET".equals(method) && "/".equals(path))) {
                sendHttp(out, 200, "{\"ok\":true,\"status\":\"ok\",\"version\":\"2.0.0\"}");
                return;
            }

            int contentLength = 0;
            for (String line : headers.split("\r\n")) {
                if (line.toLowerCase(Locale.US).startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                }
            }

            byte[] bodyBytes = new byte[contentLength];
            int read = 0;
            while (read < contentLength) {
                int r = in.read(bodyBytes, read, contentLength - read);
                if (r == -1) break;
                read += r;
            }

            String bodyStr = new String(bodyBytes, StandardCharsets.UTF_8);
            JSONObject req = new JSONObject(bodyStr);

            String host = req.getString("host").trim();
            int port = req.getInt("port");
            int connectTimeout = req.optInt("connectTimeout", 5000);
            int readTimeout = req.optInt("timeout", 65000);
            boolean waitResponse = req.optBoolean("waitResponse", true);
            boolean ensureNull = req.optBoolean("ensureNullTerminator", false);

            byte[] payload;
            if (req.has("dataBase64") && !req.getString("dataBase64").isEmpty()) {
                payload = Base64.decode(req.getString("dataBase64"), Base64.DEFAULT);
            } else {
                String data = req.optString("data", "");
                if (ensureNull && !data.endsWith("\0")) {
                    data += "\0";
                }
                payload = data.getBytes(StandardCharsets.UTF_8);
            }

            log("-> TCP " + host + ":" + port + " (" + payload.length + " B)");

            try (Socket tcp = new Socket()) {
                tcp.connect(new InetSocketAddress(host, port), connectTimeout);
                tcp.setSoTimeout(readTimeout);

                OutputStream tcpOut = tcp.getOutputStream();
                tcpOut.write(payload);
                tcpOut.flush();

                String respText = "";
                String respB64 = "";

                if (waitResponse) {
                    InputStream tcpIn = tcp.getInputStream();
                    ByteArrayOutputStream respBuf = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    try {
                        int n;
                        while ((n = tcpIn.read(buf)) != -1) {
                            respBuf.write(buf, 0, n);
                            byte[] cur = respBuf.toByteArray();
                            // Термінали ПриватБанку завершують JSON-відповідь нульовим байтом \0
                            if (cur.length > 0 && cur[cur.length - 1] == 0) {
                                break;
                            }
                            // Для пристроїв без \0 даємо коротку паузу на склеювання TCP-фрагментів
                            tcp.setSoTimeout(350);
                        }
                    } catch (SocketTimeoutException ste) {
                        if (respBuf.size() == 0) throw ste;
                    }

                    byte[] rawResp = respBuf.toByteArray();
                    respB64 = Base64.encodeToString(rawResp, Base64.NO_WRAP);
                    // Прибираємо завершальний \0 з текстового поля для чистого JSON
                    int textLen = (rawResp.length > 0 && rawResp[rawResp.length - 1] == 0)
                        ? rawResp.length - 1 : rawResp.length;
                    respText = new String(rawResp, 0, textLen, StandardCharsets.UTF_8);
                    log("<- TCP відповідь: " + rawResp.length + " B");
                } else {
                    log("<- Відправлено без очікування відповіді");
                }

                JSONObject res = new JSONObject();
                res.put("ok", true);
                res.put("data", respText);
                res.put("dataBase64", respB64);
                sendHttp(out, 200, res.toString());
            }
        } catch (Exception e) {
            log("Помилка: " + e.getMessage());
            try {
                JSONObject err = new JSONObject();
                err.put("ok", false);
                err.put("error", String.valueOf(e.getMessage()));
                sendHttp(client.getOutputStream(), 500, err.toString());
            } catch (Exception ignored) {}
        }
    }

    private void sendHttp(OutputStream out, int code, String body) throws Exception {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        String status = code == 200 ? "200 OK" : (code == 204 ? "204 No Content" : "500 Internal Server Error");
        String hdr = "HTTP/1.1 " + status + "\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
            "Access-Control-Allow-Headers: *\r\n" +
            "Access-Control-Allow-Private-Network: true\r\n" +
            "Access-Control-Max-Age: 86400\r\n" +
            "Content-Length: " + b.length + "\r\n" +
            "Connection: close\r\n\r\n";
        out.write(hdr.getBytes(StandardCharsets.UTF_8));
        if (b.length > 0) out.write(b);
        out.flush();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}

class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent s = new Intent(context, BridgeService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(s);
        } else {
            context.startService(s);
        }
    }
}
