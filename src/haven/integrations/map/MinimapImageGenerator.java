package haven.integrations.map;

import haven.*;
import haven.resutil.RidgeTile;

import java.awt.*;
import java.awt.image.BufferedImage;

import static haven.MCache.cmaps;

public class MinimapImageGenerator {

    public static BufferedImage drawmap(MCache map, MCache.Grid grid) {
        return UI.instance.gui.mmap.drawmap(grid.ul,cmaps,true);
    }

    public static Loading checkForLoading(MCache map, MCache.Grid grid) {
        Loading error = null;
        Coord c = new Coord();
        for (c.y = 0; c.y < MCache.cmaps.y; c.y++) {
            for (c.x = 0; c.x < MCache.cmaps.x; c.x++) {
                try {
                    grid.gettile(c);
                } catch (Loading l) {
                    error = l;
                }
            }
        }
        return (error);
    }
}