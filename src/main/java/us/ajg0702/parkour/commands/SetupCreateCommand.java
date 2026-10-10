package us.ajg0702.parkour.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import us.ajg0702.parkour.Main;

public class SetupCreateCommand extends SubCommand {

	public SetupCreateCommand(Main plugin) {
		super(plugin);
	}

	@Override
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("create")
				.executes(ctx -> {
					sender(ctx).sendMessage(msgs().get("setup.no-name", player(ctx)));
					return OK;
				})
				.then(Commands.argument("name", StringArgumentType.string())
						.executes(ctx -> {
							Player p = requirePlayer(ctx);
							if(p == null) return OK;
							String name = StringArgumentType.getString(ctx, "name");
							if(name.equalsIgnoreCase("overall")) {
								p.sendMessage(msgs().get("setup.invalid-name", p));
								return OK;
							}
							if(!plugin.setup.begin(p, name)) {
								p.sendMessage(msgs().get("setup.already-creating", p));
								return OK;
							}
							p.sendMessage(msgs().get("setup.created", p));
							return OK;
						}));
	}
}
