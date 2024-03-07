package haven.automation;

import haven.Coord;
import haven.MapView;

import java.util.ArrayList;

public class MapViewExt {
    private final MapView mv;

    public MapViewExt(final MapView mv) {
        this.mv = mv;
    }

    public void markForScript(final Coord mc) {
        haven.automation.Utils.dispatchmsg(mv, "click-tile", mc);
    }
}
