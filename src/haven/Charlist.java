/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import java.awt.event.KeyEvent;
import java.util.*;
import java.awt.image.BufferedImage;
import java.util.List;
import haven.integrations.map.Navigation;


public class Charlist extends Widget {
    public static final Tex bg = Resource.loadtex("gfx/hud/avakort");
    public static final int margin = 1;
    public static final int bmargin = 46;
    public static final BufferedImage[] clu = {
	Resource.loadimg("gfx/hud/login/cluu"),
	Resource.loadimg("gfx/hud/login/clud"),
	Resource.loadimg("gfx/hud/login/cluh"),
    };
    public static final BufferedImage[] cld = {
	Resource.loadimg("gfx/hud/login/cldu"),
	Resource.loadimg("gfx/hud/login/cldd"),
	Resource.loadimg("gfx/hud/login/cldh"),
    };
    public int height, y;
    public IButton sau, sad;
    public List<Char> chars = new ArrayList<Char>();
    
    //project alphabet
    private boolean charschanged = false;
    public List<Char> alphachars = new ArrayList<Char>();
    
    //project picky
    private boolean filterchanged = false;
    private boolean prefchanged = true;
    private String filterstring = "";
    public List<Char> filteredchars = new ArrayList<Char>();
    
    
    public static class Char {
	static Text.Furnace tf = new Text.Imager(new Text.Foundry(new java.awt.Font("Serif", java.awt.Font.PLAIN, 20), java.awt.Color.WHITE).aa(true)) {
		protected BufferedImage proc(Text text) {
		    return(PUtils.rasterimg(PUtils.blurmask2(text.img.getRaster(), 1, 1, java.awt.Color.BLACK)));
		}
	    };
	public String name;
	Text nt;
	// Avaview ava;
	Button plb;
	
	public Char(String name) {
	    this.name = name;
	    nt = tf.render(name);
	}
        
        public static class CharComparator implements Comparator<Char>
        {
            public int compare(Char c1, Char c2){
                return c1.name.compareTo(c2.name);
            }
        }
    }
    
    @RName("charlist")
    public static class $_ implements Factory {
	public Widget create(Coord c, Widget parent, Object[] args) {
	    return(new Charlist(c, parent, (Integer)args[0]));
	}
    }

    public Charlist(Coord c, Widget parent, int height) {
	super(c, new Coord(clu[0].getWidth(), (bmargin * 2) + (bg.sz().y * height) + (margin * (height - 1))), parent);
	this.height = height;
	y = 0;
	sau = new IButton(new Coord(0, 0), this, clu[0], clu[1], clu[2]) {
		public void click() {
		    scroll(-1);
		}
	    };
	sad = new IButton(new Coord(0, sz.y - cld[0].getHeight() - 1), this, cld[0], cld[1], cld[2]) {
		public void click() {
		    scroll(1);
		}
	    };
	sau.hide();
	sad.hide();
        
        //project alphabet
        CheckBox alphacheck = new CheckBox(new Coord(50,sz.y-30),this,"Alphabetical sorting"){
            @Override
            public void changed(boolean val) {
                super.changed(val);
                Config.alphasort = val;
                Utils.setprefb("alphasort", val);
                prefchanged = true;
            }

            {tooltip = Text.render("Sorts the list of characters alphabetically rather than by last log-in.");}
        };
        alphacheck.a = Config.alphasort;
        CheckBox reverselist = new CheckBox(new Coord(20,sz.y-30),this,""){
            @Override
            public void changed(boolean val) {
                super.changed(val);
                Config.reversesort = val;
                Utils.setprefb("reversesort", val);
            }

            {tooltip = Text.render("Reverses sorting of the list.");}
        };
        reverselist.a = Config.reversesort;
        
        TextEntry filter = new TextEntry(new Coord(225, sz.y-30), new Coord(60,18), this, ""){
            @Override
            public void changed(){
                super.changed();
                filterchanged = true;
                filterstring = this.text;
            }
            @Override
            public boolean type(char c, KeyEvent ke){
                if(c == ':'){
                    ui.root.entercmd();
                    return true;
                }
                return super.type(c, ke);
            }
        };
        filter.changed();
        
        Button logoutbutton = new Button(new Coord(295, sz.y-32),80, this, "Logout"){
            @Override
            public void click() {
                try {
                    ui.cons.run("lo");
                } catch (Exception ex) {
                    //this is really only thrown when the command doesn't exist
                    throw(new RuntimeException(ex));
                }
            }
        };
    }
    
    public void scroll(int amount) {
	y += amount;
	synchronized(chars) {
	    if(y > chars.size() - height)
		y = chars.size() - height;
	}
	if(y < 0)
	    y = 0;
    }
    
    public void draw(GOut g) {
	Coord cc = new Coord((clu[0].getWidth() - bg.sz().x) / 2, bmargin);
	synchronized(chars) {
            //project alphabet
            if(charschanged)
            {
                alphachars = new ArrayList<Char>(chars);
                Collections.sort(alphachars, new Char.CharComparator());
            }
            
            //project picky
            if(filterchanged || charschanged || prefchanged)
            {
                filteredchars = new ArrayList<Char>();
                for(Char c : (Config.alphasort?alphachars:chars))
                {
                    if("".equals(filterstring) || c.name.contains(filterstring))
                        filteredchars.add(c);
                }
            }

            if(filterchanged)
            {
                filterchanged = false;
                Utils.setpref("namefilt", filterstring);
            }
            if(charschanged)
                charschanged = false;
            if(prefchanged)
                prefchanged = false;
            
            for(Char c : chars) {
		// c.ava.hide();
		c.plb.hide();
	    }
            int begin = Config.reversesort?filteredchars.size()-1:0;
            int step = Config.reversesort?-1:1;
            int end1 = Config.reversesort?1-this.y:filteredchars.size() - this.y;
            int end1manip = Config.reversesort?-1:1;
            int end2 = Config.reversesort?height+1-filteredchars.size():height;
            int end2manip = Config.reversesort?-1:1;
            int ymanip = Config.reversesort?-1:1;
	    for(int i = begin; (i*end2manip < end2) && (i*end1manip < end1); i+=step) {
                if(i+this.y*ymanip<0)
                    continue;
		Char c = filteredchars.get(i + this.y*ymanip);
		g.image(bg, cc);
		// c.ava.show();
		c.plb.show();
		// int off = (bg.sz().y - c.ava.sz.y) / 2;
		// c.ava.c = new Coord(off, off + y);
		c.plb.c = cc.add(bg.sz()).sub(110, 30);
		// g.image(c.nt.tex(), new Coord(off + c.ava.sz.x + 5, off + y));
		g.image(c.nt.tex(), cc.add(15, 10));
		cc = cc.add(0, bg.sz().y + margin);
	    }
            if(filteredchars.size() > height) {
		    sau.show();
		    sad.show();
            }            
            else{
                sau.hide();
                sad.hide();
            }
	}
	super.draw(g);
    }
    
    public boolean mousewheel(Coord c, int amount) {
	scroll(amount);
	return(true);
    }
    
    public void wdgmsg(Widget sender, String msg, Object... args) {
	if(sender instanceof Button) {
	    synchronized(chars) {
		    for(Char c : chars) {
		        if(sender == c.plb){
			        Config.setCharName(c.name);
			        wdgmsg("play", c.name);
                    Navigation.setCharacterName(c.name);
		        }
		    }
	    }
	} else if(sender instanceof Avaview) {
	} else {
	        super.wdgmsg(sender, msg, args);
	    }
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "add") {
	    Char c = new Char((String)args[0]);
	    List<Indir<Resource>> resl = new LinkedList<Indir<Resource>>();
	    for(int i = 1; i < args.length; i++)
		resl.add(ui.sess.getres((Integer)args[i]));
	    // c.ava = new Avaview(new Coord(0, 0), this, resl);
	    // c.ava.hide();
	    c.plb = new Button(new Coord(0, 0), 100, this, "Play");
	    c.plb.hide();
	    synchronized(chars) {
		chars.add(c);
                charschanged = true;
		if(chars.size() > height) {
		    sau.show();
		    sad.show();
		}
	    }
	}
    }
}
