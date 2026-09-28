package it.anticheat.core.storage;

import it.anticheat.core.model.Report;
import it.anticheat.core.model.Violation;
import java.util.List;
import java.util.UUID;

public interface Storage {
    void saveViolation(Violation v);
    void saveReport(Report r);
    List<Violation> recentViolations(UUID player, int limit);
    List<Violation> recentViolations(int limit);
    List<Report> openReports();
    void closeReport(long id);
}
