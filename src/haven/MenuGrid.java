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
import java.awt.event.KeyEvent;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;

import haven.GobbleInfo.Event;
import haven.ItemInfo.Tip;
import haven.Resource.AButton;
import haven.Glob.Pagina;
import haven.ItemInfo.InfoFactory;
import haven.plugins.XTendedPaginae;

import java.util.*;
import java.util.Map.Entry;

import org.ender.wiki.Item;
import org.ender.wiki.Wiki;

public class MenuGrid extends Widget {
	public final static Tex bg = Resource.loadtex("gfx/hud/invsq");
	public final static Coord bgsz = bg.sz().add(-1, -1);
	public final Pagina CRAFT;
	public final Pagina next = paginafor(Resource.load("gfx/hud/sc-next").loadwait());
	public final Pagina bk = paginafor(Resource.load("gfx/hud/sc-back").loadwait());
	public final static RichText.Foundry ttfnd = new RichText.Foundry(MainFrame.uiConfig.getFontConfig("menuGrid"));
	private static Coord gsz = new Coord(4, 4);
	public Pagina cur, pressed, dragging, layout[][] = new Pagina[gsz.x][gsz.y];
	private int curoff = 0;
	private int pagseq = 0;
	private boolean loading = true;
	private Map<Character, Pagina> hotmap = new TreeMap<Character, Pagina>();

	@RName("scm")
	public static class $_ implements Factory {
		public Widget create(Coord c, Widget parent, Object[] args) {
			return(new MenuGrid(c, parent));
		}
	}

	public class PaginaException extends RuntimeException {
		public Pagina pag;

		public PaginaException(Pagina p) {
			super("Invalid pagina: " + p.res().name);
			pag = p;
		}
	}

	public boolean cons(Pagina p, Collection<Pagina> buf) {
		Pagina[] cp = new Pagina[0];
		Collection<Pagina> open, close = new HashSet<Pagina>();
		synchronized(ui.sess.glob.paginae) {
			open = new LinkedList<Pagina>();
			for(Pagina pag : ui.sess.glob.paginae) {
				if(pag.newp == 2) {
					pag.newp = 0;
					pag.fstart = 0;
				}
				open.add(pag);
			}
			for(Pagina pag : ui.sess.glob.pmap.values()) {
				if(pag.newp == 2) {
					pag.newp = 0;
					pag.fstart = 0;
				}
			}
		}
		boolean ret = true;
		while(!open.isEmpty()) {
			Iterator<Pagina> iter = open.iterator();
			Pagina pag = iter.next();
			iter.remove();
			try {
				Resource r = pag.res();
				AButton ad = r.layer(Resource.action);
				if(ad == null)
					throw(new PaginaException(pag));
				Pagina parent = paginafor(ad.parent);
				if((pag.newp != 0) && (parent != null) && (parent.newp == 0)) {
					parent.newp = 2;
					parent.fstart = (parent.fstart == 0)?pag.fstart:Math.min(parent.fstart, pag.fstart);
				}
				if(parent == p)
					buf.add(pag);
				else if((parent != null) && !close.contains(parent) && !open.contains(parent))
					open.add(parent);
				close.add(pag);
			} catch(Loading e) {
				ret = false;
			}
		}
		return(ret);
	}

	public MenuGrid(Coord c, Widget parent) {
		super(c, Inventory.invsz(gsz), parent);
		ui.mnu = this;
		CRAFT = paginafor(Resource.load("paginae/act/craft"));
		XTendedPaginae.loadXTendedPaginae(ui);

		//cons(null);
	}

	public static Comparator<Pagina> sorter = new Comparator<Pagina>() {
		public int compare(Pagina a, Pagina b) {
			AButton aa = a.act(), ab = b.act();
			if((aa.ad.length == 0) && (ab.ad.length > 0))
				return(-1);
			if((aa.ad.length > 0) && (ab.ad.length == 0))
				return(1);
			return(aa.name.compareTo(ab.name));
		}
	};

	private void updlayout() {
		synchronized(ui.sess.glob.paginae) {
			List<Pagina> cur = new ArrayList<Pagina>();
			loading = !cons(this.cur, cur);
			Collections.sort(cur, sorter);
			int i = curoff;
			hotmap.clear();
			for(int y = 0; y < gsz.y; y++) {
				for(int x = 0; x < gsz.x; x++) {
					Pagina btn = null;
					if((this.cur != null) && (x == gsz.x - 1) && (y == gsz.y - 1)) {
						btn = bk;
					} else if((cur.size() > ((gsz.x * gsz.y) - 1)) && (x == gsz.x - 2) && (y == gsz.y - 1)) {
						btn = next;
					} else if(i < cur.size()) {
						Resource.AButton ad = cur.get(i).act();
						if(ad.hk != 0)
							hotmap.put(Character.toUpperCase(ad.hk), cur.get(i));
						btn = cur.get(i++);
					}
					layout[x][y] = btn;
				}
			}
			pagseq = ui.sess.glob.pagseq;
		}
	}

	public static BufferedImage getXPgain(String name){
		try {
			Item itm = Wiki.get(name);
			if(itm != null){
				Map<String, Integer> props = itm.attgive;
				if(props != null){
					int n = props.size();
					String attrs[] = new String[n];
					int exp[] = new int[n];
					n = 0;
					for(String attr : props.keySet()){
						Integer val = props.get(attr);
						attrs[n] = attr;
						exp[n] = val;
						n ++;
					}
					Inspiration i = new Inspiration(null, 0, attrs, exp);
					return i.longtip();
				}
			}
		}catch (Exception e) {
			e.printStackTrace(System.out);
		}
		return null;
	}

	public static float[] safeFloat(Float[] input, float[] def){
		if(input == null){return def;}
		int n = input.length;
		float[] output = new float[n];
		for(int i=0; i<n; i++){
			if(input[i] != null){
				output[i] = input[i];
			} else {
				output[i] = 0;
			}
		}
		return output;
	}

	public static BufferedImage getFood(String name){
		try {
			Item itm = Wiki.get(name);
			if(itm != null){
				if(itm.food != null){
					Map<String, Float[]> food = itm.food;
					float[] def = new float[]{0, 0, 0, 0};
					String[] names = {"Blood", "Phlegm", "Yellow Bile", "Black Bile"};
					float[] heal = safeFloat(food.get("Heals"), def);
					float[] gmax = safeFloat(food.get("GluttonMax"),def);
					float[] gmin = safeFloat(food.get("GluttonMin"),def);

					int[] low = new int[4];
					int[] high = new int[4];
					int[] tempers = new int[4];
					for(int i=0; i<4; i++){
						tempers[i] = (int) (1000*heal[i]);
						high[i] = (int) (1000*gmax[i]);
						low[i] = (int) (1000*gmin[i]);
					}
					FoodInfo fi = new FoodInfo(null, tempers);
					int ft = itm.food_full*60;
					GobbleInfo gi = new GobbleInfo(null, low, high, new int[0], ft, new LinkedList<Event>());
					String uses = String.format("Uses: %d\n", itm.food_uses);
					BufferedImage uses_img = RichText.stdf.render(uses).img;

					Color debuff_color = new Color(255, 192, 192);
					Color undebuff_color = new Color(192, 255, 192);

					BufferedImage currimg = ItemInfo.catimgs(3, fi.longtip(), uses_img, gi.longtip());
					//first, debuffs
					for(Entry<String,Integer[]> e : itm.food_reduce.entrySet())
					{
						Integer[] values = e.getValue();
						BufferedImage head = RichText.render(String.format("-%d%%", values[0]), debuff_color).img;
						Resource iconres=null;
						if(Wiki.buffmap.containsKey(e.getKey()))
						{
							iconres = Resource.load("gfx/invobjs/"+Wiki.buffmap.get(e.getKey()));
						}
						else
						{
							System.out.println("Wiki food translation key not available: "+e.getKey());
						}
						if(iconres != null)
						{
							BufferedImage icon, tail;
							icon = PUtils.convolvedown(iconres.layer(Resource.imgc).img, new Coord(16, 16), GobIcon.filter);
							String classname = iconres.layer(Resource.tooltip).t;
							tail = RichText.render(classname + " [" + values[1] + "%]").img;
							currimg = ItemInfo.catimgs(0, currimg, ItemInfo.catimgsh(0, head, icon, tail));
						}
						else
						{//issue getting the res: print what we know
							BufferedImage tail;
							String classname = e.getKey() + " [" + values[1] + "%]";
							tail = RichText.render(classname).img;
							currimg = ItemInfo.catimgs(0, currimg, ItemInfo.catimgsh(0, head, tail));
						}
					}
					//then, undebuffs
					for(Entry<String,Integer[]> e : itm.food_restore.entrySet())
					{
						Integer[] values = e.getValue();
						BufferedImage head = RichText.render(String.format("+%d%%", values[0]), undebuff_color).img;
						Resource iconres = Resource.load("gfx/invobjs/"+Wiki.buffmap.get(e.getKey()));
						if(iconres != null)
						{
							BufferedImage icon, tail;
							icon = PUtils.convolvedown(iconres.layer(Resource.imgc).img, new Coord(16, 16), GobIcon.filter);
							String classname = iconres.layer(Resource.tooltip).t;
							tail = RichText.render(classname + " [" + values[1] + "%]").img;
							currimg = ItemInfo.catimgs(0, currimg, ItemInfo.catimgsh(0, head, icon, tail));
						}
						else
						{//issue getting the res: print what we know
							BufferedImage tail;
							String classname = e.getKey() + " [" + values[1] + "%]";
							tail = RichText.render(classname).img;
							currimg = ItemInfo.catimgs(0, currimg, ItemInfo.catimgsh(0, head, tail));
						}
					}
					return currimg;
				}
			}
		} catch (Loading l)
		{

		} catch (Exception e)
		{
			e.printStackTrace(System.out);
		}
		return null;
	}

	public static BufferedImage getArtifact(String name){
		try{
			Item itm = Wiki.get(name);
			if(itm == null || itm.art_profs == null || itm.art_profs.length == 0){return null;}

			Resource res = Resource.load("ui/tt/slot");
			if(res == null){return null;}
			InfoFactory f = res.layer(Resource.CodeEntry.class).get(InfoFactory.class);
			Session sess = UI.instance.sess;
			int rid = sess.getresid("ui/tt/dattr");
			if(rid == 0){return null;}
			Object[] bonuses = itm.getArtBonuses();
			bonuses[0] = rid;
			Object[] args = new Object[4 + itm.art_profs.length];//{0, itm.art_pmin, itm.art_pmax, "arts", "faith", new Object[]{bonuses}};
			int i=0;
			args[i++] = 0;
			args[i++] = itm.art_pmin;
			args[i++] = itm.art_pmax;
			for(String prof : itm.art_profs){
				args[i++] = CharWnd.attrbyname(prof);
			}
			args[i++] = new Object[]{bonuses};
			ItemInfo.Tip tip = (Tip) f.build(sess, args);
			List<ItemInfo> list = new LinkedList<ItemInfo>();
			list.add(tip);

			return tip.longtip();
		} catch (Exception e) {
			e.printStackTrace(System.out);
			return null;
		}
	}

	public static BufferedImage getSlots(String name){
		try {
			Item itm = Wiki.get(name);
			if(itm == null || itm.cloth_slots == 0){return null;}

			Object[] args = new Object[5];
			int i = 0;
			args[i++] = 0;
			args[i++] = itm.cloth_slots;
			args[i++] = 0;
			args[i++] = 25;
			args[i++] = 0;
			Resource res = Resource.load("ui/tt/slots");
			if(res == null){return null;}
			InfoFactory f = res.layer(Resource.CodeEntry.class).get(InfoFactory.class);
			ItemInfo.Tip tip = (Tip) f.build(null, args);
			return tip.longtip();
		} catch (Exception e) {
			e.printStackTrace(System.out);
			return null;
		}
	}

	public static Tex rendertt(Resource res, boolean withpg, boolean hotkey) {
		Resource.AButton ad = res.layer(Resource.action);
		Resource.Pagina pg = res.layer(Resource.pagina);
		String tt = ad.name;
		BufferedImage xp = null, food = null, slots = null, art = null;
		if(hotkey){
			int pos = tt.toUpperCase().indexOf(Character.toUpperCase(ad.hk));
			if(pos >= 0)
				tt = tt.substring(0, pos) + "$col[255,255,0]{" + tt.charAt(pos) + "}" + tt.substring(pos + 1);
			else if(ad.hk != 0)
				tt += " [" + ad.hk + "]";
		}
		if(withpg) {
			if(pg != null){tt += "\n\n" + pg.text;}
			xp = getXPgain(ad.name);
			food = getFood(ad.name);
			slots = getSlots(ad.name);
			art = getArtifact(ad.name);
		}
		BufferedImage img = ttfnd.render(tt, 300).img;
		if(xp != null){
			img = ItemInfo.catimgs(3, img, xp);
		}
		if(food != null){
			img = ItemInfo.catimgs(3, img, food);
		}
		if(slots != null){
			img = ItemInfo.catimgs(3, img, slots);
		}
		if(art != null){
			img = ItemInfo.catimgs(3, img, art);
		}
		return(new TexI(img));
	}

	private static Map<Pagina, Tex> glowmasks = new WeakHashMap<Pagina, Tex>();
	private Tex glowmask(Pagina pag) {
		Tex ret = glowmasks.get(pag);
		if(ret == null) {
			ret = new TexI(PUtils.glowmask(PUtils.glowmask(pag.res().layer(Resource.imgc).img.getRaster()), 4, new Color(32, 255, 32)));
			glowmasks.put(pag, ret);
		}
		return(ret);
	}
	public void draw(GOut g) {
		long now = System.currentTimeMillis();
		Inventory.invsq(g, Coord.z, gsz);
		for(int y = 0; y < gsz.y; y++) {
			for(int x = 0; x < gsz.x; x++) {
				Coord p = Inventory.sqoff(new Coord(x, y));
				Pagina btn = layout[x][y];
				if(btn != null) {
					Tex btex = btn.img.tex();
					g.image(btex, p);
					if(btn.meter > 0) {
						double m = btn.meter / 1000.0;
						if(btn.dtime > 0)
							m += (1 - m) * (double)(now - btn.gettime) / (double)btn.dtime;
						m = Utils.clip(m, 0, 1);
						g.chcolor(255, 255, 255, 128);
						g.fellipse(p.add(Inventory.isqsz.div(2)), Inventory.isqsz.div(2), 90, (int)(90 + (360 * m)));
						g.chcolor();
					}
					if(btn.newp != 0) {
						if(btn.fstart == 0) {
							btn.fstart = now;
						} else {
							double ph = ((now - btn.fstart) / 1000.0) - (((x + (y * gsz.x)) * 0.15) % 1.0);
							if(ph < 1.25) {
								g.chcolor(255, 255, 255, (int)(255 * ((Math.cos(ph * Math.PI * 2) * -0.5) + 0.5)));
								g.image(glowmask(btn), p.sub(4, 4));
								g.chcolor();
							} else {
								g.chcolor(255, 255, 255, 128);
								g.image(glowmask(btn), p.sub(4, 4));
								g.chcolor();
							}
						}
					}
					if(btn == pressed) {
						g.chcolor(new Color(0, 0, 0, 128));
						g.frect(p, btex.sz());
						g.chcolor();
					}
				}
			}
		}
		super.draw(g);
		if(dragging != null) {
			final Tex dt = dragging.img.tex();
			ui.drawafter(new UI.AfterDraw() {
				public void draw(GOut g) {
					g.image(dt, ui.mc.add(dt.sz().div(2).inv()));
				}
			});
		}
	}

	private Pagina curttp = null;
	private boolean curttl = false;
	Item ttitem = null;
	private Tex curtt = null;
	private long hoverstart;
	public Object tooltip(Coord c, Widget prev) {
		Pagina pag = bhit(c);
		long now = System.currentTimeMillis();
		if((pag != null) && (pag.act() != null)) {
			if(prev != this)
				hoverstart = now;
			boolean ttl = (now - hoverstart) > 500;
			Item itm = Wiki.get(pag.res().layer(Resource.action).name);
			if((pag != curttp) || (ttl != curttl) || itm != ttitem) {
				ttitem = itm;
				curtt = rendertt(pag.res(), ttl, true);
				curttp = pag;
				curttl = ttl;
			}
			return(curtt);
		} else {
			hoverstart = now;
			return("");
		}
	}

	private Pagina bhit(Coord c) {
		Coord bc = Inventory.sqroff(c);
		if((bc.x >= 0) && (bc.y >= 0) && (bc.x < gsz.x) && (bc.y < gsz.y))
			return(layout[bc.x][bc.y]);
		else
			return(null);
	}

	public boolean mousedown(Coord c, int button) {
		Pagina h = bhit(c);
		if((button == 1) && (h != null)) {
			pressed = h;
			ui.grabmouse(this);
		}
		return(true);
	}

	public void mousemove(Coord c) {
		if((dragging == null) && (pressed != null)) {
			Pagina h = bhit(c);
			if(h != pressed)
				dragging = pressed;
		}
	}

	private Pagina paginafor(Resource res) {
		return(ui.sess.glob.paginafor(res));
	}

	public Pagina paginafor(String name){
		Set<Pagina> pags = ui.sess.glob.paginae;
		for(Pagina p : pags){
			Resource res = p.res();
			if(res == null){continue;}
			AButton act = res.layer(Resource.action);
			if(act == null){continue;}
			if(name.equals(act.name)){
				return p;
			}
		}
		return null;
	}

	public void useres(Resource r){
		use(paginafor(r));
	}

	public void use(Pagina r) {
		Collection<Pagina> sub = new LinkedList<Pagina>(),
				cur = new LinkedList<Pagina>();
		cons(r, sub);
		cons(this.cur, cur);
		if(isCrafting(r)){ui.gui.showCraftWnd();}
		selectCraft(r);
		if(sub.size() > 0) {
			this.cur = r;
			curoff = 0;
		} else if(r == bk) {
			this.cur = paginafor(this.cur.act().parent);
			this.curoff = 0;
			selectCraft(this.cur);
		} else if(r == next) {
			int off = gsz.x*gsz.y - 2;
			if((curoff + off) >= cur.size())
				curoff = 0;
			else
				curoff += off;
		} else {
			r.newp = 0;
			if (!senduse(r)) return;
			if(Config.menugrid_resets) {
				this.cur = null;
				curoff = 0;
			}
		}
		updlayout();
	}

	public boolean senduse(Pagina r) {
		String [] ad = r.act().ad;
		if (ad != null && ad.length >= 1) {
			if (ad[0].equals("@")) {
				if (!XTendedPaginae.useXTended(this.ui, ad)) {
					this.use(null);
				}
			} else {
				this.wdgmsg("act", ad);
			}
			return true;
		} else {
			return false;
		}
	}

	private void selectCraft(Pagina r) {
		if(r == null){
			return;
		}
		if(ui.gui.craftwnd != null){
			ui.gui.craftwnd.select(r, true);
		}
	}

	public void tick(double dt) {
		if(loading || (pagseq != ui.sess.glob.pagseq))
			updlayout();
	}

	public boolean mouseup(Coord c, int button) {
		Pagina h = bhit(c);
		if(button == 1) {
			if(dragging != null) {
				ui.dropthing(ui.root, ui.mc, dragging.res());
				dragging = pressed = null;
			} else if(pressed != null) {
				if(pressed == h)
					use(h);
				pressed = null;
			}
			ui.grabmouse(null);
		}
		return(true);
	}

	public void uimsg(String msg, Object... args) {
		if(msg == "goto") {
			String res = (String)args[0];
			if(res.equals(""))
				cur = null;
			else
				cur = paginafor(Resource.load(res));
			curoff = 0;
			updlayout();
		}
	}

	public boolean globtype(char k, KeyEvent ev) {
		if(ev.isAltDown() || ev.isControlDown() || k == 0){return false;}
		k = (char) ev.getKeyCode();
		if(Character.toUpperCase(k) != k){return false;}

		if((k == 27) && (this.cur != null)) {
			this.cur = null;
			curoff = 0;
			updlayout();
			return(true);
		} else if((k == KeyEvent.VK_N) && (layout[gsz.x - 2][gsz.y - 1] == next)) {
			use(next);
			return(true);
		}
		Pagina r = hotmap.get(k);
		if(r != null) {
			use(r);
			return(true);
		}
		return(false);
	}

	public boolean isCrafting(Pagina p) {
		return isChildOf(p, CRAFT) || CRAFT == p;
	}

	public boolean isCrafting(Resource res){
		return isCrafting(paginafor(res));
	}

	public Pagina getParent(Pagina p){
		if(p == null){
			return null;
		}
		try {
			Resource res = p.res();
			Resource.AButton ad = res.layer(Resource.action);
			if (ad == null)
				return null;
			return paginafor(ad.parent);
		} catch (Loading e){
			return null;
		}
	}

	public boolean isChildOf(Pagina item, Pagina parent) {
		Pagina p;
		while((p = getParent(item)) != null){
			if(p == parent){ return true; }
			item = p;
		}
		return false;
	}
	public void usen(final String rname) {
        use(paginafor(rname));
    }
}
