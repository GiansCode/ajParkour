package us.ajg0702.parkour.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import us.ajg0702.parkour.Main;
import us.ajg0702.parkour.Messages;

import java.util.ArrayList;
import java.util.List;

public class HelpCommand extends SubCommand {

	public HelpCommand(Main plugin) {
		super(plugin);
	}

	@Override
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("help").executes(this::show);
	}

	int show(CommandContext<CommandSourceStack> ctx) {
		Player p = player(ctx);
		TagResolver cmd = tag("cmd", label(ctx));
		List<Component> lines = new ArrayList<>();
		lines.add(msgs().get("commands.help.header", p, cmd));
		lines.add(msgs().get("commands.help.start", p, cmd));
		if(p == null || p.hasPermission("ajparkour.start.area")) lines.add(msgs().get("commands.help.startarea", p, cmd));
		if(p == null || p.hasPermission("ajparkour.start.others")) lines.add(msgs().get("commands.help.startothers", p, cmd));
		lines.add(msgs().get("commands.help.list", p, cmd));
		lines.add(msgs().get("commands.help.top", p, cmd));
		if(p == null || p.hasPermission("ajparkour.setup")) {
			lines.add(msgs().get("commands.help.areas", p, cmd));
			lines.add(msgs().get("commands.help.setup", p, cmd));
		}
		if(p == null || p.hasPermission("ajparkour.portals") || p.hasPermission("ajparkour.setup")) {
			lines.add(msgs().get("commands.help.portals", p, cmd));
		}
		sender(ctx).sendMessage(Messages.join(lines));
		return OK;
	}
}
