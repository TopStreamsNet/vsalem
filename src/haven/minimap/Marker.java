package haven.minimap;

import haven.*;
import java.awt.Color;

public class Marker implements Comparable{
    public final String name;
    public final Gob gob;
    public final MarkerTemplate template;

    public int compareTo(Object that) {
        return this.template.order - ((Marker) that).template.order;
    }

    private boolean override=false;
    private String override_name;
    private Color override_color;
    
    public static enum Shape{
	CIRCLE(10),
	TRIANGLE(13),
	TRIANGLED(13),
	DIAMOND(13),
	SQUARE(9),
	PENTAGON(13);
	
	public Tex tex;
	public int sz;

	Shape(int sz){
	    this.sz = sz;
	}
	
	public static Shape get(String val){
	    if(val.equals("circle")){
		return CIRCLE;
	    } else if(val.equals("up")){
		return TRIANGLE;
	    } else if(val.equals("down")){
		return TRIANGLED;
	    } else if(val.equals("diamond")){
		return DIAMOND;
	    } else if(val.equals("square")){
		return SQUARE;
	    } else if(val.equals("pentagon")){
		return PENTAGON;
	    }
	    return CIRCLE;
	}
	
    }
    
    public Marker(String name, Gob gob, MarkerTemplate template) {
        this.name = name;
        this.gob = gob;
        this.template = template;
        if(template.shape.tex == null){
            template.shape.tex = Utils.generateMarkerTex(template.shape);
        }
    }
    
    public boolean hit(Coord c) {
        Coord3f ptc3f = gob.getc();
        if (ptc3f == null)
            return false;
        Coord p = new Coord((int)ptc3f.x, (int)ptc3f.y);
	int radius = 4;
	return (c.x - p.x) * (c.x - p.x) + (c.y - p.y) * (c.y - p.y) < radius * radius * MCache.tilesz.x * MCache.tilesz.y;
    }

    public void override(String name, Color c)
    {
        this.override = true;
        this.override_name = name;
        this.override_color = c;
    }
    
    public String getTooltip()
    {
        if(override)
        {
            return override_name;
        }
        else{
            return this.template.tooltip;
        }
    }
    
    public void draw(GOut g, Coord c) {
        draw(g,c,1.0);
    }
    
    public void draw(GOut g, Coord c, double scale) {
        Coord3f ptc3f = gob.getc();
        if (ptc3f == null)
            return;
        Coord ptc = new Coord((int)ptc3f.x, (int)ptc3f.y);
        ptc = ptc.div(MCache.tilesz).add(c);
        if(Config.radar_icons){
            try {
        	GobIcon icon = gob.getattr(GobIcon.class);
        	if(icon != null && template.showicon) {
        	    Tex tex = icon.tex();
        	    g.image(tex, ptc.sub(tex.sz().div(2)), tex.sz().div(scale));
        	    return;
        	}
            } catch(Loading l) {}
        }
        g.chcolor(template.color);
        if(override)
            g.chcolor(override_color);
        Tex tex = template.shape.tex;
	g.image(tex, ptc.sub(tex.sz().div(2)), tex.sz().div(scale));
        g.chcolor();
    }
}
