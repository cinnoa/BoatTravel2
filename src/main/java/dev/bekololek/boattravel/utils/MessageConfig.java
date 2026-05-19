package dev.bekololek.boattravel.utils;

import dev.bekololek.boattravel.Main;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Central style configuration. Every color, decoration, and visible glyph used
 * by chat messages, action bars, titles, and signs is loaded from config.yml
 * so the whole plugin can be re-themed without touching source.
 *
 * Call {@link #reload(Main)} on plugin enable and on /bt reload.
 */
public final class MessageConfig {

    // ── Prefix ────────────────────────────────────────────────────────────────
    public static String prefixTag = "NAF";
    public static TextColor prefixTagColor = parse("#4B22FF");
    public static Set<TextDecoration> prefixTagDecorations = EnumSet.of(TextDecoration.BOLD);
    public static TextColor prefixBracketColor = NamedTextColor.DARK_GRAY;

    // ── Message body ──────────────────────────────────────────────────────────
    public static TextColor bodyColor = NamedTextColor.GRAY;
    public static TextColor variableColor = parse("#4B22FF");
    public static Set<TextDecoration> variableDecorations = EnumSet.of(TextDecoration.BOLD);

    public static TextColor errorColor = NamedTextColor.GRAY;
    public static TextColor errorVariableColor = NamedTextColor.RED;
    public static TextColor warnColor = NamedTextColor.GRAY;
    public static TextColor successColor = NamedTextColor.GRAY;
    public static TextColor successAccentColor = NamedTextColor.GREEN;

    // ── Headers (✦ Title ✦) ───────────────────────────────────────────────────
    public static String headerStarChar = "✦";
    public static TextColor headerStarColor = NamedTextColor.WHITE;
    public static Set<TextDecoration> headerStarDecorations = EnumSet.of(TextDecoration.BOLD);
    public static TextColor headerTitleColor = parse("#4B22FF");
    public static Set<TextDecoration> headerTitleDecorations = EnumSet.of(TextDecoration.BOLD);

    // ── List rows ─────────────────────────────────────────────────────────────
    public static TextColor listRowColor = NamedTextColor.GRAY;
    public static TextColor listRowAccentColor = parse("#4B22FF");

    // ── Usage lines ───────────────────────────────────────────────────────────
    public static TextColor usageCommandColor = NamedTextColor.GRAY;
    public static TextColor usageVariableColor = parse("#4B22FF");
    public static Set<TextDecoration> usageVariableDecorations = EnumSet.noneOf(TextDecoration.class);

    // ── Big titles ────────────────────────────────────────────────────────────
    public static TextColor titleColor = parse("#4B22FF");
    public static Set<TextDecoration> titleDecorations = EnumSet.of(TextDecoration.BOLD);
    public static TextColor subtitleLabelColor = NamedTextColor.GRAY;
    public static TextColor subtitleValueColor = NamedTextColor.WHITE;
    public static String departingTitle = "Departing";
    public static String arrivedTitle = "Arrived";
    public static String destinationSubtitlePrefix = "Destination: ";

    // ── Action bar ────────────────────────────────────────────────────────────
    public static String actionbarBoatChar = "⛵";
    public static TextColor actionbarBoatColor = NamedTextColor.WHITE;
    public static Set<TextDecoration> actionbarBoatDecorations = EnumSet.of(TextDecoration.BOLD);
    public static String actionbarFilledChar = "•";
    public static String actionbarEmptyChar = "•";
    public static TextColor actionbarFilledColor = parse("#4B22FF");
    public static TextColor actionbarEmptyColor = NamedTextColor.DARK_GRAY;
    public static String actionbarBracketChar = "⋯";
    public static TextColor actionbarBracketColor = NamedTextColor.DARK_GRAY;
    public static TextColor actionbarLabelColor = NamedTextColor.GRAY;
    public static TextColor actionbarDestinationColor = NamedTextColor.WHITE;
    public static Set<TextDecoration> actionbarDestinationDecorations = EnumSet.of(TextDecoration.BOLD);
    public static TextColor actionbarRemainingColor = parse("#4B22FF");
    public static Set<TextDecoration> actionbarRemainingDecorations = EnumSet.of(TextDecoration.BOLD);
    public static String actionbarConfirmText = "Click again to confirm.";
    public static TextColor actionbarConfirmColor = NamedTextColor.GRAY;

    // ── Signs ─────────────────────────────────────────────────────────────────
    public static String signStarChar = "✦";
    public static TextColor signStarColor = NamedTextColor.WHITE;
    public static Set<TextDecoration> signStarDecorations = EnumSet.of(TextDecoration.BOLD);
    public static String signObfChar = "%";
    public static TextColor signObfColor = parse("#4B22FF");
    public static Set<TextDecoration> signObfDecorations = EnumSet.of(TextDecoration.BOLD, TextDecoration.OBFUSCATED);
    public static TextColor signTitleColor = NamedTextColor.WHITE;
    public static Set<TextDecoration> signTitleDecorations = EnumSet.of(TextDecoration.BOLD, TextDecoration.UNDERLINED);
    public static String signDockTitle = "Dock";
    public static String signVoyageTitle = "Voyage";
    public static TextColor signDestinationColor = NamedTextColor.WHITE;
    public static TextColor signTownColor = NamedTextColor.GRAY;
    public static TextColor signPriceColor = parse("#4B22FF");
    public static Set<TextDecoration> signPriceDecorations = EnumSet.of(TextDecoration.BOLD);
    public static TextColor signFreeColor = parse("#4B22FF");
    public static Set<TextDecoration> signFreeDecorations = EnumSet.of(TextDecoration.BOLD);
    public static String signFreeText = "Free";
    public static TextColor signUnavailableColor = NamedTextColor.RED;
    public static Set<TextDecoration> signUnavailableDecorations = EnumSet.of(TextDecoration.BOLD);
    public static String signUnavailableText = "Unavailable";
    public static String signWildernessText = "Wilderness";

    // ── Stats ─────────────────────────────────────────────────────────────────
    public static TextColor statsLabelColor = NamedTextColor.GRAY;
    public static TextColor statsValueColor = parse("#4B22FF");
    public static Set<TextDecoration> statsValueDecorations = EnumSet.of(TextDecoration.BOLD);
    public static TextColor statsRankColor = NamedTextColor.GRAY;
    public static TextColor statsSeparatorColor = NamedTextColor.DARK_GRAY;

    private MessageConfig() {}

    public static void reload(Main plugin) {
        FileConfiguration cfg = plugin.getConfig();

        // Prefix
        prefixTag = cfg.getString("messages.prefix.tag", "NAF");
        prefixTagColor = color(cfg, "messages.prefix.tag-color", "#4B22FF");
        prefixTagDecorations = decorations(cfg, "messages.prefix.tag-decorations", List.of("BOLD"));
        prefixBracketColor = color(cfg, "messages.prefix.bracket-color", "DARK_GRAY");

        // Body
        bodyColor = color(cfg, "messages.body.text-color", "GRAY");
        variableColor = color(cfg, "messages.body.variable-color", "#4B22FF");
        variableDecorations = decorations(cfg, "messages.body.variable-decorations", List.of("BOLD"));
        errorColor = color(cfg, "messages.body.error-color", "GRAY");
        errorVariableColor = color(cfg, "messages.body.error-variable-color", "RED");
        warnColor = color(cfg, "messages.body.warn-color", "GRAY");
        successColor = color(cfg, "messages.body.success-color", "GRAY");
        successAccentColor = color(cfg, "messages.body.success-accent-color", "GREEN");

        // Header
        headerStarChar = cfg.getString("messages.header.star-char", "✦");
        headerStarColor = color(cfg, "messages.header.star-color", "WHITE");
        headerStarDecorations = decorations(cfg, "messages.header.star-decorations", List.of("BOLD"));
        headerTitleColor = color(cfg, "messages.header.title-color", "#4B22FF");
        headerTitleDecorations = decorations(cfg, "messages.header.title-decorations", List.of("BOLD"));

        // List
        listRowColor = color(cfg, "messages.list.row-color", "GRAY");
        listRowAccentColor = color(cfg, "messages.list.row-accent-color", "#4B22FF");

        // Usage
        usageCommandColor = color(cfg, "messages.usage.command-color", "GRAY");
        usageVariableColor = color(cfg, "messages.usage.variable-color", "#4B22FF");
        usageVariableDecorations = decorations(cfg, "messages.usage.variable-decorations", List.of());

        // Titles
        titleColor = color(cfg, "messages.titles.title-color", "#4B22FF");
        titleDecorations = decorations(cfg, "messages.titles.title-decorations", List.of("BOLD"));
        subtitleLabelColor = color(cfg, "messages.titles.subtitle-label-color", "GRAY");
        subtitleValueColor = color(cfg, "messages.titles.subtitle-value-color", "WHITE");
        departingTitle = cfg.getString("messages.titles.departing-text", "Departing");
        arrivedTitle = cfg.getString("messages.titles.arrived-text", "Arrived");
        destinationSubtitlePrefix = cfg.getString("messages.titles.destination-subtitle-prefix", "Destination: ");

        // Action bar
        actionbarBoatChar = cfg.getString("messages.actionbar.boat-char", "⛵");
        actionbarBoatColor = color(cfg, "messages.actionbar.boat-color", "WHITE");
        actionbarBoatDecorations = decorations(cfg, "messages.actionbar.boat-decorations", List.of("BOLD"));
        actionbarFilledChar = cfg.getString("messages.actionbar.filled-char", "•");
        actionbarEmptyChar = cfg.getString("messages.actionbar.empty-char", "•");
        actionbarFilledColor = color(cfg, "messages.actionbar.filled-color", "#4B22FF");
        actionbarEmptyColor = color(cfg, "messages.actionbar.empty-color", "DARK_GRAY");
        actionbarBracketChar = cfg.getString("messages.actionbar.bracket-char", "⋯");
        actionbarBracketColor = color(cfg, "messages.actionbar.bracket-color", "DARK_GRAY");
        actionbarLabelColor = color(cfg, "messages.actionbar.label-color", "GRAY");
        actionbarDestinationColor = color(cfg, "messages.actionbar.destination-color", "WHITE");
        actionbarDestinationDecorations = decorations(cfg, "messages.actionbar.destination-decorations", List.of("BOLD"));
        actionbarRemainingColor = color(cfg, "messages.actionbar.remaining-color", "#4B22FF");
        actionbarRemainingDecorations = decorations(cfg, "messages.actionbar.remaining-decorations", List.of("BOLD"));
        actionbarConfirmText = cfg.getString("messages.actionbar.confirm-text", "Click again to confirm.");
        actionbarConfirmColor = color(cfg, "messages.actionbar.confirm-color", "GRAY");

        // Signs
        signStarChar = cfg.getString("messages.signs.star-char", "✦");
        signStarColor = color(cfg, "messages.signs.star-color", "WHITE");
        signStarDecorations = decorations(cfg, "messages.signs.star-decorations", List.of("BOLD"));
        signObfChar = cfg.getString("messages.signs.obf-char", "%");
        signObfColor = color(cfg, "messages.signs.obf-color", "#4B22FF");
        signObfDecorations = decorations(cfg, "messages.signs.obf-decorations", List.of("BOLD", "OBFUSCATED"));
        signTitleColor = color(cfg, "messages.signs.title-color", "WHITE");
        signTitleDecorations = decorations(cfg, "messages.signs.title-decorations", List.of("BOLD", "UNDERLINED"));
        signDockTitle = cfg.getString("messages.signs.dock-title", "Dock");
        signVoyageTitle = cfg.getString("messages.signs.voyage-title", "Voyage");
        signDestinationColor = color(cfg, "messages.signs.destination-color", "WHITE");
        signTownColor = color(cfg, "messages.signs.town-color", "GRAY");
        signPriceColor = color(cfg, "messages.signs.price-color", "#4B22FF");
        signPriceDecorations = decorations(cfg, "messages.signs.price-decorations", List.of("BOLD"));
        signFreeColor = color(cfg, "messages.signs.free-color", "#4B22FF");
        signFreeDecorations = decorations(cfg, "messages.signs.free-decorations", List.of("BOLD"));
        signFreeText = cfg.getString("messages.signs.free-text", "Free");
        signUnavailableColor = color(cfg, "messages.signs.unavailable-color", "RED");
        signUnavailableDecorations = decorations(cfg, "messages.signs.unavailable-decorations", List.of("BOLD"));
        signUnavailableText = cfg.getString("messages.signs.unavailable-text", "Unavailable");
        signWildernessText = cfg.getString("messages.signs.wilderness-text", "Wilderness");

        // Stats
        statsLabelColor = color(cfg, "messages.stats.label-color", "GRAY");
        statsValueColor = color(cfg, "messages.stats.value-color", "#4B22FF");
        statsValueDecorations = decorations(cfg, "messages.stats.value-decorations", List.of("BOLD"));
        statsRankColor = color(cfg, "messages.stats.rank-color", "GRAY");
        statsSeparatorColor = color(cfg, "messages.stats.separator-color", "DARK_GRAY");
    }

    private static TextColor color(FileConfiguration cfg, String path, String fallback) {
        String raw = cfg.getString(path, fallback);
        TextColor parsed = parse(raw);
        if (parsed != null) return parsed;
        TextColor fb = parse(fallback);
        return fb != null ? fb : NamedTextColor.WHITE;
    }

    private static Set<TextDecoration> decorations(FileConfiguration cfg, String path, List<String> fallback) {
        List<String> raw;
        if (cfg.isList(path)) {
            raw = cfg.getStringList(path);
        } else if (cfg.isString(path)) {
            String s = cfg.getString(path, "");
            raw = s.isBlank() ? List.of() : new ArrayList<>(Arrays.asList(s.split("[,\\s]+")));
        } else {
            raw = fallback;
        }
        EnumSet<TextDecoration> set = EnumSet.noneOf(TextDecoration.class);
        for (String s : raw) {
            if (s == null || s.isBlank()) continue;
            try {
                set.add(TextDecoration.valueOf(s.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return set;
    }

    /** Parse a hex (#RRGGBB) or named color. Returns null on unknown input. */
    public static TextColor parse(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        if (v.startsWith("#") && v.length() == 7) {
            return TextColor.fromHexString(v);
        }
        return switch (v.toUpperCase()) {
            case "BLACK"        -> NamedTextColor.BLACK;
            case "DARK_BLUE"    -> NamedTextColor.DARK_BLUE;
            case "DARK_GREEN"   -> NamedTextColor.DARK_GREEN;
            case "DARK_AQUA"    -> NamedTextColor.DARK_AQUA;
            case "DARK_RED"     -> NamedTextColor.DARK_RED;
            case "DARK_PURPLE"  -> NamedTextColor.DARK_PURPLE;
            case "GOLD"         -> NamedTextColor.GOLD;
            case "GRAY"         -> NamedTextColor.GRAY;
            case "DARK_GRAY"    -> NamedTextColor.DARK_GRAY;
            case "BLUE"         -> NamedTextColor.BLUE;
            case "GREEN"        -> NamedTextColor.GREEN;
            case "AQUA"         -> NamedTextColor.AQUA;
            case "RED"          -> NamedTextColor.RED;
            case "LIGHT_PURPLE" -> NamedTextColor.LIGHT_PURPLE;
            case "YELLOW"       -> NamedTextColor.YELLOW;
            case "WHITE"        -> NamedTextColor.WHITE;
            default             -> null;
        };
    }
}
