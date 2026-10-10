package us.ajg0702.parkour.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import us.ajg0702.parkour.Main;
import us.ajg0702.parkour.top.TopManager;

public class CacheDebugCommand extends SubCommand {

	public CacheDebugCommand(Main plugin) {
		super(plugin);
	}

	@Override
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("cachedebug")
				.requires(perm("ajparkour.debug"))
				.executes(ctx -> {
					CommandSender s = sender(ctx);
					s.sendMessage("Cached players: " + plugin.scores.getCachedPlayers().size());
					s.sendMessage("Players in parkour: " + plugin.man.getTotalPlayers());
					TopManager.getInstance().getBoardSizes().forEach((area, size) ->
							s.sendMessage("Top board '" + area + "': " + size + " entries"));
					return OK;
				});
	}
}
