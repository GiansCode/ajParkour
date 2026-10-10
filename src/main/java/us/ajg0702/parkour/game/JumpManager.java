package us.ajg0702.parkour.game;

import org.bukkit.configuration.file.YamlConfiguration;
import us.ajg0702.parkour.Main;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

public class JumpManager {
    private static JumpManager instance;

    public static JumpManager getInstance() {
        return instance;
    }

    public static JumpManager getInstance(Main pl) {
        if(instance == null) instance = new JumpManager(pl);
        return instance;
    }

    private final Main plugin;
    private final File file;
    private volatile YamlConfiguration jumpConfig;
    private volatile int startMedium, startHard, startExpert;

    private JumpManager(Main pl) {
        plugin = pl;
        file = new File(pl.getDataFolder(), "jumps.yml");
        reload();
    }

    public void reload() {
        if(!file.exists()) {
            plugin.saveResource("jumps.yml", false);
        }
        YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
        try(Reader r = new InputStreamReader(Objects.requireNonNull(plugin.getResource("jumps.yml")), StandardCharsets.UTF_8)) {
            c.setDefaults(YamlConfiguration.loadConfiguration(r));
        } catch(Exception e) {
            plugin.getLogger().warning("Unable to load default jumps.yml: " + e.getMessage());
        }
        jumpConfig = c;
        for(Difficulty d : Difficulty.values()) {
            if(d == Difficulty.BALANCED) continue;
            String dl = d.toString().toLowerCase(Locale.ROOT);
            d.setMin(getInt(dl + "-min"));
            d.setMax(getInt(dl + "-max"));
        }
        startMedium = getBalancedStart(Difficulty.MEDIUM);
        startHard = getBalancedStart(Difficulty.HARD);
        startExpert = getBalancedStart(Difficulty.EXPERT);
    }

    public int getInt(String key) {
        return jumpConfig.getInt(key);
    }

    public int getBalancedStart(Difficulty d) {
        return getInt("balanced-start-" + d.toString().toLowerCase(Locale.ROOT));
    }

    /** Resolves BALANCED into a concrete difficulty for the given score. */
    public Difficulty resolve(Difficulty d, int score) {
        if(d != Difficulty.BALANCED) return d;
        if(score >= startExpert) return Difficulty.EXPERT;
        if(score >= startHard) return Difficulty.HARD;
        if(score >= startMedium) return Difficulty.MEDIUM;
        return Difficulty.EASY;
    }
}
