package org.github.sawervatoryy.mirageDArena.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Утилиты текста: цвета «&» и HEX «&#RRGGBB», простые плейсхолдеры {key}. */
public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private Text() {
    }

    /** Преобразует строку с &-кодами и &#RRGGBB в Component. */
    public static Component color(String input) {
        return LEGACY.deserialize(input == null ? "" : input);
    }

    /** Заменяет {key} на value; аргументы идут парами key, value. */
    public static String replace(String input, String... pairs) {
        String out = input;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out = out.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return out;
    }
}
