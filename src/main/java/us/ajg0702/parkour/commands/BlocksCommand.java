package us.ajg0702.parkour.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import us.ajg0702.parkour.Main;

public class BlocksCommand extends SubCommand {

	public BlocksCommand(Main plugin) {
		super(plugin);
	}

	@Override
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("blocks")
				.executes(ctx -> {
					Player p = requirePlayer(ctx);
					if(p == null) return OK;
					if(!p.hasPermission("ajparkour.selector")) {
						msgs().send(p, "noperm");
						return OK;
					}
					plugin.selector.openSelector(p);
					return OK;
				})
				.then(Commands.argument("player", StringArgumentType.word())
						.requires(perm("ajparkour.selector.openothers"))
						.suggests(players())
						.executes(ctx -> {
							Player sply = player(ctx);
							String name = StringArgumentType.getString(ctx, "player");
							Player target = Bukkit.getPlayerExact(name);
							if(target == null) {
								msgs().send(sender(ctx), sply, "couldnt-find-player", tag("player", name));
								return OK;
							}
							plugin.selector.openSelector(target);
							msgs().send(sender(ctx), sply, "blockselector.openedfor", tag("player", target.getName()));
							return OK;
						}));
	}
}
