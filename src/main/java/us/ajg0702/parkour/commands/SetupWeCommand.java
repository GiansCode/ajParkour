package us.ajg0702.parkour.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import us.ajg0702.parkour.Main;
import us.ajg0702.parkour.WorldeditSelector;

public class SetupWeCommand extends SubCommand {

	public SetupWeCommand(Main plugin) {
		super(plugin);
	}

	@Override
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("we").executes(ctx -> {
			Player p = requirePlayer(ctx);
			if(p == null || !plugin.setup.requireActive(p, msgs())) return OK;
			if(Bukkit.getPluginManager().getPlugin("WorldEdit") == null) {
				p.sendMessage(msgs().get("setup.we.not-installed", p));
				return OK;
			}
			WorldeditSelector.select(p, msgs(), plugin.setup);
			plugin.setup.warnIfSmall(p, msgs());
			return OK;
		});
	}
}
