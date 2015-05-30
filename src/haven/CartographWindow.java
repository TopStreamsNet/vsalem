package haven;

import haven.Defer.Future;
import haven.LocalMiniMap.MapTile;
import static haven.MCache.cmaps;
import static haven.MCache.tilesz;
import haven.OptWnd2.Frame;
import haven.Screenshooter.Shot;
import java.awt.Color;

import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class CartographWindow extends Window {
    public static CartographWindow instance = null;
        
    private final ArrayList<Marker> markers = new ArrayList<Marker>();
    
    private Marker selected_marker = null;
    
    private class Marker{
        Coord loc;
        String name;
        Text t;
        Color co;
        
        public Marker(Coord c, String s)
        {
            loc = c;
            name = s;
            co = Color.WHITE;
            t = foundry.render(s);
        }
        
        public void changeName(String s)
        {
            name = s;
            t = foundry.render(s);
        }
    }
    
    private static final RichText.Foundry foundry = new RichText.Foundry(TextAttribute.FAMILY, "SansSerif", TextAttribute.SIZE, 12);
    private static DrawnMap drawn;
    private CheckBox gridlines;
    private Button recenter, save;
    private Frame marker_info;
    
    boolean mmv = false;
    boolean rsm = false;
    private static Coord gzsz = new Coord(15,15);
    private static Coord minsz = new Coord(500,360);
    
    private class DrawnMap extends Widget {
        Coord off = new Coord();
        boolean draw_grid = false;
        boolean save_image = false;
        
        private final Map<Coord, Future<MapTile>> pcache = new LinkedHashMap<Coord, Defer.Future<MapTile>>(9, 0.75f, true) {
            private static final long serialVersionUID = 2L;
        };
        
	private DrawnMap() {
	    super(Coord.z, CartographWindow.this.sz.sub(25,150), CartographWindow.this);
	}

        @Override
	public void draw(GOut og) {
            if(ui == null || ui.gui == null || ui.gui.map == null || ui.gui.map.player()==null)
                return;
            
            Coord cc = ui.gui.map.player().rc.div(tilesz);
            final Coord plg = cc.div(cmaps);
            
            Coord tc = cc.add(off);
            Coord ulg = tc.div(cmaps);
            int dy = -tc.y + (sz.y / 2);
            int dx = -tc.x + (sz.x / 2);
            while((ulg.x * cmaps.x) + dx > 0)
                ulg.x--;
            while((ulg.y * cmaps.y) + dy > 0)
                ulg.y--;

           final LocalMiniMap lmmap = this.ui.gui.mmap;
            
            Coord s = lmmap.bg.sz();
            for(int y = 0; (y * s.y) < sz.y; y++) {
                for(int x = 0; (x * s.x) < sz.x; x++) {
                    og.image(LocalMiniMap.bg, new Coord(x*s.x, y*s.y));
                }
            }

            GOut g = og.reclipl(new Coord(), sz);
            g.gl.glPushMatrix();

            Coord cg = new Coord();
            synchronized(pcache) {
                for(cg.y = ulg.y; (cg.y * cmaps.y) + dy < sz.y; cg.y++) {
                    for(cg.x = ulg.x; (cg.x * cmaps.x) + dx < sz.x; cg.x++) {
                        Defer.Future<MapTile> f = pcache.get(cg);
                        final Coord tcg = new Coord(cg);
                        final Coord ul = cg.mul(cmaps);
                        if((f == null) && (cg.manhattan2(plg) <= 1)) {
                            f = Defer.later(new Defer.Callable<MapTile> () {
                                public MapTile call() {
                                    BufferedImage img = lmmap.drawmap(ul, cmaps, true);
                                    if(img == null){return null;}
                                    return(new MapTile(new TexI(img), ul, tcg));
                                }
                            });
                            pcache.put(tcg, f);
                        }
                        if((f == null) || (!f.done())) {
                            continue;
                        }
                        MapTile mt = f.get();
                        if(mt == null){
                            pcache.put(cg, null);
                            continue;
                        }
                        Tex img = mt.img;
                        g.image(img, ul.add(tc.inv()).add(sz.div(2)));
                    }
                }
            }
            if(draw_grid)
            {
                g.chcolor(255,255,255,255);
                int startx = (g.sz.x/2 - tc.x)%cmaps.x;
                startx = startx>0?startx:startx+cmaps.x;
                for(int x = startx; x < sz.x; x+=cmaps.x)
                {
                    g.line(new Coord(x,0), new Coord(x,sz.y),1);
                }
                int starty = (g.sz.y/2 - tc.y)%cmaps.y;
                starty = starty>0?starty:starty+cmaps.y;
                for(int y = starty; y < sz.y; y+=cmaps.y)
                {
                    g.line(new Coord(0,y), new Coord(sz.x,y),1);
                }
            }
            
            for(Marker m : markers)
            {
                Coord onscreen = m.loc.sub(off).sub(cc).add(sz.mul(0.5));
                if(onscreen.x < 15 || onscreen.y < 30 || onscreen.x > sz.x - m.t.sz().x + 11 || onscreen.y > sz.y )
                    continue;
                g.chcolor(24,24,16,200);
                g.frect(onscreen.add(-15,-30), m.t.sz().add(4,4));
                g.chcolor(m.co);
                g.rect(onscreen.add(-15,-30), m.t.sz().add(4,4));
                g.line(onscreen.add(-5,-30+m.t.sz().y+4),onscreen,2);
                g.aimage(m.t.tex(), onscreen.add(-13,-28), 0,0);
            }
            
            g.gl.glPopMatrix();
            
            if(save_image)
            {
                String path = String.format("%s/map/", Config.userhome);
                String filename = String.format("%s.png", Utils.current_date());
                try {
                    BufferedImage bi = g.getimage();
                    Screenshooter.png.write(new FileOutputStream(path+filename), bi, new Shot(new TexI(bi), null));
                } catch (IOException ex) {}
                save_image = false;
            }
	}
        
        @Override
        public boolean mousedown(Coord c, int button) {
            parent.setfocus(this);
            raise();

            if(button == 2){
                //check whether there is something
                //on the click's location
                Marker selected = null;
                for(Marker m : markers)
                {
                    Coord onscreen = m.loc.sub(off).add(sz.mul(0.5)).sub(ui.gui.map.player().rc.div(tilesz));
                    if(onscreen.x < 15 || onscreen.y < 30 || onscreen.x > sz.x - m.t.sz().x + 11 || onscreen.y > sz.y )
                        continue;
                    Coord c1 = onscreen.add(-15,-30);
                    Coord c2 = c1.add(m.t.sz().add(4,4));
                    if(c.x > c1.x && c.y > c1.y && c.y < c2.y && c.x < c2.x)
                    {
                        selected = m;
                    }
                }
                setSelectedMarker(selected);
            }
            
            if(button == 3){
                dm = true;
                ui.grabmouse(this);
                doff = c;
                mmv = false;
                return true;
            }

            return super.mousedown(c, button);
        }
        
        @Override
        public boolean mouseup(Coord c, int button) {
            if(button == 3){
                if(!mmv)
                {
                    if(selected_marker != null)
                    {
                        selected_marker.loc = c.sub(sz.div(2)).add(off).add(ui.gui.map.player().rc.div(tilesz));
                    }
                    else
                    {
                        Marker newm = new Marker(c.sub(sz.div(2)).add(off).add(ui.gui.map.player().rc.div(tilesz)), "Marker");
                        markers.add(newm);
                        setSelectedMarker(newm);                        
                    }
                }
                dm = false; 
                ui.grabmouse(null);  
                return true;
            }
           
            return super.mouseup(c, button);
        }
        
        @Override
        public void mousemove(Coord c) {
            mmv = true;
            
            Coord d;
            if(dm){
                d = c.sub(doff);
                off = off.sub(d);
                doff = c;
                return;
            }
                
            super.mousemove(c);
        }
        
        public void savePicture()
        {
            save_image = true;
        }
    }
    
    public CartographWindow(Coord c, Widget parent) {
	super(c, new Coord(500, 360), parent, "Cartograph");
	justclose = true;
        drawn = new DrawnMap();
        
        //checkbox for the grid drawing
        gridlines = new CheckBox(new Coord(15,sz.y-145), this, "Display grid lines")
        {
            @Override
            public void changed(boolean val) {
                drawn.draw_grid = val;
            }
        };
        gridlines.a = false;
        
        recenter = new Button(new Coord(15,sz.y-120), 100, this, "Re-center"){
            @Override
            public void click() {
                drawn.off = Coord.z;
            }
        };
        
        save = new Button(new Coord(15,sz.y-80), 100, this, "Save map to file"){
            @Override
            public void click() {
                drawn.savePicture();
            }
        };
        
        setSelectedMarker(selected_marker);
    }
    
    private void setSelectedMarker(Marker selected)
    {
        selected_marker = selected;
        
        if(marker_info != null) marker_info.destroy();
        marker_info = new Frame(new Coord(130, sz.y-145), new Coord(350,85), this){
            @Override
            public void draw(GOut og) {
                super.draw(og);
                
            }
        };
        new Label(new Coord(10,10), marker_info, "Marker info:");
        
        if(selected_marker != null)        
        {
            new Label(new Coord(30,30), marker_info, "Marker name:");
            new TextEntry(new Coord(110,30),100,marker_info,selected_marker.name){
                public void activate(String text) {
                    selected_marker.changeName(text);
                }
            };
            new Label(new Coord(30,60), marker_info, "Marker color:");
            
            new TextEntry(new Coord(110,60),100,marker_info,colorHex(selected_marker.co)){
                public void activate(String text) {
                    Color c = null;
                    try{
                        c = Color.decode(text);
                    }
                    catch(NumberFormatException nfe){}
                    if(c!=null)
                        selected_marker.co = c;
                }
            };
            
            new Button(new Coord(250, 45), 50, marker_info, "Delete")
            {
                @Override
                public void click() {
                    markers.remove(selected_marker);
                    setSelectedMarker(null);
                }
            };
        }
    }
        
    private String colorHex(Color co){
        String s = "#" + Integer.toHexString(co.getRed()) + Integer.toHexString(co.getGreen())+ Integer.toHexString(co.getBlue());
        return s;
    }
    
    public void wdgmsg(Widget sender, String msg, Object... args) {
	if(sender == cbtn)
	    super.wdgmsg(sender, msg, args);
    }

    public static void toggle() {
	UI ui = UI.instance;
	if(instance == null){
	    instance = new CartographWindow(ui.gui.sz.sub(500, 360).div(2), ui.gui);
	} else {
	    ui.destroy(instance);
	}
    }

    @Override
    public void destroy() {
	instance = null;
	super.destroy();
    }

    public static void close() {
	if(instance != null){
	    UI ui = UI.instance;
	    ui.destroy(instance);
	}
    }
    
    @Override
    public boolean mousedown(Coord c, int button) {
	parent.setfocus(this);
	raise();

	if (button == 1) {
	    ui.grabmouse(this);
	    doff = c;
	    if(c.isect(sz.sub(gzsz), gzsz)) {
		rsm = true;
		return true;
	    }
	}
	return super.mousedown(c, button);
    }

    @Override
    public boolean mouseup(Coord c, int button) {
	if (button == 1 && rsm){
	    ui.grabmouse(null);
	    rsm = false;
	    storeOpt("_sz", sz);
	}
        
	return super.mouseup(c, button);
    }

    @Override
    public void mousemove(Coord c) {
	Coord d;
	if (rsm){
	    d = c.sub(doff);
	    Coord newsz = sz.add(d);
	    newsz.x = Math.max(minsz.x, newsz.x);
	    newsz.y = Math.max(minsz.y, newsz.y);
	    doff = c;
            drawn.resize(newsz.sub(25,150));
            sresize(newsz);
	} else {
	    super.mousemove(c);
	}
    }
    
    private void sresize(Coord sz) {
	IBox box;
	int th;
	if(cap == null){
	    box = wbox;
	    th = 0;
	} else {
	    box = topless;
	    th = Window.th;
	}
	this.sz = sz;
	ctl = box.btloff().add(0, th);
	csz = sz.sub(box.bisz()).sub(0, th);
	atl = ctl.add(mrgn);
	asz = csz.sub(mrgn.mul(2));
	ac = new Coord();
	//ac = tlo.add(wbox.btloff()).add(mrgn);
	placetwdgs();
	for(Widget ch = child; ch != null; ch = ch.next)
	    ch.presize();
        
        gridlines.c = new Coord(15,sz.y-145);
        recenter.c = new Coord(15,sz.y-120);
        save.c = new Coord(15,sz.y-80);
        marker_info.c = new Coord(130, sz.y-145);
    }
}