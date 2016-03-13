package haven.plugins;

import haven.Config;
import haven.Glob;
import haven.HotkeyListWindow;
import haven.Resource;
import haven.TimerPanel;
import haven.UI;
import haven.WikiBrowser;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class XTendedPaginae {

    interface PluginCallable {

        void execute(UI ui);
    }

    static Map<String, PluginCallable> dictionary = new HashMap<>();

    public static void loadXTendedPaginae(UI ui) {
        loadBaseXTendedPaginae(ui);
    }

    public static void loadBaseXTendedPaginae(UI ui) {
        Glob glob = ui.sess.glob;
        Collection<Glob.Pagina> p = glob.paginae;
        p.add(glob.paginafor(Resource.load("paginae/act/add")));
        p.add(glob.paginafor(Resource.load("paginae/add/timer")));
        dictionary.put("timers", new PluginCallable() {
            public void execute(UI ui) {
                TimerPanel.toggle();
            }
        });
        p.add(glob.paginafor(Resource.load("paginae/add/wiki")));
        dictionary.put("wiki", new PluginCallable() {
            public void execute(UI ui) {
                WikiBrowser.toggle();
            }
        });
        p.add(glob.paginafor(Resource.load("paginae/add/craft")));
        dictionary.put("craft", new PluginCallable() {
            public void execute(UI ui) {
                ui.gui.toggleCraftWnd();
            }
        });
        p.add(glob.paginafor(Resource.load("paginae/add/radius")));
        dictionary.put("radius", new PluginCallable() {
            public void execute(UI ui) {
                Config.toggleRadius();
            }
        });
        p.add(glob.paginafor(Resource.load("paginae/add/hotkey")));
        dictionary.put("hotkey", new PluginCallable() {
            public void execute(UI ui) {
                HotkeyListWindow.instance(ui).toggle();
            }
        });
    }

    public static boolean useXTended(UI ui, String[] ad) {
        boolean handled = true;
        if (ad[1].equals("act")) {
            String[] args = new String[ad.length - 2];
            System.arraycopy(ad, 2, args, 0, args.length);
            ui.gui.wdgmsg("act", (Object[]) args);
        } else if (dictionary.containsKey(ad[1])) {
            dictionary.get(ad[1]).execute(ui);
        } else {
            handled = false;
        }

        return handled;
    }
}
