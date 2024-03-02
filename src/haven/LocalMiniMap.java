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

import static haven.MCache.cmaps;
import static haven.MCache.tilesz;
import static haven.Tempers.text;

import haven.Defer.Future;
import haven.MCache.LoadingMap;
import haven.minimap.Marker;
import haven.minimap.Radar;
import haven.pathfinder.Tile;
import haven.resutil.RidgeTile;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import javax.imageio.ImageIO;

public class LocalMiniMap extends Window implements Console.Directory{
	private static final String OPT_SZ = "_sz";
	static Tex bg = Resource.loadtex("gfx/hud/bgtex");
	public static final Resource plx = Resource.load("gfx/hud/mmap/x");
	private static final Tex gridblue = Resource.loadtex("gfx/hud/mmap/gridblue");
	private static final Tex gridred = Resource.loadtex("gfx/hud/mmap/gridred");
	public final MapView mv;
	private Coord cc = null;
	public Coord cgrid = null;
	private Coord off = new Coord();
	boolean rsm = false;
	boolean dm = false;
	private static Coord gzsz = new Coord(15,15);
	public int scale = 4;
	private static final Coord minsz = new Coord(125, 125);
	private static final double scales[] = {0.5, 0.66, 0.8, 0.9, 1, 1.25, 1.5, 1.75, 2};
	private Coord sp;
	private String session;
	private final Map<String, Console.Command> cmdmap = new TreeMap<String, Console.Command>();
	private boolean radarenabled = true;
	private int height = 0;
	private Future<BufferedImage> heightmap;
	private Coord lastplg;
	private final Coord hmsz = cmaps.mul(3);

	private static final String OPT_LOCKED = "_locked";
	private static final BufferedImage ilockc = Resource.loadimg("gfx/hud/lockc");
	private static final BufferedImage ilockch = Resource.loadimg("gfx/hud/lockch");
	private static final BufferedImage ilocko = Resource.loadimg("gfx/hud/locko");
	private static final BufferedImage ilockoh = Resource.loadimg("gfx/hud/lockoh");
	private IButton lockbtn;
	boolean locked;

	private IButton gridbtn;
	private static final BufferedImage igrid = Resource.loadimg("gfx/hud/wndmap/btns/grid");

	private final Map<Coord, Future<MapTile>> cache = new LinkedHashMap<Coord, Defer.Future<MapTile>>(9, 0.75f, true) {
		private static final long serialVersionUID = 1L;

		protected boolean removeEldestEntry(Map.Entry<Coord, Defer.Future<MapTile>> eldest) {
			if(size() > 75) {
				try {
					MapTile t = eldest.getValue().get();
					t.img.dispose();
				} catch(RuntimeException e) {
				}
				return(true);
			}
			return(false);
		}
	};

	public static class MapTile {
		public final Tex img;
		public final Coord ul, c;

		public MapTile(Tex img, Coord ul, Coord c) {
			this.img = img;
			this.ul = ul;
			this.c = c;
		}
	}

	private BufferedImage tileimg(int t, BufferedImage[] texes) throws Loading {
		BufferedImage img = texes[t];
		if (img == null) {
			Resource r = ui.sess.glob.map.tilesetr(t);
			if (r == null)
				return (null);
			Resource.Image ir = r.layer(Resource.imgc);
			if (ir == null)
				return (null);
			img = ir.img;
			texes[t] = img;
		}
		return (img);
	}

	public BufferedImage drawmap(Coord ul, Coord sz, boolean pretty) {
		BufferedImage[] texes = new BufferedImage[256];
		MCache m = UI.instance.sess.glob.map;
		BufferedImage buf = TexI.mkbuf(sz);
		Coord c = new Coord();
		for(c.y = 0; c.y < sz.y; c.y++) {
			for(c.x = 0; c.x < sz.x; c.x++) {
				Coord c2 = ul.add(c);
				int t;
				try{
					t = m.gettile(c2);
				} catch (LoadingMap e) {
					return null;
				}
				try {
					BufferedImage tex = tileimg(t, texes);
					int rgb = 0x000000ff;
					if (tex != null) {
						Coord tc = pretty?c2:c;
						rgb = tex.getRGB(Utils.floormod(tc.x, tex.getWidth()), Utils.floormod(tc.y, tex.getHeight()));
					}
					buf.setRGB(c.x, c.y, rgb);
				} catch (Loading e){
					return null;
				}

				try {
					if((m.gettile(c2.add(-1, 0)) > t) ||
							(m.gettile(c2.add( 1, 0)) > t) ||
							(m.gettile(c2.add(0, -1)) > t) ||
							(m.gettile(c2.add(0,  1)) > t))
						buf.setRGB(c.x, c.y, Color.BLACK.getRGB());
				} catch (LoadingMap e) {
					continue;
				}
			}
		}
		if(Config.localmm_ridges)
			drawRidges(ul, sz, m, buf, c);
		return(buf);
	}

	private static void drawRidges(Coord ul, Coord sz, MCache m, BufferedImage buf, Coord c) {
		for(c.y = 1; c.y < sz.y - 1; c.y++) {
			for(c.x = 1; c.x < sz.x - 1; c.x++) {
				int t = m.gettile(ul.add(c));
				Tiler tl = m.tiler(t);
				if(tl instanceof RidgeTile) {
					if(((RidgeTile)tl).ridgep(m, ul.add(c))) {
						m.sethitmap(ul.add(c), Tile.RIDGE);
						for(int y = c.y; y <= c.y + 1; y++) {
							for(int x = c.x; x <= c.x + 1; x++) {
								int rgb = buf.getRGB(x, y);
								rgb = (rgb & 0xff000000) |
										(((rgb & 0x00ff0000) >> 17) << 16) |
										(((rgb & 0x0000ff00) >> 9) << 8) |
										(((rgb & 0x000000ff) >> 1) << 0);
								buf.setRGB(x, y, rgb);
							}
						}
					}
				}
			}
		}
	}

	private Future<BufferedImage> getheightmap(final Coord plg){
		Future<BufferedImage> f = Defer.later(new Defer.Callable<BufferedImage> () {
			public BufferedImage call() {
				return drawheightmap(plg);
			}
		});
		return f;
	}

	public BufferedImage drawheightmap(Coord plg) {
		MCache m = ui.sess.glob.map;
		Coord ul = (plg.sub(1, 1)).mul(cmaps);
		BufferedImage buf = TexI.mkbuf(hmsz);
		Coord c = new Coord();
		int MAX = Integer.MIN_VALUE;
		int MIN = Integer.MAX_VALUE;

		try{
			for(c.y = 0; c.y < hmsz.y; c.y++) {
				for(c.x = 0; c.x < hmsz.x; c.x++) {
					Coord c2 = ul.add(c);
					int t = m.getz(c2);
					if(t > MAX) {MAX = t;}
					if(t < MIN) {MIN = t;}
				}
			}
		} catch (LoadingMap e) {
			return null;
		}

		int SIZE = MAX - MIN;

		for(c.y = 0; c.y < hmsz.y; c.y++) {
			for(c.x = 0; c.x < hmsz.x; c.x++) {
				Coord c2 = ul.add(c);
				int t2 = m.getz(c2);
				int t = Math.max(t2, MIN);
				t = Math.min(t,  MAX);
				t = t - MIN;
				if(SIZE>0){
					t = (255*t)/SIZE;
					t = t|(t<<8)|(t<<16)|height;
				} else {
					t = 0x00FFFFFF|height;
				}
				buf.setRGB(c.x, c.y, t);
				try {
					if((m.getz(c2.add(-1, 0)) > (t2+11)) ||
							(m.getz(c2.add( 1, 0)) > (t2+11)) ||
							(m.getz(c2.add(0, -1)) > (t2+11)) ||
							(m.getz(c2.add(0,  1)) > (t2+11)))
						buf.setRGB(c.x, c.y, Color.RED.getRGB());
				} catch (LoadingMap e) {
					continue;
				}
			}
		}
		return(buf);
	}

	public LocalMiniMap(Coord c, Coord sz, Widget parent, MapView mv) {
		super(c, sz, parent, "mmap");
		cap = null;
		this.mv = mv;
		cmdmap.put("radar", new Console.Command() {
			public void run(Console console, String[] args) throws Exception {
				if (args.length == 2) {
					String arg = args[1];
					if (arg.equals("on")) {
						radarenabled = true;
						return;
					}
					else if (arg.equals("off")) {
						radarenabled = false;
						return;
					}
					else if (arg.equals("reload")) {
						ui.sess.glob.oc.radar.reload(null);
						return;
					}
				}
				throw new Exception("No such setting");
			}
		});

		lockbtn = new IButton(new Coord(-10,-43), this, locked?ilockc:ilocko, locked?ilocko:ilockc, locked?ilockch:ilockoh) {
			public void click() {
				locked = !locked;
				if(locked) {
					up = ilockc;
					down = ilocko;
					hover = ilockch;
				} else {
					up = ilocko;
					down = ilockc;
					hover = ilockoh;
				}
				storeOpt(OPT_LOCKED, locked);
			}
		};
		lockbtn.recthit = true;

		gridbtn = new IButton(new Coord(11, -43), this, igrid, igrid, igrid){
			public void click() {
				Config.mapshowgrid = !Config.mapshowgrid;
				Utils.setprefb("mapshowgrid", Config.mapshowgrid);
			}
		};
		gridbtn.recthit = true;
	}

	@Override
	protected void loadOpts() {
		super.loadOpts();
		sz = getOptCoord(OPT_SZ, sz);
	}

	public void toggleHeight(){
		if(height == 0){
			height = 0x01000000;
		} else if(height == 0x01000000){
			height = 0xb5000000;
		} else if(height == 0xb5000000){
			height = 0xff000000;
		} else {
			height = 0;
		}
		clearheightmap();
	}

	private void clearheightmap() {
		if(heightmap != null && heightmap.done() && heightmap.get() != null){
			heightmap.get().flush();
		}
		heightmap = null;
	}


	public void tick(double dt) {
		Gob pl = ui.sess.glob.oc.getgob(MapView.plgob);
		if(pl == null)
			this.cc = mv.cc.div(tilesz);
		else
			this.cc = pl.rc.div(tilesz);
	}

	public void draw(GOut og) {
		if(cc == null)
			return;
		final Coord plg = cc.div(cmaps);
		checkSession(plg);
		if(!plg.equals(lastplg)){
			lastplg = plg;
			clearheightmap();
		}
		if((height!=0) && (heightmap == null)){
			heightmap = getheightmap(plg);
		}

		double scale = getScale();
		Coord hsz = sz.div(scale);

		Coord tc = cc.add(off.div(scale));
		Coord ulg = tc.div(cmaps);
		int dy = -tc.y + (hsz.y / 2);
		int dx = -tc.x + (hsz.x / 2);
		while((ulg.x * cmaps.x) + dx > 0)
			ulg.x--;
		while((ulg.y * cmaps.y) + dy > 0)
			ulg.y--;

		Coord s = bg.sz();
		for(int y = 0; (y * s.y) < sz.y; y++) {
			for(int x = 0; (x * s.x) < sz.x; x++) {
				og.image(bg, new Coord(x*s.x, y*s.y));
			}
		}

		GOut g = og.reclipl(og.ul.mul((1-scale)/scale), hsz);
		g.gl.glPushMatrix();
		g.gl.glScaled(scale, scale, scale);

		Coord cg = new Coord();
		synchronized(cache) {
			for(cg.y = ulg.y; (cg.y * cmaps.y) + dy < hsz.y; cg.y++) {
				for(cg.x = ulg.x; (cg.x * cmaps.x) + dx < hsz.x; cg.x++) {

					Defer.Future<MapTile> f = cache.get(cg);
					final Coord tcg = new Coord(cg);
					final Coord ul = cg.mul(cmaps);
					if((f == null) && (cg.manhattan2(plg) <= 1)) {
						f = Defer.later(new Defer.Callable<MapTile>() {
							public MapTile call() {
								BufferedImage img = drawmap(ul, cmaps, true);
								if(img == null) { return null; }
								MapTile mapTile = new MapTile(new TexI(img), ul, tcg);
								if(Config.store_map) {
									img = drawmap(ul, cmaps, false);
									store(img, tcg);
								}
								return mapTile;
							}
						});
						cache.put(tcg, f);
					}
					if((f == null) || (!f.done())) {
						continue;
					}
					MapTile mt = f.get();
					if(mt == null){
						cache.put(cg, null);
						continue;
					}
					Tex img = mt.img;
					g.image(img, ul.add(tc.inv()).add(hsz.div(2)));
					if (Config.mapshowgrid)
						g.image(gridred, ul.add(tc.inv()).add(hsz.div(2)));
				}
			}
		}
		Coord c0 = hsz.div(2).sub(tc);

		if((height!=0) && (heightmap != null) && heightmap.done()){
			BufferedImage img = heightmap.get();
			if(img != null){
				g.image(img, c0.add(plg.sub(1,1).mul(cmaps)));
			} else {
				clearheightmap();
			}
		}

		drawmarkers(g, c0);
		drawview(g, c0);
		synchronized(ui.sess.glob.party.memb) {
			try {
				Tex tx = plx.layer(Resource.imgc).tex();
				Coord negc = plx.layer(Resource.negc).cc;
				for(Party.Member memb : ui.sess.glob.party.memb.values()) {
					Coord ptc = memb.getc();
					if(ptc == null)
						continue;
					ptc = c0.add(ptc.div(tilesz));
					g.chcolor(memb.col);
					g.image(tx, ptc.sub(negc));
					g.chcolor();
				}
			} catch (Loading ignored){}
		}

		g.gl.glPopMatrix();

		Window.swbox.draw(og, Coord.z, this.sz);

		//draw the lock icon
		lockbtn.draw(og.reclipl(xlate(lockbtn.c, true), lockbtn.sz));
		gridbtn.draw(og.reclipl(xlate(gridbtn.c, true), gridbtn.sz));

		// Absolute coordinates
		/*
		Coord locatedAC = Navigation.getAbsoluteCoordinates();
		locatedAC = locatedAC==null ? new Coord(0,0) : locatedAC;
		og.atext(locatedAC.div(11)+" x"+String.format("%.2f", scale),new Coord(0+og.sz.x/5, og.sz.y-og.sz.y/10), 0.5, 0.5);
		 */
	}

	private void drawview(GOut g, Coord tc) {
		Gob player = ui.gui.map.player();
		if (player != null) {
			Coord3f ptc3f = player.getc();
			if (ptc3f == null) {
				return;
			}
			Coord rc = new Coord((int) ptc3f.x, (int) ptc3f.y);
			rc = rc.div(MCache.tilesz).add(tc).sub(54,45);
			g.chcolor(Color.BLUE);
			Coord rect = new Coord(101,92);
			g.line(rc, rc.add(rect.x - 1, 0), 1);
			g.line(rc.add(rect.x - 1, 0), rc.add(rect), 1);
			g.line(rc.add(rect).sub(1, 1), rc.add(0, rect.y - 1), 1);
			g.line(rc.add(0, rect.y - 1), rc, 1);
			g.chcolor();
		}
	}

	private String mapfolder(){
		return String.format("%s/map/%s/", Config.userhome, Config.server);
	}

	private String mapfile(String file){
		return String.format("%s%s", mapfolder(), file);
	}

	private String mapsessfile(String file){
		return String.format("%s%s/%s",mapfolder(), session, file);
	}

	private String mapsessfolder(){
		return mapsessfile("");
	}

	private void store(BufferedImage img, Coord cg) {
		if(!Config.store_map || img == null){return;}
		Coord c = cg.sub(sp);
		String fileName = mapsessfile(String.format("tile_%d_%d.png", c.x, c.y));
		File outputfile = new File(fileName);
		try {
			ImageIO.write(img, "png", outputfile);
		} catch (IOException e) {}
	}

	private void checkSession(Coord plg) {
		if(cgrid == null || plg.manhattan(cgrid) > 5){
			sp = plg;
			synchronized (cache) {
				for (Future<MapTile> v : cache.values()) {
					if(v != null && v.done()) {
						MapTile tile = v.get();
						if(tile != null && tile.img != null) {
							tile.img.dispose();
						}
					}
				}
				cache.clear();
			}
			session = Utils.current_date();
			if(Config.store_map){
				(new File(mapsessfolder())).mkdirs();
				try {
					Writer currentSessionFile = new FileWriter(mapfile("currentsession.js"));
					currentSessionFile.write("var currentSession = '" + session + "';\n");
					currentSessionFile.close();
				} catch (IOException e) {}
			}
		}
		cgrid = plg;
	}

	public double getScale() {
		return scales[scale];
	}

	public void setScale(int scale) {
		this.scale = Math.max(0,Math.min(scale,scales.length-1));
	}

	public boolean mousedown(Coord c, int button) {
		parent.setfocus(this);
		raise();

		Marker m = getmarkerat(c);
		Coord mc = uitomap(c);

		if(button == 3){
			if (m != null) {
				mv.wdgmsg("click", this.c.add(c), mc, button, ui.modflags(), 0, (int)m.gob.id, m.gob.rc, 0, (-1));
				mv.pllastcc = ui.modflags() == 0 ? mc : null;
				mv.clearmovequeue();
				return true;
			}

			dm = true;
			ui.grabmouse(this);
			doff = c;
			return true;
		}

		if (button == 1) {
			if (m != null || ui.modctrl) {
				if(m != null && m.gob != null){
					m.gob.setattr(new GobHighlight(m.gob));
				}
				mv.wdgmsg("click", Coord.z, mc, button, 0);
				return true;
			}

			ui.grabmouse(this);
			doff = c;
			if(c.isect(sz.sub(gzsz), gzsz)) {
				rsm = true;
				return true;
			}
		}
		return super.mousedown(c, button);
	}

	public boolean mouseup(Coord c, int button) {
		if(button == 2){
			off.x = off.y = 0;
			return true;
		}

		if(button == 3){
			dm = false;
			ui.grabmouse(null);
			return true;
		}

		if (rsm){
			ui.grabmouse(null);
			rsm = false;
			storeOpt(OPT_SZ, sz);
		} else {
			super.mouseup(c, button);
		}
		return (true);
	}

	public void mousemove(Coord c) {
		Coord d;
		if(dm){
			d = c.sub(doff);
			off = off.sub(d);
			doff = c;
			return;
		}

		if (rsm){
			d = c.sub(doff);
			sz = sz.add(d);
			sz.x = Math.max(minsz.x, sz.x);
			sz.y = Math.max(minsz.y, sz.y);
			doff = c;
			//pack();
		} else if(!locked) {
			super.mousemove(c);
		}
	}

	@Override
	public boolean mousewheel(Coord c, int amount) {
		if( amount > 0){
			setScale(scale - 1);
		} else {
			setScale(scale + 1);
		}
		return true;
	}

	private void drawmarkers(GOut g, Coord tc) {
		if (!radarenabled)
			return;

		double scale = 1.0;//Math.max(getScale(),1.0);

		Radar radar = ui.sess.glob.oc.radar;
		try {
			for (Marker m : radar.getMarkers()) {
				if (m.template.visible)
					m.draw(g, tc, scale);
			}
		} catch (MCache.LoadingMap e) {
		}
	}

	private Coord uitomap(Coord c) {
		return c.sub(sz.div(2)).add(off).div(getScale()).mul(MCache.tilesz).add(mv.cc);
	}

	private Marker getmarkerat(Coord c) {
		if (radarenabled) {
			Radar radar = ui.sess.glob.oc.radar;
			try {
				Coord mc = uitomap(c);
				for (Marker m : radar.getMarkers()) {
					if (m.template.visible && m.hit(mc))
						return m;
				}
			} catch (MCache.LoadingMap e) {
			}
		}
		return null;
	}

	@Override
	public Object tooltip(Coord c, boolean again) {
		Marker m = getmarkerat(c);
		if (m != null)
			return m.getTooltip();
		return null;
	}

	@Override
	public Map<String, Console.Command> findcmds() {
		return cmdmap;
	}

	@Override
	public boolean type(char key, KeyEvent ev) {
		if(key == 27) {
			return false;
		}
		return super.type(key, ev);
	}

	public void wdgmsg(String msg, Object... args) {
	}
}
