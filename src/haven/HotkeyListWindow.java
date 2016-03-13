package haven;

import java.awt.event.KeyEvent;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class HotkeyListWindow extends Window{
    static final String title = "List of Hotkeys";
        
    private static HotkeyListWindow instance; 

    public HotkeyListWindow(Coord c, Widget parent) {
	super(c, new Coord(300, 100), parent, title);
        init_components();
	toggle();
	this.pack();
    }
    
    private final void init_components()
    {
        new Label(Coord.z, this, "Mouse controls");
        int y = 0,x1=30,x2=120;
        int step=15, big_step=30;
        new Label(new Coord(x1,y+=step),this,"Ctrl+left click");     new Label(new Coord(x2,y),this,"Drop from inventory.");
        new Label(new Coord(x1,y+=step),this,"Shift+left click");     new Label(new Coord(x2,y),this,"Transfer between inventories.");
        new Label(new Coord(x1,y+=step),this,"Shift+alt+left click");     new Label(new Coord(x2,y),this,"Transfer all similar items between inventories.");
        new Label(new Coord(x1,y+=step),this,"Shift+scrollwheel");     new Label(new Coord(x2,y),this,"Transfer between inventories (also construction sign slots).");
        new Label(new Coord(x1,y+=step),this,"Shift+right click");     new Label(new Coord(x2,y),this,"Interact with object and take similar item from main inventory.");
        
        new Label(new Coord(0,y+=big_step), this, "Window hotkeys");
        new Label(new Coord(x1,y+=step),this,"Ctrl+E");       new Label(new Coord(x2,y),this,"Equipment window");
        new Label(new Coord(x1,y+=step),this,"Ctrl+T");       new Label(new Coord(x2,y),this,"Study window");
        new Label(new Coord(x1,y+=step),this,"Ctrl+I/Tab");   new Label(new Coord(x2,y),this,"Inventory window");
        new Label(new Coord(x1,y+=step),this,"Ctrl+P");       new Label(new Coord(x2,y),this,"Town window");
        new Label(new Coord(x1,y+=step),this,"Ctrl+C");       new Label(new Coord(x2,y),this,"Toggle chat size");
        new Label(new Coord(x1,y+=step),this,"Ctrl+B");       new Label(new Coord(x2,y),this,"Kin window");
        new Label(new Coord(x1,y+=step),this,"Ctrl+O");       new Label(new Coord(x2,y),this,"Option window");
        new Label(new Coord(x1,y+=step),this,"Alt+S/Prnt Scrn");        new Label(new Coord(x2,y),this,"Screenshot");
        
        new Label(new Coord(0,y+=big_step), this, "Custom client features");
        new Label(new Coord(x1,y+=step),this,"Ctrl+F/Ctrl+L");       new Label(new Coord(x2,y),this,"Flatness Tool");
        new Label(new Coord(x1,y+=step),this,"Ctrl+Q");       new Label(new Coord(x2,y),this,"Locator Tool");
        new Label(new Coord(x1,y+=step),this,"Ctrl+A");       new Label(new Coord(x2,y),this,"Inventory abacus");
        new Label(new Coord(x1,y+=step),this,"Ctrl+X");       new Label(new Coord(x2,y),this,"Cartographer (unfinished)");
        new Label(new Coord(x1,y+=step),this,"Ctrl+D");       new Label(new Coord(x2,y),this,"Darkness indicator");
        new Label(new Coord(x1,y+=step),this,"Ctrl+N");       new Label(new Coord(x2,y),this,"Toggle forced non-darkness display");
        new Label(new Coord(x1,y+=step),this,"Alt+R");       new Label(new Coord(x2,y),this,"Toggle radius display (braziers, mining supports,...)");
        new Label(new Coord(x1,y+=step),this,"Alt+C");       new Label(new Coord(x2,y),this,"Open the crafting window");
        new Label(new Coord(x1,y+=step),this,"Alt+F");       new Label(new Coord(x2,y),this,"Open the filter window");
        new Label(new Coord(x1,y+=step),this,"Ctrl+Z");       new Label(new Coord(x2,y),this,"Toggle tile centering");
        new Label(new Coord(x1,y+=step),this,"Ctrl+R");       new Label(new Coord(x2,y),this,"Toggle the toolbelt");
        new Label(new Coord(x1,y+=step),this,"Ctrl+G");       new Label(new Coord(x2,y),this,"Toggle the backpack");
        
        new Label(new Coord(0,y+=big_step), this, "Handy console commands");
        new Label(new Coord(x1,y+=step),this,":fs 0/1");       new Label(new Coord(x2,y),this,"Set fullscreen (buggy!)");
        new Label(new Coord(x1,y+=step),this,":act lo");       new Label(new Coord(x2,y),this,"Log out if allowed");
        new Label(new Coord(x1,y+=step),this,":act lo cs");    new Label(new Coord(x2,y),this,"Log out to character selection if allowed");
        new Label(new Coord(x1,y+=step),this,":lo");           new Label(new Coord(x2,y),this,"Force disconnect, even if not allowed (at your own risk!)");
    }

    public static HotkeyListWindow instance(UI ui) {
	if(instance == null || instance.ui != ui){
	    instance = new HotkeyListWindow(new Coord(100, 100), ui.gui);
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