package haven;


import java.awt.event.KeyEvent;

class LocatorTool extends Window{
    static final String title = "Locator tool";
    static final String defaulttext = "Not followed anyone yet";

    private Coord location = Coord.z;
    private final Label text;

    private static LocatorTool instance;

    public LocatorTool(Coord c, Widget parent) {
        super(c, new Coord(350, 100), parent, title);

        this.text = new Label(Coord.z, this, defaulttext);
        if( !location.equals(Coord.z) )
        {
            this.text.settext("Target is at "+location+" relative to your location");
        }
        //toggle();
        this.pack();
    }

    public static LocatorTool instance(UI ui) {
        if(instance == null){
            instance = new LocatorTool(new Coord(100, 100), ui.gui);
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
        this.visible = !this.visible;
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

    private final void settext(String text) {
        this.text.settext(text);
    }

    public static void setlocation(Coord location){
        if(instance != null)
        {
            instance.settext("Target is at "+location+" relative to your location");
            instance.location = location;
            instance.pack();
        }
    }
}