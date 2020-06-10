package haven;

import haven.pathfinder.Map;

import java.awt.event.KeyEvent;

public class SecretWindow extends Window{
    static final String title = "Secret Window";

    private static SecretWindow instance;

    public SecretWindow(Coord c, Widget parent) {
        super(c, new Coord(300, 100), parent, title);
        init_components();
        toggle();
        this.pack();
    }

    private final void init_components()
    {
        new Label(Coord.z, this, "Secret controls");
        int y = 0,x1=30,x2=120;
        int step=20, big_step=30;
        (new CheckBox(new Coord(x1,y+=step), this, "MiniMap Show Grid") {
            @Override
            public void changed(boolean val) {
                Config.mapshowgrid = val;
                Utils.setprefb("mapshowgrid", Config.mapshowgrid);
            }
        }).a = Config.mapshowgrid;

        (new CheckBox(new Coord(x1,y+=step), this, "Show Bounding Boxes") {
            @Override
            public void changed(boolean val) {
                Config.showboundingboxes = val;
                Utils.setprefb("showboundingboxes", Config.showboundingboxes);
            }
        }).a = Config.showboundingboxes;

        (new CheckBox(new Coord(x1,y+=step), this, "Auto Drink") {
            @Override
            public void changed(boolean val) {
                Config.autodrink = val;
                Utils.setprefb("autodrink", Config.autodrink);
            }
        }).a = Config.autodrink;

        (new CheckBox(new Coord(x1,y+=step), this, "Show Grid") {
            @Override
            public void changed(boolean val) {
                UI.instance.gui.map.togglegrid();
            }
        }).a = Config.showgrid;

        (new CheckBox(new Coord(x1,y+=step), this, "Disable Flavour") {
            @Override
            public void changed(boolean val) {
                Config.showflavour = val;
                Utils.setprefb("showflavour", Config.showflavour);
            }
        }).a = Config.showflavour;
    }

    public static SecretWindow instance(UI ui) {
        if(instance == null || instance.ui != ui){
            instance = new SecretWindow(new Coord(100, 100), ui.gui);
        }
        return instance;
    }

    public static void close(){
        if(instance != null){
            instance.ui.destroy(instance);
            instance = null;
        }
    }

    public void toggle(){
        this.visible=!this.visible;
    }

    @Override
    public void destroy() {
        instance = null;
        super.destroy();
    }

    public boolean type(char key, java.awt.event.KeyEvent ev) {
        if (key == KeyEvent.VK_ENTER || key == KeyEvent.VK_ESCAPE) {
            close();
            return true;
        }
        return super.type(key, ev);
    }

    @Override
    public void wdgmsg(Widget wdg, String msg, Object... args) {
        if (wdg == cbtn) {
            ui.destroy(this);
        } else {
            super.wdgmsg(wdg, msg, args);
        }
    }
}
