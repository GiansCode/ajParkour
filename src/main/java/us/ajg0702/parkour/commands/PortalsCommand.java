package us.ajg0702.parkour.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;
import us.ajg0702.parkour.AreaStorage;
import us.ajg0702.parkour.Main;
import us.ajg0702.parkour.Messages;
import us.ajg0702.parkour.Portal;
import us.ajg0702.parkour.game.PkArea;

import java.util.ArrayList;
import java.util.List;

public class PortalsCommand extends SubCommand {

	public PortalsCommand(Main plugin) {
		super(plugin);
	}

	@Override
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("portals")
				.requires(perm("ajparkour.setup", "ajparkour.portals"))
				.executes(this::help)
				.then(Commands.literal("list").executes(this::list))
				.then(Commands.literal("create")
						.executes(ctx -> {
							msgs().send(sender(ctx), player(ctx), "portals.create.help", tag("cmd", label(ctx)));
							return OK;
						})
						.then(Commands.argument("name", StringArgumentType.string())
								.executes(ctx -> create(ctx, null))
								.then(Commands.argument("area", StringArgumentType.string())
										.suggests(areas())
										.executes(ctx -> create(ctx, StringArgumentType.getString(ctx, "area"))))))
				.then(Commands.literal("remove")
						.executes(ctx -> {
							msgs().send(sender(ctx), player(ctx), "portals.remove.help");
							return OK;
						})
						.then(Commands.argument("name", StringArgumentType.string())
								.suggests(portals())
								.executes(this::remove)));
	}

	private int help(CommandContext<CommandSourceStack> ctx) {
		Player p = player(ctx);
		var cmd = tag("cmd", label(ctx));
		sender(ctx).sendMessage(Messages.join(
				msgs().get("commands.portals.header", p, cmd),
				msgs().get("commands.portals.list", p, cmd),
				msgs().get("commands.portals.create", p, cmd),
				msgs().get("commands.portals.remove", p, cmd)
		));
		return OK;
	}

	private int list(CommandContext<CommandSourceStack> ctx) {
		Player p = player(ctx);
		List<Component> lines = new ArrayList<>();
		lines.add(msgs().get("portals.list.header", p));
		for(Portal po : plugin.areaStorage.getPortals()) {
			PkArea area = po.area();
			String coords = AreaStorage.coordsString(po.loc());
			if(area != null) {
				lines.add(msgs().get("portals.list.format.area", p,
						tag("name", po.name()), tag("coords", coords), tag("area", area.getName())));
			} else {
				lines.add(msgs().get("portals.list.format.no-area", p,
						tag("name", po.name()), tag("coords", coords)));
			}
		}
		sender(ctx).sendMessage(Messages.join(lines));
		return OK;
	}

	private int create(CommandContext<CommandSourceStack> ctx, @Nullable String areaName) {
		Player p = requirePlayer(ctx);
		if(p == null) return OK;
		String name = StringArgumentType.getString(ctx, "name");
		PkArea area = areaName == null ? null : plugin.man.getArea(areaName);
		plugin.areaStorage.save(new Portal(name, p.getLocation(), area));
		msgs().send(p, "portals.create.success", tag("name", name));
		return OK;
	}

	private int remove(CommandContext<CommandSourceStack> ctx) {
		Player p = player(ctx);
		String name = StringArgumentType.getString(ctx, "name");
		if(plugin.areaStorage.removePortal(name)) {
			msgs().send(sender(ctx), p, "portals.remove.success", tag("name", name));
		} else {
			msgs().send(sender(ctx), p, "portals.remove.cannot-find");
		}
		return OK;
	}
}
