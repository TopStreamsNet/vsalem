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

import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.*;

import haven.pathfinder.GobHitmap;
import org.ender.timer.Timer;

public class Glob {
	public static final int GMSG_TIME = 0;
	public static final int GMSG_ASTRO = 1;
	public static final int GMSG_LIGHT = 2;
	public static final int GMSG_SKY = 3;
	public static final float MAX_BRIGHT = 0.62f;

	public final GobHitmap gobhitmap = new GobHitmap();
	public long time, epoch = System.currentTimeMillis();
	public int season;
	public final OCache oc = new OCache(this);
	public MCache map;
	public Session sess;
	public Party party;
	public Set<Pagina> paginae = new HashSet<Pagina>();
	public int pagseq = 0;
	public Map<Resource, Pagina> pmap = new WeakHashMap<Resource, Pagina>();
	public Map<String, CAttr> cattr = new HashMap<String, CAttr>();
	public Map<Integer, Buff> buffs = new TreeMap<Integer, Buff>();
	public Color lightamb = null, lightdif = null, lightspc = null;
	public Color olightamb = null, olightdif = null, olightspc = null;
	public Color tlightamb = null, tlightdif = null, tlightspc = null;
	public double lightang = 0.0, lightelev = 0.0;
	public double olightang = 0.0, olightelev = 0.0;
	public double tlightang = 0.0, tlightelev = 0.0;
	public long lchange = -1;
	public Indir<Resource> sky1 = null, sky2 = null;
	public double skyblend = 0.0;
	public java.awt.Color origamb = null;
	public  long cattr_lastupdate = 0;

	private static WeakReference<Glob> reference = new WeakReference<>(null);
	public static Glob getByReference() {
		return reference.get();
	}

	public Glob(Session sess) {
		this.sess = sess;
		map = new MCache(sess);
		party = new Party(this);
		reference = new WeakReference<>(this);
	}

	public void purge(){
		map.purge();
		paginae.clear();
		pmap.clear();
		cattr.clear();
		buffs.clear();
	}

	public static class CAttr extends Observable {
		String nm;
		int base, comp;

		public CAttr(String nm, int base, int comp) {
			this.nm = nm.intern();
			this.base = base;
			this.comp = comp;
		}

		public void update(int base, int comp) {
			if((base == this.base) && (comp == this.comp))
				return;
			Integer old = this.comp;
			this.base = base;
			this.comp = comp;
			setChanged();
			notifyObservers(old);
		}

		public String getName(){
			return nm;
		}

		public int getBase(){
			return base;
		}

		public int getComp(){
			return comp;
		}
	}

	public static class Pagina implements java.io.Serializable {
		private final Resource res;
		public State st;
		public int meter, dtime;
		public long gettime;
		public Image img;
		public int newp;
		public long fstart; /* XXX: ABUSAN!!! */

		public interface Image {
			public Tex tex();
		}

		public static enum State {
			ENABLED, DISABLED {
				public Image img(final Pagina pag) {
					return(new Image() {
						private Tex c = null;

						public Tex tex() {
							if(pag.res() == null)
								return(null);
							if(c == null)
								c = new TexI(PUtils.monochromize(pag.res().layer(Resource.imgc).img, Color.LIGHT_GRAY));
							return(c);
						}
					});
				}
			};

			public Image img(final Pagina pag) {
				return(new Image() {
					public Tex tex() {
						if(pag.res() == null)
							return(null);
						return(pag.res().layer(Resource.imgc).tex());
					}
				});
			}
		}

		public Pagina(Resource res) {
			this.res = res;
			state(State.ENABLED);
		}

		public Resource res() {
			return(res);
		}

		public Resource.AButton act() {
			if(res().loading)
				return(null);
			return(res().layer(Resource.action));
		}

		public void state(State st) {
			this.st = st;
			this.img = st.img(this);
		}
	}

	private static Color colstep(Color o, Color t, double a) {
		int or = o.getRed(), og = o.getGreen(), ob = o.getBlue(), oa = o.getAlpha();
		int tr = t.getRed(), tg = t.getGreen(), tb = t.getBlue(), ta = t.getAlpha();
		return(new Color(or + (int)((tr - or) * a),
				og + (int)((tg - og) * a),
				ob + (int)((tb - ob) * a),
				oa + (int)((ta - oa) * a)));
	}

	private void ticklight(int dt) {
		if(lchange >= 0) {
			lchange += dt;
			if(lchange > 2000) {
				lchange = -1;
				origamb = tlightamb;
				lightdif = tlightdif;
				lightspc = tlightspc;
				lightang = tlightang;
				lightelev = tlightelev;
			} else {
				double a = lchange / 2000.0;
				origamb = colstep(olightamb, tlightamb, a);
				lightdif = colstep(olightdif, tlightdif, a);
				lightspc = colstep(olightspc, tlightspc, a);
				lightang = olightang + a * Utils.cangle(tlightang - olightang);
				lightelev = olightelev + a * Utils.cangle(tlightelev - olightelev);
			}
			brighten();
		}
	}

	private long lastctick = 0;
	public void ctick() {
		long now = System.currentTimeMillis();
		int dt;
		if(lastctick == 0)
			dt = 0;
		else
			dt = (int)(now - lastctick);
		dt = Math.max(dt, 0);

		synchronized(this) {
			ticklight(dt);
		}

		oc.ctick(dt);
		map.ctick(dt);

		lastctick = now;
	}

	private static double defix(int i) {
		return(((double)i) / 1e9);
	}

	private long lastrep = 0;
	private long rgtime = 0;
	public long globtime() {
		long now = System.currentTimeMillis();
		long raw = ((now - epoch) * 3) + (time * 1000);
		if(lastrep == 0) {
			rgtime = raw;
		} else {
			long gd = (now - lastrep) * 3;
			rgtime += gd;
			if(Math.abs(rgtime + gd - raw) > 1000)
				rgtime = rgtime + (long)((raw - rgtime) * (1.0 - Math.pow(10.0, -(now - lastrep) / 1000.0)));
		}
		lastrep = now;
		return(rgtime);
	}

	private final int minute = 60;
	private final int hour = minute*60;
	private final int day = hour*24;
	private final int month = day*30;
	private final int year = month*12;
	private static String ordinal(int i) {
		String[] sufixes = new String[] { "th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th" };
		switch (i % 100) {
			case 11:
			case 12:
			case 13:
				return i + "th";
			default:
				return i + sufixes[i % 10];
		}
	}
	private void setServerTime(int st)
	{
		time = st;
	}

	public void blob(Message msg) {
		boolean inc = msg.uint8() != 0;
		while(!msg.eom()) {
			int t = msg.uint8();
			switch(t) {
				case GMSG_TIME:
					setServerTime(msg.int32());
					season = msg.uint8();
					epoch = System.currentTimeMillis();
					if(!inc)
						lastrep = 0;
					Timer.server = 1000*time;
					Timer.local = System.currentTimeMillis();
					break;
				case GMSG_LIGHT:
					synchronized(this) {
						tlightamb = msg.color();
						tlightdif = msg.color();
						tlightspc = msg.color();
						tlightang = (msg.int32() / 1000000.0) * Math.PI * 2.0;
						tlightelev = (msg.int32() / 1000000.0) * Math.PI * 2.0;
						if(inc) {
							olightamb = origamb;
							olightdif = lightdif;
							olightspc = lightspc;
							olightang = lightang;
							olightelev = lightelev;
							lchange = 0;
						} else {
							origamb = tlightamb;
							lightdif = tlightdif;
							lightspc = tlightspc;
							lightang = tlightang;
							lightelev = tlightelev;
							lchange = -1;
						}
						brighten();
					}
					break;
				case GMSG_SKY:
					int id1 = msg.uint16();
					if(id1 == 65535) {
						synchronized(this) {
							sky1 = sky2 = null;
							skyblend = 0.0;
						}
					} else {
						int id2 = msg.uint16();
						if(id2 == 65535) {
							synchronized(this) {
								sky1 = sess.getres(id1);
								sky2 = null;
								skyblend = 0.0;
							}
						} else {
							synchronized(this) {
								sky1 = sess.getres(id1);
								sky2 = sess.getres(id2);
								skyblend = msg.int32() / 1000000.0;
							}
						}
					}
					break;
				default:
					throw(new RuntimeException("Unknown globlob type: " + t));
			}
		}
	}

	public synchronized void brighten() {
		float hsb[];
		if(!Config.alwaysbright)
		{
			hsb = Color.RGBtoHSB(origamb.getRed(), origamb.getGreen(), origamb.getBlue(), null);
			float b = hsb[2];
			if(b < MAX_BRIGHT){
				hsb[2] = b + Config.brighten*(MAX_BRIGHT - b);
			}
		}
		else
		{
			int i = (int)Config.brightang+1;
			float[] hsb2 = Color.RGBtoHSB(255 * (i + 4) / 12, 255 * (i + 4) / 12, 208 * (i + 4) / 12, null);
			this.lightdif = Color.getHSBColor(hsb2[0], hsb2[1], hsb2[2]);
			hsb2 = Color.RGBtoHSB(255, 255, 255, null);
			this.lightspc = Color.getHSBColor(hsb2[0], hsb2[1], hsb2[2]);
			hsb = Color.RGBtoHSB(96 * i / 8, 96 * i / 8, 160 * i / 8, null);
		}
		lightamb = Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
		DarknessWnd.update();
	}

	public Pagina paginafor(Resource res) {
		if(res == null)
			return(null);
		synchronized(pmap) {
			Pagina p = pmap.get(res);
			if(p == null)
				pmap.put(res, p = new Pagina(res));
			return(p);
		}
	}

	public void paginae(Message msg) {
		synchronized(paginae) {
			while(!msg.eom()) {
				int act = msg.uint8();
				if(act == '+') {
					String nm = msg.string();
					int ver = msg.uint16();
					final Pagina pag = paginafor(Resource.load(nm, ver));
					paginae.add(pag);
					pag.state(Pagina.State.ENABLED);
					pag.meter = 0;
					int t;
					while((t = msg.uint8()) != 0) {
						if(t == '!') {
							pag.state(Pagina.State.DISABLED);
						} else if(t == '*') {
							pag.meter = msg.int32();
							pag.gettime = System.currentTimeMillis();
							pag.dtime = msg.int32();
						} else if(t == '^') {
							pag.newp = 1;
							Utils.defer(new Runnable() {
								@Override
								public void run() {
									pag.res().loadwait();
									String name = pag.res().layer(Resource.action).name;
									UI.instance.message(String.format("You gain access to '%s'!", name), GameUI.MsgType.INFO);
								}
							});
						}
					}
				} else if(act == '-') {
					String nm = msg.string();
					int ver = msg.uint16();
					paginae.remove(paginafor(Resource.load(nm, ver)));
				}
			}
			pagseq++;
		}
	}

	public void cattr(Message msg) {
		synchronized(cattr) {
			while(!msg.eom()) {
				String nm = msg.string();
				int base = msg.int32();
				int comp = msg.int32();
				CAttr a = cattr.get(nm);
				if(a == null) {
					a = new CAttr(nm, base, comp);
					cattr.put(nm, a);
				} else {
					a.update(base, comp);
				}
				if(nm.equals("carry")){
					GameUI gui = UI.instance.gui;
					if(gui != null){
						gui.uimsg("weight", gui.weight);
					}
				}
			}
		}
		cattr_lastupdate = System.currentTimeMillis();
	}

	public void buffmsg(Message msg) {
		String name = msg.string().intern();
		synchronized(buffs) {
			if(name == "clear") {
				buffs.clear();
			} else if(name == "set") {
				int id = msg.int32();
				Indir<Resource> res = sess.getres(msg.uint16());
				String tt = msg.string();
				int ameter = msg.int32();
				int nmeter = msg.int32();
				int cmeter = msg.int32();
				int cticks = msg.int32();
				boolean major = msg.uint8() != 0;
				Buff buff;
				if((buff = buffs.get(id)) == null) {
					buff = new Buff(id, res);
				} else {
					buff.res = res;
				}
				if(tt.equals(""))
					buff.tt = null;
				else
					buff.tt = tt;
				buff.ameter = ameter;
				buff.nmeter = nmeter;
				buff.ntext = null;
				buff.cmeter = cmeter;
				buff.cticks = cticks;
				buff.ctext = null;
				buff.major = major;
				buff.gettime = System.currentTimeMillis();
				buffs.put(id, buff);
			} else if(name == "rm") {
				int id = msg.int32();
				buffs.remove(id);
			}
		}
	}
}
