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
}