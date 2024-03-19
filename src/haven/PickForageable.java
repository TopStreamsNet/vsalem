package haven;


import haven.*;

public class PickForageable implements Runnable {
    private GameUI gui;
    final static String[] space_patterns_exclude = new String[]{"gfx/terobjs/arch/",
            "gfx/terobjs/footprints", "gfx/terobjs/bust", "gfx/terobjs/redgroundpillow",
            "gfx/terobjs/bluegroundpillow", "gfx/terobjs/greengroundpillow",
            "gfx/terobjs/splatter"};

    public PickForageable(GameUI gui) {
        this.gui = gui;
    }

    @Override
    public void run() {
        Gob herb = null;
        synchronized (gui.map.glob.oc) {
            gobloop:
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
                        if (distFromPlayer < 100 && (herb == null || distFromPlayer < herb.rc.dist(gui.map.player().rc))) {
                            for (String exclusion : space_patterns_exclude) {
                                if (res.name.startsWith(exclusion)) {
                                    continue gobloop;
                                }
                            }
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
