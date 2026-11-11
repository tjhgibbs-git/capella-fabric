package io.github.tjhgibbs.capella.core.preview;

import io.github.tjhgibbs.capella.core.BuildingGenerator;
import io.github.tjhgibbs.capella.core.params.*;
import io.github.tjhgibbs.capella.core.small.TowerInterior;
import io.github.tjhgibbs.capella.core.voxel.BlockVolume;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Close review of finished keep blocks: exterior, peeled masonry, room side and horizontal slices.
 * All panels use the same crop coordinates and camera settings, including before/after comparisons. */
public final class KeepWindowReviewDump {
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "keep-window-review");
        Files.createDirectories(out);
        for (int width : new int[]{13, 19, 27, 35}) {
            BuildingParams p = Presets.get("squarekeep").params(7).set(Params.WIDTH, width)
                    .set(Params.HEIGHT, 32).set(Params.VARY_FLOORS, false);
            BlockVolume full = BuildingGenerator.generate(p).volume();
            int base = TowerInterior.floorLevels(p).get(1);
            int clear = TowerInterior.floorLevels(p).get(2) - base;
            String[] labels = {"Exterior + surround", "Outer face peeled: depth 1", "Two layers peeled: depth 2",
                    "Room side / full reveal", "Sill slice (plan)", "Opening slice (plan)"};
            BufferedImage sheet = new BufferedImage(1800, 1060, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = sheet.createGraphics();
            g.setColor(new Color(0xe9e6df)); g.fillRect(0,0,1800,1060);
            g.setColor(Color.DARK_GRAY); g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,20));
            g.drawString("Keep windows / width " + width + " / seed 7 / storey " + base + "–" + (base+clear),20,28);
            for (int panel=0;panel<6;panel++) {
                int loX = panel==1 ? 1 : panel==2 ? 2 : -2;
                int hiX = 5;
                int loY = panel==4 ? base+1 : panel==5 ? base+2 : base-1;
                int hiY = panel>=4 ? loY+1 : base+clear+2;
                BlockVolume crop = crop(full,loX,hiX,loY,hiY,-width/2-1,-width/2+width+1);
                double yaw = panel==3 ? 235 : panel>=4 ? 0 : 55;
                double pitch = panel>=4 ? 80 : 12;
                int[] px = PreviewRenderer.render(crop,new PreviewRenderer.View(580,450,Math.toRadians(yaw),Math.toRadians(pitch),1,0,0,false),BlockColors::rgb);
                BufferedImage im=new BufferedImage(580,450,BufferedImage.TYPE_INT_ARGB);
                im.setRGB(0,0,580,450,px,0,580);
                int x=panel%3*600,y=panel/3*500+50;
                g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,18));g.drawString(labels[panel],x+12,y+20);
                g.drawImage(im,x+10,y+30,null);
            }
            g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));
            g.drawString("Cut from generated blocks, including stairs/furnishings. Offline geometry; textures and in-game lighting are not shown.",20,1040);
            g.dispose();ImageIO.write(sheet,"png",out.resolve("keep-width"+width+"-layers.png").toFile());
        }
    }

    private static BlockVolume crop(BlockVolume v,int x0,int x1,int y0,int y1,int z0,int z1) {
        BlockVolume out=new BlockVolume(x1-x0,y1-y0,z1-z0,-x0,-y0,-z0);
        for(int x=x0;x<x1;x++) for(int y=y0;y<y1;y++) for(int z=z0;z<z1;z++) {
            int fx=x+v.originX,fy=y+v.originY,fz=z+v.originZ;
            if(fx>=0&&fx<v.width&&fy>=0&&fy<v.height&&fz>=0&&fz<v.length)
                out.set(x-x0,y-y0,z-z0,v.get(fx,fy,fz));
        }
        return out;
    }
}
