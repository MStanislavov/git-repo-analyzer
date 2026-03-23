package com.codemaster.git_repo_analyzer.app;

import com.codemaster.git_repo_analyzer.CloningEventScheduler;
import com.codemaster.git_repo_analyzer.event.RepositoryAnalysisDto;
import com.codemaster.git_repo_analyzer.persistence.ApplicationEventRepository;
import com.codemaster.git_repo_analyzer.persistence.ApplicationJobRepository;
import com.codemaster.git_repo_analyzer.persistence.RepositoryAnalysisEntity;
import com.codemaster.git_repo_analyzer.persistence.RepositoryAnalysisRepository;
import com.codemaster.git_repo_analyzer.scraper.RepositoryInfo;
import com.codemaster.git_repo_analyzer.scraper.XmlConfigParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static com.codemaster.git_repo_analyzer.util.Constants.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/v1")
final class SonarDataAnalyzerController {

  private static final Logger logger = LoggerFactory.getLogger(SonarDataAnalyzerController.class);
  private static final String PREF_KEY_CLONE_DIR = "cloneDirectory";

  private final CloningEventScheduler cloningEventScheduler;
  private final RepositoryAnalysisRepository analysisRepository;
  private final ApplicationEventRepository eventRepository;
  private final ApplicationJobRepository jobRepository;
  private final SseProgressService sseProgressService;
  private final RestTemplate restTemplate;
  private final String sonarqubeUrl;
  private final Preferences prefs;
  private final String defaultCloneDirectory;

  SonarDataAnalyzerController(
      CloningEventScheduler cloningEventScheduler,
      RepositoryAnalysisRepository analysisRepository,
      ApplicationEventRepository eventRepository,
      ApplicationJobRepository jobRepository,
      SseProgressService sseProgressService,
      RestTemplate restTemplate,
      @org.springframework.beans.factory.annotation.Value("${sonarqube-url}") String sonarqubeUrl,
      @org.springframework.beans.factory.annotation.Value("${clone-target-directory}") String defaultCloneDirectory) {
    this.cloningEventScheduler = cloningEventScheduler;
    this.analysisRepository = analysisRepository;
    this.eventRepository = eventRepository;
    this.jobRepository = jobRepository;
    this.sseProgressService = sseProgressService;
    this.restTemplate = restTemplate;
    this.sonarqubeUrl = sonarqubeUrl;
    this.defaultCloneDirectory = defaultCloneDirectory;
    this.prefs = Preferences.userNodeForPackage(SonarDataAnalyzerController.class);
  }

  @GetMapping("/config/clone-directory")
  public Map<String, String> getCloneDirectory() {
    String persisted = prefs.get(PREF_KEY_CLONE_DIR, defaultCloneDirectory);
    return Map.of("cloneDirectory", persisted);
  }

  @PutMapping("/config/clone-directory")
  public Map<String, String> saveCloneDirectory(@RequestBody Map<String, String> body) {
    String dir = body.getOrDefault("cloneDirectory", "");
    if (dir.isBlank()) {
      dir = defaultCloneDirectory;
    }
    prefs.put(PREF_KEY_CLONE_DIR, dir);
    return Map.of("cloneDirectory", dir);
  }

  @PostMapping("/config/select-directory")
  public ResponseEntity<Map<String, String>> selectDirectory() {
    String currentDir = prefs.get(PREF_KEY_CLONE_DIR, defaultCloneDirectory);
    try {
      String os = System.getProperty("os.name", "").toLowerCase();
      ProcessBuilder pb;
      if (os.contains("win")) {
        String psScript = String.join("; ",
            "Add-Type -AssemblyName System.Windows.Forms",
            "$f = New-Object System.Windows.Forms.FolderBrowserDialog",
            "$f.Description = 'Select Clone Directory'",
            "$f.SelectedPath = '" + currentDir.replace("'", "''") + "'",
            "$owner = New-Object System.Windows.Forms.Form",
            "$owner.TopMost = $true",
            "$owner.Width = 0; $owner.Height = 0; $owner.ShowInTaskbar = $false; $owner.FormBorderStyle = 'None'",
            "$owner.StartPosition = 'CenterScreen'",
            "$owner.Show(); $owner.Activate()",
            "if ($f.ShowDialog($owner) -eq 'OK') { $f.SelectedPath }",
            "$owner.Close(); $owner.Dispose()");
        pb = new ProcessBuilder("powershell", "-sta", "-NoProfile", "-Command", psScript);
      } else if (os.contains("mac")) {
        String script = "osascript -e 'POSIX path of (choose folder with prompt \"Select Clone Directory\" "
            + "default location POSIX file \"" + currentDir + "\")'";
        pb = new ProcessBuilder("bash", "-c", script);
      } else {
        pb = new ProcessBuilder("zenity", "--file-selection", "--directory",
            "--title=Select Clone Directory", "--filename=" + currentDir + "/");
      }
      pb.redirectErrorStream(true);
      Process process = pb.start();
      String output = new String(process.getInputStream().readAllBytes()).trim();
      int exitCode = process.waitFor();
      if (exitCode == 0 && !output.isEmpty()) {
        prefs.put(PREF_KEY_CLONE_DIR, output);
        return ResponseEntity.ok(Map.of("cloneDirectory", output));
      }
      return ResponseEntity.ok(Map.of("cloneDirectory", currentDir));
    } catch (Exception e) {
      logger.error("Failed to open folder picker", e);
      return ResponseEntity.internalServerError()
          .body(Map.of("error", "Failed to open folder picker: " + e.getMessage()));
    }
  }

  @GetMapping("/sonar/issues")
  public ResponseEntity<Map<String, Object>> getSonarIssues(
      @RequestParam String projectKey,
      @RequestParam(defaultValue = "BUG") String type,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    try {
      String apiUrl = String.format(
          "%s/api/issues/search?componentKeys=%s&types=%s&p=%d&ps=%d&statuses=OPEN,CONFIRMED,REOPENED",
          sonarqubeUrl, projectKey, type, page, pageSize);
      var response = restTemplate.exchange(
          apiUrl, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()),
          new ParameterizedTypeReference<Map<String, Object>>() {});
      return ResponseEntity.ok(response.getBody());
    } catch (Exception e) {
      logger.error("Failed to fetch SonarQube issues for project {}: {}", projectKey, e.getMessage());
      return ResponseEntity.internalServerError()
          .body(Map.of("error", "Failed to fetch issues: " + e.getMessage()));
    }
  }

  private String resolveCloneDirectory(String cloneDirectory) {
    if (cloneDirectory != null && !cloneDirectory.isBlank()) {
      return cloneDirectory;
    }
    return prefs.get(PREF_KEY_CLONE_DIR, defaultCloneDirectory);
  }

  @PostMapping("/analyze/url")
  public ResponseEntity<Map<String, String>> analyzeUrl(@RequestBody Map<String, String> body) {
    String url = body.get("url");
    if (url == null || url.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("error", "URL is required"));
    }
    String cloneDirectory = resolveCloneDirectory(body.get("cloneDirectory"));
    RepositoryInfo repoInfo = new RepositoryInfo(url.trim());
    int jobId = cloningEventScheduler.createJob();
    initializeAnalysisEntity(repoInfo, jobId);
    cloningEventScheduler.executeAnalysis(jobId, Set.of(repoInfo), cloneDirectory);
    return ResponseEntity.ok(Map.of("message", "Analysis started for " + repoInfo.repoName()));
  }

  @PostMapping("/analyze/xml")
  public ResponseEntity<Map<String, String>> analyzeXml(
      @RequestParam(value = "file", required = false) MultipartFile file,
      @RequestParam(value = "xml", required = false) String xmlContent,
      @RequestParam(value = "cloneDirectory", required = false) String cloneDirectory) {
    try {
      Set<RepositoryInfo> repositories;
      if (file != null && !file.isEmpty()) {
        repositories = XmlConfigParser.getRepositoriesInfo(file.getInputStream());
      } else if (xmlContent != null && !xmlContent.isBlank()) {
        repositories = XmlConfigParser.getRepositoriesInfo(
            new java.io.ByteArrayInputStream(xmlContent.getBytes()));
      } else {
        return ResponseEntity.badRequest().body(Map.of("error", "XML file or content is required"));
      }
      if (repositories.isEmpty()) {
        return ResponseEntity.badRequest().body(Map.of("error", "No repositories found in XML"));
      }
      String resolvedDir = resolveCloneDirectory(cloneDirectory);
      int jobId = cloningEventScheduler.createJob();
      repositories.forEach(repo -> initializeAnalysisEntity(repo, jobId));
      cloningEventScheduler.executeAnalysis(jobId, repositories, resolvedDir);
      return ResponseEntity.ok(Map.of("message", "Analysis started for " + repositories.size() + " repositories"));
    } catch (Exception e) {
      logger.error("Failed to parse XML", e);
      return ResponseEntity.badRequest().body(Map.of("error", "Failed to parse XML: " + e.getMessage()));
    }
  }

  @GetMapping(value = "/analyze/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream() {
    return sseProgressService.addEmitter();
  }

  @GetMapping("/results")
  public List<RepositoryAnalysisDto> getAllResults() {
    return analysisRepository.findAll().stream()
        .map(this::toDto)
        .toList();
  }

  @GetMapping("/results/{repoName}")
  public ResponseEntity<RepositoryAnalysisDto> getResult(@PathVariable String repoName) {
    return analysisRepository.findAll().stream()
        .filter(e -> repoName.equals(e.getRepositoryName()))
        .findFirst()
        .map(e -> ResponseEntity.ok(toDto(e)))
        .orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping("/results/{id}")
  public ResponseEntity<Map<String, String>> deleteRepository(@PathVariable Long id) {
    return analysisRepository.findById(id)
        .map(entity -> {
          String repoName = entity.getRepositoryName();
          Integer jobId = entity.getJobId();
          deleteClonedRepo(repoName);
          analysisRepository.delete(entity);
          cleanupPipelineData(jobId);
          return ResponseEntity.ok(Map.of("message", "Repository '" + repoName + "' removed"));
        })
        .orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping("/results/{id}/data")
  public ResponseEntity<Map<String, String>> clearAnalysisData(@PathVariable Long id) {
    return analysisRepository.findById(id)
        .map(entity -> {
          Integer jobId = entity.getJobId();
          entity.setJobId(null);
          entity.setProjectKey(null);
          entity.setNcloc(null);
          entity.setBugs(null);
          entity.setVulnerabilities(null);
          entity.setCodeSmells(null);
          entity.setSqaleIndex(null);
          entity.setCoverage(null);
          entity.setDuplicatedLinesDensity(null);
          entity.setReliabilityRating(null);
          entity.setSecurityRating(null);
          entity.setSqaleRating(null);
          entity.setAlertStatus(null);
          entity.setAnalysisStatus(null);
          entity.setCurrentStep(null);
          entity.setErrorMessage(null);
          entity.setAnalyzedAt(null);
          analysisRepository.save(entity);
          cleanupPipelineData(jobId);
          return ResponseEntity.ok(Map.of("message", "Analysis data cleared for '" + entity.getRepositoryName() + "'"));
        })
        .orElse(ResponseEntity.notFound().build());
  }

  private void deleteClonedRepo(String repoName) {
    String cloneDir = prefs.get(PREF_KEY_CLONE_DIR, defaultCloneDirectory);
    Path repoPath = Paths.get(cloneDir, repoName);
    if (Files.exists(repoPath)) {
      try {
        org.apache.commons.io.FileUtils.deleteDirectory(repoPath.toFile());
        logger.info("Deleted cloned repository: {}", repoPath);
      } catch (IOException e) {
        logger.error("Failed to delete cloned repository: {}", repoPath, e);
      }
    }
  }

  private void initializeAnalysisEntity(RepositoryInfo repoInfo, int jobId) {
    RepositoryAnalysisEntity entity = analysisRepository.findByRepositoryUrl(repoInfo.repoUrl())
        .orElse(new RepositoryAnalysisEntity());
    Integer previousJobId = entity.getJobId();
    entity.setRepositoryUrl(repoInfo.repoUrl());
    entity.setRepositoryName(repoInfo.repoName());
    entity.setJobId(jobId);
    entity.setAnalysisStatus(STATUS_IN_PROGRESS);
    entity.setCurrentStep(STEP_CLONING);
    entity.setErrorMessage(null);
    entity.setAnalyzedAt(Timestamp.from(Instant.now()));
    entity.setProjectKey(null);
    entity.setNcloc(null);
    entity.setBugs(null);
    entity.setVulnerabilities(null);
    entity.setCodeSmells(null);
    entity.setSqaleIndex(null);
    entity.setCoverage(null);
    entity.setDuplicatedLinesDensity(null);
    entity.setReliabilityRating(null);
    entity.setSecurityRating(null);
    entity.setSqaleRating(null);
    entity.setAlertStatus(null);
    analysisRepository.save(entity);
    cleanupPipelineData(previousJobId);
  }

  private void cleanupPipelineData(Integer jobId) {
    if (jobId == null) return;
    if (analysisRepository.countByJobId(jobId) == 0) {
      eventRepository.deleteByJobId(jobId);
      jobRepository.deleteById(jobId);
    }
  }

  private RepositoryAnalysisDto toDto(RepositoryAnalysisEntity e) {
    return new RepositoryAnalysisDto(
        e.getId(), e.getRepositoryUrl(), e.getRepositoryName(), e.getProjectKey(),
        e.getNcloc(), e.getBugs(), e.getVulnerabilities(), e.getCodeSmells(),
        e.getSqaleIndex(), e.getCoverage(), e.getDuplicatedLinesDensity(),
        e.getReliabilityRating(), e.getSecurityRating(), e.getSqaleRating(),
        e.getAlertStatus(), e.getAnalysisStatus(), e.getCurrentStep(),
        e.getErrorMessage(), e.getAnalyzedAt()
    );
  }

}
