package io.github.tjhgibbs.capella.core.preview;

import io.github.tjhgibbs.capella.core.BuildingGenerator;
import io.github.tjhgibbs.capella.core.params.*;
import io.github.tjhgibbs.capella.core.voxel.BlockVolume;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Close exterior and layered sections of actual finished buttress/cornice joints. */
public final class WallJoinReviewDump {
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args.length>0?args[0]:"wall-join-review");
        Files.createDirectories(out);
        for(String preset:new String[]{"curtainwall","arcadedwall","plainwall"}) for(int h:new int[]{5,12,24,40}) {
            BuildingParams p=Presets.get(preset).params(7).set(Params.LENGTH,24).set(Params.HEIGHT,h);
            BlockVolume v=BuildingGenerator.generate(p).volume();
            int edge=-p.integer(Params.WIDTH)/2+p.integer(Params.WIDTH)-1;
            BufferedImage sheet=new BufferedImage(1800,1060,BufferedImage.TYPE_INT_RGB);
            Graphics2D g=sheet.createGraphics();g.setColor(new Color(0xe9e6df));g.fillRect(0,0,1800,1060);
            g.setColor(Color.DARK_GRAY);g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,20));
            g.drawString(preset+" / height "+h+" / seed 7 / buttress at x=8",20,28);
            String[] labels={"Complete buttress bay", "Close cornice join", "Centre section (x=8)",
                    "Shoulder section (x=7)", "Upper support layer (y=h-2)", "Coping layer (y=h-1)"};
            for(int panel=0;panel<6;panel++) {
                int x0=panel==2?8:panel==3?7:4,x1=panel==2?9:panel==3?8:13;
                int y0=panel==0?-1:panel==4?h-2:panel==5?h-1:Math.max(-1,h-8);
                int y1=panel>=4?y0+1:h+4;
                BlockVolume crop=crop(v,x0,x1,y0,y1,edge-2,edge+5);
                double yaw=panel>=4?0:panel==2||panel==3?55:145;
                int[] px=PreviewRenderer.render(crop,new PreviewRenderer.View(580,450,Math.toRadians(yaw),Math.toRadians(panel>=4?80:panel>=2?8:20),1,0,0,false),BlockColors::rgb);
                BufferedImage im=new BufferedImage(580,450,BufferedImage.TYPE_INT_ARGB);im.setRGB(0,0,580,450,px,0,580);
                int x=panel%3*600,y=panel/3*500+50;
                g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,18));g.drawString(labels[panel],x+12,y+20);g.drawImage(im,x+10,y+30,null);
            }
            g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));
            g.drawString("Sections cut from finished generated blocks; offline geometry, without Minecraft textures or lighting.",20,1040);
            g.dispose();ImageIO.write(sheet,"png",out.resolve(preset+"-height"+h+"-joins.png").toFile());
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
