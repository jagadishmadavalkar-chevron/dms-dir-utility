package com.dir.sync.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events endpoint that streams the application log file.
 * Clients connect once and receive new log lines as they are written.
 *
 * GET /api/logs/stream
 */
@RestController
@RequestMapping("/api/logs")
public class LogController {

    private static final Logger logger      = LogManager.getLogger(LogController.class);
    private static final String LOG_FILE    = "logs/dir-sync-service.log";
    private static final long   SSE_TIMEOUT = 30 * 60 * 1000L; // 30 min

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        executor.execute(() -> {
            Path path = Paths.get(LOG_FILE);
            try {
                // Send existing tail first (last 200 lines)
                if (Files.exists(path)) {
                    var lines = Files.readAllLines(path);
                    int start = Math.max(0, lines.size() - 200);
                    for (int i = start; i < lines.size(); i++) {
                        emitter.send(lines.get(i));
                    }
                }

                // Follow new lines
                long lastModified = Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : 0;
                long lastSize     = Files.exists(path) ? Files.size(path) : 0;

                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(500);
                    if (!Files.exists(path)) continue;

                    long currentSize = Files.size(path);
                    if (currentSize > lastSize) {
                        var all = Files.readAllLines(path);
                        // Approximate new lines by line count delta
                        int approxOffset = (int) (all.size() * lastSize / Math.max(currentSize, 1));
                        for (int i = approxOffset; i < all.size(); i++) {
                            emitter.send(all.get(i));
                        }
                        lastSize = currentSize;
                        lastModified = Files.getLastModifiedTime(path).toMillis();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                emitter.complete();
            } catch (IOException e) {
                logger.error("[LOG-STREAM] Error reading log file", e);
                emitter.completeWithError(e);
            }
        });

        emitter.onCompletion(() -> logger.debug("[LOG-STREAM] SSE connection closed"));
        emitter.onTimeout(emitter::complete);
        return emitter;
    }
}
