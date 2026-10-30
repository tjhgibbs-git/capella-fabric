package io.github.tjhgibbs.capella.core.palette;

import java.util.EnumMap;
import java.util.Map;

/** One material in a role's pool: a block id per form ({@code BLOCK} is mandatory), and a weight. */
public final class Material {
    public final Map<BlockForm, String> forms;
    public final double weight;

    public Material(Map<BlockForm, String> forms, double weight) {
        if (!forms.containsKey(BlockForm.BLOCK)) throw new IllegalArgumentException("material needs a block form");
        this.forms = new EnumMap<>(forms);
        this.weight = weight;
    }

    public String block() {
        return forms.get(BlockForm.BLOCK);
    }
}
