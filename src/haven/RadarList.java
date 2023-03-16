package haven;

import java.util.*;

import haven.minimap.ConfigGroup;
import haven.minimap.ConfigMarker;

import static haven.Window.cbtni;

public class RadarList extends Scrollport {

    private final IBox box;

    public RadarList(Coord c, Widget parent) {
        super(c, new Coord(200, 250), parent);
        box = new IBox("gfx/hud", "tl", "tr", "bl", "br", "extvl", "extvr", "extht", "exthb");

        int i = 0;
        for(final ConfigGroup cg : OptWnd2.rc.getGroups()){
            new Item(new Coord(0, 25 * i++), cg, cont);
        }

        update();
    }

    @SuppressWarnings("SynchronizeOnNonFinalField")
    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if (msg.equals("changed")) {
            String name = (String) args[0];
            boolean val = (Boolean) args[1];
            System.out.println("Changed! "+name+" "+val);
        } else if (msg.equals("delete")) {
            String name = (String) args[0];
            System.out.println("Delete! "+name);
            ui.destroy(sender);
            update();
        } else {
            super.wdgmsg(sender, msg, args);
        }
    }

    @SuppressWarnings("SynchronizeOnNonFinalField")
    public void add(String name) {
        System.out.println("Add!! "+name);
    }

    private void update() {

        cont.update();
    }

    @Override
    public void draw(GOut g) {
        super.draw(g);
        box.draw(g, Coord.z, sz);
    }

    private static class Item extends Widget {

        public final String name;
        private final CheckBox cb;
        private boolean highlight = false;
        private boolean a = false;

        public Item(Coord c, ConfigGroup cg, Widget parent) {
            super(c, new Coord(200, 25), parent);
            this.name = cg.name;

            cb = new CheckBox(new Coord(3, 3), this, cg.name){
                @Override
                public void changed(boolean val) {
                    super.changed(val);
                    cg.show = val;
                    for(ConfigMarker cm : cg.markers)
                        cm.show = val;
                    if(OptWnd2.mf != null)
                        OptWnd2.mf.setConfig(OptWnd2.rc);
                    this.ui.sess.glob.oc.radar.reload(OptWnd2.rc);
                }
            };
            cb.a = cg.show;
            cb.canactivate = true;
        }

        @Override
        public void draw(GOut g) {
            if (highlight) {
                g.chcolor(255, 255, 0, 128);
                g.poly2(Coord.z, Listbox.selr,
                        new Coord(0, sz.y), Listbox.selr,
                        sz, Listbox.overr,
                        new Coord(sz.x, 0), Listbox.overr);
                g.chcolor();
            }
            super.draw(g);
        }

        @Override
        public void mousemove(Coord c) {
            highlight = c.isect(Coord.z, sz);
            super.mousemove(c);
        }

        @Override
        public boolean mousedown(Coord c, int button) {
            if(super.mousedown(c, button)){
                return true;
            }
            if(button != 1)
                return(false);
            a = true;
            ui.grabmouse(this);
            return(true);
        }

        @Override
        public boolean mouseup(Coord c, int button) {
            if(a && button == 1) {
                a = false;
                ui.grabmouse(null);
                if(c.isect(new Coord(0, 0), sz))
                    click();
                return(true);
            }
            return(false);
        }

        private void click() {
            cb.a = !cb.a;
            wdgmsg("changed", name, cb.a);
        }

        @Override
        public void wdgmsg(Widget sender, String msg, Object... args) {
            if (msg.equals("ch")) {
                wdgmsg("changed", name, args[0]);
            } else if (msg.equals("activate")) {
                wdgmsg("delete", name);
            } else {
                super.wdgmsg(sender, msg, args);
            }
        }
    }
}
