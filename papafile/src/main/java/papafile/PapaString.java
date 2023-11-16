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

import papafile.PapaFile.BuildNotification;

public class PapaString extends PapaComponent{

	private String value;
	private PapaFile papaFile;
	
	private PapaString() {};
	
	public PapaString(String value, PapaFile p) {
		this.value=value;
		this.papaFile=p;
	}
	
	public String getValue() {
		return value;
	}
	
	public void setValue(String value) {
		this.value = value;
	}

	@Override
	protected void build() {
		byte[] headerBytes = new byte[16];
		header = ByteBuffer.wrap(headerBytes);
		header.order(ByteOrder.LITTLE_ENDIAN);
		
		byte[] dataBytes = new byte[bodySize()];
		data = ByteBuffer.wrap(dataBytes);
		data.order(ByteOrder.LITTLE_ENDIAN);
		
		data.put(value.getBytes());
		
		header.putInt(value.length());
		header.putInt(0);
		
	}

	@Override
	protected void applyOffset(int offset) {
		header.putLong((long)offset);
	}

	@Override
	public void flush() {
		papaFile = null;
	}
	
	@Override
	protected BuildNotification[] validate() {
		return new BuildNotification[0];
	}

	@Override
	protected int headerSize() {
		return 16;
	}

	@Override
	protected int bodySize() {
		return ceilNextEight(value.length());
	}
	
	@Override
	protected void overwriteHelper(PapaComponent other) {// TODO
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj == null || !(obj.getClass().equals(PapaString.class)))
			return false;
		if(obj==this)
			return true;
		
		PapaString s = (PapaString) obj;
		return (s.value.equals(	value));
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + value.hashCode();
		return result;
	}

	@Override
	public PapaFile getParent() {
		return papaFile;
	}

	@Override
	public void detach() {
		papaFile = null;
	}

	@Override
	protected void setParent(PapaFile newParent) {
		papaFile = newParent;
	}

	@Override
	public PapaString duplicate() {
		PapaString p = new PapaString();
		p.value = this.value;
		return p;
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
	protected boolean isDependentOn(PapaComponent other) {
		return false;
	}
}
