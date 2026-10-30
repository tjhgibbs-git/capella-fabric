package io.github.tjhgibbs.capella.core.preview;

import io.github.tjhgibbs.capella.core.GeneratedBuilding;
import io.github.tjhgibbs.capella.core.BuildingGenerator;
import io.github.tjhgibbs.capella.core.params.Presets;
import io.github.tjhgibbs.capella.core.params.BuildingParams;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dev tool: renders presets to PNG (a bird's-eye view and the plan, side by side) so buildings
 * can be reviewed without the game. Usage: {@code PreviewDump <outDir> <preset|all> [seed]}.
 * The preset argument may also be an encoded param string containing '='.
 */
public final class PreviewDump {
    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "previews");
        String which = args.length > 1 ? args[1] : "all";
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 1;
        Files.createDirectories(out);
        if (which.contains("=")) {
            dump(out, "custom", BuildingParams.decode(which), 900);
            return;
        }
        for (Presets.Preset preset : Presets.all()) {
            if (!which.equals("all") && !which.equals(preset.id())) {
                continue;
            }
            BuildingParams p = preset.params(seed);
            dump(out, preset.id(), p, 900);
        }
    }

    static void dump(Path out, String name, BuildingParams p, int size) throws IOException {
        GeneratedBuilding tree = BuildingGenerator.generate(p);
        System.out.println(name + ": " + tree.summary());
        BufferedImage img = new BufferedImage(size * 2, size, BufferedImage.TYPE_INT_ARGB);
        for (int view = 0; view < 2; view++) {
            int[] px = PreviewRenderer.render(tree.volume(),
                    new PreviewRenderer.View(size, size, Math.toRadians(35), Math.toRadians(view == 0 ? 30 : 80), 1.0, 0, 0, false),
                    BlockColors::rgb);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int c = px[y * size + x];
                    if ((c >>> 24) == 0) {
                        // Sky gradient behind the building.
                        double t = y / (double) size;
                        int r = (int) (150 + 60 * t), g = (int) (190 + 40 * t), b = (int) (235 + 15 * t);
                        c = 0xFF000000 | r << 16 | g << 8 | b;
                    }
                    img.setRGB(x + view * size, y, c);
                }
            }
        }
        ImageIO.write(img, "png", new File(out.toFile(), name + ".png"));
    }
}
