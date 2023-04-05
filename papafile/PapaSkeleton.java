package papafile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import papafile.PapaFile.BuildNotification;

public class PapaSkeleton extends PapaComponent {
	private PapaFile parent;
	
	private ArrayList<PapaBone> bones = new ArrayList<PapaBone>();
	
	public PapaSkeleton(short numBones, byte[] data, PapaFile p) {
		this.parent = p;
		ByteBuffer buf = ByteBuffer.wrap(data);
		buf.order(ByteOrder.LITTLE_ENDIAN);
		decodeAll(numBones, buf);
	}
	
	private void decodeAll(short numBones, ByteBuffer buf) {
		for(int i = 0;i<numBones;i++) 
			bones.add(new PapaBone(buf, this));
		for(int i = 0;i<numBones;i++)
			bones.get(i).findParentBone();
			
	}
	
	public static class PapaBone extends PapaSubcomponent{
		private PapaSkeleton skeleton;
		private String name;
		private PapaBone parentBone;
		private short parentBoneIndex;
		private float[] location = new float[3];
		private float[] rotation = new float[4];
		private float[][] shearScale = new float[3][3];
		private float[][] bindToBone = new float[4][4];
		
		private PapaBone(ByteBuffer buf, PapaSkeleton skeleton) {
			this.skeleton = skeleton;
			short ind = buf.getShort();
			this.name = skeleton.parent.getString(ind).getValue();
			this.parentBoneIndex = buf.getShort();
			for(int i =0;i<3;i++)
				this.location[i] = buf.getFloat();
			for(int i =0;i<4;i++)
				this.rotation[i] = buf.getFloat();
			for(int y = 0;y<3;y++)
				for(int x = 0;x<3;x++)
					this.shearScale[x][y] = buf.getFloat();
			for(int y = 0;y<4;y++)
				for(int x = 0;x<4;x++)
					this.bindToBone[x][y] = buf.getFloat();
		}
		
		public String getName() {
			return this.name;
		}
		
		public void setName(String name) {
			this.name = name;
		}
		
		private void findParentBone() {
			parentBone = skeleton.getBone(parentBoneIndex);
		}
		
		public boolean hasParent() {
			return parentBone!=null;
		}
		
		public PapaBone getParent() {
			return parentBone;
		}
		
		public void setParent(PapaBone other) {
			parentBone = other;
		}
		
		private PapaSkeleton getSkeleton() {
			return skeleton;
		}
		
		private void setSkeleton(PapaSkeleton skeleton) {
			this.skeleton = skeleton;
		}

		@Override
		protected BuildNotification[] validate() {
			skeleton.parent.getOrMakeString(name);
			return new BuildNotification[0];
		}

		@Override
		protected int headerSize() {
			return 0;
		}

		@Override
		protected int bodySize() {
			return 132;
		}

		@Override
		protected void build() {
			byte[] headerBytes = new byte[headerSize()];
			header = ByteBuffer.wrap(headerBytes);
			header.order(ByteOrder.LITTLE_ENDIAN);
			
			byte[] dataBytes = new byte[bodySize()];
			super.data = ByteBuffer.wrap(dataBytes);
			super.data.order(ByteOrder.LITTLE_ENDIAN);
			super.data.putShort((short) skeleton.parent.getOrMakeString(name));
			super.data.putShort((short) skeleton.bones.indexOf(parentBone));
			for(int i =0;i<3;i++)
				super.data.putFloat(location[i]);
			for(int i =0;i<4;i++)
				super.data.putFloat(rotation[i]);
			for(int y = 0;y<3;y++)
				for(int x = 0;x<3;x++)
					super.data.putFloat(shearScale[x][y]);
			for(int y = 0;y<4;y++)
				for(int x = 0;x<4;x++)
					super.data.putFloat(bindToBone[x][y]);
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
			skeleton = null;
			
		}

		@Override
		protected PapaComponent[] getDependencies() {
			int index = skeleton.parent.getOrMakeString(name);
			return new PapaComponent[] {skeleton.parent.getString(index)};
		}
	}
	
	public PapaBone getBone(int index) {
		if(index==-1)
			return null;
		return bones.get(index);
	}
	
	public void addBone(PapaBone bone) {
		if(bone.getSkeleton()!=null)
			throw new IllegalStateException("Bone "+bone.getName()+" is already attached to a skeleton");
		bones.add(bone);
		bone.setSkeleton(this);
	}
	
	public void removeBone(PapaBone bone) {
		if( ! bones.remove(bone))
			throw new IllegalArgumentException("Bone " + bone.getName() + " is not owned by this skeleton");
		bone.setSkeleton(null);
	}

	@Override
	protected BuildNotification[] validate() {
		ArrayList<BuildNotification> notifications = new ArrayList<BuildNotification>();
		for(PapaBone b : bones)
			for(BuildNotification n : b.validate())
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
		for(PapaBone b : bones)
			size+=b.componentSize();
		return ceilNextEight(size);
	}

	@Override
	protected void build() {
		byte[] headerBytes = new byte[headerSize()];
		header = ByteBuffer.wrap(headerBytes);
		header.order(ByteOrder.LITTLE_ENDIAN);
		
		byte[] bodyBytes = new byte[bodySize()];
		data = ByteBuffer.wrap(bodyBytes);
		data.order(ByteOrder.LITTLE_ENDIAN);
		for(PapaBone b : bones) {
			b.build();
			super.data.put(b.getDataBytes());
		}
		
		header.putShort((short) bones.size());
		header.putShort((short) 0);
		header.putShort((short) 0);
		header.putShort((short) 0);

	}

	@Override
	protected void applyOffset(int offset) {
		header.putLong(offset);
	}

	@Override
	protected void overwriteHelper(PapaComponent other) {
		// TODO Auto-generated method stub

	}

	@Override
	protected boolean isDependentOn(PapaComponent other) {
		for(PapaBone b : bones)
			if(b.isDependentOn(other))
				return true;
		return false;
	}

	@Override
	public void detach() {
		if(parent==null)
			return;
		parent.removeSkeleton(this);
		parent = null;
	}

	@Override
	protected void setParent(PapaFile newParent) {
		parent = newParent;
		newParent.addSkeleton(this);
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
		
		for(PapaBone b : bones)
			for(PapaComponent p : b.getDependencies())
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
		for(PapaBone b : bones)
			b.flush();
		bones.clear();
		bones = null;

	}

}
