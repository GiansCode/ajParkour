package us.ajg0702.parkour.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import us.ajg0702.parkour.Main;
import us.ajg0702.parkour.utils.Tasks;

/**
 * Console only: removes all of a player's scores.
 */
public class ResetCommand extends SubCommand {

	public ResetCommand(Main plugin) {
		super(plugin);
	}

	@Override
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("reset")
				.requires(src -> !(src.getSender() instanceof Player))
				.executes(ctx -> {
					msgs().send(sender(ctx), "reset.usage", tag("cmd", label(ctx)));
					return OK;
				})
				.then(Commands.argument("player", StringArgumentType.word())
						.suggests(players())
						.executes(ctx -> {
							CommandSender sender = sender(ctx);
							String name = StringArgumentType.getString(ctx, "player");
							Tasks.async(() -> {
								OfflinePlayer p = Bukkit.getOfflinePlayer(name);
								plugin.scores.resetPlayer(p.getUniqueId());
								String shown = p.getName() == null ? p.getUniqueId().toString() : p.getName();
								var message = msgs().get("reset.success", tag("player", shown));
								Tasks.global(() -> sender.sendMessage(message));
							});
							return OK;
						}));
	}
}
