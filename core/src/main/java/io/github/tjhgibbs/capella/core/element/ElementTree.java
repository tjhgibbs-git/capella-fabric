package io.github.tjhgibbs.capella.core.element;

import io.github.tjhgibbs.capella.core.geom.Box;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The full set of elements one generation produced, indexed by id and parent. */
public final class ElementTree {
    private final List<Element> elements;
    private final Map<String, Element> index = new HashMap<>();
    private final Map<String, List<Element>> childIndex = new HashMap<>();

    public ElementTree(List<Element> elements) {
        for (Element e : elements) {
            if (index.putIfAbsent(e.id(), e) != null) throw new IllegalStateException("duplicate element id '" + e.id() + "'");
        }
        for (Element e : elements) {
            if (e.parentId() != null && !index.containsKey(e.parentId())) {
                throw new IllegalStateException("element '" + e.id() + "' has unknown parent '" + e.parentId() + "'");
            }
            for (String s : e.supports()) {
                if (!index.containsKey(s)) throw new IllegalStateException("element '" + e.id() + "' supported by unknown '" + s + "'");
            }
            if (e.parentId() != null) childIndex.computeIfAbsent(e.parentId(), k -> new ArrayList<>()).add(e);
        }
        this.elements = List.copyOf(elements);
    }

    public List<Element> elements() {
        return elements;
    }

    public int size() {
        return elements.size();
    }

    public Element get(String id) {
        return index.get(id);
    }

    public Element require(String id) {
        Element e = index.get(id);
        if (e == null) throw new IllegalStateException("no element '" + id + "'");
        return e;
    }

    public List<Element> children(String id) {
        return childIndex.getOrDefault(id, Collections.emptyList());
    }

    public List<Element> byKind(ElementKind kind) {
        return elements.stream().filter(e -> e.kind() == kind).toList();
    }

    public Map<String, Integer> countsByKind() {
        Map<String, Integer> counts = new TreeMap<>();
        for (Element e : elements) counts.merge(e.kind().id(), 1, Integer::sum);
        return new LinkedHashMap<>(counts);
    }

    public Box bounds() {
        return Box.unionAll(elements.stream().map(Element::bounds).toList());
    }
}
