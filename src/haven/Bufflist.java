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

import java.awt.Color;

public class Bufflist extends Widget {
    static final Tex frame = Resource.loadtex("gfx/hud/buffs/frame");
    static final Tex cframe = Resource.loadtex("gfx/hud/buffs/cframe");
    static final Tex ameter = Resource.loadtex("gfx/hud/buffs/cbar");
    static final Coord imgoff = new Coord(6, 6);
    static final Coord ameteroff = new Coord(4, 52);
    static final Coord cmeteroff = new Coord(20, 20), cmeterul = new Coord(-20, -20), cmeterbr = new Coord(20, 20);
    static final int margin = 2;
    static final int num = 15;
    
    @RName("buffs")
    public static class $_ implements Factory {
	public Widget create(Coord c, Widget parent, Object[] args) {
	    return(new Bufflist(c, parent));
	}
    }
    
    public Bufflist(Coord c, Widget parent) {
	super(c, new Coord((num * frame.sz().x) + ((num - 1) * margin), cframe.sz().y), parent);
    }
    
    public void draw(GOut g) {
	int i = 0;
	int w = frame.sz().x + margin;
	synchronized(ui.sess.glob.buffs) {
	    for(Buff b : ui.sess.glob.buffs.values()) {
		if(!b.major)
		    continue;
		Coord bc = new Coord(i * w, 0);
		if(b.ameter >= 0) {
		    g.image(cframe, bc);
		    g.image(ameter, bc.add(ameteroff), bc.add(ameteroff), new Coord((b.ameter * ameter.sz().x) / 100, ameter.sz().y));
		} else {
		    g.image(frame, bc);
		}
		try {
		    Tex img = b.res.get().layer(Resource.imgc).tex();
		    g.image(img, bc.add(imgoff));
		    if(b.nmeter >= 0) {
			Tex ntext = b.nmeter();
			g.image(ntext, bc.add(imgoff).add(img.sz()).add(ntext.sz().inv()).add(-1, -1));
		    }
		    if(b.cmeter >= 0) {
			g.chcolor(255, 255, 255, 128);
			g.prect(bc.add(imgoff).add(cmeteroff), cmeterul, cmeterbr, Math.PI * 2 * b.getCstate());
			g.chcolor();
                        Tex ctext = b.cmeter();
                        g.image(ctext, bc.add(imgoff).add(img.sz()).add(ctext.sz().inv()).add(-1, -1));
		    }
		} catch(Loading e) {}
		if(++i >= num)
		    break;
	    }
	}
    }
    
    private long hoverstart;
    private Tex shorttip, longtip;
    private String tipped;
    public Object tooltip(Coord c, Widget prev) {
	long now = System.currentTimeMillis();
	if(prev != this)
	    hoverstart = now;
	int i = 0;
	int w = frame.sz().x + margin;
	synchronized(ui.sess.glob.buffs) {
	    for(Buff b : ui.sess.glob.buffs.values()) {
		if(!b.major)
		    continue;
		Coord bc = new Coord(i * w, 0);
		if(c.isect(bc, frame.sz())) {
		    String tt = b.tooltip();
		    if(tipped != tt)
			shorttip = longtip = null;
		    tipped = tt;
		    try {
			if(now - hoverstart < 1000) {
			    if(shorttip == null)
				shorttip = Text.render(tt).tex();
			    return(shorttip);
			} else {
			    if(longtip == null) {
				String text = RichText.Parser.quote(tt);
				Resource.Pagina pag = b.res.get().layer(Resource.pagina);
				if(pag != null)
				    text += "\n\n" + pag.text;
				longtip = RichText.render(text, 200).tex();
			    }
			    return(longtip);
			}
		    } catch(Loading e) {
			return("...");
		    }
		}
		if(++i >= num)
		    break;
	    }
	}
	return(null);
    }
}
