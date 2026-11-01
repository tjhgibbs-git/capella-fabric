package io.github.tjhgibbs.capella.core.preview;

import io.github.tjhgibbs.capella.core.BuildingGenerator;
import io.github.tjhgibbs.capella.core.cathedral.*;
import io.github.tjhgibbs.capella.core.field.Field;
import io.github.tjhgibbs.capella.core.geom.Vec3;
import io.github.tjhgibbs.capella.core.kit.Band;
import io.github.tjhgibbs.capella.core.layout.Portal;
import io.github.tjhgibbs.capella.core.layout.StructureLayout;
import io.github.tjhgibbs.capella.core.params.*;
import io.github.tjhgibbs.capella.core.voxel.BlockVolume;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/** Offline diagnostic perspective views of generated blocks, not Minecraft screenshots.
 * Uses average block colours and approximate sub-block shapes; ambient lighting deliberately
 * reveals dark interiors. No claim about Minecraft textures, collision or propagated light.
 */
public final class InteriorReviewDump {
    private static final int W = 640, H = 400;
    private static final double[][] CUBE = {{0,0,0,1,1,1}};
    private record View(String name, double x, double y, double z, double yaw, double pitch) {}
    private record Hit(double t, int axis, double x, double y, double z, int id) {}
    private final BlockVolume v;
    private final PlanLayout l;
    private final Portal door;
    private final double[][][] shapes;
    private final int[] colors;

    private InteriorReviewDump(BuildingParams p, int turns) {
        v = BuildingGenerator.generate(p, turns).volume();
        Scene scene = new Scene(p.seed(), p.get(Params.STONE).paletteId, 1024, 1024,
                Field.constant(Field.TERRAIN_HEIGHT,1024,1024,64), BuildingGenerator.planOf(p),
                "cathedral",null,null,Math.floorMod(turns,4)*90,Scene.NEUTRAL_RUINATION);
        l = CathedralPlan.resolveLayout(scene);
        door = StructureLayout.placeLayout(CathedralPlan.structureLayoutOf(l), l.frame).portals.stream()
                .filter(q -> q.kind == Portal.Kind.WEST_GREAT).findFirst().orElseThrow();
        shapes = new double[v.palette().size()][][];
        colors = new int[shapes.length];
        for (int i=0;i<shapes.length;i++) {
            String state=v.palette().get(i);
            shapes[i]=shape(state);
            colors[i]=state.contains("lantern") || state.contains("candle") ? 0xf5b74f : BlockColors.rgb(state);
        }
    }

    public static void main(String[] args) throws IOException {
        Path out=Path.of(args.length>0?args[0]:"interior-review");
        String names=args.length>1?args[1]:"cathedral,minster,dark,colossal";
        long seed=args.length>2?Long.parseLong(args[2]):1;
        int turns=args.length>3?Integer.parseInt(args[3]):0;
        Files.createDirectories(out);
        for (String name:names.split(",")) {
            if (!Presets.exists(name)) throw new IllegalArgumentException("Unknown preset: "+name);
            BuildingParams p=Presets.get(name).params(seed);
            if(p.get(Params.TYPE)!=Params.BuildingType.CATHEDRAL) throw new IllegalArgumentException("Cathedral preset required");
            new InteriorReviewDump(p,turns).dump(out,name+"-seed"+seed+"-turn"+turns,args.length>4?Path.of(args[4]):null);
        }
    }

    private Vec3 volumePoint(double x,double y,double z) {
        Vec3 w=l.frame.point(new Vec3(x,y,z));
        return new Vec3(w.x()-Math.floor(door.position.x())+v.originX,
                y-Math.floor(door.threshold)+v.originY,w.z()-Math.floor(door.position.z())+v.originZ);
    }
    private String state(int x,int y,int z) {
        return x<0 || x>=v.width || y<0 || y>=v.height || z<0 || z>=v.length ? "minecraft:air" : v.get(x,y,z);
    }
    private static boolean air(String s) {return s.endsWith(":air")||s.endsWith(":cave_air")||s.endsWith(":void_air");}
    private boolean standing(double x,double y,double z) {
        Vec3 q=volumePoint(x,y,z);
        int ix=(int)Math.floor(q.x()), iy=(int)Math.floor(q.y()), iz=(int)Math.floor(q.z());
        return air(state(ix,iy,iz)) && air(state(ix,iy+1,iz)) && !air(state(ix,iy-1,iz));
    }
    private View view(String name,double x,double feet,double z,double yaw,double pitch) {
        // Move at most three cells to avoid camera embedded in a pier/furniture. Report the actual eye.
        for (int r=0;r<=3;r++) for(int dx=-r;dx<=r;dx++) for(int dz=-r;dz<=r;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=r) continue;
            if(standing(x+dx,feet,z+dz)) return new View(name,x+dx,feet+1.62,z+dz,yaw,pitch);
        }
        return new View(name+" [no standing cell]",x,feet+1.62,z,yaw,pitch);
    }
    private List<View> views() {
        List<View> out=new ArrayList<>();
        Bay first=l.bays.stream().filter(b->b.kind==Bay.Kind.NAVE).findFirst().orElseThrow();
        Bay choir=l.bays.stream().filter(b->b.kind==Bay.Kind.CHOIR).findFirst().orElse(first);
        Bay cross=l.bays.stream().filter(b->b.isCrossing).findFirst().orElse(first);
        Band aisle=l.north.aisles.getLast();
        double az=(aisle.z0()+aisle.z1())*.5;
        out.add(view("west nave",first.x0+2,l.floorY,l.axisZ,0,-.07));
        out.add(view("aisle toward transept",first.x0+first.span+2,l.floorY,az,0,-.03));
        out.add(view("stair foot",first.x0+first.span-1,l.floorY,az,Math.PI,-.1));
        out.add(view("gallery landing exit",first.x1+3,l.levels.triforiumDeckY,az,Math.PI,-.02));
        out.add(view("gallery at transept",cross.x0-3,l.levels.triforiumDeckY,az,0,-.05));
        out.add(view("crossing tower soffit",(cross.x0+cross.x1)*.5,l.floorY,l.axisZ,0,-.75));
        out.add(view("crossing into transept",(cross.x0+cross.x1)*.5,l.floorY,l.axisZ,-Math.PI/2,-.1));
        out.add(view("choir screen from inside",choir.x0+7,l.levels.choirFloorY,l.axisZ,Math.PI,-.05));
        out.add(view("choir toward altar",choir.x0+10,l.levels.choirFloorY,l.axisZ,0,-.03));
        out.add(view("east return enclosure",l.eastX-9,l.levels.choirFloorY,az,0,-.03));
        out.add(view("west tower join",first.x0+3,l.floorY,az,Math.PI,-.5));
        out.add(view("nave vault",(first.x0+first.x1)*.5,l.floorY,l.axisZ,0,-.6));
        int run=Math.min(8,(int)first.span-3);
        int xs=(int)first.x0+1, xe=xs+run+1, zw=(int)aisle.z0();
        out.add(view("stair first turn",xe,l.floorY+run,zw+3,Math.PI,-.15));
        int rise=(int)(l.levels.triforiumDeckY-l.floorY), flight=(rise-1)/run;
        boolean east=flight%2==0;
        int endx=east?xs+1+(rise-1)%run:xe-1-(rise-1)%run;
        int lane=zw+(flight%2==0?0:3);
        out.add(view("stair final arrival",endx+(east?2:-2),l.levels.triforiumDeckY,lane,east?Math.PI:0,.2));
        out.add(view("choir loft stair",choir.x0+11,l.levels.choirFloorY,l.naveZ1-6,Math.atan2(3,-5),-.1));
        out.add(view("choir loft walkway",choir.x0+1,l.levels.choirFloorY+5,l.axisZ,Math.PI/2,.05));
        out.add(view("crossing attic portal",cross.x0-6,l.levels.atticWalkY,l.axisZ,0,-.03));
        out.add(view("gallery choir threshold",choir.x0-5,l.levels.triforiumDeckY,az,0,-.03));
        return out;
    }
    private void dump(Path out,String name,Path cameraFile) throws IOException {
        List<View> views=views();
        if(cameraFile!=null) {
            views=new ArrayList<>();
            for(String line:Files.readAllLines(cameraFile)) {
                if(line.startsWith("name,")||line.isBlank())continue;
                String[] f=line.split(",");
                views.add(new View(f[0],Double.parseDouble(f[1]),Double.parseDouble(f[2]),Double.parseDouble(f[3]),Double.parseDouble(f[4]),Double.parseDouble(f[5])));
            }
        }
        BufferedImage sheet=new BufferedImage(W*3,H*((views.size()+2)/3),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=sheet.createGraphics();
        StringBuilder manifest=new StringBuilder("Offline block geometry review; average colours, diagnostic ambient light.\n");
        StringBuilder csv=new StringBuilder("name,x,eyeY,z,yaw,pitch\n");
        for(int i=0;i<views.size();i++) {
            View camera=views.get(i);
            BufferedImage image=render(camera);
            ImageIO.write(image,"png",out.resolve(name+"-"+i+".png").toFile());
            g.drawImage(image,(i%3)*W,(i/3)*H,null);
            manifest.append(i).append(" ").append(camera).append('\n');
            csv.append(camera.name).append(',').append(camera.x).append(',').append(camera.y).append(',').append(camera.z).append(',').append(camera.yaw).append(',').append(camera.pitch).append('\n');
        }
        g.dispose();
        ImageIO.write(sheet,"png",out.resolve(name+"-contact.png").toFile());
        ImageIO.write(slice(l.floorY),"png",out.resolve(name+"-floor-plan.png").toFile());
        ImageIO.write(slice(l.levels.triforiumDeckY),"png",out.resolve(name+"-gallery-plan.png").toFile());
        Files.writeString(out.resolve(name+"-cameras.txt"),manifest);
        Files.writeString(out.resolve(name+"-cameras.csv"),csv);
        System.out.println(name+": "+v.width+"x"+v.height+"x"+v.length+"; "+views.size()+" player-eye views, floor and gallery clearance maps");
    }
    private BufferedImage slice(double level) {
        int scale=Math.max(2,Math.min(5,1000/Math.max(v.width,v.length)));
        BufferedImage img=new BufferedImage(v.width*scale,v.length*scale+45,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=img.createGraphics();
        g.setColor(new Color(0x20252b));g.fillRect(0,0,img.getWidth(),img.getHeight());
        int y=(int)Math.round(level-door.threshold)+v.originY;
        for(int z=0;z<v.length;z++)for(int x=0;x<v.width;x++) {
            boolean body=air(state(x,y,z))&&air(state(x,y+1,z));
            boolean floor=!air(state(x,y-1,z));
            int color=body?(floor?0x76ae83:0x20252b):(floor?0x817361:0x9c4c4c);
            g.setColor(new Color(color));g.fillRect(x*scale,z*scale+45,scale,scale);
        }
        g.setColor(Color.WHITE);g.drawString("Offline clearance slice Y="+(y-v.originY)+" from door: green=supported 2-cell air, brown=blocked, red=unsupported obstruction",8,18);
        g.dispose();return img;
    }
    private Vec3 direction(Vec3 d) {
        return switch(l.frame.turns()) {
            case 1 -> new Vec3(-d.z(),d.y(),d.x());
            case 2 -> new Vec3(-d.x(),d.y(),-d.z());
            case 3 -> new Vec3(d.z(),d.y(),-d.x());
            default -> d;
        };
    }
    private BufferedImage render(View c) {
        Vec3 o=volumePoint(c.x+.5,c.y,c.z+.5);
        Vec3 f=direction(new Vec3(Math.cos(c.yaw)*Math.cos(c.pitch),-Math.sin(c.pitch),Math.sin(c.yaw)*Math.cos(c.pitch)));
        Vec3 right=direction(new Vec3(-Math.sin(c.yaw),0,Math.cos(c.yaw)));
        Vec3 up=new Vec3(-f.y()*right.z(), f.x()*right.z()-f.z()*right.x(), f.y()*right.x());
        BufferedImage img=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        double k=Math.tan(Math.toRadians(72)*.5);
        for(int py=0;py<H;py++)for(int px=0;px<W;px++) {
            double u=(2*(px+.5)/W-1)*k, w=(1-2*(py+.5)/H)*k*H/W;
            double dx=f.x()+right.x()*u+up.x()*w,dy=f.y()+right.y()*u+up.y()*w,dz=f.z()+right.z()*u+up.z()*w;
            double len=Math.sqrt(dx*dx+dy*dy+dz*dz);dx/=len;dy/=len;dz/=len;
            Hit h=trace(o.x(),o.y(),o.z(),dx,dy,dz);
            int color=h==null?0x9cb9d0:shade(h);
            img.setRGB(px,py,color);
        }
        Graphics2D g=img.createGraphics();g.setColor(new Color(0,0,0,210));g.fillRect(0,0,W,42);
        g.setColor(Color.WHITE);g.drawString(c.name+" | eye "+Math.round(c.x)+","+String.format("%.2f",c.y-l.floorY)+","+Math.round(c.z),10,17);
        g.drawString("OFFLINE geometry review · average colours · diagnostic ambient light",10,34);g.dispose();return img;
    }
    private int shade(Hit h) {
        int rgb=colors[h.id];
        double light=h.axis==1?.95:h.axis==0?.78:.66;
        // Thin grid and subtle grain make the block scale apparent without inventing texture assets.
        double a=h.axis==0?h.z:h.x,b=h.axis==1?h.z:h.y;
        double edge=Math.min(Math.min(fract(a),1-fract(a)),Math.min(fract(b),1-fract(b)));
        light*=edge<.018?.72:1;
        light*=.91+.09*Math.sin(h.x*57+h.z*31+h.y*71);
        return ((int)((rgb>>16&255)*light)<<16)|((int)((rgb>>8&255)*light)<<8)|(int)((rgb&255)*light);
    }
    private static double fract(double x) {return x-Math.floor(x);}
    private Hit trace(double ox,double oy,double oz,double dx,double dy,double dz) {
        int x=(int)Math.floor(ox),y=(int)Math.floor(oy),z=(int)Math.floor(oz);
        int sx=dx>0?1:-1,sy=dy>0?1:-1,sz=dz>0?1:-1;
        double tx=((dx>0?x+1:x)-ox)/dx,ty=((dy>0?y+1:y)-oy)/dy,tz=((dz>0?z+1:z)-oz)/dz;
        double ax=Math.abs(1/dx),ay=Math.abs(1/dy),az=Math.abs(1/dz);
        for(int step=0;step<700;step++) {
            if(x<0||x>=v.width||y<0||y>=v.height||z<0||z>=v.length) return null;
            int id=v.idAt(x,y,z);
            if(id!=0) {
                double best=Double.POSITIVE_INFINITY;int axis=0;
                for(double[] b:shapes[id]) {
                    double[] origin={ox-x,oy-y,oz-z},direction={dx,dy,dz};
                    double near=0,far=350;int hitAxis=0;
                    for(int j=0;j<3;j++) {
                        double t0=(b[j]-origin[j])/direction[j],t1=(b[j+3]-origin[j])/direction[j];
                        double lo=Math.min(t0,t1),hi=Math.max(t0,t1);
                        if(lo>near){near=lo;hitAxis=j;} far=Math.min(far,hi);
                    }
                    if(far>=near&&far>0&&near<best){best=near;axis=hitAxis;}
                }
                if(Double.isFinite(best))return new Hit(best,axis,ox+dx*best,oy+dy*best,oz+dz*best,id);
            }
            if(tx<ty&&tx<tz){x+=sx;tx+=ax;}else if(ty<tz){y+=sy;ty+=ay;}else{z+=sz;tz+=az;}
        }
        return null;
    }
    private static double[][] shape(String s) {
        if(air(s)||s.contains("structure_void"))return new double[0][];
        if(s.contains("_slab"))return s.contains("type=double")?CUBE:s.contains("type=top")?new double[][]{{0,.5,0,1,1,1}}:new double[][]{{0,0,0,1,.5,1}};
        if(s.contains("_stairs")) {
            boolean top=s.contains("half=top");double low=top?0:.5,high=top?.5:1;
            double[] step=s.contains("facing=east")?new double[]{.5,low,0,1,high,1}:s.contains("facing=west")?new double[]{0,low,0,.5,high,1}:s.contains("facing=south")?new double[]{0,low,.5,1,high,1}:new double[]{0,low,0,1,high,.5};
            return new double[][]{top?new double[]{0,.5,0,1,1,1}:new double[]{0,0,0,1,.5,1},step};
        }
        if(s.contains("chain"))return s.contains("axis=x")?new double[][]{{0,.44,.44,1,.56,.56}}:s.contains("axis=z")?new double[][]{{.44,.44,0,.56,.56,1}}:new double[][]{{.44,0,.44,.56,1,.56}};
        if(s.contains("lantern"))return new double[][]{{.3,0,.3,.7,.65,.7}};
        if(s.contains("candle"))return new double[][]{{.35,0,.35,.65,.5,.65}};
        double[][] boxes=BlockShapes.boxes(s);return boxes==null?CUBE:boxes;
    }
}
