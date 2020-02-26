package haven;


import haven.*;

public class PickForageable implements Runnable {
    private GameUI gui;

    public PickForageable(GameUI gui) {
        this.gui = gui;
    }

    @Override
    public void run() {
        Gob herb = null;
        synchronized (gui.map.glob.oc) {
            for (Gob gob : gui.map.glob.oc) {
                Resource res = null;
                try {
                    res = gob.getres();
                } catch (Loading l) {
                }
                if (res != null) {
                    if (res.name.startsWith("gfx/terobjs/herbs") ||
                            res.name.startsWith("gfx/terobjs/items")) {
                        double distFromPlayer = gob.rc.dist(gui.map.player().rc);
                        if (distFromPlayer <= 20 * 11 && (herb == null || distFromPlayer < herb.rc.dist(gui.map.player().rc)))
                            herb = gob;
                    }
                }
            }
        }
        if (herb == null)
            return;

        gui.map.wdgmsg("click", herb.sc, herb.rc, 3, 0, 0, (int) herb.id, herb.rc, 0, -1);
    }
}
