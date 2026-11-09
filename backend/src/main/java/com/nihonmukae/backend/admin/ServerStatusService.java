package com.nihonmukae.backend.admin;

import com.nihonmukae.backend.admin.dto.AdminDtos.Cache;
import com.nihonmukae.backend.admin.dto.AdminDtos.Counts;
import com.nihonmukae.backend.admin.dto.AdminDtos.Database;
import com.nihonmukae.backend.admin.dto.AdminDtos.Disk;
import com.nihonmukae.backend.admin.dto.AdminDtos.Jvm;
import com.nihonmukae.backend.admin.dto.AdminDtos.Memory;
import com.nihonmukae.backend.admin.dto.AdminDtos.ServerStatus;
import com.nihonmukae.backend.admin.dto.AdminDtos.Traffic;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Properties;

/** Thu thập các chỉ số cần chú ý của server cho trang "Trạng thái server" (chỉ ADMIN xem được). */
@Service
public class ServerStatusService {

    private static final Logger log = LoggerFactory.getLogger(ServerStatusService.class);

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final MeterRegistry meters;
    private final String mediaProvider;

    public ServerStatusService(JdbcTemplate jdbc,
                               StringRedisTemplate redis,
                               MeterRegistry meters,
                               @Value("${app.media.provider:local}") String mediaProvider) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.meters = meters;
        this.mediaProvider = mediaProvider;
    }

    public ServerStatus snapshot() {
        var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        double cpu = os.getCpuLoad();
        double load = os.getSystemLoadAverage();
        return new ServerStatus(ZonedDateTime.now(), memory(os), jvm(), cpu < 0 ? null : round(cpu * 100),
                load < 0 ? null : round(load), os.getAvailableProcessors(), disk(), database(), cache(), traffic(),
                counts(), mediaProvider);
    }

    /** RAM của máy. Đọc /proc/meminfo (MemAvailable) vì đó là RAM thật sự dùng được, không tính bộ nhớ đệm. */
    private Memory memory(com.sun.management.OperatingSystemMXBean os) {
        long total = os.getTotalMemorySize();
        long available = os.getFreeMemorySize();
        try {
            Path meminfo = Path.of("/proc/meminfo");
            if (Files.isReadable(meminfo)) {
                for (String line : Files.readAllLines(meminfo)) {
                    if (line.startsWith("MemTotal:")) {
                        total = kb(line);
                    } else if (line.startsWith("MemAvailable:")) {
                        available = kb(line);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Không đọc được /proc/meminfo: {}", e.getMessage());
        }
        return new Memory(total, total - available, available);
    }

    private static long kb(String meminfoLine) {
        String digits = meminfoLine.replaceAll("[^0-9]", "");
        return Long.parseLong(digits) * 1024;
    }

    private Jvm jvm() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        var heap = memory.getHeapMemoryUsage();
        return new Jvm(heap.getUsed(), heap.getMax(), ManagementFactory.getThreadMXBean().getThreadCount(),
                ManagementFactory.getRuntimeMXBean().getUptime(), System.getProperty("java.version"));
    }

    private Disk disk() {
        File root = new File("/");
        long total = root.getTotalSpace();
        long free = root.getUsableSpace();
        return new Disk(total, total - free, free);
    }

    private Database database() {
        try {
            Long size = jdbc.queryForObject("SELECT pg_database_size(current_database())", Long.class);
            Integer active = jdbc.queryForObject(
                    "SELECT count(*)::int FROM pg_stat_activity WHERE datname = current_database()", Integer.class);
            Integer max = jdbc.queryForObject("SELECT current_setting('max_connections')::int", Integer.class);
            return new Database(true, size, active, max);
        } catch (Exception e) {
            log.warn("Không đọc được trạng thái DB: {}", e.getMessage());
            return new Database(false, null, null, null);
        }
    }

    private Cache cache() {
        try {
            long start = System.nanoTime();
            redis.execute((org.springframework.data.redis.core.RedisCallback<String>) c -> c.ping());
            long pingMs = (System.nanoTime() - start) / 1_000_000;
            Properties info = redis.execute((org.springframework.data.redis.core.RedisCallback<Properties>) c -> c.serverCommands().info("memory"));
            Long used = info == null || info.getProperty("used_memory") == null ? null : Long.parseLong(info.getProperty("used_memory"));
            return new Cache(true, used, pingMs);
        } catch (Exception e) {
            log.warn("Không đọc được trạng thái Redis: {}", e.getMessage());
            return new Cache(false, null, null);
        }
    }

    /** Số request từ lúc server khởi động, lấy từ Micrometer (đã có sẵn nhờ Actuator). */
    private Traffic traffic() {
        long total = 0;
        long server = 0;
        long client = 0;
        for (Timer timer : meters.find("http.server.requests").timers()) {
            long count = timer.count();
            String status = timer.getId().getTag("status");
            total += count;
            if (status != null && status.startsWith("5")) {
                server += count;
            } else if (status != null && status.startsWith("4")) {
                client += count;
            }
        }
        return new Traffic(total, server, client);
    }

    private Counts counts() {
        try {
            Long users = jdbc.queryForObject("SELECT count(*) FROM users", Long.class);
            Long words = jdbc.queryForObject("SELECT count(*) FROM dict_entries", Long.class);
            Long questions = jdbc.queryForObject("SELECT count(*) FROM questions WHERE is_active", Long.class);
            Long sessions = jdbc.queryForObject("SELECT count(*) FROM practice_sessions", Long.class);
            Long mediaFiles = jdbc.queryForObject("SELECT count(*) FROM media_files", Long.class);
            Long mediaBytes = jdbc.queryForObject("SELECT coalesce(sum(size_bytes), 0) FROM media_files", Long.class);
            return new Counts(nz(users), nz(words), nz(questions), nz(sessions), nz(mediaFiles), nz(mediaBytes));
        } catch (Exception e) {
            return new Counts(0, 0, 0, 0, 0, 0);
        }
    }

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
