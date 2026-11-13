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
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
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
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class BridgeApp extends Activity {
    private static final String APP_VERSION = "2.0.3";
    public static volatile TextView logView;
    public static volatile TextView statusView;
    public static volatile ScrollView scrollView;
    private Button batteryBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0F172A"));
        root.setPadding(40, 60, 40, 40);

        // Заголовок
        TextView title = new TextView(this);
        title.setText("POS TCP Bridge v" + APP_VERSION);
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

        statusView = new TextView(this);
        statusView.setText("●  ЗАПУСК...  |  http://127.0.0.1:8088");
        statusView.setTextColor(Color.parseColor("#FBBF24"));
        statusView.setTextSize(15);
        statusView.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(statusView);

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

        Button restartBtn = createStyledButton("Перезапуск", "#2563EB", "#1D4ED8");
        restartBtn.setOnClickListener(v -> {
            BridgeService.log("Натиснуто: Перезапуск");
            BridgeService.restartServer(this);
        });
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp1.rightMargin = 8;
        btnRow.addView(restartBtn, lp1);

        Button testBtn = createStyledButton("Тест :8088", "#059669", "#047857");
        testBtn.setOnClickListener(v -> runSelfPingTest());
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp2.leftMargin = 8;
        lp2.rightMargin = 8;
        btnRow.addView(testBtn, lp2);

        Button clearBtn = createStyledButton("Очистити", "#334155", "#1E293B");
        clearBtn.setOnClickListener(v -> BridgeService.clearLogs());
        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp3.leftMargin = 8;
        btnRow.addView(clearBtn, lp3);

        root.addView(btnRow);

        // Кнопка вимкнення оптимізації батареї
        batteryBtn = createStyledButton("Вимкнути сплячий режим для мосту", "#D97706", "#B45309");
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
        scrollView.setFillViewport(true);
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
        scrollView.addView(logView, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(scrollView);
        setContentView(root);

        BridgeService.log("Інтерфейс запущено (v" + APP_VERSION + ", API " + Build.VERSION.SDK_INT + ")");
        startBridgeService();
        updateBatteryButton();
        refreshUi();

        if (Build.VERSION.SDK_INT >= 33) {
            try {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 101);
            } catch (Throwable t) {
                BridgeService.log("POST_NOTIFICATIONS запит: " + t.getMessage());
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBatteryButton();
        if (!BridgeService.isListening()) {
            startBridgeService();
        }
        refreshUi();
    }

    public static void refreshUi() {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (statusView != null) {
                if (BridgeService.isListening()) {
                    statusView.setText("●  АКТИВНИЙ  |  http://127.0.0.1:8088");
                    statusView.setTextColor(Color.parseColor("#4ADE80"));
                } else {
                    String err = BridgeService.lastError;
                    String suffix = (err != null && !err.isEmpty()) ? (" (" + err + ")") : "";
                    statusView.setText("●  НЕ АКТИВНИЙ  |  127.0.0.1:8088" + suffix);
                    statusView.setTextColor(Color.parseColor("#F87171"));
                }
            }
            if (logView != null) {
                logView.setText(BridgeService.getLogs());
                if (scrollView != null) {
                    scrollView.post(() -> {
                        if (scrollView != null) scrollView.fullScroll(View.FOCUS_DOWN);
                    });
                }
            }
        });
    }

    private void runSelfPingTest() {
        BridgeService.log("Самодіагностика: GET http://127.0.0.1:8088/ping ...");
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("http://127.0.0.1:8088/ping");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                int code = conn.getResponseCode();
                InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                StringBuilder sb = new StringBuilder();
                if (is != null) {
                    byte[] buf = new byte[512];
                    int n;
                    while ((n = is.read(buf)) != -1) {
                        sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                    }
                    is.close();
                }
                BridgeService.log("Самодіагностика OK (" + code + "): " + sb.toString());
                runOnUiThread(() -> Toast.makeText(this, "OK (" + code + "): 127.0.0.1:8088 працює!", Toast.LENGTH_SHORT).show());
            } catch (Throwable t) {
                BridgeService.log("Самодіагностика ПОМИЛКА: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Помилка :8088: " + t.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private Button createStyledButton(String text, String hexColor, String pressedColor) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setAllCaps(false);

        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(Color.parseColor(hexColor));
        normalBg.setCornerRadius(14f);

        GradientDrawable pressedBg = new GradientDrawable();
        pressedBg.setColor(Color.parseColor(pressedColor));
        pressedBg.setCornerRadius(14f);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressedBg);
        states.addState(new int[]{}, normalBg);

        b.setBackground(states);
        b.setPadding(16, 22, 16, 22);
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
        BridgeService.ensureServerRunning();
        try {
            Intent s = new Intent(this, BridgeService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(s);
            } else {
                startService(s);
            }
        } catch (Throwable t) {
            BridgeService.log("Попередження startForegroundService: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    public static class BridgeService extends Service {
    private static final Object SERVER_LOCK = new Object();
    private static ServerSocket serverSocket;
    private static Thread serverThread;
    private static volatile long serverGeneration = 0;
    private static volatile boolean running = false;
    public static volatile String lastError = null;
    private static PowerManager.WakeLock wakeLock;
    private static final StringBuilder logs = new StringBuilder();

    public static boolean isListening() {
        return running && serverSocket != null && !serverSocket.isClosed() && serverSocket.isBound();
    }

    public static String getLogs() {
        synchronized (logs) { return logs.toString(); }
    }

    public static void clearLogs() {
        synchronized (logs) { logs.setLength(0); }
        log("Журнал очищено. Статус :8088 = " + (isListening() ? "АКТИВНИЙ" : "НЕ АКТИВНИЙ"));
    }

    public static void log(String msg) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        String line = "[" + time + "] " + msg + "\n";
        synchronized (logs) {
            if (logs.length() > 12000) logs.delete(0, 6000);
            logs.append(line);
        }
        refreshUi();
    }

    public static void ensureServerRunning() {
        synchronized (SERVER_LOCK) {
            if (isListening() && serverThread != null && serverThread.isAlive()) {
                return;
            }
            startServerLocked();
        }
    }

    public static void restartServer(Context ctx) {
        synchronized (SERVER_LOCK) {
            running = false;
            serverGeneration++;
            try {
                if (serverSocket != null) serverSocket.close();
            } catch (Exception ignored) {}
            serverSocket = null;
            startServerLocked();
        }
        try {
            Intent s = new Intent(ctx, BridgeService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(s);
            } else {
                ctx.startService(s);
            }
        } catch (Throwable t) {
            log("Попередження перезапуску сервісу: " + t.getMessage());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            acquireWakeLock();
            startForegroundNotification();
        } catch (Throwable t) {
            log("Помилка Foreground-сервісу (onCreate): " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        ensureServerRunning();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            acquireWakeLock();
            startForegroundNotification();
        } catch (Throwable t) {
            log("Помилка Foreground-сервісу (onStartCommand): " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        ensureServerRunning();
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
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(ch);
            }

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
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(1, n);
            }
        }
    }

    private static void startServerLocked() {
        final long myGen = ++serverGeneration;
        running = true;
        lastError = null;
        refreshUi();
        serverThread = new Thread(() -> {
            ServerSocket sock = null;
            try {
                sock = new ServerSocket();
                sock.setReuseAddress(true);
                sock.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 8088), 50);
                synchronized (SERVER_LOCK) {
                    if (myGen != serverGeneration || !running) {
                        try { sock.close(); } catch (Exception ignored) {}
                        return;
                    }
                    serverSocket = sock;
                }
                lastError = null;
                log("Сервер запущено на 127.0.0.1:8088");
                while (running && myGen == serverGeneration) {
                    Socket client = sock.accept();
                    new Thread(() -> handleHttpClient(client)).start();
                }
            } catch (Throwable e) {
                if (myGen == serverGeneration) {
                    if (running) {
                        lastError = e.getMessage();
                        log("Помилка сокета: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    } else {
                        log("Сервер зупинено");
                    }
                    running = false;
                    refreshUi();
                }
            } finally {
                try {
                    if (sock != null && !sock.isClosed()) sock.close();
                } catch (Exception ignored) {}
                synchronized (SERVER_LOCK) {
                    if (myGen == serverGeneration && serverSocket == sock) {
                        serverSocket = null;
                        running = false;
                        refreshUi();
                    }
                }
            }
        }, "PosBridgeHttp-" + myGen);
        serverThread.start();
    }

    private static void handleHttpClient(Socket client) {
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
            int qPos = path.indexOf('?');
            if (qPos >= 0) {
                path = path.substring(0, qPos);
            }

            if ("OPTIONS".equals(method)) {
                log("<- HTTP OPTIONS " + path);
                sendHttp(out, 204, "");
                return;
            }

            if ("/ping".equals(path) || ("GET".equals(method) && "/".equals(path))) {
                log("<- HTTP " + method + " " + path);
                sendHttp(out, 200, "{\"ok\":true,\"status\":\"ok\",\"version\":\"" + APP_VERSION + "\"}");
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
            boolean waitFinalResponse = req.optBoolean("waitFinalResponse", false);
            Set<String> ignoreMethods = new HashSet<>();
            if (req.has("ignoreMethods") && !req.isNull("ignoreMethods")) {
                JSONArray arr = req.optJSONArray("ignoreMethods");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        String m = arr.optString(i, "").trim();
                        if (!m.isEmpty()) {
                            ignoreMethods.add(m.toLowerCase(Locale.US));
                        }
                    }
                }
            } else if (waitFinalResponse) {
                ignoreMethods.add("servicemessage");
            }

            byte[] payload;
            String dataB64 = req.optString("dataBase64", "");
            if (!req.isNull("dataBase64") && !dataB64.isEmpty()) {
                payload = Base64.decode(dataB64, Base64.DEFAULT);
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
                JSONArray serviceMessages = new JSONArray();

                if (waitResponse) {
                    InputStream tcpIn = tcp.getInputStream();
                    byte[] buf = new byte[4096];

                    if (waitFinalResponse) {
                        ByteArrayOutputStream frameBuf = new ByteArrayOutputStream();
                        long deadlineMs = System.currentTimeMillis() + Math.max(1, readTimeout);
                        byte[] finalFrameBytes = null;
                        boolean finalHadNull = false;

                        while (finalFrameBytes == null) {
                            long remainingMs = deadlineMs - System.currentTimeMillis();
                            if (remainingMs <= 0) {
                                throw new SocketTimeoutException("Timeout waiting for final TCP response (" + readTimeout + " ms)");
                            }
                            tcp.setSoTimeout((int) Math.min(Integer.MAX_VALUE, remainingMs));

                            int n = tcpIn.read(buf);
                            if (n == -1) {
                                if (frameBuf.size() > 0) {
                                    byte[] tail = frameBuf.toByteArray();
                                    String tailTrim = new String(tail, StandardCharsets.UTF_8).trim();
                                    if (!tailTrim.isEmpty() && !tryConsumeIgnoredFrame(tailTrim, ignoreMethods, serviceMessages)) {
                                        finalFrameBytes = tail;
                                    }
                                }
                                break;
                            }

                            int start = 0;
                            for (int i = 0; i < n; i++) {
                                if (buf[i] == 0) {
                                    if (i > start) {
                                        frameBuf.write(buf, start, i - start);
                                    }
                                    byte[] frameBytes = frameBuf.toByteArray();
                                    frameBuf.reset();
                                    start = i + 1;

                                    if (frameBytes.length == 0) {
                                        continue;
                                    }
                                    String frameTrim = new String(frameBytes, StandardCharsets.UTF_8).trim();
                                    if (frameTrim.isEmpty()) {
                                        continue;
                                    }
                                    if (tryConsumeIgnoredFrame(frameTrim, ignoreMethods, serviceMessages)) {
                                        log("<- TCP ServiceMessage (" + frameBytes.length + " B)");
                                        continue;
                                    }
                                    finalFrameBytes = frameBytes;
                                    finalHadNull = true;
                                    break;
                                }
                            }
                            if (finalFrameBytes == null && start < n) {
                                frameBuf.write(buf, start, n - start);
                            }
                        }

                        if (finalFrameBytes == null) {
                            if (serviceMessages.length() > 0) {
                                throw new SocketTimeoutException("TCP socket closed before final response");
                            }
                            finalFrameBytes = new byte[0];
                        }

                        respText = new String(finalFrameBytes, StandardCharsets.UTF_8);
                        if (finalHadNull) {
                            byte[] rawWithNull = new byte[finalFrameBytes.length + 1];
                            System.arraycopy(finalFrameBytes, 0, rawWithNull, 0, finalFrameBytes.length);
                            rawWithNull[finalFrameBytes.length] = 0;
                            respB64 = Base64.encodeToString(rawWithNull, Base64.NO_WRAP);
                            log("<- TCP фінальна відповідь: " + rawWithNull.length + " B (ServiceMessages: " + serviceMessages.length() + ")");
                        } else {
                            respB64 = Base64.encodeToString(finalFrameBytes, Base64.NO_WRAP);
                            log("<- TCP фінальна відповідь: " + finalFrameBytes.length + " B (ServiceMessages: " + serviceMessages.length() + ")");
                        }
                    } else {
                        ByteArrayOutputStream respBuf = new ByteArrayOutputStream();
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
                    }
                } else {
                    log("<- Відправлено без очікування відповіді");
                }

                JSONObject res = new JSONObject();
                res.put("ok", true);
                res.put("data", respText);
                res.put("dataBase64", respB64);
                res.put("serviceMessages", serviceMessages);
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

    private static boolean tryConsumeIgnoredFrame(String frameTrim, Set<String> ignoreMethods, JSONArray serviceMessages) {
        if (ignoreMethods == null || ignoreMethods.isEmpty() || frameTrim.isEmpty() || frameTrim.charAt(0) != '{') {
            return false;
        }
        try {
            JSONObject obj = new JSONObject(frameTrim);
            String method = obj.optString("method", "").trim();
            if (!method.isEmpty() && ignoreMethods.contains(method.toLowerCase(Locale.US))) {
                serviceMessages.put(obj);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static void sendHttp(OutputStream out, int code, String body) throws Exception {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        String status = code == 200 ? "200 OK" : (code == 204 ? "204 No Content" : "500 Internal Server Error");
        StringBuilder hdr = new StringBuilder();
        hdr.append("HTTP/1.1 ").append(status).append("\r\n")
            .append("Access-Control-Allow-Origin: *\r\n")
            .append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            .append("Access-Control-Allow-Headers: *\r\n")
            .append("Access-Control-Allow-Private-Network: true\r\n")
            .append("Access-Control-Max-Age: 86400\r\n");
        if (code != 204) {
            hdr.append("Content-Type: application/json; charset=utf-8\r\n")
                .append("Content-Length: ").append(b.length).append("\r\n");
        }
        hdr.append("Connection: close\r\n\r\n");
        out.write(hdr.toString().getBytes(StandardCharsets.UTF_8));
        if (b.length > 0) out.write(b);
        out.flush();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}

    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                Intent s = new Intent(context, BridgeService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(s);
                } else {
                    context.startService(s);
                }
            } catch (Exception ignored) {}
        }
    }
}
