package haven.automation;

import haven.Coord;
import haven.GameUI;
import haven.Gob;

import java.awt.geom.Line2D;

public class Utils {
    private static final int HAND_DELAY = 5;
    private static final int PROG_ACT_DELAY = 8;
    private static final int PROG_FINISH_DELAY = 70;

    public static boolean waitForEmptyHand(GameUI gui, int timeout, String error) throws InterruptedException {
        int t = 0;
        while (gui.vhand != null) {
            t += HAND_DELAY;
            if (t >= timeout) {
                gui.error(error);
                return false;
            }
            try {
                Thread.sleep(HAND_DELAY);
            } catch (InterruptedException ie) {
                throw ie;
            }
        }
        return true;
    }

    public static boolean insect(Coord[] polygon1, Coord[] polygon2, Gob gob1, Gob gob2) {
        Coord gobc1 = gob1.rc, gobc2 = gob2.rc;
        Coord[] p1 = new Coord[polygon1.length], p2 = new Coord[polygon2.length];
        for (int i = 0; i < polygon1.length; i++)
            p1[i] = polygon1[i].rotate((float) gob1.a).add(gobc1);
        for (int i = 0; i < polygon2.length; i++)
            p2[i] = polygon2[i].rotate((float) gob2.a).add(gobc2);
        for (int i1 = 0; i1 < polygon1.length; i1++)
            for (int i2 = 0; i2 < polygon2.length; i2++)
                if (crossing(p1[i1], p1[i1 + 1 == p1.length ? 0 : i1 + 1], p2[i2], p2[i2 + 1 == p2.length ? 0 : i2 + 1]))
                    return (true);
        return (false);
    }

    public static boolean crossing(Coord c1, Coord c2, Coord c3, Coord c4) {
        Line2D l1 = new Line2D.Double(c1.x, c1.y, c2.x, c2.y);
        Line2D l2 = new Line2D.Double(c3.x, c3.y, c4.x, c4.y);
        return l1.intersectsLine(l2);
    }

    public static Coord abs(Coord c, double adding) {
        return (new Coord(c.x + (c.x / Math.abs(c.x) * adding), c.y + (c.y / Math.abs(c.y) * adding)));
    }

    public static Coord[] abs(Coord[] c, double adding) {
        Coord[] c2 = new Coord[c.length];
        for (int i = 0; i < c.length; i++)
            c2[i] = abs(c[i], adding);
        return (c2);
    }

    public static synchronized void launchLispScript(final String script, final SessionDetails session) {
        try {
            LispScript.reloadConfig();
        }catch (Throwable t){
            System.out.println("Config failed to reload!");
        }
        final Script thr = new LispScript(script, 0L, session);
        thr.start();
    }
}
