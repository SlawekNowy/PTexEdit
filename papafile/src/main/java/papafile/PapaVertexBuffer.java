/*
 *  This file is part of PapaFile
 * 
 *  File IO tools for Planetary Annihilation's papa files.
 *  Copyright (C) 2020 Marcus Der <marcusder@hotmail.com>
 * 
 *  PapaFile is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  PapaFile is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License
 *  along with PapaFile.  If not, see <https://www.gnu.org/licenses/>.
 */
package papafile;

import java.awt.Color;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import papafile.PapaFile.BuildNotification;

public class PapaVertexBuffer extends PapaComponent {
	private static final String[] formats = {	"Position3","Position3Color4bTexCoord2","Position3Color4bTexCoord4", "Position3Color4bTexCoord6", "Position3Normal3",
												"Position3Normal3TexCoord2", "Position3Normal3Color4TexCoord2", "Position3Normal3Color4TexCoord4",
												"Position3Weights4bBones4bNormal3TexCoord2", "Position3Normal3Tan3Bin3TexCoord2", "Position3Normal3Tan3Bin3TexCoord4",
												"Position3Normal3Tan3Bin3Color4TexCoord4", "TexCoord4", "Position3Color8fTexCoord6", "Matrix"};
	
	private PapaFile parent;
	
	private byte format;
	
	private PapaVertex[] vertices;
	
	private VertexBufferConverter modelConverter;

	public String getFormat() {
		return formats[format];
	}
	
	public int getNumVertices() {
		return vertices.length;
	}
	
	public PapaVertex getVertex(int index) {
		return vertices[index];
	}
	
	private VertexBufferConverter getInstance(String format) throws IOException {
		switch(format) {
		case 			"Position3":
			return new   Position3();
		case 			"Position3Normal3Color4TexCoord2":
			return new 	 Position3Normal3Color4TexCoord2();
		case 			"Position3Normal3Color4TexCoord4":
			return new   Position3Normal3Color4TexCoord4();
		case 			"Position3Weights4bBones4bNormal3TexCoord2":
			return new   Position3Weights4bBones4bNormal3TexCoord2();
		case 			"Position3Normal3Tan3Bin3TexCoord4":
			return new   Position3Normal3Tan3Bin3TexCoord4();
		default:
			throw new IOException("Unsupported format: "+format);
		}
	}
	
	private void checkData(ByteBuffer buf, int numVertices, VertexBufferConverter converter) throws IOException {
		int expectedSize = converter.calcSize(numVertices);
		int actualSize = buf.limit();
		if(actualSize != expectedSize)
			throw new IOException("Vertex buffer data size of "+actualSize+" bytes does not match expected size of " + expectedSize+" bytes");
	}
	
	
	public PapaVertexBuffer(byte format, int vertices, byte[] data, PapaFile p) throws IOException{
		this.format = format;
		this.parent = p;
		
		ByteBuffer buf = ByteBuffer.wrap(data);
		buf.order(ByteOrder.LITTLE_ENDIAN);
		modelConverter = getInstance(getFormat());
		
		decodeAll(vertices, buf, modelConverter);
	}
	
	private void decodeAll(int numVertices, ByteBuffer buf, VertexBufferConverter converter) throws IOException {
		checkData(buf, numVertices, converter);
		
		this.vertices = modelConverter.decode(numVertices, buf);
	}
	
	private abstract class VertexBufferConverter {
		protected abstract void encodeVertices(PapaVertex[] vertices, ByteBuffer writer);
		public abstract PapaVertex[] decode(int vertices, ByteBuffer buf);
		public abstract int calcSize(int vertices);
		public abstract byte formatIndex();
		public abstract boolean testCompatibility(PapaVertex v);
		public byte[] encode(PapaVertex... v) {
			byte[] buf = new byte[calcSize(v.length)];
			ByteBuffer b = ByteBuffer.wrap(buf);
			b.order(ByteOrder.LITTLE_ENDIAN);
			encodeVertices(v, b);
			return buf;
		}
	}
	
	private class Position3 extends VertexBufferConverter {

		@Override
		protected void encodeVertices(PapaVertex[] vertices, ByteBuffer writer) {
			for(PapaVertex v : vertices) {
				float[] pos = v.getPosition();
				writer.putFloat(pos[0]);
				writer.putFloat(pos[1]);
				writer.putFloat(pos[2]);
			}
		}

		@Override
		public PapaVertex[] decode(int vertices, ByteBuffer buf) {
			PapaVertex[] vert = new PapaVertex[vertices];
			for(int i = 0;i<vertices;i++) {
				float[] pos = new float[] {buf.getFloat(), buf.getFloat(),buf.getFloat()};
				vert[i] = new PapaVertex(pos);
			}
			return vert;
		}
		
		@Override
		public int calcSize(int vertices) {
			return vertices * 12;
		}

		@Override
		public byte formatIndex() {
			return 0;
		}

		@Override
		public boolean testCompatibility(PapaVertex v) {
			return true;
		}
	}
	
	private class Position3Normal3Color4TexCoord2 extends VertexBufferConverter {
		
		@Override
		protected void encodeVertices(PapaVertex[] vertices, ByteBuffer writer) {
			for(PapaVertex v : vertices) {
				float[] pos = v.getPosition();
				writer.putFloat(pos[0]);
				writer.putFloat(pos[1]);
				writer.putFloat(pos[2]);
				
				float[] norm = v.getNormal();
				writer.putFloat(norm[0]);
				writer.putFloat(norm[1]);
				writer.putFloat(norm[2]);
				
				Color c = v.getColour();
				writer.put((byte) c.getRed());
				writer.put((byte) c.getGreen());
				writer.put((byte) c.getBlue());
				writer.put((byte) c.getAlpha());
				
				float[] texcoord = v.getTexcoord1();
				writer.putFloat(texcoord[0]);
				writer.putFloat(texcoord[1]);
			}
		}
		
		@Override
		public PapaVertex[] decode(int vertices, ByteBuffer buf) {
			PapaVertex[] vert = new PapaVertex[vertices];
			for(int i = 0;i<vertices;i++) {
				float[] pos = new float[] {buf.getFloat(), buf.getFloat(),buf.getFloat()};
				float[] normal = new float[] {buf.getFloat(), buf.getFloat(),buf.getFloat()};
				Color c = new Color(buf.get() & 0xff,buf.get() & 0xff,buf.get() & 0xff,buf.get() & 0xff);
				float[] texcoord = new float[] {buf.getFloat(), buf.getFloat()};
				vert[i] = new PapaVertex(pos,normal,c,texcoord);
			}
			return vert;
		}
		
		@Override
		public int calcSize(int vertices) {
			return vertices * 36;
		}
		
		@Override
		public byte formatIndex() {
			return 6;
		}

		@Override
		public boolean testCompatibility(PapaVertex v) {
			return v.normalAvailable() && v.tangentAvailable() && v.texcoord1Available();
		}
	}
	
	private class Position3Normal3Color4TexCoord4 extends VertexBufferConverter {
		
		@Override
		protected void encodeVertices(PapaVertex[] vertices, ByteBuffer writer) {
			for(PapaVertex v : vertices) {
				float[] pos = v.getPosition();
				writer.putFloat(pos[0]);
				writer.putFloat(pos[1]);
				writer.putFloat(pos[2]);
				
				float[] norm = v.getNormal();
				writer.putFloat(norm[0]);
				writer.putFloat(norm[1]);
				writer.putFloat(norm[2]);
				
				Color c = v.getColour();
				writer.put((byte) c.getRed());
				writer.put((byte) c.getGreen());
				writer.put((byte) c.getBlue());
				writer.put((byte) c.getAlpha());
				
				float[] texcoord = v.getTexcoord1();
				writer.putFloat(texcoord[0]);
				writer.putFloat(texcoord[1]);
				
				float[] texcoord2 = v.getTexcoord2();
				writer.putFloat(texcoord2[0]);
				writer.putFloat(texcoord2[1]);
			}
		}
		
		@Override
		public PapaVertex[] decode(int vertices, ByteBuffer buf) {
			PapaVertex[] vert = new PapaVertex[vertices];
			for(int i = 0;i<vertices;i++) {
				float[] pos = new float[] {buf.getFloat(), buf.getFloat(),buf.getFloat()};
				float[] normal = new float[] {buf.getFloat(), buf.getFloat(),buf.getFloat()};
				Color c = new Color(buf.get() & 0xff,buf.get() & 0xff,buf.get() & 0xff,buf.get() & 0xff);
				float[] texcoord1 = new float[] {buf.getFloat(), buf.getFloat()};
				float[] texcoord2 = new float[] {buf.getFloat(), buf.getFloat()};
				vert[i] = new PapaVertex(pos,normal,c,texcoord1, texcoord2);
			}
			return vert;
		}
		
		@Override
		public int calcSize(int vertices) {
			return vertices * 44;
		}
		
		@Override
		public byte formatIndex() {
			return 7;
		}
		
		@Override
		public boolean testCompatibility(PapaVertex v) {
			return v.normalAvailable() && v.tangentAvailable() && v.colourAvailable() && v.texcoord1Available() && v.texcoord2Available();
		}
	}
	
	private class Position3Weights4bBones4bNormal3TexCoord2 extends VertexBufferConverter {
		
		@Override
		protected void encodeVertices(PapaVertex[] vertices, ByteBuffer writer) {
			for(PapaVertex v : vertices) {
				float[] pos = v.getPosition();
				writer.putFloat(pos[0]);
				writer.putFloat(pos[1]);
				writer.putFloat(pos[2]);
				
				byte[] bones = v.getBones();
				writer.put(bones[0]);
				writer.put(bones[1]);
				writer.put(bones[2]);
				writer.put(bones[3]);
				
				byte[] weights = toBytes(v.getWeights());
				writer.put(weights[0]);
				writer.put(weights[1]);
				writer.put(weights[2]);
				writer.put(weights[3]);
				
				float[] normal = v.getNormal();
				writer.putFloat(normal[0]);
				writer.putFloat(normal[1]);
				writer.putFloat(normal[2]);
				
				float[] texcoord = v.getTexcoord1();
				writer.putFloat(texcoord[0]);
				writer.putFloat(texcoord[1]);
			}
		}
		
		private byte[] toBytes(float[] floats) {
			byte[] val = new byte[floats.length];
			for(int i =0;i<floats.length;i++)
				val[i] = (byte) (floats[i] * 255);
			return val;
		}
		
		@Override
		public PapaVertex[] decode(int vertices, ByteBuffer buf) {
			PapaVertex[] vert = new PapaVertex[vertices];
			for(int i = 0;i<vertices;i++) {
				float[] pos = new float[] {buf.getFloat(), buf.getFloat(),buf.getFloat()};
				byte[] bones = new byte[] {buf.get(), buf.get(), buf.get(), buf.get()};
				byte[] weights = new byte[] {buf.get(), buf.get(), buf.get(), buf.get()};
				float[] normal = new float[] {buf.getFloat(), buf.getFloat(),buf.getFloat()};
				float[] texcoord = new float[] {buf.getFloat(), buf.getFloat()};
				vert[i] = new PapaVertex(pos, bones, weights, normal, texcoord);
			}
			return vert;
		}
		
		@Override
		public int calcSize(int vertices) {
			return vertices * 40;
		}
		
		@Override
		public byte formatIndex() {
			return 8;
		}
		
		@Override
		public boolean testCompatibility(PapaVertex v) {
			return v.bonesAvailable() && v.weightsAvailable() && v.normalAvailable() && v.tangentAvailable() && v.texcoord1Available();
		}
	}
	
	
	private class Position3Normal3Tan3Bin3TexCoord4 extends VertexBufferConverter {
		
		@Override
		protected void encodeVertices(PapaVertex[] vertices, ByteBuffer writer) {
			for(PapaVertex v : vertices) {
				float[] pos = v.getPosition();
				writer.putFloat(pos[0]);
				writer.putFloat(pos[1]);
				writer.putFloat(pos[2]);
				
				float[] normal = v.getNormal();
				writer.putFloat(normal[0]);
				writer.putFloat(normal[1]);
				writer.putFloat(normal[2]);
				
				float[] tangent = v.getTangent();
				writer.putFloat(tangent[0]);
				writer.putFloat(tangent[1]);
				writer.putFloat(tangent[2]);
				
				float[] binormal = v.getBinormal();
				writer.putFloat(binormal[0]);
				writer.putFloat(binormal[1]);
				writer.putFloat(binormal[2]);
				
				float[] texcoord1 = v.getTexcoord1();
				writer.putFloat(texcoord1[0]);
				writer.putFloat(texcoord1[1]);
				
				float[] texcoord2 = v.getTexcoord2();
				writer.putFloat(texcoord2[0]);
				writer.putFloat(texcoord2[1]);
			}
		}
		
		@Override
		public PapaVertex[] decode(int vertices, ByteBuffer buf) {
			PapaVertex[] vert = new PapaVertex[vertices];
			for(int i = 0;i<vertices;i++) {
				float[] pos = new float[] {buf.getFloat(), buf.getFloat(),buf.getFloat()};
				float[] normal = new float[] {buf.getFloat(), buf.getFloat(),buf.getFloat()};
				float[] tangent = new float[] {buf.getFloat(), buf.getFloat(),buf.getFloat()};
				float[] binormal = new float[] {buf.getFloat(), buf.getFloat(),buf.getFloat()};
				float[] texcoord1 = new float[] {buf.getFloat(), buf.getFloat()};
				float[] texcoord2 = new float[] {buf.getFloat(), buf.getFloat()};
				vert[i] = new PapaVertex(pos, normal, tangent, binormal, texcoord1, texcoord2);
			}
			return vert;
		}
		
		@Override
		public int calcSize(int vertices) {
			return vertices * 64;
		}
		
		@Override
		public byte formatIndex() {
			return 10;
		}
		
		@Override
		public boolean testCompatibility(PapaVertex v) {
			return v.normalAvailable() && v.tangentAvailable() && v.binormalAvailable() && v.texcoord1Available() && v.texcoord2Available();
		}
	}
	
	public static class PapaVertex {
		private final float[] position;
		private final float[] normal;
		private final float[] binormal;
		private final float[] tangent;
		private final Color colour;
		private final float[] texcoord1;
		private final float[] texcoord2;
		private final byte[] bones;
		private final float[] weights;
		
		public boolean normalAvailable() {
			return normal!=null;
		}
		
		public boolean binormalAvailable() {
			return binormal!=null;
		}
		
		public boolean tangentAvailable() {
			return tangent!=null;
		}
		
		public boolean colourAvailable() {
			return colour!=null;
		}
		
		public boolean texcoord1Available() {
			return texcoord1!=null;
		}
		
		public boolean texcoord2Available() {
			return texcoord2!=null;
		}
		
		public boolean bonesAvailable() {
			return bones!=null;
		}
		
		public boolean weightsAvailable() {
			return weights!=null;
		}

		public float[] getPosition() {
			return position;
		}

		public float[] getNormal() {
			return normal;
		}

		public float[] getBinormal() {
			return binormal;
		}

		public float[] getTangent() {
			return tangent;
		}

		public Color getColour() {
			return colour;
		}

		public float[] getTexcoord1() {
			return texcoord1;
		}

		public float[] getTexcoord2() {
			return texcoord2;
		}

		public byte[] getBones() {
			return bones;
		}

		public float[] getWeights() {
			return weights;
		}

		public PapaVertex(float[] position) {
			this.position = position;
			this.normal = null;
			this.binormal = null;
			this.tangent = null;
			this.colour = null;
			this.texcoord1 = null;
			this.texcoord2 = null;
			this.bones = null;
			this.weights = null;
		}
		
		public PapaVertex(float[] position, float[] normal, Color c, float[] texcoord) {
			this.position = position;
			this.normal = null;
			this.binormal = normal;
			this.tangent = null;
			this.colour = c;
			this.texcoord1 = texcoord;
			this.texcoord2 = null;
			this.bones = null;
			this.weights = null;
		}
		
		public PapaVertex(float[] position, float[] normal, Color c, float[] texcoord1, float[] texcoord2) {
			this.position = position;
			this.normal = normal;
			this.binormal = null;
			this.tangent = null;
			this.colour = c;
			this.texcoord1 = texcoord1;
			this.texcoord2 = texcoord2;
			this.bones = null;
			this.weights = null;
		}
		
		public PapaVertex(float[] position, byte[] bones, byte[] weights, float[] normal, float[] texcoord) {
			this.position = position;
			this.normal = normal;
			this.binormal = null;
			this.tangent = null;
			this.colour = null;
			this.texcoord1 = texcoord;
			this.texcoord2 = null;
			this.bones = bones;
			float[] tWeight = new float[4];
			for(int i = 0;i<weights.length;i++)
				tWeight[i] =(float)(weights[i] & 0xff)/255f;
			this.weights=tWeight;
		}
		
		public PapaVertex(float[] position, float[] normal, float[] tangent, float[] binormal, float[] texcoord1, float[] texcoord2) {
			this.position = position;
			this.normal = normal;
			this.binormal = binormal;
			this.tangent = tangent;
			this.colour = null;
			this.texcoord1 = texcoord1;
			this.texcoord2 = texcoord2;
			this.bones = null;
			this.weights=null;
		}
		
		
	}
	
	@Override
	protected BuildNotification[] validate() {
		return new BuildNotification[0];
	}

	@Override
	protected int headerSize() {
		return 24;
	}

	@Override
	protected int bodySize() {
		return modelConverter.calcSize(vertices.length);
	}

	@Override
	protected void build() {
		byte[] headerBytes = new byte[headerSize()];
		header = ByteBuffer.wrap(headerBytes);
		header.order(ByteOrder.LITTLE_ENDIAN);
		
		super.data = ByteBuffer.wrap(modelConverter.encode(vertices));
		super.data.order(ByteOrder.LITTLE_ENDIAN);
		
		header.put((byte)this.format);
		header.put((byte) 0);
		header.put((byte) 0);
		header.put((byte) 0);
		header.putInt(vertices.length);
		header.putLong((long)super.data.limit());
	}

	@Override
	protected void applyOffset(int offset) {
		header.putLong((long)offset);
	}

	@Override
	protected void overwriteHelper(PapaComponent other) {
		// TODO Auto-generated method stub

	}

	@Override
	protected boolean isDependentOn(PapaComponent other) {
		return false;
	}

	@Override
	public void detach() {
		if(parent==null)
			return;
		parent.removeVertexBuffer(this);
		parent = null;
	}

	@Override
	protected void setParent(PapaFile newParent) {
		parent = newParent;
		newParent.addVertexBuffer(this);
	}

	@Override
	public PapaComponent duplicate() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PapaFile getParent() {
		return this.parent;
	}

	@Override
	public PapaComponent[] getDependencies() {
		return new PapaComponent[0];
	}

	@Override
	public PapaComponent[] getDependents() {
		if(getParent()==null)
			return new PapaComponent[0];
		return getParent().getAllDependentsFor(this);
	}

	@Override
	public void flush() {
		parent = null;
		vertices = null;
		modelConverter = null;
	}

}
