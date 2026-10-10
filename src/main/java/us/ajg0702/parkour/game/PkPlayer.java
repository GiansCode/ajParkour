package us.ajg0702.parkour.game;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.jspecify.annotations.Nullable;
import us.ajg0702.parkour.Main;
import us.ajg0702.parkour.Messages;
import us.ajg0702.parkour.Rewards;
import us.ajg0702.parkour.Settings;
import us.ajg0702.parkour.api.events.PlayerEndParkourEvent;
import us.ajg0702.parkour.api.events.PlayerJumpEvent;
import us.ajg0702.parkour.api.events.PlayerStartParkourEvent;
import us.ajg0702.parkour.storage.PlayerData;
import us.ajg0702.parkour.utils.InfiniteJumpHook;
import us.ajg0702.parkour.utils.Tasks;
import us.ajg0702.parkour.utils.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A player that is doing parkour. Game state is only mutated on the thread that owns the player.
 */
public class PkPlayer {

	private enum State { STARTING, PLAYING, ENDED }

	private static final Set<PotionEffectType> DISALLOWED_POTIONS = Set.of(
			PotionEffectType.SPEED,
			PotionEffectType.JUMP_BOOST,
			PotionEffectType.LEVITATION,
			PotionEffectType.SLOW_FALLING);

	final Main plugin;
	private final Manager man;
	private final Messages msgs;
	private final Player ply;
	private final UUID uuid;
	private final PkArea area;
	private final World world;

	private final List<PkJump> jumps = new ArrayList<>();
	private volatile List<BlockPos> jumpSnapshot = List.of();

	private volatile State state = State.STARTING;
	volatile boolean teleporting = false;

	private volatile int score = 0;
	private long started;
	private volatile long lastMove;
	private volatile int prevhigh = 0;
	private int lowestY;
	private int highestY;
	private Material blockMaterial = Material.STONE;

	private @Nullable ScheduledTask tickTask;
	private ItemStack @Nullable [] savedInventory;
	private boolean infiniteJumpEnableAfter = false;

	private final List<String> cmds = Collections.synchronizedList(new ArrayList<>());

	public volatile boolean beatServerHighscore = false;

	/**
	 * Creates a parkour player. Use {@link Manager#startGame(Player, PkArea)} to actually start a game.
	 * @param p The {@link org.bukkit.entity.Player Player} that is in parkour
	 * @param m The {@link us.ajg0702.parkour.game.Manager Manager}.
	 * @param a The {@link us.ajg0702.parkour.game.PkArea PkArea} that the parkour should take place in.
	 */
	public PkPlayer(Player p, Manager m, PkArea a) {
		ply = p;
		uuid = p.getUniqueId();
		man = m;
		area = a;
		world = a.getWorld();
		plugin = m.main;
		msgs = plugin.msgs;
	}

	public static boolean isDisallowedPotion(PotionEffectType type) {
		return DISALLOWED_POTIONS.contains(type);
	}

	// ------------------------------------------------------------------ starting

	/**
	 * Starts the game. Must be called on the player's thread.
	 */
	void begin() {
		long now = System.currentTimeMillis();
		started = now;
		lastMove = now;
		Settings settings = plugin.settings();
		blockMaterial = plugin.selector.getBlockMaterial(ply, area);

		plugin.scores.load(uuid, ply.getName()).thenAccept(data -> Tasks.entity(ply, () -> {
			if(state == State.ENDED) return;
			plugin.scores.incrementGamesPlayed(uuid);
			prevhigh = data.highScore(settings.beginScorePerArea() ? area.getName() : null);
			String key = prevhigh > 0 ? "start.score" : "start.first";
			ply.sendMessage(msgs.get(key, ply, Messages.tag("score", prevhigh)));
		}));

		Location loc = ply.getLocation();
		float yaw = loc.getYaw();
		float pitch = loc.getPitch();
		BlockPos start = area.randomPosition();

		int cx = start.x() >> 4;
		int cz = start.z() >> 4;
		List<CompletableFuture<?>> chunks = new ArrayList<>(9);
		for(int dx = -1; dx <= 1; dx++) {
			for(int dz = -1; dz <= 1; dz++) {
				chunks.add(world.getChunkAtAsync(cx + dx, cz + dz));
			}
		}
		CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new)).whenComplete((_, _) ->
				Tasks.region(world, start.x(), start.z(), () -> generateStart(start, yaw, pitch)));
	}

	/** Runs on the region that owns the start position. */
	private void generateStart(BlockPos start, float yaw, float pitch) {
		if(state != State.STARTING) return;

		List<List<BlockPos>> others = man.otherJumpSnapshots(this);
		List<PkJump> list = new ArrayList<>();
		list.add(PkJump.generate(this, world, start, yaw, list, others));
		list.add(PkJump.generate(this, world, list.getLast().pos(), yaw, list, others));
		list.getFirst().place();
		for(int i = 0; i < plugin.settings().jumpsAhead(); i++) {
			PkJump j = PkJump.generate(this, world, list.getLast().pos(), yaw, list, others);
			list.add(j);
			j.place();
		}
		for(PkJump jump : list) {
			if(!jump.isPlaced()) jump.place();
		}

		BlockPos first = list.getFirst().pos();
		Location tp = new Location(world, first.x() + 0.5, first.y() + 1.5, first.z() + 0.5, yaw, pitch);
		Runnable cleanup = () -> list.forEach(PkJump::remove);

		Tasks.entity(ply, () -> {
			if(state != State.STARTING) {
				cleanup.run();
				return;
			}
			jumps.addAll(list);
			publishJumps();
			teleporting = true;
			ply.teleportAsync(tp, PlayerTeleportEvent.TeleportCause.PLUGIN).whenComplete((ok, _) ->
					Tasks.entity(ply, () -> onStartTeleport(Boolean.TRUE.equals(ok)), () -> finish(Component.empty(), false)));
		}, cleanup);
	}

	private void onStartTeleport(boolean success) {
		if(state != State.STARTING) return;
		if(!success) {
			teleporting = false;
			finish(Component.empty(), false);
			return;
		}
		state = State.PLAYING;
		lastMove = System.currentTimeMillis();
		Tasks.entityLater(ply, () -> teleporting = false, 5);

		Settings settings = plugin.settings();
		playSound(settings.startSound(), ply.getLocation());

		if(settings.parkourInventory()) {
			setupInventory(settings);
		}

		clearPots();
		tickTask = Tasks.entityTimer(ply, this::tick, 20, 20);

		Bukkit.getPluginManager().callEvent(new PlayerStartParkourEvent(this));

		if(plugin.hasInfiniteJump() && InfiniteJumpHook.disableIfActive(ply)) {
			infiniteJumpEnableAfter = true;
		}
	}

	private void setupInventory(Settings settings) {
		ItemStack[] saved = plugin.inventories.save(ply);
		if(saved == null) {
			ply.sendMessage(msgs.get("errors.inventory-save", ply));
			return;
		}
		savedInventory = saved;
		ply.getInventory().clear();
		boolean requirePerm = settings.requirePermissionForBlockSelectorItem();
		if(settings.enableBlockSelectorItem() && (!requirePerm || ply.hasPermission("ajparkour.selector"))) {
			ItemStack bsItem = new ItemStack(Material.CHEST);
			bsItem.editMeta(meta -> meta.displayName(msgs.get("items.blockselector.name", ply)));
			ply.getInventory().setItem(4, bsItem);
		}
	}

	private void clearPots() {
		for(PotionEffectType type : DISALLOWED_POTIONS) {
			if(ply.hasPotionEffect(type)) ply.removePotionEffect(type);
		}
	}

	// ------------------------------------------------------------------ gameplay

	private void tick() {
		int kick = plugin.settings().kickTime();
		if(kick > 0 && System.currentTimeMillis() - lastMove > kick * 1000L) {
			end(msgs.get("fall.force.reasons.afk", ply));
		}
	}

	void onMove(PlayerMoveEvent e) {
		if(state != State.PLAYING) return;
		lastMove = System.currentTimeMillis();
		if(!e.hasChangedPosition()) return;
		Location to = e.getTo();
		if(!checkMadeIt(to)) checkFall(to);
	}

	private void publishJumps() {
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		List<BlockPos> snap = new ArrayList<>(jumps.size());
		for(PkJump j : jumps) {
			BlockPos p = j.pos();
			snap.add(p);
			int y = p.y();
			if(y < lo) lo = y;
			if(y > hi) hi = y;
		}
		lowestY = lo;
		highestY = hi;
		jumpSnapshot = List.copyOf(snap);
	}

	/** Thread-safe snapshot of this player's jump positions. */
	public List<BlockPos> jumpSnapshot() {
		return jumpSnapshot;
	}

	private void madeIt(float yaw) {
		score++;
		jumps.removeFirst().remove();
		PkJump nj = PkJump.generate(this, world, jumps.getLast().pos(), yaw, jumps, man.otherJumpSnapshots(this));
		nj.place();
		jumps.add(nj);
		publishJumps();

		ply.sendActionBar(msgs.get("score", ply,
				Messages.tag("score", score),
				Messages.tag("highscore", Math.max(prevhigh, 0))));

		if(score == prevhigh && prevhigh > 0) {
			ply.sendMessage(msgs.get("beatrecord-ingame", ply, Messages.tag("score", prevhigh)));
		}

		Settings settings = plugin.settings();
		BlockPos p = nj.pos();
		Particle particle = settings.newBlockParticle();
		if(particle != null && settings.particleCount() > 0) {
			ply.spawnParticle(particle, p.x() + 0.5, p.y() + 0.5, p.z() + 0.5, settings.particleCount(), 0.005, 0.001, 0.005);
		}
		Sound jumpSound = settings.jumpSound();
		if(jumpSound != null) {
			ply.playSound(jumpSound, p.x(), p.y(), p.z());
		}

		plugin.rewards.checkRewards(this, score, area);

		Bukkit.getPluginManager().callEvent(new PlayerJumpEvent(this));
	}

	private void playSound(@Nullable Sound sound, Location loc) {
		if(sound != null) ply.playSound(sound, loc.getX(), loc.getY(), loc.getZ());
	}

	/**
	 * Add a command to execute after the parkour ends.
	 * @param cmd The command to execute in a string.
	 */
	public void addCommand(String cmd) {
		cmds.add(cmd);
	}

	/**
	 * Add a list of commands to execute after the parkour ends.
	 * @param cmds The commands to execute in a string.
	 */
	public void addCommands(List<String> cmds) {
		this.cmds.addAll(cmds);
	}

	/**
	 * Gets the highest (y-level) block
	 * @return The PkJump with the highest y-level
	 */
	public @Nullable PkJump getHighestBlock() {
		PkJump highest = null;
		for(PkJump j : jumps) {
			if(highest == null || j.pos().y() > highest.pos().y()) highest = j;
		}
		return highest;
	}

	/**
	 * Check if the player made the jump
	 * @return A boolean telling if they made the jump or not.
	 */
	public boolean checkMadeIt() {
		return checkMadeIt(ply.getLocation());
	}

	private boolean checkMadeIt(Location l) {
		if(state != State.PLAYING || jumps.size() < 2) return false;
		BlockPos goal = jumps.get(1).pos();
		if(Math.abs(l.getBlockX() - goal.x()) > 1 || Math.abs(l.getBlockZ() - goal.z()) > 1) return false;
		if(Math.abs(l.getX() - (goal.x() + 0.5)) < 0.8 && Math.abs(l.getZ() - (goal.z() + 0.5)) < 0.8) {
			madeIt(l.getYaw());
			return true;
		}
		return false;
	}

	/**
	 * Check if the player fell. If they did, end the parkour
	 */
	public void checkFall() {
		checkFall(ply.getLocation());
	}

	private void checkFall(Location l) {
		if(state != State.PLAYING || jumps.isEmpty()) return;
		int y = l.getBlockY();
		if(y < lowestY - 1 || ply.isFlying() || y > highestY + 3) {
			end();
		}
	}

	// ------------------------------------------------------------------ getters

	/**
	 * Get the {@link org.bukkit.entity.Player Player} this instance represents.
	 * @return The {@link org.bukkit.entity.Player Player} this instance represents.
	 */
	public Player getPlayer() {
		return ply;
	}

	/**
	 * Gets the player's current score
	 * @return The score
	 */
	public int getScore() {
		return score;
	}

	/**
	 * The material name of the blocks. This is selected when the parkour starts.
	 */
	public String getBlock() {
		return blockMaterial.name();
	}

	public Material getBlockMaterial() {
		return blockMaterial;
	}

	/**
	 * Used to get all the jumps for this player
	 * @return A {@link java.util.List list} of all {@link us.ajg0702.parkour.game.PkJump PkJump}s
	 */
	public List<PkJump> getJumps() {
		return List.copyOf(jumps);
	}

	/**
	 * Get the area that this parkour is in.
	 * @return The {@link us.ajg0702.parkour.game.PkArea PkArea} the parkour is in.
	 */
	public PkArea getArea() {
		return area;
	}

	public boolean isActive() {
		return state != State.ENDED;
	}

	public boolean isPlaying() {
		return state == State.PLAYING;
	}

	// ------------------------------------------------------------------ ending

	/**
	 * End the parkour with no reason
	 */
	public void end() {
		end(Component.empty());
	}

	/**
	 * End the parkour with a reason. Safe to call from any thread.
	 * @param reason The reason to end the parkour
	 */
	public void end(String reason) {
		end(reason == null || reason.isEmpty() ? Component.empty() : Text.parse(reason));
	}

	/**
	 * End the parkour with a reason. Safe to call from any thread.
	 * @param reason The reason to end the parkour
	 */
	public void end(Component reason) {
		Tasks.entity(ply, () -> finish(reason, true), () -> finish(reason, false));
	}

	private void finish(Component reason, boolean withPlayer) {
		if(state == State.ENDED) return;
		boolean wasPlaying = state == State.PLAYING;
		state = State.ENDED;
		man.remove(this);
		Tasks.cancel(tickTask);
		for(PkJump j : jumps) {
			j.remove();
		}
		jumps.clear();
		publishJumps();
		if(!wasPlaying) return;

		Settings settings = plugin.settings();
		PlayerData data = plugin.scores.getCached(uuid);
		int before = data == null ? 0 : data.highScore(settings.beginScorePerArea() ? area.getName() : null);
		int time = (int) ((System.currentTimeMillis() - started) / 1000);
		plugin.scores.submitBest(uuid, area.getName(), score, time);

		if(!withPlayer) return;

		if(!Component.empty().equals(reason)) {
			ply.sendMessage(msgs.get("fall.force.base", ply).append(reason));
		}
		ply.sendMessage(msgs.get("fall.normal", ply, Messages.tag("score", score)));
		if(before < score) {
			ply.sendMessage(msgs.get("beatrecord", ply, Messages.tag("score", before)));
		}

		ply.setFallDistance(Integer.MIN_VALUE);

		Location fall = area.getFallPos();
		if(fall != null) {
			teleporting = true;
			ply.teleportAsync(fall, PlayerTeleportEvent.TeleportCause.PLUGIN);
		}

		if(savedInventory != null) {
			plugin.inventories.restore(ply, savedInventory);
			savedInventory = null;
		}

		playSound(settings.endSound(), ply.getLocation());

		if(infiniteJumpEnableAfter) {
			InfiniteJumpHook.enable(ply);
		}

		if(!cmds.isEmpty()) {
			List<String> toRun;
			synchronized(cmds) {
				toRun = List.copyOf(cmds);
			}
			Rewards.staticExecuteCommands(toRun, ply);
		}

		Bukkit.getPluginManager().callEvent(new PlayerEndParkourEvent(ply, score));
	}
}
