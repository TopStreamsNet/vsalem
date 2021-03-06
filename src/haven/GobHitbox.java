package haven;

import javax.media.opengl.GL2;
import java.awt.*;

public class GobHitbox extends Sprite {
    public static States.ColState fillclrstate = new States.ColState(new Color(215,255,0));
    private static final States.ColState bbclrstate = new States.ColState(new Color(255, 255, 255, 255));
    private Coordf a, b, c, d;
    private int mode;
    private States.ColState clrstate;

    public GobHitbox(Gob gob, Coord ac, Coord bc, boolean fill) {
        super(gob, null);

        if (fill) {
            mode =  GL2.GL_QUADS;
            clrstate = fillclrstate;
        } else {
            mode =  GL2.GL_LINE_LOOP;
            clrstate = bbclrstate;
        }

        a = new Coordf(ac.x, ac.y);
        b = new Coordf(ac.x, bc.y);
        c = new Coordf(bc.x, bc.y);
        d = new Coordf(bc.x, ac.y);
    }

    public boolean setup(RenderList rl) {
        rl.prepo(clrstate);
        if (mode ==  GL2.GL_LINE_LOOP)
            rl.prepo(States.xray);
        return true;
    }

    public void draw(GOut g) {
        g.apply();
        GL2 gl = g.gl.getGL2();
        if (mode ==  GL2.GL_LINE_LOOP) {
            gl.glLineWidth(2.0F);
            gl.glBegin(mode);
            gl.glVertex3f(a.x, a.y, 1);
            gl.glVertex3f(b.x, b.y, 1);
            gl.glVertex3f(c.x, c.y, 1);
            gl.glVertex3f(d.x, d.y, 1);
        } else {
            gl.glBegin(mode);
            gl.glVertex3f(a.x, a.y, 1);
            gl.glVertex3f(d.x, d.y, 1);
            gl.glVertex3f(c.x, c.y, 1);
            gl.glVertex3f(b.x, b.y, 1);
        }
        gl.glEnd();
        gl.glBegin(GL2.GL_LINES);
        gl.glVertex3f(0, 0, 1);
        gl.glVertex3f(a.x, a.y, 1);
        gl.glEnd();
    }

    public static class BBox {
        public Coord a;
        public Coord b;

        public BBox(Coord a, Coord b) {
            this.a = a;
            this.b = b;
        }
    }

    private static final BBox bboxCalf = new BBox(new Coord(-9, -3), new Coord(9, 3));
    private static final BBox bboxLamb = new BBox(new Coord(-6, -2), new Coord(6, 2));
    private static final BBox bboxGoat = new BBox(new Coord(-6, -2), new Coord(6, 2));
    private static final BBox bboxPig = new BBox(new Coord(-6, -3), new Coord(6, 3));
    private static final BBox bboxCattle  = new BBox(new Coord(-12, -4), new Coord(12, 4));
    private static final BBox bboxHorse = new BBox(new Coord(-8, -4), new Coord(8, 4));
    private static final BBox bboxWallseg = new BBox(new Coord(-5, -6), new Coord(6, 5));
    private static final BBox bboxHwall = new BBox(new Coord(-1, 0), new Coord(0, 11));
    
    private static final BBox bboxSmelter = new BBox(new Coord(-34, -13), new Coord(13, 24));
    private static final BBox bboxForge = new BBox(new Coord(-5, -50), new Coord(5, 40));
    private static final BBox bboxCons = new BBox(new Coord(-5, 5), new Coord(5, -5));

    public static BBox getBBox(Gob gob) {
        Resource res = null;
        try {
            res = gob.getres();
        } catch (Loading l) {
        }
        if (res == null)
            return null;

        String name = res.name;

        // calves, lambs, cattle, goat
        if (name.equals("gfx/kritter/cattle/calf"))
            return bboxCalf;
        else if (name.equals("gfx/kritter/sheep/lamb"))
            return bboxLamb;
        else if (name.equals("gfx/kritter/cattle/cattle"))
            return bboxCattle;
        else if (name.startsWith("gfx/kritter/horse/"))
            return bboxHorse;
        else if (name.startsWith("gfx/kritter/goat/"))
            return bboxGoat;
        else if (name.startsWith("gfx/kritter/pig/"))
            return bboxPig;
        else if (name.startsWith("gfx/terobjs/consobj"))
            return bboxCons;

        // dual state gobs
        if (name.endsWith("gate") && name.startsWith("gfx/terobjs/")) {
            GAttrib rd = gob.getattr(ResDrawable.class);
            if (rd == null)     // shouldn't happen
                return null;
            int state = ((ResDrawable) rd).sdt.peekrbuf(0);
            if (state == 1)     // open gate
                return null;
        } else if (name.endsWith("/pow")) {
            GAttrib rd = gob.getattr(ResDrawable.class);
            if (rd == null)     // shouldn't happen
                return null;
            int state = ((ResDrawable) rd).sdt.peekrbuf(0);
            if (state == 17 || state == 33) // hf
                return null;
        }

        // either i completely misinterpreted how bounding boxes are defined
        // or some negs simply have wrong Y dimensions. in either case this fixes it
        if (name.endsWith("/oresmelter"))
            return bboxSmelter;
        else if (name.endsWith("/fineryforge"))
            return bboxForge;
        else if (name.endsWith("brickwallseg") || name.endsWith("brickwallcp") ||
                name.endsWith("palisadeseg") || name.endsWith("palisadecp") ||
                name.endsWith("poleseg") || name.endsWith("polecp"))
            return bboxWallseg;
        else if (name.endsWith("/hwall"))
            return bboxHwall;

        Resource.Neg neg = gob.getneg();

        /*if (name.endsWith("/alchemytable"))
            System.out.println("alchemytable" +neg.bc+":"+neg.bs);*/
        return neg == null ? null : new BBox(neg.bc, neg.bs);
    }
}
