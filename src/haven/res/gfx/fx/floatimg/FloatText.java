package haven.res.gfx.fx.floatimg;

import haven.*;
import java.awt.Color;

public class FloatText
        extends FloatSprite {
    public static final Text.Foundry fnd = new Text.Foundry("SansSerif", 10);

    public FloatText(Sprite.Owner owner, Resource resource, String string, Color color) {
        super(owner, resource, new TexI(Utils.outline2(FloatText.fnd.render((String)Utils.generate_dmg_string(owner, string, color), (Color)color).img, Utils.contrast(color))), 2000);

        String oN = "";
        Color altCol = null;

        Gob g = (Gob)owner;
        try {
            if (g.id == MapView.plgob) {
                oN = MainFrame.cName != null ? MainFrame.cName : "Player";
            }
        } catch (Exception exception) {
            // empty catch block
        }
        if (oN == "") {
            try {
                KinInfo ki = g.getattr(KinInfo.class);
                if (ki != null) {
                    oN = ki.name;
                    altCol = BuddyWnd.gc[ki.group];
                }
            } catch (Exception ki) {
                // empty catch block
            }
        }
        if (oN == "") {
            try {
                ResDrawable rd = g.getattr(ResDrawable.class);
                oN = rd.res.get().name;
            } catch (Exception rd) {
                // empty catch block
            }
        }
        if (oN == "") {
            try {
                Composite co = g.getattr(Composite.class);
                oN = co.base.get().name;
            } catch (Exception exception) {
                // empty catch block
            }
        }
        if (oN.contains("/") && altCol == null) {
            oN = oN.split("/")[oN.split("/").length - 1];
        }
        if (oN.equals("body")) {
            oN = "Player";
        }
        oN = oN + ": ";
        float dmg = 0;
        try {
            dmg = Float.parseFloat(string);
        } catch (Exception ignored) {
            // empty catch block
        }
        if(dmg > 0){
            UI.instance.gui.syslog.append("" + oN + string +
                    " red: " + String.format("%.2f", g.red_damage) +
                    " black: " + String.format("%.2f", g.black_damage) +
                    " blue " + String.format("%.2f", g.blue_damage) +
                    " yellow: " + String.format("%.2f", g.yellow_damage) +
                    " " + color, altCol == null ? color : altCol);
        } else {
            UI.instance.gui.syslog.append("" + oN + string, altCol == null ? color : altCol);
        }
    }
}