package haven.automation;

import haven.*;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SessionDetails {
    public final WeakReference<Session> session;

    public enum InventoryType {
        MAIN, BELT, SUPPLEMENTAL
    }

    public int shp, hhp, mhp;
    public int stam;
    public int energy;

    private WeakReference<GItem> helditem;
    private final Object heldlock = new Object();
    private WeakReference<Inventory> maininv = null;
    private WeakReference<Inventory> beltinv = null;
    private final List<WeakReference<Inventory>> invs = new ArrayList<>();

    public SessionDetails(final Session sess) {
        this.session = new WeakReference<>(sess);
        shp = hhp = mhp = stam = energy = 0;
    }



    public UI getUI() {
        final Session sess = session.get();
        return sess != null ? UI.instance : null;
    }

    public FlowerMenu getFlowermenu(){
        FlowerMenu fm = UI.instance.root.findchild(FlowerMenu.class);
        if(fm != null) {
            for (FlowerMenu.Petal petal : fm.opts) {
                if (petal == null) {
                    fm = null;
                    break;
                }
            }
        }
        return fm;
    }

    public Inventory[] inventories() {
        final List<Inventory> ret = new ArrayList<>();
        synchronized (invs) {
            if (maininv != null) {
                final Inventory main = maininv.get();
                if (main != null) {
                    ret.add(main);
                } else {
                    maininv = null;
                }
            }

            if (beltinv != null) {
                final Inventory belt = beltinv.get();
                if (belt != null) {
                    ret.add(belt);
                } else {
                    beltinv = null;
                }
            }

            final Iterator<WeakReference<Inventory>> itr = invs.iterator();
            while (itr.hasNext()) {
                final Inventory inv = itr.next().get();
                if (inv != null)
                    ret.add(inv);
                else// cleanup bad references
                    itr.remove();
            }
        }
        return ret.toArray(new Inventory[0]);
    }

    public void attachInventory(final Inventory inv, final InventoryType type) {
        synchronized (invs) {
            switch (type) {
                case MAIN:
                    maininv = new WeakReference<>(inv);
                    break;
                case BELT:
                    beltinv = new WeakReference<>(inv);
                    break;
                case SUPPLEMENTAL:
                    invs.add(new WeakReference<>(inv));
                    break;
            }
        }
    }

    public void removeInventory(final Inventory inv, final InventoryType type) {
        synchronized (invs) {
            switch (type) {
                case MAIN:
                    maininv = null;
                    break;
                case BELT:
                    beltinv = null;
                    break;
                case SUPPLEMENTAL:
                    //This will also clean up any references that are no longer valid
                    invs.removeIf(i -> i.get() == null || i.get() == inv);
                    break;
            }
        }
    }

    public void attachHeldItem(final GItem item) {
        synchronized (heldlock) {
            helditem = new WeakReference<>(item);
        }
    }

    public void removeHeldItem() {
        synchronized (heldlock) {
            helditem = null;
        }
    }

    public GItem getHeldItem() {
        synchronized (heldlock) {
            return helditem != null ? helditem.get() : null;
        }
    }
}
