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

public class HScrollport extends Widget {
    public final HSlider bar;
    public final Scrollcont cont;

    @RName("hscr")
    public static class $_ implements Factory {
	public Widget create(Coord c, Widget parent, Object[] args) {
	    return(new HScrollport(c, (Coord)args[0], parent));
	}
    }

    public HScrollport(Coord c, Coord sz, Widget parent) {
	super(c, sz, parent);
	bar = new HSlider(new Coord(0, sz.y - HSlider.h), sz.x, this, 0, 0, 0) {
		public void changed() {
		    cont.sx = bar.val;
		}
	    };
	cont = new Scrollcont(Coord.z, sz.sub(0, bar.sz.y), this) {
		public void update() {
		    bar.max = Math.max(0, csz().x - sz.x);
		}
	    };
    }

    public static class Scrollcont extends Widget {
	public int sx = 0;

	public Scrollcont(Coord c, Coord sz, Widget parent) {
	    super(c, sz, parent);
	}

	public Coord csz() {
	    Coord mx = new Coord();
	    for(Widget ch = child; ch != null; ch = ch.next) {
		if(ch.c.x + ch.sz.x > mx.x)
		    mx.x = ch.c.x + ch.sz.x;
		if(ch.c.y + ch.sz.y > mx.y)
		    mx.y = ch.c.y + ch.sz.y;
	    }
	    return(mx);
	}

	public void update() {}

	public Widget makechild(String type, Object[] pargs, Object[] cargs) {
	    Widget ret = super.makechild(type, pargs, cargs);
	    update();
	    return(ret);
	}

	public Coord xlate(Coord c, boolean in) {
	    if(in)
		return(c.add(-sx, 0));
	    else
		return(c.add(sx, 0));
	}

	public void draw(GOut g) {
	    Widget next;

	    for(Widget wdg = child; wdg != null; wdg = next) {
		next = wdg.next;
		if(!wdg.visible)
		    continue;
		Coord cc = xlate(wdg.c, true);
		if((cc.x + wdg.sz.x < 0) || (cc.x > sz.x))
		    continue;
		wdg.draw(g.reclip(cc, wdg.sz));
	    }
	}
    }

    public boolean mousewheel(Coord c, int amount) {
	bar.ch(amount * 15);
	return(true);
    }

    public Widget makechild(String type, Object[] pargs, Object[] cargs) {
	return(cont.makechild(type, pargs, cargs));
    }

    public void resize(Coord nsz) {
	super.resize(nsz);
	bar.c = new Coord(0, sz.y - bar.sz.y);
	cont.resize(sz.sub(0, bar.sz.y));
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "hpack") {
	    resize(new Coord(sz.x, cont.contentsz().y + bar.sz.y));
	} else {
	    super.uimsg(msg, args);
	}
    }
}
