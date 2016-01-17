package haven;


import java.awt.event.KeyEvent;

class LocatorTool extends Window{
    static final String title = "Locator tool";
    static final String defaulttext = "Not homed in yet.";
    
    private static Coord location = Coord.z;
    private final Label text;
    
    private static boolean grabbing = false;
    
    private static LocatorTool instance; 

    public LocatorTool(Coord c, Widget parent) {
	super(c, new Coord(350, 100), parent, title);
        
        if(((GameUI)parent).map.player()!=null)
        {
            Gob pl = ((GameUI)parent).map.player();
            Moving m = pl.getattr(Moving.class);
            if(m!=null){
                if(m instanceof LinMove)
                {
                    LinMove gobpath = (LinMove) m;
                    this.location = gobpath.t.sub(gobpath.s).div(11);
                }
            }
        }

	this.text = new Label(Coord.z, this, defaulttext);
        if( !location.equals(Coord.z) )
        {
            this.text.settext("Target is at "+location+" relative to your location");
        }
	toggle();
        
        new Button(new Coord(0,25), 65, this, "Grab"){
            @Override
            public void click(){
                grabbing = true;
            }
        };
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
        this.pack();
    }
    
    private final void setlocation(Coord loc){
        this.location = loc;
        this.settext("Target is at "+location+" relative to your location");        
    }
    
    public static void addPath(LinMove gobpath){
        if(!grabbing) return;
        
        Coord loc = gobpath.t.sub(gobpath.s).div(11);
        if(instance!=null)
            instance.setlocation(loc);
        
        grabbing = false;
    }
}