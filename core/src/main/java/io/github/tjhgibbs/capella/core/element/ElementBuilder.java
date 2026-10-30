package io.github.tjhgibbs.capella.core.element;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Collects elements in insertion order, refusing duplicate ids and empty bounds. */
public final class ElementBuilder {
    private final List<Element> out = new ArrayList<>();
    private final Set<String> seen = new HashSet<>();

    public Element add(Element e) {
        if (!seen.add(e.id())) throw new IllegalStateException("duplicate element id '" + e.id() + "'");
        if (e.bounds().isEmpty()) throw new IllegalStateException("element '" + e.id() + "' has empty bounds");
        out.add(e);
        return e;
    }

    public boolean has(String id) {
        return seen.contains(id);
    }

    public List<Element> elements() {
        return out;
    }

    public ElementTree build() {
        return new ElementTree(out);
    }
}
