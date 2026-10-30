package io.github.tjhgibbs.capella.core.blockstate;

import io.github.tjhgibbs.capella.core.geom.Axis;
import io.github.tjhgibbs.capella.core.geom.Dir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

/**
 * Port of Capella's {@code blockstates.ts}: parsing, rotation and mirroring, shape
 * classification, and the neighbour-dependent normalisation of stairs, walls, fences, panes and
 * multiface blocks. Kept table-driven; a property the transform table does not know is an error
 * in strict mode, so a new block cannot silently rotate wrong.
 */
public final class Blockstates {
    private Blockstates() {
    }

    public static final BlockState AIR = new BlockState("minecraft:air");
    public static final BlockState STRUCTURE_VOID = new BlockState("minecraft:structure_void");
    public static final String STRUCTURE_VOID_KEY = "minecraft:structure_void";
    public static final BlockState WATER = new BlockState("minecraft:water", Map.of("level", "0"));

    private static String full(String name) {
        return name.contains(":") ? name : "minecraft:" + name;
    }

    /** {@code bs}: a state with exactly these props (no defaults). */
    public static BlockState bs(String name, Map<String, String> props) {
        return new BlockState(full(name), props);
    }

    public static String stateKey(BlockState s) {
        return s.key();
    }

    public static BlockState parseState(String key) {
        int open = key.indexOf('[');
        if (open < 0) return new BlockState(key);
        if (!key.endsWith("]")) throw new IllegalArgumentException("malformed blockstate '" + key + "'");
        String name = key.substring(0, open);
        Map<String, String> props = new HashMap<>();
        String body = key.substring(open + 1, key.length() - 1);
        if (!body.isEmpty()) {
            for (String part : body.split(",")) {
                int eq = part.indexOf('=');
                if (eq < 0) throw new IllegalArgumentException("malformed blockstate property '" + part + "' in '" + key + "'");
                props.put(part.substring(0, eq), part.substring(eq + 1));
            }
        }
        return new BlockState(name, props);
    }

    public static BlockState withProps(BlockState s, Map<String, String> extra) {
        Map<String, String> p = new HashMap<>(s.props);
        p.putAll(extra);
        return new BlockState(s.name, p);
    }

    public static BlockState withProp(BlockState s, String k, String v) {
        return withProps(s, Map.of(k, v));
    }

    public static boolean statesEqual(BlockState a, BlockState b) {
        return a.key().equals(b.key());
    }

    // ---- directions ---------------------------------------------------------------------------

    private static boolean isDir6(String v) {
        return v != null && switch (v) {
            case "north", "south", "east", "west", "up", "down" -> true;
            default -> false;
        };
    }

    private static boolean isHorizontal(String v) {
        return v != null && switch (v) {
            case "north", "south", "east", "west" -> true;
            default -> false;
        };
    }

    private static String cw(String d) {
        return Dir.of(d).clockWise().id();
    }

    private static String opposite(String d) {
        return Dir.of(d).opposite().id();
    }

    private static String counterClockWise(String d) {
        return Dir.of(d).counterClockWise().id();
    }

    private static int normaliseTurns(double turns) {
        return (int) (((Math.floor(turns + 0.5) % 4) + 4) % 4);
    }

    // ---- rotation and mirroring ---------------------------------------------------------------

    private static final List<String> STAIR_SHAPES = List.of("straight", "inner_left", "inner_right", "outer_left", "outer_right");
    private static final List<String> RAIL_SHAPES = List.of("north_south", "east_west", "ascending_east", "ascending_west",
            "ascending_north", "ascending_south", "south_east", "south_west", "north_west", "north_east");

    private static Map<String, String> pairs(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    private static final Map<String, String> RAIL_CW = pairs(
            "north_south", "east_west", "east_west", "north_south",
            "ascending_east", "ascending_south", "ascending_south", "ascending_west",
            "ascending_west", "ascending_north", "ascending_north", "ascending_east",
            "south_east", "south_west", "south_west", "north_west", "north_west", "north_east", "north_east", "south_east");
    private static final Map<String, String> RAIL_MIRROR_X = pairs(
            "north_south", "north_south", "east_west", "east_west",
            "ascending_east", "ascending_west", "ascending_west", "ascending_east",
            "ascending_north", "ascending_north", "ascending_south", "ascending_south",
            "south_east", "south_west", "south_west", "south_east", "north_west", "north_east", "north_east", "north_west");
    private static final Map<String, String> RAIL_MIRROR_Z = pairs(
            "north_south", "north_south", "east_west", "east_west",
            "ascending_east", "ascending_east", "ascending_west", "ascending_west",
            "ascending_north", "ascending_south", "ascending_south", "ascending_north",
            "south_east", "north_east", "north_east", "south_east", "south_west", "north_west", "north_west", "south_west");
    private static final Map<String, String> SWAP_HAND = pairs(
            "left", "right", "right", "left", "single", "single",
            "inner_left", "inner_right", "inner_right", "inner_left",
            "outer_left", "outer_right", "outer_right", "outer_left", "straight", "straight");
    private static final Set<String> HAND_TYPE_VALUES = Set.of("left", "right", "single");

    private static Map<String, String> transformProps(Map<String, String> props, Function<String, String> mapDir, boolean handSwap,
                                                      IntUnaryOperator rotationDelta, Map<String, String> railMap,
                                                      String name, boolean strict) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : props.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            switch (key) {
                case "facing" -> {
                    if (!isDir6(value)) throw new IllegalArgumentException(name + ": bad facing '" + value + "'");
                    out.put("facing", mapDir.apply(value));
                }
                case "axis" -> out.put("axis", mapAxis(value, mapDir));
                case "rotation" -> {
                    int r;
                    try {
                        r = Integer.parseInt(value);
                    } catch (NumberFormatException ex) {
                        r = -1;
                    }
                    if (r < 0 || r > 15) throw new IllegalArgumentException(name + ": bad rotation '" + value + "'");
                    out.put("rotation", Integer.toString(rotationDelta.applyAsInt(r)));
                }
                case "shape" -> {
                    if (STAIR_SHAPES.contains(value)) out.put("shape", handSwap ? SWAP_HAND.get(value) : value);
                    else if (RAIL_SHAPES.contains(value)) out.put("shape", railMap.get(value));
                    else throw new IllegalArgumentException(name + ": bad shape '" + value + "'");
                }
                case "hinge" -> out.put("hinge", handSwap ? SWAP_HAND.getOrDefault(value, value) : value);
                case "type" -> out.put("type", handSwap && HAND_TYPE_VALUES.contains(value) ? SWAP_HAND.get(value) : value);
                case "orientation" -> {
                    List<String> parts = new ArrayList<>();
                    for (String token : value.split("_")) {
                        if (!isDir6(token)) throw new IllegalArgumentException(name + ": bad orientation '" + value + "'");
                        parts.add(mapDir.apply(token));
                    }
                    out.put("orientation", String.join("_", parts));
                }
                case "north", "south", "east", "west" -> out.put(mapDir.apply(key), value);
                default -> {
                    if (!BlockstateData.INVARIANT_PROPS.contains(key) && strict) {
                        throw new IllegalArgumentException(name + ": property '" + key
                                + "' is not in the blockstate transform table; add it to Blockstates");
                    }
                    out.put(key, value);
                }
            }
        }
        return out;
    }

    private static String mapAxis(String axis, Function<String, String> mapDir) {
        if (axis.equals("y")) return "y";
        String mapped = mapDir.apply(axis.equals("x") ? "east" : "south");
        return mapped.equals("east") || mapped.equals("west") ? "x" : "z";
    }

    public static BlockState rotateState(BlockState state, double quarterTurns) {
        return rotateState(state, quarterTurns, true);
    }

    public static BlockState rotateState(BlockState state, double quarterTurns, boolean strict) {
        int turns = normaliseTurns(quarterTurns);
        if (turns == 0) return state;
        Map<String, String> props = state.props;
        for (int i = 0; i < turns; i++) {
            props = transformProps(props, Blockstates::cw, false, r -> (r + 4) % 16, RAIL_CW, state.name, strict);
        }
        return new BlockState(state.name, props);
    }

    public static BlockState mirrorState(BlockState state, Axis axis) {
        return mirrorState(state, axis, true);
    }

    public static BlockState mirrorState(BlockState state, Axis axis, boolean strict) {
        boolean x = axis == Axis.X;
        Function<String, String> mapDir = d -> Dir.of(d).mirror(axis).id();
        IntUnaryOperator delta = x ? r -> (16 - r) % 16 : r -> (24 - r) % 16;
        return new BlockState(state.name, transformProps(state.props, mapDir, true, delta, x ? RAIL_MIRROR_X : RAIL_MIRROR_Z, state.name, strict));
    }

    // ---- classification -----------------------------------------------------------------------

    private static final List<String> NON_FULL_SUFFIXES = List.of("_stairs", "_wall", "_fence", "_fence_gate", "_pane",
            "_slab", "_door", "_trapdoor", "_button", "_pressure_plate", "_sign", "_carpet", "_candle", "_bars");
    private static final Set<String> NON_FULL_EXACT = Set.of(
            "minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:water",
            "minecraft:lava", "minecraft:iron_bars", "minecraft:chain", "minecraft:ladder",
            "minecraft:vine", "minecraft:glow_lichen", "minecraft:moss_carpet", "minecraft:snow",
            "minecraft:torch", "minecraft:wall_torch", "minecraft:soul_torch", "minecraft:soul_wall_torch",
            "minecraft:lantern", "minecraft:soul_lantern", "minecraft:short_grass", "minecraft:tall_grass",
            "minecraft:fern", "minecraft:large_fern", "minecraft:dead_bush", "minecraft:cobweb",
            "minecraft:scaffolding", "minecraft:end_rod", "minecraft:lightning_rod", "minecraft:campfire",
            "minecraft:soul_campfire", "minecraft:pointed_dripstone", "minecraft:hanging_roots",
            "minecraft:structure_void");
    private static final Set<String> GLASSY = Set.of("minecraft:glass", "minecraft:tinted_glass");

    public static boolean isAir(BlockState s) {
        return s.name.equals("minecraft:air") || s.name.equals("minecraft:cave_air") || s.name.equals("minecraft:void_air");
    }

    public static boolean isStairs(BlockState s) {
        return s.name.endsWith("_stairs");
    }

    public static boolean isSlab(BlockState s) {
        return s.name.endsWith("_slab");
    }

    public static boolean isWall(BlockState s) {
        return s.name.endsWith("_wall") && !s.name.endsWith("_wall_sign");
    }

    public static boolean isFence(BlockState s) {
        return s.name.endsWith("_fence");
    }

    public static boolean isFenceGate(BlockState s) {
        return s.name.endsWith("_fence_gate");
    }

    public static boolean isPane(BlockState s) {
        return s.name.endsWith("_pane");
    }

    public static boolean isBars(BlockState s) {
        return s.name.equals("minecraft:iron_bars");
    }

    public static boolean hasConnections(BlockState s) {
        return isWall(s) || isFence(s) || isPane(s) || isBars(s);
    }

    public static boolean isFullCube(BlockState s) {
        if (isAir(s)) return false;
        if (NON_FULL_EXACT.contains(s.name)) return false;
        if (isSlab(s)) return "double".equals(s.prop("type"));
        for (String suffix : NON_FULL_SUFFIXES) if (s.name.endsWith(suffix)) return false;
        return true;
    }

    // ---- neighbour-dependent shape ------------------------------------------------------------

    /** Reads the state at an offset from the block being normalised. */
    public interface StateLookup {
        BlockState at(int dx, int dy, int dz);
    }

    private static BlockState look(StateLookup lookup, String dir) {
        Dir d = Dir.of(dir);
        return lookup.at(d.dx, d.dy, d.dz);
    }

    private static boolean sameAxis(String a, String b) {
        return (a.equals("north") || a.equals("south")) == (b.equals("north") || b.equals("south"));
    }

    public static String stairShape(BlockState state, StateLookup lookup) {
        String facing = state.prop("facing");
        String half = state.prop("half");
        if (!isHorizontal(facing)) throw new IllegalArgumentException("stairShape: '" + state.name + "' has no horizontal facing");
        BlockState front = look(lookup, facing);
        if (isStairs(front) && java.util.Objects.equals(front.prop("half"), half)) {
            String ff = front.prop("facing");
            if (isHorizontal(ff) && !sameAxis(ff, facing) && canTakeShape(state, lookup, opposite(ff))) {
                return ff.equals(counterClockWise(facing)) ? "outer_left" : "outer_right";
            }
        }
        BlockState back = look(lookup, opposite(facing));
        if (isStairs(back) && java.util.Objects.equals(back.prop("half"), half)) {
            String bf = back.prop("facing");
            if (isHorizontal(bf) && !sameAxis(bf, facing) && canTakeShape(state, lookup, bf)) {
                return bf.equals(counterClockWise(facing)) ? "inner_left" : "inner_right";
            }
        }
        return "straight";
    }

    private static boolean canTakeShape(BlockState state, StateLookup lookup, String dir) {
        BlockState n = look(lookup, dir);
        return !isStairs(n) || !java.util.Objects.equals(n.prop("facing"), state.prop("facing"))
                || !java.util.Objects.equals(n.prop("half"), state.prop("half"));
    }

    public static BlockState normaliseStair(BlockState state, StateLookup lookup) {
        String shape = stairShape(state, lookup);
        return shape.equals(state.prop("shape")) ? state : withProp(state, "shape", shape);
    }

    public static BlockState connectionState(BlockState state, StateLookup lookup) {
        if (isWall(state)) return normaliseWall(state, lookup);
        if (isFence(state) || isPane(state) || isBars(state)) return normaliseThinConnector(state, lookup);
        return state;
    }

    private static final List<String> CONNECTION_KEYS = List.of("north", "east", "south", "west");

    private static boolean wallConnectsTo(BlockState n, String from) {
        if (isWall(n) || isFullCube(n)) return true;
        if (isFenceGate(n)) {
            String f = n.prop("facing");
            return isHorizontal(f) && !sameAxis(f, from);
        }
        return false;
    }

    private static BlockState normaliseWall(BlockState state, StateLookup lookup) {
        BlockState above = look(lookup, "up");
        boolean raised = isFullCube(above) || isWall(above);
        Map<String, String> props = new HashMap<>(state.props);
        int connections = 0;
        Map<String, Boolean> connected = new HashMap<>();
        for (String dir : CONNECTION_KEYS) {
            boolean ok = wallConnectsTo(look(lookup, dir), dir);
            connected.put(dir, ok);
            props.put(dir, ok ? (raised ? "tall" : "low") : "none");
            if (ok) connections++;
        }
        boolean straightRun = connections == 2 && ((connected.get("north") && connected.get("south"))
                || (connected.get("east") && connected.get("west")));
        props.put("up", raised || !straightRun ? "true" : "false");
        return new BlockState(state.name, props);
    }

    private static boolean thinConnectsTo(BlockState state, BlockState n, String from) {
        if (isAir(n)) return false;
        if (isFullCube(n)) return true;
        if (isFenceGate(n)) {
            String f = n.prop("facing");
            return isHorizontal(f) && !sameAxis(f, from);
        }
        if (isFence(state)) return isFence(n) && woodLike(state) == woodLike(n);
        if (isPane(state) || isBars(state)) return isPane(n) || isBars(n) || GLASSY.contains(n.name);
        return false;
    }

    private static boolean woodLike(BlockState s) {
        return !s.name.equals("minecraft:nether_brick_fence");
    }

    private static BlockState normaliseThinConnector(BlockState state, StateLookup lookup) {
        Map<String, String> props = new HashMap<>(state.props);
        for (String dir : CONNECTION_KEYS) props.put(dir, thinConnectsTo(state, look(lookup, dir), dir) ? "true" : "false");
        return new BlockState(state.name, props);
    }

    // ---- multiface ----------------------------------------------------------------------------

    private static final Map<String, List<String>> MULTIFACE_FACES = Map.of(
            "minecraft:vine", List.of("north", "east", "south", "west", "up"),
            "minecraft:glow_lichen", List.of("north", "east", "south", "west", "up", "down"));

    private static boolean isSturdyFace(BlockState s) {
        return isFullCube(s) || GLASSY.contains(s.name) || s.name.endsWith("_stained_glass");
    }

    public static boolean isMultiface(BlockState s) {
        return MULTIFACE_FACES.containsKey(s.name);
    }

    public static BlockState multifaceState(BlockState state, StateLookup lookup) {
        List<String> faces = MULTIFACE_FACES.get(state.name);
        if (faces == null) return state;
        Map<String, String> props = new HashMap<>(state.props);
        for (String dir : faces) {
            boolean attached = isSturdyFace(look(lookup, dir));
            if (!attached && !dir.equals("up") && !dir.equals("down")) {
                BlockState above = look(lookup, "up");
                attached = above.name.equals(state.name) && "true".equals(above.prop(dir));
            }
            props.put(dir, attached ? "true" : "false");
        }
        return new BlockState(state.name, props);
    }

    public static boolean multifaceAttached(BlockState s) {
        List<String> faces = MULTIFACE_FACES.get(s.name);
        if (faces == null) return true;
        for (String d : faces) if ("true".equals(s.prop(d))) return true;
        return false;
    }

    // ---- waterlogging -------------------------------------------------------------------------

    private static final List<String> WATERLOGGABLE_SUFFIXES = List.of("_stairs", "_slab", "_wall", "_fence",
            "_fence_gate", "_pane", "_trapdoor", "_sign", "_hanging_sign", "_candle");
    private static final Set<String> WATERLOGGABLE_EXACT = Set.of(
            "minecraft:iron_bars", "minecraft:chain", "minecraft:ladder", "minecraft:lantern",
            "minecraft:soul_lantern", "minecraft:scaffolding", "minecraft:conduit", "minecraft:campfire",
            "minecraft:soul_campfire", "minecraft:sea_pickle", "minecraft:end_rod",
            "minecraft:lightning_rod", "minecraft:pointed_dripstone", "minecraft:amethyst_cluster",
            "minecraft:hanging_roots", "minecraft:mangrove_roots", "minecraft:light",
            "minecraft:decorated_pot", "minecraft:glow_lichen");

    public static boolean isWaterloggable(BlockState s) {
        if (isAir(s)) return false;
        if (s.name.equals("minecraft:nether_portal")) return false;
        if (WATERLOGGABLE_EXACT.contains(s.name)) return true;
        for (String suffix : WATERLOGGABLE_SUFFIXES) if (s.name.endsWith(suffix)) return true;
        return false;
    }

    public static BlockState waterlog(BlockState s) {
        if (!isWaterloggable(s)) return s;
        if ("true".equals(s.prop("waterlogged"))) return s;
        return withProp(s, "waterlogged", "true");
    }

    // ---- defaults -----------------------------------------------------------------------------

    /** The properties Minecraft would fill in for a bare block id. */
    public static Map<String, String> defaultsFor(String name) {
        if (name.endsWith("_stairs")) return pairs("facing", "north", "half", "bottom", "shape", "straight", "waterlogged", "false");
        if (name.endsWith("_slab")) return pairs("type", "bottom", "waterlogged", "false");
        if (name.endsWith("_wall")) return pairs("north", "none", "east", "none", "south", "none", "west", "none", "up", "true", "waterlogged", "false");
        if (name.endsWith("_fence")) return pairs("north", "false", "east", "false", "south", "false", "west", "false", "waterlogged", "false");
        if (name.endsWith("_pane") || name.equals("minecraft:iron_bars")) {
            return pairs("north", "false", "east", "false", "south", "false", "west", "false", "waterlogged", "false");
        }
        if (name.endsWith("_trapdoor")) return pairs("facing", "north", "half", "bottom", "open", "false", "powered", "false", "waterlogged", "false");
        if (name.endsWith("_log") || name.endsWith("_wood") || name.equals("minecraft:polished_basalt") || name.equals("minecraft:hay_block")) {
            return pairs("axis", "y");
        }
        if (name.equals("minecraft:lantern") || name.equals("minecraft:soul_lantern")) return pairs("hanging", "false", "waterlogged", "false");
        if (name.equals("minecraft:scaffolding")) return pairs("distance", "0", "waterlogged", "false", "bottom", "false");
        if (name.equals("minecraft:composter")) return pairs("level", "0");
        if (name.equals("minecraft:ladder")) return pairs("facing", "north", "waterlogged", "false");
        if (name.equals("minecraft:grass_block") || name.equals("minecraft:podzol") || name.equals("minecraft:mycelium")) return pairs("snowy", "false");
        if (name.equals("minecraft:vine")) return pairs("north", "false", "east", "false", "south", "false", "west", "false", "up", "false");
        if (name.equals("minecraft:glow_lichen")) {
            return pairs("north", "false", "east", "false", "south", "false", "west", "false", "up", "false", "down", "false", "waterlogged", "false");
        }
        if (name.equals("minecraft:chain")) return pairs("axis", "y", "waterlogged", "false");
        if (name.equals("minecraft:sugar_cane")) return pairs("age", "0");
        if (name.equals("minecraft:water") || name.equals("minecraft:lava")) return pairs("level", "0");
        if (name.equals("minecraft:moss_carpet") || name.endsWith("_carpet")) return pairs();
        if (name.equals("minecraft:oak_leaves") || name.endsWith("_leaves")) return pairs("distance", "7", "persistent", "true", "waterlogged", "false");
        return pairs();
    }

    /** {@code makeState}: the full id with Minecraft's defaults, overridden by {@code props}. */
    public static BlockState makeState(String name, Map<String, String> props) {
        String f = full(name);
        Map<String, String> p = defaultsFor(f);
        p.putAll(props);
        return new BlockState(f, p);
    }

    public static BlockState makeState(String name) {
        return makeState(name, Map.of());
    }

    /** Every property outside its legal domain, as messages; empty when the state is legal. */
    public static List<String> domainViolations(BlockState s) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : s.props.entrySet()) {
            List<String> domain = BlockstateData.PROPERTY_DOMAINS.get(e.getKey());
            if (domain == null) out.add(s.name + ": property '" + e.getKey() + "' is not a known blockstate property");
            else if (!domain.contains(e.getValue())) {
                out.add(s.name + ": " + e.getKey() + "=" + e.getValue() + " is outside its legal domain (" + String.join("|", domain) + ")");
            }
        }
        return out;
    }
}
