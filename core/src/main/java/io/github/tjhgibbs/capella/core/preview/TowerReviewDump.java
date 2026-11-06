package io.github.tjhgibbs.capella.core.preview;

import io.github.tjhgibbs.capella.core.BuildingGenerator;
import io.github.tjhgibbs.capella.core.params.*;
import io.github.tjhgibbs.capella.core.small.TowerInterior;
import io.github.tjhgibbs.capella.core.voxel.BlockVolume;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Removes the exterior from the finished generated blocks, rather than rendering a separate mock interior.
 * Writes two views of each exposed floor stack and individual roofless floor plans. */
public final class TowerReviewDump {
    private TowerReviewDump() {}

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "tower-review");
        String names = args.length > 1 ? args[1] : "squarekeep,roundwatchtower,gothicturret";
        String seeds = args.length > 2 ? args[2] : "1,7,19";
        Files.createDirectories(out);
        StringBuilder csv = new StringBuilder("preset,seed,floor,feet_y,clear_height\n");
        for (String name : names.split(",")) for (String rawSeed : seeds.split(",")) {
            BuildingParams p = Presets.get(name).params(Long.parseLong(rawSeed));
            if (!p.get(Params.TYPE).fortification() || p.get(Params.TYPE).wall()) throw new IllegalArgumentException("Castle tower required");
            BlockVolume full = BuildingGenerator.generate(p).volume();
            TowerInterior.Plan plan = TowerInterior.plan(p);
            BlockVolume stripped = strip(full, p, -1, p.integer(Params.HEIGHT) + 2);
            BufferedImage sheet = new BufferedImage(1600, 900, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = sheet.createGraphics();
            g.setColor(new Color(0xe9e6df)); g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            g.setColor(Color.DARK_GRAY); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
            g.drawString(name + " / seed " + rawSeed + " / stairs " + plan.stairWidth() + " wide / floor feet " + plan.floors(), 20, 28);
            for (int view = 0; view < 2; view++) {
                BufferedImage im = render(stripped, 780, 790, 35 + view * 180, 22);
                g.drawImage(im, 10 + 800 * view, 55, null);
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            g.drawString("Exterior and roof removed from finished blocks. Offline geometry, not Minecraft textures. Stairs climb clockwise; ladders provide a second route.", 20, 880);
            g.dispose();
            String prefix = name + "-seed" + rawSeed;
            ImageIO.write(sheet, "png", out.resolve(prefix + "-cutaway.png").toFile());
            int cols = 4, cell = 320, rows = (plan.floors().size() + cols - 1) / cols;
            BufferedImage floors = new BufferedImage(cols * cell, rows * (cell + 45), BufferedImage.TYPE_INT_RGB);
            g = floors.createGraphics(); g.setColor(new Color(0xe9e6df)); g.fillRect(0, 0, floors.getWidth(), floors.getHeight());
            for (int i = 0; i < plan.floors().size(); i++) {
                int feet = plan.floors().get(i), upper = i + 1 < plan.floors().size() ? plan.floors().get(i + 1) : feet + 4;
                csv.append(name).append(',').append(rawSeed).append(',').append(i).append(',').append(feet).append(',').append(upper - feet).append('\n');
                BlockVolume floor = strip(full, p, feet - 1, upper - 2);
                BufferedImage im = render(floor, cell, cell, 0, 78);
                int x = i % cols * cell, y = i / cols * (cell + 45);
                g.drawImage(im, x, y + 35, null); g.setColor(Color.DARK_GRAY);
                g.drawString("Floor " + i + ": feet " + feet + ", next +" + (upper - feet), x + 12, y + 22);
            }
            g.dispose(); ImageIO.write(floors, "png", out.resolve(prefix + "-floors.png").toFile());
            System.out.println(prefix + " floors " + plan.floors() + ", " + plan.route().size() + " stair/landing nodes");
        }
        Files.writeString(out.resolve("floor-heights.csv"), csv);
    }

    private static BlockVolume strip(BlockVolume full, BuildingParams p, int loY, int hiY) {
        int w = p.integer(Params.WIDTH), z0 = -w / 2;
        BlockVolume out = new BlockVolume(w + 6, hiY - loY + 1, w + 6, 3, -loY, 3 - z0);
        for (int x = -3; x < w + 3; x++) for (int z = z0 - 3; z < z0 + w + 3; z++) {
            boolean interior = TowerInterior.interiorCell(p, x, z);
            boolean door = doorway(p, x, z);
            if (!interior && !door) continue;
            for (int y = loY; y <= hiY; y++) {
                // Retain doorway thresholds while removing the masonry above them.
                if (!interior && !TowerInterior.floorLevels(p).contains(y + 1)) continue;
                int fx = x + full.originX, fy = y + full.originY, fz = z + full.originZ;
                if (fx >= 0 && fx < full.width && fy >= 0 && fy < full.height && fz >= 0 && fz < full.length)
                    out.set(x + 3, y - loY, z - z0 + 3, full.get(fx, fy, fz));
            }
        }
        return out;
    }

    private static boolean doorway(BuildingParams p, int x, int z) {
        if (p.get(Params.TYPE) != Params.BuildingType.SQUARE_KEEP) return false;
        int w = p.integer(Params.WIDTH), z0 = -w / 2;
        for (int cx : new int[]{1, w - 2}) for (int cz : new int[]{z0 + 1, z0 + w - 2}) {
            int ex = cx < w / 2 ? 4 : w - 5, ez = cz < 0 ? z0 + 4 : z0 + w - 5;
            if (z == cz && x >= Math.min(cx, ex) && x <= Math.max(cx, ex)
                    || x == ex && z >= Math.min(cz, ez) && z <= Math.max(cz, ez)) return true;
        }
        return false;
    }

    private static BufferedImage render(BlockVolume v, int w, int h, double yaw, double pitch) {
        int[] pixels = PreviewRenderer.render(v, new PreviewRenderer.View(w, h, Math.toRadians(yaw), Math.toRadians(pitch), 1, 0, 0, false), BlockColors::rgb);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, pixels, 0, w);
        return out;
    }
}
