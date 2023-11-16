package papafile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import papafile.PapaFile.BuildNotification;

public class PapaMesh extends PapaComponent {
	private static String[] primitiveTypes = new String[] {"PRIM_Points","PRIM_Lines","PRIM_Triangles"};
	
	private PapaFile parent;
	private PapaVertexBuffer vBuffer;
	private PapaIndexBuffer iBuffer;
	private ArrayList<PapaMaterialGroup> materialGroups = new ArrayList<PapaMaterialGroup>();
	
	public PapaMesh(PapaVertexBuffer papaVertexBuffer, PapaIndexBuffer papaIndexBuffer, short materialGroups, byte[] data, PapaFile p) {
		this.vBuffer = papaVertexBuffer;
		this.iBuffer = papaIndexBuffer;
		this.parent = p;
		
		ByteBuffer buf = ByteBuffer.wrap(data);
		buf.order(ByteOrder.LITTLE_ENDIAN);
		decodeAll(materialGroups, buf);
	}
	
	private void decodeAll(short materialGroups, ByteBuffer buf) {
		for(int i = 0;i<materialGroups;i++)
			this.materialGroups.add(new PapaMaterialGroup(buf, this));
	}

	public static class PapaMaterialGroup extends PapaSubcomponent{
		private PapaMesh mesh;
		private String name;
		private PapaMaterial material;
		private int firstIndex;
		private int numPrimitives;
		private byte primitiveType;
		
		private PapaMaterialGroup(ByteBuffer buf, PapaMesh mesh) {
			this.mesh = mesh;
			short ind = buf.getShort();
			this.name = ind == -1 ? "" : mesh.parent.getString(ind).getValue();
			this.material = mesh.parent.getMaterial(buf.getShort());
			this.firstIndex = buf.getInt();
			this.numPrimitives = buf.getInt();
			this.primitiveType = buf.get();
			buf.get();
			buf.get();
			buf.get();
		}
		
		public String getPrimitiveType() {
			if(primitiveType>=0 && primitiveType<=2)
				return primitiveTypes[primitiveType];
			else
				return "PrimitiveType("+primitiveType+")";
		}

		@Override
		protected BuildNotification[] validate() {
			mesh.parent.getOrMakeString(name);
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
			super.data.putShort((short) mesh.parent.getOrMakeString(name));
			super.data.putShort((short) mesh.parent.getMaterialIndex(material));
			super.data.putInt(firstIndex);
			super.data.putInt(numPrimitives);
			super.data.put(primitiveType);
		}

		@Override
		protected void applyOffset(int offset) {}

		@Override
		protected boolean isDependentOn(PapaComponent other) {
			if(other.getClass()==PapaString.class)
				return ((PapaString)other).getValue().equals(name);
			return other == material;
		}

		@Override
		public void flush() {
			mesh=null;
			material=null;
		}

		@Override
		protected PapaComponent[] getDependencies() {
			int index = mesh.parent.getOrMakeString(name);
			PapaString s = mesh.parent.getString(index);
			return new PapaComponent[] {material, s};
		}
	}

	@Override
	protected BuildNotification[] validate() {
		ArrayList<BuildNotification> notifications = new ArrayList<BuildNotification>();
		for(PapaMaterialGroup m : materialGroups)
			for(BuildNotification n :m.validate())
				notifications.add(n);
		return notifications.toArray(new BuildNotification[notifications.size()]);
	}

	@Override
	protected int headerSize() {
		return 16;
	}

	@Override
	protected int bodySize() {
		int size = 0;
		for(PapaMaterialGroup m : materialGroups)
			size+=m.componentSize();
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
		for(PapaMaterialGroup m : materialGroups) {
			m.build();
			super.data.put(m.getHeaderBytes());
			super.data.put(m.getDataBytes());
		}
		
		header.putShort((short) parent.getVertexBufferIndex(vBuffer));
		header.putShort((short) parent.getIndexBufferIndex(iBuffer));
		header.putShort((short) materialGroups.size());
		header.putShort((short) 0);
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
		for(PapaMaterialGroup m : materialGroups)
			if(m.isDependentOn(other))
				return true;
		return other == iBuffer || other == vBuffer;
	}

	@Override
	public void detach() {
		if(parent==null)
			return;
		parent.removeMesh(this);
		parent = null;
	}

	@Override
	protected void setParent(PapaFile newParent) {
		parent = newParent;
		newParent.addMesh(this);
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
		ArrayList<PapaComponent> dependencies = new ArrayList<PapaComponent>();
		dependencies.add(vBuffer);
		dependencies.add(iBuffer);
		
		for(PapaMaterialGroup m : materialGroups)
			for(PapaComponent p : m.getDependencies())
				dependencies.add(p);
		
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
		vBuffer = null;
		iBuffer = null;
		for(PapaMaterialGroup m : materialGroups)
			m.flush();
		materialGroups=null;

	}
}
