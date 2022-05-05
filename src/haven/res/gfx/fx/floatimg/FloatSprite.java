package haven.res.gfx.fx.floatimg;

        import haven.Coord;
        import haven.GOut;
        import haven.Gob;
        import haven.PView;
        import haven.RenderList;
        import haven.Resource;
        import haven.Sprite;
        import haven.Tex;
        import haven.Utils;

public class FloatSprite
        extends Sprite
        implements PView.Render2D {
    public final int ms;
    final Tex tex;
    final int sy;
    double a = 0.0;

    public int cury() {
        return this.sy - (int)(10.0 * this.a);
    }

    public FloatSprite(Sprite.Owner owner, Resource resource, Tex tex2, int n) {
        super(owner, resource);
        this.tex = tex2;
        this.ms = n;
        this.sy = FloatSprite.place((Gob)owner, tex2.sz().y);
    }

    private static int place(Gob gob, int n) {
        int n2 = 0;
        block0: while (true) {
            for (Gob.Overlay overlay : gob.ols) {
                if (!(overlay.spr instanceof FloatSprite)) continue;
                FloatSprite floatSprite = (FloatSprite)overlay.spr;
                int n3 = floatSprite.cury();
                int n4 = floatSprite.tex.sz().y;
                if (!(n3 >= n2 && n3 < n2 + n || n2 >= n3 && n2 < n3 + n4)) continue;
                n2 = n3 - n;
                continue block0;
            }
            break;
        }
        return n2;
    }

    @Override
    public void draw2d(GOut gOut) {
        Coord coord = ((Gob)this.owner).sc;
        if (coord == null) {
            return;
        }
        int n = this.a < 0.75 ? 255 : (int)Utils.clip(255.0 * ((1.0 - this.a) / 0.25), 0.0, 255.0);
        gOut.chcolor(255, 255, 255, n);
        Coord coord2 = this.tex.sz().inv();
        coord2.x /= 2;
        coord2.y += this.cury();
        coord2.y -= 15;
        gOut.image(this.tex, coord.add(coord2));
        gOut.chcolor();
    }

    @Override
    public boolean setup(RenderList renderList) {
        return false;
    }

    @Override
    public boolean tick(int n) {
        this.a += (double)n / (double)this.ms;
        return this.a >= 1.0;
    }
}