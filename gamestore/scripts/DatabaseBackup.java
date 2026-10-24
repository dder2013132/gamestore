import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.postgresql.ds.PGSimpleDataSource;

/** Local backup and isolated restore verification. Never prints connection passwords. */
public class DatabaseBackup {
    private static final Properties manifest = new Properties();
    private static PGSimpleDataSource source;
    private static Path tools;
    private static Path destination;
    private static Properties urlOptions;

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        if (args.length != 2) { throw new IllegalArgumentException("PostgreSQL bin directory and a new backup directory are required"); }
        tools = Path.of(args[0]).toAbsolutePath();
        destination = Path.of(args[1]).toAbsolutePath();
        Path dump = destination.resolve("database.dump");
        if (Files.exists(dump)) { throw new IllegalStateException("Refusing to overwrite an existing database archive"); }
        Files.createDirectories(destination);
        String configured = System.getenv("GAMESTORE_DB_CONFIG");
        Path config = configured == null || configured.isBlank() ? Path.of("db.properties") : Path.of(configured);
        Properties settings = new Properties();
        try (InputStream in = Files.newInputStream(config)) { settings.load(in); }
        source = new PGSimpleDataSource();
        source.setURL(settings.getProperty("db.url"));
        source.setUser(settings.getProperty("db.user"));
        source.setPassword(settings.getProperty("db.password"));
        urlOptions = org.postgresql.Driver.parseURL(settings.getProperty("db.url"), null);
        Path settingsFolder = destination.resolve("settings");
        Files.createDirectories(settingsFolder);
        Files.copy(config, settingsFolder.resolve("db.properties"));
        manifest.setProperty("database", source.getDatabaseName());
        manifest.setProperty("host", String.join(",", source.getServerNames()));
        manifest.setProperty("port", Arrays.stream(source.getPortNumbers()).mapToObj(Integer::toString).collect(Collectors.joining(",")));
        manifest.setProperty("username", source.getUser());
        manifest.setProperty("createdAtUtc", Instant.now().toString());
        manifest.setProperty("javaVersion", System.getProperty("java.version"));

        try (Connection conn = source.getConnection()) {
            conn.setReadOnly(true);
            conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            conn.setAutoCommit(false);
            normalize(conn);
            String snapshot;
            try (Statement stmt = conn.createStatement(); ResultSet rows = stmt.executeQuery("SELECT pg_export_snapshot(), current_setting('server_version')")) {
                rows.next(); snapshot = rows.getString(1); manifest.setProperty("serverVersion", rows.getString(2));
            }
            List<String[]> tables = tables(conn);
            manifest.setProperty("tableCount", Integer.toString(tables.size()));
            for (int i = 0; i < tables.size(); i++) {
                String[] table = tables.get(i);
                String prefix = "table." + i + ".";
                manifest.setProperty(prefix + "schema", table[0]);
                manifest.setProperty(prefix + "name", table[1]);
                String[] fingerprint = fingerprint(conn, table[0], table[1]);
                manifest.setProperty(prefix + "rows", fingerprint[0]);
                manifest.setProperty(prefix + "sha256", fingerprint[1]);
            }
            System.out.println("[DB 1/3] Creating a consistent database archive...");
            List<String> command = command("pg_dump.exe", source.getDatabaseName());
            command.addAll(List.of("--format=custom", "--no-owner", "--no-privileges", "--snapshot=" + snapshot, "--file=" + dump));
            run(command);
            conn.commit();
        }
        List<String> toc = new ArrayList<>(List.of(tools.resolve("pg_restore.exe").toString(), "--list", dump.toString()));
        Files.writeString(destination.resolve("database-contents.txt"), run(toc), StandardCharsets.UTF_8);
        try (OutputStream out = Files.newOutputStream(destination.resolve("database-manifest.properties"))) {
            manifest.store(out, "Database snapshot metadata and table fingerprints; no passwords");
        }

        System.out.println("[DB 2/3] Restoring into a disposable verification database...");
        String temporary = "gamestore_restore_check_" + UUID.randomUUID().toString().replace("-", "");
        boolean created = false;
        try {
            try (Connection admin = source.getConnection(); Statement stmt = admin.createStatement()) {
                stmt.execute("CREATE DATABASE " + quote(temporary) + " TEMPLATE template0 ENCODING 'UTF8'");
                created = true;
            }
            List<String> restore = command("pg_restore.exe", temporary);
            restore.addAll(List.of("--exit-on-error", "--single-transaction", "--no-owner", "--no-privileges", dump.toString()));
            run(restore);
            PGSimpleDataSource restored = new PGSimpleDataSource();
            restored.setURL(settings.getProperty("db.url"));
            restored.setDatabaseName(temporary);
            restored.setUser(source.getUser());
            restored.setPassword(source.getPassword());
            List<String> report = new ArrayList<>();
            report.add("Verified at UTC: " + Instant.now());
            report.add("Method: pg_restore into a new disposable database, compare every user table's row count and sorted row SHA-256.");
            try (Connection conn = restored.getConnection()) {
                conn.setReadOnly(true);
                normalize(conn);
                int expected = Integer.parseInt(manifest.getProperty("tableCount"));
                if (tables(conn).size() != expected) { throw new IllegalStateException("Restored table count differs"); }
                for (int i = 0; i < expected; i++) {
                    String prefix = "table." + i + ".";
                    String schema = manifest.getProperty(prefix + "schema");
                    String table = manifest.getProperty(prefix + "name");
                    String[] actual = fingerprint(conn, schema, table);
                    if (!actual[0].equals(manifest.getProperty(prefix + "rows")) || !actual[1].equals(manifest.getProperty(prefix + "sha256"))) {
                        throw new IllegalStateException("Restored data differs: " + schema + "." + table);
                    }
                    String line = schema + "." + table + ": " + actual[0] + " rows, SHA-256 matches";
                    report.add(line);
                    System.out.println(line);
                }
            }
            Files.write(destination.resolve("database-verification.txt"), report, StandardCharsets.UTF_8);
        } finally {
            if (created) {
                if (!temporary.matches("gamestore_restore_check_[a-f0-9]{32}")) { throw new IllegalStateException("Unexpected verification database name"); }
                try (Connection admin = source.getConnection(); Statement stmt = admin.createStatement()) {
                    stmt.execute("DROP DATABASE " + quote(temporary) + " WITH (FORCE)");
                }
            }
        }
        Files.writeString(destination.resolve("database-verified.ok"), Instant.now().toString(), StandardCharsets.UTF_8);
        System.out.println("[DB 3/3] Restore verified; disposable database removed.");
    }

    private static void normalize(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SET TIME ZONE 'UTC'");
            stmt.execute("SET DateStyle = 'ISO, YMD'");
        }
    }

    private static List<String[]> tables(Connection conn) throws SQLException {
        List<String[]> result = new ArrayList<>();
        try (Statement stmt = conn.createStatement(); ResultSet rows = stmt.executeQuery(
                "SELECT schemaname, tablename FROM pg_tables WHERE schemaname NOT IN ('pg_catalog', 'information_schema') "
                + "AND schemaname NOT LIKE 'pg_toast%' AND schemaname NOT LIKE 'pg_temp_%' ORDER BY schemaname, tablename")) {
            while (rows.next()) { result.add(new String[] { rows.getString(1), rows.getString(2) }); }
        }
        return result;
    }

    private static String[] fingerprint(Connection conn, String schema, String table) throws Exception {
        List<String> data = new ArrayList<>();
        try (Statement stmt = conn.createStatement(); ResultSet rows = stmt.executeQuery(
                "SELECT row_to_json(row_value)::text FROM " + quote(schema) + "." + quote(table) + " row_value")) {
            while (rows.next()) { data.add(rows.getString(1)); }
        }
        Collections.sort(data);
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        for (String row : data) {
            byte[] bytes = row.getBytes(StandardCharsets.UTF_8);
            hash.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
            hash.update(bytes);
        }
        return new String[] { Integer.toString(data.size()), HexFormat.of().formatHex(hash.digest()) };
    }

    private static String quote(String name) { return "\"" + name.replace("\"", "\"\"") + "\""; }

    private static List<String> command(String executable, String database) {
        return new ArrayList<>(List.of(tools.resolve(executable).toString(), "--host=" + manifest.getProperty("host"),
                "--port=" + manifest.getProperty("port"), "--username=" + source.getUser(), "--no-password", "--dbname=" + database));
    }

    private static String run(List<String> command) throws Exception {
        Path log = Files.createTempFile(destination, "postgres-tool-", ".log");
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
            builder.environment().put("PGPASSWORD", source.getPassword());
            builder.environment().put("PGCONNECT_TIMEOUT", "10");
            for (String option : List.of("sslmode", "sslrootcert", "sslcert", "sslkey")) {
                String value = urlOptions.getProperty(option);
                if (value != null) { builder.environment().put("PG" + option.toUpperCase(Locale.ROOT), value); }
            }
            Process process = builder.start();
            if (!process.waitFor(180, TimeUnit.SECONDS)) {
                process.destroyForcibly().waitFor();
                throw new IOException("PostgreSQL backup tool timed out");
            }
            String output = Files.readString(log, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) { throw new IOException("PostgreSQL backup tool failed: " + output); }
            return output;
        } finally { Files.deleteIfExists(log); }
    }
}
