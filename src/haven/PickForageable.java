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
                if (gob.id == gui.map.player().id)
                    continue;
                Resource res = null;
                try {
                    res = gob.getres();
                } catch (Loading ignored) {
                }
                if (res != null) {
                    double distFromPlayer = gob.rc.dist(gui.map.player().rc);
                    if (herb == null || distFromPlayer < herb.rc.dist(gui.map.player().rc))
                        herb = gob;
                }
            }
        }
        if (herb == null)
            return;

        gui.map.wdgmsg("click", herb.sc, herb.rc, 3, 0, 0, (int) herb.id, herb.rc, 0, -1);
    }
}
