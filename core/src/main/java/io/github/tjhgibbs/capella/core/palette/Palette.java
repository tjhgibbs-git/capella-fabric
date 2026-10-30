package io.github.tjhgibbs.capella.core.palette;

import io.github.tjhgibbs.capella.core.hash.Hash;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A complete answer to every {@link MaterialRole} (Capella {@code Palette}). Swapping palettes
 * changes block ids and nothing else; the occupancy of a build never depends on it.
 */
public final class Palette {
    public final String id;
    public final String name;
    public final Map<MaterialRole, RoleEntry> roles;

    private static final double WEATHER_WAVELENGTH = 11;
    private static final double MATERIAL_WAVELENGTH = 4;

    public Palette(String id, String name, Map<MaterialRole, RoleEntry> declared) {
        this.id = id;
        this.name = name;
        this.roles = completeRoles(id, declared);
    }

    /** Fills every role not declared by walking its fallback chain. Throws on a missing root. */
    public static Map<MaterialRole, RoleEntry> completeRoles(String id, Map<MaterialRole, RoleEntry> declared) {
        Map<MaterialRole, RoleEntry> out = new EnumMap<>(MaterialRole.class);
        for (MaterialRole role : MaterialRole.values()) {
            MaterialRole at = role;
            List<MaterialRole> chain = new ArrayList<>();
            RoleEntry entry = null;
            while (at != null) {
                if (chain.contains(at)) throw new IllegalStateException("palette '" + id + "': role fallback cycle " + chain + " -> " + at);
                chain.add(at);
                entry = declared.get(at);
                if (entry != null) break;
                at = at.fallback();
            }
            if (entry == null) {
                String via = chain.size() > 1 ? " (via " + chain.subList(1, chain.size()) + ")" : "";
                throw new IllegalStateException("palette '" + id + "': palette.roles." + role + ": missing" + via);
            }
            out.put(role, entry);
        }
        return out;
    }

    private RoleEntry entry(MaterialRole role) {
        RoleEntry e = roles.get(role);
        if (e == null) throw new IllegalStateException("palette '" + id + "' has no role '" + role + "'");
        return e;
    }

    private static String formOfMaterial(Material chosen, List<Material> pool, List<Material> materials, BlockForm form) {
        String direct = chosen.forms.get(form);
        if (direct != null) return direct;
        for (Material m : pool) {
            String alt = m.forms.get(form);
            if (alt != null) return alt;
        }
        for (Material m : materials) {
            String alt = m.forms.get(form);
            if (alt != null) return alt;
        }
        return chosen.block();
    }

    /** {@code resolveRole}: the block id for one voxel. Weathering is 0..255. */
    public String resolveRole(MaterialRole role, BlockForm form, double seed, double x, double y, double z, double weathering) {
        RoleEntry entry = entry(role);
        boolean useWeathered = entry.weathered != null && weathering > 0
                && Hash.noise3(seed, "weathering", x, y, z, WEATHER_WAVELENGTH) < weathering / 255;
        List<Material> pool = useWeathered ? entry.weathered : entry.materials;
        double[] weights = pool.stream().mapToDouble(m -> m.weight).toArray();
        Material chosen = Hash.pickWeighted(Hash.noise3(seed, "palette/" + role.name(), x, y, z, MATERIAL_WAVELENGTH), pool, weights);
        return formOfMaterial(chosen, pool, entry.materials, form);
    }

    /** Resolves block ids for one role and form; the fast path the rasterizer calls per voxel. */
    public interface RoleResolver {
        String resolve(int x, int y, int z, double weathering);
    }

    private record Pool(String[] names, double[] weights, double total) {
    }

    /** {@code roleResolver}: same answers as {@link #resolveRole}, with the per-role work hoisted. */
    public RoleResolver roleResolver(MaterialRole role, BlockForm form, double seed) {
        RoleEntry entry = entry(role);
        int weatherPrefix = Hash.hashPrefix(seed, "weathering");
        int materialPrefix = Hash.hashPrefix(seed, "palette/" + role.name());
        Pool plain = prepare(entry.materials, entry.materials, form);
        Pool weathered = entry.weathered != null ? prepare(entry.weathered, entry.materials, form) : null;
        if (weathered == null && plain.names.length == 1) {
            String only = plain.names[0];
            return (x, y, z, w) -> only;
        }
        return (x, y, z, weathering) -> {
            Pool pool = weathered != null && weathering > 0
                    && Hash.noise3p(weatherPrefix, x, y, z, WEATHER_WAVELENGTH) < weathering / 255 ? weathered : plain;
            if (pool.names.length == 1) return pool.names[0];
            double draw = Hash.noise3p(materialPrefix, x, y, z, MATERIAL_WAVELENGTH);
            double r = Math.min(0.999999, Math.max(0, draw)) * pool.total;
            for (int i = 0; i < pool.weights.length; i++) {
                r -= pool.weights[i];
                if (r < 0) return pool.names[i];
            }
            return pool.names[pool.names.length - 1];
        };
    }

    private static Pool prepare(List<Material> pool, List<Material> materials, BlockForm form) {
        String[] names = new String[pool.size()];
        double[] weights = new double[pool.size()];
        double total = 0;
        for (int i = 0; i < pool.size(); i++) {
            names[i] = formOfMaterial(pool.get(i), pool, materials, form);
            weights[i] = pool.get(i).weight;
            if (!(weights[i] >= 0)) throw new IllegalStateException("pickWeighted: bad weight " + weights[i]);
            total += weights[i];
        }
        if (total <= 0) throw new IllegalStateException("pickWeighted: weights sum to zero");
        return new Pool(names, weights, total);
    }
}
