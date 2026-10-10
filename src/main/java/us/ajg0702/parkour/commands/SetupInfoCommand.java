package us.ajg0702.parkour.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import us.ajg0702.parkour.Main;
import us.ajg0702.parkour.Messages;

public class SetupInfoCommand extends SubCommand {

	public SetupInfoCommand(Main plugin) {
		super(plugin);
	}

	@Override
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("info").executes(ctx -> {
			Player p = requirePlayer(ctx);
			if(p == null || !plugin.setup.requireActive(p, msgs())) return OK;
			SetupSession s = plugin.setup;
			sender(ctx).sendMessage(Messages.join(
					msgs().get("setup.info.header", p),
					status(p, "pos1", s.pos1 != null, true),
					status(p, "pos2", s.pos2 != null, true),
					status(p, "difficulty", s.diff != null, true),
					status(p, "max", s.max != null, false),
					status(p, "fallpos", s.fallpos != null, false)
			));
			return OK;
		});
	}

	private Component status(Player p, String name, boolean set, boolean required) {
		String key = set ? "setup.info.set" : (required ? "setup.info.missing-required" : "setup.info.missing-optional");
		return msgs().get(key, p, tag("name", name));
	}
}
