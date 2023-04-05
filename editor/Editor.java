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

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import javax.swing.tree.*;
import javax.swing.border.Border;
import javax.swing.event.*;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;

import com.github.memo33.jsquish.Squish.CompressionMethod;

import editor.FileHandler.*;
import editor.FileHandler.ImportInfo.ActivityListener;
import javafx.util.Pair;
import papafile.*;
import papafile.PapaTexture.*;

import java.io.*;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;

public class Editor extends JFrame {
	
	public 	static boolean ALLOW_EMPTY_FILES = false;
	public 	static boolean SUPPRESS_WARNINGS = false;
	public 	static boolean WRITE_TO_OUTPUT = false;
	private static final long serialVersionUID = 894467903207605180L;
	private static final String APPLICATION_NAME = "PTexEdit";
	private static final String VERSION_STRING = "0.4";
	private static final String BUILD_DATE = "April 4, 2023";
	private static final File settingsFile = new File(System.getProperty("user.home") + 
						File.separatorChar+APPLICATION_NAME+File.separatorChar+APPLICATION_NAME+".properties");
	private static Editor APPLICATION_WINDOW;
	private static final BufferedImage checkerboard = loadImageFromResources("checkerboard64x64.png");
	private static final BufferedImage icon = loadImageFromResources("icon.png");
	private static final BufferedImage iconSmall = loadImageFromResources("iconSmall.png");
	private static final ImageIcon imageIcon = new ImageIcon(icon);
	private static final ImageIcon imgPapafile = loadIconFromResources("papafile.png");
	private static final ImageIcon imgPapafileImage = loadIconFromResources("papafileImage.png");
	private static final ImageIcon imgPapafileLinked = loadIconFromResources("papafileLinked.png");
	private static final ImageIcon imgPapafileError = loadIconFromResources("papafileError.png");
	private static final ImageIcon imgPapafileNoLinks = loadIconFromResources("papafileNoLinks.png");
	private static final ImageIcon imgPapaFileUnsaved = loadIconFromResources("papafileUnsaved.png");
	private static final ImageIcon plusIcon = loadIconFromResources("plus.png");
	private static final ImageIcon minusIcon = loadIconFromResources("minus.png");
	private static final ImageIcon upArrowIcon = loadIconFromResources("upArrow.png");
	private static final Properties prop = new Properties();
	private static final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
	//private static int maxThreads;
	private JPanel contentPane;
	
	private PapaFile activeFile = null;
	private PapaTexture activeTexture = null;
	private Set<PapaComponent> dependencies = Collections.newSetFromMap(new IdentityHashMap<>()), dependents = Collections.newSetFromMap(new IdentityHashMap<>());
	private String defaultSignature = "";
	private int associationState = 0; // 0 = not windows, 1 = un associated, 2 = associated
	
	private ImagePanel imagePanel;
	private ConfigPanelTop configSection1;
	private ConfigPanelBottom configSection2;
	private ConfigPanelSelector configSelector;
	private RibbonPanel ribbonPanel;
	 
	private ClipboardListener clipboardOwner;
	
	private JPanel config, canvas;
	private JSplitPane mainPanel;
	private JScrollBar horizontal, vertical;
	private JPanel box;
	private MenuBar menu;
	
	private PapaOptions papaOptions;
	private BatchConvert batchConvert;
	
	private int loadCounter = 0;
	
	public static void main(String[] args) {
		applyPlatformChanges();
		
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				APPLICATION_WINDOW = new Editor();
				
				readAndApplyConfig();
				addShutdownHooks();
				addUncaughtExceptionHandler();
				
				
				
				APPLICATION_WINDOW.setTitle(APPLICATION_NAME);
				APPLICATION_WINDOW.setVisible(true);
				
				if(args.length != 0) {
					APPLICATION_WINDOW.startOperation();
					try {
						File[] files = new File[args.length];
						for(int i = 0;i<args.length;i++) {
							files[i] = new File(args[i]).getCanonicalFile();
							
						}
						APPLICATION_WINDOW.readAll(null, files);
					} catch (IOException e) {
						showError("Failed to open file: "+e.toString(), "Error", new Object[] {"OK"}, "OK");
					}
					
					APPLICATION_WINDOW.endOperation();
				}
			}
		});
	}
	
	private static BufferedImage loadImageFromResources(String name) {
		URL imageURL = getResourceURL(name);
		if (imageURL != null) {
		    try {
				return ImageIO.read(imageURL);
			} catch (IOException e) {}
		}
		return new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
	}
	
	private static ImageIcon loadIconFromResources(String name) {
		return new ImageIcon(getResourceURL(name));
	}
	
	private static URL getResourceURL(String name) {
		return Editor.class.getResource("/resources/"+name);
	}
	
	private static void readAndApplyConfig() {
		if(!settingsFile.exists()) {
			settingsFile.getParentFile().mkdirs();
			try {
				settingsFile.createNewFile();
			} catch (IOException e) {}
		}
		
		FileInputStream fis = null;
		
		try {
			fis = new FileInputStream(settingsFile);
			prop.load(fis);
		} catch (IOException e) {
			System.err.println("Could not load settings file.");
		} finally {
			try {
				fis.close();
			} catch (IOException e) {}
		}
		
		Editor e = APPLICATION_WINDOW;
		e.setLocation(Integer.valueOf(prop.getProperty("Application.Location.X", "100")), Integer.valueOf(prop.getProperty("Application.Location.Y", "100")));
		e.setBounds(Integer.valueOf(prop.getProperty("Application.Location.X", "100")), Integer.valueOf(prop.getProperty("Application.Location.Y", "100")),
					Integer.valueOf(prop.getProperty("Application.Size.Width", "1060")), Integer.valueOf(prop.getProperty("Application.Size.Height", "800")));
		e.mainPanel.setDividerLocation(Integer.valueOf(prop.getProperty("Application.SplitPane.Location", "395")));
		e.setExtendedState(Integer.valueOf(prop.getProperty("Application.State", ""+JFrame.NORMAL)));
		
		e.menu.setSelectedRadioButton(Integer.valueOf(prop.getProperty("Application.Menu.View.Channels", "0")));
		if(Boolean.valueOf(prop.getProperty("Application.Menu.View.Luminance", "false")))
			e.menu.mViewLuminance.doClick(0); // The fastest click in the west.
		if(Boolean.valueOf(prop.getProperty("Application.Menu.View.Alpha", "false")))
			e.menu.mViewNoAlpha.doClick(0);
		if(Boolean.valueOf(prop.getProperty("Application.Menu.View.Tile", "false")))
			e.menu.mViewTile.doClick(0);
		if(Boolean.valueOf(prop.getProperty("Application.Menu.View.DXT", "false")))
			e.menu.mViewDXT.doClick(0);
		if(Boolean.valueOf(prop.getProperty("Application.Menu.Options.ShowRoot", "false")))
			e.menu.mOptionsShowRoot.doClick(0);
		if(Boolean.valueOf(prop.getProperty("Application.Menu.Options.AllowEmpty", "false")))
			e.menu.mOptionsAllowEmpty.doClick(0);
		if(Boolean.valueOf(prop.getProperty("Application.Menu.Options.SuppressWarnings", "false")))
			e.menu.mOptionsAllowEmpty.doClick(0);
		if(Boolean.valueOf(prop.getProperty("Application.Menu.Options.WriteToDefaultOutput", "false")))
			e.menu.mOptionsWriteToOutput.doClick(0);
		e.associationState = Integer.valueOf(prop.getProperty("Application.Menu.Options.Associate", "0"));
		//e.maxThreads = Integer.valueOf(prop.getProperty("Application.Config.MaxThreads", "4"));
		
		e.defaultSignature = prop.getProperty("Application.Menu.Options.DefaultSignature","");
		
		TextureSettings t = new TextureSettings();
		TextureSettings def = TextureSettings.defaultSettings();
		
		t.setFormat(prop.getProperty("PapaOptions.Format", def.getFormat()));
		t.setCompressionMethod(CompressionMethod.valueOf(prop.getProperty("PapaOptions.DxtMethod", ""+def.getCompressionMethod())));
		t.setGenerateMipmaps(Boolean.valueOf(prop.getProperty("PapaOptions.GenMipmaps", ""+def.getGenerateMipmaps())));
		t.setMipmapResizeMethod(Integer.valueOf(prop.getProperty("PapaOptions.MipmapResizeMethod", ""+def.getMipmapResizeMethod())));
		t.setSRGB(Boolean.valueOf(prop.getProperty("PapaOptions.SRGB", ""+def.getSRGB())));
		t.setResize(Boolean.valueOf(prop.getProperty("PapaOptions.Resize", ""+def.getResize())));
		t.setResizeMethod(Integer.valueOf(prop.getProperty("PapaOptions.ResizeMethod", ""+def.getResizeMethod())));
		t.setResizeMode(Integer.valueOf(prop.getProperty("PapaOptions.ResizeMode", ""+def.getResizeMode())));
		t.setSRGBTexname(prop.getProperty("PapaOptions.SRGBTexname", def.getSRGBTexname()));
		
		PapaFile.setPADirectory(prop.getProperty("PapaFile.PADirectory",null)!=null ? new File(prop.getProperty("PapaFile.PADirectory")) : null);
		e.papaOptions = new PapaOptions(e, t.immutable());
		e.batchConvert = new BatchConvert(e, e.papaOptions);
		
		e.batchConvert.setRecursive(Boolean.valueOf(prop.getProperty("BatchConvert.Recursive", ""+true)));
		e.batchConvert.setWriteLinkedFiles(Boolean.valueOf(prop.getProperty("BatchConvert.WriteLinked", ""+false)));
		e.batchConvert.setOverwrite(Boolean.valueOf(prop.getProperty("BatchConvert.Overwrite", ""+false)));
		e.batchConvert.setIgnoreHierarchy(Boolean.valueOf(prop.getProperty("BatchConvert.IgnoreHierarchy", ""+false)));
		e.batchConvert.setInputFolder(prop.getProperty("BatchConvert.InputFolder", ""));
		e.batchConvert.setOutputFolder(prop.getProperty("BatchConvert.OutputFolder", ""));
		
		// awful jank
		String name1 = "Associate With PAPA";
		String name2 = "Dissociate With PAPA";
		String name = e.associationState == 1 ? name1 : name2;
		e.menu.mOptionsRegisterPTexEdit.setText(name);
	}
	
	private static void addShutdownHooks() {
		Runtime.getRuntime().addShutdownHook(onExit);
	}
	
	private static void addUncaughtExceptionHandler() {
		Thread.setDefaultUncaughtExceptionHandler(onThreadException);
	}
	
	private static void applyPlatformChanges() {
		float size = 12f;
		String name = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
		if(name.contains("linux") || name.contains("unix")) {
			size = 10f;
		} else if(name.contains("mac")) {
			size = 11f;
		}
		try {
			UIManager.put("Label.font", UIManager.getFont("Label.font").deriveFont(size));
			UIManager.put("CheckBox.font", UIManager.getFont("CheckBox.font").deriveFont(size));
			UIManager.put("ComboBox.font", UIManager.getFont("ComboBox.font").deriveFont(size));
			UIManager.put("Button.font", UIManager.getFont("Button.font").deriveFont(size));
			UIManager.put("RadioButton.font", UIManager.getFont("RadioButton.font").deriveFont(size));
		} catch (NullPointerException e) {
			System.err.println("Failed to set font");
		}
		
	}
	
	private static final UncaughtExceptionHandler onThreadException = new UncaughtExceptionHandler() {
		@Override
		public void uncaughtException(Thread t, Throwable e) {
			Thread.setDefaultUncaughtExceptionHandler(null); // prevent other errors from cascading if total failure
			FileHandler.cancelActiveTask();
			String message = "Fatal error in thread "+t.getName()+":\n"+e.getClass().getName()+": "+e.getMessage()+"\n";
			Throwable cause = e;
			for(StackTraceElement element : e.getStackTrace())
				message+="\n"+element.toString();
			while(cause.getCause()!=null) {
				cause = cause.getCause();
				message+="\n\nCaused by:\n"+cause.getClass().getName()+": "+cause.getMessage()+"\n";
				for(StackTraceElement element : cause.getStackTrace())
					message+="\n"+element.toString();
			}
			
			
			JTextArea jte = new JTextArea();
			jte.setEditable(false);
			jte.setText(message);
			jte.setCaretPosition(0);
			
			JScrollPane jsp = new JScrollPane(jte,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
			jsp.setPreferredSize(new Dimension(650,350));
			
			Editor.showError(jsp, "Fatal Error", new Object[] {"Exit"}, "Exit");
			System.err.println(message);
			System.exit(-1);
		}
	};
	
	private static final Thread onExit = new Thread(new Runnable() {
		
		public void run() {
			if(!settingsFile.exists())
				return;
			
			Editor e = APPLICATION_WINDOW;
			int state = e.getExtendedState();
			if(state!=JFrame.MAXIMIZED_BOTH && state!=JFrame.MAXIMIZED_HORIZ && state!=JFrame.MAXIMIZED_VERT) {
				prop.setProperty("Application.Location.X", 			""+(int)e.getX());
				prop.setProperty("Application.Location.Y", 			""+(int)e.getY());
				prop.setProperty("Application.Size.Width",			""+(int)e.getWidth());
				prop.setProperty("Application.Size.Height", 		""+(int)e.getHeight());
			}
			prop.setProperty("Application.SplitPane.Location", 					""+(int)e.mainPanel.getDividerLocation());
			prop.setProperty("Application.State", 								""+e.getExtendedState());
			prop.setProperty("Application.Menu.View.Channels", 					""+e.menu.getSelectedRadioButton());
			prop.setProperty("Application.Menu.View.Luminance", 				""+e.menu.mViewLuminance.isSelected());
			prop.setProperty("Application.Menu.View.Alpha", 					""+e.menu.mViewNoAlpha.isSelected());
			prop.setProperty("Application.Menu.View.Tile", 						""+e.menu.mViewTile.isSelected());
			prop.setProperty("Application.Menu.View.DXT", 						""+e.menu.mViewDXT.isSelected());
			prop.setProperty("Application.Menu.Options.ShowRoot",				""+e.menu.mOptionsShowRoot.isSelected());
			prop.setProperty("Application.Menu.Options.AllowEmpty",				""+e.menu.mOptionsAllowEmpty.isSelected());
			prop.setProperty("Application.Menu.Options.SuppressWarnings",		""+e.menu.mOptionsSuppressWarnings.isSelected());
			prop.setProperty("Application.Menu.Options.WriteToDefaultOutput",	""+e.menu.mOptionsWriteToOutput.isSelected());
			//prop.setProperty("Application.Config.MaxThreads", 	""+e.maxThreads);
			prop.setProperty("Application.Menu.Options.DefaultSignature",		""+e.defaultSignature);
			prop.setProperty("Application.Menu.Options.Associate",				""+e.associationState);
			
			ImmutableTextureSettings settings = e.papaOptions.getCurrentSettings();
			
			prop.setProperty("PapaOptions.Format", 				""+settings.format);
			prop.setProperty("PapaOptions.DxtMethod", 			""+settings.method);
			prop.setProperty("PapaOptions.GenMipmaps", 			""+settings.generateMipmaps);
			prop.setProperty("PapaOptions.MipmapResizeMethod", 	""+settings.mipmapResizeMethod);
			prop.setProperty("PapaOptions.SRGB", 				""+settings.SRGB);
			prop.setProperty("PapaOptions.Resize", 				""+settings.resize);
			prop.setProperty("PapaOptions.ResizeMethod", 		""+settings.resizeMethod);
			prop.setProperty("PapaOptions.ResizeMode", 			""+settings.resizeMode);
			prop.setProperty("PapaOptions.SRGBTexname", 		""+settings.srgbTexname);
			
			BatchConvert b = e.batchConvert;
			prop.setProperty("BatchConvert.Recursive", 			""+b.isRecursive());
			prop.setProperty("BatchConvert.WriteLinked", 		""+b.isWritingLinkedFiles());
			prop.setProperty("BatchConvert.Overwrite", 			""+b.isOverwrite());
			prop.setProperty("BatchConvert.IgnoreHierarchy", 	""+b.isIgnoreHierarchy());
			prop.setProperty("BatchConvert.InputFolder", 		""+b.getInputFolder());
			prop.setProperty("BatchConvert.OutputFolder", 		""+b.getOutputFolder());
			
			if(PapaFile.getPlanetaryAnnihilationDirectory()!=null)
				prop.setProperty("PapaFile.PADirectory", 			PapaFile.getPlanetaryAnnihilationDirectory().getAbsolutePath());
			
			try {
				prop.store(new FileOutputStream(settingsFile), null);
			} catch (IOException e2) { e2.printStackTrace();}
		}
	});
	
	private class FileWorker extends SwingWorker<Void,PapaFile> {

		private File[] files;
		protected ImmutableTextureSettings settings = null;
		protected ImmutableTextureSettings defaultSettings = null;
		protected ImportInterface importInterface = null;
		private boolean ignoreReprompt = false;
		private float subProgress = 0f;
		private float totalSubFiles = 0f;
		private AtomicInteger processedFiles = new AtomicInteger();
		private boolean optimize = false;
		private int optimizeCounter = 0, optimizeFactor=0;
		private File overrideLocation = null;
		
		public FileWorker(File...files) {
			this(null, files);
		}
		
		public FileWorker(ImmutableTextureSettings defaultSettings, File...files) {
			this.defaultSettings=defaultSettings;
			this.files = files;
			papaOptions.setMultiMode(files.length>1);
			ribbonPanel.setTotalNumberOfJobs(files.length);
		}
		
		public void setOverrideLocation(File f) {
			overrideLocation = f;
		}
		
		public float getSubProgress() {
			return subProgress;
		}
		
		@Override
		protected Void doInBackground() throws Exception {
			long time = System.nanoTime();
			SwingUtilities.invokeAndWait(() -> APPLICATION_WINDOW.setReadingFiles(true));
			processedFiles.set(0);
			for(int i =0;i<files.length;i++) {
				setProgress((int)(100f/(float)files.length * i));
				ribbonPanel.setActiveJobIndex(i+1);
				
				importInterface = FileHandler.determineInterface(files[i]);
				if(importInterface==null)
					continue;
				
				if(importInterface == FileHandler.IMAGE_INTERFACE) {
					if (defaultSettings!=null) {
						settings = defaultSettings;
					} else if(! ignoreReprompt) {
						final int index = i;
						SwingUtilities.invokeAndWait(() -> settings = getTextureImportSettings(files[index]));
						ignoreReprompt = papaOptions.ignoreReprompt(); 
					}
					if(settings==null)
						continue;
				}
				
				ImportInfo info = new ImportInfo();
				info.setTextureSettings(settings);
				info.setSignature(defaultSignature);
				info.setActivityListener(new ActivityListener() {
					@Override
					public void onFoundAcceptableFile(File f, int currentTotal) {
						ribbonPanel.setNumberOfFoundFiles(currentTotal);
					}
					@Override
					public void onGotTotalFiles(int totalFiles) {
						ribbonPanel.startImport(totalFiles);
						totalSubFiles = totalFiles;
						if(totalSubFiles>=50) {
							optimize = true; // reloading the tree is expensive
							optimizeFactor = Math.min((int) ((totalSubFiles+100) / 50), 8); // min 3 files per reload, max 8
						}
					}
					@Override
					public void onEndProcessFile(File f, String threadName, boolean success) {
						int count = processedFiles.getAndAdd(1);
						subProgress = (float) count / totalSubFiles;
						ribbonPanel.setNumberProcessedFiles(count);
					}
					@Override
					public void onRejectFile(File f, String reason) {}
					@Override
					public void onAcceptFile(PapaFile p) {
						publish(p);
					}
				});
				boolean wait = importInterface == FileHandler.IMAGE_INTERFACE || i == files.length-1;
				ribbonPanel.setNumberOfFoundFiles(0);
				FileHandler.readFiles(files[i], importInterface, info, true, wait);
				// TODO this is a bit of a band aid solution. Currently the file handler has no information on the progress of an import.
				// FileHandler must encapsulate the import into a class for better info. (this will also allow for >1 import at a time)
				if(wait)
					ribbonPanel.endImport();
			}
			
			SwingUtilities.invokeLater(() -> APPLICATION_WINDOW.setReadingFiles(false));
			papaOptions.setMultiMode(false);
			System.out.println("Time: "+(double)(System.nanoTime()-time)/1000000d+" ms");
			return null;
		}
		
		@Override
		protected void process(List<PapaFile> chunks) {
			if(chunks.size()!=0) {
				for(PapaFile p : chunks) {
					if(defaultSettings == null) {
						configSelector.addToTreeOrSelect(p,!optimize || optimizeCounter++%optimizeFactor==0 || optimizeCounter == totalSubFiles);
					} else {
						if(importInterface == FileHandler.IMAGE_INTERFACE) {
							try {
								if(overrideLocation != null) {
									File target = new File(overrideLocation+File.separator 
										+ FileHandler.replaceExtension(new File(p.getFileName()),FileHandler.getPapaFilter()));
									FileHandler.saveFileTo(p, target, true);
								} else {
									FileHandler.saveFileTo(p, FileHandler.replaceExtension(p.getFile(), FileHandler.getPapaFilter()), true);
								}
								
							} catch (IOException e) {
								showError("Failed to save file. "+e.getMessage(),"Save error",new Object[] {"OK"},"OK");
							}
						} else {
							for(int i =0;i<p.getNumTextures();i++) {
								PapaTexture tex = p.getTexture(i);
								
								File dst = p.getFile();
								
								try {
									if(tex.isLinked()) {
										if(!tex.linkValid()) {
											continue;
										}
										tex = tex.getLinkedTexture();
										// remap file so linked textures save in the local directory
										PapaFile owner = tex.getParent();
										dst = new File(p.getFile().getParentFile().getCanonicalPath() + "/" + owner.getFileName());
									}
									File target;
									if(overrideLocation != null) {
										String s = dst.toString();
										s = s.substring(s.lastIndexOf(File.separatorChar));
										target = new File(overrideLocation+File.separator 
												+ FileHandler.replaceExtension(new File(s), FileHandler.getImageFilter("png")));
									} else {
										target = FileHandler.replaceExtension(dst, FileHandler.getImageFilter("png"));
									}
									FileHandler.exportImageTo(tex, target, FileHandler.getImageFilter("png"), true);
								} catch (IOException e) {
									showError("Failed to save file. "+e.getMessage(),"Save error",new Object[] {"OK"},"OK");
								}
							}
						}
					}
					
				}
			}
		}
		
		@Override
		protected void done() {
			try {
				get();
			} catch (Exception e) {
				if(e.getClass()!=CancellationException.class)
					throw new RuntimeException(e);
			}
		}
	}
	
	private class ImageFileWorker extends FileWorker {
		private Image image;
		public ImageFileWorker(Image image) {
			this.image = image;
			papaOptions.setMultiMode(false);
		}
		
		@Override
		protected Void doInBackground() throws Exception {
			long time = System.nanoTime();
			SwingUtilities.invokeLater(() -> APPLICATION_WINDOW.setReadingFiles(true));
			
			SwingUtilities.invokeAndWait(() -> settings = getTextureImportSettings(null));
			
			if(settings!=null) {
				
				ImportInfo info = new ImportInfo();
				info.setTextureSettings(settings);
				info.setInternalMode(true);
				info.setActivityListener(new ActivityListener() {
					@Override
					public void onRejectFile(File f, String reason) {}
					@Override
					public void onAcceptFile(PapaFile p) {
						p.setFileLocation(null);
						publish(p);
					}
				});
				
				File f = File.createTempFile("PTexEditImport", ".png");
				ImageIO.write((RenderedImage) image, "png", f);
				try {
					FileHandler.readFiles(f, FileHandler.IMAGE_INTERFACE, info, false, true);
				} finally {
					f.delete();
				}
				
				if(info.getNumAcceptedFiles()==0)
					showError("Unable to paste image: " + info.getRejectedFileReasons()[0], "Paste error", new Object[] {"Ok"}, "Ok");
			}
			
			SwingUtilities.invokeLater(() -> APPLICATION_WINDOW.setReadingFiles(false));
			System.out.println("Time: "+(double)(System.nanoTime()-time)/1000000d+" ms");
			return null;
		}
	}
	
	private synchronized void startOperation() {
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		loadCounter++;
	}
	
	private synchronized void endOperation() {
		loadCounter--;
		if(loadCounter==0)
			setCursor(Cursor.getDefaultCursor());
	}
	
	private void readAll(ImmutableTextureSettings defaultSettings, File...files) {
		FileWorker fw = new FileWorker(defaultSettings, files);
		if(menu.mOptionsWriteToOutput.isSelected()) {
			fw.setOverrideLocation(new File(batchConvert.getOutputFolder()));
		}
		fw.execute();
	}
	
	private void readImage(Image image) {
		FileWorker fw = new ImageFileWorker(image);
		fw.execute();
	}
	
	private void setReadingFiles(boolean b) {
		if(b && ! menu.readingFiles)
			startOperation();
		else if(!b && menu.readingFiles)
			endOperation();
		menu.setReadingFiles(b);
		boolean enable = !b;
		imagePanel.dragDropEnabled = enable;
	}

	private ImmutableTextureSettings getTextureImportSettings(File f) {
		papaOptions.setActiveFile(f);
		papaOptions.updateLinkOptions(getTargetablePapaFiles());
		papaOptions.showAt(getX() + getWidth() / 2, getY() + getHeight() / 2); // this blocks code execution at this point. neat!
		if(papaOptions.getWasAccepted())
			return papaOptions.getCurrentSettings();
		return null;
	}
	
	private void setActiveFile(PapaFile p) {
		if(p==null) {
			unloadFileFromConfig();
			return;
		}
		PapaFile old = activeFile;
		activeFile = p;
		int index = Math.max(0, configSection1.getActiveTextureIndex(activeFile));
		activeTexture = activeFile.getNumTextures()!=0 ? activeFile.getTexture(index) : null;
		
		boolean changed = old!=activeFile;
		menu.applySettings(activeFile, index, activeTexture ,changed);
		configSection1.applySettings(activeFile, index, activeTexture ,changed);
		configSection2.applySettings(activeFile, index, activeTexture ,changed);
		configSelector.applySettings(activeFile, index, activeTexture ,changed);
	}
	
	/*private void refreshActiveFile() {
		setActiveFile(activeFile);
		configSelector.selectedNodeChanged();
	}*/
	
	private void setActiveTexture(PapaTexture p) {
		activeTexture = p;
		setActiveFile(p.getParent());
	}
	
	private void unloadFileFromConfig() {
		activeFile = null;
		activeTexture = null;
		dependents.clear();
		dependencies.clear();
		menu.unload();
		configSection1.unload();
		configSection2.unload();
		configSelector.unload();
		imagePanel.unload();
		configSelector.fileTree.repaint();
	}
	
	private PapaFile[] getTargetablePapaFiles() {
		return configSelector.getTargetablePapaFiles();
	}

	public Editor() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		setIconImages(Arrays.asList(icon,iconSmall));
		
		String name = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
		if(name.contains("win")) {
			associationState = Math.max(1, associationState);
		}
		
		contentPane = new JPanel();
		setContentPane(contentPane);
		SpringLayout contentPaneLayout = new SpringLayout();
		contentPane.setLayout(contentPaneLayout);
		
		menu = new MenuBar();
		setJMenuBar(menu);
		
		ribbonPanel = new RibbonPanel();
		contentPaneLayout.putConstraint(SpringLayout.WEST, ribbonPanel, 0, SpringLayout.WEST, contentPane);
		contentPaneLayout.putConstraint(SpringLayout.NORTH, ribbonPanel, -25, SpringLayout.SOUTH, contentPane);
		contentPaneLayout.putConstraint(SpringLayout.SOUTH, ribbonPanel, 0, SpringLayout.SOUTH, contentPane);
		contentPaneLayout.putConstraint(SpringLayout.EAST, ribbonPanel, 0, SpringLayout.EAST, contentPane);
		contentPane.add(ribbonPanel);
		
		// left panel
		config = new JPanel();
		// right panel
		canvas = new JPanel();
		
		mainPanel = new JSplitPane();
		mainPanel.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
		contentPaneLayout.putConstraint(SpringLayout.NORTH, mainPanel, 0, SpringLayout.NORTH, contentPane);
		contentPaneLayout.putConstraint(SpringLayout.SOUTH, mainPanel, 0, SpringLayout.NORTH, ribbonPanel);
		contentPaneLayout.putConstraint(SpringLayout.EAST, mainPanel, 0, SpringLayout.EAST, contentPane);
		contentPaneLayout.putConstraint(SpringLayout.WEST, mainPanel, 0, SpringLayout.WEST, contentPane);
		contentPane.add(mainPanel);
		
		mainPanel.setLeftComponent(config);
		mainPanel.setRightComponent(canvas);
		mainPanel.setDividerLocation(230);
		
		SpringLayout configLayout = new SpringLayout();
		config.setLayout(configLayout);
		
		SpringLayout canvasLayout = new SpringLayout();
		canvas.setLayout(canvasLayout);
		
		// RIGHT SIDE
		
		box = new JPanel();
		canvasLayout.putConstraint(SpringLayout.NORTH, box, -15, SpringLayout.SOUTH, canvas);
		canvasLayout.putConstraint(SpringLayout.SOUTH, box, 0, SpringLayout.SOUTH, canvas);
		canvasLayout.putConstraint(SpringLayout.WEST, box, -15, SpringLayout.EAST, canvas);
		canvasLayout.putConstraint(SpringLayout.EAST, box, 0, SpringLayout.EAST, canvas);
		canvas.add(box);
		
		AdjustmentListener scrollListener = new AdjustmentListener() {
			@Override
			public void adjustmentValueChanged(AdjustmentEvent e) {
				imagePanel.repaint();
			}
		};
		
		horizontal = new JScrollBar();
		canvasLayout.putConstraint(SpringLayout.NORTH, horizontal, 0, SpringLayout.NORTH, box);
		canvasLayout.putConstraint(SpringLayout.SOUTH, horizontal, 0, SpringLayout.SOUTH, box);
		canvasLayout.putConstraint(SpringLayout.WEST, horizontal, 0, SpringLayout.WEST, canvas);
		canvasLayout.putConstraint(SpringLayout.EAST, horizontal, 0, SpringLayout.WEST, box);
		horizontal.setOrientation(JScrollBar.HORIZONTAL);
		horizontal.setEnabled(false);
		horizontal.addAdjustmentListener(scrollListener);
		canvas.add(horizontal);
		
		vertical = new JScrollBar();
		canvasLayout.putConstraint(SpringLayout.NORTH, vertical, 0, SpringLayout.NORTH, canvas);
		canvasLayout.putConstraint(SpringLayout.SOUTH, vertical, 0, SpringLayout.NORTH, box);
		canvasLayout.putConstraint(SpringLayout.EAST, vertical, 0, SpringLayout.EAST, box);
		canvasLayout.putConstraint(SpringLayout.WEST, vertical, 0, SpringLayout.WEST, box);
		vertical.setOrientation(JScrollBar.VERTICAL);
		vertical.setEnabled(false);
		vertical.addAdjustmentListener(scrollListener);
		canvas.add(vertical);
		
		imagePanel = new ImagePanel();
		canvasLayout.putConstraint(SpringLayout.NORTH, imagePanel, 0, SpringLayout.NORTH, canvas);
		canvasLayout.putConstraint(SpringLayout.WEST, imagePanel, 0, SpringLayout.WEST, canvas);
		canvasLayout.putConstraint(SpringLayout.SOUTH, imagePanel, 0, SpringLayout.NORTH, box);
		canvasLayout.putConstraint(SpringLayout.EAST, imagePanel, 0, SpringLayout.WEST, box);
		imagePanel.setBackground(new Color(160,160,160));
		canvas.add(imagePanel);
		
		// LEFT SIDE
		
		// top panel
		configSection1 = new ConfigPanelTop();
		configLayout.putConstraint(SpringLayout.NORTH, configSection1, 5, SpringLayout.NORTH, config);
		configLayout.putConstraint(SpringLayout.WEST, configSection1, 5, SpringLayout.WEST, config);
		configLayout.putConstraint(SpringLayout.SOUTH, configSection1, 160, SpringLayout.NORTH, config); // 195 with buttons enabled
		configLayout.putConstraint(SpringLayout.EAST, configSection1, -5, SpringLayout.EAST, config);
		config.add(configSection1);
		
		//bottom panel
		configSection2 = new ConfigPanelBottom();
		configLayout.putConstraint(SpringLayout.NORTH, configSection2, 5, SpringLayout.SOUTH, configSection1);
		configLayout.putConstraint(SpringLayout.WEST, configSection2, 5, SpringLayout.WEST, config);
		configLayout.putConstraint(SpringLayout.SOUTH, configSection2, 270, SpringLayout.SOUTH, configSection1);
		configLayout.putConstraint(SpringLayout.EAST, configSection2, -5, SpringLayout.EAST, config);
		config.add(configSection2);
		
		//selector panel
		configSelector = new ConfigPanelSelector();
		configLayout.putConstraint(SpringLayout.NORTH, configSelector, 5, SpringLayout.SOUTH, configSection2);
		configLayout.putConstraint(SpringLayout.WEST, configSelector, 5, SpringLayout.WEST, config);
		configLayout.putConstraint(SpringLayout.SOUTH, configSelector, -5, SpringLayout.SOUTH, config);
		configLayout.putConstraint(SpringLayout.EAST, configSelector, -5, SpringLayout.EAST, config);
		config.add(configSelector);
		
		clipboardOwner = new ClipboardListener();
	}
	
	private class MenuBar extends JMenuBar {
		
		private static final long serialVersionUID = 275294845979597235L;

		private ButtonGroup mViewChannelItems;
		private JCheckBoxMenuItem mViewLuminance, mViewNoAlpha, mViewTile, mViewDXT, mOptionsShowRoot, mOptionsAllowEmpty, mOptionsSuppressWarnings, mOptionsWriteToOutput;
		private JRadioButtonMenuItem mViewChannelRGB, mViewChannelR, mViewChannelG, mViewChannelB, mViewChannelA;
		private JMenuItem mFileOpen,mFileImport, mFileSave, mFileSaveAs, mFileExport, mToolsConvertFolder, mToolsShowInFileBrowser, mToolsReloadLinked,
							mEditCopy, mEditPaste, mOptionsRegisterPTexEdit;
		private boolean clipboardHasImage, readingFiles;
		
		public int getSelectedRadioButton() { // I hate ButtonGroup.
			Enumeration<AbstractButton> i = mViewChannelItems.getElements();
			int j=0;
			while(i.hasMoreElements()) {
				if(i.nextElement().isSelected())
					return j;
				j++;
			}
			return 0;
		}
		
		public void setReadingFiles(boolean b) {
			boolean enable = !b;
			readingFiles = b;
			mFileImport.setEnabled(enable);
			mFileOpen.setEnabled(enable);
			mToolsConvertFolder.setEnabled(enable);
			mEditPaste.setEnabled(enable && clipboardHasImage);
		}

		public void unload() {
			mFileSave.setEnabled(false);
			mFileSaveAs.setEnabled(false);
			mFileExport.setEnabled(false);
			mEditCopy.setEnabled(false);
			mToolsShowInFileBrowser.setEnabled(false);
			mToolsReloadLinked.setEnabled(false);
		}

		public void applySettings(PapaFile activeFile, int index, PapaTexture tex, boolean same) {
			boolean hasTextures = !(activeFile.getNumTextures() == 0 || tex == null);
			boolean linkValid = hasTextures && (!tex.isLinked() || tex.linkValid());
			mFileSave.setEnabled(true);
			mFileSaveAs.setEnabled(true);
			mToolsShowInFileBrowser.setEnabled(activeFile.getFile()!=null && activeFile.getFile().exists());
			
			mFileExport.setEnabled(hasTextures && linkValid);
			mEditCopy.setEnabled(hasTextures && linkValid);
			mToolsReloadLinked.setEnabled(hasTextures && activeFile!=null);
		}

		public void setSelectedRadioButton(int index) {
			Enumeration<AbstractButton> i = mViewChannelItems.getElements();
			int j=0;
			AbstractButton a;
			while(i.hasMoreElements()) {
				a=i.nextElement();
				if(j++==index) {
					a.doClick(0);
					return;
				}
			}
		}
		
		private void saveFile(PapaFile target) {
			if( ! target.isPapaFile()) {
				saveFileAs(target);
				return;
			}
			
			startOperation();
			try {
				FileHandler.writeFile(target,target.getFile());
				configSelector.selectedPapaFileNodeChanged();
			} catch (IOException e1) {
				showError(e1.getMessage(), "Save", new Object[] {"Ok"}, "Ok");
			}
			endOperation();
		}
		
		private void saveFileAs(PapaFile target) {
			JFileChooser j = new JFileChooser();
			
			j.setFileSelectionMode(JFileChooser.FILES_ONLY);
			j.setDialogTitle("Save Papa File");
			j.setFileFilter(FileHandler.getPapaFilter());
			if(target.getFile()!=null) {
				if(!target.getFile().exists())
					j.setCurrentDirectory(getLowestExistingDirectory(target.getFile()));
				j.setSelectedFile(FileHandler.replaceExtension(target.getFile(), FileHandler.getPapaFilter()));
			}
			startOperation();
			if (j.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
				try {
					FileHandler.saveFileTo(target, j.getSelectedFile(), false);
				} catch (IOException e1) {
					showError(e1.getMessage(), "Save As...", new Object[] {"Ok"}, "Ok");
				}
				configSelector.selectedPapaFileNodeChanged();
			}
			endOperation();
		}
		
		private File getLowestExistingDirectory(File file) {
			while(file!=null && !file.exists()) {
				file = file.getParentFile();
			}
			return file;
		}

		private void clipboardChanged() {
			try {
				for(DataFlavor flavor : clipboard.getAvailableDataFlavors())
					if(flavor.equals(DataFlavor.imageFlavor)) {
						clipboardHasImage = true;
						return;
					}
			} catch (IllegalStateException e) {
				clipboardHasImage = true; // rely on future checks if we can't determine right now
				return;
			}
			clipboardHasImage = false;
		}
		
		public void changeMediaDirectory() {
			JFileChooser j = new JFileChooser();
			j.setDialogTitle("Set Media Directory");
			
			j.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			if(PapaFile.getPlanetaryAnnihilationDirectory() != null)
				j.setSelectedFile(PapaFile.getPlanetaryAnnihilationDirectory());
			
			if (j.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				File file = j.getSelectedFile();
				if(file.listFiles((File dir, String name)-> name.equals("pa")).length==0 && 
					Editor.optionBox("The selected directory does not include the /pa subdirectory.\n Are you sure this is the correct directory?",
									"Confirm Directory", new Object[] {"Yes","Cancel"}, "Cancel") != 0) {
						return;
				}
				PapaFile.setPADirectory(file);
				
			}
		}
		// https://stackoverflow.com/a/23538961
		private boolean isAdmin()
	    {
	        Preferences preferences = Preferences.systemRoot();

	        synchronized (System.err)
	        {
	        	PrintStream old = System.err;
	        	System.setErr(new PrintStream(new OutputStream()
	            {
	                @Override
	                public void write(int b) {}
	            }));

	            try {
	                preferences.put("foo", "bar"); // SecurityException on Windows
	                preferences.remove("foo");
	                preferences.flush(); // BackingStoreException on Linux
	                return true;
	            } catch (Exception exception) {
	                return false;
	            } finally {
	            	System.setErr(old);
	            }
	        }
	    }
		
		public MenuBar() {
			JMenu mFile = new JMenu("File");
			mFile.setMnemonic('f');
			add(mFile);
			
			mFileOpen = new JMenuItem("Open");
			mFileOpen.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_MASK));
			mFile.add(mFileOpen);
			mFileOpen.setMnemonic('o');
			
			mFileOpen.addActionListener((ActionEvent e) -> {
				JFileChooser j = new JFileChooser();
				
				j.setFileSelectionMode(JFileChooser.FILES_ONLY);
				j.setAcceptAllFileFilterUsed(false);
				j.setFileFilter(FileHandler.getPapaFilter());
				if (j.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
					File file = j.getSelectedFile();
					readAll(null, file);
				}
			});
			
			JSeparator separator_1 = new JSeparator();
			mFile.add(separator_1);
			
			mFileSave = new JMenuItem("Save");
			mFileSave.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_MASK));
			mFile.add(mFileSave);
			mFileSave.setMnemonic('s');
			mFileSave.setEnabled(false);
			mFileSave.addActionListener((ActionEvent e) -> {
				saveFile(activeFile);
			});
			
			mFileSaveAs = new JMenuItem("Save As...");
			mFileSaveAs.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK));
			mFile.add(mFileSaveAs);
			mFileSaveAs.setMnemonic('A');
			mFileSaveAs.setEnabled(false);
			mFileSaveAs.addActionListener((ActionEvent e) -> {
				saveFileAs(activeFile);
			});
			
			JSeparator separator = new JSeparator();
			mFile.add(separator);
			
			mFileImport = new JMenuItem("Import");
			mFileImport.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_MASK));
			mFile.add(mFileImport);
			mFileImport.setMnemonic('i');
			mFileImport.addActionListener((ActionEvent e) -> { // this is identical to mFileOpen and just changes the accepted file types. Fight me.
				JFileChooser j = new JFileChooser();
				
				j.setFileSelectionMode(JFileChooser.FILES_ONLY);
				j.setAcceptAllFileFilterUsed(false);
				j.setDialogTitle("Import File");
				
				for(FileNameExtensionFilter f : FileHandler.getImageFilters())
					j.addChoosableFileFilter(f);
				
				if (j.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
					File file = j.getSelectedFile();
					readAll(null, file);
				}
			});
			
			mFileExport = new JMenuItem("Export");
			mFileExport.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_MASK));
			mFile.add(mFileExport);
			mFileExport.setMnemonic('e');
			mFileExport.setEnabled(false);
			mFileExport.addActionListener((ActionEvent e) -> {
				
				JFileChooser j = new JFileChooser();
				
				j.setFileSelectionMode(JFileChooser.FILES_ONLY);
				j.setAcceptAllFileFilterUsed(false);
				j.setDialogTitle("Export File");
				for(FileNameExtensionFilter f : FileHandler.getImageFilters())
					j.addChoosableFileFilter(f);
				
				j.setFileFilter(FileHandler.getImageFilter("png"));
				
				if(activeFile.getFile()!=null) {
					if(activeFile.getFile().exists())
						j.setSelectedFile(FileHandler.replaceExtension(activeFile.getFile(), FileHandler.getImageFilter("png")));
					else
						j.setCurrentDirectory(getLowestExistingDirectory(activeFile.getFile()));
				}
				
				PapaTexture tex = activeTexture;
				if(tex.isLinked() && tex.linkValid())
					tex = tex.getLinkedTexture();
				
				startOperation();
				if (j.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
					try {
						FileHandler.exportImageTo(tex, j.getSelectedFile(), (FileNameExtensionFilter)j.getFileFilter(), false);
					} catch (IOException e1) {
						showError(e1.getMessage(), "Export Error", new Object[] {"Ok"}, "Ok");
					}
				endOperation();
			});
			
			JSeparator separator_2 = new JSeparator();
			mFile.add(separator_2);
			
			JMenuItem mFileExit = new JMenuItem("Exit");
			mFileExit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_MASK));
			mFile.add(mFileExit);
			mFileExit.setMnemonic('x');
			
			mFileExit.addActionListener((ActionEvent e) -> {
				System.exit(0);
			});
			
			JMenu mEdit = new JMenu("Edit");
			mEdit.setMnemonic('e');
			add(mEdit);
			
			mEditCopy = new JMenuItem("Copy");
			mEditCopy.setMnemonic('c');
			mEditCopy.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK));
			mEditCopy.setEnabled(false);
			mEdit.add(mEditCopy);
			mEditCopy.addActionListener((ActionEvent e)-> {
				transferToClipboard(imagePanel.getFullImage());
			});
			
			mEditPaste = new JMenuItem("Paste");
			mEditPaste.setMnemonic('p');
			mEditPaste.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK));
			mEdit.add(mEditPaste);
			mEditPaste.addActionListener((ActionEvent e)-> {
				Image i = getImageFromClipboard();
				if(i==null) {
					showError("Clipboard does not contain an image.", "Invalid input", new Object[] {"Ok"}, "Ok");
					return;
				}
				readImage(i);
			});
			
			clipboard.addFlavorListener(new FlavorListener() {
				@Override
				public void flavorsChanged(FlavorEvent e) {
					clipboardChanged();
					mEditPaste.setEnabled(clipboardHasImage && ! readingFiles);
				}
			});
			clipboardChanged();
			mEditPaste.setEnabled(clipboardHasImage);
			
			JMenu mView = new JMenu("View");
			mView.setMnemonic('v');
			add(mView);
			
			
			JMenu mViewChannel = new JMenu("Channels");
			mView.add(mViewChannel);
			mViewChannel.setMnemonic('C');
			
			mViewChannelItems = new ButtonGroup();
			
			mViewChannelRGB = new JRadioButtonMenuItem("RGB",true);
			mViewChannelRGB.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK));
			mViewChannel.add(mViewChannelRGB);
			mViewChannelItems.add(mViewChannelRGB);
			mViewChannelRGB.addActionListener((ActionEvent e) -> {
				if(mViewChannelRGB.isSelected())
					imagePanel.setMode(ImagePanel.RGBA);
			});
			
			mViewChannelR = new JRadioButtonMenuItem("R");
			mViewChannelR.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK));
			mViewChannel.add(mViewChannelR);
			mViewChannelItems.add(mViewChannelR);
			mViewChannelR.addActionListener((ActionEvent e) -> {
				if(mViewChannelR.isSelected())
					imagePanel.setMode(ImagePanel.RED);
			});
			
			
			mViewChannelG = new JRadioButtonMenuItem("G");
			mViewChannelG.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK));
			mViewChannel.add(mViewChannelG);
			mViewChannelItems.add(mViewChannelG);
			mViewChannelG.addActionListener((ActionEvent e) -> {
				if(mViewChannelG.isSelected())
					imagePanel.setMode(ImagePanel.GREEN);
			});
			
			
			mViewChannelB = new JRadioButtonMenuItem("B");
			mViewChannelB.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK));
			mViewChannel.add(mViewChannelB);
			mViewChannelItems.add(mViewChannelB);
			mViewChannelB.addActionListener((ActionEvent e) -> {
				if(mViewChannelB.isSelected())
					imagePanel.setMode(ImagePanel.BLUE);
			});
			
			
			mViewChannelA = new JRadioButtonMenuItem("A");
			mViewChannelA.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK));
			mViewChannel.add(mViewChannelA);
			mViewChannelItems.add(mViewChannelA);
			mViewChannelA.addActionListener((ActionEvent e) -> {
				if(mViewChannelA.isSelected())
					imagePanel.setMode(ImagePanel.ALPHA);
			});
			
			
			mViewLuminance = new JCheckBoxMenuItem("Luminance");
			mViewLuminance.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK));
			mViewLuminance.setMnemonic('l');
			mView.add(mViewLuminance);
			mViewLuminance.addActionListener((ActionEvent e) -> {
					imagePanel.setLuminance(mViewLuminance.isSelected());
			});
			
			
			mViewNoAlpha = new JCheckBoxMenuItem("Ignore Alpha");
			mViewNoAlpha.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK));
			mViewNoAlpha.setMnemonic('i');
			mView.add(mViewNoAlpha);
			mViewNoAlpha.addActionListener((ActionEvent e) -> {
				imagePanel.setIgnoreAlpha(mViewNoAlpha.isSelected());
			});
			
			
			mViewTile = new JCheckBoxMenuItem("Tile");
			mViewTile.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK));
			mViewTile.setMnemonic('t');
			mView.add(mViewTile);
			mViewTile.addActionListener((ActionEvent e) -> {
				imagePanel.setTile(mViewTile.isSelected());
			});
			
			mView.add(new JSeparator());
			
			mViewDXT = new JCheckBoxMenuItem("DXT Grid");
			mViewDXT.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK));
			mViewDXT.setMnemonic('d');
			mView.add(mViewDXT);
			mViewDXT.addActionListener((ActionEvent e) -> {
				imagePanel.showDXT(mViewDXT.isSelected());
			});
			
			
			JMenu mTools = new JMenu("Tools");
			mTools.setMnemonic('t');
			add(mTools);
			
			mToolsConvertFolder = new JMenuItem("Convert Folder");
			mToolsConvertFolder.setMnemonic('c');
			mTools.add(mToolsConvertFolder);
			mToolsConvertFolder.addActionListener((ActionEvent e) -> {
				batchConvert.setDefualtSignature(APPLICATION_WINDOW.defaultSignature);
				batchConvert.showAt(APPLICATION_WINDOW.getX() + APPLICATION_WINDOW.getWidth() / 2, APPLICATION_WINDOW.getY() + APPLICATION_WINDOW.getHeight() / 2);
			});
			
			mToolsShowInFileBrowser = new JMenuItem("Show in File Manager");
			mToolsShowInFileBrowser.setEnabled(false);
			mToolsShowInFileBrowser.setMnemonic('s');
			boolean supported = Desktop.getDesktop().isSupported(Desktop.Action.OPEN);
			if(supported)
				mTools.add(mToolsShowInFileBrowser);
			mToolsShowInFileBrowser.addActionListener((ActionEvent e) -> {
				try {
					File target = activeFile.getFile().getParentFile();
					if(!target.exists())
						throw new IOException("Directory does not exist");
					Desktop.getDesktop().open(target);
				} catch (Exception e1) {
					showError("Failed to open file in file manager: "+e1.getMessage(), "Error", new Object[] {"Ok"}, "Ok");
				}
			});
			
			mTools.add(new JSeparator());
			
			mToolsReloadLinked = new JMenuItem("Reload Linked Files");
			mToolsReloadLinked.setEnabled(false);
			mToolsReloadLinked.setMnemonic('c');
			mTools.add(mToolsReloadLinked);
			mToolsReloadLinked.addActionListener((ActionEvent e) -> {
				activeFile.reloadLinkedTextures();
				configSelector.reloadTopLevelSelectedNode();
				setActiveFile(activeFile);
			});
			
			
			//JMenuItem mToolsReload = new JMenuItem("Reload File"); TODO
			//mTools.add(mToolsReload);
			
			JMenu mOptions = new JMenu("Options");
			mOptions.setMnemonic('o');
			add(mOptions);
			
			JMenuItem mOptionsSetDirectory = new JMenuItem("Set Media Directory...");
			mOptionsSetDirectory.setToolTipText("This is the base directory that will be used when finding linked textures.");
			mOptionsSetDirectory.setMnemonic('s');
			mOptions.add(mOptionsSetDirectory);
			mOptionsSetDirectory.addActionListener((ActionEvent e) -> {
				changeMediaDirectory();
			});
			
			JMenuItem mOptionsImageSettings = new JMenuItem("View Import Settings");
			mOptions.add(mOptionsImageSettings);
			mOptionsImageSettings.addActionListener((ActionEvent e) -> {
				getTextureImportSettings(null);
			});
			mOptionsImageSettings.setMnemonic('v');
			
			JMenuItem mOptionsDefaultSignature = new JMenuItem("Set Default Signature...");
			mOptions.add(mOptionsDefaultSignature);
			mOptionsDefaultSignature.addActionListener((ActionEvent e) -> {
				String signature = JOptionPane.showInputDialog(this, "Signature: ", APPLICATION_WINDOW.defaultSignature);
				if(signature == null) {
					return;
				}
				if(signature.length() > 6) {
					signature = signature.substring(0,6);
				}
				APPLICATION_WINDOW.defaultSignature = signature;
			});
			mOptionsDefaultSignature.setMnemonic('s');
			
			if(associationState != 0) {
				String name1 = "Associate With PAPA";
				String name2 = "Dissociate With PAPA";
				String name = associationState == 1 ? name1 : name2;
				
				mOptionsRegisterPTexEdit = new JMenuItem(name);
				mOptions.add(mOptionsRegisterPTexEdit);
				mOptionsRegisterPTexEdit.addActionListener((ActionEvent e) -> {
					try {
						File local = new File(Editor.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
						File jre = new File(System.getProperty("java.home") + "/bin/javaw.exe");
						if(!local.exists() || !local.isFile()) {
							throw new IOException("Error locating PTexEdit");
						}
						if(!jre.exists() || !jre.isFile()) {
							throw new IOException("Error locating JRE");
						}
						
						String localPath = local.getCanonicalPath();
						String jrePath =  jre.getCanonicalPath();
						
						if(!isAdmin()) {
							int result = showError("PTexEdit must be run as administrator to perform this operation, would you like to relaunch as admin?"
									+ "\nYou will need to run this command again after restarting!", 
									mOptionsRegisterPTexEdit.getText(), new Object[] {"Yes","No"}, "Yes");
							
							if(result == 0) {
								Runtime.getRuntime().exec("powershell Start-Process javaw.exe -Argument '-jar \\\""+localPath+"\\\"' -verb RunAs");
								System.exit(0);
							}
							return;
						}
						
						
						String command1 = "cmd /c assoc .papa=PAPAFile";
						String command2 = "cmd /c ftype PAPAFile="+jrePath +" -jar \""+localPath+"\" %1";
						String res = "Successfully associated papa with PTexEdit";
						if(associationState == 2) {
							command1 = "cmd /c assoc .papa=";
							command2 = "cmd /c ftype PAPAFile=";
							res = "Successfully dissociated papa with PTexEdit";
						}
						Runtime.getRuntime().exec(command1);
						Runtime.getRuntime().exec(command2);
						optionBox(res, "Success", new Object[] {"OK"}, "OK");
					} catch (URISyntaxException | IOException e1) {
						showError("Error: "+e1.toString(), "Failed to associate", new Object[] {"OK"}, "OK");
					}
					if(associationState == 1) {
						associationState = 2;
					} else {
						associationState = 1;
					}
					mOptionsRegisterPTexEdit.setText(associationState == 1 ? name1 : name2);
					
				});
				mOptionsRegisterPTexEdit.setMnemonic('o');
			}
			
			
			
			JSeparator optionsSeparator = new JSeparator();
			mOptions.add(optionsSeparator);
			
			//JMenuItem mOptionsCacheInfo = new JMenuItem("Remember Image Settings"); TODO
			//mOptions.add(mOptionsCacheInfo);
			
			mOptionsShowRoot = new JCheckBoxMenuItem("Always Show Root");
			mOptions.add(mOptionsShowRoot);
			mOptionsShowRoot.addActionListener((ActionEvent e) -> {
				configSelector.setAlwaysShowRoot(mOptionsShowRoot.isSelected());
			});
			mOptionsShowRoot.setMnemonic('a');
			
			mOptionsAllowEmpty = new JCheckBoxMenuItem("Allow Non-Image Files");
			mOptions.add(mOptionsAllowEmpty);
			mOptionsAllowEmpty.addActionListener((ActionEvent e) -> {
				ALLOW_EMPTY_FILES = mOptionsAllowEmpty.isSelected();
				if(!ALLOW_EMPTY_FILES)
					configSelector.removeEmptyFiles();
			});
			mOptionsAllowEmpty.setMnemonic('n');
			
			mOptionsSuppressWarnings = new JCheckBoxMenuItem("Suppress Warnings");
			mOptions.add(mOptionsSuppressWarnings);
			mOptionsSuppressWarnings.addActionListener((ActionEvent e) -> {
				SUPPRESS_WARNINGS = mOptionsSuppressWarnings.isSelected();
			});
			mOptionsSuppressWarnings.setMnemonic('s');
			
			mOptionsWriteToOutput = new JCheckBoxMenuItem("Use Default Output Folder");
			mOptions.add(mOptionsWriteToOutput);
			mOptionsWriteToOutput.addActionListener((ActionEvent e) -> {
				WRITE_TO_OUTPUT = mOptionsWriteToOutput.isSelected();
			});
			mOptionsWriteToOutput.setMnemonic('d');
			
			JMenu mHelp = new JMenu("Help");
			mHelp.setMnemonic('h');
			add(mHelp);
			
			JMenuItem mHelpAbout = new JMenuItem("About");
			mHelp.add(mHelpAbout);
			mHelpAbout.addActionListener((ActionEvent e) -> {
				JOptionPane.showMessageDialog(APPLICATION_WINDOW, "PTexEdit version: " + VERSION_STRING + "\n"
						+ "Date: "+BUILD_DATE, "About PTexEdit", JOptionPane.INFORMATION_MESSAGE,imageIcon);
			});
			mHelpAbout.setMnemonic('a');
		}
		private Image getImageFromClipboard() {
			try {
				return (Image)clipboard.getData(DataFlavor.imageFlavor);
			} catch (UnsupportedFlavorException | IllegalStateException e) {
				return null;
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		}

		// https://coderanch.com/t/333565/java/BufferedImage-System-Clipboard
		private void transferToClipboard(BufferedImage image) {
			TransferableImage t = new TransferableImage(image);
			try {
				clipboard.setContents(t, clipboardOwner);
			} catch (IllegalStateException e) {
				showError("Failed to copy image to clipboad. Clipboard is unavailable.", "Copy error", new Object[] {"Ok"}, "Ok");
			}
		}
		
		private class TransferableImage implements Transferable {
			Image image;
			
			public TransferableImage(Image image) {
				this.image = image;
			}
			
			@Override
			public DataFlavor[] getTransferDataFlavors() {
				return new DataFlavor[] {DataFlavor.imageFlavor};
			}

			@Override
			public boolean isDataFlavorSupported(DataFlavor flavor) {
				DataFlavor[] flavors = getTransferDataFlavors();
				for(DataFlavor d : flavors)
					if(d.equals(flavor))
						return true;
				return false;
			}

			@Override
			public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
				if(flavor.equals(DataFlavor.imageFlavor))
					if(image!=null)
						return image;
					else
						throw new IOException("Image is null");
				throw new UnsupportedFlavorException(flavor);
			}
			
		}
	}
	
	private class ClipboardListener implements ClipboardOwner {

		@Override
		public void lostOwnership(Clipboard clipboard, Transferable contents) {
			// TODO
		}
		
	}
	
	private class RibbonPanel extends JPanel {
		
		private static final long serialVersionUID = 8964510055023743821L;
		
		private final int SLIDER_TICKS = 12;
		private double zoomScale=1;
		
		private JSlider zoomSlider;
		private JLabel zoomLabel, locationLabel,colourLabelRed,colourLabelGreen,colourLabelBlue,colourLabelAlpha, colourLabel, importLabel;
		
		private String scanLabel = "";
		private String progressLabel = "";
		private String jobLabel = "";
		
		private int importFileCount=0;
		private int importJobCount = 0;
		
		private void updateZoomFromSlider() {
			zoomScale = Math.pow(2, (zoomSlider.getValue()-SLIDER_TICKS/2));
			if(zoomScale>=0.25) // no decimals
				zoomLabel.setText((int)(100*zoomScale)+"%");
			else
				zoomLabel.setText((100*zoomScale)+"%");
			imagePanel.updateZoom();
		}
		
		public void changeZoom(int change) {
			zoomSlider.setValue(zoomSlider.getValue() + change);
		}
		
		public double getZoomScale() {
			return zoomScale;
		}
		
		public void updateMouseLocation() {
			if(imagePanel.isMouseInBounds()) {
				locationLabel.setText(imagePanel.getMouseX()+", "+imagePanel.getMouseY());
				if(imagePanel.isMouseHeld()) {
					Color c = imagePanel.getColourUnderMouse();
					setColourLabel(c);
				}
			} else
				locationLabel.setText("");
			
		}
		
		private void setColourLabel(Color c) {
			colourLabelRed.setText("R="+c.getRed());
			colourLabelGreen.setText("G="+c.getGreen());
			colourLabelBlue.setText("B="+c.getBlue());
			colourLabelAlpha.setText("A="+c.getAlpha());
			colourLabel.setBackground(new Color(c.getRGB(),false));
		}
		
		private class ButtonMouseListener implements MouseListener {
			private JButton j;
			public ButtonMouseListener(JButton j) {
				this.j=j;
			}
			public void mouseReleased(MouseEvent e) {}
			public void mousePressed(MouseEvent e) {}
			public void mouseClicked(MouseEvent e) {}
			public void mouseExited(MouseEvent e) {
				j.setBorderPainted(false);
			}
			
			@Override
			public void mouseEntered(MouseEvent e) {
				j.setBorderPainted(true);
			}
			
		};
		
		private void updateLabel() {
			importLabel.setText(scanLabel+" "+progressLabel+" "+jobLabel);
		}
		
		public void setNumberOfFoundFiles(int number) {
			importFileCount=number;
			scanLabel = "Scanning... "+number+" files found";
			progressLabel = "";
			updateLabel();
		}
		
		public void setTotalNumberOfJobs(int number) {
			importJobCount=number;
			jobLabel = "(Job 0 of "+number+")";
			updateLabel();
		}
		
		public void setActiveJobIndex(int number) {
			jobLabel = "(Job "+number+" of "+importJobCount+")";
			updateLabel();
		}
		
		public void startImport(int number) {
			importFileCount = number;
			setNumberProcessedFiles(0);
		}
		
		public void setNumberProcessedFiles(int number) {
			if(number > importFileCount)
				number = 0; // parallel processing fix (next import starts before the last finished on multi import)
			scanLabel = "";
			progressLabel = "Processed: "+String.format("%s", number)+" of "+String.format("%s", importFileCount);
			updateLabel();
		}
		
		public void endImport() {
			importLabel.setText("");
			scanLabel = "";
			progressLabel = "";
			jobLabel ="";
		}

		public RibbonPanel() {
			setBorder(BorderFactory.createLineBorder(new Color(192, 192, 192)));
			
			SpringLayout layout = new SpringLayout();
			setLayout(layout);
			
			JButton plusButton = new JButton();
			plusButton.setIcon(plusIcon);
			layout.putConstraint(SpringLayout.NORTH, plusButton, 0, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.EAST, plusButton, 0, SpringLayout.EAST, this);
			layout.putConstraint(SpringLayout.SOUTH, plusButton, 0, SpringLayout.SOUTH, this);
			layout.putConstraint(SpringLayout.WEST, plusButton, -25, SpringLayout.EAST, this);
			plusButton.setOpaque(false);
			plusButton.setBorderPainted(false);
			plusButton.setFocusPainted(false);
			plusButton.setBackground(new Color(0f,0f,0f,0f));
			plusButton.setForeground(new Color(0f,0f,0f,0f));
			plusButton.addMouseListener(new ButtonMouseListener(plusButton));
			add(plusButton);
			
			plusButton.addActionListener((ActionEvent e) -> {
				changeZoom(1);
			});
			
			zoomSlider = new JSlider();
			layout.putConstraint(SpringLayout.NORTH, zoomSlider, 0, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.EAST, zoomSlider, 0, SpringLayout.WEST, plusButton);
			layout.putConstraint(SpringLayout.SOUTH, zoomSlider, 0, SpringLayout.SOUTH, this);
			layout.putConstraint(SpringLayout.WEST, zoomSlider, -5 - SLIDER_TICKS*10, SpringLayout.EAST, this);
			zoomSlider.setMajorTickSpacing(1);
			zoomSlider.setMaximum(SLIDER_TICKS);
			zoomSlider.setValue(SLIDER_TICKS/2);
			zoomSlider.setPaintTicks(true);
			zoomSlider.setSnapToTicks(true);
			add(zoomSlider);
			
			zoomSlider.addChangeListener(new ChangeListener() {
				public void stateChanged(ChangeEvent e) {
					updateZoomFromSlider();
				}
			});
			
			JButton minusButton = new JButton();
			minusButton.setIcon(minusIcon);
			layout.putConstraint(SpringLayout.NORTH, minusButton, 0, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.EAST, minusButton, 0, SpringLayout.WEST, zoomSlider);
			layout.putConstraint(SpringLayout.SOUTH, minusButton, 0, SpringLayout.SOUTH, this);
			layout.putConstraint(SpringLayout.WEST, minusButton, -25, SpringLayout.WEST, zoomSlider);
			minusButton.setOpaque(false);
			minusButton.setBorderPainted(false);
			minusButton.setFocusPainted(false);
			minusButton.setBackground(new Color(0f,0f,0f,0f));
			minusButton.setForeground(new Color(0f,0f,0f,0f));
			minusButton.addMouseListener(new ButtonMouseListener(minusButton));
			add(minusButton);
			
			minusButton.addActionListener((ActionEvent e) -> {
				changeZoom(-1);
			});
			
			JSeparator sep1 = new JSeparator();
			layout.putConstraint(SpringLayout.NORTH, sep1, 0, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.EAST, sep1, 0, SpringLayout.WEST, minusButton); // -20
			layout.putConstraint(SpringLayout.SOUTH, sep1, 0, SpringLayout.SOUTH, this);
			layout.putConstraint(SpringLayout.WEST, sep1, -2, SpringLayout.WEST, minusButton);
			sep1.setOrientation(JSlider.VERTICAL);
			add(sep1);
			
			zoomLabel = new JLabel();
			layout.putConstraint(SpringLayout.NORTH, zoomLabel, 0, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.EAST, zoomLabel, 0, SpringLayout.WEST, sep1);
			layout.putConstraint(SpringLayout.SOUTH, zoomLabel, 0, SpringLayout.SOUTH, this);
			layout.putConstraint(SpringLayout.WEST, zoomLabel, -60, SpringLayout.WEST, sep1);
			zoomLabel.setText("100%");
			add(zoomLabel);
			
			JSeparator sep2 = new JSeparator();
			layout.putConstraint(SpringLayout.NORTH, sep2, 0, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.EAST, sep2, -5, SpringLayout.WEST, zoomLabel);
			layout.putConstraint(SpringLayout.SOUTH, sep2, 0, SpringLayout.SOUTH, this);
			layout.putConstraint(SpringLayout.WEST, sep2, -7, SpringLayout.WEST, zoomLabel);
			sep2.setOrientation(JSlider.VERTICAL);
			add(sep2);
			
			locationLabel = new JLabel();
			layout.putConstraint(SpringLayout.NORTH, locationLabel, 0, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.EAST, locationLabel, 0, SpringLayout.WEST, sep2);
			layout.putConstraint(SpringLayout.SOUTH, locationLabel, 0, SpringLayout.SOUTH, this);
			layout.putConstraint(SpringLayout.WEST, locationLabel, -68, SpringLayout.WEST, sep2);
			add(locationLabel);
			
			JSeparator sep3 = new JSeparator();
			layout.putConstraint(SpringLayout.NORTH, sep3, 0, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.EAST, sep3, -5, SpringLayout.WEST, locationLabel);
			layout.putConstraint(SpringLayout.SOUTH, sep3, 0, SpringLayout.SOUTH, this);
			layout.putConstraint(SpringLayout.WEST, sep3, -7, SpringLayout.WEST, locationLabel);
			sep3.setOrientation(JSlider.VERTICAL);
			add(sep3);
			
			final JPopupMenu popupColour = new JPopupMenu();
	        JMenuItem item = new JMenuItem("Copy as Hex");
	        item.setMnemonic(KeyEvent.VK_H);
	        item.addActionListener((ActionEvent e)-> {
	        	Color c = colourLabel.getBackground();
	        	int i = (c.getRed()<<16) | (c.getGreen()<<8) | c.getBlue();
	        	String hex = Integer.toHexString(i);
	        	hex = "000000".substring(hex.length()) + hex;
	        	StringSelection s = new StringSelection(hex);
	        	clipboard.setContents(s, clipboardOwner);
	        });
	        popupColour.add(item);
	        
	        item = new JMenuItem("Copy as RGB");
	        item.setMnemonic(KeyEvent.VK_R);
	        item.addActionListener((ActionEvent e)-> {
	        	Color c = colourLabel.getBackground();
	        	String rgb = c.getRed()+" "+c.getGreen()+" "+c.getBlue();
	        	StringSelection s = new StringSelection(rgb);
	        	clipboard.setContents(s, clipboardOwner);
	        });
	        popupColour.add(item);
			
			colourLabel = new JLabel();
			layout.putConstraint(SpringLayout.NORTH, colourLabel, 0, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.EAST, colourLabel, 0, SpringLayout.WEST, sep3);
			layout.putConstraint(SpringLayout.SOUTH, colourLabel, 0, SpringLayout.SOUTH, this);
			layout.putConstraint(SpringLayout.WEST, colourLabel, -20, SpringLayout.WEST, sep3);
			colourLabel.setOpaque(true);
			//colourLabel.setBorder(BorderFactory.createLineBorder(new Color(192, 192, 192)));;
			add(colourLabel);
			
			colourLabel.addMouseListener(new MouseListener() {
				boolean armed = false;
				@Override
				public void mouseReleased(MouseEvent e) {
					if(armed) {
						Point m = colourLabel.getMousePosition();
						popupColour.show(colourLabel, m.x ,m.y);
					}
				}
				
				@Override
				public void mousePressed(MouseEvent e) {
					armed = true;
				}
				
				@Override
				public void mouseExited(MouseEvent e) {
					armed = false;
				}
				
				@Override
				public void mouseEntered(MouseEvent e) {}
				
				@Override
				public void mouseClicked(MouseEvent e) {}
			});
			
			// i hated all the monospaced fonts so i'm just making 4 JLabels instead.
			
			colourLabelAlpha = new JLabel();
			layout.putConstraint(SpringLayout.NORTH, colourLabelAlpha, 0, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.EAST, colourLabelAlpha, 0, SpringLayout.WEST, colourLabel);
			layout.putConstraint(SpringLayout.SOUTH, colourLabelAlpha, 0, SpringLayout.SOUTH, this);
			layout.putConstraint(SpringLayout.WEST, colourLabelAlpha, -45, SpringLayout.WEST, colourLabel);
			add(colourLabelAlpha);
			
			colourLabelBlue = new JLabel();
			layout.putConstraint(SpringLayout.NORTH, colourLabelBlue, 0, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.EAST, colourLabelBlue, 0, SpringLayout.WEST, colourLabelAlpha);
			layout.putConstraint(SpringLayout.SOUTH, colourLabelBlue, 0, SpringLayout.SOUTH, this);
			layout.putConstraint(SpringLayout.WEST, colourLabelBlue, -45, SpringLayout.WEST, colourLabelAlpha);
			add(colourLabelBlue);
			
			colourLabelGreen = new JLabel();
			layout.putConstraint(SpringLayout.NORTH, colourLabelGreen, 0, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.EAST, colourLabelGreen, 0, SpringLayout.WEST, colourLabelBlue);
			layout.putConstraint(SpringLayout.SOUTH, colourLabelGreen, 0, SpringLayout.SOUTH, this);
			layout.putConstraint(SpringLayout.WEST, colourLabelGreen, -45, SpringLayout.WEST, colourLabelBlue);
			add(colourLabelGreen);
			
			colourLabelRed = new JLabel();
			layout.putConstraint(SpringLayout.NORTH, colourLabelRed, 0, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.EAST, colourLabelRed, 0, SpringLayout.WEST, colourLabelGreen);
			layout.putConstraint(SpringLayout.SOUTH, colourLabelRed, 0, SpringLayout.SOUTH, this);
			layout.putConstraint(SpringLayout.WEST, colourLabelRed, -45, SpringLayout.WEST, colourLabelGreen);
			add(colourLabelRed);
			
			JSeparator sep4 = new JSeparator();
			layout.putConstraint(SpringLayout.NORTH, sep4, 0, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.EAST, sep4, -5, SpringLayout.WEST, colourLabelRed);
			layout.putConstraint(SpringLayout.SOUTH, sep4, 0, SpringLayout.SOUTH, this);
			layout.putConstraint(SpringLayout.WEST, sep4, -7, SpringLayout.WEST, colourLabelRed);
			sep4.setOrientation(JSlider.VERTICAL);
			add(sep4);
			
			importLabel = new JLabel("");
			layout.putConstraint(SpringLayout.NORTH, importLabel, 0, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.EAST, importLabel, -5, SpringLayout.WEST, sep4);
			layout.putConstraint(SpringLayout.SOUTH, importLabel, 0, SpringLayout.SOUTH, this);
			layout.putConstraint(SpringLayout.WEST, importLabel, 5, SpringLayout.WEST, this);
			add(importLabel);
			
			setColourLabel(Color.black);
		}
	}
	
	private class LimitTextFieldDocument extends PlainDocument {
		private static final long serialVersionUID = 5103888339135962097L;
		private final int max;
		
		public LimitTextFieldDocument(int max) {
			super();
			this.max = max;
		}
		
		@Override
		public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
			if(str == null) {
				return;
			}
			
			if(getLength() + str.length() <= max) {
				super.insertString(offs, str, a);
			}
		}
		
		
	}
	
	private class ConfigPanelTop extends JPanel {
		
		private static final long serialVersionUID = 14053510615893605L;
		private JTextField imageName, filePath, fileSignature;
		
		private JSpinner spinnerImage, spinnerMipmap;
		
		private JCheckBox srgb;
		
		private JLabel filePathLabel;
		
		private final Border defaultButtonBorder;
		
		public int getActiveTextureIndex(PapaFile p) {
			if(p==null || activeTexture == null)
				return -1;
			for(int i =0;i<p.getNumTextures();i++)
				if(p.getTexture(i)==activeTexture)
					return i;
			return -1;
		}
		
		public void applySettings(PapaFile pf, int image, PapaTexture tex, boolean changed) {
			if(changed) {
				setTextureCount(pf.getNumTextures());
			}
			
			filePath.setBorder(defaultButtonBorder);
			if(pf.isLinkedFile()) {
				filePathLabel.setEnabled(true);
				filePath.setEnabled(true);
				filePath.setText(pf.getRelativeFileName());
				if(! pf.getParent().isLinkedFileReferenced(pf))
					filePath.setBorder(BorderFactory.createLineBorder(Color.red));
			} else {
				filePathLabel.setEnabled(false);
				filePath.setEnabled(false);
				filePath.setText("");
			}
			fileSignature.setText(pf.getSignature());
			spinnerImage.setValue(image);
			
			applySettings(tex);
		}
		
		private void applySettings(PapaTexture tex) {
			if(tex==null) {
				spinnerImage.setEnabled(false);
				spinnerMipmap.setEnabled(false);
				imageName.setEnabled(false);
				imageName.setText("");
				filePath.setEnabled(false);
				filePathLabel.setEnabled(false);
				fileSignature.setEnabled(false);
				srgb.setEnabled(false);
				srgb.setSelected(false);
				//resizeButton.setEnabled(false);
				//changeFormatButton.setEnabled(false);
				imagePanel.setImage(null);
				return;
			}
			
			PapaTexture t = tex;
			boolean enable = tex!=null;
			
			spinnerMipmap.setEnabled(enable);
			imageName.setEnabled(enable);
			srgb.setEnabled(enable);
			fileSignature.setEnabled(true);
			//resizeButton.setEnabled(enable);
			//changeFormatButton.setEnabled(enable);
			
			imageName.setBorder(defaultButtonBorder);
			if(!enable) {
				imagePanel.unload();
				return;
			}
			
			if(t.isLinked()) {
				spinnerMipmap.setEnabled(false);
				srgb.setEnabled(false);
				//resizeButton.setEnabled(false);
				//changeFormatButton.setEnabled(false);
				imageName.setEnabled(true);
				imageName.setText(t.getName());
				if(!t.linkValid())
					imageName.setBorder(BorderFactory.createLineBorder(Color.red));
				t = t.getLinkedTexture();
			} else {
				imageName.setText(t.getName());
			}

			setMipmapCount(t.getMips());
			srgb.setSelected(t.getSRGB());
			
			imagePanel.setImage(t);
			imagePanel.setImageIndex(0);
			
		}
		
		private void refreshActiveTexture() {
			if(activeTexture.isLinked() && ! activeTexture.linkValid()) {
				if(imageName.getBorder()==defaultButtonBorder)
					imagePanel.setImage(activeTexture.getLinkedTexture());
				imageName.setBorder(BorderFactory.createLineBorder(Color.red));
			} else {
				if(imageName.getBorder()!=defaultButtonBorder)
					imagePanel.setImage(activeTexture.getLinkedTexture());
				imageName.setBorder(defaultButtonBorder);
			}
			configSelector.selectedNodeChanged();
			configSelector.fileTree.repaint();
		}
		
		private void refreshActiveFileLinks() {
			if(activeFile.isLinkedFile() && ! activeFile.getParent().isLinkedFileReferenced(activeFile))
				filePath.setBorder(BorderFactory.createLineBorder(Color.red));
			else 
				filePath.setBorder(defaultButtonBorder);
			configSelector.selectedNodeChanged();
		}
		
		public void unload() {
			spinnerImage.setEnabled(false);
			spinnerImage.setValue(0);
			spinnerMipmap.setEnabled(false);
			spinnerMipmap.setValue(0);
			srgb.setEnabled(false);
			srgb.setSelected(false);
			imageName.setEnabled(false);
			imageName.setText("");
			filePathLabel.setEnabled(false);
			filePath.setEnabled(false);
			filePath.setText("");
			fileSignature.setEnabled(false);
			fileSignature.setText("");
			//resizeButton.setEnabled(false);
			//changeFormatButton.setEnabled(false);
			imagePanel.setImage(null);
			imageName.setBorder(defaultButtonBorder);
		}
		
		private void setTextureCount(int newMax) {
			if(newMax>1) {
				spinnerImage.setEnabled(true);
				spinnerImage.setModel(new SpinnerNumberModel(new Integer(0), new Integer(0), new Integer(newMax-1), new Integer(1)));
			}
			else
				spinnerImage.setEnabled(false);
			
		}
		
		public int getSelectedFileImageIndex() {
			return (int)spinnerImage.getValue();
		}
		
		private void setMipmapCount(int mips) {
			if(mips == 0)
				spinnerMipmap.setEnabled(false);
			else
				spinnerMipmap.setEnabled(true);
			spinnerMipmap.setModel(new SpinnerNumberModel(new Integer(0), new Integer(0), new Integer(mips), new Integer(1)));
		}
		
		public int getSelectedMipmapIndex() {
			return (int)spinnerMipmap.getValue();
		}
		
		public ConfigPanelTop() {
			setBackground(new Color(240, 240, 240));
			setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(192, 192, 192)),"Settings"));
			
			SpringLayout layout = new SpringLayout();
			setLayout(layout);
			
			spinnerImage = new JSpinner();
			layout.putConstraint(SpringLayout.NORTH, spinnerImage, 5, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.WEST, spinnerImage, 75, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.SOUTH, spinnerImage, 25, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.EAST, spinnerImage, -10, SpringLayout.EAST, this);
			add(spinnerImage);
			spinnerImage.setEnabled(false);
			
			spinnerMipmap = new JSpinner();
			layout.putConstraint(SpringLayout.NORTH, spinnerMipmap, 5, SpringLayout.SOUTH, spinnerImage);
			layout.putConstraint(SpringLayout.WEST, spinnerMipmap, 75, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.SOUTH, spinnerMipmap, 25, SpringLayout.SOUTH, spinnerImage);
			layout.putConstraint(SpringLayout.EAST, spinnerMipmap, -10, SpringLayout.EAST, this);
			spinnerMipmap.setEnabled(false);
			add(spinnerMipmap);
			
			imageName = new JTextField();
			layout.putConstraint(SpringLayout.NORTH, imageName, 5, SpringLayout.SOUTH, spinnerMipmap);
			layout.putConstraint(SpringLayout.WEST, imageName, 75, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.SOUTH, imageName, 25, SpringLayout.SOUTH, spinnerMipmap);
			layout.putConstraint(SpringLayout.EAST, imageName, -10, SpringLayout.EAST, this);
			imageName.setColumns(1);
			imageName.setEnabled(false);
			add(imageName);
			
			imageName.getDocument().addDocumentListener(new DocumentListener() {
				
				@Override
				public void removeUpdate(DocumentEvent e) {
					update();
				}
				
				@Override
				public void insertUpdate(DocumentEvent e) {
					update();
				}
				
				@Override
				public void changedUpdate(DocumentEvent e) {
					update();
				}
				
				private void update() {
					if(activeTexture==null)
						return;
					String name = imageName.getText();
					if(name.equals(""))
						name = "<no name>";
					activeTexture.setName(name);
					refreshActiveTexture();
				}
			});
			
			defaultButtonBorder = imageName.getBorder();
			
			filePath = new JTextField();
			layout.putConstraint(SpringLayout.NORTH, filePath, 5, SpringLayout.SOUTH, imageName);
			layout.putConstraint(SpringLayout.WEST, filePath, 75, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.SOUTH, filePath, 25, SpringLayout.SOUTH, imageName);
			layout.putConstraint(SpringLayout.EAST, filePath, -10, SpringLayout.EAST, this);
			filePath.setColumns(1);
			filePath.setEnabled(false);
			add(filePath);
			
			filePath.getDocument().addDocumentListener(new DocumentListener() {
				
				@Override
				public void removeUpdate(DocumentEvent e) {
					update();
				}
				
				@Override
				public void insertUpdate(DocumentEvent e) {
					update();
				}
				
				@Override
				public void changedUpdate(DocumentEvent e) {
					update();
				}
				
				private void update() {
					if(activeFile==null || ! filePath.isEnabled())
						return;
					String path = filePath.getText();
					if(path.equals(""))
						path = "<no path>";
					activeFile.setLocationRelative(path);
					refreshActiveFileLinks();
				}
			});
			
			srgb = new JCheckBox("SRGB");
			layout.putConstraint(SpringLayout.NORTH, srgb, 5, SpringLayout.SOUTH, filePath);
			layout.putConstraint(SpringLayout.WEST, srgb, 15, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.SOUTH, srgb, 25, SpringLayout.SOUTH, filePath);
			layout.putConstraint(SpringLayout.EAST, srgb, 75, SpringLayout.WEST, this);
			srgb.setEnabled(false);
			srgb.addActionListener((ActionEvent e) -> {
				activeTexture.setSRGB(srgb.isSelected());
			});
			add(srgb);
			
			fileSignature = new JTextField();
			layout.putConstraint(SpringLayout.NORTH, fileSignature, 5, SpringLayout.SOUTH, filePath);
			layout.putConstraint(SpringLayout.WEST, fileSignature, -100, SpringLayout.EAST, this);
			layout.putConstraint(SpringLayout.SOUTH, fileSignature, 25, SpringLayout.SOUTH, filePath);
			layout.putConstraint(SpringLayout.EAST, fileSignature, -10, SpringLayout.EAST, this);
			fileSignature.setColumns(1);
			fileSignature.setEnabled(false);
			fileSignature.setDocument(new LimitTextFieldDocument(6));
			add(fileSignature);
			
			fileSignature.getDocument().addDocumentListener(new DocumentListener() {
				
				@Override
				public void removeUpdate(DocumentEvent e) {
					update();
				}
				
				@Override
				public void insertUpdate(DocumentEvent e) {
					update();
				}
				
				@Override
				public void changedUpdate(DocumentEvent e) {
					update();
				}
				
				private void update() {
					if(activeFile==null)
						return;
					String signature = fileSignature.getText();
					activeFile.setSignature(signature);
				}
			});
			
			
			JLabel imageSpinnerLabel = new JLabel("Image:");
			layout.putConstraint(SpringLayout.NORTH, imageSpinnerLabel, 2, SpringLayout.NORTH, spinnerImage);
			layout.putConstraint(SpringLayout.WEST, imageSpinnerLabel, 20, SpringLayout.WEST, this);
			add(imageSpinnerLabel);
			
			JLabel mipmapSpinerLabel = new JLabel("Mipmap:");
			layout.putConstraint(SpringLayout.NORTH, mipmapSpinerLabel, 2, SpringLayout.NORTH, spinnerMipmap);
			layout.putConstraint(SpringLayout.WEST, mipmapSpinerLabel, 20, SpringLayout.WEST, this);
			add(mipmapSpinerLabel);
			
			JLabel imageNameLabel = new JLabel("Name:");
			layout.putConstraint(SpringLayout.NORTH, imageNameLabel, 2, SpringLayout.NORTH, imageName);
			layout.putConstraint(SpringLayout.WEST, imageNameLabel, 20, SpringLayout.WEST, this);
			add(imageNameLabel);
			
			filePathLabel = new JLabel("Filepath:");
			layout.putConstraint(SpringLayout.NORTH, filePathLabel, 2, SpringLayout.NORTH, filePath);
			layout.putConstraint(SpringLayout.WEST, filePathLabel, 20, SpringLayout.WEST, this);
			add(filePathLabel);
			
			filePathLabel.setEnabled(false);
			
			spinnerMipmap.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					if(activeFile!=null)
						imagePanel.setImageIndex(getSelectedMipmapIndex());
				}
			});
			
			spinnerImage.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					if(activeFile!=null && activeFile.getNumTextures()!=0) {
						PapaTexture target = activeFile.getTexture(getSelectedFileImageIndex());
						if(target!=activeTexture)
							setActiveTexture(target);
					}
				}
			});
			
			JLabel fileSignatureLabel = new JLabel("Signature:");
			layout.putConstraint(SpringLayout.NORTH, fileSignatureLabel, 2, SpringLayout.NORTH, fileSignature);
			layout.putConstraint(SpringLayout.EAST, fileSignatureLabel, -5, SpringLayout.WEST, fileSignature);
			add(fileSignatureLabel);
		}
	}
	
	private class ConfigPanelBottom extends JPanel {

		private static final long serialVersionUID = 1721448070098959177L;
		
		private JLabel versionValueLabel, fileSizeValueLabel, imagesValueLabel, widthValueLabel, heightValueLabel, formatValueLabel, mipmapsValueLabel;
		
		private final Color defaultTextColour;
		
		private void applySettings(PapaFile pf, int image, PapaTexture tex, boolean changed) {
			if(changed) {
				versionValueLabel.setText(pf.getVersion());
				if(pf.getFileSize()<1024)
					fileSizeValueLabel.setText(pf.getFileSize()+" bytes");
				else if (pf.getFileSize()<1048576)
					fileSizeValueLabel.setText(pf.getFileSize()/1024+" KB");
				else
					fileSizeValueLabel.setText(String.format("%.2f",(double)pf.getFileSize()/1048576d)+" MB");
				imagesValueLabel.setText(""+pf.getNumTextures());
			}
			applySettings(tex);
		}
		
		private void applySettings(PapaTexture tex) {
			if(tex==null) {
				widthValueLabel.setText("");
				heightValueLabel.setText("");
				formatValueLabel.setText("");
				mipmapsValueLabel.setText("");
				return;
			}
			PapaTexture t = tex;
			setTextColour(defaultTextColour);
			if(t.isLinked()) {
				if(t.linkValid()) {
					t = t.getLinkedTexture();
				} else {
					widthValueLabel.setText("Invalid Link");
					heightValueLabel.setText("Invalid Link");
					formatValueLabel.setText("Invalid Link");
					mipmapsValueLabel.setText("Invalid Link");
					setTextColour(Color.red);
					return;
				}
			}
			
			widthValueLabel.setText(""+t.getWidth());
			heightValueLabel.setText(""+t.getHeight());
			formatValueLabel.setText(t.getFormat());
			mipmapsValueLabel.setText(""+t.getMips());
		}
		
		private void setTextColour(Color c) {
			widthValueLabel.setForeground(c);
			heightValueLabel.setForeground(c);
			formatValueLabel.setForeground(c);
			mipmapsValueLabel.setForeground(c);
		}
		
		private void unload() {
			versionValueLabel.setText("");
			fileSizeValueLabel.setText("");
			imagesValueLabel.setText("");
			widthValueLabel.setText("");
			heightValueLabel.setText("");
			formatValueLabel.setText("");
			mipmapsValueLabel.setText("");
		}

		public ConfigPanelBottom() {

			setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(192, 192, 192)),"Info"));
			
			SpringLayout layout = new SpringLayout();
			setLayout(layout);
			
			JLabel fileInfoLabel = new JLabel("File Info:");
			layout.putConstraint(SpringLayout.NORTH, fileInfoLabel, 5, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.WEST, fileInfoLabel, 10, SpringLayout.WEST, this);
			add(fileInfoLabel);
			
			JLabel versionLabel = new JLabel("Version:");
			layout.putConstraint(SpringLayout.NORTH, versionLabel, 30, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.WEST, versionLabel, 20, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.EAST, versionLabel, 90, SpringLayout.WEST, this);
			add(versionLabel);
			
			versionValueLabel = new JLabel("");
			layout.putConstraint(SpringLayout.NORTH, versionValueLabel, 0, SpringLayout.NORTH, versionLabel);
			layout.putConstraint(SpringLayout.WEST, versionValueLabel, 100, SpringLayout.WEST, this);
			add(versionValueLabel);
			
			JLabel fileSizeLabel = new JLabel("File Size:");
			layout.putConstraint(SpringLayout.NORTH, fileSizeLabel, 25, SpringLayout.NORTH, versionValueLabel);
			layout.putConstraint(SpringLayout.WEST, fileSizeLabel, 20, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.EAST, fileSizeLabel, 90, SpringLayout.WEST, this);
			add(fileSizeLabel);
			
			fileSizeValueLabel = new JLabel("");
			layout.putConstraint(SpringLayout.NORTH, fileSizeValueLabel, 0, SpringLayout.NORTH, fileSizeLabel);
			layout.putConstraint(SpringLayout.WEST, fileSizeValueLabel, 100, SpringLayout.WEST, this);
			add(fileSizeValueLabel);
			
			JLabel imagesLabel = new JLabel("Images:");
			layout.putConstraint(SpringLayout.NORTH, imagesLabel, 25, SpringLayout.NORTH, fileSizeLabel);
			layout.putConstraint(SpringLayout.WEST, imagesLabel, 20, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.EAST, imagesLabel, 90, SpringLayout.WEST, this);
			add(imagesLabel);
			
			imagesValueLabel = new JLabel("");
			layout.putConstraint(SpringLayout.NORTH, imagesValueLabel, 0, SpringLayout.NORTH, imagesLabel);
			layout.putConstraint(SpringLayout.WEST, imagesValueLabel, 100, SpringLayout.WEST, this);
			add(imagesValueLabel);
			
			JSeparator separator_3 = new JSeparator();
			layout.putConstraint(SpringLayout.NORTH, separator_3, 13, SpringLayout.SOUTH, imagesLabel);
			layout.putConstraint(SpringLayout.WEST, separator_3, 5, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.SOUTH, separator_3, 15, SpringLayout.SOUTH, imagesLabel);
			layout.putConstraint(SpringLayout.EAST, separator_3, -5, SpringLayout.EAST, this);
			add(separator_3);
			
			JLabel imageInfoLabel = new JLabel("Image Info:");
			layout.putConstraint(SpringLayout.NORTH, imageInfoLabel, 10, SpringLayout.SOUTH, separator_3);
			layout.putConstraint(SpringLayout.WEST, imageInfoLabel, 10, SpringLayout.WEST, this);
			add(imageInfoLabel);
			
			JLabel widthLabel = new JLabel("Width:");
			layout.putConstraint(SpringLayout.NORTH, widthLabel, 35, SpringLayout.NORTH, separator_3);
			layout.putConstraint(SpringLayout.WEST, widthLabel, 20, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.EAST, widthLabel, 90, SpringLayout.WEST, this);
			add(widthLabel);
			
			widthValueLabel = new JLabel("");
			layout.putConstraint(SpringLayout.NORTH, widthValueLabel, 0, SpringLayout.NORTH, widthLabel);
			layout.putConstraint(SpringLayout.WEST, widthValueLabel, 100, SpringLayout.WEST, this);
			add(widthValueLabel);
			
			JLabel heightLabel = new JLabel("Height:");
			layout.putConstraint(SpringLayout.NORTH, heightLabel, 25, SpringLayout.NORTH, widthLabel);
			layout.putConstraint(SpringLayout.WEST, heightLabel, 20, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.EAST, heightLabel, 90, SpringLayout.WEST, this);
			add(heightLabel);
			
			heightValueLabel = new JLabel("");
			layout.putConstraint(SpringLayout.NORTH, heightValueLabel, 0, SpringLayout.NORTH, heightLabel);
			layout.putConstraint(SpringLayout.WEST, heightValueLabel, 100, SpringLayout.WEST, this);
			add(heightValueLabel);
			
			JLabel formatLabel = new JLabel("Format:");
			layout.putConstraint(SpringLayout.NORTH, formatLabel, 25, SpringLayout.NORTH, heightLabel);
			layout.putConstraint(SpringLayout.WEST, formatLabel, 20, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.EAST, formatLabel, 90, SpringLayout.WEST, this);
			add(formatLabel);
			
			formatValueLabel = new JLabel("");
			layout.putConstraint(SpringLayout.NORTH, formatValueLabel, 0, SpringLayout.NORTH, formatLabel);
			layout.putConstraint(SpringLayout.WEST, formatValueLabel, 100, SpringLayout.WEST, this);
			add(formatValueLabel);
			
			JLabel mipmapsLabel = new JLabel("Mipmaps:");
			layout.putConstraint(SpringLayout.NORTH, mipmapsLabel, 25, SpringLayout.NORTH, formatLabel);
			layout.putConstraint(SpringLayout.WEST, mipmapsLabel, 20, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.EAST, mipmapsLabel, 90, SpringLayout.WEST, this);
			add(mipmapsLabel);
			
			mipmapsValueLabel = new JLabel("");
			layout.putConstraint(SpringLayout.NORTH, mipmapsValueLabel, 0, SpringLayout.NORTH, mipmapsLabel);
			layout.putConstraint(SpringLayout.WEST, mipmapsValueLabel, 100, SpringLayout.WEST, this);
			add(mipmapsValueLabel);
			
			defaultTextColour = mipmapsValueLabel.getForeground();
			
		}
	}
	
	private class ConfigPanelSelector extends JPanel {
		
		private static final long serialVersionUID = 2925995949876826577L;
		private final DefaultMutableTreeNode root;
		private DefaultMutableTreeNode lastSelected;
		private final JTree fileTree;
		private final JScrollPane treeScrollPane;
		private final DefaultTreeModel treeModel;
		private boolean alwaysShowRoot;
		private HashSet<TreePath> expandedPaths = new HashSet<TreePath>();
		private JMenuItem removeSelected, addLinked;
		
		public DefaultMutableTreeNode refreshEntry(DefaultMutableTreeNode node) {
			PapaFile p = getAssociatedPapaFile(node);
			if(p==null)
				return node;
			return refreshEntry(node, p);
		}
		
		public void unload() {
			removeSelected.setEnabled(false);
			addLinked.setEnabled(false);
			
		}

		private void applySettings(PapaFile pf, int image, PapaTexture tex, boolean changed) {
			removeSelected.setEnabled(true);
			addLinked.setEnabled(!pf.isLinkedFile());
		}

		public DefaultMutableTreeNode refreshEntry(DefaultMutableTreeNode node, PapaFile p) {
			DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
			if(parent == null)
				parent = root; 
			if(p==null)
				return node;
			
			if(p.getNumTextures()!=0 || ALLOW_EMPTY_FILES) {
				removeChildrenFromNode(node);
				DefaultMutableTreeNode first = generateHierarchy(p);
				for(DefaultMutableTreeNode n : getChildren(first))
					node.add(n);
				node.setUserObject(first.getUserObject());
			}
			reloadAndExpand(parent);
			select(new TreePath(node.getPath()));
			return node;
		}

		private void reloadAndExpand(DefaultMutableTreeNode node) {
			TreePath[] selected = fileTree.getSelectionPaths();
			TreePath mainSelect = fileTree.getSelectionPath();
			ArrayList<TreePath> selectedArray = new ArrayList<TreePath>();
			if(selected!=null)
				for(TreePath t : selected)
					selectedArray.add(t);
			if(mainSelect!=null) {
				selectedArray.remove(mainSelect);
				selectedArray.add(0,mainSelect);
			}
			
			@SuppressWarnings("unchecked")
			HashSet<TreePath> expanded = (HashSet<TreePath>) expandedPaths.clone();
			
			treeModel.reload(node);
			
			// Expanding paths
			for(TreePath t : expanded) {
				fileTree.expandPath(t);
			}
			
			removeIfAncestor(root, expanded); // find all paths which do not exist anymore
			for(TreePath t : expanded) {
				TreePath similar = findSimilar(t);
				if(similar != null) {
					fileTree.expandPath(similar);
				}
			}
			
			expandedPaths.removeAll(expanded); // remove non existent paths
			
			ArrayList<TreePath> toSelect = new ArrayList<TreePath>();
			for(TreePath t : selectedArray) {
				TreePath similar = findSimilar(t);
				if(similar != null)
					toSelect.add(similar);
			}
			fileTree.setSelectionPaths(toSelect.toArray(new TreePath[toSelect.size()]));
		}

		private void removeIfAncestor(DefaultMutableTreeNode node, Collection<TreePath> c) {
			for(Iterator<TreePath> treeIterator = c.iterator();treeIterator.hasNext();) {
				DefaultMutableTreeNode n = (DefaultMutableTreeNode) treeIterator.next().getLastPathComponent();
				if(n.isNodeAncestor(node))
					treeIterator.remove();
			}
		}

		private TreePath findSimilar(TreePath t) { // expensive operation
			DefaultMutableTreeNode n = (DefaultMutableTreeNode) t.getLastPathComponent();
			if(n.isNodeAncestor(root))
				return t;
			
			TreeNode[] newPath = new TreeNode[t.getPathCount()];
			newPath[0] = root;
			
			DefaultMutableTreeNode current = root;
			for(int i = 1;i<t.getPathCount();i++) {
				DefaultMutableTreeNode test = (DefaultMutableTreeNode) t.getPathComponent(i);
				Object testComp = test.getUserObject();
				
				DefaultMutableTreeNode child = getSimilarChildFromObject(current, testComp);
				if(child==null)
					return null;
				
				newPath[i] = child;
				current = child;
			}
			return new TreePath(newPath);
		}
		
		private DefaultMutableTreeNode getSimilarChildFromObject(DefaultMutableTreeNode host, Object o) {
			for(@SuppressWarnings("unchecked") Enumeration<DefaultMutableTreeNode> e = host.children();e.hasMoreElements();) {
				DefaultMutableTreeNode child = e.nextElement();
				Object childComp = child.getUserObject();
				if(o.getClass()==String.class ? o.equals(childComp) : o == childComp)
					return child;
			}
			return null;
		}

		public void setAlwaysShowRoot(boolean selected) {
			this.alwaysShowRoot = selected;
			reloadAll();
		}
		
		public void removeEmptyFiles() {
			SwingUtilities.invokeLater(() -> {
				boolean changed = false;
				for(DefaultMutableTreeNode n : getTopLevelNodes())
					if(n.getUserObject().getClass()==PapaFile.class && ((PapaFile) n.getUserObject()).getNumTextures()==0) {
						changed = true;
						root.remove(n);
					}
				if(changed)
					reloadAndExpand(root);
			});
		}
		
		public void reloadAll() {
			PapaFile selected = lastSelected==null ? null : getAssociatedPapaFile(lastSelected);
			DefaultMutableTreeNode[] nodes = getTopLevelNodes();
			PapaFile[] papafiles = new PapaFile[nodes.length];
			for(int i = 0;i<nodes.length;i++)
				papafiles[i] = getAssociatedPapaFile(nodes[i]);
			
			SwingUtilities.invokeLater(() -> {
				DefaultMutableTreeNode toSelect = null;
				root.removeAllChildren();
				
				for(PapaFile p : papafiles) {
					DefaultMutableTreeNode n = addToTree(p, false);
					if(p == selected)
						toSelect = n;
				}
				
				reloadAndExpand(root);
				if(toSelect!=null) {
					TreePath path = new TreePath(toSelect.getPath());
					select(path);
				}
			});
		}

		private DefaultMutableTreeNode[] getTopLevelNodes() {
			ArrayList<DefaultMutableTreeNode> nodes = new ArrayList<DefaultMutableTreeNode>();
			@SuppressWarnings("unchecked")
			Enumeration<DefaultMutableTreeNode> e = root.children();
			while(e.hasMoreElements())
				nodes.add(e.nextElement());
			return nodes.toArray(new DefaultMutableTreeNode[nodes.size()]);
		}

		public void selectedNodeChanged() {
			if(lastSelected==null)
				return;
			treeModel.nodeChanged(lastSelected);
		}
		
		public void selectedPapaFileNodeChanged() {
			if(lastSelected==null)
				return;
			DefaultMutableTreeNode node = lastSelected;
			while(node!=null && node.getUserObject()!=activeFile)
				node = (DefaultMutableTreeNode) node.getParent();
			if(node!=null)
				treeModel.nodeChanged(node);
			else
				repaint(); // default if we miss
		}
		
		public void reloadTopLevelSelectedNode() {
			if(lastSelected==null)
				return;
			DefaultMutableTreeNode node = getLowestUnlinkedNode(lastSelected);
			if(node==null)
				node = getTopLevelNodeFromPapaFile(activeFile);
			refreshEntry(node);
		}

		public void removeFromTree(DefaultMutableTreeNode node) {
			DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
			removeFromTreeHelper(node);
			reloadAndExpand(parent);
		}
		
		private void removeChildrenFromNode(DefaultMutableTreeNode node) {
			for(DefaultMutableTreeNode n : getChildren(node))
				removeFromTreeHelper(n);
		}
		
		private DefaultMutableTreeNode[] getChildren(DefaultMutableTreeNode node) {
			ArrayList<DefaultMutableTreeNode> nodes = new ArrayList<DefaultMutableTreeNode>();
			@SuppressWarnings("unchecked")
			Enumeration<DefaultMutableTreeNode> e = node.children();
			while(e.hasMoreElements()) {
				nodes.add(e.nextElement());
			}
			return nodes.toArray(new DefaultMutableTreeNode[nodes.size()]);
		}

		private void removeFromTreeHelper(DefaultMutableTreeNode node) {
			for(DefaultMutableTreeNode n : getChildren(node))
				removeFromTreeHelper(n);
			node.removeFromParent();
		}
		
		private DefaultMutableTreeNode getLowestUnlinkedNode(DefaultMutableTreeNode node) {
			if(node==null || node.getParent()==null)
				return null;
			Object o = node.getUserObject();
			if(o instanceof String)
				return getLowestUnlinkedNode((DefaultMutableTreeNode)(node.getParent()));
			
			if(o instanceof PapaTexture) { // a texture could either be a top level node, or texture in a file.
				DefaultMutableTreeNode result = getLowestUnlinkedNode((DefaultMutableTreeNode)(node.getParent()));
				if(((PapaTexture)o).isLinked())
					return result;
				if(result==null)
					return node;
				else
					return result;
			}
			
			PapaFile p = getAssociatedPapaFile(node);
			if(p==null)
				return null;
			if(p.isLinkedFile())
				return getLowestUnlinkedNode((DefaultMutableTreeNode)(node.getParent()));
			return node;
		}
		
		private PapaFile getAssociatedPapaFile(DefaultMutableTreeNode node) {
			Object o = node.getUserObject();
			if(o instanceof String)
				return null;
			
			PapaFile p = null;
			if(o instanceof PapaTexture)
				p = ((PapaTexture)o).getParent();
			else
				p = ((PapaFile)o);
			return p;
		}
		
		private DefaultTreeCellRenderer papaTreeRenderer = new DefaultTreeCellRenderer() {
			private static final long serialVersionUID = -3988740979113661683L;
			private final Font font = this.getFont().deriveFont(Font.PLAIN);
			private final Font underline;
			
			{
				Map<TextAttribute, Object> map =new Hashtable<TextAttribute, Object>();
				map.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_LOW_DOTTED);
				underline = font.deriveFont(map);
			}
			
			@Override
			public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
				super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
				DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
				Object o = node.getUserObject();
				
				if(dependencies.contains(o) || dependents.contains(o)) {
					this.setFont(underline);
				} else
					this.setFont(font);
				
				
				if(o instanceof PapaFile) {
					PapaFile p = (PapaFile) o;
					this.setText(p.getFileName());
					this.setIcon(createImageIconFor(p));
				} else if (o instanceof PapaTexture) {
					PapaTexture t = (PapaTexture) o;
					this.setIcon(createImageIconFor(t));
					this.setText(t.getName());
				} else {
					this.setText(o.toString());
				}
				return this;
			}
			
			private ImageIcon createImageIconFor(PapaFile p) {
				ArrayList<ImageIcon> iconList = new ArrayList<ImageIcon>();
				if(!p.getFile().exists())
					iconList.add(imgPapaFileUnsaved);
				if(p.isLinkedFile())
					iconList.add(imgPapafileLinked);
				return createImageIcon((ImageIcon[]) iconList.toArray(new ImageIcon[iconList.size()]));
			}
			
			private ImageIcon createImageIconFor(PapaTexture t) {
				PapaFile p = t.getParent();
				if(p==null) // TODO band aid fix for one frame reload bug
					return createImageIcon();
				
				ArrayList<ImageIcon> iconList = new ArrayList<ImageIcon>();
				
				if(p.getFile() == null || !p.getFile().exists())
					iconList.add(imgPapaFileUnsaved);
				
				iconList.add(imgPapafileImage);
				if(t.isLinked()) {
					iconList.add(imgPapafileLinked);
					if(!t.linkValid())
						iconList.add(imgPapafileError);
				}
				if(p.isLinkedFile() && !p.getParent().isLinkedFileReferenced(p))
					iconList.add(imgPapafileNoLinks);
				return createImageIcon((ImageIcon[]) iconList.toArray(new ImageIcon[iconList.size()]));
					
			}
			
			private ImageIcon createImageIcon(ImageIcon... composite) {
				BufferedImage iconCache = new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
				Graphics iconGraphics = iconCache.getGraphics();
				imgPapafile.paintIcon(this, iconGraphics, 0, 0);
				for(ImageIcon i : composite) {
					i.paintIcon(this, iconGraphics, 0, 0);
				}
				return new ImageIcon(iconCache);
			}
			
		};
		
		/*
		private DefaultMutableTreeNode getNodeFromPapaFile(PapaFile p, DefaultMutableTreeNode n) {
			if(n.getUserObject() instanceof PapaTexture && ((PapaTexture)n.getUserObject()).getParent().equals(p)
				|| n.getUserObject() instanceof PapaFile && ((PapaFile)n.getUserObject()).equals(p)) {
				fileTree.setSelectionPath(new TreePath(n.getPath()));
				requestFocus();
				return n;
			}
			@SuppressWarnings("unchecked")
			Enumeration<DefaultMutableTreeNode> e = (Enumeration<DefaultMutableTreeNode>)n.children();
			while(e.hasMoreElements()) {
				DefaultMutableTreeNode element = e.nextElement();
				DefaultMutableTreeNode result = getNodeFromPapaFile(p,element);
				if(result!=null)
					return result;
			}
			return null;
		}*/
		
		private DefaultMutableTreeNode getTopLevelNodeFromPapaFile(PapaFile p) {
			@SuppressWarnings("unchecked")
			Enumeration<DefaultMutableTreeNode> e = (Enumeration<DefaultMutableTreeNode>)root.children();
			while(e.hasMoreElements()) {
				DefaultMutableTreeNode element = e.nextElement();
				if(element.getUserObject() instanceof PapaTexture && ((PapaTexture)element.getUserObject()).getParent().equals(p)
						|| element.getUserObject() instanceof PapaFile && ((PapaFile)element.getUserObject()).equals(p)) {
					fileTree.setSelectionPath(new TreePath(element.getPath()));
					requestFocus();
					return element;
				}
			}
			return null;
		}
		
		public DefaultMutableTreeNode addToTreeOrSelect(PapaFile p, boolean reload) {
			DefaultMutableTreeNode result = getTopLevelNodeFromPapaFile(p);
			if(result!=null) {
				requestFocus();
				refreshEntry(result); // refresh to account for Link
				fileTree.expandPath(new TreePath(result.getPath()));
				return result;
			}
			return addToTree(p, reload);
		}
		
		public DefaultMutableTreeNode addToTree(PapaFile p, boolean reload) {
			DefaultMutableTreeNode papaRoot = addPapaFileToNode(p,root);
			if(reload) {
				TreePath selected = new TreePath(papaRoot.getPath());
				reloadAndExpand(root);
				select(selected);
			}
			return papaRoot;
		}
		
		private void select(TreePath toSelect) {
			fileTree.setSelectionPath(toSelect);
			fileTree.scrollPathToVisible(toSelect); 
			treeScrollPane.getHorizontalScrollBar().setValue(0); // ensure that the left of the tree is always visible
		}
		
		private DefaultMutableTreeNode addPapaFileToNode(PapaFile file, DefaultMutableTreeNode parent) { // potential crash if two files are linked to each other...
			DefaultMutableTreeNode papaRoot = generateHierarchy(file);
			parent.add(papaRoot);
			return papaRoot;
		}
		
		private DefaultMutableTreeNode generateHierarchy(PapaFile file) {
			DefaultMutableTreeNode papaRoot = new DefaultMutableTreeNode(file);
			
			if(file.containsLinkedFiles()) {
				// add linked files
				DefaultMutableTreeNode linked = new DefaultMutableTreeNode("Linked Files");
				papaRoot.add(linked);
				
				for(PapaFile l : file.getLinkedFiles())
					addPapaFileToNode(l,linked);
				
				for(int i =0;i<file.getNumTextures();i++)
					papaRoot.add(new DefaultMutableTreeNode(file.getTexture(i)));
				
				papaRoot.add(linked);
			} else {
				if( ! alwaysShowRoot && !file.containsComponents(~PapaFile.TEXTURE & ~PapaFile.STRING) && file.getNumTextures()==1 && ! file.getTexture(0).isLinked())
					papaRoot.setUserObject(file.getTexture(0));
				else
					for(int i =0;i<file.getNumTextures();i++)
						papaRoot.add(new DefaultMutableTreeNode(file.getTexture(i)));
			}
			return papaRoot;
		}

		public ConfigPanelSelector() {

			setBorder(BorderFactory.createLineBorder(new Color(192, 192, 192)));
			
			SpringLayout layout = new SpringLayout();
			setLayout(layout);
			
			root = new DefaultMutableTreeNode("Open Files");
			fileTree = new JTree(root);
			treeModel = ((DefaultTreeModel)fileTree.getModel());
			
			fileTree.addTreeSelectionListener(new TreeSelectionListener() {
				@Override
				public void valueChanged(TreeSelectionEvent e) {
					TreePath newPath = e.getNewLeadSelectionPath();
					
					if(newPath==null) // This happened once and I have no idea why.
						return;
					
					lastSelected = (DefaultMutableTreeNode)newPath.getLastPathComponent();
					Object o = lastSelected.getUserObject();
					Class<?> cl = o.getClass();
					if(o instanceof PapaComponent) {
						PapaComponent pc = (PapaComponent)o;
						dependents.clear();
						dependents.addAll(Arrays.asList(pc.getDependents()));
						dependencies.clear();
						dependencies.addAll(Arrays.asList(pc.getDependencies()));
						configSelector.fileTree.repaint();
					}
					if(cl == PapaFile.class) {
						PapaFile selected = (PapaFile) o;
						setActiveFile(selected);
					} else if(cl == PapaTexture.class) {
						PapaTexture selected = (PapaTexture) o;
						setActiveTexture(selected);
					} else {
						unloadFileFromConfig();
					}
				}
			});
			
			fileTree.addTreeExpansionListener(new TreeExpansionListener() {
				
				@Override
				public void treeExpanded(TreeExpansionEvent e) {
					expandedPaths.add(e.getPath());
				}
				
				@Override
				public void treeCollapsed(TreeExpansionEvent e) {
					expandedPaths.remove(e.getPath());
				}
			});
			
			initializeTransferHandler();
			fileTree.setCellRenderer(papaTreeRenderer);
			fileTree.setDragEnabled(true);
			fileTree.setDropMode(DropMode.ON);
			fileTree.setRootVisible(true);
			
			treeScrollPane = new JScrollPane(fileTree,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
			treeScrollPane.setBorder(BorderFactory.createLineBorder(new Color(192, 192, 192)));
			layout.putConstraint(SpringLayout.NORTH, treeScrollPane, 5, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.WEST, treeScrollPane, 5, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.EAST, treeScrollPane, -5, SpringLayout.EAST, this);
			layout.putConstraint(SpringLayout.SOUTH, treeScrollPane, -25, SpringLayout.SOUTH, this);
			add(treeScrollPane);
			
			final JPopupMenu popup = new JPopupMenu();
	        removeSelected = new JMenuItem("Remove Selected");
	        removeSelected.setMnemonic(KeyEvent.VK_R);
	        removeSelected.addActionListener((ActionEvent e)-> {
	        	HashSet<Pair<DefaultMutableTreeNode,PapaFile>> nodes = new HashSet<>();
	        	TreePath[] selected = fileTree.getSelectionPaths();
				fileTree.clearSelection();
				if(selected==null)
					return;
				unloadFileFromConfig();
				for(TreePath t : selected) {
					DefaultMutableTreeNode node = (DefaultMutableTreeNode)t.getLastPathComponent();
					Object o = node.getUserObject();
					
					DefaultMutableTreeNode refreshNode = getLowestUnlinkedNode((DefaultMutableTreeNode) t.getLastPathComponent());
					if(refreshNode==null) // was already removed
						continue;
					PapaFile p = getAssociatedPapaFile(refreshNode);
					if(p==null)
						continue;
					
					if(o.getClass()==PapaTexture.class) {
						PapaTexture t2 = (PapaTexture)o;
						if(t2.getParent().isLinkedFile())
							t2.getParent().detach();
						t2.detach();
					}
					if(o.getClass()==PapaFile.class) {
						PapaFile p2 = (PapaFile)o;
						if(p2.isLinkedFile())
							p2.detach();
						else
							removeFromTreeHelper(node);
					}
					nodes.add(new Pair<>(refreshNode,p));
				}
				for(Pair<DefaultMutableTreeNode,PapaFile> p : nodes) {
					if(p.getValue().getNumTextures()== 0 && !ALLOW_EMPTY_FILES)
						removeFromTreeHelper(p.getKey());
					else
						refreshEntry(p.getKey(), p.getValue());
				}
				reloadAndExpand(root);
	        });
	        popup.add(removeSelected);
	        removeSelected.setEnabled(false);
	        
	        addLinked = new JMenuItem("Add Linked Texture");
	        addLinked.setMnemonic(KeyEvent.VK_A);
	        addLinked.addActionListener((ActionEvent e)-> {
	        	TreePath path = fileTree.getSelectionPath();
	        	if(path==null)
	        		return;
	        	fileTree.clearSelection();
	        	DefaultMutableTreeNode refreshNode = getLowestUnlinkedNode((DefaultMutableTreeNode) path.getLastPathComponent());
	        	PapaFile p = getAssociatedPapaFile((DefaultMutableTreeNode) path.getLastPathComponent());
	        	PapaTexture t = new PapaTexture("New Linked File", null);
	        	t.attach(p);
	        	
	        	DefaultMutableTreeNode replaced = refreshEntry(refreshNode);
	        	reloadAndExpand(root);
	        	
	        	DefaultMutableTreeNode toSelect = replaced;
	        	@SuppressWarnings("unchecked")
				Enumeration<DefaultMutableTreeNode> en = replaced.children();
	        	while(en.hasMoreElements()) {
	        		DefaultMutableTreeNode n = en.nextElement();
	        		if(n.getUserObject()==t)
	        			toSelect = n;
	        	}
	        	select(new TreePath(toSelect.getPath()));
	        	
	        	
	        });
	        popup.add(addLinked);
	        addLinked.setEnabled(false);
			
			JButton unloadButton = new JButton("Unload");
			layout.putConstraint(SpringLayout.NORTH, unloadButton, 5, SpringLayout.SOUTH, treeScrollPane);
			layout.putConstraint(SpringLayout.WEST, unloadButton, 5, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.EAST, unloadButton, -24, SpringLayout.EAST, this);
			layout.putConstraint(SpringLayout.SOUTH, unloadButton, -5, SpringLayout.SOUTH, this);
			add(unloadButton);
			
			JButton popupButton = new JButton(upArrowIcon);
			popupButton.setMargin(new Insets(0, 0, 0, 0));
			layout.putConstraint(SpringLayout.NORTH, popupButton, 5, SpringLayout.SOUTH, treeScrollPane);
			layout.putConstraint(SpringLayout.WEST, popupButton, 5, SpringLayout.EAST, unloadButton);
			layout.putConstraint(SpringLayout.EAST, popupButton, -5, SpringLayout.EAST, this);
			layout.putConstraint(SpringLayout.SOUTH, popupButton, -5, SpringLayout.SOUTH, this);
			add(popupButton);
			popupButton.setFocusPainted(false);
			
	        popupButton.addActionListener((ActionEvent e) -> {
	        	popup.show(popupButton, 0, (int)-popup.getPreferredSize().getHeight());
			});
	        
	        unloadButton.addActionListener((ActionEvent e) -> {
				TreePath[] selected = fileTree.getSelectionPaths();
				fileTree.clearSelection();
				if(selected==null)
					return;
				unloadFileFromConfig();
				for(TreePath t : selected) {
					DefaultMutableTreeNode node = getLowestUnlinkedNode((DefaultMutableTreeNode) t.getLastPathComponent());
					if(node==null) // was already removed
						continue;
					PapaFile p = getAssociatedPapaFile(node);
					if(p==null)
						continue;
					
					removeFromTreeHelper(node);
					p.flush();
				}
				reloadAndExpand(root);
			});
		}
		
		private DefaultMutableTreeNode[] getSelectedNodes() {
        	TreePath[] selectedPaths = fileTree.getSelectionPaths();
        	if(selectedPaths == null)
        		return null;
	        DefaultMutableTreeNode[] selectedNodes = new DefaultMutableTreeNode[selectedPaths.length];
	        for(int i =0;i<selectedPaths.length;i++)
	        	selectedNodes[i] = (DefaultMutableTreeNode) selectedPaths[i].getLastPathComponent();
	        return selectedNodes;
        }
		
		private void initializeTransferHandler() {
			fileTree.setTransferHandler(new TreeTransferHandler());
		}
		
		public PapaFile[] getTargetablePapaFiles() {
			@SuppressWarnings("unchecked")
			Enumeration<DefaultMutableTreeNode> e =  root.children();
			ArrayList<PapaFile> files = new ArrayList<PapaFile>();
			while(e.hasMoreElements())
				files.add(getAssociatedPapaFile(e.nextElement()));
			return files.toArray(new PapaFile[files.size()]);
		}
		
		// https://stackoverflow.com/questions/4588109/drag-and-drop-nodes-in-jtree
		private class TreeTransferHandler extends TransferHandler {
			private static final long serialVersionUID = 1609783140208717380L;

			private DataFlavor nodesFlavor;
		 	private DataFlavor[] flavors = new DataFlavor[1];
		 	//private DefaultMutableTreeNode[] nodesToRemove;

		    public TreeTransferHandler() {
		        try {
		            String mimeType = DataFlavor.javaJVMLocalObjectMimeType + ";class=\"" + DefaultMutableTreeNode[].class.getName() + "\"";
		            nodesFlavor = new DataFlavor(mimeType);
		            flavors[0] = nodesFlavor;
		        } catch(ClassNotFoundException e) {
		            System.out.println("ClassNotFound: " + e.getMessage());
		        }
		    }
			
			public boolean canImport(TransferHandler.TransferSupport support) {
		        if(!support.isDataFlavorSupported(nodesFlavor) || !support.isDrop()) {
		            return false;
		        }
		        support.setShowDropLocation(true);
		        
		        JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
		        TreePath dropPath = dl.getPath();
		        DefaultMutableTreeNode target;
		        if(dropPath==null)
		        	target = root;
	        	else
	        		target = (DefaultMutableTreeNode) dl.getPath().getLastPathComponent();
		        
		        if(target!=root && target.getParent()!=root) // only allow top level transfers
		        	return false;
		        
		        DefaultMutableTreeNode[] selectedNodes = getSelectedNodes();
		        
		        // Do not allow a drop on the drag source selections.
		        for(DefaultMutableTreeNode node : selectedNodes)
		        	if(node == target)
		        		return false;
		        // Selections containing strings are unsupported TODO: select children if encountered
		        for(DefaultMutableTreeNode node : selectedNodes)
		        	if(node.getUserObject().getClass() == String.class) 
		        		return false;
		        
		        // Selections containing empty Papa Files are not allowed.
		        for(DefaultMutableTreeNode node : selectedNodes)
		        	if(node.getUserObject().getClass() == PapaFile.class && ((PapaFile)node.getUserObject()).getNumTextures()==0) 
		        		return false;
		        
		        DefaultMutableTreeNode testNode = (DefaultMutableTreeNode) target.getParent();
		        while(testNode!=null) {
		        	for(DefaultMutableTreeNode node : selectedNodes)
		        		if(testNode == node)
		        			return false;
		        	testNode = (DefaultMutableTreeNode) testNode.getParent();
		        }
		        
		        //if(target.getUserObject().getClass() == String.class && target != root)
		        	//return false;
		        
		        if(target.getParent() == null) {
		        	support.setDropAction(MOVE);
		        } else {
		        	support.setDropAction(LINK);
		        }
		        return true;
	        }
			
			public int getSourceActions(JComponent c) {
				return MOVE | LINK;
			}

	        public boolean importData(TransferHandler.TransferSupport support) {
	        	if(!canImport(support)) {
	                return false;
	            }
	            // Extract transfer data.
	            DefaultMutableTreeNode[] nodes = null;
	            try {
	                Transferable t = support.getTransferable();
	                nodes = pruneSelectedNodes((DefaultMutableTreeNode[])t.getTransferData(nodesFlavor));
	            } catch(UnsupportedFlavorException ufe) {
	                System.err.println("UnsupportedFlavor: " + ufe.getMessage());
	                return false;
	            } catch(IOException ioe) {
	                System.err.println("I/O error: " + ioe.getMessage());
	                return false;
	            }
	            
	            // Get drop location info.
	            JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
		        TreePath dropPath = dl.getPath();
		        DefaultMutableTreeNode target;
		        if(dropPath==null)
		        	target = root;
	        	else
	        		target = (DefaultMutableTreeNode) dl.getPath().getLastPathComponent();

	            PapaFile targetFile = getAssociatedPapaFile(target);
            	LinkedHashSet<Pair<DefaultMutableTreeNode,PapaFile>> toReload;
            
            
	            if(target!=root)
	            	toReload = dropTargetPapa(target,targetFile,nodes);
	            else
	            	toReload = dropTargetGlobal(nodes);
		        
	            // I'm not sure why, but not invoking later will cause the JTree to incorrectly refresh leading to a crash. (still true as of v0.2)
	            SwingUtilities.invokeLater(() -> toReload.forEach((n) -> refreshEntry(n.getKey(),n.getValue())));
	            return true;
	        }
	        
	        private DefaultMutableTreeNode[] pruneSelectedNodes(DefaultMutableTreeNode[] transferData) {
	        	ArrayList<DefaultMutableTreeNode> 	nodes 				= new ArrayList<DefaultMutableTreeNode>();
	        	ArrayList<DefaultMutableTreeNode> 	nodesToTest 		= new ArrayList<DefaultMutableTreeNode>();
	        	ArrayList<DefaultMutableTreeNode> 	nodesToTestUnlinked = new ArrayList<DefaultMutableTreeNode>();
	        	ArrayList<PapaTexture> 				linkedTextureList 	= new ArrayList<PapaTexture>();
	        	ArrayList<DefaultMutableTreeNode> 	topLevelNodes 		= new ArrayList<DefaultMutableTreeNode>();
	        	
	        	for(DefaultMutableTreeNode node : transferData) { //filter the files from the textures
	        		if(node.getUserObject().getClass() == PapaFile.class) {
	        			nodes.add(node);
	        			topLevelNodes.add(node);
	        		} else {
	        			nodesToTest.add(node);
	        		}
	        	}
	        	
	        	for(DefaultMutableTreeNode node : nodesToTest) { // get all the linked textures
	        		if( ! isChildAny(topLevelNodes, node)) {
		        		PapaTexture tex = (PapaTexture) node.getUserObject();
	        			if(tex.isLinked()) {
	        				linkedTextureList.add(tex.getLinkedTexture());
	        				nodes.add(node);
	        			} else {
	        				nodesToTestUnlinked.add(node);
	        			}
	        		}
	        	}
	        	
	        	for(DefaultMutableTreeNode node : nodesToTestUnlinked) {
        			PapaTexture tex = (PapaTexture) node.getUserObject();
        			if( ! tex.isLinked() &&  ! isAnyEqualTo(linkedTextureList, tex))
	        			nodes.add(node);
	        	}
				return nodes.toArray(new DefaultMutableTreeNode[nodes.size()]);
			}
	        
	        private boolean isAnyEqualTo(ArrayList<PapaTexture> linkedTextureList, PapaTexture tex) { // skips over calling .equals
				for(PapaTexture t : linkedTextureList)
					if(t == tex)
						return true;
				return false;
			}

			private boolean isChildAny(ArrayList<DefaultMutableTreeNode> nodes, DefaultMutableTreeNode toTest) {
	        	for(DefaultMutableTreeNode n : nodes)
					if(n.isNodeDescendant(toTest))
						return true;
	        	return false;
	        }

			private LinkedHashSet<Pair<DefaultMutableTreeNode,PapaFile>> dropTargetGlobal(DefaultMutableTreeNode[] nodes) {
	        	int mode = optionBox("Move selected textures into their own files?", "Rip Settings", new Object[] {"Rip","Copy","Cancel"}, "Rip");
	        	LinkedHashSet<Pair<DefaultMutableTreeNode,PapaFile>> toReload = new LinkedHashSet<Pair<DefaultMutableTreeNode,PapaFile>>();
	            if(mode == -1 || mode == 2)
	            	return toReload;
	            boolean rip = mode == 0;
	            
	            for(DefaultMutableTreeNode node : nodes) {
	            	DefaultMutableTreeNode refreshNode = getLowestUnlinkedNode(node);
	            	PapaFile associated = getAssociatedPapaFile(refreshNode);
	            	PapaFile file = null;
	            	Object o = node.getUserObject();
	            	DefaultMutableTreeNode[] results = null;
	            	if(o instanceof PapaTexture) {
	            		PapaTexture t = (PapaTexture)o;
	            		file = t.getParent();
	            		PapaTexture t2 = extract(t,rip);
	            		results = placeInOwnFiles(file, t2);
	            		
	            	} else {
	            		file = (PapaFile)o;
	            		PapaTexture[] textures = extract(file,rip);
	            		results = placeInOwnFiles(file, textures);
	            	}
	            	if(rip) {
		            	if(associated==null || (! ALLOW_EMPTY_FILES && associated.getNumTextures()==0) || !associated.containsComponents(~PapaFile.STRING))
	            			removeFromTree(refreshNode);
	            		toReload.add(new Pair<DefaultMutableTreeNode,PapaFile>(refreshNode, associated));
	            	}
	            	for(DefaultMutableTreeNode n : results)
            			toReload.add(new Pair<DefaultMutableTreeNode, PapaFile>(n, getAssociatedPapaFile(n)));
	            }
	            return toReload;
			}

			private LinkedHashSet<Pair<DefaultMutableTreeNode,PapaFile>> 
					dropTargetPapa(DefaultMutableTreeNode target, PapaFile targetFile, DefaultMutableTreeNode[] nodes) {
	        	int mode = optionBox("Link selected textures into "+getAssociatedPapaFile(target)+"?", "Link Settings", new Object[] {"Link","Embed","Cancel"}, "Link");
	            
	        	LinkedHashSet<Pair<DefaultMutableTreeNode,PapaFile>> toReload = new LinkedHashSet<Pair<DefaultMutableTreeNode,PapaFile>>();
	        	
	            if(mode == -1 || mode == 2)
	            	return toReload;
	            
	            boolean link = mode == 0;
	            if(link && PapaFile.getPlanetaryAnnihilationDirectory()==null) {
	            	if(showError("Link is unavailable until the media directory is set.", "Cannot link", new Object[] {"Ok","Set Media Directory"}, "Ok") == 1)
	            		menu.changeMediaDirectory();
	            	if(PapaFile.getPlanetaryAnnihilationDirectory()==null)
	            		return toReload;
	            }
	            for(DefaultMutableTreeNode node : nodes) {
	            	DefaultMutableTreeNode refreshNode = getLowestUnlinkedNode(node);
	            	PapaFile associated = getAssociatedPapaFile(refreshNode);
	            	PapaFile file = null;
	            	Object o = node.getUserObject();
	            	
	            	if(o instanceof PapaTexture) {
	            		PapaTexture t = (PapaTexture)o;
	            		file = t.getParent();
	            		t = extract(t, true);
	            		AttachToNewFile(targetFile, link, t);
	            		
	            	} else {
	            		file = (PapaFile)o;
	            		PapaTexture[] textures = extract(file,true);
	            		AttachToNewFile(targetFile, link, textures);
	            	}
	            	if(associated== null || (! ALLOW_EMPTY_FILES && associated.getNumTextures()==0) || !associated.containsComponents(~PapaFile.STRING))
            			removeFromTree(refreshNode);
            		toReload.add(new Pair<DefaultMutableTreeNode,PapaFile>(refreshNode, associated));
	            }
	            toReload.add(new Pair<DefaultMutableTreeNode,PapaFile>(target, targetFile));
	            return toReload;
	            
			}
			
			private DefaultMutableTreeNode[] placeInOwnFiles(PapaFile source, PapaTexture... textures) {
				ArrayList<DefaultMutableTreeNode> nodes = new ArrayList<DefaultMutableTreeNode>();
	        	for(PapaTexture t : textures) {
	        		if(t==null)
	        			continue;
        			PapaFile p = source.getEmptyCopy();
        			t.attach(p);
        			nodes.add(addToTree(p, false));
	        	}
	        	return nodes.toArray(new DefaultMutableTreeNode[nodes.size()]);
	        }
	        
	        private PapaTexture[] extract(PapaFile p, boolean rip) {
	        	PapaTexture[] textures = getValidTextures(p);
	        	HashSet<PapaTexture> uniqueTextures = new HashSet<PapaTexture>();
	        	for(int i =0;i<textures.length;i++) {
        			uniqueTextures.add(extract(textures[i],rip));
	        	}
	        	return uniqueTextures.toArray(new PapaTexture[uniqueTextures.size()]);
	        }
	        
	        private PapaTexture[] getValidTextures(PapaFile p) {
	        	ArrayList<PapaTexture> textures = new ArrayList<PapaTexture>();
	        	for(int i = 0; i <p.getNumTextures(); i++) { //TODO: the order of this actually matters. Fundamental problems with detach...
	        		PapaTexture t = p.getTexture(i);
	        		if(t.isLinked() && ! t.linkValid())
	        			continue;
	        		textures.add(t);
	        	}
	        	return textures.toArray(new PapaTexture[textures.size()]);
	        }
	        
	        private PapaTexture extract(PapaTexture t,boolean rip) {
	        	PapaTexture target = t;
	        	if(target.isLinked()) {
	        		if(target.getParent() == null || ! target.linkValid())
	        			return null;
	        		target = target.getLinkedTexture();
	        	}
	        	if(rip) {
	        		if(t!=target) { // overwrite the linked texture's name if it was not the initial target of the operation
	        			t.detach();
		        		target.setName(t.getName());
	        		}
	        		PapaFile parent = target.getParent();
	        		if(parent!=null && parent.isLinkedFile())
	        			parent.detach();
	        		target.detach();
	        		return target;
	        	}
	        	return target.duplicate();
	        }

			private void AttachToNewFile(PapaFile file, boolean link, PapaTexture... textures) {
				for(PapaTexture t : textures) {
					if(t==null)
						continue;
        			if(link) {
        				if(!t.getName().startsWith("/"))
        					t.setName("/"+t.getName()); // add implicit /
        				file.generateLinkedTexture(t);
        			}
        			else
        				t.attach(file);
				}
			}

			@Override
	        protected Transferable createTransferable(JComponent c) {
	        	DefaultMutableTreeNode[] selectedNodes = getSelectedNodes();
	            if(selectedNodes != null) {
	                return new NodesTransferable(selectedNodes);
	            }
	            return null;
	        }
	        
	        private class NodesTransferable implements Transferable {
		        DefaultMutableTreeNode[] nodes;

		        public NodesTransferable(DefaultMutableTreeNode[] nodes) {
		            this.nodes = nodes;
		         }

		        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
		            if(!isDataFlavorSupported(flavor))
		                throw new UnsupportedFlavorException(flavor);
		            return nodes;
		        }

		        public DataFlavor[] getTransferDataFlavors() {
		            return flavors;
		        }

		        public boolean isDataFlavorSupported(DataFlavor flavor) {
		            return nodesFlavor.equals(flavor);
		        }
		    }
	    }
	}
	
	private class ImagePanel extends JPanel { //TODO: Sprite sheet support (enter number of columns, rows, and images. animate button
		private static final long serialVersionUID = 341369068060762310L;
		
		private boolean ignoreAlpha,luminance, tile, mouseInBounds, mouseHeld, showDXT;
		private int width, height, index, mouseX, mouseY;
		private PapaTexture image;
		private double scale=1;
		
		private BufferedImage ignoreAlphaCache;
		private int ignoreAlphaIndexCache = 0,ignoreAlphaModeCache = 1;
		private boolean ignoreAlphaLuminanceCache = false;
		
		private boolean dragDropEnabled = true;
		
		public final static int RGBA = 0, RED = 1, GREEN = 2, BLUE = 3, ALPHA = 4;
		private int mode = 0;
		
		ImagePanel() {
			super();
			initialzeListeners();
			initializeTransferHandler();
		}

		private void initializeTransferHandler() {
			setTransferHandler(new TransferHandler() {

				private static final long serialVersionUID = 6432655799671782224L;

				public boolean canImport(TransferHandler.TransferSupport support) {
					if(!dragDropEnabled)
						return false;
		            if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor) || ! support.isDrop()) {
		                return false;
		            }
		            boolean moveSupported = (COPY & support.getSourceDropActions()) == COPY;

		            if (!moveSupported)
		                return false;
		            support.setDropAction(TransferHandler.COPY);
		            if (support.getUserDropAction() == TransferHandler.COPY) {
		            	support.setDropAction(TransferHandler.MOVE);
		            }
		            return true;
		        }

		        public boolean importData(TransferHandler.TransferSupport support) {
		            if (!canImport(support)) {
		                return false;
		            }
		            Transferable t = support.getTransferable();
		            try {
						@SuppressWarnings("unchecked")
						List<File> l =(List<File>)t.getTransferData(DataFlavor.javaFileListFlavor);
						if(support.getDropAction() == TransferHandler.MOVE) { // holding ctrl (https://docs.oracle.com/javase/8/docs/api/java/awt/dnd/DropTargetDragEvent.html)
							// if the user is holding ctrl, don't prompt and just use old settings
							readAll(papaOptions.getCurrentSettings(), l.toArray(new File[l.size()]));
						} else {
							readAll(null, l.toArray(new File[l.size()]));
						}
						
		            } catch (UnsupportedFlavorException e) {
		            	showError("An unexpected error orccured:\n"+e.getMessage(),"Error", new Object[] {"Ok"},"Ok");
		            	e.printStackTrace();
		                return false;
		            } catch (IOException e) {
		            	showError(e.getMessage(),"IO Error", new Object[] {"Ok"},"Ok");
		            	e.printStackTrace();
		                return false;
		            } catch (Exception ex) { //TODO this is only for testing
		            	showError(ex.getMessage()+", "+ex.getClass(),"Error", new Object[] {"Ok"},"Ok");
		            	ex.printStackTrace();
		            	return false;
		            }

		            return true;
		        }
		    });
		}
		
		private void initialzeListeners() {
			addMouseWheelListener(new MouseWheelListener() { // mouse zoom support
				@Override
				public void mouseWheelMoved(MouseWheelEvent e) {
					int sign = e.getWheelRotation()>0 ? -1 : 1;
					if(e.isControlDown()) 
						ribbonPanel.changeZoom(sign);
					else if(e.isShiftDown()) {
						horizontal.setValue((int) (horizontal.getValue() + horizontal.getVisibleAmount() / 2 * -sign));
						updateMouseLocation(e.getX(), e.getY());
					} else {
						vertical.setValue((int) (vertical.getValue() + vertical.getVisibleAmount() / 2 *  -sign));
						updateMouseLocation(e.getX(), e.getY());
					}
				}
			});
			
			addMouseListener(new MouseListener() {
				
				@Override
				public void mouseReleased(MouseEvent e) {
					mouseHeld=false;
				}
				
				@Override
				public void mousePressed(MouseEvent e) {
					if(e.getButton()==MouseEvent.BUTTON1)
						mouseHeld=true;
					updateMouseLocation(e.getX(), e.getY());
				}
				
				@Override
				public void mouseExited(MouseEvent e) {
					updateMouseLocation(-1, -1);
				}
				
				@Override
				public void mouseEntered(MouseEvent e) {}
				
				@Override
				public void mouseClicked(MouseEvent e) {}
			});
			
			addMouseMotionListener(new MouseMotionListener() {
				
				@Override
				public void mouseMoved(MouseEvent e) {
					updateMouseLocation(e.getX(), e.getY());
				}
				
				@Override
				public void mouseDragged(MouseEvent e) {
					mouseMoved(e);
				}
			});
			
			addComponentListener(new ComponentListener() {
				
				@Override
				public void componentShown(ComponentEvent e) {}
				
				@Override
				public void componentResized(ComponentEvent e) {
					updateScrollBars();
					
				}
				
				@Override
				public void componentMoved(ComponentEvent e) {}
				
				@Override
				public void componentHidden(ComponentEvent e) {}
			});
		}

		private void updateMouseLocation(int mouseX, int mouseY) {
			if(mouseX >= getTotalDrawWidth() || mouseX<0 || mouseY>=getTotalDrawHeight() || mouseY<0)
				mouseInBounds=false;
			else {
				mouseInBounds=true;
				this.mouseX = (int) (mouseX / scale + valueToPixels(horizontal)) % width;
				this.mouseY = (int) (mouseY / scale + valueToPixels(vertical)) % height;
			}
			ribbonPanel.updateMouseLocation();
		}
		
		private int getTotalDrawWidth() {
			if(!isImageLoaded())
				return 0;
			return (int) (scale * width * (tile ? 2 : 1));
		}
		
		private int getTotalDrawHeight() {
			if(!isImageLoaded())
				return 0;
			return (int) (scale * height * (tile ? 2 : 1));
		}
		
		private void updateScrollBars() {
			updateScrollBars(valueToPixels(horizontal),valueToPixels(vertical));
		}
		
		private void updateScrollBars(double lastX, double lastY) {
			if(!isImageLoaded()) {
				horizontal.setEnabled(false);
				vertical.setEnabled(false);
				return;
			}
			int imageWidth = getTotalDrawWidth();
			int imageHeight = getTotalDrawHeight();

			if(imageWidth > getWidth()) {
				horizontal.setEnabled(true);
				horizontal.setMaximum(imageWidth);
				horizontal.setVisibleAmount(getWidth());
				horizontal.setValue(pixelsToValue(lastX));
				horizontal.setUnitIncrement((int)Math.max(10*scale, 1));
			} else {
				horizontal.setEnabled(false);
				horizontal.setValue(0);
				horizontal.setMaximum(0);
			}
			
			if(imageHeight > getHeight()) {
				vertical.setEnabled(true);
				vertical.setMaximum(imageHeight);
				vertical.setVisibleAmount(getHeight());
				vertical.setValue(pixelsToValue(lastY));
				vertical.setUnitIncrement((int)Math.max(10*scale, 1));
			} else {
				vertical.setEnabled(false);
				vertical.setValue(0);
				vertical.setMaximum(0);
			}
		}
		
		public void updateZoom() {
			int mouseX = (int) (this.mouseX * scale); // undo the transform from the previous scale
			int mouseY = (int) (this.mouseY * scale);
			double lastXVal = valueToPixels(horizontal);
			double lastYVal = valueToPixels(vertical);
			scale = ribbonPanel.getZoomScale();
			updateMouseLocation(mouseX,mouseY);
			updateScrollBars(lastXVal,lastYVal);
			repaint();
		}

		public Color getColourUnderMouse() {
			if(!mouseInBounds)
				return new Color(1f,1f,1f,1f);
			return new Color(image.getImage(index).getRGB(mouseX % image.getImage(index).getWidth(), mouseY % image.getImage(index).getHeight()),true);
		}
		
		public int getMouseX() {
			return mouseX;
		}
		
		public int getMouseY() {
			return mouseY;
		}
		
		public boolean isMouseInBounds() {
			return mouseInBounds;
		}
		
		public boolean isMouseHeld() {
			return mouseHeld;
		}
		
		public boolean isImageLoaded() {
			return image != null;
		}
		
		public void unload() {
			setImage(null);
		}
		
		public void setImage(PapaTexture tex) {
			this.image = tex;
			this.ignoreAlphaCache=null;
			if(image!=null) {
				this.width = image.getWidth();
				this.height= image.getHeight();
			}
			updateScrollBars();
			repaint();
		}
		
		public void setMode(int mode) {
			this.mode = mode;
			repaint();
		}
		
		public void setLuminance(boolean b) {
			this.luminance = b;
			repaint();
		}
		
		public void setIgnoreAlpha(boolean b) {
			this.ignoreAlpha = b;
			repaint();
		}
		
		public void showDXT(boolean selected) {
			this.showDXT = selected;
			repaint();
		}
		
		public void setTile(boolean b) {
			this.tile = b;
			updateScrollBars();
			repaint();
		}
		
		public void setImageIndex(int index) {
			this.index=index;
			this.width=image.getWidth(index);
			this.height=image.getHeight(index);
			updateScrollBars();
			repaint();
		}
		
		private double valueToPixels(JScrollBar scroll) {
			return scroll.getValue() / scale;
		}
		
		private int pixelsToValue(double pixels) {
			return (int) (pixels * scale);
		}
		
		private BufferedImage getImageFromTexture() {
			if(luminance)
				return image.asLuminance(index);
			
			switch(mode) {
				case RGBA:
					return image.getImage(index);
				case RED:
					return image.asRed(index);
				case GREEN:
					return image.asGreen(index);
				case BLUE:
					return image.asBlue(index);
				case ALPHA:
					return image.asAlpha(index);
				default:
					throw new IndexOutOfBoundsException("Invalid mode, "+mode+" is not within bounds [0, 4]");
			}
		}
		
		public BufferedImage getImage() {
			BufferedImage draw = getImageFromTexture();
			if (ignoreAlpha && image.supportsAlpha()) {
				if(ignoreAlphaChacheInvalid()) {
					 // copy the data into a new BufferedImage with no alpha.
					BufferedImage tmp = removeAlpha(draw);
					
					ignoreAlphaCache = tmp;
					
					ignoreAlphaIndexCache = index;
					ignoreAlphaModeCache = mode;
					ignoreAlphaLuminanceCache = luminance;
				}
				draw = ignoreAlphaCache;
			}
			return draw;
		}
		
		public BufferedImage getFullImage() {
			BufferedImage draw = getImage();
			if(tile) {
				int w = draw.getWidth();
				int h = draw.getHeight();
				BufferedImage out = new BufferedImage(w * 2, h * 2, draw.getType());
				Graphics2D g2d = (Graphics2D) out.getGraphics();
				g2d.drawImage(draw, 0, 0, null);
				g2d.drawImage(draw, w, 0, null);
				g2d.drawImage(draw, 0, h, null);
				g2d.drawImage(draw, w, h, null);
				draw = out;
			}
			return draw;
		}
		
		private boolean ignoreAlphaChacheInvalid() {
			return ignoreAlphaCache==null || ignoreAlphaIndexCache!=index || ignoreAlphaModeCache!=mode || ignoreAlphaLuminanceCache!=luminance;
		}
		
		private BufferedImage removeAlpha(BufferedImage input) {
			BufferedImage tmp = new BufferedImage(input.getWidth(),input.getHeight(),BufferedImage.TYPE_INT_RGB);
			int[] data = new int[input.getWidth()*input.getHeight()];
			input.getRGB(0, 0, input.getWidth(), input.getHeight(), data, 0, input.getWidth());
			tmp.setRGB(0, 0, input.getWidth(), input.getHeight(), data, 0, input.getWidth());
			return tmp;
		}
		
		@Override
		public void paintComponent(Graphics g) {
			super.paintComponent(g);
			
			if(image == null)
				return;
			
			Graphics2D g2d = (Graphics2D)g.create();
			g2d.setClip(0, 0, Math.min(getTotalDrawWidth(),getWidth()), Math.min(getTotalDrawHeight(),getHeight()));
			
			BufferedImage draw = getImage();
			
			if(! ignoreAlpha && image.supportsAlpha())
				drawCheckerboardGrid(g2d);
			
			AffineTransform newTransform = g2d.getTransform();
			newTransform.scale(scale, scale);
			newTransform.translate(-valueToPixels(horizontal), -valueToPixels(vertical));
			g2d.setTransform(newTransform);
			g2d.drawImage(draw, 0, 0, null);
			if(tile) {
				g2d.drawImage(draw, image.getWidth(index), 0, null);
				g2d.drawImage(draw, 0, image.getHeight(index), null);
				g2d.drawImage(draw, image.getWidth(index), image.getHeight(index), null);
			}
			
			if(showDXT) {
				drawDXTZones((Graphics2D)g.create());
			}
		}
		
		private void drawDXTZones(Graphics2D g) {
			g.setColor(new Color(0.1f,0.1f,0.1f));
			g.setXORMode(new Color(1f,1f,1f));
			if(scale<1)
				return;
			float spacing = (float) (4 * scale);
			
			int horizontal = (int) (image.getWidth(index) * scale);
			int vertical = (int) (image.getHeight(index) * scale);
			
			int xOrig = (int) (valueToPixels(Editor.this.horizontal) * scale); // the origin of the image, corrected for scale
			int yOrig = (int) (valueToPixels(Editor.this.vertical) * scale);
			
			int canvasWidth = getWidth();
			int canvasHeight = getHeight();
			// converting between coordinate spaces is hard.
			drawDXTZone(g, (int)(-xOrig % spacing),  (int) (-yOrig % spacing), Math.min(horizontal - xOrig,canvasWidth), Math.min(vertical - yOrig,canvasHeight), spacing);
			if(tile) {
				drawDXTZone(g,	Math.max((int)((horizontal - xOrig) % spacing), horizontal - xOrig),(int) (-yOrig % spacing),
								Math.min(2 * horizontal - xOrig,canvasWidth), Math.min(vertical - yOrig,canvasHeight), spacing);
				drawDXTZone(g,	(int)(-xOrig % spacing), Math.max((int)((vertical - yOrig) % spacing), vertical - yOrig),
								Math.min(horizontal - xOrig,canvasWidth), Math.min(2 * vertical - yOrig,canvasHeight), spacing);
				drawDXTZone(g,	Math.max((int)((horizontal - xOrig) % spacing), horizontal - xOrig), Math.max((int)((vertical - yOrig) % spacing), vertical - yOrig),
								Math.min(2 * horizontal - xOrig,canvasWidth), Math.min(2 * vertical - yOrig,canvasHeight), spacing);
			}
			
		}
		
		private void drawDXTZone(Graphics2D g2d,int startX, int startY, int endX, int endY, float change) {
			if(endY <= startY)
				return;
			
			for(float x = startX;x<endX;x+=change) {
				int xx = (int)x;
				g2d.drawLine(xx, startY, xx, endY);
			}
			for(float y =startY;y<endY;y+=change) {
				int yy = (int) y;
				g2d.drawLine(startX, yy, endX, yy);
			}
		}

		private void drawCheckerboardGrid(Graphics2D g) {
			int drawWidth = Math.min(getTotalDrawWidth(),getWidth());
			int drawHeight = Math.min(getTotalDrawHeight(),getHeight());
			
			for(int x = (int) (-horizontal.getValue() % 64);x<drawWidth;x+=64)
				for(int y = (int) (-vertical.getValue() % 64);y<drawHeight;y+=64)
					g.drawImage(checkerboard, x, y, null);
		}
		
	}
	
	public static int showError(Object message, String title, Object[] options, Object Default)
	{
		exclamationSound();
		System.err.println(message);
		return JOptionPane.showOptionDialog(APPLICATION_WINDOW, message,
	             title, JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE,
	             null, options, Default);
	}
	
	public static int optionBox(Object message, String title, Object[] options, Object Default)
	{
		return JOptionPane.showOptionDialog(APPLICATION_WINDOW, message,
	             title, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
	             null, options, Default);
	}
		
	public static void exclamationSound()
	{
		Toolkit.getDefaultToolkit().beep();
	}
}
