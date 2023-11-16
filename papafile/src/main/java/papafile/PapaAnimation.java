package papafile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import papafile.PapaFile.BuildNotification;

public class PapaAnimation extends PapaComponent {

	private PapaFile parent;
	private String name;
	private int fpsNumerator;
	private int fpsDenominator;
	private float fps;
	private String[] boneMap;
	private PapaFrame[] frames;
	private int framePosition;
	
	public PapaAnimation(String name, short numBones, int numFrames, int fpsNumerator, int fpsDenominator, byte[] boneData, byte[] transformData, PapaFile p) {
		this.name=name;
		this.parent = p;
		this.fpsNumerator=fpsNumerator;
		this.fpsDenominator=fpsDenominator;
		this.fps = (float)fpsNumerator / (float)fpsDenominator;
		this.frames = new PapaFrame[numFrames];
		
		ByteBuffer boneBuf = ByteBuffer.wrap(boneData);
		boneBuf.order(ByteOrder.LITTLE_ENDIAN);
		
		ByteBuffer transformBuf = ByteBuffer.wrap(transformData);
		transformBuf.order(ByteOrder.LITTLE_ENDIAN);
		
		decodeAll(numBones, numFrames, boneBuf, transformBuf);
	}

	private void decodeAll(short numBones, int numFrames, ByteBuffer boneBuf, ByteBuffer transformBuf) {
		boneMap = decodeBones(numBones, boneBuf);
		for(int f = 0;f<numFrames;f++) {
			PapaAnimationTransform[] transforms = new PapaAnimationTransform[numBones];
			for(int b = 0;b<numBones;b++) {
				transforms[b] = new PapaAnimationTransform(transformBuf);
			}
			this.frames[f] = new PapaFrame(transforms, this);
		}
			
	}

	private String[] decodeBones(short numBones, ByteBuffer boneBuf) {
		String[] bones = new String[numBones];
		for(int i =0;i<numBones;i++)
			bones[i] = parent.getString(boneBuf.getShort()).getValue();
		return bones;
	}
	
	private static class PapaAnimationTransform {
		private float[] location = new float[3];
		private float[] rotation = new float[4];
		
		private PapaAnimationTransform(ByteBuffer buf) {
			for(int i =0;i<3;i++)
				location[i] = buf.getFloat();
			for(int i =0;i<4;i++)
				rotation[i] = buf.getFloat();
		}
		
		public float[] getLocation() {
			return this.location;
		}
		
		public float[] getRotation() {
			return this.rotation;
		}
	}
	
	public static class PapaFrame extends PapaSubcomponent{
		//private PapaAnimation animation;
		private PapaAnimationTransform[] transforms;
		
		private PapaFrame(PapaAnimationTransform[] transforms, PapaAnimation animation) {
			//this.animation = animation;
			this.transforms = transforms;
		}

		@Override
		protected BuildNotification[] validate() {
			return new BuildNotification[0];
		}

		@Override
		protected int headerSize() {
			return 0;
		}

		@Override
		protected int bodySize() {
			return 28*transforms.length;
		}

		@Override
		protected void build() {
			byte[] headerBytes = new byte[headerSize()];
			header = ByteBuffer.wrap(headerBytes);
			header.order(ByteOrder.LITTLE_ENDIAN);
			
			byte[] dataBytes = new byte[bodySize()];
			super.data = ByteBuffer.wrap(dataBytes);
			super.data.order(ByteOrder.LITTLE_ENDIAN);
			for(PapaAnimationTransform t : transforms) {
				for(float f : t.getLocation())
					super.data.putFloat(f);
				for(float f : t.getRotation())
					super.data.putFloat(f);
			}
		}

		@Override
		protected void applyOffset(int offset) {}

		@Override
		protected boolean isDependentOn(PapaComponent other) {
			return false;
		}

		@Override
		protected PapaComponent[] getDependencies() {
			return new PapaComponent[0];
		}

		@Override
		public void flush() {
			//animation = null;
			transforms = null;
		}
	}
	
	public float getFps() {
		return this.fps;
	}
	
	@Override
	protected BuildNotification[] validate() {
		parent.getOrMakeString(name);
		for(String s : boneMap)
			parent.getOrMakeString(s);
		return new BuildNotification[0];

	}

	@Override
	protected int headerSize() {
		return 32;
	}

	@Override
	protected int bodySize() {
		int size = 0;
		for(PapaFrame f : frames)
			size+=f.componentSize();
		return ceilEight(size) + 2 * boneMap.length;
	}

	@Override
	protected void build() {
		byte[] headerBytes = new byte[headerSize()];
		header = ByteBuffer.wrap(headerBytes);
		header.order(ByteOrder.LITTLE_ENDIAN);
		
		byte[] bodyBytes = new byte[bodySize()];
		data = ByteBuffer.wrap(bodyBytes);
		data.order(ByteOrder.LITTLE_ENDIAN);
		
		for(String s : boneMap)
			data.putShort((short) parent.getStringIndex(s));
		
		data.position(ceilEight(data.position()));
		framePosition = data.position();
		
		for(PapaFrame f : frames) {
			f.build();
			data.put(f.getDataBytes());
		}
		
		
		header.putShort((short) parent.getOrMakeString(name));
		header.putShort((short) boneMap.length);
		header.putInt(frames.length);
		header.putInt(fpsNumerator);
		header.putInt(fpsDenominator);
	}

	@Override
	protected void applyOffset(int offset) {
		if(boneMap.length==0)
			header.putLong(-1);
		else
			header.putLong(offset);
		if(frames.length==0)
			header.putLong(-1);
		else
			header.putLong(offset + framePosition);

	}

	@Override
	protected void overwriteHelper(PapaComponent other) {
		// TODO Auto-generated method stub

	}

	@Override
	protected boolean isDependentOn(PapaComponent other) {
		if(other.getClass()==PapaString.class) {
			String val = ((PapaString)other).getValue();
			for(String s : boneMap)
				if(val.equals(s))
					return true;
			return val.equals(name);
		}
		return false;
	}

	@Override
	public void detach() {
		if(parent==null)
			return;
		parent.removeAnimation(this);
		parent = null;
	}

	@Override
	protected void setParent(PapaFile newParent) {
		parent = newParent;
		newParent.addAnimation(this);
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
		
		for(String s : boneMap) {
			int index = parent.getOrMakeString(s);
			PapaString s1 = parent.getString(index);
			dependencies.add(s1);
		}
		
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
		for(PapaFrame f : frames)
			f.flush();
		parent = null;
		frames = null;
		boneMap = null;
	}

}
