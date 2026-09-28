package it.anticheat.core.storage;

import it.anticheat.core.model.Report;
import it.anticheat.core.model.Violation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Storage in memoria (default). Gli adapter possono sostituirlo con SQLite/MySQL. */
public class MemoryStorage implements Storage {
    private final ConcurrentLinkedDeque<Violation> violations = new ConcurrentLinkedDeque<>();
    private final Map<Long, Report> reports = new ConcurrentHashMap<>();

    @Override
    public void saveViolation(Violation v) {
        violations.addFirst(v);
        while (violations.size() > 2000) violations.pollLast();
    }

    @Override
    public void saveReport(Report r) {
        reports.put(r.id, r);
    }

    @Override
    public List<Violation> recentViolations(UUID player, int limit) {
        List<Violation> out = new ArrayList<>();
        for (Violation v : violations) {
            if (v.player.equals(player)) {
                out.add(v);
                if (out.size() >= limit) break;
            }
        }
        return out;
    }

    @Override
    public List<Violation> recentViolations(int limit) {
        List<Violation> out = new ArrayList<>();
        int i = 0;
        for (Violation v : violations) {
            out.add(v);
            if (++i >= limit) break;
        }
        return out;
    }

    @Override
    public List<Report> openReports() {
        List<Report> out = new ArrayList<>();
        for (Report r : reports.values()) if (r.open) out.add(r);
        out.sort((a, b) -> Long.compare(b.time, a.time));
        return out;
    }

    @Override
    public void closeReport(long id) {
        Report r = reports.get(id);
        if (r != null) r.open = false;
    }
}
