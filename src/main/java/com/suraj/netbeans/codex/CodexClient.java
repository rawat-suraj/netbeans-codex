package com.suraj.netbeans.codex;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class CodexClient implements AutoCloseable {

    private final AtomicInteger nextId = new AtomicInteger();
    private final Map<Integer, CompletableFuture<JsonObject>> pending
            = new ConcurrentHashMap<>();

    private final Consumer<JsonObject> notifications;
    private final Consumer<Throwable> disconnected;
    private volatile Process process;
    private BufferedWriter writer;
    private boolean closed;
    private Thread shutdownHook;

    public CodexClient(Consumer<JsonObject> notifications,
            Consumer<Throwable> disconnected) {
        this.notifications = notifications;
        this.disconnected = disconnected;
    }

    // Call from a background thread.
    public void start() throws Exception {
        Path executable = CodexRuntime.prepare();

        synchronized (this) {
            if (closed) {
                throw new IOException("Client is closed.");
            }

            process = new ProcessBuilder(
                    executable.toString(), "app-server")
                    .directory(executable.getParent().toFile())
                    // Keep stderr separate from JSON protocol output.
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();

            writer = new BufferedWriter(new OutputStreamWriter(
                    process.getOutputStream(), StandardCharsets.UTF_8));

            Process startedProcess = process;
            shutdownHook = new Thread(
                    startedProcess::destroy, "codex-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            Thread reader = new Thread(
                    () -> readMessages(startedProcess), "codex-reader");
            reader.setDaemon(true);
            reader.start();
        }

        JsonObject info = new JsonObject();
        info.addProperty("name", "netbeans_codex");
        info.addProperty("title", "NetBeans Codex");
        info.addProperty("version", "1");

        JsonObject params = new JsonObject();
        params.add("clientInfo", info);

        request("initialize", params).get(30, TimeUnit.SECONDS);

        JsonObject initialized = new JsonObject();
        initialized.addProperty("method", "initialized");
        send(initialized);
    }

    public CompletableFuture<JsonObject> request(
            String method, JsonObject params) {

        int id = nextId.incrementAndGet();
        CompletableFuture<JsonObject> result = new CompletableFuture<>();
        pending.put(id, result);

        result.orTimeout(30, TimeUnit.SECONDS)
                .whenComplete((value, error) -> pending.remove(id, result));

        JsonObject message = new JsonObject();
        message.addProperty("id", id);
        message.addProperty("method", method);
        message.add("params", params);

        try {
            send(message);
        } catch (IOException error) {
            result.completeExceptionally(error);
        }

        return result;
    }

    private synchronized void send(JsonObject message) throws IOException {
        if (closed || process == null || !process.isAlive()) {
            throw new IOException("Codex App Server is not running.");
        }

        writer.write(message.toString());
        writer.newLine();
        writer.flush();
    }

    private void readMessages(Process startedProcess) {
        Throwable disconnectError = new IOException("Codex App Server disconnected.");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(startedProcess.getInputStream(),
                        StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                JsonObject message
                        = JsonParser.parseString(line).getAsJsonObject();

                if (message.has("method") && message.has("id")) {
                    // This initial client handles authentication only.
                    JsonObject error = new JsonObject();
                    error.addProperty("code", -32601);
                    error.addProperty("message", "Unsupported client method");

                    JsonObject response = new JsonObject();
                    response.add("id", message.get("id"));
                    response.add("error", error);
                    send(response);

                } else if (message.has("id")) {
                    CompletableFuture<JsonObject> result
                            = pending.remove(message.get("id").getAsInt());

                    if (result != null) {
                        if (message.has("error")) {
                            result.completeExceptionally(new IOException(
                                    message.get("error").toString()));
                        } else {
                            result.complete(
                                    message.getAsJsonObject("result"));
                        }
                    }
                } else if (message.has("method")) {
                    notifications.accept(message);
                }
            }

        } catch (Exception error) {
            disconnectError = error;
        } finally {
            boolean notifyDisconnect;
            synchronized (this) {
                notifyDisconnect = !closed;
                failPending(disconnectError);
                close();
            }
            if (notifyDisconnect) {
                disconnected.accept(disconnectError);
            }
        }
    }

    private void failPending(Throwable error) {
        pending.forEach((id, result)
                -> result.completeExceptionally(error));
        pending.clear();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;

        if (process != null) {
            process.destroy();
        }

        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM shutdown is already underway.
            }
        }

        failPending(new IOException("Codex client closed."));
    }
}
