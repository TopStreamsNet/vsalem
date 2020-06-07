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
import java.util.*;
import java.io.*;
import java.util.List;

public class AudioSprite {
	public static final Map<Resource,Long> last_instances = new HashMap<>();
	public static final Sprite.Factory fact = new Sprite.Factory() {
		private Resource.Audio randoom(Resource res, String id) {
			List<Resource.Audio> cl = new ArrayList<Resource.Audio>();
			for(Resource.Audio clip : res.layers(Resource.audio)) {
				if(clip.id == id)
					cl.add(clip);
			}
			if(!cl.isEmpty())
				return(cl.get((int)(Math.random() * cl.size())));
			return(null);
		}

		public Sprite create(Sprite.Owner owner, Resource res, Message sdt) {
			if(res.name != null && !res.name.startsWith("sfx/kritter") && !res.name.startsWith("sfx/terobjs"))
				UI.instance.gui.syslog.append("Audio: "+res.name, Color.RED);
			synchronized(last_instances) {
				Long lasttime = last_instances.get(res);
				Long now = System.currentTimeMillis();
				if(lasttime != null) {
					if(now  - lasttime < 300) {
						throw new Loading("Too many sounds playing at once.");
					}
				}
				last_instances.put(res, now);
			}
			{
				Resource.Audio clip = randoom(res, "cl");
				if(clip != null)
					return(new ClipSprite(owner, res, clip));
			}
			{
				Resource.Audio clip = randoom(res, "rep");
				if(clip != null)
					return(new RepeatSprite(owner, res, randoom(res, "beg"), clip, randoom(res, "end")));
			}
			{
				Resource.Audio clip = res.layer(Resource.audio, "amb");
				if(clip != null)
					return(new Ambience(owner, res));
			}
			return(null);
		}
	};

	public static class ClipSprite extends Sprite {
		public final ActAudio.PosClip clip;
		private boolean done = false;

		public ClipSprite(Owner owner, Resource res, Resource.Audio clip) {
			super(owner, res);
			this.clip = new ActAudio.PosClip(new Audio.DataClip(clip.pcmstream()) {
				protected void eof() {
					super.eof();
					done = true;
				}
			});
		}

		public boolean setup(RenderList r) {
			r.add(clip, null);
			return(false);
		}

		public boolean tick(int dt) {
			return(done);
		}
	}

	public static class RepeatSprite extends Sprite implements Gob.Overlay.CDel {
		private ActAudio.PosClip clip;
		private final Resource.Audio end;

		public RepeatSprite(Owner owner, Resource res, final Resource.Audio beg, final Resource.Audio clip, Resource.Audio end) {
			super(owner, res);
			this.end = end;
			RepeatStream.Repeater rep = new RepeatStream.Repeater() {
				private boolean f = true;

				public InputStream cons() {
					if(f && (beg != null)) {
						f = false;
						return(beg.pcmstream());
					}
					return(clip.pcmstream());
				}
			};
			this.clip = new ActAudio.PosClip(new Audio.DataClip(new RepeatStream(rep)));
		}

		public boolean setup(RenderList r) {
			if(clip != null && !(res.name.equals("sfx/bgm/boston") && Config.mute_violin) && !(res.name.equals("sfx/bgm/markethustle") && Config.mute_violin))
				r.add(clip, null);
			return(false);
		}

		public boolean tick(int dt) {
			return(clip == null);
		}

		public void delete() {
			if(end != null)
				clip = new ActAudio.PosClip(new Audio.DataClip(end.pcmstream()) {
					protected void eof() {
						super.eof();
						RepeatSprite.this.clip = null;
					}
				});
			else
				clip = null;
		}
	}

	public static class Ambience extends Sprite {
		public final ActAudio.Ambience amb;

		public Ambience(Owner owner, Resource res) {
			super(owner, res);
			this.amb = new ActAudio.Ambience(res);
		}

		public boolean setup(RenderList r) {
			r.add(amb, null);
			return(false);
		}
	}
}
