package haven.lisp;

import haven.*;
//import haven.pathfinder.Pathfinder;
import org.armedbear.lisp.LispObject;

import java.awt.*;
import java.util.HashSet;

public class LispUtil {
    public static long findObjectByNames(LispObject names) {
        Coord plc = UI.instance.gui.map.player().rc;
        double min = Double.MAX_VALUE;
        Gob nearest = null;
        synchronized (UI.instance.sess.glob.oc) {
            for (Gob gob : UI.instance.sess.glob.oc) {
                double dist = gob.rc.dist(plc);
                if (dist < min && dist > 0) {
                    boolean match = false;
                    LispObject inames = names;
                    LispObject name = inames.car();
                    try {
                        while (name.getBooleanValue()) {
                            if (gob.getres() != null && gob.getres().name.endsWith(name.getStringValue())) {
                                match = true;
                                break;
                            }
                            inames = inames.cdr();
                            name = inames.car();
                        }
                        if (match) {
                            min = dist;
                            nearest = gob;
                        }
                    } catch (Session.LoadingIndir ignored) {
                    }catch (Resource.Loading ignored) {
                    }
                }
            }
        }
        return ((nearest == null) ? 0 : nearest.id);
    }

    public static int pfRightClick(long gobid) {
        Gob destGob = UI.instance.sess.glob.oc.getgob(gobid);
        UI.instance.gui.map.pfRightClick(destGob, -1, 3, 0, null);
        return 0;
    }

    private static Coord coordToTile(Coord c) {
        return c.div(11);
    }

}
