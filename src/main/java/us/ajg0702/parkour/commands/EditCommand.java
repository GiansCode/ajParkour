package us.ajg0702.parkour.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import us.ajg0702.parkour.Main;
import us.ajg0702.parkour.game.PkArea;

public class EditCommand extends SubCommand {

	public EditCommand(Main plugin) {
		super(plugin);
	}

	@Override
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("edit")
				.requires(perm("ajparkour.setup"))
				.executes(ctx -> {
					msgs().send(sender(ctx), player(ctx), "setup.no-name");
					return OK;
				})
				.then(Commands.argument("area", StringArgumentType.string())
						.suggests(areas())
						.executes(ctx -> {
							Player p = requirePlayer(ctx);
							if(p == null) return OK;
							PkArea a = plugin.man.getArea(StringArgumentType.getString(ctx, "area"));
							if(a == null) {
								msgs().send(p, "setup.edit.no-area");
								return OK;
							}
							if(!plugin.setup.edit(p, a)) {
								msgs().send(p, "setup.already-creating");
								return OK;
							}
							msgs().send(p, "setup.edit.loaded", tag("cmd", label(ctx)));
							return OK;
						}));
	}
}
