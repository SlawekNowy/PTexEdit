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
import java.io.*;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.Map.Entry;

import javax.imageio.ImageIO;

import com.github.memo33.jsquish.Squish.CompressionMethod;

import papafile.PapaModel.PapaMeshBinding;
import papafile.PapaTexture.ImmutableTextureSettings;
import papafile.PapaTexture.TextureSettings;

public class PapaFile extends PapaComponent{
	
	private long 	fileSize;

	private ArrayList<PapaString> strings = new ArrayList<PapaString>();
	private ArrayList<PapaTexture> textures = new ArrayList<PapaTexture>();
	private ArrayList<PapaVertexBuffer> vBuffers = new ArrayList<PapaVertexBuffer>();
	private ArrayList<PapaIndexBuffer> iBuffers = new ArrayList<PapaIndexBuffer>();
	private ArrayList<PapaMaterial> materials = new ArrayList<PapaMaterial>();
	private ArrayList<PapaMesh> meshes = new ArrayList<PapaMesh>();
	private ArrayList<PapaSkeleton> skeletons = new ArrayList<PapaSkeleton>();
	private ArrayList<PapaModel> models = new ArrayList<PapaModel>();
	private ArrayList<PapaAnimation> animations = new ArrayList<PapaAnimation>();
	
	private HashMap<String, PapaFile> linkedFiles = new HashMap<String, PapaFile>();
	
	private byte[] fileBytes = null;
	
	private static final boolean ERROR_IF_NOT_FOUND = false;
	
	@SuppressWarnings("unchecked")
	private ArrayList<? extends PapaComponent>[] components = (ArrayList<? extends PapaComponent>[]) new ArrayList<?>[] {strings,textures,vBuffers,iBuffers, materials, 
																														meshes,skeletons, models, animations};
	private static final String[] COMPONENT_NAMES = new String[] {	"Strings","Textures","Vertex Buffers","Index Buffers","Materials",
																	"Meshes", "Skeletons", "Models", "Animations"};
	private static final int[] buildOrder = new int[] {7,5,4,1,2,3,6,8,0};
	
	public static final int NONE =		0b000000000;
	public static final int STRING = 	0b000000001;
	public static final int TEXTURE = 	0b000000010;
	public static final int VBUF = 		0b000000100;
	public static final int IBUF = 		0b000001000;
	public static final int MATERIAL = 	0b000010000;
	public static final int MESH = 		0b000100000;
	public static final int SKELETON = 	0b001000000;
	public static final int MODEL = 	0b010000000;
	public static final int ANIMATION = 0b100000000;
	public static final int ALL = 		0b111111111;
	private static final int HEADER_SIZE = 0x68;
	
	private static File PA_ROOT_DIR = null;
	private static String PA_ROOT_DIR_STRING = null;
	
	private int 	signature;
	private int 	minorVersion = 0;
	private int 	majorVersion = 3;
	
	private short	numStrings;
	private short 	numTextures;
	private short 	numVBuffers;
	private short 	numIBuffers;
	
	private short 	numMaterials;
	private short 	numMeshes;
	private short 	numSkeletons;
	private short	numModels;
	
	
	private short 	numAnimations;
	
	private long 	offsetStringTable;
	private long 	offsetTextureTable;
	private long 	offsetVBufferTable;
	private long 	offsetIBufferTable;
	private long 	offsetMaterialTable;
	private long 	offsetMeshTable;
	private long 	offsetSkeletonTable;
	private long 	offsetModelTable;
	private long 	offsetAnimationTable;
	
	
	private File fileLocation;
	private String 	fileName = "Unknown";
	private String 	filePath = "Unknown";
	private String	relativePath = "Unknown";
	private String  fileSignature = "";
	
	private boolean isPapa;
	private boolean buildSuccessful = false;
	
	private int maxErrorLevel = 0;
	
	private BuildNotification[] buildNotifications = new BuildNotification[0];
	
	private boolean isLinked = false;
	private PapaFile parentFile = null;
	
	private static final PapaFile DEFAULT = new PapaFile();
	
	static {
		try {
			DEFAULT.addTexture(ImageIO.read(PapaFile.class.getResource("/papafile/error.png")), 
								new TextureSettings("R8G8B8A8",CompressionMethod.CLUSTER_FIT, false, 0, false, 0, 0, false,false,null,0,"diffuse").immutable());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private ByteBuffer in;
	
	private static PapaFile readLinkedFile(String fullPath, PapaFile parent) throws IOException{
		PapaFile p = new PapaFile(fullPath);
		p.attach(parent);
		return p;
	}

	public PapaFile() {};
	
	public PapaFile(PapaComponent... comp) {
		for(PapaComponent c : comp) {
			Class<?> cl = c.getClass();
			if(cl.equals(PapaTexture.class)) { // adding strings is illegal since they are generated on build.
				textures.add((PapaTexture) c);
				numTextures++;
			} else {
				throw new IllegalArgumentException("Unsupported class: "+cl.getSimpleName());
			}
		}
		validateAll();
		fileSize = calcFileSize();
	}
	
	private InputStream getStream(String path) throws IOException {
		File f = new File(path);
		return new FileInputStream(f);
	}
	
	public PapaFile(String path, int flags) throws IOException {
		instantiate(getStream(path), path, flags);
	}

	public PapaFile(String path) throws IOException {
		instantiate(getStream(path), path, ALL);
	}
	
	public PapaFile(InputStream stream, String path, int flags) throws IOException {
		instantiate(stream, path, flags);
	}
	
	public PapaFile(InputStream stream, String path) throws IOException {
		instantiate(stream, path, ALL);
	}
	
	public void setFileLocation(File newLocation) {
		
		if(newLocation == null) {
			fileLocation = null;
			fileName = "Unknown";
			filePath = "Unknown";
			relativePath = "Unknown";
			return;
		}
		
		String path;
		try {
			path = newLocation.getCanonicalPath().replace('\\', '/');  // PA only uses /
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		}
		int loc = path.lastIndexOf('/');
		fileLocation = newLocation;
		fileName = loc != -1 ? path.substring(loc + 1) : path;
		filePath = loc != -1 ? path.substring(0,loc) : path;
		loc = fileName.lastIndexOf(".");
		isPapa = loc != -1 && fileName.substring(loc).equals(".papa");
		if(PA_ROOT_DIR != null && path.startsWith(PA_ROOT_DIR_STRING))
			relativePath = path.substring(PA_ROOT_DIR_STRING.length());
		else
			relativePath = "Unknown";
		if(isLinked)
			parentFile.updateLinkedFile(this, relativePath);
	}
	
	public void setLocationRelative(String newName) {
		setFileLocation(new File((PA_ROOT_DIR==null ? "" : PA_ROOT_DIR) + newName));
	}
	
	public void setSignature(String signature) {
		if(signature.length() > 6) {
			signature = signature.substring(0, 6);
		}
		this.fileSignature = signature;
	}
	
	public String getSignature() {
		return this.fileSignature;
	}
	
	public String getFileName() {
		return fileName;
	}
	
	public boolean relativeFileNameAvailable() {
		return PA_ROOT_DIR !=null && !relativePath.equals("Unknown");
	}
	
	public String getRelativeFileName() {
		return relativePath;
	}
	
	public String getFilePath() {
		return filePath;
	}
	
	public File getFile() {
		return fileLocation;
	}
	
	public long getFileSize() {
		return fileSize;
	}
	
	public int getMajorVersion() {
		return majorVersion;
	}
	
	public int getMinorVersion() {
		return minorVersion;
	}
	
	public String getVersion() {
		return majorVersion+"."+minorVersion;
	}
	
	public int getNumStrings() {
		return numStrings;
	}
	
	public int getNumTextures() {
		return numTextures;
	}
	
	public int getNumVBuffers() {
		return numVBuffers;
	}
	
	public int getNumIBuffers() {
		return numIBuffers;
	}
	
	public int getNumMaterials() {
		return numMaterials;
	}
	
	public int getNumMeshes() {
		return numMeshes;
	}
	
	public int getNumSkeletons() {
		return numSkeletons;
	}
	
	public int getNumModels() {
		return numModels;
	}
	
	public int getNumAnimations() {
		return numAnimations;
	}
	
	public boolean isPapaFile() {
		return isPapa;
	}
	
	public boolean buildSuccessful() {
		return this.buildSuccessful;
	}
	
	public boolean testBuildErrorLevel(int maxErrorLevel) {
		return this.maxErrorLevel >= maxErrorLevel;
	}
	
	public BuildNotification[] getBuildNotifications() {
		return this.buildNotifications;
	}
	
	public void generateLinkedTexture(PapaTexture t) {
		PapaFile p = generateLinkedFile(t.getName());
		t.attach(p);
		addTexture(new PapaTexture(t.getName(),this));
	}
	
	private PapaFile generateLinkedFile(String name) {
		PapaFile p = new PapaFile();
		p.setLocationRelative(name);
		linkedFiles.put(name, p);
		p.attach(this);
		return p;
	}

	void updateLinkedFile(PapaFile link, String newReference) {
		String originalName = getLinkName(link);
		linkedFiles.remove(originalName);
		addToLinkedFiles(newReference, link);
	}
	
	private void addToLinkedFiles(String name, PapaFile link) {
		//PapaFile p = linkedFiles.get(name);
		/*if(p!=null && !p.equals(link)) {
			throw new IllegalArgumentException("Every linked texture must point to a unique file");
		}*/
		linkedFiles.put(name, link);
	}

	private String getLinkName(PapaFile other) {
		if(other.getParent() != this)
			throw new IllegalArgumentException("PapaFile is not linked to this file");
		for(Entry<String,PapaFile> e : linkedFiles.entrySet())
			if(e.getValue() == other)
				return e.getKey();
		throw new IllegalStateException("A child linked file was not found in the parent PapaFile");
	}
	
	public void absorbLinkedTexture(PapaTexture t) {
		if(!t.isLinked())
			throw new IllegalArgumentException("Cannot absorb non linked texture");
		int index = textures.indexOf(t);
		if(index==-1)
			throw new IllegalArgumentException("Cannot absorb texture which is not owned by this PapaFile");
		PapaTexture link = textures.remove(index);
		PapaTexture target = link.getLinkedTexture();
		
		target.detach();
		link.detach();
		link.flush();
		target.attach(this);
		
		textures.add(target);
		recalculateFileSize();
	}
	
	public PapaTexture getTexture(String name) {
		for(int i = 0; i< textures.size();i++)
			if(textures.get(i).getName().equals(name))
				return textures.get(i);
		throw new IllegalArgumentException("Texture \""+name+"\" not found.");
	}
	
	public void addTexture(BufferedImage b, ImmutableTextureSettings t) throws IOException{
		textures.add(new PapaTexture(b, t, this));
		recalculateFileSize();
	}
	
	void addTexture(PapaTexture tex) {
		textures.add(tex);
		recalculateFileSize();
	}
	
	/*void removeTexture(PapaTexture tex) {
		PapaFile linkedFile = null;
		if(tex.isLinked() && tex.linkValid() && getReferencesToLinkedFile(tex.getLinkedTexture().getParent()) == 1) {
			linkedFile = tex.getLinkedTexture().getParent();
		}
		
		if(isLinked) {
			detach();
		}
		
		int index = indexOfReference(textures, tex);
		if(index==-1)
			throw new IllegalArgumentException("Cannot remove texture which does not belong to this papaFile");
		textures.remove(index);
		
		if(linkedFile!=null)
			linkedFile.detach();
		
		recalculateFileSize();
	}*/
	
	void removeTexture(PapaTexture tex) {
		removeComponent(textures, tex, "texture");
	}
	
	
	private int indexOfReference(ArrayList<? extends PapaComponent> list, PapaComponent comp) {
		for(int i =0;i<list.size();i++)
			if(list.get(i)==comp)
				return i;
		return -1;
	}
	
	void addVertexBuffer(PapaVertexBuffer buf) {
		vBuffers.add(buf);
		recalculateFileSize();
	}
	
	void removeVertexBuffer(PapaVertexBuffer buf) {
		removeComponent(vBuffers, buf, "vertex buffer");
	}
	
	void addIndexBuffer(PapaIndexBuffer buf) {
		iBuffers.add(buf);
		recalculateFileSize();
	}
	
	void removeIndexBuffer(PapaIndexBuffer buf) {
		removeComponent(iBuffers, buf, "index buffer");
	}
	
	void addMaterial(PapaMaterial mat) {
		materials.add(mat);
		recalculateFileSize();
	}
	
	void removeMaterial(PapaMaterial mat) {
		removeComponent(materials, mat, "material");
	}
	
	void addMesh(PapaMesh mesh) {
		meshes.add(mesh);
		recalculateFileSize();
	}
	
	void removeMesh(PapaMesh mesh) {
		removeComponent(meshes, mesh, "mesh");
	}
	
	void addSkeleton(PapaSkeleton skeleton) {
		skeletons.add(skeleton);
		recalculateFileSize();
	}
	
	void removeSkeleton(PapaSkeleton skeleton) {
		removeComponent(skeletons, skeleton, "skeleton");
	}
	
	void addModel(PapaModel model) {
		models.add(model);
		recalculateFileSize();
	}
	
	void removeModel(PapaModel model) {
		removeComponent(models, model, "model");
	}
	
	void addAnimation(PapaAnimation animation) {
		animations.add(animation);
		recalculateFileSize();
	}
	
	void removeAnimation(PapaAnimation animation) {
		removeComponent(animations, animation, "animation");
	}
	
	private void removeComponent(ArrayList<? extends PapaComponent> list, PapaComponent comp, String componentType) {
		int index = indexOfReference(list,comp);
		if(index==-1)
			throw new IllegalArgumentException("Cannot remove "+componentType+" which does not belong to this papaFile");
		list.remove(index);
		recalculateFileSize();
	}
	
	public PapaString getString(int index) {  // it is unwise to use this method directly unless you know what you're doing. Strings are highly volatile.
		if(index==-1)
			return new PapaString("", null);
		checkAccess(strings,index);
		return strings.get(index);
	}
	
	public PapaTexture getTexture(int index) {
		if(index==-1)
			return null;
		checkAccess(textures,index);
		return textures.get(index);
	}
	
	public PapaVertexBuffer getVertexBuffer(int index) {
		if(index==-1)
			return null;
		checkAccess(vBuffers,index);
		return vBuffers.get(index);
	}
	public PapaIndexBuffer getIndexBuffer(int index) {
		if(index==-1)
			return null;
		checkAccess(iBuffers,index);
		return iBuffers.get(index);
	}
	public PapaMaterial getMaterial(int index) {
		if(index==-1)
			return null;
		checkAccess(materials,index);
		return materials.get(index);
	}
	public PapaMesh getMesh(int index) {
		if(index==-1)
			return null;
		checkAccess(meshes,index);
		return meshes.get(index);
	}
	public PapaSkeleton getSkeleton(int index) {
		if(index==-1)
			return null;
		checkAccess(skeletons,index);
		return skeletons.get(index);
	}
	public PapaModel getModel(int index) {
		if(index==-1)
			return null;
		checkAccess(models,index);
		return models.get(index);
	}
	public PapaAnimation getAnimation(int index) {
		if(index==-1)
			return null;
		checkAccess(animations,index);
		return animations.get(index);
	}
	
	private void checkAccess(ArrayList<? extends PapaComponent> list, int index) {
		if(index <-1 || index >= list.size())
			throw new IllegalArgumentException("Invalid component array access.");
	}
	
	int getStringIndex(String string) {  // it is unwise to use this method directly unless you know what you're doing. Strings are highly volatile.
		if(strings==null)
			return -1;
		for(int i =0;i<strings.size();i++)
			if(strings.get(i).getValue().equals(string))
				return i;
		throw new IllegalArgumentException("PapaComponent "+string+" does not belong to this PapaFile ("+toString()+")");
	}
	int getTextureIndex(PapaTexture tex) {
		return getComponentIndex(textures, tex);
	}
	int getVertexBufferIndex(PapaVertexBuffer buf) {
		return getComponentIndex(vBuffers, buf);
	}
	int getIndexBufferIndex(PapaIndexBuffer buf) {
		return getComponentIndex(iBuffers, buf);
	}
	int getMaterialIndex(PapaMaterial mat) {
		return getComponentIndex(materials, mat);
	}
	int getMeshIndex(PapaMesh mesh) {
		return getComponentIndex(meshes, mesh);
	}
	int getSkeletonIndex(PapaSkeleton skeleton) {
		return getComponentIndex(skeletons, skeleton);
	}
	int getModelIndex(PapaModel model) {
		return getComponentIndex(models, model);
	}
	int getAnimationIndex(PapaAnimation anim) {
		return getComponentIndex(animations, anim);
	}
	private int getComponentIndex(ArrayList<? extends PapaComponent> list, PapaComponent comp) {
		if(comp==null)
			return -1;
		for(int i =0;i<list.size();i++)
			if(list.get(i)==comp)
				return i;
		throw new IllegalArgumentException("PapaComponent "+comp+" does not belong to this PapaFile ("+toString()+")");
	}

	public boolean isLinkedFileReferenced(PapaFile other) {
		return getReferencesToLinkedFile(other)!=0;
	}
	
	private int getReferencesToLinkedFile(PapaFile other) {
		int count = 0;
		for(PapaTexture t : textures) {
			if(!t.isLinked())
				continue;
			if(t.linkValid() && t.getLinkedTexture().getParent()==other)
				count++;
		}
		return count;
	}
	
	public boolean containsComponents(int flags) {
		for(int i =0;i<9;i++)
			if((flags & 1<<i) != 0 && components[i].size()!=0)
				return true;
		return false;
	}

	public boolean containsLinkedFiles() {
		return linkedFiles.size()!=0;
	}
	
	public boolean containsLinkedFile(String linkName) {
		return linkedFiles.containsKey(linkName);
	}
	
	public PapaFile getLinkedFile(String linkName) {
		return linkedFiles.getOrDefault(linkName, DEFAULT);
	}
	
	public PapaFile[] getLinkedFiles() {
		return linkedFiles.values().toArray(new PapaFile[linkedFiles.size()]);
	}
	
	public boolean isLinkedFile() {
		return isLinked;
	}
	
	private void ensureLink() {
		if(!isLinked)
			throw new IllegalStateException("PapaFile is not linked!");
	}
	
	private void unlinkFile(PapaFile other, boolean removeDependencies) { // helper method for detach
		if(removeDependencies)
			removeDependencies(other);
		String key = getLinkName(other);
		linkedFiles.remove(key);
	}
	
	public void reloadLinkedTextures() {
		for(PapaFile p : getLinkedFiles())
			p.flush();
		linkedFiles.clear();
		for(PapaTexture t : textures)
			if(t.isLinked()) {
				try {
					String fullPath = PapaFile.PA_ROOT_DIR + t.getName();
					PapaFile p = openLinkedPapaFile(fullPath);
					if(p!=null)
						addToLinkedFiles(t.getName(), p);
				} catch(IOException e) {};
			}
				
	}
	
	private void removeDependencies(PapaFile other) {
		boolean change = false;
		for(int i = 0; i<textures.size();i++) {
			PapaTexture t = textures.get(i);
			if(!t.isLinked())
				continue;
			if(t.linkValid() && t.getLinkedTexture().getParent() == other) {
				textures.remove(i--);
				change = true;
				t.flush();
			}
		}
		if(change)
			recalculateFileSize();
	}
	
	private void recalculateFileSize() {
		validateAll();
		fileSize = calcFileSize();
	}
	
	@Override
	public PapaFile getParent() {
		return parentFile;
	}
	
	public PapaFile getTopParentFile() { // this might be redundant.
		ensureLink();
		return getTopParentFileHelper();
	}
	
	private PapaFile getTopParentFileHelper() {
		if(!isLinked)
			return this;
		return parentFile.getTopParentFileHelper();
	}
	
	private void instantiate(InputStream stream, String path, int flags) throws IOException {
		try {
			File f = new File(path);
			/*if( ! f.exists())
				throw new IOException("File "+f.getPath()+" not found.");
			if(f.isDirectory())
				throw new IOException("File "+f.getPath()+" is a directory.");*/
			setFileLocation(f);
		
			byte[] fileData = readStream(stream);
			in = ByteBuffer.wrap(fileData);
			in.order(ByteOrder.LITTLE_ENDIAN);
			
			fileSize = in.limit();
			
			if(fileSize == 0)
				throw new IOException("File is empty");
			readHeader(in);
			
			if((STRING & flags) == STRING)
				readStrings(in);
			if((TEXTURE & flags) == TEXTURE)
				readTextures(in);
			if((VBUF & flags) == VBUF)
				readVBuffers(in);
			if((IBUF & flags) == IBUF)
				readIBuffers(in);
			if((MATERIAL & flags) == MATERIAL)
				readMaterials(in);
			if((MESH & flags) == MESH)
				readMeshes(in);
			if((SKELETON & flags) == SKELETON)
				readSkeletons(in);
			if((MODEL & flags) == MODEL)
				readModels(in);
			if((ANIMATION & flags) == ANIMATION)
				readAnimations(in);
		} catch (IOException e) {
			throw e;
		} catch (BufferUnderflowException b) {
			throw new IOException(b);
		} catch(IllegalArgumentException i) {
			throw new IOException("File data could not be parsed.");
		} finally {
			in.clear();
			in = null;
			stream.close();
		}
	}
	
	private byte[] readStream(InputStream stream) throws IOException {
		int available = stream.available();
		int dataSize = 0;
		byte[] buf = new byte[Math.max(available,1024)];
		ByteArrayOutputStream bos = new ByteArrayOutputStream(available);
		
		while(true) {
			dataSize = stream.read(buf);
			if(dataSize==-1)
				return bos.toByteArray();
			bos.write(buf, 0, dataSize);
		}
	}
	
	private void readHeader(ByteBuffer in) throws IOException {
		signature = in.getInt();
		
		if(signature != 0x50617061)
			throw new IOException("File signature does not match Papa specification.");
		
		minorVersion = 	in.getShort();
		majorVersion = 	in.getShort();
		
		numStrings = 	in.getShort();
		numTextures = 	in.getShort();
		numVBuffers = 	in.getShort();
		numIBuffers = 	in.getShort();
		
		numMaterials = 	in.getShort();
		numMeshes = 	in.getShort();
		numSkeletons = 	in.getShort();
		numModels = 	in.getShort();
		
		numAnimations = in.getShort();
		
		byte[] signatureBuffer = new byte[6];
		in.get(signatureBuffer);
		this.fileSignature = new String(signatureBuffer).replace("\u0000", "");
		
		offsetStringTable = 	in.getLong();
		offsetTextureTable = 	in.getLong();
		offsetVBufferTable = 	in.getLong();
		offsetIBufferTable = 	in.getLong();
		offsetMaterialTable = 	in.getLong();
		offsetMeshTable = 		in.getLong();
		offsetSkeletonTable = 	in.getLong();
		offsetModelTable = 		in.getLong();
		offsetAnimationTable = 	in.getLong();
	}
	
	private void readStrings(ByteBuffer in) throws IOException {
		if (numStrings == 0)
			return;
		in.position((int) offsetStringTable);
		
		int[] length = 	new int[numStrings];
		long[] offset = new long[numStrings];
		
		for(int i =0;i<numStrings;i++) {
			length[i] = 	in.getInt();
			in.getInt();
			offset[i] = 	in.getLong();
		}
		
		for(int i=0;i<numStrings;i++) {
			in.position((int) offset[i]);
			
			byte[] buf = new byte[length[i]];
			in.get(buf);
			
			String s = new String(buf);
			
			strings.add(new PapaString(s,this));
		}
	}
	
	private void readTextures(ByteBuffer in) throws IOException {
		if(numTextures == 0)
			return;
		in.position((int) offsetTextureTable);
		
		short[] nameIndex = new short[numTextures];
		byte[] format = 	new byte[numTextures];
		byte mips[] = 		new byte[numTextures];
		boolean srgb[] = 	new boolean[numTextures];
		short[] width = 	new short[numTextures];
		short[] height = 	new short[numTextures];
		long[] size = 		new long[numTextures];
		long[] offset = 	new long[numTextures];
		
		for(int i =0;i<numTextures;i++) {
			nameIndex[i] = 	in.getShort();
			format[i] = 	in.get();
			byte input = 	in.get();
			mips[i] = 		(byte) (input&0b0111_1111);
			srgb[i] = 		(input & 0b1000_0000)==0b1000_0000;
			width[i] = 		in.getShort();
			height[i] = 	in.getShort();
			size[i] = 		in.getLong();
			offset[i] = 	in.getLong();
		}
		
		for(int i=0;i<numTextures;i++) {
			if(offset[i] >=0) {
				in.position((int) offset[i]);
				byte[] buf = new byte[(int) size[i]];
				in.get(buf);
				
				textures.add(new PapaTexture(getString(nameIndex[i]).getValue(), format[i],
											mips[i], srgb[i], width[i], height[i], buf, this));
			} else { // file is linked
				if(PA_ROOT_DIR==null) 
					throw new IOException("Cannot load external images. Media directory not set.");
				String name = strings.get(nameIndex[i]).getValue();
				String fullPath = PapaFile.PA_ROOT_DIR + name;
				PapaFile p = openLinkedPapaFile(fullPath);
				if(p!=null)
					addToLinkedFiles(name, p);
				textures.add(new PapaTexture(name,this));
			}
			
		}
	}
	
	private void readVBuffers(ByteBuffer in) throws IOException {
		if(numVBuffers == 0)
			return;
		in.position((int) offsetVBufferTable);
		
		byte[] format = 	new byte[numVBuffers];
		int[] vertices = 	new int[numVBuffers];
		long[] size =	 	new long[numVBuffers];
		long[] offset =	 	new long[numVBuffers];
		
		for(int i =0;i<numVBuffers;i++) {
			format[i] = 	in.get();
			in.position(in.position() + 3);
			vertices[i] = 	in.getInt();
			size[i] =		in.getLong();
			offset[i] = 	in.getLong();
		}
		
		for(int i=0;i<numVBuffers;i++) {
			in.position((int) offset[i]);
			byte[] buf = new byte[(int)size[i]];
			in.get(buf);
			vBuffers.add(new PapaVertexBuffer(format[i], vertices[i], buf, this));
		}
	}
	
	private void readIBuffers(ByteBuffer in) throws IOException {
		if(numIBuffers == 0)
			return;
		in.position((int) offsetIBufferTable);
		
		byte[] format = 	new byte[numIBuffers];
		int[] indices = 	new int[numIBuffers];
		long[] size =	 	new long[numIBuffers];
		long[] offset =	 	new long[numIBuffers];
		
		for(int i =0;i<numIBuffers;i++) {
			format[i] = 	in.get();
			in.position(in.position() + 3);
			indices[i] = 	in.getInt();
			size[i] =		in.getLong();
			offset[i] = 	in.getLong();
		}
		
		for(int i=0;i<numIBuffers;i++) {
			in.position((int) offset[i]);
			byte[] buf = new byte[(int)size[i]];
			in.get(buf);
			iBuffers.add(new PapaIndexBuffer(format[i], indices[i], buf, this));
		}
	}
	
	private void readMaterials(ByteBuffer in) throws IOException {
		if(numMaterials == 0)
			return;
		in.position((int) offsetMaterialTable);
		
		short[] shaderIndex = 			new short[numMaterials];
		short[] numVectorParam = 		new short[numMaterials];
		short[] numTextureParam =		new short[numMaterials];
		short[] numMatrixParam =		new short[numMaterials];
		long[] offsetVectorParam = 		new long[numMaterials];
		long[] offsetTextureParam =		new long[numMaterials];
		long[] offsetMatrixparam =		new long[numMaterials];
		
		for(int i =0;i<numMaterials;i++) {
			shaderIndex[i] = 		in.getShort();
			numVectorParam[i] =		in.getShort();
			numTextureParam[i] =	in.getShort();
			numMatrixParam[i] =		in.getShort();
			offsetVectorParam[i] = 	in.getLong();
			offsetTextureParam[i] = in.getLong();
			offsetMatrixparam[i] = 	in.getLong();
		}
		
		for(int i=0;i<numMaterials;i++) {
			byte[] vectorBuf = new byte[0];
			byte[] textureBuf = new byte[0];
			byte[] matrixBuf = new byte[0];
			if(numVectorParam[i] != 0) {
				vectorBuf = new byte[24 * numVectorParam[i]];
				in.position((int)offsetVectorParam[i]);
				in.get(vectorBuf);
			}
			
			if(numTextureParam[i] != 0) {
				textureBuf = new byte[8 * numTextureParam[i]];
				in.position((int)offsetTextureParam[i]);
				in.get(textureBuf);
			}
			
			if(numMatrixParam[i] != 0) {
				matrixBuf = new byte[72 * numMatrixParam[i]];
				in.position((int)offsetMatrixparam[i]);
				in.get(matrixBuf);
			}
			
			materials.add(new PapaMaterial(getString(shaderIndex[i]).getValue(), numVectorParam[i], numTextureParam[i], numMatrixParam[i],
											vectorBuf, textureBuf, matrixBuf, this));
		}
	}
	
	private void readMeshes(ByteBuffer in) throws IOException {
		if(numMeshes == 0)
			return;
		in.position((int) offsetMeshTable);
		
		short[] vBuffer = 			new short[numMeshes];
		short[] iBuffer = 			new short[numMeshes];
		short[] materialGroups =	new short[numMeshes];
		long[] offset =				new long[numMeshes];
		
		for(int i =0;i<numMeshes;i++) {
			vBuffer[i] = 		in.getShort();
			iBuffer[i] =		in.getShort();
			materialGroups[i] =	in.getShort();
			in.getShort();
			offset[i] = 	in.getLong();
		}
		
		for(int i=0;i<numMeshes;i++) {
			byte[] buf = new byte[0];
			
			if(materialGroups[i] != 0) {
				buf = new byte[16 * materialGroups[i]];
				in.position((int)offset[i]);
				in.get(buf);
			}
			
			meshes.add(new PapaMesh(getVertexBuffer(vBuffer[i]),getIndexBuffer(iBuffer[i]),materialGroups[i], buf, this));
		}
	}
	
	private void readSkeletons(ByteBuffer in) throws IOException {
		if(numSkeletons == 0)
			return;
		in.position((int) offsetSkeletonTable);
		
		short[] bones = 			new short[numSkeletons];
		long[] offset =				new long[numSkeletons];
		
		for(int i =0;i<numSkeletons;i++) {
			bones[i] = 		in.getShort();
			in.getShort();
			in.getShort();
			in.getShort();
			offset[i] = 	in.getLong();
		}
		
		for(int i=0;i<numSkeletons;i++) {
			byte[] buf = new byte[132 * bones[i]];
			in.position((int)offset[i]);
			in.get(buf);
			
			skeletons.add(new PapaSkeleton(bones[i], buf, this));
		}
	}
	
	private void readModels(ByteBuffer in) throws IOException {
		if(numModels == 0)
			return;
		in.position((int) offsetModelTable);
		
		short[] name = 				new short[numModels];
		short[] skeleton = 			new short[numModels];
		short[] meshBindings = 		new short[numModels];
		float[][][] modelToScene = 	new float[numModels][4][4];
		long[] offset =				new long[numModels];
		
		for(int i =0;i<numModels;i++) {
			name[i] = 						in.getShort();
			skeleton[i] = 					in.getShort();
			meshBindings[i] = 				in.getShort();
			in.getShort();
			
			for(int y = 0 ;y<4;y++)
				for(int x = 0;x<4;x++)
					modelToScene[i][x][y] = in.getFloat();
			
			offset[i] = 					in.getLong();
		}
		
		for(int i=0;i<numModels;i++) {
			PapaMeshBinding[] bindings = readMeshBindings(in, meshBindings[i], offset[i]);
			
			models.add(new PapaModel(getString(name[i]).getValue(), getSkeleton(skeleton[i]), modelToScene[i], bindings, this));
		}
	}
	
	private PapaMeshBinding[] readMeshBindings(ByteBuffer in, short numMeshBindings, long offsetMeshBindingTable) {
		if(numMeshBindings == 0)
			return new PapaMeshBinding[0];
		in.position((int) offsetMeshBindingTable);
		
		PapaMeshBinding[] meshBindings = new PapaMeshBinding[numMeshBindings];
		
		short[] name = 				new short[numMeshBindings];
		short[] mesh = 				new short[numMeshBindings];
		short[] boneMappings = 		new short[numMeshBindings];
		float[][][] meshToModel = 	new float[numMeshBindings][4][4];
		long[] offset =				new long[numMeshBindings];
		
		for(int i =0;i<numMeshBindings;i++) {
			name[i] = 						in.getShort();
			mesh[i] = 						in.getShort();
			boneMappings[i] = 				in.getShort();
			in.getShort();
			
			for(int y = 0 ;y<4;y++)
				for(int x = 0;x<4;x++)
					meshToModel[i][x][y] = in.getFloat();
			
			offset[i] = 					in.getLong();
		}
		
		for(int i=0;i<numMeshBindings;i++) {
			
			byte[] buf = new byte[0];
			
			if(boneMappings[i] != 0) {
				buf = new byte[2 * boneMappings[i]];
				in.position((int)offset[i]);
				in.get(buf);
			}
			
			meshBindings[i] = new PapaMeshBinding(getString(name[i]).getValue(), getMesh(mesh[i]), boneMappings[i], meshToModel[i], buf);
		}
		
		return meshBindings;
	}
	
	private void readAnimations(ByteBuffer in) throws IOException {
		if(numAnimations == 0)
			return;
		in.position((int) offsetAnimationTable);
		
		short[] name = 				new short[numAnimations];
		short[] bones = 			new short[numAnimations];
		int[] frames = 				new int[numAnimations];
		int[] fps1 =				new int[numAnimations];
		int[] fps2 =				new int[numAnimations];
		long[] boneNameTableOffset =new long[numAnimations];
		long[] transformOffset =	new long[numAnimations];
		
		for(int i =0;i<numAnimations;i++) {
			name[i] = 						in.getShort();
			bones[i] = 						in.getShort();
			frames[i] = 					in.getInt();
			fps1[i] = 						in.getInt();
			fps2[i] = 						in.getInt();
			boneNameTableOffset[i] =			in.getLong();
			transformOffset[i] =			in.getLong();
		}
		
		for(int i=0;i<numAnimations;i++) {
			byte[] boneBuf = new byte[0];
			byte[] transformBuf = new byte[0];
			
			if(bones[i] != 0) {
				boneBuf = new byte[2 * bones[i]];
				in.position((int)boneNameTableOffset[i]);
				in.get(boneBuf);
			}
			
			if(frames[i] != 0) {
				transformBuf = new byte[28 * frames[i] * bones[i]];
				in.position((int)transformOffset[i]);
				in.get(transformBuf);
			}
			
			animations.add(new PapaAnimation(getString(name[i]).getValue(), bones[i], frames[i], fps1[i], fps2[i], boneBuf, transformBuf, this));
		}
	}
	public int indexOf(PapaString string) {
		return strings.indexOf(string);
	}
	public int indexOf(PapaTexture texture) {
		return textures.indexOf(texture);
	}
	public int indexOf(PapaVertexBuffer vBuffer) {
		return vBuffers.indexOf(vBuffer);
	}
	public int indexOf(PapaIndexBuffer iBuffer) {
		return iBuffers.indexOf(iBuffer);
	}
	public int indexOf(PapaMaterial mat) {
		return materials.indexOf(mat);
	}
	public int indexOf(PapaMesh mesh) {
		return meshes.indexOf(mesh);
	}
	public int indexOf(PapaSkeleton skeleton) {
		return skeletons.indexOf(skeleton);
	}
	public int indexOf(PapaModel model) {
		return models.indexOf(model);
	}
	public int indexOf(PapaAnimation animation) {
		return animations.indexOf(animation);
	}
	
	@Override
	public String toString() {
		return this.fileName;
	}
	
	private PapaFile openLinkedPapaFile(String fullPath) throws IOException{
		try {
			return PapaFile.readLinkedFile(fullPath,this);
		} catch (IOException e) {
			if(PapaFile.ERROR_IF_NOT_FOUND)
				throw new IOException("Failed to open linked file: "+e.getMessage());
			else {
				System.err.println("Failed to open linked file: "+e.getMessage());
				return null;
			}
		}
	}
	
	public byte[] getFileBytes() {
		if(fileBytes==null)
			throw new IllegalStateException("PapaFile is not built");
		return fileBytes;
	}
	
	public void build() {
		buildSuccessful=false;
		validateAll();
		if(testBuildErrorLevel(BuildNotification.ERROR))
			return;
		
		byte[] bytes = new byte[calcFileSize()];
		ByteBuffer fileBytes = ByteBuffer.wrap(bytes);
		fileBytes.order(ByteOrder.LITTLE_ENDIAN);
		fileBytes.position(HEADER_SIZE);
		
		byte[] header = new byte[PapaFile.HEADER_SIZE];
		ByteBuffer headerBuilder = ByteBuffer.wrap(header);
		headerBuilder.order(ByteOrder.LITTLE_ENDIAN);
		buildHeader(headerBuilder);
		
		int offsetIndex = headerBuilder.position();
		
		
		for(int i=0;i<9;i++) {
			int index = buildOrder[i];
			headerBuilder.position(offsetIndex + index * 8);
			
			if(components[index].size()!=0)
				headerBuilder.putLong((long)fileBytes.position());
			else
				headerBuilder.putLong(-1);
			
			buildComponent(components[index], fileBytes);
		}
		
		fileBytes.position(0);
		fileBytes.put(header);
		
		this.fileBytes = fileBytes.array();
		buildSuccessful=true;
	}
	
	private int calcFileSize() {
		int totalSize = PapaFile.HEADER_SIZE;
		for(int i=0;i<components.length;i++)
			for(PapaComponent comp : components[i])
				totalSize+=comp.componentSize();
		return totalSize;
	}
	
	private void validateComponentCount() {
		this.numTextures = 	(short) textures.size();
		this.numStrings = 	(short) strings.size();
		this.numVBuffers =	(short) vBuffers.size();
		this.numIBuffers =	(short) iBuffers.size();
		this.numMaterials =	(short) materials.size();
		this.numMeshes =	(short) meshes.size();
		this.numSkeletons =	(short) skeletons.size();
		this.numModels =	(short) models.size();
		this.numAnimations =(short) animations.size();
	}
	
	private BuildNotification[] validateAll() {
		this.maxErrorLevel=0;
		ArrayList<BuildNotification> notifications = new ArrayList<BuildNotification>();
		flushStringTable();
		for(int i=0;i<9;i++) {
			int index = buildOrder[i];
			for(BuildNotification n : validateComponent(components[index])) {
				this.maxErrorLevel = Math.max(n.getType(), this.maxErrorLevel);
				notifications.add(n);
			}
		}
		validateComponentCount();
		buildNotifications = notifications.toArray(new BuildNotification[notifications.size()]);
		return buildNotifications;
	}
	
	private ArrayList<BuildNotification> validateComponent(ArrayList<? extends PapaComponent> comp) {
		ArrayList<BuildNotification> notifications = new ArrayList<BuildNotification>();
		for(PapaComponent p : comp)
			for(BuildNotification b : p.validate())
				notifications.add(b);
		return notifications;
	}
	
	private void buildHeader(ByteBuffer b) {
		
		b.put("apaP".getBytes());
		b.putShort((short)minorVersion);
		b.putShort((short)majorVersion);
		
		b.putShort((short)numStrings);
		b.putShort((short)numTextures);
		b.putShort((short)numVBuffers);
		b.putShort((short)numIBuffers);
		
		b.putShort((short)numMaterials);
		b.putShort((short)numMeshes);
		b.putShort((short)numSkeletons);
		b.putShort((short)numModels);
		
		b.putShort((short)numAnimations);
		b.put(this.fileSignature.getBytes());
		for(int i = 0;i<6 - this.fileSignature.length(); i++) {
			b.put((byte) 0); // pad
		}
	}
	
	private void buildComponent(ArrayList<? extends PapaComponent> comp, ByteBuffer b) {
		int currentSize = 0;
		
		for(PapaComponent p : comp) {
			p.build();
			currentSize+=p.headerSize();
		}
		
		for(PapaComponent p : comp) {
			p.applyOffset(currentSize + b.position());
			currentSize+=ceilEight(p.bodySize());
		}
		
		for(PapaComponent p : comp)
			b.put(p.getHeaderBytes());
		for(PapaComponent p : comp) {
			b.put(p.getDataBytes());
			b.position(ceilEight(b.position()));
		}
	}
	
	private void flushStringTable() {
		for(PapaString s : strings)
			s.flush();
		strings.clear();
	}
	
	public int getOrMakeString(String s) {
		if(s.equals(""))
			return -1;
		for(int i = 0;i<strings.size();i++)
			if(strings.get(i).getValue().equals(s)) {
				return i;
			}
		
		strings.add(new PapaString(s,this));
		numStrings++;
		return strings.size() - 1;
	}
	
	@Override
	public void flush() {
		if(linkedFiles==null)
			return;
		PapaFile[] entries = getLinkedFiles(); // error if you directly access values() due to child files modifying the parent files
		for(PapaFile p : entries)
			p.flush();
		if(isLinked)
			detach();
		
		for(ArrayList<? extends PapaComponent> al : components)
			for(PapaComponent p : al)
				p.flush();
		strings = null;
		textures = null;
		fileBytes = null;
		fileName = null;
		linkedFiles = null;
		parentFile = null;
	}
	
	@Override
	protected void overwriteHelper(PapaComponent other) {// TODO
		
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj == null || !(obj.getClass().equals(PapaFile.class)))
			return false;
		if(obj==this)
			return true;
		
		PapaFile p = (PapaFile) obj;
		
		return 		(p.fileSize 		== 	fileSize)
				&&	(p.numAnimations 	== 	numAnimations)
				&&	(p.numIBuffers	 	== 	numIBuffers)
				&&	(p.numMaterials 	== 	numMaterials)
				&&	(p.numMeshes 		== 	numMeshes)
				&&	(p.numModels 		== 	numModels)
				&&	(p.numSkeletons 	== 	numSkeletons)
				&&	(p.numStrings	 	== 	numStrings)
				&&	(p.numTextures 		== 	numTextures)
				&&	(p.numVBuffers 		== 	numVBuffers)
				&&	(p.isLinked 		== 	isLinked)
				&&	Objects.equals(p.filePath, filePath)
				&&	Objects.equals(p.fileName, fileName)
				&&	Objects.equals(p.strings, strings)
				&&	Objects.equals(p.textures, textures)
				&&	Objects.equals(p.linkedFiles, linkedFiles);
		
	}
	
	@Override
	public int hashCode() { //TODO
		final int prime = 31;
		int result = 17;
		result = prime * result + (int) (fileSize ^ fileSize>>>32);
		result = prime * result + (int) numAnimations;
		result = prime * result + (int) numIBuffers;
		result = prime * result + (int) numMaterials;
		result = prime * result + (int) numMeshes;
		result = prime * result + (int) numModels;
		result = prime * result + (int) numSkeletons;
		result = prime * result + (int) numStrings;
		result = prime * result + (int) numTextures;
		result = prime * result + (int) numVBuffers;
		result = prime * result + 		(isLinked ? 1 : 0);
		result = prime * result + 		filePath.hashCode();
		result = prime * result + 		fileName.hashCode();
		result = prime * result + 		strings.hashCode();
		result = prime * result + 		textures.hashCode();
		result = prime * result + 		linkedFiles.hashCode();
		return result;
	}
	//TODO: add multiple search paths
	/*
	public static String getSearchPaths()
	public static void addSearchPath(File f)
	 */
	
	public static File getPlanetaryAnnihilationDirectory() {
		return PA_ROOT_DIR;
	}
	
	public static void setPADirectory(File f) {
		PA_ROOT_DIR = f;
		if(f==null) {
			PA_ROOT_DIR_STRING = null;
			return;
		}
		PA_ROOT_DIR_STRING = f.getAbsolutePath().replace('\\', '/');
	}

	@Override
	protected BuildNotification[] validate() {
		return validateAll();
	}

	@Override
	protected int headerSize() {
		return PapaFile.HEADER_SIZE;
	}

	@Override
	protected int bodySize() {
		return (int) (fileSize - headerSize());
	}

	@Override
	protected void applyOffset(int offset) {}

	@Override
	public void detach() {
		ensureLink();
		parentFile.unlinkFile(this, false); // hard coded for now
		parentFile = null;
		isLinked = false;
	}

	@Override
	protected void setParent(PapaFile newParent) {
		if(isLinked)
			detach();
		isLinked = true;
		parentFile = newParent;
	}

	@Override
	public PapaFile duplicate() {
		return null;
	}

	public PapaFile getEmptyCopy() {
		PapaFile p = new PapaFile();
		p.setFileLocation(fileLocation);
		return p;
	}

	//TODO: a dependency is defined as any instance which this PapaComponent uses in any way except for parents
	// or: if you were to delete the dependency, you should likely also delete this PapaComponent unless you plan on replacing that dependency.
	// Dependents and Dependencies should be symmetric
	@Override
	public PapaComponent[] getDependencies() {
		return linkedFiles.values().toArray(new PapaFile[linkedFiles.size()]);
	}

	//TODO: a dependent is defined as any instance which uses this PapaComponent in any way.
	@Override
	public PapaComponent[] getDependents() {
		ArrayList<PapaComponent> dependents = new ArrayList<PapaComponent>();
		if(isLinkedFile()) {
			dependents.add(getParent());
			for(PapaComponent p : getParent().getAllDependentsFor(this))
				dependents.add(p);
		}
		return dependents.toArray(new PapaComponent[dependents.size()]);
	}

	@Override
	protected boolean isDependentOn(PapaComponent other) {
		return linkedFiles.containsValue(other);
	}
	
	public PapaComponent[] getAllDependentsFor(PapaComponent comp) {
		ArrayList<PapaComponent> fileComponents = getAllComponents();
		ArrayList<PapaComponent> dependents = new ArrayList<PapaComponent>();
		for(PapaComponent p : fileComponents)
			if(p.isDependentOn(comp))
				dependents.add(p);
		
		if(isLinkedFile())
			for(PapaComponent p : getParent().getAllDependentsFor(comp))
				dependents.add(p);
		
		return dependents.toArray(new PapaComponent[dependents.size()]);
	}
	
	public PapaComponent[] getOwnedComponents(PapaComponent[] comp) {
		ArrayList<PapaComponent> components = new ArrayList<PapaComponent>();
		for(PapaComponent p : comp)
			if(p.getParent()==this)
				components.add(p);
		return components.toArray(new PapaComponent[components.size()]);
	}

	private ArrayList<PapaComponent> getAllComponents() {
		ArrayList<PapaComponent> comp = new ArrayList<PapaComponent>();
		for(ArrayList<? extends PapaComponent> list : components)
			comp.addAll(list);
		return comp;
	}
	
	public void printContents() {
		for(int i=0;i<components.length;i++)
			System.out.println(COMPONENT_NAMES[i]+": "+components[i].size());
	}
	
	public static class BuildNotification {
		public static final int INFO = 0, WARNING = 1, ERROR = 2;
		private static final String[] errorNames = new String[] {"INFO","WARNING","ERROR"};
		
		private PapaComponent component;
		private int type = 0;
		private String message;
		
		public BuildNotification(PapaComponent comp, int type, String message) {
			this.type = type;
			this.message = message;
		}
		
		public PapaComponent getSource() {
			return component;
		}
		
		public int getType() {
			return this.type;
		}
		
		public String getMessage() {
			return this.message;
		}
		
		public String toString() {
			return errorNames[type]+": "+message;
		}
		
	}

}
