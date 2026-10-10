package us.ajg0702.parkour.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jspecify.annotations.Nullable;

public final class Text {
	/** Applied last so help args like {@code <area>} print as text instead of failing the parse. */
	public static final TagResolver UNKNOWN_AS_LITERAL = new TagResolver() {
		@Override
		public @Nullable Tag resolve(String name, ArgumentQueue arguments, Context ctx) {
			return Tag.selfClosingInserting(Component.text("<" + name + ">"));
		}

		@Override
		public boolean has(String name) {
			return true;
		}
	};

	public static final MiniMessage MINI = MiniMessage.miniMessage();
	public static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
	public static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();
	public static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

	private Text() {}

	public static TagResolver tag(String name, String value) {
		return Placeholder.unparsed(name, value);
	}

	public static TagResolver tag(String name, int value) {
		return Placeholder.unparsed(name, Integer.toString(value));
	}

	public static TagResolver withUnknown(TagResolver... tags) {
		return tags.length == 0
				? UNKNOWN_AS_LITERAL
				: TagResolver.resolver(TagResolver.resolver(tags), UNKNOWN_AS_LITERAL);
	}

	/**
	 * Parses MiniMessage, or {@code &}/{@code §} legacy codes if those are present.
	 */
	public static Component parse(String text, TagResolver... tags) {
		if(text == null || text.isEmpty()) return Component.empty();
		if(hasLegacy(text)) {
			return LEGACY_AMPERSAND.deserialize(text.replace('\u00A7', '&'));
		}
		return MINI.deserialize(text, withUnknown(tags));
	}

	public static String plain(Component component) {
		return PLAIN.serialize(component);
	}

	public static boolean hasLegacy(String text) {
		int amp = text.indexOf('&');
		if(amp >= 0 && amp + 1 < text.length() && isLegacyCode(text.charAt(amp + 1))) return true;
		return text.indexOf('\u00A7') >= 0;
	}

	private static boolean isLegacyCode(char c) {
		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
				|| c == 'k' || c == 'K' || c == 'l' || c == 'L' || c == 'm' || c == 'M'
				|| c == 'n' || c == 'N' || c == 'o' || c == 'O' || c == 'r' || c == 'R' || c == 'x' || c == 'X';
	}

	/** Turns an existing {@code &} / {@code {PLACEHOLDER}} string into MiniMessage. */
	public static String modernize(String raw) {
		if(raw == null) return "";
		String s = raw;
		if(hasLegacy(s)) {
			s = MINI.serialize(LEGACY_AMPERSAND.deserialize(s.replace('\u00A7', '&')));
		}
		return s
				.replace("{SCORE}", "<score>")
				.replace("{HIGHSCORE}", "<highscore>")
				.replace("{CMD}", "<cmd>")
				.replace("{NAME}", "<name>")
				.replace("{PLY}", "<player>")
				.replace("{PLAYER}", "<player>")
				.replace("{AREA}", "<area>")
				.replace("{INPUT}", "<input>")
				.replace("{POSS}", "<options>")
				.replace("{#}", "<pos>")
				.replace("{TIME}", "<time>")
				.replace("{DIFFICULTY}", "<difficulty>")
				.replace("{COORDS}", "<coords>")
				.replace("{COUNT}", "<count>")
				.replace("{FILE}", "<file>")
				.replace("{VERSION}", "<version>")
				.replace("{w}", "<w>")
				.replace("{h}", "<h>")
				.replace("{l}", "<l>")
				.replace("{m}", "<m>")
				.replace("{s}", "<s>");
	}
}
