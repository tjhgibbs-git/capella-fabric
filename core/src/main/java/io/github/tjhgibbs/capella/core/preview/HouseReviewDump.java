package io.github.tjhgibbs.capella.core.preview;

import io.github.tjhgibbs.capella.core.BuildingGenerator;
import io.github.tjhgibbs.capella.core.params.Presets;
import io.github.tjhgibbs.capella.core.params.Params;
import io.github.tjhgibbs.capella.core.voxel.BlockVolume;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Contact sheet of generated blocks. Floor sections retain two courses above each walking surface. */
public final class HouseReviewDump {
    private static final int W = 480, H = 360;

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        var sheet = new BufferedImage(3 * W, 3 * H, BufferedImage.TYPE_INT_RGB);
        var g = sheet.createGraphics();
        g.setColor(new Color(0xf3efe7));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        int row = 0;
        for (String id : new String[]{"townhouse", "cottage", "farmhouse"}) {
            var preset = Presets.get(id);
            var volume = BuildingGenerator.generate(preset.params(7)).volume();
            panel(g, volume, 0, row, preset.name(), 135, 19);
            panel(g, section(volume, -1, 1), 1, row, "Ground floor", 96, 65);
            boolean single = id.equals("cottage");
            panel(g, single ? volume : section(volume, 4, 6), 2, row,
                    single ? "Garden side" : "Upper floor", single ? -45 : 96, single ? 22 : 65);
            row++;
        }
        g.dispose();
        ImageIO.write(sheet, "png", out.resolve("houses.png").toFile());
        var options = new BufferedImage(4 * W, 3 * H, BufferedImage.TYPE_INT_RGB);
        var graphics = options.createGraphics();
        graphics.setColor(new Color(0xf3efe7));
        graphics.fillRect(0, 0, options.getWidth(), options.getHeight());
        for (var layout : Params.HouseLayout.values()) for (var roof : Params.HouseRoof.values()) {
            var params = Presets.get("cottage").params(7).set(Params.HOUSE_LAYOUT, layout).set(Params.HOUSE_ROOF, roof);
            String label = switch (layout) {
                case ORIGINAL -> "Original";
                case REAR_ELL -> "L-shaped wing";
                case MANOR -> "Bay-fronted manor";
            };
            panel(graphics, BuildingGenerator.generate(params).volume(), roof.ordinal(), layout.ordinal(),
                    label + " / " + Params.HOUSE_ROOF.format(roof), 135, 19);
        }
        graphics.dispose();
        ImageIO.write(options, "png", out.resolve("house-options.png").toFile());
    }

    private static BlockVolume section(BlockVolume v, int bottom, int top) {
        var out = new BlockVolume(v.width, top - bottom + 1, v.length, v.originX, 0, v.originZ);
        for (int x = 0; x < v.width; x++) for (int z = 0; z < v.length; z++)
            for (int y = bottom; y <= top; y++)
                out.set(x, y - bottom, z, v.get(x, y + v.originY, z));
        return out;
    }

    private static void panel(Graphics2D g, BlockVolume v, int col, int row, String label, int yaw, int pitch) {
        var view = new PreviewRenderer.View(W, H - 30, Math.toRadians(yaw), Math.toRadians(pitch), 1, 0, 0, false);
        int[] pixels = PreviewRenderer.render(v, view, BlockColors::rgb);
        var image = new BufferedImage(W, H - 30, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, W, H - 30, pixels, 0, W);
        g.drawImage(image, col * W, row * H + 30, null);
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.drawString(label, col * W + 16, row * H + 25);
    }
}
