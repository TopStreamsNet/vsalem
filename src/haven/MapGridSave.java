package haven;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import haven.resutil.RidgeTile;

import static haven.MCache.cmaps;


public class MapGridSave {
    private MCache map;
    private MCache.Grid g;
    public static Coord gul;
    public static Coord mgs;
    private static String session;
    private static Map<Coord, Long> sessionIds = new HashMap<>();

    public MapGridSave(MCache map, MCache.Grid g) {
        this.map = map;
        this.g = g;

        int x = Math.abs(g.gc.x);
        int y = Math.abs(g.gc.y);
        synchronized (MapGridSave.class) {
            if (x == 0 && y == 0 || x == 10 && y == 10 || mgs == null) {
                session = (new SimpleDateFormat("yyyy-MM-dd HH.mm.ss")).format(new Date(System.currentTimeMillis()));
                sessionIds.clear();
                (new File("alt_map/" + session)).mkdirs();
                mgs = g.gc;
                gul = g.ul;
            }
            BufferedImage img = drawmap(g.ul, cmaps, true);
            if (img != null)
                save(img);
        }
    }

    public void save(BufferedImage img) {
        Coord normc = g.gc.sub(mgs);

        Long knownId = sessionIds.get(normc);
        if (knownId == null)
            sessionIds.put(normc, g.id);
            // tiles might arrive out of order, so we defer those until new session has been created
        else if (knownId != g.id)
            throw new Loading();

        String fileName = String.format("alt_map/%s/tile_%d_%d.png", session, normc.x, normc.y);
        try {
            File outputfile = new File(fileName);
            ImageIO.write(img, "png", outputfile);
        } catch (IOException e) {
            return;
        }

        if (knownId == null) {
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(String.format("alt_map/%s/ids.txt", session), true))) {
                bw.write(String.format("%d,%d,%d\n", normc.x, normc.y, g.id));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private BufferedImage tileimg(int t, BufferedImage[] texes) {
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

    public BufferedImage drawmap(Coord ul, Coord sz, boolean pretty) {
        BufferedImage[] texes = new BufferedImage[256];
        MCache m = UI.instance.sess.glob.map;
        BufferedImage buf = TexI.mkbuf(sz);
        Coord c = new Coord();
        for (c.y = 0; c.y < sz.y; c.y++) {
            for (c.x = 0; c.x < sz.x; c.x++) {
                Coord c2 = ul.add(c);
                int t;
                try {
                    t = m.gettile(c2);
                } catch (MCache.LoadingMap e) {
                    return null;
                }
                try {
                    BufferedImage tex = tileimg(t, texes);
                    int rgb = 0x000000ff;
                    if (tex != null) {
                        Coord tc = pretty ? c2 : c;
                        rgb = tex.getRGB(Utils.floormod(tc.x, tex.getWidth()), Utils.floormod(tc.y, tex.getHeight()));
                    }
                    buf.setRGB(c.x, c.y, rgb);
                } catch (Loading e) {
                    return null;
                }

                try {
                    if ((m.gettile(c2.add(-1, 0)) > t) ||
                            (m.gettile(c2.add(1, 0)) > t) ||
                            (m.gettile(c2.add(0, -1)) > t) ||
                            (m.gettile(c2.add(0, 1)) > t))
                        buf.setRGB(c.x, c.y, Color.BLACK.getRGB());
                } catch (MCache.LoadingMap e) {
                    continue;
                }
            }
        }

        drawRidges(ul, sz, m, buf, c);
        return (buf);
    }

    private static void drawRidges(Coord ul, Coord sz, MCache m, BufferedImage buf, Coord c) {
        for (c.y = 1; c.y < sz.y - 1; c.y++) {
            for (c.x = 1; c.x < sz.x - 1; c.x++) {
                int t = m.gettile(ul.add(c));
                Tiler tl = m.tiler(t);
                if (tl instanceof RidgeTile) {
                    if (((RidgeTile) tl).ridgep(m, ul.add(c))) {
                        for (int y = c.y; y <= c.y + 1; y++) {
                            for (int x = c.x; x <= c.x + 1; x++) {
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

