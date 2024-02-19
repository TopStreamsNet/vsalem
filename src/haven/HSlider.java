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

import java.awt.image.BufferedImage;

public class HSlider extends Widget {
    public static final Tex sflarp = Resource.loadtex("gfx/hud/sflarp");
    public static final Tex schain;
    public static final int h = sflarp.sz().y;
    public int val, min, max;
    private boolean drag = false;

    static {
	BufferedImage vc = Resource.loadimg("gfx/hud/schain");
	BufferedImage hc = TexI.mkbuf(new Coord(vc.getHeight(), vc.getWidth()));
	for(int y = 0; y < vc.getHeight(); y++) {
	    for(int x = 0; x < vc.getWidth(); x++)
		hc.setRGB(y, x, vc.getRGB(x, y));
	}
	schain = new TexI(hc);
    }

    public HSlider(Coord c, int w, Widget parent, int min, int max, int val) {
	super(c, new Coord(w, h), parent);
	this.val = val;
	this.min = min;
	this.max = max;
    }

    public boolean vis() {
	return(max > min);
    }
    
    public void draw(GOut g) {
	if(vis()) {
	    int cy = (sflarp.sz().y - schain.sz().y) / 2;
	    for(int x = 0; x < sz.x; x += schain.sz().x)
		g.image(schain, new Coord(x, cy));
	    int fx = ((sz.x - sflarp.sz().x) * val) / (max - min);
	    g.image(sflarp, new Coord(fx, 0));
	}
    }
    
    public boolean mousedown(Coord c, int button) {
	if(button != 1)
	    return(false);
	if(!vis())
	    return(false);
	drag = true;
	ui.grabmouse(this);
	mousemove(c);
	return(true);
    }
    
    public void mousemove(Coord c) {
	if(drag) {
	    double a = (double)(c.x - (sflarp.sz().x / 2)) / (double)(sz.x - sflarp.sz().x);
	    if(a < 0)
		a = 0;
	    if(a > 1)
		a = 1;
	    val = (int)Math.round(a * (max - min)) + min;
	    changed();
	}
    }
    
    public boolean mouseup(Coord c, int button) {
	if(button != 1)
	    return(false);
	if(!drag)
	    return(false);
	drag = false;
	ui.grabmouse(null);
	return(true);
    }

    public void changed() {}
    
    public void ch(int a) {
	int val = this.val + a;
	if(val > max)
	    val = max;
	if(val < min)
	    val = min;
	if(this.val != val) {
	    this.val = val;
	    changed();
	}
    }
    
    public void resize(int w) {
	super.resize(new Coord(w, h));
    }
}
