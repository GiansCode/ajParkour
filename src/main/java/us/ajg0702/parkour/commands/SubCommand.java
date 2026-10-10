package us.ajg0702.parkour.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import us.ajg0702.parkour.Main;
import us.ajg0702.parkour.Messages;
import us.ajg0702.parkour.game.PkArea;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * A single /ajparkour sub command.
 */
public abstract class SubCommand {

	protected static final int OK = Command.SINGLE_SUCCESS;

	protected final Main plugin;

	protected SubCommand(Main plugin) {
		this.plugin = plugin;
	}

	public abstract LiteralArgumentBuilder<CommandSourceStack> build();

	protected Messages msgs() {
		return plugin.msgs;
	}

	protected static TagResolver tag(String name, String value) {
		return Messages.tag(name, value);
	}

	protected static TagResolver tag(String name, int value) {
		return Messages.tag(name, value);
	}

	/** The alias the command was run with, for {CMD} in messages. */
	protected static String label(CommandContext<CommandSourceStack> ctx) {
		String input = ctx.getInput();
		if(input.startsWith("/")) input = input.substring(1);
		int space = input.indexOf(' ');
		return space == -1 ? input : input.substring(0, space);
	}

	protected static CommandSender sender(CommandContext<CommandSourceStack> ctx) {
		return ctx.getSource().getSender();
	}

	/** The sender if it is a player, otherwise null. */
	protected static @Nullable Player player(CommandContext<CommandSourceStack> ctx) {
		return ctx.getSource().getSender() instanceof Player p ? p : null;
	}

	/** Sends the not-from-console message and returns null if the sender isn't a player. */
	protected @Nullable Player requirePlayer(CommandContext<CommandSourceStack> ctx) {
		Player p = player(ctx);
		if(p == null) sender(ctx).sendMessage(msgs().get("not-from-console"));
		return p;
	}

	/** Passes if the sender has any of the permissions. */
	protected static Predicate<CommandSourceStack> perm(String... perms) {
		return src -> {
			CommandSender s = src.getSender();
			for(String perm : perms) {
				if(s.hasPermission(perm)) return true;
			}
			return false;
		};
	}

	protected static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder, Iterable<String> options) {
		String remaining = builder.getRemainingLowerCase();
		for(String option : options) {
			if(option.toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(option);
		}
		return builder.buildFuture();
	}

	protected SuggestionProvider<CommandSourceStack> areas() {
		return (_, builder) -> suggest(builder, plugin.man.getAreas().stream().map(PkArea::getName).toList());
	}

	protected SuggestionProvider<CommandSourceStack> configuredAreas() {
		return (_, builder) -> suggest(builder, plugin.areaStorage.getAreaNames());
	}

	protected SuggestionProvider<CommandSourceStack> portals() {
		return (_, builder) -> suggest(builder, plugin.areaStorage.getPortalNames());
	}

	protected static SuggestionProvider<CommandSourceStack> players() {
		return (ctx, builder) -> {
			CommandSender s = ctx.getSource().getSender();
			String remaining = builder.getRemainingLowerCase();
			for(Player p : Bukkit.getOnlinePlayers()) {
				if(s instanceof Player viewer && !viewer.canSee(p)) continue;
				if(p.getName().toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(p.getName());
			}
			return builder.buildFuture();
		};
	}
}
