package us.ajg0702.parkour.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import us.ajg0702.parkour.Main;

public class RemoveAreaCommand extends SubCommand {

	public RemoveAreaCommand(Main plugin) {
		super(plugin);
	}

	@Override
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("removearea")
				.requires(perm("ajparkour.setup"))
				.executes(ctx -> {
					msgs().send(sender(ctx), player(ctx), "areas.remove.help");
					return OK;
				})
				.then(Commands.argument("area", StringArgumentType.string())
						.suggests(configuredAreas())
						.executes(ctx -> {
							Player sply = player(ctx);
							String name = StringArgumentType.getString(ctx, "area");
							if(!plugin.areaStorage.hasArea(name)) {
								msgs().send(sender(ctx), sply, "areas.remove.cannot-find");
								return OK;
							}
							plugin.areaStorage.removeArea(name);
							msgs().send(sender(ctx), sply, "areas.remove.success", tag("name", name));
							return OK;
						}));
	}
}
