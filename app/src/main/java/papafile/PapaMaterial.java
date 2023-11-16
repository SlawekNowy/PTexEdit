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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import papafile.PapaFile.BuildNotification;

public class PapaMaterial extends PapaComponent {
	private PapaFile parent;
	
	private String name;
	
	ArrayList<PapaVectorParameter> vectorParameters = new ArrayList<PapaVectorParameter>();
	ArrayList<PapaTextureParameter> textureParameters = new ArrayList<PapaTextureParameter>();
	ArrayList<PapaMatrixParameter> matrixParameters = new ArrayList<PapaMatrixParameter>();
	
	private int vectorPosition, texturePosition, matrixPosition;
	
	public void setName(String name) {
		this.name=name;
	}
	
	public String getName() {
		return this.name;
	}
	
	public PapaMaterial(String name, short numVectorParams, short numTextureParams, short numMatrixParams, byte[] vectorParams,
							byte[] textureParams, byte[] matrixParams, PapaFile p) {
		this.parent=p;
		this.name = name;
		ByteBuffer b1 = ByteBuffer.wrap(vectorParams);
		b1.order(ByteOrder.LITTLE_ENDIAN);
		ByteBuffer b2 = ByteBuffer.wrap(textureParams);
		b2.order(ByteOrder.LITTLE_ENDIAN);
		ByteBuffer b3 = ByteBuffer.wrap(matrixParams);
		b3.order(ByteOrder.LITTLE_ENDIAN);
		decodeAll(numVectorParams,numTextureParams,numMatrixParams,b1,b2,b3);
		
	}
	
	private void decodeAll(short numVectorParams, short numTextureParams, short numMatrixParams, ByteBuffer b1, ByteBuffer b2, ByteBuffer b3) {
		for(int i =0;i<numVectorParams;i++) { //TODO add size checks
			vectorParameters.add(readVector(b1));
		}
		for(int i =0;i<numTextureParams;i++) {
			textureParameters.add(readTexture(b2));
		}
		for(int i =0;i<numMatrixParams;i++) {
			matrixParameters.add(readMatrix(b3));
		}
	}

	private PapaVectorParameter readVector(ByteBuffer buf) {
		short nameIndex = buf.getShort();
		buf.getShort();
		float[] values = new float[4];
		for(int i =0;i<4;i++)
			values[i] = buf.getFloat();
		return new PapaVectorParameter(parent.getString(nameIndex).getValue(), values, this);
	}
	
	private PapaTextureParameter readTexture(ByteBuffer buf) {
		short nameIndex = buf.getShort();
		PapaTexture tex = parent.getTexture(buf.getShort());
		return new PapaTextureParameter(parent.getString(nameIndex).getValue(), tex, this);
	}
	
	private PapaMatrixParameter readMatrix(ByteBuffer buf) {
		short nameIndex = buf.getShort();
		buf.getShort();
		float[][] values = new float[4][4];
		for(int y =0;y<4;y++)
			for(int x = 0;x<4;x++)
				values[x][y] = buf.getFloat();
		return new PapaMatrixParameter(parent.getString(nameIndex).getValue(), values, this);
	}
	
	public static class PapaVectorParameter extends PapaSubcomponent{
		private PapaMaterial material;
		private float[] values = new float[4];
		private String name;
		
		public PapaVectorParameter(String name, float[] values, PapaMaterial material) {
			this.material= material;
			this.name=name;
			for(int i =0;i<4;i++)
				this.values[i] = values[i];
			
		}
		
		public String getName() {
			return this.name;
		}
		
		public void setName(String newName) {
			this.name= newName;
		}
		
		public float get(int index) {
			return values[index];
		}
		
		public void set(int index, float value) {
			values[index] = value;
		}

		@Override
		protected BuildNotification[] validate() {
			material.parent.getOrMakeString(name);
			return new BuildNotification[0];
		}

		@Override
		protected int headerSize() {
			return 0;
		}

		@Override
		protected int bodySize() {
			return 16;
		}

		@Override
		protected void build() {
			byte[] headerBytes = new byte[headerSize()];
			header = ByteBuffer.wrap(headerBytes);
			header.order(ByteOrder.LITTLE_ENDIAN);
			
			byte[] dataBytes = new byte[bodySize()];
			super.data = ByteBuffer.wrap(dataBytes);
			super.data.order(ByteOrder.LITTLE_ENDIAN);
			super.data.putShort((short) material.parent.getOrMakeString(name));
			super.data.putShort((short) 0);
			for(int i =0;i<4;i++)
				super.data.putFloat(values[i]);
		}

		@Override
		protected void applyOffset(int offset) {}

		@Override
		protected boolean isDependentOn(PapaComponent other) {
			if(other.getClass()==PapaString.class)
				return ((PapaString)other).getValue().equals(name);
			return false;
		}

		@Override
		public void flush() {
			material = null;
			values = null;
			
		}

		@Override
		protected PapaComponent[] getDependencies() {
			int index = material.parent.getOrMakeString(name);
			PapaString s = material.parent.getString(index);
			return new PapaComponent[] {s};
		}
	}
	
	public static class PapaTextureParameter extends PapaSubcomponent{
		private String name;
		private PapaMaterial material;
		private PapaTexture tex;
		
		public PapaTextureParameter(String name, PapaTexture tex, PapaMaterial material) {
			this.name=name;
			this.material=material;
			this.tex = tex;
		}
		
		public String getName() {
			return this.name;
		}
		
		public void setName(String newName) {
			this.name= newName;
		}
		
		public void setTexture(PapaTexture tex) {
			if(tex.getParent()!=material.parent)
				throw new IllegalArgumentException("Texture does not belong to the same PapaFile");
		}
		
		public PapaTexture getTexture() { //TODO: this now has a dependency
			return material.parent.getTexture(tex.getName());
		}
		
		public boolean textureValid() {
			return tex.getParent()==material.parent && material.parent!=null;
		}

		@Override
		protected BuildNotification[] validate() {
			material.parent.getOrMakeString(name);
			if(!textureValid()) {
				try {
					PapaTexture find = material.parent.getTexture(tex.getName());
					tex = find;
				} catch(IllegalArgumentException e) {
					return new BuildNotification[] {new BuildNotification(material, BuildNotification.ERROR, "Texture paramater expects texture named \""+tex.getName()+"\"")};
				}
			}
			return new BuildNotification[0];
		}

		@Override
		protected int headerSize() {
			return 0;
		}

		@Override
		protected int bodySize() {
			return 4;
		}

		@Override
		protected void build() {
			byte[] headerBytes = new byte[headerSize()];
			header = ByteBuffer.wrap(headerBytes);
			header.order(ByteOrder.LITTLE_ENDIAN);
			
			byte[] dataBytes = new byte[bodySize()];
			super.data = ByteBuffer.wrap(dataBytes);
			super.data.order(ByteOrder.LITTLE_ENDIAN);
			super.data.putShort((short) material.parent.getOrMakeString(name));
			super.data.putShort((short) material.parent.getTextureIndex(tex));
		}

		@Override
		protected void applyOffset(int offset) {}

		@Override
		protected boolean isDependentOn(PapaComponent other) {
			if(other.getClass()==PapaString.class)
				return ((PapaString)other).getValue().equals(name);
			return other == tex;
		}

		@Override
		public void flush() {
			material = null;
			tex = null;
			
		}

		@Override
		protected PapaComponent[] getDependencies() {
			int index = material.parent.getOrMakeString(name);
			PapaString s = material.parent.getString(index);
			return new PapaComponent[] {tex, s};
		}
	}
	
	public static class PapaMatrixParameter extends PapaSubcomponent{
		private PapaMaterial material;
		private float[][] values = new float[4][4];
		private String name;
		
		public PapaMatrixParameter(String name, float[][] values, PapaMaterial material) {
			this.material = material;
			this.name=name;
			for(int y =0;y<4;y++)
				for(int x = 0;x<4;x++)
					this.values[x][y] = values[x][y];
		}
		
		public String getName() {
			return this.name;
		}
		
		public void setName(String newName) {
			this.name= newName;
		}
		
		public float get(int x, int y) {
			return values[x][y];
		}
		
		public void set(int x, int y, float value) {
			values[x][y] = value;
		}

		@Override
		protected BuildNotification[] validate() {
			material.parent.getOrMakeString(name);
			return new BuildNotification[0];
		}

		@Override
		protected int headerSize() {
			return 0;
		}

		@Override
		protected int bodySize() {
			return 68;
		}

		@Override
		protected void build() {
			byte[] headerBytes = new byte[headerSize()];
			header = ByteBuffer.wrap(headerBytes);
			header.order(ByteOrder.LITTLE_ENDIAN);
			
			byte[] dataBytes = new byte[bodySize()];
			super.data = ByteBuffer.wrap(dataBytes);
			super.data.order(ByteOrder.LITTLE_ENDIAN);
			super.data.putShort((short) material.parent.getOrMakeString(name));
			super.data.putShort((short) 0);
			for(int y = 0;y<4;y++)
				for(int x =0;x<4;x++)
					super.data.putFloat(values[x][y]);
		}

		@Override
		protected void applyOffset(int offset) {}

		@Override
		protected boolean isDependentOn(PapaComponent other) {
			if(other.getClass()==PapaString.class)
				return ((PapaString)other).getValue().equals(name);
			return false;
		}

		@Override
		public void flush() {
			material = null;
			values = null;
			
		}

		@Override
		protected PapaComponent[] getDependencies() {
			int index = material.parent.getOrMakeString(name);
			PapaString s = material.parent.getString(index);
			return new PapaComponent[] {s};
		}
	}
	
	@Override
	protected BuildNotification[] validate() {
		ArrayList<BuildNotification> notifications = new ArrayList<BuildNotification>();
		parent.getOrMakeString(name);
		for(PapaVectorParameter p : vectorParameters)
			for(BuildNotification n : p.validate())
				notifications.add(n);
		for(PapaTextureParameter p : textureParameters)
			for(BuildNotification n :p.validate())
				notifications.add(n);
		for(PapaMatrixParameter p : matrixParameters)
			for(BuildNotification n : p.validate())
				notifications.add(n);
		return notifications.toArray(new BuildNotification[notifications.size()]);

	}

	@Override
	protected int headerSize() {
		return 32;
	}

	@Override
	protected int bodySize() {
		int size = 0;
		for(PapaVectorParameter p : vectorParameters)
			size+=p.componentSize();
		size = ceilEight(size);
		for(PapaTextureParameter p : textureParameters)
			size+=p.componentSize();
		size = ceilEight(size);
		for(PapaMatrixParameter p : matrixParameters)
			size+=p.componentSize();
		size = ceilEight(size);
		return size;
	}

	@Override
	protected void build() {
		byte[] headerBytes = new byte[headerSize()];
		header = ByteBuffer.wrap(headerBytes);
		header.order(ByteOrder.LITTLE_ENDIAN);
		
		byte[] bodyBytes = new byte[bodySize()];
		data = ByteBuffer.wrap(bodyBytes);
		data.order(ByteOrder.LITTLE_ENDIAN);
		
		vectorPosition = 0;
		for(PapaVectorParameter p : vectorParameters) {
			p.build();
			data.put(p.getDataBytes());
		}
		data.position(ceilEight(data.position()));
		texturePosition = data.position();
		for(PapaTextureParameter p : textureParameters) {
			p.build();
			data.put(p.getDataBytes());
		}
		data.position(ceilEight(data.position()));
		matrixPosition = data.position();
		for(PapaMatrixParameter p : matrixParameters) {
			p.build();
			data.put(p.getDataBytes());
		}
		
		header.putShort((short) parent.getOrMakeString(name));
		header.putShort((short) vectorParameters.size());
		header.putShort((short) textureParameters.size());
		header.putShort((short) matrixParameters.size());
	}

	@Override
	protected void applyOffset(int offset) {
		if(vectorParameters.size()==0)
			header.putLong(-1);
		else
			header.putLong(offset + vectorPosition);
		
		if(textureParameters.size()==0)
			header.putLong(-1);
		else
			header.putLong(offset + texturePosition);
		
		if(matrixParameters.size()==0)
			header.putLong(-1);
		else
			header.putLong(offset + matrixPosition);

	}

	@Override
	protected void overwriteHelper(PapaComponent other) {
		// TODO Auto-generated method stub

	}

	@Override
	protected boolean isDependentOn(PapaComponent other) {
		for(PapaVectorParameter p : vectorParameters)
			if(p.isDependentOn(other))
				return true;
		for(PapaTextureParameter p : textureParameters)
			if(p.isDependentOn(other))
				return true;
		for(PapaMatrixParameter p : matrixParameters)
			if(p.isDependentOn(other))
				return true;
		if(other.getClass()==PapaString.class)
			return ((PapaString)other).getValue().equals(name);
		return false;
	}

	@Override
	public void detach() {
		if(parent==null)
			return;
		parent.removeMaterial(this);
		parent = null;
	}

	@Override
	protected void setParent(PapaFile newParent) {
		parent = newParent;
		newParent.addMaterial(this);
	}

	@Override
	public PapaComponent duplicate() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PapaFile getParent() {
		return parent;
	}

	@Override
	public PapaComponent[] getDependencies() {
		ArrayList<PapaComponent> dependencies = new ArrayList<PapaComponent>();
		
		for(PapaVectorParameter p : vectorParameters)
			for(PapaComponent c : p.getDependencies())
				dependencies.add(c);
		for(PapaTextureParameter p : textureParameters)
			for(PapaComponent c : p.getDependencies())
				dependencies.add(c);
		for(PapaMatrixParameter p : matrixParameters)
			for(PapaComponent c : p.getDependencies())
				dependencies.add(c);
		
		int index = parent.getOrMakeString(name);
		PapaString s = parent.getString(index);
		dependencies.add(s);
		
		return dependencies.toArray(new PapaComponent[dependencies.size()]);
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
		for(PapaVectorParameter p : vectorParameters)
			p.flush();
		for(PapaTextureParameter p : textureParameters)
			p.flush();
		for(PapaMatrixParameter p : matrixParameters)
			p.flush();
		vectorParameters.clear();
		textureParameters.clear();
		matrixParameters.clear();
		vectorParameters = null;
		textureParameters = null;
		matrixParameters = null;
	}

}
