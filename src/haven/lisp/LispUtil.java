package haven.lisp;

import haven.*;
//import haven.pathfinder.Pathfinder;
import org.armedbear.lisp.LispObject;

import java.awt.*;
import java.util.HashSet;

import static java.lang.Thread.sleep;

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
                    } catch (Resource.Loading ignored) {
                    }
                }
            }
        }
        return ((nearest == null) ? 0 : nearest.id);
    }

    public static boolean pfRightClick(long gobid) {
        Gob destGob = UI.instance.sess.glob.oc.getgob(gobid);
        UI.instance.gui.map.pfRightClick(destGob, -1, 3, 0, null);
        return true;
    }

    private static Coord coordToTile(Coord c) {
        return c.div(11);
    }

    public static void waitForFlowerMenu() {
        UI.instance.gui.syslog.append("_ waitForFlowerMenu", Color.RED);
        FlowerMenu menu = UI.instance.gui.ui.root.findchild(FlowerMenu.class);
        while (menu == null || menu.opts == null) {
            try {
                sleep(15);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            menu = UI.instance.gui.ui.root.findchild(FlowerMenu.class);
        }
        UI.instance.gui.syslog.append("^ waitForFlowerMenu", Color.RED);
    }

    public static boolean choosePetal(String name) {
        UI.instance.gui.syslog.append("_ Choose petal", Color.RED);
        FlowerMenu menu = UI.instance.gui.ui.root.findchild(FlowerMenu.class);
        if (menu != null) {
            for (FlowerMenu.Petal opt : menu.opts) {
                if (opt.name.equals(name)) {
                    menu.choose(opt);
                    menu.destroy();
                    UI.instance.gui.syslog.append("^ Choose petal", Color.RED);
                    return true;
                }
            }
        }
        UI.instance.gui.syslog.append("^ Choose petal", Color.RED);
        return false;
    }

    public static boolean waitFlowerMenuClose() {
        while (UI.instance.gui.ui.root.findchild(FlowerMenu.class) != null) {
            try {
                sleep(25);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    public static int carryWeight() {
        return UI.instance.gui.weight;
    }

    public static int maxWeight() {
        return UI.instance.sess.glob.cattr.get("carry").getComp();
    }
}
