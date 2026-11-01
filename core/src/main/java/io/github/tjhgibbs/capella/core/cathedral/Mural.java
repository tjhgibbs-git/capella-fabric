package io.github.tjhgibbs.capella.core.cathedral;

import io.github.tjhgibbs.capella.core.element.ElementKind;
import io.github.tjhgibbs.capella.core.element.LevelName;
import io.github.tjhgibbs.capella.core.geom.Box;
import io.github.tjhgibbs.capella.core.kit.AddOptions;
import io.github.tjhgibbs.capella.core.kit.Band;
import io.github.tjhgibbs.capella.core.kit.Parts;
import io.github.tjhgibbs.capella.core.kit.ShellBuilder;
import io.github.tjhgibbs.capella.core.kit.WallWalk;
import io.github.tjhgibbs.capella.core.palette.MaterialRole;

import java.util.ArrayList;
import java.util.List;

/**
 * The mural passage in the clerestory wall, and the stairs that reach it and (where the aisle
 * is leaded, not roofed) the aisle parapet from the triforium (Capella {@code cathedral/mural.ts}).
 */
public final class Mural {
    private Mural() {
    }

    public static void buildMuralPassage(ShellBuilder s, PlanLayout layout, String side) {
        Levels L = layout.levels;
        if (!L.hasMuralPassage) return;
        SideBands bands = side.equals("north") ? layout.north : layout.south;
        if (bands.arcades.isEmpty()) return;
        Band band = bands.arcades.get(0);
        List<WallWalk.WalkBay> bays = new ArrayList<>();
        for (Bay b : layout.bays) bays.add(new WallWalk.WalkBay(b.index, b.x0, b.x1));
        WallWalk.cutWallWalk(s, new WallWalk.WallWalkRequest("mural-" + side, bays, layout.westX, layout.eastX,
                side.equals("north") ? band.z1() : band.z0(), side.equals("north") ? -1 : 1, band.z1() - band.z0(),
                L.muralFloorY, L.muralHeadY, LevelName.MURAL,
                bay -> "wall/clerestory-" + side + "/bay-" + bay.index, List.of("wall/clerestory-" + side + "/bay-1")));
    }

    public static void buildGalleryStairs(ShellBuilder s, PlanLayout layout, String side) {
        Levels L = layout.levels;
        SideBands bands = side.equals("north") ? layout.north : layout.south;
        if (bands.aisles.isEmpty()) return;
        Bay bay = stairBay(layout);
        if (bay == null) return;
        Band inner = bands.arcades.get(0);
        Band outer = bands.outerWall;
        double foot = bay.x0 + 1;

        // Do not build a flight or punch a doorway unless a real passage can receive it.
        double muralRise = L.hasMuralPassage && inner.z1() - inner.z0() - WallWalk.OUTER_SKIN >= WallWalk.MIN_PASSAGE_WIDTH
                && L.muralFloorY - L.triforiumDeckY <= bay.span - 2 ? L.muralFloorY - L.triforiumDeckY : 0;
        WallWalk.wallWalkFlight(s, "stair/mural-" + side, foot, muralRise, L.triforiumDeckY,
                side.equals("north") ? inner.z0() - 3 : inner.z1(), 3, LevelName.MURAL,
                List.of("wall/clerestory-" + side + "/bay-" + bay.index));
        if (muralRise >= 1) {
            double at = foot + muralRise - 1;
            s.add(new AddOptions("aperture/mural-" + side + "-door", ElementKind.APERTURE, MaterialRole.MASONRY_MAIN, LevelName.MURAL,
                    Parts.solid(Box.of(at, L.muralFloorY, inner.z0(), at + 2, L.muralFloorY + 3, inner.z1())))
                    .parent("wall/clerestory-" + side + "/bay-" + bay.index).param("symmetry", "x"));
        }

        // Up to the parapet over the leads, unless the aisle is roofed at the sill: then it
        // would climb into the underside of the roof.
        if (L.aisleWallTopY - L.triforiumDeckY >= CathedralPlan.MIN_STOREY_CLEAR + 1) return;
        WallWalk.wallWalkFlight(s, "stair/parapet-" + side, foot, L.aisleWallTopY - L.triforiumDeckY, L.triforiumDeckY,
                side.equals("north") ? outer.z1() : outer.z0() - 3, 3, LevelName.ROOF,
                List.of("wall/aisle-" + side + "/bay-" + bay.index));
    }

    private static Bay stairBay(PlanLayout layout) {
        double towerEastX = layout.towerSize > 0 ? layout.westX - layout.wallThickness + layout.towerSize : layout.westX;
        for (Bay b : layout.bays) {
            if (b.x0 >= towerEastX && !b.isCrossing && b.span >= 6 && b.kind != Bay.Kind.TOWER) return b;
        }
        return null;
    }
}
