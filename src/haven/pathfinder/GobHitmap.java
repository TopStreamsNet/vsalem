package haven.pathfinder;

import haven.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A hitmap for Gobs
 * Conflicts are resolves by keeping a list of gobs that are on a given tile
 * Player gob is ignored
 */
public class GobHitmap {
    private final static HitTile nulltile = new HitTile(null);

    public int size() {
        return this.map.size();
    }

    public String tile_info(Coord c) {
        HitTile tile = this.map.get(c);
        return(c+" "+tile.tile+" "+tile.ownedby);
    }

    public void print_tiles(){
        for (Coord c:this.map.keySet()){
            HitTile tile = this.map.get(c);
            System.out.println(c+" "+tile.tile+" "+tile.ownedby);
        }
    }


    private static class HitTile {
        private final Tile tile;
        private final Set<Long> ownedby = new HashSet<>();

        private HitTile(final Tile t) {
            this.tile = t;
        }

        public void add(Gob g) {
            ownedby.add(g.id);
        }

        public void rem(Gob g) {
            ownedby.remove(g.id);
        }

        public boolean isEmpty() {
            return ownedby.isEmpty();
        }
    }

    private final Map<Coord, HitTile> map = new HashMap<>();

    public synchronized BufferedImage debug2(final Coord tl, final Coord br) {
        //Update tl/br if needed
        for (final Coord c : map.keySet()) {
            if (c.x < tl.x)
                tl.x = c.x;
            else if (c.x > br.x)
                br.x = c.x;

            if (c.y < tl.y)
                tl.y = c.y;
            else if (c.y > br.y)
                br.y = c.y;
        }
        final BufferedImage buf = new BufferedImage(br.x - tl.x + 1, br.y - tl.y + 1, BufferedImage.TYPE_INT_RGB);

        //Render our hitmap
        final HashMap<Tile, Color> tmap = new HashMap<>();
        tmap.put(Tile.GOB, Color.RED);
        tmap.put(Tile.DEEPWATER, Color.BLUE);
        tmap.put(Tile.SHALLOWWATER, Color.CYAN);
        tmap.put(Tile.CAVE, Color.GRAY);
        tmap.put(Tile.RIDGE, Color.YELLOW);
        tmap.put(Tile.PLAYER, Color.GREEN);

        for(int x = tl.x; x < br.x; x++){
            for (int y = tl.y; y < br.y; y++){
                final int x_offset = x-tl.x;
                final int y_offset = y-tl.y;
                final Tile tt = UI.instance.sess.glob.map.gethitmap(new Coord(x/MCache.tilesz.x,y/MCache.tilesz.y));
                if (tt != null) {
                    buf.setRGB(x_offset, y_offset, tmap.get(tt).getRGB());
                }
            }
        }

        for (final Coord c : map.keySet()) {
            final Coord offset = c.sub(tl);
            buf.setRGB(offset.x, offset.y, tmap.get(map.get(c).tile).getRGB());
        }

        return buf;
    }

    public synchronized void debug() {
        if (map.size() > 0) {
            //find our boundaries
            Coord tl = new Coord(map.keySet().iterator().next());
            Coord br = new Coord(tl);
            for (final Coord c : map.keySet()) {
                if (c.x < tl.x)
                    tl.x = c.x;
                else if (c.x > br.x)
                    br.x = c.x;

                if (c.y < tl.y)
                    tl.y = c.y;
                else if (c.y > br.y)
                    br.y = c.y;
            }
            final BufferedImage buf = debug2(tl, br);

            try {
                javax.imageio.ImageIO.write(buf, "png", new File("hitmap4.png"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized boolean checkHit(final Coord c) {
        return map.containsKey(c);
    }

    public synchronized List<Coord> add(final Gob g) {
        final UI ui = UI.instance;
        if (ui != null && ui.gui != null && ui.gui.map != null && g.id != MapView.plgob && !(g instanceof OCache.Virtual) && g.id >= 0) {
            return fill(g);
        } else {
            return null;
        }
    }

    public synchronized void rem(final Gob g, final List<Coord> coords) {
        if (coords != null) {
            for (final Coord c : coords) {
                final HitTile tile = map.get(c);
                if (tile != null) {
                    tile.rem(g);
                    if (tile.isEmpty()) {
                        map.remove(c);
                    }
                }
            }
        }
    }

    //Some modified Bresenham's line alg for steep slopes from a berkeley slide
    private void drawline(Coord c1, Coord c2, final Gob g, final List<Coord> coords) {
        drawline(c1.x, c1.y, c2.x, c2.y, g, coords);
    }

    /**
     * Drawing the outline of our gob
     */
    private void drawline(int x0, int y0, int x1, int y1, final Gob g, final List<Coord> coords) {
        boolean steep = Math.abs(y1 - y0) > Math.abs(x1 - x0);
        if (steep) {
            //Reverse the axis
            int t = x0;
            x0 = y0;
            y0 = t;

            t = x1;
            x1 = y1;
            y1 = t;
        }
        if (x0 > x1) {
            int t = x0;
            x0 = x1;
            x1 = t;

            t = y0;
            y0 = y1;
            y1 = t;
        }

        float
                slope = ((float) y1 - y0) / ((float) x1 - x0),
                dErr = Math.abs(slope);
        int yStep = y0 > y1 ? -1 : 1;
        float err = 0.0f;
        int y = y0;

        for (int x = x0; x <= x1; ++x) {
            final Coord c;
            if (steep) {
                //Reverse the axis
                c = new Coord(y, x);
            } else {
                c = new Coord(x, y);
            }
            map.put(c, map.getOrDefault(c, new HitTile(Tile.GOB)));
            map.get(c).add(g);
            coords.add(c);

            err += dErr;
            if (err >= 0.5f) {
                y += yStep;
                err -= 1.0;
            }
        }
    }

    /**
     * Filling in the inner space of a gob that is at an angle.
     */
    private void fillspace(Coord start, final Gob g, final List<Coord> coords) {
        ArrayDeque<Coord> queue = new ArrayDeque<>();
        queue.push(start);

        Coord c;
        while (queue.size() > 0) {
            c = queue.pop();
            if (map.getOrDefault(c, nulltile).tile != Tile.GOB) {
                map.put(c, map.getOrDefault(c, new HitTile(Tile.GOB)));
                map.get(c).add(g);
                coords.add(c);
                queue.add(c.add(1, 0));
                queue.add(c.add(0, 1));
                queue.add(c.add(-1, 0));
                queue.add(c.add(0, -1));
            }
        }
    }

    private List<Coord> fill(final Gob g) {
        final List<Coord> coords = new ArrayList<>();
        final GobHitbox.BBox hb = GobHitbox.getBBox(g);
        if (hb != null) {
            ArrayList<Coord> coordes = new ArrayList<>();
            for (int j = 0; j < hb.points.length; j++) {
                Coord c = new Coord(hb.points[j].round().x, hb.points[j].round().y);
                c = c.rotate((float) g.a);
                final Coord gc = new Coord(g.getc());
                c = gc.add(c);
                coordes.add(c);
            }

            for (int i = 0; i < coordes.size(); i++) {
                int i1 = i == coordes.size() - 1 ? 0 : i + 1;
                drawline(coordes.get(i), coordes.get(i1), g, coords);
            }

            int allx = 0;
            int ally = 0;
            for (Coord coorde : coordes) {
                allx += coorde.x;
                ally += coorde.y;
            }
            final Coord center = new Coord(allx / coordes.size(), ally / coordes.size());
            fillspace(center, g, coords);

        } else {
            System.out.println("No hitbox found for %s"+g.resname());
        }
        return coords;
    }
}
