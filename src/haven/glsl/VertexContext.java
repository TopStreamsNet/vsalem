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

package haven.glsl;

public class VertexContext extends ShaderContext {
    public VertexContext(ProgramContext prog) {
	super(prog);
    }

    public final Function.Def main = new Function.Def(Type.VOID, new Symbol.Fix("main"));
    public final ValBlock mainvals = new ValBlock();

    public static final Variable gl_Vertex = new Variable.Implicit(Type.VEC4, new Symbol.Fix("gl_Vertex"));
    public static final Variable gl_Normal = new Variable.Implicit(Type.VEC3, new Symbol.Fix("gl_Normal"));
    public static final Variable gl_Color = new Variable.Implicit(Type.VEC4, new Symbol.Fix("gl_Color"));
    public static final Variable gl_ModelViewMatrix = new Variable.Implicit(Type.VEC4, new Symbol.Fix("gl_ModelViewMatrix"));
    public static final Variable gl_NormalMatrix = new Variable.Implicit(Type.VEC4, new Symbol.Fix("gl_NormalMatrix"));
    public static final Variable gl_ProjectionMatrix = new Variable.Implicit(Type.VEC4, new Symbol.Fix("gl_ProjectionMatrix"));
    public static final Variable gl_ModelViewProjectionMatrix = new Variable.Implicit(Type.VEC4, new Symbol.Fix("gl_ModelViewProjectionMatrix"));
    public static final Variable gl_Position = new Variable.Implicit(Type.VEC4, new Symbol.Fix("gl_Position"));
    public static final Variable[] gl_MultiTexCoord = {
	new Variable.Implicit(Type.VEC4, new Symbol.Fix("gl_MultiTexCoord0")),
	new Variable.Implicit(Type.VEC4, new Symbol.Fix("gl_MultiTexCoord1")),
	new Variable.Implicit(Type.VEC4, new Symbol.Fix("gl_MultiTexCoord2")),
	new Variable.Implicit(Type.VEC4, new Symbol.Fix("gl_MultiTexCoord3")),
	new Variable.Implicit(Type.VEC4, new Symbol.Fix("gl_MultiTexCoord4")),
	new Variable.Implicit(Type.VEC4, new Symbol.Fix("gl_MultiTexCoord5")),
	new Variable.Implicit(Type.VEC4, new Symbol.Fix("gl_MultiTexCoord6")),
	new Variable.Implicit(Type.VEC4, new Symbol.Fix("gl_MultiTexCoord7")),
    };

    public final ValBlock.Value eyev = mainvals.new Value(Type.VEC4, new Symbol.Gen("eyev")) {
	    public Expression root() {
		return(new Mul(gl_ModelViewMatrix.ref(), gl_Vertex.ref()));
	    }
	};
    public final ValBlock.Value eyen = mainvals.new Value(Type.VEC3, new Symbol.Gen("eyen")) {
	    public Expression root() {
		return(new Mul(gl_NormalMatrix.ref(), gl_Normal.ref()));
	    }
	};
    public final ValBlock.Value posv = mainvals.new Value(Type.VEC4, new Symbol.Gen("posv")) {
	    {
		softdep(eyev);
		force();
	    }

	    public Expression root() {
		return(new Expression() {
			public Expression process(Context ctx) {
			    if(eyev.used) {
				return(new Mul(gl_ProjectionMatrix.ref(), eyev.ref()).process(ctx));
			    } else {
				return(new Mul(gl_ModelViewProjectionMatrix.ref(), gl_Vertex.ref()).process(ctx));
			    }
			}
		    });
	    }

	    protected void cons2(Block blk) {
		var = gl_Position;
		blk.add(new LBinOp.Assign(var.ref(), init));
	    }
	};

    public void construct(java.io.Writer out) {
	mainvals.cons(main.code);
	main.define(this);
	output(new Output(out, this));
    }
}
