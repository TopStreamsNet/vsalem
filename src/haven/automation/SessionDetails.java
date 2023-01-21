package haven.automation;

import haven.FlowerMenu;
import haven.Session;
import haven.UI;

import java.lang.ref.WeakReference;

public class SessionDetails {
    public final WeakReference<Session> session;

    public int shp, hhp, mhp;
    public int stam;
    public int energy;

    public SessionDetails(final Session sess) {
        this.session = new WeakReference<>(sess);
        shp = hhp = mhp = stam = energy = 0;
    }

    public UI getUI() {
        final Session sess = session.get();
        return sess != null ? UI.instance : null;
    }

    public FlowerMenu getFlowermenu(){
        return UI.instance.root.findchild(FlowerMenu.class);
    }
}
