package us.ajg0702.parkour.top;

import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;
import us.ajg0702.parkour.Main;
import us.ajg0702.parkour.game.PkArea;
import us.ajg0702.parkour.storage.PlayerData;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Non-blocking leaderboard cache. Each area is fetched with a single query and refreshed in the background
 * while it is being requested. Readers never wait on the database.
 */
public class TopManager {
    private static final long STALE_MS = 5_000;
    private static final int MAX_POSITIONS = 500;

    private static TopManager instance;

    public static TopManager getInstance() {
        return instance;
    }

    public static TopManager getInstance(Main pl) {
        if(instance == null) {
            instance = new TopManager(pl);
        }
        return instance;
    }

    private final Main plugin;
    private final Map<String, Board> boards = new ConcurrentHashMap<>();

    private TopManager(Main pl) {
        plugin = pl;
    }

    private static final class Board {
        volatile List<TopEntry> entries = List.of();
        volatile boolean loaded;
        volatile long fetchedAt;
        volatile int size = 10;
        @Nullable CompletableFuture<List<TopEntry>> pending;
    }

    private static String key(@Nullable String area) {
        return area == null ? PlayerData.OVERALL : area;
    }

    /**
     * Gets a cached leaderboard position, triggering a background refresh when stale.
     * Returns an empty entry (name "--") if there is no data (yet).
     */
    public TopEntry getTop(int position, @Nullable String area) {
        if(position < 1 || position > MAX_POSITIONS) return TopEntry.empty(position);
        String k = key(area);
        Board b = boards.computeIfAbsent(k, _ -> new Board());
        if(position > b.size) {
            b.size = position;
            refresh(k, b, true);
        } else if(System.currentTimeMillis() - b.fetchedAt > STALE_MS) {
            refresh(k, b, false);
        }
        List<TopEntry> e = b.entries;
        return position <= e.size() ? e.get(position - 1) : TopEntry.empty(position);
    }

    /**
     * Gets the top {@code count} entries, fetching them first if the cache is cold or stale.
     */
    public CompletableFuture<List<TopEntry>> getTopAsync(@Nullable String area, int count) {
        String k = key(area);
        int n = Math.min(Math.max(count, 1), MAX_POSITIONS);
        Board b = boards.computeIfAbsent(k, _ -> new Board());
        boolean grow = n > b.size;
        if(grow) b.size = n;
        if(b.loaded && !grow && System.currentTimeMillis() - b.fetchedAt <= STALE_MS) {
            return CompletableFuture.completedFuture(head(b.entries, n));
        }
        return refresh(k, b, grow).thenApply(list -> head(list, n));
    }

    /** The cached top score for an area, or -1 if nobody has played or it is not loaded yet. */
    public int getCachedTopScore(@Nullable String area) {
        return getTop(1, area).score();
    }

    public boolean isLoaded(@Nullable String area) {
        Board b = boards.get(key(area));
        return b != null && b.loaded;
    }

    public void invalidate(@Nullable String area) {
        Board b = boards.get(key(area));
        if(b != null) b.fetchedAt = 0;
    }

    public void invalidateAll() {
        boards.values().forEach(b -> b.fetchedAt = 0);
    }

    /** Builds every known leaderboard from the in-memory score cache. */
    public void warm() {
        int n = plugin.settings().topShown();
        getTopAsync(null, n);
        for(PkArea a : plugin.man.getAreas()) {
            getTopAsync(a.getName(), n);
        }
    }

    private static List<TopEntry> head(List<TopEntry> list, int n) {
        return list.size() <= n ? list : list.subList(0, n);
    }

    private CompletableFuture<List<TopEntry>> refresh(String area, Board b, boolean force) {
        synchronized(b) {
            if(b.pending != null && !b.pending.isDone() && !force) return b.pending;
            int size = b.size;
            CompletableFuture<List<TopEntry>> f = plugin.scores.fetchTop(area, size).whenComplete((list, ex) -> {
                if(ex != null) {
                    plugin.getLogger().warning("Unable to fetch top scores for " + area + ": " + ex.getMessage());
                    b.fetchedAt = System.currentTimeMillis();
                    return;
                }
                b.entries = list;
                b.loaded = true;
                b.fetchedAt = System.currentTimeMillis();
            });
            b.pending = f;
            return f;
        }
    }

    public Map<String, Integer> getBoardSizes() {
        Map<String, Integer> out = new java.util.TreeMap<>();
        boards.forEach((k, v) -> out.put(k, v.entries.size()));
        return out;
    }

    /**
     * @deprecated use {@link us.ajg0702.parkour.Scores#getHighScore(java.util.UUID, String)}; data is cached for online players.
     */
    @Deprecated
    public int getHighScore(Player player, @Nullable String area) {
        PlayerData d = plugin.scores.getCached(player.getUniqueId());
        if(d == null) {
            plugin.scores.load(player.getUniqueId(), player.getName());
            return 0;
        }
        return d.highScore(area);
    }

    /**
     * @deprecated player data is evicted automatically.
     */
    @Deprecated
    public void clearPlayerCache(Player ply) {}
}
