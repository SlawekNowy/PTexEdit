/*
 *  This file is part of PTexEdit 
 * 
 *  Texture editor for Planetary Annihilation's papa files.
 *  Copyright (C) 2020 Marcus Der <marcusder@hotmail.com>
 * 
 *  PTexEdit is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  PTexEdit is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License
 *  along with PTexEdit.  If not, see <https://www.gnu.org/licenses/>.
 */
package editor;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
//import java.util.zip.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.*;
import javax.swing.table.DefaultTableModel;

import papafile.*;
import papafile.PapaFile.BuildNotification;
import papafile.PapaTexture.*;

public class FileHandler {
	
	private static LinkedBlockingQueue<Runnable> queue;
	private static ThreadPoolExecutor executor;
	private static Object lock = new Object();
	private static Vector<Future<?>> tasks;
	private static FileNameExtensionFilter[] imageFilters;
	private static FileNameExtensionFilter papaFilter = new FileNameExtensionFilter("Planetary Annihilation File (*.papa)", "papa");
	private static Vector<RunnableHandle> submittedTasks;
	
	private static void initializeThreadPool() {
		 queue = new LinkedBlockingQueue<Runnable>();
		 executor = new ThreadPoolExecutor(4, 4, 500, TimeUnit.MILLISECONDS, queue);
		 tasks = new Vector<Future<?>>();
		 submittedTasks = new Vector<RunnableHandle>();
	}
	
	static {
		initializeThreadPool();
		generateFileNameFilters();
	}
	
	
	public static void readFiles(File f, ImportInterface importInterface, ImportInfo info) throws InterruptedException {
		readFiles(f, importInterface, info, true, false);
	}
	
	public static void readFiles(File f, ImportInterface importInterface, ImportInfo info, boolean recursive, boolean wait) throws InterruptedException {
		synchronized(executor) { // do not let more than one thread call readFiles at the same time.
			if(!f.exists()) {
				Editor.showError("File does not exist", "IO Error", new Object[] {"Ok"}, "Ok");
				return;
			}
			readFilesInternal(f, importInterface, info,recursive, wait);
		}
	}
	
	private static int determineMode(File f) {
		File[] files = f.listFiles();
		int papa = 0;
		int image = 0;
		int unknown = 0;
		int total = 0;
		for(File file : files) {
			total++;
			if(! file.isDirectory()) {
				if(PAPA_INTERFACE.filter(file))
					papa++;
				else if(IMAGE_INTERFACE.filter(file)){
					image++;
				} else {
					unknown++;
				}
			}
		}
		if(total == unknown) // nothing but unrecognized
			return 0;
		if(unknown!=0)
			return 1;
		if(papa!=0 && image==0)
			return 2;
		if(papa==0 && image!=0)
			return 3;
		
		return 1;
	}
	
	private static void readFilesInternal(File f, ImportInterface importInterface, ImportInfo info,boolean recursive, boolean wait) throws InterruptedException {
		
		info.resetStatistics();  // in case of reuse
		ArrayList<StreamableData> toParse = getValidInputs(f, importInterface, info, recursive);
		info.setTotalFileCount(toParse.size());
		
		if(f.isDirectory()) {
			if(! info.isInternalMode() && ! checkMassInput(f,toParse,importInterface)) {
				return;
			}
			info.setDirectoryMode();
		} else {
			if(toParse.size()==0)
				importInterface.rejectFile(f, info, f.getName()+" is not a"+(importInterface == IMAGE_INTERFACE ? "n ":" ")+importInterface.getType() + " file");
		}
		
		toParse.stream().forEach((StreamableData sd) -> {
			try {
				RunnableHandle r = importInterface.getRunnable(sd.getHandle(), sd.getStream(), sd.getFile(), info);
				tasks.add(executor.submit(r));
				submittedTasks.add(r);
			} catch (IOException e1) {
				throw new IllegalArgumentException(e1);
			}
		});
		
		if(wait || info.isDirectoryMode()) {
			try {
				for(Future<?> result : tasks)
					result.get();
			} catch (InterruptedException e) {
				throw e;
			} catch (ExecutionException e) {
				throw new RuntimeException(e);
			} catch (CancellationException e) {} 
			finally {
				synchronized(tasks) {
					tasks.clear();
				}
			}
			if( ! info.isInternalMode() && info.isDirectoryMode() && (info.getNumRejectedFiles()!=0 || info.getNumAcceptedFiles() == 0)) {
				JComponent t = getErrorTable(info.getRejectedFiles(),info.getRejectedFileReasons());
				if(info.getNumAcceptedFiles()==0) {
					if(info.getNumRejectedFiles()!=0)
						Editor.showError(t, "Unable to read any "+importInterface.getType()+" files from the folder", new Object[] {"Ok"}, "Ok");
					else
						Editor.showError("Unable to find any "+importInterface.getType()+" files in the folder", "Invalid Input", new Object[] {"Ok"}, "Ok");
				} else {
					Editor.showError(t, "Import Error Summary", new Object[] {"Ok"}, "Ok");
				}
			}
		}
	}
	
	private static JComponent getErrorTable(File[] rejectedFiles, String[] reasons) {
		JTable j = new JTable();
		JScrollPane jsp = new JScrollPane(j, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		DefaultTableModel model = (DefaultTableModel) j.getModel();
		model.addColumn("File");
		model.addColumn("Reason");
		jsp.setPreferredSize(new Dimension(950,150));
		j.getColumnModel().getColumn(0).setPreferredWidth(300);
		j.getColumnModel().getColumn(1).setPreferredWidth(650);
		for(int i =0;i<rejectedFiles.length;i++)
			model.addRow(new Object[] {rejectedFiles[i].getName(),reasons[i]});
		
		return jsp;
		
	}

	private static ArrayList<StreamableData> getValidInputs(File f, ImportInterface importInterface, ImportInfo info, boolean recursive) throws InterruptedException {
		try {
			ArrayList<StreamableData> list = new ArrayList<StreamableData>();
			getValidFiles(list,f,importInterface, info,recursive);
			return list;
		} catch (InterruptedException e) {
			throw e;
		}
	}
	
	private static void getValidFiles(ArrayList<StreamableData> list, File f, ImportInterface importInterface, ImportInfo info, boolean recursive) throws InterruptedException {
		
		if(Thread.interrupted())
			throw new InterruptedException();
		if(f.isDirectory() && recursive) {
			File [] files = f.listFiles();
			
			for(File f2 : files)
				getValidFiles(list,f2,importInterface, info, recursive);
		} else if(importInterface.filter(f)) {
			list.add(new StreamableFile(f));
			info.activityListener.onFoundAcceptableFile(f, list.size());
		}
		
	}
	
	public static void cancelActiveTask() {
		executor.shutdownNow();
		try {
			executor.awaitTermination(60, TimeUnit.MINUTES);
		} catch (InterruptedException e) {}
		
		synchronized(tasks) {
			for(Future<?> f : tasks)
				f.cancel(false);
		}
		synchronized(submittedTasks) {
			for(RunnableHandle r : submittedTasks)
				r.countDown();
		}
		
		initializeThreadPool();
	}
	
	private static boolean checkMassInput(File source, ArrayList<StreamableData> files, ImportInterface importInterface) {
		int count = files.size();
		if(count > 100) {
			synchronized(lock) {
				int result = Editor.showError("You are attempting to open " + count + " " + importInterface.getType()
						+ " files.\nAre you sure you want to continue?\n(you may want to use tools => convert folder)", source.getAbsolutePath(), new Object[] {"Yes","No" }, "No");
				if(result!=0)
					return false;
			}
		}
		return true;
	}
	
	private static String getExtension(File f) {
		String s = f.getPath();
		int index = s.lastIndexOf('.');
		
		if(index ==-1)
			return "";
		return s.substring(index+1).toLowerCase();
	}
	
	public static final ImportInterface PAPA_INTERFACE = new ImportInterface() {

		@Override
		public boolean filter(File file) {
			return file.getPath().endsWith("papa");
		}

		@Override
		public RunnableHandle getRunnable(Handle handle, InputStream stream, File file, ImportInfo info) {
			return new RunnableHandle(handle) {
				@Override
				public void run() {
					info.onStartProcessFile(file, Thread.currentThread().getName());
					String path = file.getPath();
					PapaFile papaFile;
					try {
						papaFile = new PapaFile(stream, path);
					} catch (IOException e) {
						log("Error on file "+file,e);
						rejectFile(file, info, e.getMessage());
						return;
					} finally {
						PAPA_INTERFACE.closeStream(stream);
						countDown();
					}
					
					if(papaFile.getNumTextures()==0 && ! Editor.ALLOW_EMPTY_FILES) {
						rejectFile(file, info, "Papa file contains no images");
						return;
					}
					
					info.accept(file, papaFile);
				}
				
			};
		}
		
		@Override
		public String getType() {
			return "papa";
		}
	};
	
	public static final ImportInterface IMAGE_INTERFACE = new ImportInterface() {
		
		private String[] extensions = ImageIO.getReaderFileSuffixes();

		{
			Arrays.sort(extensions);
		}
		
		@Override
		public boolean filter(File file) {
			return Arrays.binarySearch(extensions, getExtension(file)) >= 0;
		}

		@Override
		public RunnableHandle getRunnable(Handle handle, InputStream stream, File file, ImportInfo info) {
			return new RunnableHandle(handle) {
				@Override
				public void run() {
					info.onStartProcessFile(file, Thread.currentThread().getName());
					BufferedImage b;
					PapaFile p;
					
					ImmutableTextureSettings settings = info.getTextureSettings();
					boolean hasTarget = settings.linkEnabled;
					boolean link = settings.linkMethod==TextureSettings.LINK_TYPE_REFERENCE && hasTarget;
					
					if(hasTarget) {
						p = settings.linkTarget;
					} else {
						p = new PapaFile();
						p.setFileLocation(file);
						p.setSignature(info.getSignature());
					}
					
					try {
						if(link && PapaFile.getPlanetaryAnnihilationDirectory()==null)
							throw new IOException("Link is not available until the media directory is set.");
						
						b = ImageIO.read(stream);
						PapaTexture t = new PapaTexture(b, info.getTextureSettings(), null, file.getName());
						if(link) {
							if(!t.getName().startsWith("/"))
	        					t.setName("/"+t.getName()); // add implicit /
							p.generateLinkedTexture(t);
						}
						else
							t.attach(p);
						
					} catch (IOException | NullPointerException e) { // NPE is quick solution for unload while converting
						e.printStackTrace();
						rejectFile(file, info, e.getClass().getName()+": "+e.getMessage());
						return;
					} finally {
						IMAGE_INTERFACE.closeStream(stream);
						countDown();
					}
					
					info.accept(file, p);
				}
				
			};
		}

		@Override
		public String getType() {
			return "image";
		}
	};
	
	public static abstract class ImportInterface {
		public abstract boolean filter(File file);
		public abstract RunnableHandle getRunnable(Handle handle, InputStream stream, File input, ImportInfo info);
		public abstract String getType();
		public final void rejectFile(File file, ImportInfo info, String reason) {
			info.reject(file, reason);
			if( ! info.isDirectoryMode() && ! info.isInternalMode()) {
				synchronized(lock) {
					Editor.showError(reason, "Error: " + file.getName(), new Object[] {"Ok"}, "Ok");
				}
			}
		}
		private final void closeStream(InputStream s) {
			try {
				s.close();
			} catch (IOException e) {
				System.err.println("Memory leak: Failed to close stream "+s);
			}
		}
	}
	
	private static abstract class RunnableHandle implements Runnable {
		private final Handle handle;
		private AtomicBoolean counted = new AtomicBoolean();
		public RunnableHandle(Handle handle) {
			this.handle=handle;
			if(handle==null)
				counted.set(true);
		}
		
		public synchronized void countDown() {
			if(counted.compareAndSet(false, true))
				handle.countDown();
		}
		
	}
	
	public static class ImportInfo {
		private static ActivityListener defaultResultManager = new ActivityListener() {
			@Override
			public void onRejectFile(File f, String reason) {}
			@Override
			public void onAcceptFile(PapaFile p) {}
		};
		
		private boolean directoryMode, internalMode;
		private AtomicInteger acceptedFiles = new AtomicInteger();
		private AtomicInteger rejectedFiles  = new AtomicInteger();
		private ImmutableTextureSettings importSettings;
		private ActivityListener activityListener = ImportInfo.defaultResultManager;
		private String signature = "";
		
		private Vector<File> rejectedFileList = new Vector<File>();
		private Vector<String> rejectedFileReasons = new Vector<String>();
		
		public void setTextureSettings(ImmutableTextureSettings t) {
			if(importSettings!=null)
				throw new IllegalStateException("Cannot modify TextureImportSettings");
			importSettings = t;
		}
		
		public void setSignature(String signature) {
			this.signature = signature;
		}
		
		public String getSignature() {
			return this.signature;
		}
		
		public void onStartProcessFile(File file, String name) {
			activityListener.onStartProcessFile(file, name);
		}

		public void setTotalFileCount(int size) {
			activityListener.onGotTotalFiles(size);
		}

		public ImmutableTextureSettings getTextureSettings() {
			return importSettings;
		}
		
		public void accept(File f, PapaFile p) {
			activityListener.onEndProcessFile(f, Thread.currentThread().getName(), true);
			activityListener.onAcceptFile(p);
			acceptedFiles.addAndGet(1);
		}
		
		public void reject(File f, String reason) {
			rejectedFileList.add(f);
			rejectedFileReasons.add(reason);
			activityListener.onEndProcessFile(f, Thread.currentThread().getName(), false);
			activityListener.onRejectFile(f, reason);
			rejectedFiles.addAndGet(1);
		}
		
		public void setDirectoryMode() {
			directoryMode = true;
		}
		
		public boolean isDirectoryMode() {
			return directoryMode;
		}
		
		public void setActivityListener(ActivityListener m) {
			this.activityListener = m;
		}
		
		public int getNumRejectedFiles() {
			return rejectedFiles.get();
		}
		
		public int getNumAcceptedFiles() {
			return acceptedFiles.get();
		}
		
		public File[] getRejectedFiles() {
			return rejectedFileList.toArray(new File[rejectedFileList.size()]);
		}
		
		public String[] getRejectedFileReasons() {
			return rejectedFileReasons.toArray(new String[rejectedFileReasons.size()]);
		}
		
		public void setInternalMode(boolean internal) {
			this.internalMode = internal;
		}
		
		public boolean isInternalMode() {
			return this.internalMode;
		}
		
		private void resetStatistics() {
			directoryMode = false;
			acceptedFiles.set(0);
			rejectedFiles.set(0);
			rejectedFileList.clear();
			rejectedFileReasons.clear();
		}
		
		public static abstract class ActivityListener {
			public void onFoundAcceptableFile(File f, int currentTotal) {};
			public void onGotTotalFiles(int totalFiles) {};
			public void onStartProcessFile(File f, String threadName) {};
			public void onEndProcessFile(File f, String threadName, boolean success) {}
			public abstract void onAcceptFile(PapaFile p);
			public abstract void onRejectFile(File f, String reason);
		}
	}
	
	private static abstract class StreamableData {
		public abstract File getFile();
		public abstract Handle getHandle();
		public abstract InputStream getStream() throws IOException;
	}
	
	private static class StreamableFile extends StreamableData {
		private final File input;
		
		public StreamableFile(File f) {
			input = f;
		}
		
		@Override
		public InputStream getStream() throws FileNotFoundException {
			return new FileInputStream(input);
		}

		@Override
		public File getFile() {
			return input;
		}
		
		@Override
		public Handle getHandle() {
			return null;
		}
		
	}
	
	/*private static class StreamableZipEntry extends StreamableData { //TODO: this currently causes a memory leak as nothing ever closes the zipFile
	 // fixing would require that a reference to the zip file is to be stored and returned, then closed when the method ends
		private final File input;
		private final ZipFile zipFile;
		private final ZipEntry entry;
		private final Handle zipHandle;
		
		public StreamableZipFile(File path, ZipFile file, ZipEntry entry, Handle zipHandle) {
			this.input = path;
			this.zipFile = file;
			this.entry = entry;
			this.zipHandle = zipHandle;
		}

		@Override
		public File getFile() {
			return input;
		}

		@Override
		public InputStream getStream() throws IOException {
			return zipFile.getInputStream(entry);
		}
		
		@Override
		public Handle getHandle() {
			return zipHandle;
		}
	}
	
	Zip has no file heirarchy in java,  all entries are at the root.
	names of zip entries in PTexEdit zip file (with extra new folder + license copy):
	PTexEdit/LICENSE
	PTexEdit/LICENSE-3RD-PARTY
	PTexEdit/New folder/
	PTexEdit/New folder/LICENSE
	PTexEdit/PTexEdit.jar
	
	*/
	
	private static class Handle {
		private Closeable handle;
		private AtomicBoolean isStarted = new AtomicBoolean();
		private AtomicInteger count = new AtomicInteger();
			
		public Handle(Closeable c) {
			this.handle=c;
		}
		
		public synchronized void start() {
			this.isStarted.set(true);
		}
		
		public synchronized void countUp() {
			if(isStarted.get())
				throw new IllegalStateException("Cannot add elements to this handle after it is started");
			count.incrementAndGet();
		}
		
		public synchronized void countDown() {
			if( ! isStarted.get())
				throw new IllegalStateException("Cannot remove elements from this handle before it is started");
			if(count.decrementAndGet() == 0) {
				closeStream();
			}
		}
		
		private void closeStream() {
			try {
				handle.close();
			} catch (IOException e) {
				System.err.println("Failed to close handle: "+handle);
			}
		}
	}
	
	public static void log(String message, Exception e) {
		System.err.println(message);
		e.printStackTrace();
	}
	
	public static boolean checkIsInPA(File f) {
		try {
			return f.getCanonicalPath().contains(PapaFile.getPlanetaryAnnihilationDirectory().getCanonicalPath() + File.separator);
		} catch (IOException e) {
			return false;
		}
	}
	
	public static boolean isPapa(File f) {
		return PAPA_INTERFACE.filter(f);
	}
	
	public static ImportInterface determineInterface(File f) {
		if(!f.exists())
			throw new IllegalArgumentException("File does not exist");
		if(f.isFile())
			if(isPapa(f))
				return PAPA_INTERFACE;
			else
				return IMAGE_INTERFACE;
		
		int mode = determineMode(f);
		
		if(mode==0) {
			Editor.showError("No recognized files in the directory", "Invalid Directory", new Object[] {"Ok"}, "Ok");
			return null;
		}
		if(mode==1) {
			int result = Editor.optionBox("What mode would you like to open the directory in?", f.getAbsolutePath(), new Object[] {"Papa","Image","Cancel"}, "Cancel");
			if(result==-1 || result == 2)
				return null;
			mode = result + 2;
		}
		if(mode==2)
			return PAPA_INTERFACE;
		else
			return IMAGE_INTERFACE;
	}
	
	private static File enforceExtension(File input, FileNameExtensionFilter filter) {
		String[] extensions = filter.getExtensions();
		String selectedExtension = null;
		String path = input.getPath();
		String pathLower = path.toLowerCase();
		for(String s : extensions)
			if(pathLower.endsWith(s))
				selectedExtension = "."+s;
		
		if(selectedExtension == null) {
			selectedExtension = "."+extensions[0];
		}
		
		if(!path.endsWith(selectedExtension)) {
			if(pathLower.endsWith(selectedExtension))
				return new File(path.substring(0,path.length()-selectedExtension.length())+selectedExtension);
			else
				return new File(path+selectedExtension);
		}
		return input;
		
	}
	
	public static File replaceExtension(File input, FileNameExtensionFilter filter) {
		String[] extensions = filter.getExtensions();
		String path = input.getPath();
		String pathLower = path.toLowerCase();
		for(String s : extensions)
			if(pathLower.endsWith(s))
				return input;
		
		if(!pathLower.endsWith(extensions[0])) {
			int index = path.lastIndexOf('.');
			if(index==-1)
				index = path.length();
			return new File(path.substring(0,index)+"."+extensions[0]);
		}
		return input;
	}

	public static void writeFile(PapaFile target, File location) throws IOException {
		
		FileOutputStream fos = null;
		try {
			target.build();
			if( ! target.buildSuccessful())
				throw new IOException(generateBuildError(target.getBuildNotifications()));
			
			location.createNewFile();
			fos = new FileOutputStream(location);
			byte[] data = target.getFileBytes();
			fos.write(data);
			if(!Editor.SUPPRESS_WARNINGS)
				if (target.testBuildErrorLevel(1))
					throw new UnsupportedEncodingException(generateBuildWarnings(target.getBuildNotifications())); // build succeeded, but warnings
		} catch (IOException e1) {
			throw e1;
		} finally {
			try {
				if(fos!=null)
					fos.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}
	
	private static String generateBuildWarnings(BuildNotification[] notifications) {
		String error = "The file produced the following warnings when being built:\n";
		for(BuildNotification n : notifications) {
			error+=n+"\n";
		}
		return error;
	}
	
	private static String generateBuildError(BuildNotification[] notifications) {
		String error = "The following problems occured when attempting to build the file:\n";
		for(BuildNotification n : notifications) {
			if(n.getType()==BuildNotification.ERROR)
				error+=n+"\n";
		}
		return error;
	}
	
	public static void exportImage(PapaTexture tex, File file) throws IOException {
		String ext = file.getName().substring(file.getName().lastIndexOf(".") + 1);
		BufferedImage toWrite = tex.getImage();
		if(ext.equals("jpeg") || ext.equals("jpg") || ext.equals("bmp") || ext.equals("wbmp")) {
			toWrite = toModel(toWrite,BufferedImage.TYPE_INT_RGB);
		}
		try {
			if(!ImageIO.write(toWrite, ext, file))
				throw new IOException("File extension "+ext+" is not supported for export.");
		} catch (IOException e) {
			throw e;
		}
	}
	
	private static BufferedImage toModel(BufferedImage input, int model) {
		BufferedImage tmp = new BufferedImage(input.getWidth(),input.getHeight(),model);
		int[] data = new int[input.getWidth()*input.getHeight()];
		input.getRGB(0, 0, input.getWidth(), input.getHeight(), data, 0, input.getWidth());
		tmp.setRGB(0, 0, input.getWidth(), input.getHeight(), data, 0, input.getWidth());
		return tmp;
	}
	
	public static FileNameExtensionFilter[] getImageFilters() {
		return imageFilters;
	}
	
	public static FileNameExtensionFilter getImageFilter(String name) {
		for(FileNameExtensionFilter f : imageFilters) {
			if(f.getExtensions().length != 1)
				continue;
			if(f.getExtensions()[0].equals(name))
				return f;
		}
		for(FileNameExtensionFilter f : imageFilters)
			for(String s : f.getExtensions())
				if(s.equals(name))
					return f;
		return null;
	}
	
	public static FileNameExtensionFilter getPapaFilter() {
		return papaFilter;
	}
	
	private static void generateFileNameFilters() {
		String[] filters = ImageIO.getWriterFileSuffixes();
		imageFilters = new FileNameExtensionFilter[filters.length + 1];
		imageFilters[0] = new FileNameExtensionFilter("Image Files (*."+String.join(", *.", ImageIO.getReaderFileSuffixes())+")", ImageIO.getReaderFileSuffixes());
		for(int i = 0;i<filters.length;i++)
			imageFilters[i + 1] = new FileNameExtensionFilter(filters[i].toUpperCase()+" Files (*."+filters[i]+")", filters[i]);
	}

	public static void saveFileTo(PapaFile target, File selectedFile, boolean force) throws IOException {
		File file = enforceExtension(selectedFile, papaFilter);
		
		if(!force && file.exists())
			if(Editor.optionBox(file.getName() +" already exists.\nDo you want to replace it?", "Confirm Save As", new Object[] {"Yes","No"}, "Yes") != 0)
				return;
		try {
			writeFile(target,file);
		} catch (UnsupportedEncodingException e) {
			target.setFileLocation(file);
			throw e;
		}
		target.setFileLocation(file);
	}

	public static void exportImageTo(PapaTexture activeTexture, File selectedFile, FileNameExtensionFilter filter, boolean force) throws IOException {
		File file = enforceExtension(selectedFile,filter);
		
		int index = selectedFile.getName().lastIndexOf(".") + 1;
		String ext = "";
		if(index!=0)
			ext = selectedFile.getName().substring(index).toLowerCase();
		
		if(file==null) {
			Editor.showError("Extension "+ext+" does not match any known extensions", "Export error", new Object[] {"Ok"}, "Ok");
			return;
		}
		if(!force && file.exists())
			if(Editor.showError(file.getName() +" already exists.\nDo you want to replace it?", "Confirm Export", new Object[] {"Yes","No"}, "No") != 0)
				return;
		exportImage(activeTexture,file);
	}
	
}
