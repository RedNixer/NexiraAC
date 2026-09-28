package it.anticheat.core.model;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class Report {
    private static final AtomicLong SEQ = new AtomicLong(1);
    public final long id = SEQ.getAndIncrement();
    public final long time = System.currentTimeMillis();
    public final UUID reporter;
    public final String reporterName;
    public final UUID reported;
    public final String reportedName;
    public final String reason;
    public volatile boolean open = true;

    public Report(UUID reporter, String reporterName, UUID reported, String reportedName, String reason) {
        this.reporter = reporter;
        this.reporterName = reporterName;
        this.reported = reported;
        this.reportedName = reportedName;
        this.reason = reason;
    }
}
