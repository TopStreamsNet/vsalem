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

import haven.integrations.map.Navigation;
import haven.integrations.map.RemoteNavigation;
import haven.pathfinder.Move;
import haven.pathfinder.NBAPathfinder;

import javax.media.opengl.GL;
import java.awt.*;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.*;

import static haven.MCache.tilesz;
import static haven.automation.Utils.waitForEmptyHand;

public class MapView extends PView implements DTarget, Console.Directory {
	public static final String DEFCAM = "sortho";
	private final R2DWdg r2dwdg;
	public static long plgob = -1;
	public Coord cc;
	public final Glob glob;
	private int view = 2;
	private Collection<Delayed> delayed = new LinkedList<Delayed>();
	private Collection<Delayed> delayed2 = new LinkedList<Delayed>();
	private Collection<Rendered> extradraw = new LinkedList<Rendered>();
	public Camera camera;
	private Plob placing = null;
	private int[] visol = new int[32];
	private Grabber grab;
	public static int ffThread = 0;
	public static boolean signalToStop = false;
	private static Gob ffTarget = null;
	public static final java.util.Map<String, Class<? extends Camera>> camtypes = new HashMap<String, Class<? extends Camera>>();
	private TileOutline gridol;
	private Coord lasttc = Coord.z;

	private Gob pathfindGob;
	private int pathfindGobMod = 0;
	private int pathfindGobMouse = 0;
	private Coord movingto;
	private Queue<Coord> movequeue = new ArrayDeque<>();

	{
		camtypes.put("follow", FollowCam.class);
		camtypes.put("sfollow", SmoothFollowCam.class);
		camtypes.put("free", FreeCam.class);
		camtypes.put("ortho", OrthoCam.class);
		camtypes.put("sortho", SOrthoCam.class);
	}

	public interface Delayed {
		public void run(GOut g);
	}

	public interface Grabber {
		boolean mmousedown(Coord mc, int button);

		boolean mmouseup(Coord mc, int button);

		boolean mmousewheel(Coord mc, int amount);

		void mmousemove(Coord mc);
	}

	public static abstract class Camera extends GLState.Abstract {
		protected haven.Camera view = new haven.Camera(Matrix4f.identity());
		protected Projection proj = new Projection(Matrix4f.identity());
		protected MapView mv;

		public Camera(MapView mv) {
			this.mv = mv;
			resized();
		}

		public boolean click(Coord sc) {
			return (false);
		}

		public void drag(Coord sc) {
		}

		public void release() {
		}

		public boolean wheel(Coord sc, int amount) {
			return (false);
		}

		public void fixangle() {
		}

		public void resized() {
			float field = Config.camera_field_of_view;
			float aspect = ((float) mv.sz.y) / ((float) mv.sz.x);
			proj.update(Projection.makefrustum(new Matrix4f(), -field, field, -aspect * field, aspect * field, 1, 5000));
		}

		public void prep(Buffer buf) {
			proj.prep(buf);
			view.prep(buf);
		}

		public abstract float angle();

		public abstract void tick(double dt);
	}

	private static class FollowCam extends Camera {
		public FollowCam(MapView mv) {
			super(mv);
		}

		private final float fr = 0.0f, h = 10.0f;
		private float ca, cd;
		private Coord3f curc = null;
		private float elev = (float) Math.PI / 6.0f;
		private float angl = 0.0f;
		private Coord dragorig = null;
		private float anglorig;

		public void resized() {
			ca = (float) mv.sz.y / (float) mv.sz.x;
			cd = 400.0f * ca;
		}

		public boolean click(Coord c) {
			anglorig = angl;
			dragorig = c;
			return (true);
		}

		public void drag(Coord c) {
			angl = anglorig + ((float) (c.x - dragorig.x) / 100.0f);
			angl = angl % ((float) Math.PI * 2.0f);
		}

		private double f0 = 0.2, f1 = 0.5, f2 = 0.9;
		private double fl = Math.sqrt(2);
		private double fa = ((fl * (f1 - f0)) - (f2 - f0)) / (fl - 2);
		private double fb = ((f2 - f0) - (2 * (f1 - f0))) / (fl - 2);

		private float field(float elev) {
			double a = elev / (Math.PI / 4);
			return ((float) (f0 + (fa * a) + (fb * Math.sqrt(a))));
		}

		private float dist(float elev) {
			float da = (float) Math.atan(ca * field(elev));
			return ((float) (((cd - (h / Math.tan(elev))) * Math.sin(elev - da) / Math.sin(da)) - (h / Math.sin(elev))));
		}

		@Override
		public void tick(double dt) {
			Coord3f cc = mv.getcc();
			cc.y = -cc.y;
			if (curc == null)
				curc = cc;
			float dx = cc.x - curc.x, dy = cc.y - curc.y;
			if (Math.sqrt((dx * dx) + (dy * dy)) > fr) {
				Coord3f oc = curc;
				float pd = (float) Math.cos(elev) * dist(elev);
				Coord3f cambase = new Coord3f(curc.x + ((float) Math.cos(angl) * pd), curc.y + ((float) Math.sin(angl) * pd), 0.0f);
				float a = cc.xyangle(curc);
				float nx = cc.x + ((float) Math.cos(a) * fr), ny = cc.y + ((float) Math.sin(a) * fr);
				curc = new Coord3f(nx, ny, cc.z);
				angl = curc.xyangle(cambase);
			}

			float field = field(elev);
			view.update(PointedCam.compute(curc.add(0.0f, 0.0f, h), dist(elev), elev, angl));
			proj.update(Projection.makefrustum(new Matrix4f(), -field, field, -ca * field, ca * field, 1, 5000));
		}

		public float angle() {
			return (angl);
		}

		private static final float maxang = (float) (Math.PI / 2 - 0.1);
		private static final float mindist = 10.0f;

		public boolean wheel(Coord c, int amount) {
			float fe = elev;
			elev += amount * elev * 0.02f;
			if (elev > maxang)
				elev = maxang;
			if (dist(elev) < mindist)
				elev = fe;
			return (true);
		}
	}

	private static class SmoothFollowCam extends Camera {
		private final float fr = 0.0f, h = 10.0f;
		private float ca, cd, da;
		private Coord3f curc = null;
		private float elev, telev;
		private float angl, tangl;
		private Coord dragorig = null;
		private float anglorig;

		public SmoothFollowCam(MapView mv) {
			super(mv);
			elev = telev = (float) Math.PI / 6.0f;
			angl = tangl = 0.0f;
		}

		public void resized() {
			ca = (float) mv.sz.y / (float) mv.sz.x;
			cd = 400.0f * ca;
		}

		public boolean click(Coord c) {
			anglorig = tangl;
			dragorig = c;
			return (true);
		}

		public void drag(Coord c) {
			tangl = anglorig + ((float) (c.x - dragorig.x) / 100.0f);
			tangl = tangl % ((float) Math.PI * 2.0f);
		}

		private double f0 = 0.2, f1 = 0.5, f2 = 0.9;
		private double fl = Math.sqrt(2);
		private double fa = ((fl * (f1 - f0)) - (f2 - f0)) / (fl - 2);
		private double fb = ((f2 - f0) - (2 * (f1 - f0))) / (fl - 2);

		private float field(float elev) {
			double a = elev / (Math.PI / 4);
			return ((float) (f0 + (fa * a) + (fb * Math.sqrt(a))));
		}

		private float dist(float elev) {
			float da = (float) Math.atan(ca * field(elev));
			return ((float) (((cd - (h / Math.tan(elev))) * Math.sin(elev - da) / Math.sin(da)) - (h / Math.sin(elev))));
		}

		public void tick(double dt) {
			elev += (telev - elev) * (float) (1.0 - Math.pow(500, -dt));
			if (Math.abs(telev - elev) < 0.0001)
				elev = telev;

			float dangl = tangl - angl;
			while (dangl > Math.PI) dangl -= (float) (2 * Math.PI);
			while (dangl < -Math.PI) dangl += (float) (2 * Math.PI);
			angl += dangl * (float) (1.0 - Math.pow(500, -dt));
			if (Math.abs(tangl - angl) < 0.0001)
				angl = tangl;

			Coord3f cc = mv.getcc();
			cc.y = -cc.y;
			if (curc == null)
				curc = cc;
			float dx = cc.x - curc.x, dy = cc.y - curc.y;
			float dist = (float) Math.sqrt((dx * dx) + (dy * dy));
			if (dist > 250) {
				curc = cc;
			} else if (dist > fr) {
				Coord3f oc = curc;
				float pd = (float) Math.cos(elev) * dist(elev);
				Coord3f cambase = new Coord3f(curc.x + ((float) Math.cos(tangl) * pd), curc.y + ((float) Math.sin(tangl) * pd), 0.0f);
				float a = cc.xyangle(curc);
				float nx = cc.x + ((float) Math.cos(a) * fr), ny = cc.y + ((float) Math.sin(a) * fr);
				Coord3f tgtc = new Coord3f(nx, ny, cc.z);
				curc = curc.add(tgtc.sub(curc).mul((float) (1.0 - Math.pow(500, -dt))));
				if (curc.dist(tgtc) < 0.01)
					curc = tgtc;
				tangl = curc.xyangle(cambase);
			}

			float field = field(elev);
			view.update(PointedCam.compute(curc.add(0.0f, 0.0f, h), dist(elev), elev, angl));
			proj.update(Projection.makefrustum(new Matrix4f(), -field, field, -ca * field, ca * field, 1, 5000));
		}

		public float angle() {
			return (angl);
		}

		private static final float maxang = (float) (Math.PI / 2 - 0.1);
		private static final float mindist = 50.0f;

		public boolean wheel(Coord c, int amount) {
			float fe = telev;
			telev += amount * telev * 0.02f;
			if (telev > maxang)
				telev = maxang;
			if (dist(telev) < mindist)
				telev = fe;
			return (true);
		}

		public String toString() {
			return (String.format("%f %f %f", elev, dist(elev), field(elev)));
		}
	}

	private static class FreeCam extends Camera {
		public FreeCam(MapView mv) {
			super(mv);
		}

		private float dist = 500.0f;
		private float elev = (float) Math.PI / 4.0f;
		private float angl = 0.0f;
		private Coord dragorig = null;
		private float elevorig, anglorig;

		public void tick(double dt) {
			Coord3f cc = mv.getcc();
			cc.y = -cc.y;
			view.update(PointedCam.compute(cc.add(0.0f, 0.0f, 15f), dist, elev, angl));
		}

		public float angle() {
			return (angl);
		}

		public boolean click(Coord c) {
			elevorig = elev;
			anglorig = angl;
			dragorig = c;
			return (true);
		}

		public void drag(Coord c) {
			elev = elevorig - ((float) (c.y - dragorig.y) / 100.0f);
			if (elev < 0.0f) elev = 0.0f;
			if (elev > (Math.PI / 2.0)) elev = (float) Math.PI / 2.0f;
			angl = anglorig + ((float) (c.x - dragorig.x) / 100.0f);
			angl = angl % ((float) Math.PI * 2.0f);
		}

		public boolean wheel(Coord c, int amount) {
			float d = dist + (amount * 10);
			if (d < 5)
				d = 5;
			dist = d;
			return (true);
		}
	}

	private static class SFreeCam extends Camera {

		public SFreeCam(MapView mv) {
			super(mv);
		}

		private float dist = 500.0f, tdist = dist;
		private float elev = (float) Math.PI / 4.0f, telev = elev;
		private float angl = 0.0f, tangl = angl;
		private Coord dragorig = null;
		private float elevorig, anglorig;
		private final float pi2 = (float) (Math.PI * 2);
		private Coord3f cc = null;

		public void tick(double dt) {
			angl = angl + ((tangl - angl) * (1f - (float) Math.pow(500, -dt)));
			while (angl > pi2) {
				angl -= pi2;
				tangl -= pi2;
				anglorig -= pi2;
			}
			while (angl < 0) {
				angl += pi2;
				tangl += pi2;
				anglorig += pi2;
			}
			if (Math.abs(tangl - angl) < 0.0001) angl = tangl;

			elev = elev + ((telev - elev) * (1f - (float) Math.pow(500, -dt)));
			if (Math.abs(telev - elev) < 0.0001) elev = telev;

			dist = dist + ((tdist - dist) * (1f - (float) Math.pow(500, -dt)));
			if (Math.abs(tdist - dist) < 0.0001) dist = tdist;

			Coord3f mc = mv.getcc();
			mc.y = -mc.y;
			if ((cc == null) || (Math.hypot(mc.x - cc.x, mc.y - cc.y) > 250))
				cc = mc;
			else
				cc = cc.add(mc.sub(cc).mul(1f - (float) Math.pow(500, -dt)));
			view.update(PointedCam.compute(cc.add(0.0f, 0.0f, 15f), dist, elev, angl));
		}

		public float angle() {
			return (angl);
		}

		public boolean click(Coord c) {
			elevorig = elev;
			anglorig = angl;
			dragorig = c;
			return (true);
		}

		public void drag(Coord c) {
			telev = elevorig - ((float) (c.y - dragorig.y) / 100.0f);
			if (telev < 0.0f) telev = 0.0f;
			if (telev > (Math.PI / 2.0)) telev = (float) Math.PI / 2.0f;
			tangl = anglorig + ((float) (c.x - dragorig.x) / 100.0f);
		}

		public boolean wheel(Coord c, int amount) {
			float d = tdist + (amount * 5);
			if (d < 5)
				d = 5;
			tdist = d;
			return (true);
		}
	}

	static {
		camtypes.put("best", FreeCam.class);
	}

	static {
		camtypes.put("sucky", SFreeCam.class);
	}

	private static class OrthoCam extends Camera {

		public static final float DEFANGLE = -(float) Math.PI / 4.0f;

		public OrthoCam(MapView mv) {
			super(mv);
		}

		protected float dist = 500.0f;
		protected float elev = (float) Math.PI / 6.0f;
		protected float angl = DEFANGLE;
		protected float field = (float) (100 * Math.sqrt(2));
		private Coord dragorig = null;
		private float anglorig;
		protected Coord3f cc;

		public void tick2(double dt) {
			Coord3f cc = mv.getcc();
			cc.y = -cc.y;
			this.cc = cc;
		}

		public void tick(double dt) {
			tick2(dt);
			float aspect = ((float) mv.sz.y) / ((float) mv.sz.x);
			view.update(PointedCam.compute(cc.add(0.0f, 0.0f, 15f), dist, elev, angl));
			proj.update(Projection.makeortho(new Matrix4f(), -field, field, -field * aspect, field * aspect, 1, 5000));
		}

		public float angle() {
			return (angl);
		}

		public boolean click(Coord c) {
			anglorig = angl;
			dragorig = c;
			return (true);
		}

		@Override
		public void fixangle() {
			angl = stepify(angl - DEFANGLE) + DEFANGLE;
		}

		protected float stepify(float a) {
			if (Config.isocam_steps) {
				a = Math.round(4 * a / Math.PI);
				a = (float) (a * Math.PI / 4);
			}
			return a;
		}

		public void drag(Coord c) {
			float delta = stepify((float) (c.x - dragorig.x) / 100.0f);
			angl = anglorig + delta;
			angl = angl % ((float) Math.PI * 2.0f);
		}

		public boolean wheel(Coord c, int amount) {
			field += amount * 10;
			field = Math.max(Math.min(field, 500), 50);
			return (true);
		}

		public String toString() {
			return (String.format("%f %f %f %f", dist, elev / Math.PI, angl / Math.PI, field));
		}
	}

	private static class SOrthoCam extends OrthoCam {
		public SOrthoCam(MapView mv) {
			super(mv);
		}

		private Coord dragorig = null;
		private float anglorig;
		private float tangl = angl;
		private float tfield = field;
		private final float pi2 = (float) (Math.PI * 2);

		public void tick2(double dt) {
			Coord3f mc = mv.getcc();
			mc.y = -mc.y;
			if ((cc == null) || (Math.hypot(mc.x - cc.x, mc.y - cc.y) > 250))
				cc = mc;
			else
				cc = cc.add(mc.sub(cc).mul(1f - (float) Math.pow(500, -dt)));

			angl = angl + ((tangl - angl) * (1f - (float) Math.pow(500, -dt)));
			while (angl > pi2) {
				angl -= pi2;
				tangl -= pi2;
				anglorig -= pi2;
			}
			while (angl < 0) {
				angl += pi2;
				tangl += pi2;
				anglorig += pi2;
			}
			if (Math.abs(tangl - angl) < 0.0001)
				angl = tangl = tangl % ((float) Math.PI * 2.0f);

			field = field + ((tfield - field) * (1f - (float) Math.pow(500, -dt)));
			if (Math.abs(tfield - field) < 0.0001)
				field = tfield;
		}

		public boolean click(Coord c) {
			anglorig = angl;
			dragorig = c;
			return (true);
		}

		@Override
		public void fixangle() {
			tangl = stepify(tangl - DEFANGLE) + DEFANGLE;
		}

		public void drag(Coord c) {
			float delta = stepify((float) (c.x - dragorig.x) / 100.0f);
			tangl = anglorig + delta;

			if (Config.isocam_steps) {
				float tangl_by10k = tangl * 10000;
				float oneStep_by10k = (pi2 / 8) * 10000;
				float mod_result = tangl_by10k % oneStep_by10k;
				if (mod_result > 100 && (oneStep_by10k - mod_result) > 100) {
					tangl = Math.round(tangl_by10k / oneStep_by10k) * pi2 / 8;
				}
			}
		}

		public boolean wheel(Coord c, int amount) {
			tfield += amount * 10;
			tfield = Math.max(Math.min(tfield, 400), 50);
			return (true);
		}
	}

	@RName("mapview")
	public static class $_ implements Factory {
		public Widget create(Coord c, Widget parent, Object[] args) {
			Coord sz = (Coord) args[0];
			Coord mc = (Coord) args[1];
			int pgob = -1;
			if (args.length > 2)
				pgob = (Integer) args[2];
			return (new MapView(c, sz, parent, mc, pgob));
		}
	}

	public MapView(Coord c, Coord sz, Widget parent, Coord cc, long plgob) {
		super(c, sz, parent);
		setcam(Utils.getpref("defcam", DEFCAM));
		glob = ui.sess.glob;
		this.cc = cc;
		MapView.plgob = plgob;
		try {
			Navigation.setCharacterId(plgob, glob.oc.getgob(plgob).rc);
		} catch (Exception ignore) {
		}
		this.gridol = new TileOutline(glob.map);
		setcanfocus(true);

		r2dwdg = new R2DWdg(this);
	}

	public void enol(int... overlays) {
		for (int ol : overlays)
			visol[ol]++;
	}

	public void disol(int... overlays) {
		for (int ol : overlays)
			visol[ol]--;
	}

	public boolean visol(int ol) {
		return (visol[ol] > 0);
	}

	private final Rendered map = new Rendered() {
		public void draw(GOut g) {
		}

		public boolean setup(RenderList rl) {
			Coord cc = MapView.this.cc.div(tilesz).div(MCache.cutsz);
			Coord o = new Coord();
			for (o.y = -view; o.y <= view; o.y++) {
				for (o.x = -view; o.x <= view; o.x++) {
					Coord pc = cc.add(o).mul(MCache.cutsz).mul(tilesz);
					MapMesh cut = glob.map.getcut(cc.add(o));
					rl.add(cut, Location.xlate(new Coord3f(pc.x, -pc.y, 0)));
					Collection<Gob> fol;
					try {
						fol = glob.map.getfo(cc.add(o));
					} catch (Loading e) {
						fol = Collections.emptyList();
					}
					if (fol != null)
						for (Gob fo : fol)
							addgob(rl, fo);
				}
			}
			return (false);
		}
	};
	public static final int WFOL = 18;
	public static final Tex wftex = Resource.loadtex("gfx/hud/flat");
	private final Rendered mapol = new Rendered() {
		private final GLState[] mats;

		{
			mats = new GLState[32];
			mats[0] = new Material(new Color(255, 0, 128, 32));
			mats[1] = new Material(new Color(0, 0, 255, 32));
			mats[2] = new Material(new Color(255, 0, 0, 32));
			mats[3] = new Material(new Color(128, 0, 255, 32));
			mats[4] = new Material(new Color(255, 0, 0, 96));
			mats[16] = new Material(new Color(0, 255, 0, 32));
			mats[17] = new Material(new Color(255, 255, 0, 32));
			mats[WFOL] = new Material(wftex, true);
			mats[WFOL] = new Material(wftex);
		}

		public void draw(GOut g) {
		}

		public boolean setup(RenderList rl) {
			Coord cc = MapView.this.cc.div(tilesz).div(MCache.cutsz);
			Coord o = new Coord();
			for (o.y = -view; o.y <= view; o.y++) {
				for (o.x = -view; o.x <= view; o.x++) {
					Coord pc = cc.add(o).mul(MCache.cutsz).mul(tilesz);
					for (int i = 0; i < visol.length; i++) {
						if (mats[i] == null)
							continue;
						if (visol[i] > 0) {
							Rendered olcut;
							olcut = glob.map.getolcut(i, cc.add(o));
							if (olcut != null)
								rl.add(olcut, GLState.compose(Location.xlate(new Coord3f(pc.x, -pc.y, 0)), mats[i]));
						}
					}
				}
			}
			return (false);
		}
	};

	void addgob(RenderList rl, final Gob gob) {
		GLState xf;
		try {
			xf = Following.xf(gob);
		} catch (Loading e) {
			xf = null;
		}
		GLState extra = null;
		if (xf == null) {
			xf = gob.loc;
			try {
				Coord3f c = gob.getc();
				Tiler tile = glob.map.tiler(glob.map.gettile(new Coord(c).div(tilesz)));
				extra = tile.drawstate(glob, rl.cfg, c);
			} catch (Loading e) {
				extra = null;
			}
		}
		if (extra != null)
			rl.add(gob, GLState.compose(extra, xf, gob.olmod, gob.save));
		else
			rl.add(gob, GLState.compose(xf, gob.olmod, gob.save));
	}

	private final Rendered gobs = new Rendered() {
		public void draw(GOut g) {
		}

		public boolean setup(RenderList rl) {
			synchronized (glob.oc) {
				for (Gob gob : glob.oc)
					addgob(rl, gob);
			}
			return (false);
		}
	};

	public GLState camera() {
		return (camera);
	}

	protected Projection makeproj() {
		return (null);
	}

	private Coord3f smapcc = null;
	private ShadowMap smap = null;
	private long lsmch = 0;

	private void updsmap(RenderList rl, DirLight light) {
		if (rl.cfg.pref.lshadow.val) {
			if (smap == null)
				smap = new ShadowMap(new Coord(2048, 2048), 750, 5000, 1);
			smap.light = light;
			Coord3f dir = new Coord3f(-light.dir[0], -light.dir[1], -light.dir[2]);
			Coord3f cc = getcc();
			cc.y = -cc.y;
			boolean ch = false;
			long now = System.currentTimeMillis();
			if ((smapcc == null) || (smapcc.dist(cc) > 50)) {
				smapcc = cc;
				ch = true;
			} else {
				if (now - lsmch > 100)
					ch = true;
			}
			if (ch) {
				smap.setpos(smapcc.add(dir.neg().mul(1000f)), dir);
				lsmch = now;
			}
			rl.prepc(smap);
		} else {
			if (smap != null)
				smap.dispose();
			smap = null;
			smapcc = null;
		}
	}

	private DropSky.ResSky sky1 = new DropSky.ResSky(null);
	private DropSky.ResSky sky2 = new DropSky.ResSky(null);

	public Light amb = null;
	private Outlines outlines = new Outlines(false);

	public void setup(RenderList rl) {
		Gob pl = player();
		if (pl != null)
			this.cc = new Coord(pl.getc());
		synchronized (glob) {
			if (glob.lightamb != null) {
				DirLight light = new DirLight(glob.lightamb, glob.lightdif, glob.lightspc, Coord3f.o.sadd((float) glob.lightelev, (float) glob.lightang, 1f));
				rl.add(light, null);
				updsmap(rl, light);
				amb = light;
			} else {
				amb = null;
			}
		}
		if (rl.cfg.pref.outline.val)
			rl.add(outlines, null);
		rl.add(map, null);
		if (Config.showgrid)
			rl.add(gridol, null);
		rl.add(mapol, null);
		rl.add(gobs, null);
		if (placing != null)
			addgob(rl, placing);
		synchronized (extradraw) {
			for (Rendered extra : extradraw)
				rl.add(extra, null);
			extradraw.clear();
		}
		if (Config.skybox) {
			if (glob.sky1 != null) {
				sky1.update(glob.sky1);
				rl.add(sky1, Rendered.last);
				if (glob.sky2 != null) {
					sky2.update(glob.sky2);
					sky2.alpha = glob.skyblend;
					rl.add(sky2, Rendered.last);
				}
			}
		}
	}

	public static final haven.glsl.Uniform amblight = new haven.glsl.Uniform.AutoApply(haven.glsl.Type.INT) {
		public void apply(GOut g, int loc) {
			int idx = -1;
			RenderContext ctx = g.st.get(PView.ctx);
			if (ctx instanceof WidgetContext) {
				Widget wdg = ((WidgetContext) ctx).widget();
				if (wdg instanceof MapView)
					idx = g.st.get(Light.lights).index(((MapView) wdg).amb);
			}
			g.gl.glUniform1i(loc, idx);
		}
	};

	public void drawadd(Rendered extra) {
		synchronized (extradraw) {
			extradraw.add(extra);
		}
	}

	public Gob player() {
		return (glob.oc.getgob(plgob));
	}

	public Coord3f getcc() {
		Gob pl = player();
		if (pl != null)
			return (pl.getc());
		else
			return (new Coord3f(cc.x, cc.y, glob.map.getcz(cc)));
	}

	public static class ClickContext extends RenderContext {
	}

	private TexGL clickbuf = null;
	private GLFrameBuffer clickfb = null;
	private final RenderContext clickctx = new ClickContext();

	private GLState.Buffer clickbasic(GOut g) {
		GLState.Buffer ret = basic(g);
		clickctx.prep(ret);
		if ((clickbuf == null) || !clickbuf.sz().equals(sz)) {
			if (clickbuf != null) {
				clickfb.dispose();
				clickfb = null;
				clickbuf.dispose();
				clickbuf = null;
			}
			clickbuf = new TexE(sz, GL.GL_RGBA, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE);
			clickfb = new GLFrameBuffer(clickbuf, null);
		}
		clickfb.prep(ret);
		new States.Blending(GL.GL_ONE, GL.GL_ZERO).prep(ret);
		return (ret);
	}

	private abstract static class Clicklist<T> extends RenderList {
		private java.util.Map<Color, T> rmap = new HashMap<Color, T>();
		private int i = 1;
		private GLState.Buffer plain, bk;

		abstract protected T map(Rendered r);

		private Clicklist(GLState.Buffer plain) {
			super(plain.cfg);
			this.plain = plain;
			this.bk = new GLState.Buffer(plain.cfg);
		}

		protected Color newcol(T t) {
			int cr = ((i & 0x00000f) << 4) | ((i & 0x00f000) >> 12),
					cg = ((i & 0x0000f0) << 0) | ((i & 0x0f0000) >> 16),
					cb = ((i & 0x000f00) >> 4) | ((i & 0xf00000) >> 20);
			Color col = new Color(cr, cg, cb);
			i++;
			rmap.put(col, t);
			return (col);
		}

		protected void render(GOut g, Rendered r) {
			try {
				if (r instanceof FRendered)
					((FRendered) r).drawflat(g);
			} catch (RenderList.RLoad l) {
				if (ignload) return;
				else throw (l);
			}
		}

		public T get(GOut g, Coord c) {
			return (rmap.get(g.getpixel(c)));
		}

		protected void setup(Slot s, Rendered r) {
			T t = map(r);
			super.setup(s, r);
			s.os.copy(bk);
			plain.copy(s.os);
			bk.copy(s.os, GLState.Slot.Type.GEOM);
			if (t != null) {
				Color col = newcol(t);
				new States.ColState(col).prep(s.os);
			}
		}
	}

	private static class Maplist extends Clicklist<MapMesh> {
		private int mode = 0;
		private MapMesh limit = null;

		private Maplist(GLState.Buffer plain) {
			super(plain);
		}

		protected MapMesh map(Rendered r) {
			if (r instanceof MapMesh)
				return ((MapMesh) r);
			return (null);
		}

		protected void render(GOut g, Rendered r) {
			if (r instanceof MapMesh) {
				MapMesh m = (MapMesh) r;
				if (mode != 0)
					g.state(States.vertexcolor);
				if ((limit == null) || (limit == m))
					m.drawflat(g, mode);
			}
		}
	}

	private Coord checkmapclick(GOut g, Coord c) {
		Maplist rl = new Maplist(clickbasic(g));
		rl.setup(map, clickbasic(g));
		rl.fin();
		{
			rl.render(g);
			MapMesh hit = rl.get(g, c);
			if (hit == null)
				return (null);
			rl.limit = (MapMesh) hit;
		}
		Coord tile;
		{
			rl.mode = 1;
			rl.render(g);
			Color hitcol = g.getpixel(c);
			tile = new Coord(hitcol.getRed() - 1, hitcol.getGreen() - 1);
			if (!tile.isect(Coord.z, rl.limit.sz))
				return (null);
		}
		Coord pixel;
		{
			rl.mode = 2;
			rl.render(g);
			Color hitcol = g.getpixel(c);
			if (hitcol.getBlue() != 0)
				return (null);
			pixel = new Coord((hitcol.getRed() * tilesz.x) / 255, (hitcol.getGreen() * tilesz.y) / 255);
		}
		return (rl.limit.ul.add(tile).mul(tilesz).add(pixel));
	}

	public static class ClickInfo {
		Gob gob;
		Gob.Overlay ol;
		Rendered r;

		ClickInfo(Gob gob, Gob.Overlay ol, Rendered r) {
			this.gob = gob;
			this.ol = ol;
			this.r = r;
		}
	}

	private ClickInfo checkgobclick(GOut g, Coord c) {
		Clicklist<ClickInfo> rl = new Clicklist<ClickInfo>(clickbasic(g)) {
			Gob curgob;
			Gob.Overlay curol;
			ClickInfo curinfo;

			public ClickInfo map(Rendered r) {
				return (curinfo);
			}

			public void add(Rendered r, GLState t) {
				Gob prevg = curgob;
				Gob.Overlay prevo = curol;
				if (r instanceof Gob)
					curgob = (Gob) r;
				else if (r instanceof Gob.Overlay)
					curol = (Gob.Overlay) r;
				if ((curgob == null) || !(r instanceof FRendered))
					curinfo = null;
				else
					curinfo = new ClickInfo(curgob, curol, r);
				super.add(r, t);
				curgob = prevg;
				curol = prevo;
			}
		};
		rl.setup(gobs, clickbasic(g));
		rl.fin();
		rl.render(g);
		return (rl.get(g, c));
	}

	public void delay(Delayed d) {
		synchronized (delayed) {
			delayed.add(d);
		}
	}

	public void delay2(Delayed d) {
		synchronized (delayed2) {
			delayed2.add(d);
		}
	}

	protected void undelay(Collection<Delayed> list, GOut g) {
		synchronized (list) {
			for (Delayed d : list)
				d.run(g);
			list.clear();
		}
	}

	private static final Text.Furnace polownertf = new PUtils.BlurFurn(new Text.Foundry("serif", 30).aa(true), 3, 1, Color.BLACK);
	private Text polownert = null;
	private long polchtm = 0;

	public void setpoltext(String text) {
		polownert = polownertf.render(text);
		polchtm = System.currentTimeMillis();
	}

	private void poldraw(GOut g) {
		long now = System.currentTimeMillis();
		long poldt = now - polchtm;
		if ((polownert != null) && (poldt < 6000)) {
			int a;
			if (poldt < 1000)
				a = (int) ((255 * poldt) / 1000);
			else if (poldt < 4000)
				a = 255;
			else
				a = (int) ((255 * (2000 - (poldt - 4000))) / 2000);
			g.chcolor(255, 255, 255, a);
			g.aimage(polownert.tex(), sz.div(new Coord(2, 4)), 0.5, 0.5);
			g.chcolor();
		}
	}

	private void drawarrow(GOut g, double a) {
		Coord hsz = sz.div(2);
		double ca = -Coord.z.angle(hsz);
		Coord ac;
		if ((a > ca) && (a < -ca)) {
			ac = new Coord(sz.x, hsz.y - (int) (Math.tan(a) * hsz.x));
		} else if ((a > -ca) && (a < Math.PI + ca)) {
			ac = new Coord(hsz.x - (int) (Math.tan(a - Math.PI / 2) * hsz.y), 0);
		} else if ((a > -Math.PI - ca) && (a < ca)) {
			ac = new Coord(hsz.x + (int) (Math.tan(a + Math.PI / 2) * hsz.y), sz.y);
		} else {
			ac = new Coord(0, hsz.y + (int) (Math.tan(a) * hsz.x));
		}
		Coord bc = ac.add(Coord.sc(a, -10));
		g.line(bc, bc.add(Coord.sc(a, -40)), 2);
		g.line(bc, bc.add(Coord.sc(a + Math.PI / 4, -10)), 2);
		g.line(bc, bc.add(Coord.sc(a - Math.PI / 4, -10)), 2);
	}

	public double screenangle(Coord mc, boolean clip) {
		Coord3f cc;
		try {
			cc = getcc();
		} catch (Loading e) {
			return (Double.NaN);
		}
		Coord3f mloc = new Coord3f(mc.x, -mc.y, cc.z);
		float[] sloc = camera.proj.toclip(camera.view.fin(Matrix4f.id).mul4(mloc));
		if (clip) {
			float w = sloc[3];
			if ((sloc[0] > -w) && (sloc[0] < w) && (sloc[1] > -w) && (sloc[1] < w))
				return (Double.NaN);
		}
		float a = ((float) sz.y) / ((float) sz.x);
		return (Math.atan2(sloc[1] * a, sloc[0]));
	}

	private void partydraw(GOut g) {
		for (Party.Member m : ui.sess.glob.party.memb.values()) {
			if (m.gobid == this.plgob)
				continue;
			Coord mc = m.getc();
			if (mc == null)
				continue;
			double a = screenangle(mc, true);
			if (a == Double.NaN)
				continue;
			g.chcolor(m.col);
			drawarrow(g, a);
		}
		g.chcolor();
	}

	private boolean camload = false;
	private Loading lastload = null;

	public void draw(GOut g) {
		glob.map.sendreqs();
		if ((olftimer != 0) && (olftimer < System.currentTimeMillis()))
			unflashol();
		try {
			if (camload)
				throw (new MCache.LoadingMap());
			undelay(delayed, g);
			super.draw(g);
			//project marathon
			//assumes you use the freestyle cam.
			//but who doesn't?
			Matrix4f dgpcam = new Matrix4f(), dgpwxf = new Matrix4f();
			dgpcam.load(g.st.cam);
			dgpwxf.load(g.st.wxf);

			undelay(delayed2, g);
			poldraw(g);
			partydraw(g);

			//project marathon
			if (Config.showgobpath)
				drawGobPath(g, dgpcam, dgpwxf);

			glob.map.reqarea(cc.div(tilesz).sub(MCache.cutsz.mul(view + 1)),
					cc.div(tilesz).add(MCache.cutsz.mul(view + 1)));

			if (Config.showgrid) {
				double tx = Math.ceil(cc.x / tilesz.x / MCache.cutsz.x);
				double ty = Math.ceil(cc.y / tilesz.y / MCache.cutsz.y);
				Coord tc = new Coord((int) (tx - view - 1) * MCache.cutsz.x, (int) (ty - view - 1) * MCache.cutsz.y);
				if (!tc.equals(lasttc)) {
					lasttc = tc;
					gridol.update(tc);
				}
			}
		} catch (Loading e) {
			lastload = e;
			String text = "Loading...";
			g.chcolor(Color.BLACK);
			g.frect(Coord.z, sz);
			g.chcolor(Color.WHITE);
			g.atext(text, sz.div(2), 0.5, 0.5);
		}
	}


	//project marathon
	private void drawGobPath(GOut g, Matrix4f dgpcam, Matrix4f dgpwxf) {
		Moving m;
		g.chcolor(Color.GREEN);

		Matrix4f cam = new Matrix4f(), wxf = new Matrix4f(),
				mv = new Matrix4f(), wxfaccent = new Matrix4f();
		mv.load(cam.load(dgpcam)).mul1(wxf.load(dgpwxf));
		wxfaccent = wxf.trim3(1).transpose();
		float field = Config.camera_field_of_view;
		float aspect = ((float) g.sz.y) / ((float) g.sz.x);
		Projection proj = Projection.frustum(-field, field, -aspect * field, aspect * field, 1, 5000);

		synchronized (glob.oc) {
			for (Gob gob : glob.oc) {
				if (gob.sc == null) {
					continue;
				}
				m = gob.getattr(Moving.class);
				if (m != null) {
					Coord3f reposstart = Coord3f.o, reposend = Coord3f.o;

					reposstart = gob.getc();
					if (m instanceof LinMove) {
						LinMove gobpath = (LinMove) m;
						float targetheight = 0;
						try {
							targetheight = glob.map.getcz(gobpath.t);
						} catch (MCache.LoadingMap e) {
							targetheight = reposstart.z;
						}
						reposend = new Coord3f(gobpath.t.x, gobpath.t.y, targetheight);
					} else if (m instanceof Homing) {
						Homing gobpath = (Homing) m;
						long targetid = gobpath.tgt;
						Gob target = glob.oc.getgob(targetid);
						if (target != null) {
							reposend = target.getc();
						} else {
							reposend = new Coord3f(gobpath.tc.x, gobpath.tc.y, reposstart.z);
						}
						UI.instance.gui.syslog.append("Homing " + reposend.sub(reposstart), Color.CYAN);
						if (gob.id == this.player().id) {
							Coord3f relloc = reposend.sub(reposstart).div(11.0f);
							LocatorTool.setlocation(new Coord((int) relloc.x, (int) relloc.y));
						}
					} else if (m instanceof Following) {
						Following gobpath = (Following) m;
						long targetid = gobpath.tgt;
						Gob target = glob.oc.getgob(targetid);
						if (target != null) {
							reposend = target.getc();
						} else {
							reposend = gobpath.getc();
						}
					} else {
						continue;
					}

					reposstart.x -= wxf.get(3, 0);
					reposstart.y *= -1;
					reposstart.y -= wxf.get(3, 1);
					reposstart.z -= wxf.get(3, 2);
					reposstart = wxfaccent.mul4(reposstart);
					Coord3f sstart = proj.toscreen(mv.mul4(reposstart), g.sz);
					Coord scstart = new Coord(sstart);

					reposend.x -= wxf.get(3, 0);
					reposend.y *= -1;
					reposend.y -= wxf.get(3, 1);
					reposend.z -= wxf.get(3, 2);
					reposend = wxfaccent.mul4(reposend);
					Coord3f send = proj.toscreen(mv.mul4(reposend), g.sz);
					Coord scend = new Coord(send);

					g.line(scstart, scend, 2);
				}
			}
		}
		g.chcolor();
	}

	public void tick(double dt) {
		camload = false;
		try {
			camera.tick(dt);
		} catch (Loading e) {
			camload = true;
		}
		if (placing != null)
			placing.ctick((int) (dt * 1000));
	}

	public void resize(Coord sz) {
		super.resize(sz);
		r2dwdg.resize(sz);
		camera.resized();
	}

	@Override
	protected void render2d(GOut g) {
		//2d render will be done in r2dwdg
	}

	private class Plob extends Gob {
		Coord lastmc = null;
		boolean freerot = false;

		private Plob(Indir<Resource> res, Message sdt) {
			super(MapView.this.glob, Coord.z);
			setattr(new ResDrawable(this, res, sdt));
			if (ui.mc.isect(rootpos(), sz)) {
				delay(new Adjust(ui.mc.sub(rootpos()), false));
			}
		}

		private class Adjust extends Maptest {
			boolean adjust;

			Adjust(Coord c, boolean ta) {
				super(c);
				adjust = ta;
			}

			public void hit(Coord pc, Coord mc) {
				rc = mc;
				if (adjust)
					rc = rc.div(tilesz).mul(tilesz).add(tilesz.div(2));
				Gob pl = player();
				if ((pl != null) && !freerot)
					a = rc.angle(pl.rc);
				lastmc = pc;
			}
		}
	}

	private int olflash;
	private long olftimer;

	private void unflashol() {
		for (int i = 0; i < visol.length; i++) {
			if ((olflash & (1 << i)) != 0)
				visol[i]--;
		}
		olflash = 0;
		olftimer = 0;
	}

	public void uimsg(String msg, Object... args) {
		if (msg == "place") {
			int a = 0;
			Indir<Resource> res = ui.sess.getres((Integer) args[a++]);
			Message sdt;
			if ((args.length > a) && (args[a] instanceof byte[]))
				sdt = new Message(0, (byte[]) args[a++]);
			else
				sdt = Message.nil;
			placing = new Plob(res, sdt);
			while (a < args.length) {
				Indir<Resource> ores = ui.sess.getres((Integer) args[a++]);
				Message odt;
				if ((args.length > a) && (args[a] instanceof byte[]))
					odt = new Message(0, (byte[]) args[a++]);
				else
					odt = Message.nil;
				placing.ols.add(new Gob.Overlay(-1, ores, odt));
			}
		} else if (msg == "unplace") {
			placing = null;
		} else if (msg == "move") {
			cc = (Coord) args[0];
		} else if (msg == "flashol") {
			unflashol();
			olflash = (Integer) args[0];
			for (int i = 0; i < visol.length; i++) {
				if ((olflash & (1 << i)) != 0)
					visol[i]++;
			}
			olftimer = System.currentTimeMillis() + (Integer) args[1];
		} else {
			super.uimsg(msg, args);
		}
	}

	private boolean camdrag = false;

	public abstract class Maptest implements Delayed {
		private final Coord pc;

		public Maptest(Coord c) {
			this.pc = c;
		}

		public void run(GOut g) {
			GLState.Buffer bk = g.st.copy();
			Coord mc;
			try {
				GL gl = g.gl;
				g.st.set(clickbasic(g));
				g.apply();
				gl.glClear(GL.GL_DEPTH_BUFFER_BIT | GL.GL_COLOR_BUFFER_BIT);
				mc = checkmapclick(g, pc);
			} finally {
				g.st.set(bk);
			}
			if (mc != null)
				hit(pc, mc);
			else
				nohit(pc);
		}

		protected abstract void hit(Coord pc, Coord mc);

		protected void nohit(Coord pc) {
		}
	}

	public abstract class Hittest implements Delayed {
		private final Coord clickc;

		public Hittest(Coord c) {
			clickc = c;
		}

		public void run(GOut g) {
			GLState.Buffer bk = g.st.copy();
			Coord mapcl;
			ClickInfo gobcl;
			try {
				GL gl = g.gl;
				g.st.set(clickbasic(g));
				g.apply();
				gl.glClear(GL.GL_DEPTH_BUFFER_BIT | GL.GL_COLOR_BUFFER_BIT);
				mapcl = checkmapclick(g, clickc);
				g.st.set(bk);
				g.st.set(clickbasic(g));
				g.apply();
				gl.glClear(GL.GL_COLOR_BUFFER_BIT);
				gobcl = checkgobclick(g, clickc);
			} finally {
				g.st.set(bk);
			}
			if (mapcl != null) {
				if (gobcl == null)
					hit(clickc, mapcl, null);
				else
					hit(clickc, mapcl, gobcl);
			} else {
				nohit(clickc);
			}
		}

		protected abstract void hit(Coord pc, Coord mc, ClickInfo inf);

		protected void nohit(Coord pc) {
		}
	}

	private static int getid(Rendered tgt) {
		if (tgt instanceof ResPart)
			return (((ResPart) tgt).partid());
		return (-1);
	}

	private class Click extends Hittest {
		int clickb;

		private Click(Coord c, int b) {
			super(c);
			clickb = b;
		}

		protected void hit(Coord pc, Coord mc, ClickInfo inf) {
			int modflags = ui.modflags();
			if (inf == null) {
				if (Config.center) {
					mc = mc.div(11).mul(11).add(5, 5);
				}
				wdgmsg("click", pc, mc, clickb, modflags);
			} else {
				if (ui.modmeta) {
					ChatUI.Channel channel = ui.gui.chat.sel;
					if (channel != null && channel instanceof ChatUI.EntryChannel) {
						((ChatUI.EntryChannel) channel).send(String.format("$hl[%d]", inf.gob.id));
					}
					if(ui.modshift){
						Coord gobc = inf.gob.rc;
						Coord gobtc = gobc.div(11.0f);
						MCache.Grid gobgrid = UI.instance.sess.glob.map.getgridt(gobtc);
						Coord gridOffset = gobtc.sub(gobgrid.ul);
						RemoteNavigation.MarkerData markerData = new RemoteNavigation.MarkerData();
						ResDrawable d = inf.gob.getattr(ResDrawable.class);
						markerData.setName(d.res.get().basename());
						markerData.setGridId(gobgrid.id);
						markerData.setGridOffset(gridOffset);
						markerData.setImage(d.res.get().name);
						RemoteNavigation.getInstance().uploadMarkerData(markerData);
					}
					System.out.println(inf.gob.details());
				}
				if (inf.ol == null) {
					wdgmsg("click", pc, mc, clickb, modflags, 0, (int) inf.gob.id, inf.gob.rc, 0, getid(inf.r));
				} else {
					wdgmsg("click", pc, mc, clickb, modflags, 1, (int) inf.gob.id, inf.gob.rc, inf.ol.id, getid(inf.r));
				}
			}
		}
	}

	public void grab(Grabber grab) {
		this.grab = grab;
	}

	public void release(Grabber grab) {
		if (this.grab == grab)
			this.grab = null;
	}

	//project free the camera
	private boolean LMBdown = false;
	private boolean mousemoved = false;

	public boolean mousedown(Coord c, int button) {
		parent.setfocus(this);

		if (button == 1) {
			LMBdown = true;
			mousemoved = false;
		}

		if ((!Config.laptopcontrols && button == 2) || (Config.laptopcontrols && button == 3 && LMBdown)) {
			if (((Camera) camera).click(c)) {
				ui.grabmouse(this);
				camdrag = true;
			}
		} else if (placing != null) {
			if (placing.lastmc != null)
				wdgmsg("place", placing.rc, (int) (placing.a * 180 / Math.PI), button, ui.modflags());
		} else if ((grab != null) && grab.mmousedown(c, button)) {
		} else if (!(Config.laptopcontrols && LMBdown)) {
			delay(new Click(c, button));
		}
		return (true);
	}

	public void mousemove(Coord c) {
		if (grab != null)
			grab.mmousemove(c);
		if (camdrag) {
			((Camera) camera).drag(c);
			mousemoved = true;
		} else if (placing != null) {
			if ((placing.lastmc == null) || !placing.lastmc.equals(c)) {
				delay(placing.new Adjust(c, !ui.modctrl));
			}
		}
	}

	public boolean mouseup(Coord c, int button) {
		if ((!Config.laptopcontrols && button == 2) || (Config.laptopcontrols && button == 3 && camdrag)) {
			if (camdrag) {
				((Camera) camera).release();
				ui.grabmouse(null);
				camdrag = false;
			}
		} else if (grab != null) {
			grab.mmouseup(c, button);
		} else if (Config.laptopcontrols && LMBdown && button == 1 && !mousemoved && placing == null) {
			delay(new Click(c, button));
		}

		if (button == 1) LMBdown = false;

		return (true);
	}

	public boolean mousewheel(Coord c, int amount) {
		if ((grab != null) && grab.mmousewheel(c, amount))
			return (true);
		if (ui.modshift) {
			if (placing != null) {
				placing.freerot = true;
				if (ui.modctrl || (Config.laptopcontrols && ui.modmeta))
					placing.a += amount * Math.PI / 16;
				else
					placing.a = (Math.PI / 4) * Math.round((placing.a + (amount * Math.PI / 4)) / (Math.PI / 4));
			}
			return (true);
		}
		return (((Camera) camera).wheel(c, amount));
	}

	public boolean drop(final Coord cc, final Coord ul) {
		delay(new Hittest(cc) {
			public void hit(Coord pc, Coord mc, ClickInfo inf) {
				wdgmsg("drop", pc, mc, ui.modflags());
			}
		});
		return (true);
	}

	private void itemact(Coord pc, Coord mc, ClickInfo inf){
		if (Config.clientshift) {
			int modflags = ui.modflags();
			WItem witem = ui.gui.vhand;
			if ((modflags & 1) == 1) {
				modflags ^= 1;
			}
			wdgmsg("itemact", pc, mc, modflags, (int) inf.gob.id, inf.gob.rc, getid(inf.r));
			if ((ui.modflags() & 1) == 1) {
				if (witem != null) {
					List<WItem> items = ui.gui.maininv.getSameName(witem.item.resname(), ui.modmeta);
					if (items != null) {
						Defer.later(new Defer.Callable<Object>() {
							public java.lang.Object call() {
								WItem nxtitem = items.remove(0);
								try {
									waitForEmptyHand(ui.gui, 5000, "itemact timeout waiting for empty hand");
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
								nxtitem.item.wdgmsg("take", Coord.z);
								return true;
							}
						});

					}
				}
			}
		} else {
			wdgmsg("itemact", pc, mc, ui.modflags(), (int) inf.gob.id, inf.gob.rc, getid(inf.r));
		}
	}

	public boolean iteminteract(Coord cc, Coord ul) {
		delay(new Hittest(cc) {
			public void hit(Coord pc, Coord mc, ClickInfo inf) {
				if (inf == null) {
					wdgmsg("itemact", pc, mc, ui.modflags());
				} else if (MapView.this.ui.modctrl && !MapView.this.ui.gui.hand.isEmpty()) {
					if (signalToStop) {
						try {
							if (inf.gob == ffTarget) {
								return;
							}
						} catch (Exception exception) {
							// empty catch block
						}
					}
					ffTarget = inf.gob;
					UI.instance.modshift = true;
					itemact(pc, mc, inf);
					UI.instance.modshift = false;
					try {
						new Thread(new Runnable() {

							@Override
							public void run() {
								while (ffThread > 0) {
									signalToStop = true;
									MapView.sleep(10);
								}
								signalToStop = false;
								++ffThread;
								int handID = MapView.this.getHandID();
								int counter = 0;
								boolean waiting = true;
								while (!signalToStop && waiting) {
									if (++counter > 300) {
										--ffThread;
										return;
									}
									if (handID != MapView.this.getHandID()) {
										waiting = false;
									}
									if (UI.instance.gui.map.player().getattr(Moving.class) != null) break;
									MapView.sleep(10);
								}
								while (!signalToStop && waiting) {
									if (++counter > 500) {
										--ffThread;
										return;
									}
									if (handID != MapView.this.getHandID()) {
										waiting = false;
									}
									if (UI.instance.gui.map.player().getattr(Moving.class) == null) break;
									MapView.sleep(10);
								}
								MapView.this.startFuelFiller(inf, pc, mc);
							}
						}, "FuelFillerIntegrated").start();
					} catch (Exception e) {
						--ffThread;
					}
				} else {
					itemact(pc, mc, inf);
				}
			}
		});
		return (true);
	}

	public boolean globtype(char c, java.awt.event.KeyEvent ev) {
		//project free the camera
		if (Config.laptopcontrols) {
			if (c == '+') {
				this.mousewheel(cc, -1);
			} else if (c == '-') {
				this.mousewheel(cc, +1);
			}
		}
		return (false);
	}

	public void setcam(String cam) {
		try {
			Constructor<? extends Camera> constructor;
			Class<? extends Camera> camtype = camtypes.get(cam);
			if (camtype == null)
				camtype = camtypes.values().iterator().next();
			constructor = camtype.getConstructor(MapView.class);
			camera = Utils.construct(constructor, MapView.this);//constructor.newInstance(this);
		} catch (NoSuchMethodException e) {
			e.printStackTrace(System.out);
		} catch (SecurityException e) {
			e.printStackTrace(System.out);
		} catch (IllegalArgumentException e) {
			e.printStackTrace(System.out);
		}
	}

	public class GrabXL implements Grabber {
		private final Grabber bk;
		public boolean mv = false;

		public GrabXL(Grabber bk) {
			this.bk = bk;
		}

		public boolean mmousedown(Coord cc, final int button) {
			delay(new Hittest(cc) {
				public void hit(Coord pc, Coord mc, ClickInfo inf) {
					bk.mmousedown(mc, button);
				}
			});
			return (true);
		}

		public boolean mmouseup(Coord cc, final int button) {
			delay(new Hittest(cc) {
				public void hit(Coord pc, Coord mc, ClickInfo inf) {
					bk.mmouseup(mc, button);
				}
			});
			return (true);
		}

		public boolean mmousewheel(Coord cc, final int amount) {
			delay(new Hittest(cc) {
				public void hit(Coord pc, Coord mc, ClickInfo inf) {
					bk.mmousewheel(mc, amount);
				}
			});
			return (true);
		}

		public void mmousemove(Coord cc) {
			if (mv) {
				delay(new Hittest(cc) {
					public void hit(Coord pc, Coord mc, ClickInfo inf) {
						bk.mmousemove(mc);
					}
				});
			}
		}
	}

	private java.util.Map<String, Console.Command> cmdmap = new TreeMap<String, Console.Command>();

	{
		cmdmap.put("cam", new Console.Command() {
			public void run(Console cons, String[] args) throws Exception {
				setcam(args[1]);
				Class<? extends Camera> cc = camtypes.get(args[1]);
				if (cc == null)
					throw (new Exception("no such camera type: " + args[1]));
				Constructor<? extends Camera> ccc = cc.getConstructor(MapView.class);
				camera = Utils.construct(ccc, MapView.this);
			}
		});
		cmdmap.put("whyload", new Console.Command() {
			public void run(Console cons, String[] args) throws Exception {
				Loading l = lastload;
				if (l == null)
					throw (new Exception("Not loading"));
				l.printStackTrace(cons.out);
			}
		});
	}

	public java.util.Map<String, Console.Command> findcmds() {
		return (cmdmap);
	}

	public void togglegrid() {
		Config.showgrid = !Config.showgrid;
		Utils.setprefb("showgrid", Config.showgrid);
		if (Config.showgrid) {
			Coord tc = new Coord((int) (cc.x / tilesz.x / MCache.cutsz.x - view - 1) * MCache.cutsz.x,
					(int) (cc.y / tilesz.y / MCache.cutsz.y - view - 1) * MCache.cutsz.y);
			lasttc = tc;
			gridol.update(tc);
		}
	}

	private static void sleep(int timeInMiliS) {
		try {
			Thread.sleep(timeInMiliS);
		} catch (InterruptedException interruptedException) {
			// empty catch block
		}
	}

	private int getHandID() {
		try {
			return UI.instance.gui.hand.iterator().next().wdgid();
		} catch (Exception exception) {
			return 0;
		}
	}

	private boolean checkNames(String nm) {
		return nm.contains("terobjs/brazier") || nm.contains("terobjs/torchpost") || nm.contains("terobjs/babybrazier") || nm.contains("terobjs/stove") || nm.contains("terobjs/oven") || nm.contains("terobjs/fireplace") || nm.contains("terobjs/meatsmoker") || nm.contains("terobjs/fineryforge") || nm.contains("terobjs/oresmelter") || nm.contains("terobjs/cementationfurnace") || nm.contains("terobjs/kiln") || nm.contains("terobjs/haystack") || nm.contains("terobjs/field") || nm.contains("terobjs/compost") || nm.contains("terobjs/turkeycoop") || nm.contains("terobjs/barrel") || nm.contains("terobjs/bigbarrel") || nm.contains("terobjs/farmerbin") || nm.contains("terobjs/ttub") || nm.contains("terobjs/windmillgrinderbottom") || nm.contains("gfx/terobjs/foodtrough") || nm.contains("gfx/terobjs/watertower");
	}

	private void startFuelFiller(ClickInfo inf, Coord pc, Coord mc) {
		Gob closest_gob = null;
		Gob current_gob = inf.gob;
		ResDrawable rd = null;
		String nm = "";
		try {
			rd = current_gob.getattr(ResDrawable.class);
			if (rd != null) {
				nm = rd.res.get().name;
			}
		} catch (Loading loading) {
			// empty catch block
		}
		if (this.checkNames(nm)) {
			closest_gob = current_gob;
		}
		int counter = 0;
		int oldID = 0;
		int handID = 0;
		if (closest_gob != null) {
			while (!signalToStop) {
				try {
					handID = this.ui.gui.hand.iterator().next().wdgid();
				} catch (Exception exception) {
					// empty catch block
				}
				if (handID == oldID) {
					if (++counter > 2) {
						signalToStop = true;
					}
				} else {
					counter = 0;
					oldID = handID;
				}
				if (!signalToStop) {
					UI.instance.modshift=true;
					this.itemact(pc, mc, inf);
					UI.instance.modshift=false;
					MapView.sleep(150);
				}
				if (!this.ui.gui.hand.isEmpty()) continue;
				for (int i = 0; i < 15; ++i) {
					MapView.sleep(100);
					if (!this.ui.gui.hand.isEmpty()) break;
				}
				if (!this.ui.gui.hand.isEmpty()) continue;
				signalToStop = true;
			}
		}
		--ffThread;
		signalToStop = false;
	}

	public Move[] findpath(final Coord c) {
		System.out.println("findpath(final Coord c)");
		final NBAPathfinder finder = new NBAPathfinder(ui);
		System.out.println("Got NBA");
		final List<Move> moves = finder.path(new Coord(ui.sess.glob.oc.getgob(plgob).getc()), c.floor());
		System.out.println("Got Moves "+moves);
		return moves != null ? moves.toArray(new Move[0]) : null;
	}

	public Move[] findpath(final Gob g) {
		g.updatePathfindingBlackout(true);
		final Move[] moves = findpath(new Coord(g.getc()));
		g.updatePathfindingBlackout(false);
		return moves;
	}

	public boolean pathto(final Coord c) {
		System.out.println("pathto(final Coord c)");
		clearmovequeue();
		final Move[] moves = findpath(c);
		if (moves != null) {
			for (final Move m : moves) {
				queuemove(m.dest());
			}
			return (true);
		} else {
			return (false);
		}
	}

	public boolean pathto(final Gob g) {
		g.updatePathfindingBlackout(true);
		boolean yea = pathto(new Coord(g.getc()));
		pathfindGob = g;
		pathfindGobMouse = 1;
		g.updatePathfindingBlackout(false);
		return yea;
	}

	int finishTimes = 0;
	int maxfinish = 100;
	boolean isclickongob = false;

	public void clearmovequeue() {
		finishTimes = 0;
		if (pathfindGob != null) {
			pathfindGob = null; //set pathfind gob back to null incase pathfinding was interrupted in the middle of a pathfind right click.
			pathfindGobMod = 0;
			pathfindGobMouse = 0;
			isclickongob = false;
		}
		movequeue.clear();
		movingto = null;
	}

	public void queuemove(final Coord c) {
		System.out.println("Queue move");
		movequeue.add(c);
	}
}
