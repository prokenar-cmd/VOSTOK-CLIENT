package com.blackrussia.game.vostok.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * UI-neutral radial menu contract.
 *
 * No visual radial menu is enabled yet. This model is reusable by interaction, jobs and
 * future gameplay systems so they do not create incompatible radial implementations.
 */
public final class RadialMenuContract {
    private RadialMenuContract() {
    }

    public interface Host {
        void open(Request request);
        void close();
    }

    public interface SelectionSink {
        void onSelected(Request request, Item item);
        void onCancelled(Request request);
    }

    public static final class Item {
        public final int id;
        public final int kind;
        public final String title;
        public final String subtitle;
        public final boolean enabled;

        public Item(int id, int kind, String title, String subtitle, boolean enabled) {
            this.id = id;
            this.kind = kind;
            this.title = safe(title);
            this.subtitle = safe(subtitle);
            this.enabled = enabled;
        }
    }

    public static final class Request {
        public final int purpose;
        public final int sourceTargetType;
        public final int sourceTargetId;
        public final String title;
        public final List<Item> items;
        public final SelectionSink selectionSink;

        public Request(int purpose,
                       int sourceTargetType,
                       int sourceTargetId,
                       String title,
                       List<Item> items,
                       SelectionSink selectionSink) {
            this.purpose = purpose;
            this.sourceTargetType = sourceTargetType;
            this.sourceTargetId = sourceTargetId;
            this.title = safe(title);
            this.items = immutableCopy(items);
            this.selectionSink = selectionSink;
        }

        public boolean isEmpty() {
            return items.isEmpty();
        }
    }

    private static List<Item> immutableCopy(List<Item> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}