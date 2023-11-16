package papafile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import papafile.PapaFile.BuildNotification;

public class PapaModel extends PapaComponent {
	
	private PapaFile parent;
	private PapaSkeleton skeleton;
	private String name;
	private float[][] modelToScene;
	private ArrayList<PapaMeshBinding> meshBindings = new ArrayList<PapaMeshBinding>();

	public PapaModel(String name, PapaSkeleton skeleton, float[][] modelToScene, PapaMeshBinding[] bindings, PapaFile p) {
		this.parent = p;
		this.skeleton = skeleton;
		this.name = name;
		this.modelToScene = modelToScene;
		for(PapaMeshBinding m : bindings) {
			this.meshBindings.add(m);
			m.setModel(this);
		}
	}
	
	public static class PapaMeshBinding extends PapaSubcomponent{
		private PapaModel model;
		private String name;
		private PapaMesh mesh;
		private float[][] meshToModel;
		private short[] boneMappings;
		
		
		public PapaMeshBinding(String name, PapaMesh mesh, short numBoneMappings, float[][] meshToModel, byte[] data) {
			this.name = name;
			this.mesh = mesh;
			this.meshToModel = meshToModel;
			ByteBuffer buf = ByteBuffer.wrap(data);
			buf.order(ByteOrder.LITTLE_ENDIAN);
			decodeAll(numBoneMappings, buf);
		}
		
		private void setModel(PapaModel m) { // i don't like this.
			this.model = m;
		}
		
		private void decodeAll(short numBoneMappings, ByteBuffer buf) {
			boneMappings = new short[numBoneMappings];
			for(int i =0;i<numBoneMappings;i++)
				boneMappings[i] = buf.getShort();
		}
		
		public short getNumBoneMappings() {
			return (short) boneMappings.length;
		}
		
		public void setBoneMapping(int index, short mapTo) {
			boneMappings[index] = mapTo;
		}

		@Override
		protected BuildNotification[] validate() {
			model.parent.getOrMakeString(name);
			return new BuildNotification[0];
		}

		@Override
		protected int headerSize() {
			return 80;
		}

		@Override
		protected int bodySize() {
			return ceilEight(boneMappings.length * 2);
		}

		@Override
		protected void build() {
			byte[] headerBytes = new byte[headerSize()];
			header = ByteBuffer.wrap(headerBytes);
			header.order(ByteOrder.LITTLE_ENDIAN);
			
			byte[] dataBytes = new byte[bodySize()];
			super.data = ByteBuffer.wrap(dataBytes);
			super.data.order(ByteOrder.LITTLE_ENDIAN);
			for(int i =0;i<boneMappings.length;i++)
				super.data.putShort(boneMappings[i]);
			
			header.putShort((short) model.parent.getOrMakeString(name));
			header.putShort((short) model.parent.getMeshIndex(mesh));
			header.putShort((short) boneMappings.length);
			header.putShort((short) 0);
			for(int y = 0;y<4;y++)
				for(int x =0;x<4;x++)
					header.putFloat(meshToModel[x][y]);
		}

		@Override
		protected void applyOffset(int offset) {
			if(boneMappings.length!=0)
				header.putLong((long)offset);
			else
				header.putLong((long)-1);
			
		}

		@Override
		protected boolean isDependentOn(PapaComponent other) {
			if(other.getClass()==PapaString.class)
				return ((PapaString)other).getValue().equals(name);
			return other == mesh;
		}

		@Override
		public void flush() {
			mesh=null;
			
		}

		@Override
		protected PapaComponent[] getDependencies() {
			int index = model.parent.getOrMakeString(name);
			PapaString s = model.parent.getString(index);
			return new PapaComponent[] {mesh, s};
		}
	}

	@Override
	protected BuildNotification[] validate() {
		ArrayList<BuildNotification> notifications = new ArrayList<BuildNotification>();
		parent.getOrMakeString(name);
		for(PapaMeshBinding m : meshBindings)
			for(BuildNotification n : m.validate())
				notifications.add(n);
		return notifications.toArray(new BuildNotification[notifications.size()]);
	}

	@Override
	protected int headerSize() {
		return 80;
	}

	@Override
	protected int bodySize() {
		int size = 0;
		for(PapaMeshBinding m : meshBindings)
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
		for(PapaMeshBinding m : meshBindings)
			m.build();
		
		header.putShort((short) parent.getOrMakeString(name));
		header.putShort((short) parent.getSkeletonIndex(skeleton));
		header.putShort((short) meshBindings.size());
		header.putShort((short) 0);
		for(int y = 0;y<4;y++)
			for(int x =0;x<4;x++)
				header.putFloat(modelToScene[x][y]);
	}
	
	private void addSubComponents() {
		for(PapaMeshBinding m : meshBindings)
			super.data.put(m.getHeaderBytes());
		for(PapaMeshBinding m : meshBindings)
			super.data.put(m.getDataBytes());
	}

	@Override
	protected void applyOffset(int offset) {
		int localOffset = offset;
		
		if(meshBindings.size()!=0)
			header.putLong(localOffset);
		else
			header.putLong(-1);
		
		for(PapaMeshBinding m : meshBindings) {
			localOffset+=m.headerSize();
		}
		for(PapaMeshBinding m : meshBindings) {
			m.applyOffset(localOffset);
			localOffset+=m.bodySize();
		}
		
		addSubComponents();
	}

	@Override
	protected void overwriteHelper(PapaComponent other) {
		// TODO Auto-generated method stub

	}

	@Override
	protected boolean isDependentOn(PapaComponent other) {
		for(PapaMeshBinding m : meshBindings)
			if(m.isDependentOn(other))
				return true;
		return other == skeleton;
	}

	@Override
	public void detach() {
		if(parent==null)
			return;
		parent.removeModel(this);
		parent = null;
	}

	@Override
	protected void setParent(PapaFile newParent) {
		parent = newParent;
		newParent.addModel(this);
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
		dependencies.add(skeleton);
		
		for(PapaMeshBinding m : meshBindings)
			for(PapaComponent p : m.getDependencies())
				dependencies.add(p);
		
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
		skeleton = null;
		for(PapaMeshBinding m : meshBindings)
			m.flush();
		meshBindings.clear();
		meshBindings=null;
	}

}
