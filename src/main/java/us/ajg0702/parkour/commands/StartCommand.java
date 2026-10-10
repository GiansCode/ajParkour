package us.ajg0702.parkour.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import us.ajg0702.parkour.Main;
import us.ajg0702.parkour.Messages;
import us.ajg0702.parkour.game.PkArea;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StartCommand extends SubCommand {

	public StartCommand(Main plugin) {
		super(plugin);
	}

	@Override
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("start")
				.executes(this::startSelf)
				.then(Commands.argument("target", StringArgumentType.string())
						.suggests((ctx, builder) -> {
							CommandSender s = ctx.getSource().getSender();
							List<String> options = new ArrayList<>();
							if(s.hasPermission("ajparkour.start.area")) {
								plugin.man.getAreas().forEach(a -> options.add(a.getName()));
							}
							if(s.hasPermission("ajparkour.start.others") || s.hasPermission("ajparkour.start.othersarea")) {
								for(Player p : Bukkit.getOnlinePlayers()) {
									if(s instanceof Player viewer && !viewer.canSee(p)) continue;
									options.add(p.getName());
								}
							}
							return suggest(builder, options);
						})
						.executes(this::startOne)
						.then(Commands.argument("area", StringArgumentType.string())
								.requires(src -> canStartOthersInArea(src.getSender()))
								.suggests(areas())
								.executes(this::startOtherInArea)));
	}

	private static boolean canStartOthersInArea(CommandSender s) {
		return (s.hasPermission("ajparkour.start.others") && s.hasPermission("ajparkour.start.area"))
				|| s.hasPermission("ajparkour.start.othersarea");
	}

	private boolean precheck(CommandContext<CommandSourceStack> ctx) {
		CommandSender sender = sender(ctx);
		Player sply = player(ctx);
		if(plugin.man.getAreas().isEmpty()) {
			sender.sendMessage(Messages.join(
					msgs().get("errors.notsetup.base", sply),
					sender.hasPermission("ajparkour.setup")
							? msgs().get("errors.notsetup.admin", sply, tag("cmd", label(ctx)))
							: msgs().get("errors.notsetup.player", sply)
			));
			return false;
		}
		if(sply == null) return true;
		if(plugin.man.getPlayer(sply) != null) {
			msgs().send(sender, sply, "alreadyin");
			return false;
		}
		if(plugin.settings().startDisabledWorlds().contains(sply.getWorld().getName())) {
			msgs().send(sply, "disabledworld");
			return false;
		}
		return true;
	}

	private int startSelf(CommandContext<CommandSourceStack> ctx) {
		if(!precheck(ctx)) return OK;
		Player p = requirePlayer(ctx);
		if(p != null) plugin.man.startGame(p);
		return OK;
	}

	private int startOne(CommandContext<CommandSourceStack> ctx) {
		if(!precheck(ctx)) return OK;
		CommandSender sender = sender(ctx);
		Player sply = player(ctx);
		String input = StringArgumentType.getString(ctx, "target");

		Player target = Bukkit.getPlayerExact(input);
		if(target != null) {
			if(!sender.hasPermission("ajparkour.start.others")) {
				msgs().send(sender, sply, "noperm");
				return OK;
			}
			plugin.man.startGame(target);
			msgs().send(sender, sply, "start.started-other", tag("player", target.getName()));
			return OK;
		}

		Player p = requirePlayer(ctx);
		if(p == null) return OK;
		if(!sender.hasPermission("ajparkour.start.area")) {
			msgs().send(sender, sply, "noperm");
			return OK;
		}
		PkArea area = plugin.man.getArea(input.toLowerCase(Locale.ROOT));
		if(area == null) {
			msgs().send(sender, sply, "start.no-area-player", tag("input", input));
			return OK;
		}
		plugin.man.startGame(p, area);
		return OK;
	}

	private int startOtherInArea(CommandContext<CommandSourceStack> ctx) {
		if(!precheck(ctx)) return OK;
		CommandSender sender = sender(ctx);
		Player sply = player(ctx);
		Player p = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
		PkArea a = plugin.man.getArea(StringArgumentType.getString(ctx, "area"));
		if(p == null) {
			msgs().send(sender, sply, "start.unknown-player");
			return OK;
		}
		if(a == null) {
			msgs().send(sender, sply, "start.unknown-area");
			return OK;
		}
		plugin.man.startGame(p, a);
		msgs().send(sender, sply, "start.started-other-area", tag("player", p.getName()), tag("area", a.getName()));
		return OK;
	}
}
