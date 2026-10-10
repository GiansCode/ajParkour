package us.ajg0702.parkour.game;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.jspecify.annotations.Nullable;
import us.ajg0702.parkour.Main;
import us.ajg0702.parkour.Messages;
import us.ajg0702.parkour.Portal;
import us.ajg0702.parkour.api.events.PrePlayerStartParkourEvent;
import us.ajg0702.parkour.utils.Tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * A class for managing all players in the parkour.
 * To get the instance, use Manager.getInstance()
 * @author ajgeiss0702
 *
 */
public class Manager implements Listener {

	static Manager instance = null;

	private final Map<UUID, PkPlayer> plys = new ConcurrentHashMap<>();
	private final Map<String, Integer> areaCounts = new ConcurrentHashMap<>();

	final Main main;
	final Messages msgs;

	public volatile boolean pluginDisabling = false;

	public Manager(Main pl) {
		instance = this;
		main = pl;
		msgs = main.msgs;
	}

	public void start() {
		Tasks.globalTimer(this::drawDebugOutlines, 10, 20);
	}

	/**
	 * Gets the manager instance
	 * @return the instance of the manager
	 */
	public static Manager getInstance() {
		return instance;
	}

	private void drawDebugOutlines() {
		if(!main.settings().debug()) return;
		List<PkArea> areas = getAreas();
		if(areas.isEmpty()) return;
		for(Player p : Bukkit.getOnlinePlayers()) {
			Tasks.entity(p, () -> {
				for(PkArea a : areas) {
					if(a.getWorld() == p.getWorld()) a.draw(p);
				}
			});
		}
	}

	/**
	 * Gets all (loaded) arenas
	 * @return A list of PkAreas
	 */
	public List<PkArea> getAreas() {
		return main.areaStorage.getAreas();
	}

	/**
	 * Reloads all areas, portals, etc
	 */
	public void reloadPositions() {
		main.areaStorage.reload();
	}

	/**
	 * Get an area by name.
	 * @param name A String with the name of the area.
	 * @return The found {@link us.ajg0702.parkour.game.PkArea PkArea}. Null if none is found.
	 */
	public @Nullable PkArea getArea(String name) {
		return main.areaStorage.getArea(name);
	}

	/**
	 * Gets a PkPlayer.
	 * @param ply The {@link org.bukkit.entity.Player Player} to find.
	 * @return The found PkPlayer. Null if none is found.
	 */
	public @Nullable PkPlayer getPlayer(@Nullable Player ply) {
		return ply == null ? null : plys.get(ply.getUniqueId());
	}

	public @Nullable PkPlayer getPlayer(UUID uuid) {
		return plys.get(uuid);
	}

	/**
	 * Gets a list of all players in all areas.
	 * @return A {@link java.util.List List} of {@link us.ajg0702.parkour.game.PkPlayer PkPlayer}s.
	 */
	public List<PkPlayer> getPlayers() {
		return List.copyOf(plys.values());
	}

	/**
	 * Checks if a player is in parkour in any area.
	 * @param ply a {@link org.bukkit.entity.Player Player} to check.
	 * @return A boolean telling if the player is in parkour.
	 */
	public boolean inParkour(Player ply) {
		return plys.containsKey(ply.getUniqueId());
	}

	/**
	 * Gets all players in an area
	 * @param area A {@link us.ajg0702.parkour.game.PkArea PkArea} that the players are in.
	 * @return A {@link java.util.List List} of all {@link us.ajg0702.parkour.game.PkPlayer PkPlayer}s in the area.
	 */
	public List<PkPlayer> getPlayersInArea(PkArea area) {
		List<PkPlayer> f = new ArrayList<>();
		for(PkPlayer p : plys.values()) {
			if(p.getArea().equals(area)) f.add(p);
		}
		return f;
	}

	List<List<BlockPos>> otherJumpSnapshots(PkPlayer self) {
		if(areaCounts.getOrDefault(self.getArea().getName(), 0) <= 1) return List.of();
		List<List<BlockPos>> out = new ArrayList<>();
		for(PkPlayer p : plys.values()) {
			if(p != self && p.getArea().equals(self.getArea())) out.add(p.jumpSnapshot());
		}
		return out;
	}

	/**
	 * Gets the total number of players
	 * @return The number of players in parkour in all areas.
	 */
	public int getTotalPlayers() {
		return plys.size();
	}

	/**
	 * Kick a player from parkour.
	 * @param ply The {@link org.bukkit.entity.Player Player} to kick.
	 * @return A boolean indicating if the player was found and kicked.
	 */
	public boolean kickPlayer(Player ply) {
		PkPlayer f = getPlayer(ply);
		if(f == null) return false;
		f.end();
		return true;
	}

	/**
	 * Start parkour for a player
	 * @param ply The {@link org.bukkit.entity.Player Player} to start parkour on.
	 */
	public void startGame(Player ply) {
		startGame(ply, null);
	}

	/**
	 * Start parkour for a player in a certain area. Safe to call from any thread.
	 * @param ply The {@link org.bukkit.entity.Player Player} to start parkour on.
	 * @param area The {@link us.ajg0702.parkour.game.PkArea PkArea} to start the parkour in.
	 */
	public void startGame(Player ply, @Nullable PkArea area) {
		Tasks.entity(ply, () -> startNow(ply, area));
	}

	private void startNow(Player ply, @Nullable PkArea area) {
		if(pluginDisabling || !ply.isOnline()) return;
		List<PkArea> areas = getAreas();
		if(areas.isEmpty()) return;
		if(plys.containsKey(ply.getUniqueId())) return;

		PkArea s = area != null ? area : selectArea(areas);
		if(getPlayerCounts(s) >= s.getMax()) {
			ply.sendMessage(msgs.get("areafull", ply));
			return;
		}
		if(ply.getFoodLevel() <= 6) {
			ply.setFoodLevel(7);
		}

		PrePlayerStartParkourEvent preevent = new PrePlayerStartParkourEvent(ply);
		Bukkit.getPluginManager().callEvent(preevent);
		if(preevent.isCancelled()) return;

		PkPlayer p = new PkPlayer(ply, this, s);
		if(plys.putIfAbsent(ply.getUniqueId(), p) != null) return;
		areaCounts.merge(s.getName(), 1, Integer::sum);
		p.begin();
	}

	private PkArea selectArea(List<PkArea> areas) {
		if(!main.settings().areaSelectionLowest() || areas.size() == 1) {
			return areas.getFirst();
		}
		int lowest = Integer.MAX_VALUE;
		List<PkArea> best = new ArrayList<>();
		for(PkArea a : areas) {
			int c = areaCounts.getOrDefault(a.getName(), 0);
			if(c < lowest) {
				lowest = c;
				best.clear();
			}
			if(c == lowest) best.add(a);
		}
		return best.get(ThreadLocalRandom.current().nextInt(best.size()));
	}

	/**
	 * Removes players that are no longer in parkour.
	 */
	public void checkActive() {
		plys.values().removeIf(p -> !p.isActive());
	}

	void remove(PkPlayer p) {
		if(!plys.remove(p.getPlayer().getUniqueId(), p)) return;
		areaCounts.computeIfPresent(p.getArea().getName(), (_, v) -> v <= 1 ? null : v - 1);
	}

	/**
	 * Get the number of people in an area
	 * @param a The {@link us.ajg0702.parkour.game.PkArea PkArea} to check.
	 * @return The number of people in that area.
	 */
	public int getPlayerCounts(PkArea a) {
		return areaCounts.getOrDefault(a.getName(), 0);
	}

	/**
	 * Function called when plugin is getting disabled. It kicks all players from the parkour.
	 */
	public void disable() {
		if(plys.isEmpty()) return;
		main.getLogger().info("Removing all active players from parkour because the plugin is disabling.");
		pluginDisabling = true;
		for(PkPlayer p : List.copyOf(plys.values())) {
			try {
				p.end();
			} catch(Exception e) {
				main.getLogger().log(Level.WARNING, "Unable to cleanly remove " + p.getPlayer().getName() + " from parkour"
						+ " (their inventory will be restored when they rejoin):", e);
			}
		}
	}

	// ------------------------------------------------------------------ listeners

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerMove(PlayerMoveEvent e) {
		PkPlayer p = plys.get(e.getPlayer().getUniqueId());
		if(p != null) {
			p.onMove(e);
			return;
		}
		if(!e.hasChangedBlock() || !main.settings().enablePortals() || !main.areaStorage.hasPortals()) return;
		Portal portal = main.areaStorage.portalAt(e.getTo());
		if(portal != null) {
			startGame(e.getPlayer(), portal.getArea());
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerTeleport(PlayerTeleportEvent e) {
		PkPlayer p = plys.get(e.getPlayer().getUniqueId());
		if(p == null || !p.isPlaying() || p.teleporting) return;
		Location from = e.getFrom();
		Location to = e.getTo();
		if(from.getWorld() != to.getWorld() || from.distanceSquared(to) > 100) {
			p.end(msgs.get("fall.force.reasons.teleport"));
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPreLogin(AsyncPlayerPreLoginEvent e) {
		if(e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
		main.scores.loadBlocking(e.getUniqueId(), e.getName());
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		Player p = e.getPlayer();
		if(main.scores.getCached(p.getUniqueId()) == null) {
			main.scores.load(p.getUniqueId(), p.getName());
		}
		main.inventories.recover(p);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerLeave(PlayerQuitEvent e) {
		Player p = e.getPlayer();
		kickPlayer(p);
		main.scores.unload(p.getUniqueId());
	}

	@EventHandler(ignoreCancelled = true)
	public void onDrop(PlayerDropItemEvent e) {
		if(inParkour(e.getPlayer())) e.setCancelled(true);
	}

	@EventHandler(ignoreCancelled = true)
	public void onInvClick(InventoryClickEvent e) {
		if(e.getWhoClicked() instanceof Player p && inParkour(p)) e.setCancelled(true);
	}

	@EventHandler
	public void onInteract(PlayerInteractEvent e) {
		Player p = e.getPlayer();
		if(!inParkour(p)) return;
		if(e.getMaterial() == Material.CHEST && main.settings().parkourInventory()) {
			main.selector.openSelector(p);
			e.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onBlockPlace(BlockPlaceEvent e) {
		Player p = e.getPlayer();
		if(inParkour(p)) {
			e.setCancelled(true);
			p.sendMessage(msgs.get("block.place", p));
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onEmptyBucket(PlayerBucketEmptyEvent e) {
		Player p = e.getPlayer();
		if(inParkour(p)) {
			e.setCancelled(true);
			p.sendMessage(msgs.get("block.place", p));
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent e) {
		Player p = e.getPlayer();
		if(inParkour(p)) {
			e.setCancelled(true);
			p.sendMessage(msgs.get("block.break", p));
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onHungerDeplete(FoodLevelChangeEvent e) {
		if(!(e.getEntity() instanceof Player p) || !inParkour(p)) return;
		if(e.getFoodLevel() <= 6) {
			e.setCancelled(true);
			p.setFoodLevel(7);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onPotion(EntityPotionEffectEvent e) {
		if(!(e.getEntity() instanceof Player p) || !inParkour(p)) return;
		PotionEffect effect = e.getNewEffect();
		if(effect != null && PkPlayer.isDisallowedPotion(effect.getType())) {
			e.setCancelled(true);
		}
	}
}
