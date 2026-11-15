package io.github.tjhgibbs.capella.core.preview;

import io.github.tjhgibbs.capella.core.BuildingGenerator;
import io.github.tjhgibbs.capella.core.params.*;
import io.github.tjhgibbs.capella.core.voxel.BlockVolume;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Reproducible close and cutaway views of finished blocks, plus every bridge heading. */
public final class SentryBridgeReviewDump {
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args.length>0?args[0]:"sentry-bridge-review"); Files.createDirectories(out);
        for(int seed:new int[]{1,7,19}) {
            BlockVolume v=BuildingGenerator.generate(Presets.get("sentrytower").params(seed)).volume();
            BufferedImage sheet=sheet(1500,1080); Graphics2D g=sheet.createGraphics();
            String[] labels={"Complete / seed "+seed,"Crown and corbels","Loop, sill and hood",
                    "Half tower / floors and ladder","Two-course loop reveal","Entrance / level threshold"};
            BlockVolume[] views={v,crop(v,-2,11,12,20,-6,6),crop(v,2,8,3,9,1,7),
                    crop(v,0,11,-1,20,-5,2),crop(v,3,7,3,9,1,6),crop(v,-3,4,-1,6,-3,4)};
            for(int i=0;i<6;i++) panel(g,views[i],i%3*500,i/3*520,500,480,labels[i],i==3?135:i==5||i==0?145:35,25);
            g.setColor(Color.DARK_GRAY);g.drawString("Offline block geometry: cutaways omit blocks; these are not Minecraft screenshots.",15,1060);
            g.dispose(); ImageIO.write(sheet,"png",out.resolve("sentry-seed"+seed+".png").toFile());
        }
        for(String id:new String[]{"suspensionbridge","gothicbridge","stoneviaduct","timbertrestle"}) {
            BufferedImage sheet=sheet(1600,1440); Graphics2D g=sheet.createGraphics();
            for(int heading=0;heading<16;heading++) {
                var p=Presets.get(id).params(7).set(Params.BRIDGE_HEADING,heading);
                panel(g,BuildingGenerator.generate(p).volume(),heading%4*400,heading/4*360,400,350,
                        id+" / "+heading*22.5+"°",35,22);
            }
            g.dispose(); ImageIO.write(sheet,"png",out.resolve(id+"-headings.png").toFile());
            var v=BuildingGenerator.generate(Presets.get(id).params(7)).volume();
            BufferedImage detail=sheet(1600,650);g=detail.createGraphics();
            panel(g,v,0,0,800,620,id+" / complete",35,15);
            panel(g,crop(v,-1,70,-33,34,-1,12),800,0,800,620,"Longitudinal cutaway / near half removed",35,10);
            g.dispose();ImageIO.write(detail,"png",out.resolve(id+"-section.png").toFile());
        }
    }
    private static BufferedImage sheet(int w,int h) {
        var im=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);var g=im.createGraphics();
        g.setColor(new Color(0xe9e6df));g.fillRect(0,0,w,h);g.dispose();return im;
    }
    private static void panel(Graphics2D g,BlockVolume v,int x,int y,int w,int h,String label,double yaw,double pitch) {
        int[] pixels=PreviewRenderer.render(v,new PreviewRenderer.View(w,h-30,Math.toRadians(yaw),Math.toRadians(pitch),1,0,0,false),BlockColors::rgb);
        var im=new BufferedImage(w,h-30,BufferedImage.TYPE_INT_ARGB);im.setRGB(0,0,w,h-30,pixels,0,w);
        g.setColor(Color.DARK_GRAY);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,16));g.drawString(label,x+12,y+22);g.drawImage(im,x,y+30,null);
    }
    private static BlockVolume crop(BlockVolume v,int x0,int x1,int y0,int y1,int z0,int z1) {
        // Tight dimensions ensure the renderer zooms to the retained blocks.
        x0=Math.max(x0,-v.originX);x1=Math.min(x1,v.width-v.originX);
        y0=Math.max(y0,-v.originY);y1=Math.min(y1,v.height-v.originY);
        z0=Math.max(z0,-v.originZ);z1=Math.min(z1,v.length-v.originZ);
        var out=new BlockVolume(x1-x0,y1-y0,z1-z0,-x0,-y0,-z0);
        for(int x=x0;x<x1;x++) for(int y=y0;y<y1;y++) for(int z=z0;z<z1;z++)
            out.set(x-x0,y-y0,z-z0,v.get(x+v.originX,y+v.originY,z+v.originZ));
        return out;
    }
}
