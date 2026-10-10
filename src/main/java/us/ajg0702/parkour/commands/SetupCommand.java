package us.ajg0702.parkour.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import us.ajg0702.parkour.Main;
import us.ajg0702.parkour.Messages;

import java.util.List;

public class SetupCommand extends SubCommand {

	public SetupCommand(Main plugin) {
		super(plugin);
	}

	@Override
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		var cmd = Commands.literal("setup")
				.requires(perm("ajparkour.setup"))
				.executes(this::help);
		for(SubCommand sub : List.of(
				new SetupCreateCommand(plugin),
				new SetupPos1Command(plugin),
				new SetupPos2Command(plugin),
				new SetupFallposCommand(plugin),
				new SetupDifficultyCommand(plugin),
				new SetupWeCommand(plugin),
				new SetupMaxCommand(plugin),
				new SetupInfoCommand(plugin),
				new SetupSaveCommand(plugin)
		)) {
			cmd.then(sub.build());
		}
		return cmd;
	}

	private int help(CommandContext<CommandSourceStack> ctx) {
		Player p = player(ctx);
		var cmd = tag("cmd", label(ctx));
		sender(ctx).sendMessage(Messages.join(
				msgs().get("commands.setup.header", p, cmd),
				msgs().get("commands.setup.edit", p, cmd),
				msgs().get("commands.setup.create", p, cmd),
				msgs().get("commands.setup.pos1", p, cmd),
				msgs().get("commands.setup.pos2", p, cmd),
				msgs().get("commands.setup.fallpos", p, cmd),
				msgs().get("commands.setup.we", p, cmd),
				msgs().get("commands.setup.diff", p, cmd),
				msgs().get("commands.setup.max", p, cmd),
				msgs().get("commands.setup.info", p, cmd),
				msgs().get("commands.setup.save", p, cmd)
		));
		return OK;
	}
}
