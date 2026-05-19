package dev.bekololek.boattravel.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Builds {@link Component}s from the central {@link MessageConfig} so every
 * visible piece of text in the plugin can be re-themed via config.yml alone.
 *
 * Naming convention used throughout the plugin:
 *  - text(...)   → light body text, default color
 *  - var(...)    → highlighted variable (dock/route name, price, etc.)
 *  - error/warn  → light body text wrapped in prefix; identical color but used
 *                  for semantic clarity at call sites
 *  - usage(...)  → command-line help: command in command color, args in variable color
 *  - header(...) → "✦ Title ✦" branded heading
 */
public final class MessageUtils {

    private MessageUtils() {}

    public static Component prefix() {
        Component tag = Component.text(MessageConfig.prefixTag).color(MessageConfig.prefixTagColor);
        for (TextDecoration d : MessageConfig.prefixTagDecorations) tag = tag.decorate(d);
        return Component.empty()
                .append(Component.text("[", MessageConfig.prefixBracketColor))
                .append(tag)
                .append(Component.text("] ", MessageConfig.prefixBracketColor));
    }

    public static Component prefixed(Component body) {
        return prefix().append(body);
    }

    public static Component text(String s) {
        return Component.text(s, MessageConfig.bodyColor);
    }

    public static Component var(String s) {
        Component c = Component.text(s, MessageConfig.variableColor);
        for (TextDecoration d : MessageConfig.variableDecorations) c = c.decorate(d);
        return c;
    }

    public static Component error(String s) {
        return prefixed(Component.text(s, MessageConfig.errorColor));
    }

    public static Component warn(String s) {
        return prefixed(Component.text(s, MessageConfig.warnColor));
    }

    public static Component success(String s) {
        return prefixed(Component.text(s, MessageConfig.successColor));
    }

    public static Component usage(String command, String... variables) {
        Component c = Component.text(command, MessageConfig.usageCommandColor);
        for (String v : variables) {
            Component vc = Component.text(v, MessageConfig.usageVariableColor);
            for (TextDecoration d : MessageConfig.usageVariableDecorations) vc = vc.decorate(d);
            c = c.append(Component.text(" ", MessageConfig.usageCommandColor)).append(vc);
        }
        return prefixed(c);
    }

    public static Component header(String title) {
        Component star = Component.text(MessageConfig.headerStarChar, MessageConfig.headerStarColor);
        for (TextDecoration d : MessageConfig.headerStarDecorations) star = star.decorate(d);
        Component t = Component.text(title, MessageConfig.headerTitleColor);
        for (TextDecoration d : MessageConfig.headerTitleDecorations) t = t.decorate(d);
        return prefixed(Component.empty().append(star).append(Component.text(" ")).append(t).append(Component.text(" ")).append(star));
    }

    /** "  prefix accent suffix" — handy for inline list rows. */
    public static Component listRow(String prefix, String accent, String suffix) {
        return Component.text(prefix, MessageConfig.listRowColor)
                .append(Component.text(accent, MessageConfig.listRowAccentColor))
                .append(Component.text(suffix, MessageConfig.listRowColor));
    }

    /** ✦ % Title % ✦ — used as line 1 of dock/voyage signs. */
    public static Component signHeader(String title) {
        Component star = Component.text(MessageConfig.signStarChar, MessageConfig.signStarColor);
        for (TextDecoration d : MessageConfig.signStarDecorations) star = star.decorate(d);
        Component obf = Component.text(MessageConfig.signObfChar, MessageConfig.signObfColor);
        for (TextDecoration d : MessageConfig.signObfDecorations) obf = obf.decorate(d);
        Component t = Component.text(title, MessageConfig.signTitleColor);
        for (TextDecoration d : MessageConfig.signTitleDecorations) t = t.decorate(d);
        return Component.empty()
                .append(star)
                .append(Component.text(" "))
                .append(obf)
                .append(Component.text(" "))
                .append(t)
                .append(Component.text(" "))
                .append(obf)
                .append(Component.text(" "))
                .append(star);
    }

    public static Component signDestination(String dockName) {
        return Component.text(dockName, MessageConfig.signDestinationColor);
    }

    public static Component signTown(String town) {
        return Component.text(town, MessageConfig.signTownColor);
    }

    public static Component signPrice(String text) {
        Component c = Component.text(text, MessageConfig.signPriceColor);
        for (TextDecoration d : MessageConfig.signPriceDecorations) c = c.decorate(d);
        return c;
    }

    public static Component signFree() {
        Component c = Component.text(MessageConfig.signFreeText, MessageConfig.signFreeColor);
        for (TextDecoration d : MessageConfig.signFreeDecorations) c = c.decorate(d);
        return c;
    }

    public static Component signUnavailable() {
        Component c = Component.text(MessageConfig.signUnavailableText, MessageConfig.signUnavailableColor);
        for (TextDecoration d : MessageConfig.signUnavailableDecorations) c = c.decorate(d);
        return c;
    }
}
