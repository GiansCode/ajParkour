package us.ajg0702.parkour;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jspecify.annotations.Nullable;
import us.ajg0702.parkour.game.PkArea;
import us.ajg0702.parkour.game.PkPlayer;
import us.ajg0702.parkour.storage.PlayerData;
import us.ajg0702.parkour.top.TopEntry;
import us.ajg0702.parkour.top.TopManager;

/**
 * PlaceholderAPI expansion. Every placeholder is answered from memory; it never blocks on the database.
 */
public class Placeholders extends PlaceholderExpansion {

    private final Main plugin;

    public Placeholders(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public String getIdentifier() {
        return "ajpk";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer player, String identifier) {
        identifier = identifier.replace("_nocache", "");

        if(identifier.startsWith("stats_top_")) {
            return top(player, identifier.substring("stats_top_".length()));
        }

        switch(identifier) {
            case "stats_gamesplayed" -> {
                PlayerData d = data(player);
                return Integer.toString(d == null ? 0 : d.gamesPlayed());
            }
            case "current" -> {
                if(player == null) return "0";
                PkPlayer p = plugin.man.getPlayer(player.getUniqueId());
                return p != null ? Integer.toString(p.getScore()) : plugin.msgs.plain("placeholders.current.no-data");
            }
            case "jumping" -> {
                return Integer.toString(plugin.man.getTotalPlayers());
            }
            case "stats_highscore" -> {
                PlayerData d = data(player);
                return Integer.toString(d == null ? 0 : d.highScore(null));
            }
            default -> {}
        }

        if(identifier.startsWith("jumping_")) {
            PkArea area = plugin.man.getArea(identifier.substring("jumping_".length()));
            return area == null ? "!" : Integer.toString(plugin.man.getPlayerCounts(area));
        }
        if(identifier.startsWith("stats_highscore_")) {
            PlayerData d = data(player);
            return Integer.toString(d == null ? 0 : d.highScore(identifier.substring("stats_highscore_".length())));
        }
        return null;
    }

    /** Handles {@code <name|score|time>_<position>[_<area>]}. */
    private @Nullable String top(@Nullable OfflinePlayer player, String rest) {
        String[] parts = rest.split("_", 3);
        if(parts.length < 2) return null;
        int number;
        try {
            number = Integer.parseInt(parts[1]);
        } catch(NumberFormatException e) {
            return null;
        }
        if(number < 1) return null;
        String area = parts.length == 3 ? parts[2] : null;
        TopEntry pos = TopManager.getInstance().getTop(number, area);
        String noData = plugin.msgs.plain("placeholders.stats.no-data", player);

        return switch(parts[0]) {
            case "name" -> pos.isEmpty() ? noData : pos.name();
            case "score" -> pos.isEmpty() ? noData : Integer.toString(pos.score());
            case "time" -> pos.isEmpty() || pos.time() < 0
                    ? noData
                    : plugin.msgs.plain("placeholders.stats.time-format", player,
                            Messages.tag("m", pos.time() / 60), Messages.tag("s", pos.time() % 60));
            default -> null;
        };
    }

    private @Nullable PlayerData data(@Nullable OfflinePlayer player) {
        if(player == null) return null;
        PlayerData d = plugin.scores.getCached(player.getUniqueId());
        if(d == null) {
            plugin.scores.load(player.getUniqueId(), null);
        }
        return d;
    }
}
