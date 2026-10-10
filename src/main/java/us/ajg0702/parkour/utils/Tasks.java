package us.ajg0702.parkour.utils;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * Scheduling helpers that work on both Paper and Folia.
 * <p>
 * Every method runs the task inline when the caller already owns the relevant region,
 * and when the plugin is disabled (schedulers reject tasks during shutdown).
 */
public final class Tasks {
	private static Plugin plugin;

	private Tasks() {}

	public static void init(Plugin p) {
		plugin = p;
	}

	private static boolean inline() {
		return !plugin.isEnabled();
	}

	public static void async(Runnable task) {
		if(inline()) {
			task.run();
			return;
		}
		Bukkit.getAsyncScheduler().runNow(plugin, _ -> task.run());
	}

	public static ScheduledTask asyncTimer(Runnable task, long delay, long period, TimeUnit unit) {
		return Bukkit.getAsyncScheduler().runAtFixedRate(plugin, _ -> task.run(), delay, period, unit);
	}

	public static void global(Runnable task) {
		if(inline() || Bukkit.isGlobalTickThread()) {
			task.run();
			return;
		}
		Bukkit.getGlobalRegionScheduler().execute(plugin, task);
	}

	public static ScheduledTask globalLater(Runnable task, long delayTicks) {
		return Bukkit.getGlobalRegionScheduler().runDelayed(plugin, _ -> task.run(), Math.max(1, delayTicks));
	}

	public static ScheduledTask globalTimer(Runnable task, long delayTicks, long periodTicks) {
		return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, _ -> task.run(), Math.max(1, delayTicks), periodTicks);
	}

	public static void entity(Entity entity, Runnable task) {
		entity(entity, task, null);
	}

	/**
	 * Runs the task on the thread that owns the entity.
	 * @param retired run instead of the task if the entity was removed before the task could run
	 */
	public static void entity(Entity entity, Runnable task, @Nullable Runnable retired) {
		if(inline() || Bukkit.isOwnedByCurrentRegion(entity)) {
			task.run();
			return;
		}
		if(!entity.getScheduler().execute(plugin, task, retired, 1) && retired != null) {
			retired.run();
		}
	}

	public static @Nullable ScheduledTask entityLater(Entity entity, Runnable task, long delayTicks) {
		return entity.getScheduler().runDelayed(plugin, _ -> task.run(), null, Math.max(1, delayTicks));
	}

	public static @Nullable ScheduledTask entityTimer(Entity entity, Runnable task, long delayTicks, long periodTicks) {
		return entity.getScheduler().runAtFixedRate(plugin, _ -> task.run(), null, Math.max(1, delayTicks), periodTicks);
	}

	/**
	 * Runs the task on the thread that owns the block at the given block coordinates.
	 */
	public static void region(World world, int blockX, int blockZ, Runnable task) {
		int cx = blockX >> 4;
		int cz = blockZ >> 4;
		if(inline() || Bukkit.isOwnedByCurrentRegion(world, cx, cz)) {
			task.run();
			return;
		}
		Bukkit.getRegionScheduler().execute(plugin, world, cx, cz, task);
	}

	public static void cancel(@Nullable ScheduledTask task) {
		if(task != null) task.cancel();
	}
}
