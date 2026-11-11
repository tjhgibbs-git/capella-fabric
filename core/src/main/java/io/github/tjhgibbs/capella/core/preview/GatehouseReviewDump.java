package io.github.tjhgibbs.capella.core.preview;

import io.github.tjhgibbs.capella.core.BuildingGenerator;
import io.github.tjhgibbs.capella.core.params.*;
import io.github.tjhgibbs.capella.core.small.CastleGatehouse;
import io.github.tjhgibbs.capella.core.voxel.BlockVolume;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Finished gatehouse elevations, peeled half-sections and deck/passage layers. */
public final class GatehouseReviewDump {
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args.length>0?args[0]:"gatehouse-review");Files.createDirectories(out);
        for(String name:new String[]{"gatehouse","roundgatehouse","roofedgatehouse","compact","high","left","right"}) {
            var p=Presets.get(name.equals("compact")||name.equals("high")||name.equals("left")||name.equals("right")?"gatehouse":name).params(7);
            if(name.equals("compact")) p.set(Params.LENGTH,9).set(Params.WIDTH,5).set(Params.HEIGHT,5);
            if(name.equals("high")) p.set(Params.LENGTH,65).set(Params.WIDTH,11).set(Params.HEIGHT,40)
                    .set(Params.GATE_TOWERS,Params.GateTower.ROUND).set(Params.GATE_STYLE,Params.GateStyle.CLOSED_PORTCULLIS);
            if(name.equals("left")||name.equals("right")) p.set(Params.GATE_POSITION,name.equals("left")?0.0:1.0);
            BlockVolume full=BuildingGenerator.generate(p).volume();var a=CastleGatehouse.layout(p);
            BufferedImage sheet=new BufferedImage(1600,1060,BufferedImage.TYPE_INT_RGB);
            Graphics2D g=sheet.createGraphics();g.setColor(new Color(0xe9e6df));g.fillRect(0,0,1600,1060);
            g.setColor(Color.DARK_GRAY);g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,20));
            g.drawString(name+" / "+p.integer(Params.LENGTH)+" x "+p.integer(Params.WIDTH)+" x "+p.integer(Params.HEIGHT)+" / gate "+Math.round(p.num(Params.GATE_POSITION)*100)+"% / seed 7",20,28);
            String[] labels={"Exterior and wall joins", "Front half removed", "Wall-walk layer (floor + two-high route)", "Gateway section through gate plane"};
            for(int panel=0;panel<4;panel++) {
                BlockVolume v=new BlockVolume(full.width,full.height,full.length,full.originX,full.originY,full.originZ);
                for(int x=0;x<full.width;x++) for(int y=0;y<full.height;y++) for(int z=0;z<full.length;z++) {
                    int wy=y-full.originY,wz=z-full.originZ;
                    boolean keep=switch(panel) {
                        case 0 -> true;
                        case 1 -> wz>=0 || wy==-1;
                        case 2 -> wy>=a.wallHeight()-1&&wy<=a.wallHeight()+1;
                        default -> wz==(a.z0()+a.z1())/2;
                    };
                    if(keep) v.set(x,y,z,full.get(x,y,z));
                }
                double yaw=panel==3?180:35,pitch=panel==2?80:panel==3?0:25;
                int[] px=PreviewRenderer.render(v,new PreviewRenderer.View(780,450,Math.toRadians(yaw),Math.toRadians(pitch),1,0,0,false),BlockColors::rgb);
                BufferedImage im=new BufferedImage(780,450,BufferedImage.TYPE_INT_ARGB);im.setRGB(0,0,780,450,px,0,780);
                int x=panel%2*800,y=panel/2*500+50;
                g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,18));g.drawString(labels[panel],x+12,y+20);g.drawImage(im,x+10,y+30,null);
            }
            g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));g.drawString("Layered cutaways of finished blocks. Offline geometry; Minecraft textures and lighting are not shown.",20,1040);
            g.dispose();ImageIO.write(sheet,"png",out.resolve(name+"-layers.png").toFile());
        }
    }
}
