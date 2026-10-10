package us.ajg0702.parkour.utils;

import fr.mrmicky.infinitejump.InfiniteJump;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Only loaded when InfiniteJump is installed.
 */
public final class InfiniteJumpHook {
	private InfiniteJumpHook() {}

	/** @return true if infinite jump was active and has been disabled */
	public static boolean disableIfActive(Player p) {
		InfiniteJump ij = JavaPlugin.getPlugin(InfiniteJump.class);
		if(!ij.getJumpManager().isActive(p)) return false;
		ij.getJumpManager().disable(p);
		return true;
	}

	public static void enable(Player p) {
		JavaPlugin.getPlugin(InfiniteJump.class).getJumpManager().enable(p);
	}
}
