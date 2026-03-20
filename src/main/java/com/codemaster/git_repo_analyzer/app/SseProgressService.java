package com.codemaster.git_repo_analyzer.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseProgressService {

  private static final Logger logger = LoggerFactory.getLogger(SseProgressService.class);

  private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

  public SseEmitter addEmitter() {
    SseEmitter emitter = new SseEmitter(0L);
    emitters.add(emitter);
    emitter.onCompletion(() -> emitters.remove(emitter));
    emitter.onTimeout(() -> emitters.remove(emitter));
    emitter.onError(e -> emitters.remove(emitter));
    return emitter;
  }

  public void broadcast(String repoName, String step, String status, String message) {
    Map<String, String> payload = Map.of(
        "repoName", repoName,
        "step", step,
        "status", status,
        "message", message
    );
    for (SseEmitter emitter : emitters) {
      try {
        emitter.send(SseEmitter.event().name("progress").data(payload));
      } catch (IOException e) {
        logger.debug("Removing failed SSE emitter", e);
        emitters.remove(emitter);
      }
    }
  }

}
