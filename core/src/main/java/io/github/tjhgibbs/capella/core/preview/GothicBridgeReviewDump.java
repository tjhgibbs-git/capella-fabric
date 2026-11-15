package io.github.tjhgibbs.capella.core.preview;

import io.github.tjhgibbs.capella.core.BuildingGenerator;
import io.github.tjhgibbs.capella.core.params.*;
import io.github.tjhgibbs.capella.core.voxel.BlockVolume;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Offline geometry sheets of the Gothic bridge presets, arch forms and tier/walkway options. */
public final class GothicBridgeReviewDump {
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "gothic-bridge-review");
        Files.createDirectories(out);
        String[] ids = {"gothicbridge", "fortifiedbridge", "arcadedviaduct", "townbridge", "tudorfootbridge"};
        BufferedImage sheet = sheet(1600, 1500);
        Graphics2D g = sheet.createGraphics();
        for (int i = 0; i < ids.length; i++) {
            var v = BuildingGenerator.generate(Presets.get(ids[i]).params(7)).volume();
            panel(g, v, i % 2 * 800, i / 2 * 500, 800, 490, ids[i], 25, 12);
        }
        g.dispose();
        ImageIO.write(sheet, "png", out.resolve("gothic-presets.png").toFile());

        BufferedImage forms = sheet(1600, 900);
        g = forms.createGraphics();
        int i = 0;
        for (var arch : Params.BridgeArch.values()) {
            var p = Presets.get("gothicbridge").params(7).set(Params.BRIDGE_ARCH, arch);
            panel(g, BuildingGenerator.generate(p).volume(), i % 2 * 800, i / 2 * 450, 800, 440, "arch " + arch, 5, 3);
            i++;
        }
        g.dispose();
        ImageIO.write(forms, "png", out.resolve("gothic-arch-forms.png").toFile());

        BufferedImage opts = sheet(1600, 1350);
        g = opts.createGraphics();
        i = 0;
        for (var c : Params.Cutwaters.values()) for (var par : new Params.BridgeParapet[]{Params.BridgeParapet.SOLID, Params.BridgeParapet.PIERCED}) {
            var p = Presets.get("gothicbridge").params(7).set(Params.CUTWATERS, c).set(Params.BRIDGE_PARAPET, par).set(Params.LENGTH, 33);
            panel(g, BuildingGenerator.generate(p).volume(), i % 2 * 800, i / 2 * 450, 800, 440, c + " / " + par, 30, 25);
            i++;
        }
        g.dispose();
        ImageIO.write(opts, "png", out.resolve("gothic-options.png").toFile());

        var viaduct = BuildingGenerator.generate(Presets.get("arcadedviaduct").params(7)).volume();
        BufferedImage det = sheet(1600, 650);
        g = det.createGraphics();
        panel(g, viaduct, 0, 0, 800, 620, "arcadedviaduct / turret", 140, 15);
        panel(g, crop(viaduct, -2, 90, -45, 20, -20, 1), 800, 0, 800, 620, "Cutaway / near half removed", 20, 10);
        g.dispose();
        ImageIO.write(det, "png", out.resolve("gothic-viaduct-detail.png").toFile());
    }

    private static BufferedImage sheet(int w, int h) {
        var im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = im.createGraphics();
        g.setColor(new Color(0xe9e6df));
        g.fillRect(0, 0, w, h);
        g.dispose();
        return im;
    }

    private static void panel(Graphics2D g, BlockVolume v, int x, int y, int w, int h, String label, double yaw, double pitch) {
        int[] pixels = PreviewRenderer.render(v, new PreviewRenderer.View(w, h - 30, Math.toRadians(yaw), Math.toRadians(pitch), 1, 0, 0, false), BlockColors::rgb);
        var im = new BufferedImage(w, h - 30, BufferedImage.TYPE_INT_ARGB);
        im.setRGB(0, 0, w, h - 30, pixels, 0, w);
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        g.drawString(label, x + 12, y + 22);
        g.drawImage(im, x, y + 30, null);
    }

    private static BlockVolume crop(BlockVolume v, int x0, int x1, int y0, int y1, int z0, int z1) {
        x0 = Math.max(x0, -v.originX); x1 = Math.min(x1, v.width - v.originX);
        y0 = Math.max(y0, -v.originY); y1 = Math.min(y1, v.height - v.originY);
        z0 = Math.max(z0, -v.originZ); z1 = Math.min(z1, v.length - v.originZ);
        var out = new BlockVolume(x1 - x0, y1 - y0, z1 - z0, -x0, -y0, -z0);
        for (int x = x0; x < x1; x++) for (int y = y0; y < y1; y++) for (int z = z0; z < z1; z++)
            out.set(x - x0, y - y0, z - z0, v.get(x + v.originX, y + v.originY, z + v.originZ));
        return out;
    }
}
