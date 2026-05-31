package net.hytaledepot.templates.mod.permissions;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PermissionsModTemplate {
  private final Map<String, AtomicLong> actionCounters = new ConcurrentHashMap<>();
  private final Map<String, String> lastActionBySender = new ConcurrentHashMap<>();
  private final AtomicBoolean demoFlagEnabled = new AtomicBoolean(false);
  private final AtomicLong errorCount = new AtomicLong();
  Map<String, Set<String>> permissionsBySender = new ConcurrentHashMap<>();
  private volatile Path dataDirectory;

  public void onInitialize(Path dataDirectory) {
    this.dataDirectory = dataDirectory;
    permissionsBySender.clear();
  }

  public void onShutdown() {
    permissionsBySender.clear();
  }

  public void onHeartbeat(long tick) {
    actionCounters.computeIfAbsent("heartbeat", key -> new AtomicLong()).incrementAndGet();

  }

  public String runAction(String sender, String action, long heartbeatTicks) {
    String normalizedSender = String.valueOf(sender == null ? "unknown" : sender);
    String normalizedAction = normalizeAction(action);

    actionCounters.computeIfAbsent(normalizedAction, key -> new AtomicLong()).incrementAndGet();
    lastActionBySender.put(normalizedSender, normalizedAction);

    if ("toggle".equals(normalizedAction)) {
      boolean enabled = toggleFlag(demoFlagEnabled);
      return "[PermissionsMod] demoFlag=" + enabled + ", heartbeatTicks=" + heartbeatTicks;
    }

    if ("info".equals(normalizedAction)) {
      return "[PermissionsMod] " + diagnostics(normalizedSender, heartbeatTicks);
    }

    String domainResult = handleDomainAction(normalizedSender, normalizedAction, heartbeatTicks);
    if (domainResult != null) {
      return "[PermissionsMod] " + domainResult;
    }

    return "[PermissionsMod] unknown action='" + normalizedAction + "' (try: info, toggle, sample, grant-demo, revoke-demo, check-demo)";
  }

  public String diagnostics(String sender, long heartbeatTicks) {
    String directory = dataDirectory == null ? "unset" : dataDirectory.toString();
    return "sender=" + sender
        + ", heartbeatTicks=" + heartbeatTicks
        + ", demoFlag=" + demoFlagEnabled.get()
        + ", ops=" + operationCount()
        + ", lastAction=" + lastActionBySender.getOrDefault(sender, "none")
        + ", errors=" + errorCount.get()
        + ", principals=" + permissionsBySender.size() + ", grants=" + permissionsBySender.values().stream().mapToInt(Set::size).sum() + ", dataDirectory=" + directory;
  }

  public long operationCount() {
    long total = 0;
    for (AtomicLong value : actionCounters.values()) {
      total += value.get();
    }
    return total;
  }

  public void incrementErrorCount() {
    errorCount.incrementAndGet();
  }

  private String handleDomainAction(String sender, String action, long heartbeatTicks) {
    Set<String> grants = grantsFor(sender);
    if ("sample".equals(action) || "grant-demo".equals(action)) {
      grants.add("hd.example.use");
      grants.add("hd.example.manage");
      return "granted " + new TreeSet<>(grants);
    }
    if ("revoke-demo".equals(action)) {
      grants.remove("hd.example.manage");
      return "remaining grants=" + new TreeSet<>(grants);
    }
    if ("check-demo".equals(action)) {
      return "hd.example.use=" + grants.contains("hd.example.use") + ", hd.example.manage=" + grants.contains("hd.example.manage");
    }
    return null;
  }

  private Set<String> grantsFor(String sender) {
    return permissionsBySender.computeIfAbsent(String.valueOf(sender).toLowerCase(), key -> ConcurrentHashMap.newKeySet());
  }

  private static String normalizeAction(String action) {
    String normalized = String.valueOf(action == null ? "" : action).trim().toLowerCase();
    return normalized.isEmpty() ? "sample" : normalized;
  }

  private static boolean toggleFlag(AtomicBoolean flag) {
    while (true) {
      boolean current = flag.get();
      boolean next = !current;
      if (flag.compareAndSet(current, next)) {
        return next;
      }
    }
  }
}
