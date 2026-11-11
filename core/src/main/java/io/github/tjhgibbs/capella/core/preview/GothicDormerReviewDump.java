package io.github.tjhgibbs.capella.core.preview;

import io.github.tjhgibbs.capella.core.BuildingGenerator;
import io.github.tjhgibbs.capella.core.params.*;
import io.github.tjhgibbs.capella.core.voxel.BlockVolume;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Actual finished dormer geometry: glazing, peeled reveals, depth sections and whole-roof context. */
public final class GothicDormerReviewDump {
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args.length>0?args[0]:"gothic-dormer-review");Files.createDirectories(out);
        for(int width:new int[]{13,14,19,35}) {
            var p=Presets.get("gothicturret").params(7).set(Params.WIDTH,width).set(Params.HEIGHT,24);
            BlockVolume full=BuildingGenerator.generate(p).volume();int h=p.integer(Params.HEIGHT);
            BufferedImage sheet=new BufferedImage(1800,1060,BufferedImage.TYPE_INT_RGB);
            Graphics2D g=sheet.createGraphics();g.setColor(new Color(0xe9e6df));g.fillRect(0,0,1800,1060);
            g.setColor(Color.DARK_GRAY);g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,20));
            g.drawString("Gothic roof dormer / width "+width+" / seed 7",20,28);
            String[] labels={"Exterior / recessed glazing", "Front peeled: depth >= 1", "Behind glazing: depth >= 2",
                    "Section through side light", "Horizontal light-level slice", "Complete roof"};
            for(int panel=0;panel<6;panel++) {
                BlockVolume v;
                if(panel==5) v=crop(full,-4,width+4,h+2,h+width+10,-width/2-4,-width/2+width+4);
                else {
                    int x0=panel==1?1:panel==2?2:-2;
                    int y0=panel==4?h+6:h+2,y1=panel==4?h+7:h+15;
                    v=crop(full,x0,8,y0,y1,panel==3?-1:-5,panel==3?0:6);
                }
                double yaw=panel==4?0:55,pitch=panel==4?80:panel==3?5:25;
                int[] px=PreviewRenderer.render(v,new PreviewRenderer.View(580,450,Math.toRadians(yaw),Math.toRadians(pitch),1,0,0,false),BlockColors::rgb);
                BufferedImage im=new BufferedImage(580,450,BufferedImage.TYPE_INT_ARGB);im.setRGB(0,0,580,450,px,0,580);
                int x=panel%3*600,y=panel/3*500+50;
                g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,18));g.drawString(labels[panel],x+12,y+20);g.drawImage(im,x+10,y+30,null);
            }
            g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));g.drawString("Layered cutaways of generated blocks. Offline geometry; glass transparency, textures and game lighting need client verification.",20,1040);
            g.dispose();ImageIO.write(sheet,"png",out.resolve("gothic-width"+width+"-layers.png").toFile());
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
