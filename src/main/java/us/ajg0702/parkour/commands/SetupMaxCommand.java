package us.ajg0702.parkour.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import us.ajg0702.parkour.Main;

public class SetupMaxCommand extends SubCommand {

	public SetupMaxCommand(Main plugin) {
		super(plugin);
	}

	@Override
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("max")
				.executes(ctx -> {
					sender(ctx).sendMessage(msgs().get("setup.max.need-args", player(ctx)));
					return OK;
				})
				.then(Commands.argument("max", IntegerArgumentType.integer(1))
						.executes(ctx -> {
							Player p = requirePlayer(ctx);
							if(p == null || !plugin.setup.requireActive(p, msgs())) return OK;
							plugin.setup.max = IntegerArgumentType.getInteger(ctx, "max");
							p.sendMessage(msgs().get("setup.set.max", p));
							return OK;
						}));
	}
}
