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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import papafile.PapaFile.BuildNotification;

public class PapaIndexBuffer extends PapaComponent {
	private PapaFile parent;
	
	private byte format;
	
	private int[] indices;
	
	public String getFormat() {
		return format==0 ? "IF_UInt16" : "IF_UInt32";
	}
	
	private void checkData(ByteBuffer buf, int numVertices, boolean isShort) throws IOException {
		int expectedSize = numVertices * (isShort ? 2 : 4);
		int actualSize = buf.limit();
		if(actualSize != expectedSize)
			throw new IOException("Index buffer data size of "+actualSize+" bytes does not match expected size of " + expectedSize+" bytes");
	}
	
	
	public PapaIndexBuffer(byte format, int indices, byte[] data, PapaFile p) throws IOException{
		this.format = format;
		this.parent = p;
		
		boolean isShort = format==0;
		
		ByteBuffer buf = ByteBuffer.wrap(data);
		buf.order(ByteOrder.LITTLE_ENDIAN);
		
		decodeAll(indices, buf, isShort);
	}
	
	private void decodeAll(int numIndices, ByteBuffer buf, boolean isShort) throws IOException {
		checkData(buf, numIndices, isShort);
		
		this.indices = isShort ? readShorts(buf, numIndices) : readInts(buf, numIndices);
	}
	
	private int[] readShorts(ByteBuffer buf, int numIndices) {
		int[] vals = new int[numIndices];
		for(int i =0;i<numIndices;i++)
			vals[i] = buf.getShort() & 0xffff;
		return vals;
	}
	
	private int[] readInts(ByteBuffer buf, int numIndices) {
		int[] vals = new int[numIndices];
		for(int i =0;i<numIndices;i++)
			vals[i] = buf.getInt();
		return vals;
	}
	
	public int getNumIndices() {
		return indices.length;
	}
	
	public int getIndex(int index) {
		return indices[index];
	}
	
	private void encode(ByteBuffer writer) {
		if(format==0)
			for(int i =0;i<indices.length;i++)
				writer.putShort((short) indices[i]);
		else
			for(int i =0;i<indices.length;i++)
				writer.putInt(indices[i]);
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
		return format==0 ? indices.length * 2 : indices.length*4;
	}

	@Override
	protected void build() {
		byte[] headerBytes = new byte[headerSize()];
		header = ByteBuffer.wrap(headerBytes);
		header.order(ByteOrder.LITTLE_ENDIAN);
		
		super.data = ByteBuffer.wrap(new byte[bodySize()]);
		super.data.order(ByteOrder.LITTLE_ENDIAN);
		encode(super.data);
		
		header.put((byte)this.format);
		header.put((byte) 0);
		header.put((byte) 0);
		header.put((byte) 0);
		header.putInt(indices.length);
		header.putLong((long)super.data.limit());
	}

	@Override
	protected void applyOffset(int offset) {
		header.putLong((long) offset);

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
		parent.removeIndexBuffer(this);
		parent = null;
	}

	@Override
	protected void setParent(PapaFile newParent) {
		// TODO Auto-generated method stub
		this.parent=newParent;
		newParent.addIndexBuffer(this);
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
		this.indices=null;
	}

}
