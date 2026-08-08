package com.smsweb.sms.services.admin;

import com.smsweb.sms.models.admin.SystemConfig;
import com.smsweb.sms.repositories.admin.SystemConfigRepository;
import com.smsweb.sms.services.globalaccess.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * NEW service (feature: Database Backup). Runs mysqldump against whatever database
 * spring.datasource.* is actually pointed at (parsed straight from that connection
 * string at call time, never hardcoded), zips the result, and emails it — either on
 * a schedule (see DbBackupScheduler / DynamicSchedulingConfig) or on demand from the
 * "Backup Now" button (DbBackupController).
 *
 * Schedule and email settings are admin-configurable, stored in the existing
 * system_config table (same table/pattern as BIRTHDAY_NOTIFICATION_CRON):
 *   DB_BACKUP_SCHEDULE     - cron expression
 *   EMAIL_DB_BACKUP        - recipient AND sender account (backup is emailed to itself)
 *   EMAIL_DB_BKP_PASSWORD  - app password/passkey for that account
 *
 * Both the .sql and its .zip are kept permanently in app.backup.path (no automatic
 * pruning) — cleanup is manual, via the delete button on the Database Backup screen.
 */
@Service
public class DbBackupService {

    private static final Logger log = LoggerFactory.getLogger(DbBackupService.class);

    public static final String CONFIG_CRON = "DB_BACKUP_SCHEDULE";
    public static final String CONFIG_EMAIL_TO = "EMAIL_DB_BACKUP";
    public static final String CONFIG_EMAIL_PASSWORD = "EMAIL_DB_BKP_PASSWORD";

    /** Used only if no row exists yet in system_config for CONFIG_CRON. */
    public static final String DEFAULT_CRON = "0 0 2 ? * SUN";

    // Most SMTP providers (Gmail included) reject attachments above ~25MB — stay
    // comfortably under that. A zip larger than this is kept on disk but a plain
    // notification email is sent instead of failing outright with the attachment.
    private static final long MAX_ATTACHMENT_BYTES = 20L * 1024 * 1024;

    private static final Pattern JDBC_URL_PATTERN = Pattern.compile("jdbc:mysql://([^:/]+):(\\d+)/([^?;]+)");

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    @Value("${app.backup.path}")
    private String backupPath;

    @Value("${app.backup.mysqldumpPath}")
    private String mysqldumpPath;

    private final SystemConfigRepository systemConfigRepository;
    private final EmailService emailService;

    public DbBackupService(SystemConfigRepository systemConfigRepository, EmailService emailService) {
        this.systemConfigRepository = systemConfigRepository;
        this.emailService = emailService;
    }

    /**
     * Runs one backup end-to-end: mysqldump -> zip -> email (if configured). Safe to
     * call from both the scheduler and the manual "Backup Now" endpoint — same code
     * path either way, so there's exactly one place this logic can go wrong.
     */
    public Map<String, Object> runBackupNow() {
        log.info("Inside runBackupNow");
        Map<String, Object> result = new LinkedHashMap<>();
        File optionsFile = null;
        try {
            File backupDir = new File(backupPath);
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                result.put("error", "Could not create backup folder at " + backupPath + ". Check the mounted volume and permissions.");
                return result;
            }

            String[] conn = parseDatasourceUrl(datasourceUrl);
            String host = conn[0], port = conn[1], dbName = conn[2];

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String baseName = "db_backup_" + timestamp;
            File sqlFile = new File(backupDir, baseName + ".sql");
            File zipFile = new File(backupDir, baseName + ".zip");

            optionsFile = createMysqlOptionsFile(host, port, datasourceUsername, datasourcePassword);

            List<String> command = List.of(
                    mysqldumpPath,
                    "--defaults-extra-file=" + optionsFile.getAbsolutePath(),
                    "--single-transaction", "--routines", "--events", "--set-gtid-purged=OFF",
                    dbName
            );
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectOutput(sqlFile);
            File errLog = File.createTempFile("mysqldump-err-", ".log");
            pb.redirectError(errLog);

            Process process = pb.start();
            boolean finished = process.waitFor(15, TimeUnit.MINUTES);

            if (!finished) {
                process.destroyForcibly();
                cleanupQuietly(sqlFile);
                cleanupQuietly(errLog);
                result.put("error", "Backup timed out after 15 minutes.");
                return result;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0 || !sqlFile.exists() || sqlFile.length() == 0) {
                String errOutput = readFileQuietly(errLog);
                log.error("mysqldump failed (exit={}): {}", exitCode, errOutput);
                cleanupQuietly(sqlFile);
                result.put("error", "Backup failed. " + (errOutput.isBlank() ? "Check server logs for details." : errOutput.trim()));
                return result;
            }
            errLog.delete();

            zipFile(sqlFile, zipFile);

            String emailTo = configValue(CONFIG_EMAIL_TO);
            String emailPassword = configValue(CONFIG_EMAIL_PASSWORD);
            boolean emailed = false;
            String note;

            if (emailTo == null || emailTo.isBlank() || emailPassword == null || emailPassword.isBlank()) {
                note = "Backup created, but no backup email/passkey is configured yet, so nothing was sent.";
            } else {
                try {
                    if (zipFile.length() <= MAX_ATTACHMENT_BYTES) {
                        emailService.sendBackupEmail(emailTo, emailPassword, emailTo,
                                "Database Backup - " + displayTimestamp(timestamp),
                                "Attached is the database backup created on " + displayTimestamp(timestamp) + ".",
                                zipFile);
                        emailed = true;
                        note = "Backup created and emailed to " + emailTo + ".";
                    } else {
                        emailService.sendBackupEmail(emailTo, emailPassword, emailTo,
                                "Database Backup - " + displayTimestamp(timestamp) + " (too large to attach)",
                                "A database backup was created on " + displayTimestamp(timestamp)
                                        + " but is too large to email (" + formatSize(zipFile.length())
                                        + "). It has been kept on the server — you can download/manage it from the Database Backup admin screen.",
                                null);
                        note = "Backup created (" + formatSize(zipFile.length()) + "). Too large to email, so only a notification was sent to " + emailTo + ".";
                    }
                } catch (Exception mailEx) {
                    log.error("Failed to send backup email", mailEx);
                    note = "Backup created, but sending the email failed: " + mailEx.getMessage();
                }
            }

            result.put("success", true);
            result.put("filename", baseName);
            result.put("size", formatSize(zipFile.length() + sqlFile.length()));
            result.put("emailed", emailed);
            result.put("message", note);
        } catch (Exception e) {
            log.error("Database backup failed", e);
            result.put("error", "Backup failed: " + e.getLocalizedMessage());
        } finally {
            if (optionsFile != null) cleanupQuietly(optionsFile);
        }
        return result;
    }

    /**
     * Lists existing backups from app.backup.path, grouped by shared base filename
     * (the .sql and .zip created together share one name minus extension) so the
     * admin screen shows one row per backup, not two.
     */
    public List<Map<String, Object>> listBackups() {
        log.info("Inside listBackups");
        List<Map<String, Object>> list = new ArrayList<>();
        File backupDir = new File(backupPath);
        if (!backupDir.exists() || !backupDir.isDirectory()) return list;

        File[] files = backupDir.listFiles(f -> f.isFile() && (f.getName().endsWith(".sql") || f.getName().endsWith(".zip")));
        if (files == null) return list;

        Map<String, long[]> grouped = new LinkedHashMap<>(); // baseName -> [totalSizeBytes, lastModifiedMillis]
        for (File f : files) {
            String base = stripExtension(f.getName());
            long[] agg = grouped.getOrDefault(base, new long[]{0, 0});
            agg[0] += f.length();
            agg[1] = Math.max(agg[1], f.lastModified());
            grouped.put(base, agg);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MMM/yyyy HH:mm", Locale.ENGLISH);
        for (Map.Entry<String, long[]> e : grouped.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("filename", e.getKey());
            row.put("size", formatSize(e.getValue()[0]));
            row.put("createdOn", sdf.format(new Date(e.getValue()[1])));
            row.put("createdOnMillis", e.getValue()[1]);
            list.add(row);
        }
        list.sort((a, b) -> Long.compare((long) b.get("createdOnMillis"), (long) a.get("createdOnMillis")));
        return list;
    }

    /**
     * Deletes both the .sql and .zip for one backup. baseFilename comes straight from
     * the admin screen's delete button — validated against path traversal the same
     * way DownloadDocsController validates its filenames.
     */
    public boolean deleteBackup(String baseFilename) {
        log.info("Inside deleteBackup - baseFilename={}", baseFilename);
        if (baseFilename == null || baseFilename.isBlank()
                || baseFilename.contains("..") || baseFilename.contains("/") || baseFilename.contains("\\")) {
            return false;
        }
        File backupDir = new File(backupPath);
        File sql = new File(backupDir, baseFilename + ".sql");
        File zip = new File(backupDir, baseFilename + ".zip");
        boolean deletedAny = false;
        if (sql.exists()) deletedAny |= sql.delete();
        if (zip.exists()) deletedAny |= zip.delete();
        return deletedAny;
    }

    // ── Internals ────────────────────────────────────────────────────────────────

    private String configValue(String key) {
        return systemConfigRepository.findByConfigName(key).map(SystemConfig::getConfigValue).orElse(null);
    }

    private String[] parseDatasourceUrl(String url) {
        Matcher matcher = JDBC_URL_PATTERN.matcher(url);
        if (!matcher.find()) {
            throw new IllegalStateException("Could not parse host/port/database from spring.datasource.url");
        }
        return new String[]{matcher.group(1), matcher.group(2), matcher.group(3)};
    }

    /**
     * Writes DB credentials to a mysqldump --defaults-extra-file instead of passing
     * -p<password> on the command line — the latter is visible to any other process
     * on the same host via `ps aux` for as long as mysqldump runs. This temp file is
     * owner-read-only (where the filesystem supports POSIX permissions) and deleted
     * in a finally block regardless of how the backup turns out.
     */
    private File createMysqlOptionsFile(String host, String port, String username, String password) throws IOException {
        Path optionsPath = Files.createTempFile("mysqldump-opts-", ".cnf");
        String content = "[client]\nhost=" + host + "\nport=" + port + "\nuser=" + username + "\npassword=" + password + "\n";
        Files.writeString(optionsPath, content, StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(optionsPath, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            log.warn("Filesystem doesn't support POSIX permissions — mysqldump options file may be more readable than intended: {}", optionsPath);
        }
        return optionsPath.toFile();
    }

    private void zipFile(File source, File destZip) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(destZip);
             ZipOutputStream zos = new ZipOutputStream(fos);
             FileInputStream fis = new FileInputStream(source)) {
            zos.putNextEntry(new ZipEntry(source.getName()));
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) zos.write(buffer, 0, len);
            zos.closeEntry();
        }
    }

    private void cleanupQuietly(File f) {
        if (f != null && f.exists() && !f.delete()) {
            log.warn("Could not delete temp file: {}", f.getAbsolutePath());
        }
    }

    private String readFileQuietly(File f) {
        try {
            return Files.readString(f.toPath());
        } catch (IOException e) {
            return "";
        } finally {
            f.delete();
        }
    }

    private String displayTimestamp(String ts) {
        try {
            LocalDateTime dt = LocalDateTime.parse(ts, DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            return dt.format(DateTimeFormatter.ofPattern("dd/MMM/yyyy HH:mm:ss", Locale.ENGLISH));
        } catch (Exception e) {
            return ts;
        }
    }

    private String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        DecimalFormat df = new DecimalFormat("#.##");
        if (bytes < 1024 * 1024) return df.format(bytes / 1024.0) + " KB";
        if (bytes < 1024L * 1024 * 1024) return df.format(bytes / (1024.0 * 1024)) + " MB";
        return df.format(bytes / (1024.0 * 1024 * 1024)) + " GB";
    }
}
