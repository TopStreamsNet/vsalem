package haven.integrations.map;

import haven.*;
import haven.resutil.RidgeTile;

import java.awt.*;
import java.awt.image.BufferedImage;

import static haven.MCache.cmaps;

/**
 * @author APXEOLOG (Artyom Melnikov), at 28.01.2019
 */
public class MinimapImageGenerator {

    private static BufferedImage tileimg(int t, BufferedImage[] texes, MCache map) {
        BufferedImage img = texes[t];
        if (img == null) {
            Resource r = map.tilesetr(t);
            if (r == null)
                return (null);
            Resource.Image ir = r.layer(Resource.imgc);
            if (ir == null)
                return (null);
            img = ir.img;
            texes[t] = img;
        }
        return (img);
    }

    public static BufferedImage drawmap(MCache map, MCache.Grid grid) {
        BufferedImage[] texes = new BufferedImage[256];
        BufferedImage buf = TexI.mkbuf(cmaps);
        Coord c = new Coord();
        for (c.y = 0; c.y < cmaps.y; c.y++) {
            for (c.x = 0; c.x < cmaps.x; c.x++) {
                BufferedImage tex = tileimg(grid.gettile(c), texes, map);
                int rgb = 0;
                if (tex != null)
                    rgb = tex.getRGB(Utils.floormod(c.x, tex.getWidth()),
                            Utils.floormod(c.y, tex.getHeight()));
                buf.setRGB(c.x, c.y, rgb);
            }
        }

        drawRidges(grid.ul, cmaps, map, buf, c);

        for (c.y = 0; c.y < cmaps.y; c.y++) {
            for (c.x = 0; c.x < cmaps.x; c.x++) {
                try {
                    int t = grid.gettile(c);
                    Coord r = c.add(grid.ul);
                    if ((map.gettile(r.add(-1, 0)) > t) ||
                            (map.gettile(r.add(1, 0)) > t) ||
                            (map.gettile(r.add(0, -1)) > t) ||
                            (map.gettile(r.add(0, 1)) > t)) {
                        buf.setRGB(c.x, c.y, Color.BLACK.getRGB());
                    }
                } catch (Exception e) {
                }
            }
        }
        return buf;
    }

    private static void drawRidges(Coord ul, Coord sz, MCache m, BufferedImage buf, Coord c) {
        for(c.y = 1; c.y < sz.y - 1; c.y++) {
            for(c.x = 1; c.x < sz.x - 1; c.x++) {
                int t = m.gettile(ul.add(c));
                Tiler tl = m.tiler(t);
                if(tl instanceof RidgeTile) {
                    if(((RidgeTile)tl).ridgep(m, ul.add(c))) {
                        for(int y = c.y; y <= c.y + 1; y++) {
                            for(int x = c.x; x <= c.x + 1; x++) {
                                int rgb = buf.getRGB(x, y);
                                rgb = (rgb & 0xff000000) |
                                        (((rgb & 0x00ff0000) >> 17) << 16) |
                                        (((rgb & 0x0000ff00) >> 9) << 8) |
                                        (((rgb & 0x000000ff) >> 1) << 0);
                                buf.setRGB(x, y, rgb);
                            }
                        }
                    }
                }
            }
        }
    }
}