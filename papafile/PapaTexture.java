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

import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.regex.Pattern;

import com.github.memo33.jsquish.Squish;
import com.github.memo33.jsquish.Squish.CompressionMethod;
import com.github.memo33.jsquish.Squish.CompressionType;

import papafile.PapaFile.BuildNotification;

/**
 * A container class to hold Texture objects. Each PapaTexture has a main texture, and possibly some MipMap textures.
 *
 */
public class PapaTexture extends PapaComponent{
	// papadump suggests the first 3 formats have a different name, but is incorrect.
	// despite listing all these textures, PA will only actually support: R8G8B8A8, R8G8B8X8, B8G8R8A8, DXT1, DXT5, and R8
	private static final String[] formats = {	"R8G8B8A8", "R8G8B8X8", "B8G8R8A8", "DXT1", "DXT3", "DXT5", "R32F", "RG32F", "RGBA32F", 
												"R16F", "RG16F", "RGBA16F", "R8", "R8G8",  "D0", "D16", "D24", "D24S8", "D32", "R8I", "R8UI", 
												"R16I", "R16UI", "RG8I", "RG8UI", "RG16I", "RG16UI", "R32I", "R32UI", "Shadow16", "Shadow24", "Shadow32"};
	
	
	private String name;
	private byte format;
	private byte mips;
	private boolean srgb;
	private short width;
	private short height;
	private int numImages;
	private PapaFile parent;
	private PapaFile linkedFile;
	private boolean isLinked;
	private byte[] data = new byte[0];
	
	private BufferedImage [] textures, red, green, blue, alpha, luminance;
	
	private TextureConverter textureConverter;
	
	public int getNumImages() {
		checkLinked(false);
		return numImages;
	}
	
	public BufferedImage getMip(int i) {
		checkLinked(false);
		return textures[i+1];
	}
	
	public BufferedImage getImage() {
		checkLinked(false);
		return textures[0];
	}
	
	public BufferedImage getImage(int index) { // skips over mip vs image check.
		checkLinked(false);
		return textures[index];
	}
	
	public BufferedImage asRed(int index) {
		checkLinked(false);
		if(red[index]==null)
			red[index] = textureConverter.asRed(textures[index]);
		return red[index];
	}
	
	public BufferedImage asGreen(int index) {
		checkLinked(false);
		if(green[index]==null)
			green[index] = textureConverter.asGreen(textures[index]);
		return green[index];
	}
	
	public BufferedImage asBlue(int index) {
		checkLinked(false);
		if(blue[index]==null)
			blue[index] = textureConverter.asBlue(textures[index]);
		return blue[index];
	}

	public BufferedImage asAlpha(int index) {
		checkLinked(false);
		if(alpha[index]==null)
			alpha[index] = textureConverter.asAlpha(textures[index]);
		return alpha[index];
	}
	
	public BufferedImage asLuminance(int index) {
		checkLinked(false);
		if(luminance[index]==null)
			luminance[index] = textureConverter.asLuminance(textures[index]);
		return luminance[index];
	}
	
	public boolean supportsAlpha() {
		checkLinked(false);
		return textureConverter.supportsAlpha();
	}
	
	public String getFormat() {
		checkLinked(false);
		return formats[format - 1]; // format for texture indexed starting at 1
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	/*public void adjustLinkedTextureName(String newName) { TODO: remove if not necessary (might've become obsolete)
		checkLinked(true);
		parent.updateLinkedFile(getLinkedTexture().getParent(), newName);
		this.name = newName;
	}*/
	
	public byte getMips() {
		checkLinked(false);
		return mips;
	}

	public boolean getSRGB() {
		checkLinked(false);
		return srgb;
	}
	
	public void setSRGB(boolean srgb) {
		checkLinked(false);
		this.srgb = srgb;
	}

	public short getWidth() {
		checkLinked(false);
		return width;
	}
	
	public short getHeight() {
		checkLinked(false);
		return height;
	}
	
	public int getWidth(int mipLevel) {
		checkLinked(false);
		return textures[mipLevel].getWidth();
	}
	
	public int getHeight(int mipLevel) {
		checkLinked(false);
		return textures[mipLevel].getHeight();
	}
	
	public PapaFile getParent() {
		return this.parent;
	}
	
	public boolean isEqualOrLinked(PapaTexture other) {
		if(other==null)
			return false;
		if(this.equals(other))
			return true;
		if(isLinked) {
			PapaTexture tex = getLinkedTexture();
			if(other.equals(tex))
				return true;
		}
		return false;
	}
	
	public PapaTexture getLinkedTexture() {
		checkLinked(true);
		PapaFile p = parent.getLinkedFile(name);
		linkedFile = p;
		if(linkedFile.getNumTextures()!=1)
			throw new IllegalStateException("Linked PapaFile "+linkedFile+" does not contain exactly 1 texture. This is unknown behaviour!");
		return linkedFile.getTexture(0);
	}
	
	public boolean linkValid() {
		checkLinked(true);
		return parent.containsLinkedFile(name);
	}
	
	private BufferedImage scaleImage(BufferedImage input, int width, int height, Object interpolation) {
		BufferedImage out = new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
		Graphics g = out.getGraphics();
		Graphics2D g2d = (Graphics2D)g;
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
		g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
		g2d.drawImage(input, 0, 0, out.getWidth(), out.getHeight(), 0, 0, input.getWidth(), input.getHeight(), null);
		return out;
	}
	
	private void checkLinked(boolean state) {
		if(isLinked != state)
			throw new IllegalStateException(isLinked ? "PapaTexture is linked." : "PapaTexture is not linked.");
	}
	
	public boolean isLinked() {
		return isLinked;
	}
	
	public PapaTexture(String linkedFilePath, PapaFile p) {
		this.parent=p;
		this.isLinked = true;
		this.name = linkedFilePath;
		if(p!=null)
			getLinkedTexture(); // calculate linked file variable
	}
	
	public PapaTexture(String name, byte format, byte mips, boolean srgb, short width, short height, byte[] data, PapaFile p) throws IOException {
		this.name = name;
		this.format = format;
		this.mips = (byte) (mips - 1); // mips appear to include the texture itself.
		this.data = data;
		createTextureArrays(mips);
		this.numImages = mips;
		this.srgb = srgb;
		this.width = width;
		this.height = height;
		this.parent = p;
		
		ByteBuffer buf = ByteBuffer.wrap(data);
		buf.order(ByteOrder.LITTLE_ENDIAN);
		try {
			textureConverter = getInstance(getFormat());
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new IOException("Invalid name index");
		}
		
		decodeAll(new TextureInfo(this.mips,this.width,this.height), buf, textureConverter);
		
		System.out.println("Loaded "+this.width+" by "+this.height+" image named "+this.name+" of format "+getFormat()+" with "+this.mips+" mipmaps. Data size: "+data.length+".");
	}
	
	public PapaTexture(BufferedImage input, ImmutableTextureSettings settings, PapaFile p, String name) throws IOException {
		this.parent = p;
		textureConverter = getInstance(settings.format);
		if(textureConverter instanceof DXT)
			((DXT) textureConverter).setCompressionMethod(settings.method);
		generateTexture(input,textureConverter, settings, name);
		if(Pattern.compile(settings.srgbTexname).matcher(name.toLowerCase()).find()) {
			this.setSRGB(true);
		}
		
	}
	
	public PapaTexture(BufferedImage input, ImmutableTextureSettings settings, PapaFile p) throws IOException {
		this(input,settings,p,p.getFileName());
	}
	
	private void generateTexture(BufferedImage input, TextureConverter textureConverter, ImmutableTextureSettings settings, String name) throws IOException {
		
		BufferedImage in = input;
		int width = in.getWidth();
		int height = in.getHeight();
		int mipCount = 0;
		if(settings.resize) {
			/*if(!settings.resize)
				throw new IOException("Texture dimensions are not powers of two.\n" 
					+ (!widthPOT ? "Width = "+width+" (nearest powers are "+toPowerOfTwo(width, 2)+", "+toPowerOfTwo(width, 1)+")\n" :"")
					+ (!heightPOT ? "Height = "+height+" (nearest powers are "+toPowerOfTwo(height, 2)+", "+toPowerOfTwo(height, 1)+")\n" :""));*/
			width = resizeInputValue(width, settings.resizeMode);
			height = resizeInputValue(height, settings.resizeMode);
			in = scaleImage(input, width, height, getScaleRenderingHint(settings.resizeMethod));
		}
		
		if(settings.generateMipmaps) {
			int max = Math.max(width, height);
			while(max>2) {
				max/=2;
				mipCount++;
			}
		}
		
		BufferedImage[] images = new BufferedImage[mipCount + 1];
		images[0]=in;
		for(int i = 1;i<=mipCount;i++) {
			int mipWidth = (int) Math.max(width / Math.pow(2, i), 1);
			int mipHeight = (int) Math.max(height / Math.pow(2, i), 1);
			images[i] = scaleImage(images[i-1], mipWidth, mipHeight, getScaleRenderingHint(settings.mipmapResizeMethod));
		}
		
		createTextureArrays(mipCount + 1);
		
		this.data = textureConverter.encode(images);
		
		this.name = name;
		this.format = textureConverter.formatIndex();
		this.mips = (byte) mipCount;
		this.numImages = mips + 1;
		this.srgb = settings.SRGB;
		this.width = (short) width;
		this.height = (short) height;
		
		ByteBuffer buf = ByteBuffer.wrap(data);
		buf.order(ByteOrder.LITTLE_ENDIAN);
		decodeAll(new TextureInfo(mipCount, width, height),buf,textureConverter);
	}
	
	private int resizeInputValue(int value, int roundMode) {
		double val = value;
		int count = 0;
		while(val>1) {
			val/=2;
			count++;
		}
		switch(roundMode) {
			case TextureSettings.RESIZE_DOWN:
				return (int) Math.pow(2, count - 1);
			case TextureSettings.RESIZE_UP:
				return (int) Math.pow(2, count);
			case TextureSettings.RESIZE_NEAREST:
				if(val >=0.75d)
					return (int) Math.pow(2, count);
				return (int) Math.pow(2, count - 1);
			default:
				return roundMode; // assumed the roundMode is the wanted size
		}
	}
	
	private Object getScaleRenderingHint(int mode) {
		if(mode == TextureSettings.RESIZE_TYPE_BICUIBIC)
			return RenderingHints.VALUE_INTERPOLATION_BICUBIC;
		if(mode == TextureSettings.RESIZE_TYPE_BILEANR)
			return RenderingHints.VALUE_INTERPOLATION_BILINEAR;
		if(mode == TextureSettings.RESIZE_TYPE_NEAREST_NEIGHBOUR)
			return RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
		throw new IllegalArgumentException("Invalid resize mode paramater "+mode);
	}

	private void createTextureArrays(int amount) {
		this.textures = new BufferedImage[amount];
		this.red = new BufferedImage[amount];
		this.green = new BufferedImage[amount];
		this.blue = new BufferedImage[amount];
		this.alpha = new BufferedImage[amount];
		this.luminance = new BufferedImage[amount];
	}
	
	private TextureConverter getInstance(String format) throws IOException {
		switch(format) {
			case TextureSettings.R8G8B8A8:
				return new R8G8B8A8();
			case TextureSettings.R8G8B8X8:
				return new R8G8B8X8();
			case TextureSettings.B8G8R8A8:
				return new B8G8R8A8();
			case TextureSettings.DXT1:          
				return new DXT1();           
			case TextureSettings.DXT5:          
				return new DXT5(); 
			case TextureSettings.R8:
				return new R8();
			case TextureSettings.DXT_AUTO:
				return new DXTAuto();
			default:
				throw new IOException("Unsupported format: "+format);
		}
	}
	
	private void decodeAll(TextureInfo info, ByteBuffer buf, TextureConverter converter) throws IOException {
		
		checkData(info,buf,converter);
		
		this.textures[0] = converter.decode(buf, info);
		for(int i=1;i<=info.mips;i++) {
			
			int mipScale = (int) Math.pow(2, i);
			int width = Math.max(info.width / mipScale,1);
			int height = Math.max(info.height / mipScale,1);
			this.textures[i] = converter.decode(buf, new TextureInfo(info.mips,width,height));
		}
	}
	
	private void checkData(TextureInfo info, ByteBuffer buf, TextureConverter converter) throws IOException {
		int expectedSize = converter.calcSize(info.width, info.height, info.mips);
		int actualSize = buf.limit();
		if(actualSize != expectedSize) {
			throw new IOException("Image data size of "+actualSize+" bytes does not match expected size of " + expectedSize+" bytes");
		}
	}
	

	@Override
	protected void build() {
		byte[] headerBytes = new byte[headerSize()];
		header = ByteBuffer.wrap(headerBytes);
		header.order(ByteOrder.LITTLE_ENDIAN);
		
		super.data = ByteBuffer.wrap(this.data);
		super.data.order(ByteOrder.LITTLE_ENDIAN);
		
		int nameIndex = parent.getOrMakeString(this.name);
		header.putShort((short)nameIndex);
		header.put((byte)this.format);
		header.put((byte)(((this.mips + (isLinked() ? 0 : 1)) & 0b0111_1111) | (srgb ? 0b1000_0000 : 0)));
		header.putShort((short)this.width);
		header.putShort((short)this.height);
		header.putLong((long)super.data.limit());
	}

	@Override
	protected void applyOffset(int offset) {
		if(isLinked)
			header.putLong(-1l);
		else
			header.putLong((long)offset);
	}

	@Override
	public void flush() {
		parent = linkedFile = null;
		textures = red = green = blue = alpha = null;
		textureConverter = null;
		data = null;
	}

	@Override
	protected BuildNotification[] validate() {
		parent.getOrMakeString(this.name);
		if(isLinked && !linkValid())
			return new BuildNotification[] {new BuildNotification(this, BuildNotification.WARNING, "Linked file \""+getName()+"\" not found in parent")};
		return new BuildNotification[0];
	}

	@Override
	protected int headerSize() {
		return 24;
	}

	@Override
	protected int bodySize() {
		if(isLinked)
			return 0;
		return ceilEight(this.data.length);
	}
	
	private class TextureInfo {
		
		public final int mips;
		public final int width;
		public final int height;
		
		public TextureInfo(int mips, int width, int height) {
			this.mips=mips;
			this.width=width;
			this.height=height;
		}
	}
	
	private abstract class TextureConverter {
		
		public abstract BufferedImage decode(ByteBuffer buf, TextureInfo info);
		
		public abstract int calcSize(int width, int height, int mips);
		
		public abstract byte formatIndex();
		
		protected abstract void encodeImage(BufferedImage input, ByteBuffer writer);
		
		public byte[] encode(BufferedImage[] input) {
			int images = input.length;
			int width = input[0].getWidth();
			int height = input[0].getHeight();
			byte[] buf = new byte[calcSize(width,height,images - 1)];
			ByteBuffer b = ByteBuffer.wrap(buf);
			b.order(ByteOrder.LITTLE_ENDIAN);
			for(int i=0;i<images;i++)
				encodeImage(input[i],b);
			return buf;
		}
		
		public boolean supportsAlpha() {
			return true;
		}
		
		public BufferedImage asLuminance(BufferedImage image) {
			return calcLuminance(image);
		}
		
		public BufferedImage asRed(BufferedImage image) {
			return filterImage(image, 0b11111111_11111111_00000000_00000000);
		}
		
		public BufferedImage asGreen(BufferedImage image) {
			return filterImage(image, 0b11111111_00000000_11111111_00000000);
		}
		
		public BufferedImage asBlue(BufferedImage image) {
			return filterImage(image, 0b11111111_00000000_00000000_11111111);
		}
		
		public BufferedImage asAlpha(BufferedImage image) {
			return calcAlpha(image);
		}
		
		protected BufferedImage calcLuminance(BufferedImage image) {
			BufferedImage out = new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_INT_ARGB);
			for(int y= 0;y<image.getHeight();y++)
				for(int x =0;x<image.getWidth();x++) {
					int value = image.getRGB(x, y);
					int average = (int) ((float)(value & 0b00000000_00000000_11111111) 			* 0.082f 	//Blue
										+ (float)((value & 0b00000000_11111111_00000000)>>>8) 	* 0.6094f 	//Green
										+ (float)((value & 0b11111111_00000000_00000000)>>>16) 	* 0.3086f);	//Red
					int RGB = average | (average<<8) | (average<<16);
					out.setRGB(x, y, RGB  | (value & 0b11111111_00000000_00000000_00000000));
				}
			return out;
		}
		
		protected BufferedImage calcAlpha(BufferedImage image) {
			BufferedImage out = new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_INT_ARGB);
			for(int y= 0;y<image.getHeight();y++)
				for(int x =0;x<image.getWidth();x++) {
					int alphaBits = (image.getRGB(x, y) & 0b11111111_00000000_00000000_00000000)>>>24;
					alphaBits |= alphaBits<<8 | alphaBits<<16 | 0b11111111_00000000_00000000_00000000;
					out.setRGB(x, y, alphaBits);
				}
			return out;
		}
		
		protected BufferedImage filterImage(BufferedImage input, int filterBits) {
			BufferedImage out = new BufferedImage(input.getWidth(),input.getHeight(),BufferedImage.TYPE_INT_ARGB);
			for(int y= 0;y<input.getHeight();y++)
				for(int x =0;x<input.getWidth();x++)
					out.setRGB(x, y, input.getRGB(x, y) & filterBits);
			return out;
		}
	}
	
	private class R8G8B8A8 extends TextureConverter {

		@Override
		public BufferedImage decode(ByteBuffer buf, TextureInfo info) {
			int width = info.width;
			int height = info.height;
			int length = width*height;
			
			BufferedImage b = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			
			int[] array = new int[width*height];
			for(int i = 0;i<length;i++) {
				int tmp=0;
				tmp |=(buf.get() & 0b11111111)<<16;
				tmp |=(buf.get() & 0b11111111)<<8;
				tmp |=(buf.get() & 0b11111111);
				tmp |=(buf.get() & 0b11111111)<<24;
				array[i] = tmp;
			}
			b.setRGB(0, 0, width, height, array, 0, width);
			return b;
		}
		
		@Override
		protected void encodeImage(BufferedImage input, ByteBuffer writer) {
			int width = input.getWidth();
			int height = input.getHeight();
			int[] rgbArray = input.getRGB(0, 0, width, height, null, 0, input.getWidth());
			for(int i : rgbArray) {// ARGB -> RGBA
				writer.put((byte)(i>>>16));
				writer.put((byte)(i>>>8));
				writer.put((byte)(i));
				writer.put((byte)(i>>>24));
			}
		}
		
		@Override
		public int calcSize(int width, int height, int mips) {
			int size = 0;
			
			for(int i=0;i<mips + 1;i++) {
				int mipScale = (int) Math.pow(2, i);
				int w = Math.max(width / mipScale,1);
				int h = Math.max(height / mipScale,1);
				size+= w*h*4;
			}
			
			return size;
		}

		@Override
		public byte formatIndex() {
			return 1;
		}
	}
	
	private class R8G8B8X8 extends R8G8B8A8 {
		
		@Override
		protected void encodeImage(BufferedImage input, ByteBuffer writer) {
			int width = input.getWidth();
			int height = input.getHeight();
			int[] rgbArray = input.getRGB(0, 0, width, height, null, 0, input.getWidth());
			for(int i : rgbArray) {// ARGB -> RRGB
				writer.put((byte)(i>>>16));
				writer.put((byte)(i>>>8));
				writer.put((byte)(i));
				writer.put((byte)0xff);
			}
		}
		
		@Override
		public boolean supportsAlpha() {
			return false;
		}
		
		@Override
		public byte formatIndex() {
			return 2;
		}
		
	}
	
	private class B8G8R8A8 extends R8G8B8A8 {

		@Override
		public BufferedImage decode(ByteBuffer buf, TextureInfo info) {
			int width = info.width;
			int height = info.height;
			int length = width*height;
			
			BufferedImage b = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			
			int[] array = new int[width*height];
			for(int i = 0;i<length;i++) {
				array[i] = buf.getInt();
			}
			
			b.setRGB(0, 0, width, height, array, 0, width);
			return b;
			
		}
		
		@Override
		protected void encodeImage(BufferedImage input, ByteBuffer writer) {
			int width = input.getWidth();
			int height = input.getHeight();
			int[] rgbArray = input.getRGB(0, 0, width, height, null, 0, input.getWidth());
			for(int i : rgbArray) {// ARGB -> BGRA
				writer.put((byte)i);
				writer.put((byte)(i>>>8));
				writer.put((byte)(i>>>16));
				writer.put((byte)(i>>>24));
			}
		}
		@Override
		public byte formatIndex() {
			return 3;
		}
		
	}
	
	// https://www.fsdeveloper.com/wiki/index.php?title=DXT_compression_explained
	// https://docs.microsoft.com/en-us/windows/win32/direct3d9/opaque-and-1-bit-alpha-textures
	// https://en.wikipedia.org/wiki/S3_Texture_Compression#DXT1
	
	private abstract class DXT extends TextureConverter {
		
		protected int chunkByteSize = 8;
		
		protected CompressionMethod method = CompressionMethod.CLUSTER_FIT;
		
		public void setCompressionMethod(CompressionMethod method) {
			this.method = method;
		}
		
		protected byte[] imageToByteArray(BufferedImage input) {
			int width = input.getWidth();
			int height = input.getHeight();
			int size = input.getWidth() * input.getHeight();
			byte[] ret = new byte[size * 4];
			for(int y=0;y<height;y++)
				for(int x=0;x<width;x++) {
					int data = input.getRGB(x, y);
					for(int i =3;i>=0;i--)
						ret[4*(x+y*width) + i] = (byte)(data>>>i*8);
				}
			return ret;
		}
		
		public Color[] decodeColourMap(byte[] data) {
			Color[] colours = new Color[4];
			int colour0 = ((data[0]&0b11111111) | data[1]<<8) & 0b11111111_11111111;
			int colour1 = ((data[2]&0b11111111) | data[3]<<8) & 0b11111111_11111111;
			colours[0] = new Color(((colour0>>>11 & 0b00011111)*8), (((colour0>>>5) & 0b00111111)*4), (((colour0) & 0b00011111)*8));
			colours[1] = new Color(((colour1>>>11 & 0b00011111)*8), (((colour1>>>5) & 0b00111111)*4), (((colour1) & 0b00011111)*8));
			if(colour0>colour1) {
				colours[2] = new Color(	(float)(2 * colours[0].getRed() + 		colours[1].getRed()) / 765f, // 765 = 3*255
										(float)(2 * colours[0].getGreen() + 	colours[1].getGreen()) / 765f,
										(float)(2 * colours[0].getBlue() + 		colours[1].getBlue()) / 765f);
				colours[3] = new Color(	(float)(colours[0].getRed() + 		2 * colours[1].getRed()) / 765f,
										(float)(colours[0].getGreen() + 	2 * colours[1].getGreen()) / 765f,
										(float)(colours[0].getBlue() + 		2 * colours[1].getBlue()) / 765f);
			} else {
				colours[2] = new Color(	(float)(colours[0].getRed() + 		colours[1].getRed()) / 510f, // 5 = 2*255
										(float)(colours[0].getGreen() + 	colours[1].getGreen()) / 510f,
										(float)(colours[0].getBlue() + 		colours[1].getBlue()) / 510f);
				colours[3] = new Color(0f, 0f, 0f);
				
			}
			return colours;
		}
		
		public abstract int[] decodeAlphaMap(byte[] bytes);
		
		@Override
		public int calcSize(int width, int height, int mips) {
			int size = 0;

			for(int i=0;i<mips + 1;i++) {
				int mipScale = (int) Math.pow(2, i);
				int w = (int) Math.ceil(Math.max(width / mipScale, 1) / 4d);
				int h = (int) Math.ceil(Math.max(height / mipScale, 1) / 4d);
				size+= w*h*chunkByteSize;
			}
			
			return size;
		}
		
		
	}
	
	private class DXT1 extends DXT {
		
		
		@Override
		public BufferedImage decode(ByteBuffer buf, TextureInfo info) {
			Color [] colours;
		
			int width = info.width;
			int height = info.height;

			byte[] colourBuffer = new byte[4];
			
			BufferedImage b = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			
			//64 bits per 4x4 segment
			for(int y = 0;y<height;y+=4) {
				for(int x =0;x<width;x+=4) {
					
					buf.get(colourBuffer);
					colours = decodeColourMap(colourBuffer);
					// 4x4 segment
					int bits = buf.getInt();
					for(int yy=0;yy<4;yy++) {
						for(int xx=0;xx<4;xx++) {
							int colourIndex = (int) (bits & 0b11);
							if(yy + y < height && xx + x < width)
								b.setRGB(x+xx, y+yy, colours[colourIndex].getRGB());
							bits>>>=2;
						}
					}
				}
			}
			return b;
		}
		
		
		@Override
		protected void encodeImage(BufferedImage input, ByteBuffer writer) {
			int width = input.getWidth();
			int height = input.getHeight();
			byte[] in = imageToByteArray(input);
			byte[] result = Squish.compressImage(in, width, height, new byte[] {}, CompressionType.DXT1, method);
			writer.put(result);
		}

		@Override
		public boolean supportsAlpha() {
			return false;
		}
		
		@Override
		public byte formatIndex() {
			return 4;
		}


		@Override
		public int[] decodeAlphaMap(byte[] bytes) {
			throw new UnsupportedOperationException("DXT1 contains no alpha data");
		}
		
	}
	
	private class DXT3 extends DXT {
		
		{chunkByteSize = 16;}
		
		@Override
		public BufferedImage decode(ByteBuffer buf, TextureInfo info) {
			Color [] colours;
			int[] alphaValues;
			int width = info.width;
			int height = info.height;
			byte[] alphaBuf = new byte[8];
			byte[] colourBuf = new byte[4];
			
			BufferedImage b = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			
			//128 bits per 4x4 segment
			for(int y = 0;y<height;y+=4) {
				for(int x =0;x<width;x+=4) {
					//if(br.index() + 8 > br.size()) // DXT3 is broken for papatran, break early if we run out of data
						//return b;
					buf.get(alphaBuf);
					alphaValues = decodeAlphaMap(alphaBuf);
					
					buf.get(colourBuf);
					colours = decodeColourMap(colourBuf);
					int bits = buf.getInt();
					for(int yy=0;yy<4;yy++) {
						for(int xx=0;xx<4;xx++) {
							int colourIndex = (int) (bits & 0b11);
							if(yy + y < height && xx + x < width)
								b.setRGB(x+xx, y+yy, (colours[colourIndex].getRGB()&0b11111111_11111111_11111111) | alphaValues[yy*4+xx]<<24);
							bits>>>=2;
						}
					}
				}
			}
			return b;
		}
		
		@Override
		protected void encodeImage(BufferedImage input, ByteBuffer writer) {
			int width = input.getWidth();
			int height = input.getHeight();
			byte[] in = imageToByteArray(input);
			byte[] result = Squish.compressImage(in, width, height, new byte[] {}, CompressionType.DXT3, method);
			writer.put(result);
		}

		
		@Override
		public Color[] decodeColourMap(byte[] data) {
			Color[] colours = new Color[4];
			int colour0 = ((data[0]&0b11111111) | data[1]<<8) & 0b11111111_11111111;
			int colour1 = ((data[2]&0b11111111) | data[3]<<8) & 0b11111111_11111111;
			colours[0] = new Color((((colour0>>>11 & 0b00011111)*8)<<16) | (((colour0>>>5) & 0b00111111)*4)<<8 | (((colour0) & 0b00011111)*8));
			colours[1] = new Color((((colour1>>>11 & 0b00011111)*8)<<16) | (((colour1>>>5) & 0b00111111)*4)<<8 | (((colour1) & 0b00011111)*8));
			colours[2] = new Color(	(float)(2 * colours[0].getRed() + 		colours[1].getRed()) / 765f, // 765 = 3*255
									(float)(2 * colours[0].getGreen() + 	colours[1].getGreen()) / 765f,
									(float)(2 * colours[0].getBlue() + 		colours[1].getBlue()) / 765f);
			colours[3] = new Color(	(float)(colours[0].getRed() + 		2 * colours[1].getRed()) / 765f,
									(float)(colours[0].getGreen() + 	2 * colours[1].getGreen()) / 765f,
									(float)(colours[0].getBlue() + 		2 * colours[1].getBlue()) / 765f);
			
			return colours;
		}
		
		@Override
		public int[] decodeAlphaMap(byte[] bytes) {
			int[] alphaValues = new int[16];
			for(int i =0;i<8;i++) {
				int bits = (int)bytes[i] & 0b11111111;
				int val = bits & 0b00001111;
				alphaValues[2*i] = 	(val<<4) | val;
				val = bits & 0b11110000;
				alphaValues[2*i+1] = val | (val>>>4);
			}
			return alphaValues;
		}
		
		@Override
		public byte formatIndex() {
			return 5;
		}

		
	}
	
	private class DXT5 extends DXT {

		{chunkByteSize = 16;}
		
		@Override
		public BufferedImage decode(ByteBuffer br, TextureInfo info) {
			Color [] colours;
			int[] alphaValues;
			int width = info.width;
			int height = info.height;
			
			byte[] alphaBuf = new byte[8];
			byte[] colourBuf = new byte[4];
			
			BufferedImage b = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			
			//128 bits per 4x4 segment
			for(int y = 0;y<height;y+=4) {
				for(int x =0;x<width;x+=4) {
					
					// calculate and store the alpha values for the pixel.
					br.get(alphaBuf);
					alphaValues = decodeAlphaMap(alphaBuf);
					
					br.get(colourBuf);
					colours = decodeColourMap(colourBuf);
					
					int bits = br.getInt();
					for(int yy=0;yy<4;yy++) {
						for(int xx=0;xx<4;xx++) {
							int colourIndex = (int) (bits & 0b11);
							if(yy + y < height && xx + x < width)
								b.setRGB(x+xx, y+yy, (colours[colourIndex].getRGB() & 0b11111111_11111111_11111111) | (alphaValues[yy*4+xx]<<24));
							bits>>>=2;
						}
					}
				}
			}
			return b;
		}

		@Override
		protected void encodeImage(BufferedImage input, ByteBuffer writer) {
			int width = input.getWidth();
			int height = input.getHeight();
			byte[] in = imageToByteArray(input);
			byte[] result = Squish.compressImage(in, width, height, new byte[] {}, CompressionType.DXT5, method);
			writer.put(result);
		}
		
		@Override
		public int[] decodeAlphaMap(byte[] bytes) {
			int[] alphaValues = new int[16];
			
			int alphaMap[] = new int[8];
			alphaMap[0]=(int)(bytes[0] & 0b11111111);
			alphaMap[1]=(bytes[1] & 0b11111111);
			
			if(alphaMap[0]>alphaMap[1]) {
				for(int j = 1;j<7;j++)
					alphaMap[j+1] = ((7-j)*alphaMap[0] + j * alphaMap[1])/7;
			} else {
				for(int j = 1;j<5;j++)
					alphaMap[j+1] = ((5-j)*alphaMap[0] + j * alphaMap[1])/5;
				alphaMap[6] = 0;
				alphaMap[7] = 255;
			}
			
			long alphaBits = 0;
			for (int i=2;i<8;i++)
				alphaBits |= (((long)bytes[i]) & 0b11111111)<<((i-2)*8);
			
			for(int j = 0;j<16;j++) {
				alphaValues[j]= alphaMap[(int) (alphaBits&0b111)];
				alphaBits>>>=3;
			}
			
			return alphaValues;
		}
		
		@Override
		public byte formatIndex() {
			return 6;
		}
		
	}
	
	private class DXTAuto extends DXT {
		
		private final DXT1 dxt1 = new DXT1();
		private final DXT5 dxt5 = new DXT5();
		private DXT currentConverter = dxt1;

		@Override
		public int[] decodeAlphaMap(byte[] bytes) {
			return currentConverter.decodeAlphaMap(bytes);
		}

		@Override
		public BufferedImage decode(ByteBuffer buf, TextureInfo info) {
			return currentConverter.decode(buf, info);
		}

		@Override
		public byte formatIndex() {
			return currentConverter.formatIndex();
		}
		
		private void setCurrentConverterFromImage(BufferedImage input) {
			for( int x=0; x<input.getWidth(); x++ ) {
				for( int y=0; y<input.getHeight(); y++ ) {
					if((input.getRGB(x, y) >>> 24) < 255) {
						currentConverter = dxt5;
						return;
					}
					
				}
			}
			// no pixels with alpha found
			currentConverter = dxt1;
		}
		
		public byte[] encode(BufferedImage[] input) {
			setCurrentConverterFromImage(input[0]);
			return super.encode(input);
		}
		
		@Override
		public int calcSize(int width, int height, int mips) {
			return currentConverter.calcSize(width, height, mips);
		}

		@Override
		protected void encodeImage(BufferedImage input, ByteBuffer writer) {
			currentConverter.encodeImage(input, writer);
			
		}
		
		@Override
		public boolean supportsAlpha() {
			return currentConverter.supportsAlpha();
		}
		
	}
	
	private class R8 extends TextureConverter {

		@Override
		public BufferedImage decode(ByteBuffer buf, TextureInfo info) {
			int width = info.width;
			int height = info.height;
			int length = width*height;
			
			BufferedImage b = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			
			int[] array = new int[width*height];
			for(int i = 0;i<length;i++) {
				array[i] = buf.get()<<16 | 0b11111111_00000000_11111111_11111111;
			}
			
			b.setRGB(0, 0, width, height, array, 0, width);
			return b;
			
		}

		@Override
		protected void encodeImage(BufferedImage input, ByteBuffer writer) {
			int width = input.getWidth();
			int height = input.getHeight();
			int[] rgbArray = input.getRGB(0, 0, width, height, null, 0, input.getWidth());
			for(int i : rgbArray) {// ARGB
				writer.put((byte)(i>>>16));
			}
		}
		
		@Override
		protected BufferedImage calcLuminance(BufferedImage image) {
			BufferedImage out = new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_INT_ARGB);
			for(int y= 0;y<image.getHeight();y++)
				for(int x =0;x<image.getWidth();x++) {
					int value = image.getRGB(x, y);
					int average = (value & 0b11111111_00000000_00000000);
					int RGB = average | (average>>>8) | (average>>>16);
					out.setRGB(x, y, RGB  | 0b11111111_00000000_00000000_00000000);
				}
			return out;
		}
		
		@Override
		public boolean supportsAlpha() {
			return false;
		}

		@Override
		public int calcSize(int width, int height, int mips) {
			int size = 0;
			
			for(int i=0;i<mips + 1;i++) {
				int mipScale = (int) Math.pow(2, i);
				int w = Math.max(width / mipScale,1);
				int h = Math.max(height / mipScale,1);
				size+= w*h;
			}
			
			return size;
		}
		
		@Override
		public byte formatIndex() {
			return 13;
		}
	}
	
	
	public static class TextureSettings {
		public static final int RESIZE_NEAREST = 0;
		public static final int RESIZE_UP = 1;
		public static final int RESIZE_DOWN = 2;
		
		public static final int RESIZE_TYPE_NEAREST_NEIGHBOUR = 0;
		public static final int RESIZE_TYPE_BICUIBIC = 1;
		public static final int RESIZE_TYPE_BILEANR = 2;
		
		public static final int LINK_TYPE_EMBED = 0;
		public static final int LINK_TYPE_REFERENCE = 1;
		public static final int LINK_TYPE_OVERWRITE = 2; //TODO: unsupported!
		
		public static final String DXT_AUTO = "DXT (Auto)";
		public static final String DXT1 = "DXT1";
		public static final String DXT5 = "DXT5";
		public static final String R8G8B8A8 = "R8G8B8A8";
		public static final String R8G8B8X8 = "R8G8B8X8";
		public static final String B8G8R8A8 = "B8G8R8A8";
		public static final String R8 = "R8"; 
		
		private String format;
		private CompressionMethod method;
		private boolean generateMipmaps;
		private int mipmapResizeMethod;
		private boolean SRGB;
		private boolean resize;
		private int resizeMethod;
		private int resizeMode;
		private boolean linkEnabled;
		private PapaFile linkTarget;
		private int linkMethod;
		private String srgbTexname;
		
		public String getFormat() {
			return format;
		}
		public TextureSettings setFormat(String format) {
			this.format = format;
			return this;
		}
		public CompressionMethod getCompressionMethod() {
			return method;
		}
		public TextureSettings setCompressionMethod(CompressionMethod method) {
			this.method = method;
			return this;
		}
		public boolean getGenerateMipmaps() {
			return generateMipmaps;
		}
		public TextureSettings setGenerateMipmaps(boolean generateMipmaps) {
			this.generateMipmaps = generateMipmaps;
			return this;
		}
		public int getMipmapResizeMethod() {
			return mipmapResizeMethod;
		}
		public TextureSettings setMipmapResizeMethod(int mipmapResizeMethod) {
			this.mipmapResizeMethod = mipmapResizeMethod;
			return this;
		}
		public boolean getSRGB() {
			return SRGB;
		}
		public TextureSettings setSRGB(boolean sRGB) {
			SRGB = sRGB;
			return this;
		}
		public boolean getResize() {
			return resize;
		}
		public TextureSettings setResize(boolean resize) {
			this.resize = resize;
			return this;
		}
		public int getResizeMethod() {
			return resizeMethod;
		}
		public TextureSettings setResizeMethod(int resizeMethod) {
			this.resizeMethod = resizeMethod;
			return this;
		}
		public int getResizeMode() {
			return resizeMode;
		}
		public TextureSettings setResizeMode(int resizeMode) {
			this.resizeMode = resizeMode;
			return this;
		}
		public boolean getLinkEnabled() {
			return linkEnabled;
		}
		public TextureSettings setLinkEnabled(boolean linkEnabled) {
			this.linkEnabled = linkEnabled;
			return this;
		}
		public PapaFile getLinkTarget() {
			return linkTarget;
		}
		public TextureSettings setLinkTarget(PapaFile linkTarget) {
			this.linkTarget = linkTarget;
			return this;
		}
		public int getLinkMethod() {
			return linkMethod;
		}
		public TextureSettings setLinkMethod(int linkMethod) {
			this.linkMethod = linkMethod;
			return this;
		}
		public String getSRGBTexname() {
			return srgbTexname;
		}
		public TextureSettings setSRGBTexname(String texname) {
			this.srgbTexname=texname;
			return this;
		}
		public static TextureSettings defaultSettings() {
			return new TextureSettings(TextureSettings.DXT_AUTO,CompressionMethod.CLUSTER_FIT,true, RESIZE_TYPE_BICUIBIC, true, RESIZE_TYPE_BICUIBIC,RESIZE_NEAREST, 
					false,false,null,LINK_TYPE_EMBED,"diffuse|skybox");
		}
		
		public TextureSettings() {};
		
		public TextureSettings(	String format, CompressionMethod method, boolean generateMipmaps, int mipmapResizeMethod, boolean resize, int resizeMethod, int resizeMode, boolean SRGB,
								boolean linkEnabled, PapaFile linkTarget, int linkMethod, String srgbTexname) {
			this.format = format;
			this.method = method;
			this.generateMipmaps = generateMipmaps;
			this.mipmapResizeMethod = mipmapResizeMethod;
			this.resize = resize;
			this.resizeMethod = resizeMethod;
			this.resizeMode=resizeMode;
			this.SRGB = SRGB;
			this.linkEnabled=linkEnabled;
			this.linkTarget=linkTarget;
			this.linkMethod=linkMethod;
			this.srgbTexname=srgbTexname;
		}
		
		public ImmutableTextureSettings immutable() {
			return new ImmutableTextureSettings(format,method, generateMipmaps, mipmapResizeMethod, resize, resizeMethod, 
					resizeMode, SRGB,linkEnabled,linkTarget,linkMethod,srgbTexname);
		}
	}
	
	public static final class ImmutableTextureSettings {
		public final String format;
		public final CompressionMethod method;
		public final boolean generateMipmaps;
		public final int mipmapResizeMethod;
		public final boolean SRGB;
		public final boolean resize;
		public final int resizeMethod;
		public final int resizeMode;
		public final boolean linkEnabled;
		public final PapaFile linkTarget;
		public final int linkMethod;
		public final String srgbTexname;
		
		private ImmutableTextureSettings(	String format, CompressionMethod method,  boolean generateMipmaps, int mipmapResizeMethod, boolean resize, int resizeMethod, 
											int resizeMode, boolean SRGB, boolean linkEnabled, PapaFile linkTarget, int linkMethod, String srgbTexname) {
			this.format = format;
			this.method = method;
			this.generateMipmaps = generateMipmaps;
			this.mipmapResizeMethod = mipmapResizeMethod;
			this.resize = resize;
			this.resizeMethod = resizeMethod;
			this.resizeMode = resizeMode;
			this.SRGB = SRGB;
			this.linkEnabled=linkEnabled;
			this.linkTarget=linkTarget;
			this.linkMethod=linkMethod;
			this.srgbTexname=srgbTexname;
		}
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj == null || !(obj.getClass().equals(PapaTexture.class)))
			return false;
		if(obj==this)
			return true;
		PapaTexture t = (PapaTexture) obj;
		return 		(t.format 		== 	format)
				&&	(t.mips			==	mips)
				&&	(t.srgb			==	srgb)
				&&	(t.width		==	width)
				&&	(t.height		==	height)
				&&	(t.numImages	==	numImages)
				&&	(t.isLinked		==	isLinked)
				&&	(t.name.equals(		name))
				&&	(isLinked ? linkValid() == t.linkValid() && getLinkedTexture().equals(t.getLinkedTexture()) : compareImages(textures, t.textures));
				
	}
	
	private boolean compareImages(BufferedImage[] set1, BufferedImage[] set2) {
		if(set1==null || set2==null)
			return set1==set2;
		if(set1.length!=set2.length)
			return false;
		for(int i =0;i<set1.length;i++) {
			if(set1[i].getWidth()!=set2[i].getWidth())
				return false;
			if(set1[i].getHeight()!=set2[i].getHeight())
				return false;
			if(set1[i].getTransparency()!=set2[i].getTransparency())
				return false;
			if(! set1[i].getColorModel().equals(set2[i].getColorModel()))
				return false;
		}
		for(int i =0;i<set1.length;i++) {
			if(!compareImage(set1[i], set2[i]))
				return false;
		}
		return true;
	}
	
	private boolean compareImage(BufferedImage a, BufferedImage b) {
		for(int x = 0;x<a.getWidth();x++)
			for(int y = 0;y<a.getHeight();y++)
				if(a.getRGB(x, y)!=b.getRGB(x, y))
					return false;
		return true;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) format;
		result = prime * result + (int) mips;
		result = prime * result + 		(srgb ? 1 : 0);
		result = prime * result + (int) width;
		result = prime * result + (int) height;
		result = prime * result + 		numImages;
		result = prime * result + 		(isLinked ? 1 : 0);
		result = prime * result + 		name.hashCode();
		return result;
	}

	@Override
	public void detach() {
		if(parent==null)
			return;
		parent.removeTexture(this);
		parent = null;
	}

	@Override
	protected void setParent(PapaFile newParent) {
		parent = newParent;
		/*if(isLinked) {
			adjustLinkedTextureName(name); // refresh parent TODO: remove if obsolete
		}*/
		newParent.addTexture(this);
	}

	@Override
	public PapaTexture duplicate() {
		PapaTexture copy = this;
		//if(copy.isLinked)
		//	copy = copy.getLinkedTexture();
		try {
			return new PapaTexture(copy.name,copy.format,(byte) (copy.mips + 1), copy.srgb, copy.width,copy.height, copy.data.clone(),null);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
	
	@Override
	protected void overwriteHelper(PapaComponent other) {// TODO
		PapaTexture tex = (PapaTexture) other;
		checkLinked(false);
		tex.checkLinked(false);
		
		this.textures = tex.textures;
		this.red=tex.red;
		this.green=tex.green;
		this.blue=tex.blue;
		this.alpha=tex.alpha;
		this.data = tex.data;
		this.width = tex.width;
		this.height = tex.height;
		this.srgb = tex.srgb;
		this.format = tex.format;
		this.mips = tex.mips;
		this.numImages = tex.numImages;
		this.textureConverter = tex.textureConverter;
	}

	@Override
	public PapaComponent[] getDependencies() {
		ArrayList<PapaComponent> dependencies = new ArrayList<PapaComponent>();
		if(isLinked() && linkValid()) {
			dependencies.add(getLinkedTexture());
			dependencies.add(getLinkedTexture().getParent());
		}
		int index = getParent().getOrMakeString(name);
		dependencies.add(getParent().getString(index));
		return dependencies.toArray(new PapaComponent[dependencies.size()]);
	}

	@Override
	public PapaComponent[] getDependents() {
		if(getParent()==null)
			return new PapaComponent[0];
		return getParent().getAllDependentsFor(this);
	}

	@Override
	protected boolean isDependentOn(PapaComponent other) {
		if(other==null)
			return false;
		if(other.getClass()==PapaString.class)
			return ((PapaString)other).getValue().equals(name);
		if(isLinked() && linkValid()) {
			if(other.getClass()==PapaTexture.class)
				return getLinkedTexture() == other;
			if(other.getClass()==PapaFile.class)
				return getLinkedTexture().getParent() == other;
		}
		return false;
	}
}
