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

import java.awt.Color;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.*;

import editor.FileHandler.*;
import editor.FileHandler.ImportInfo.ActivityListener;
import papafile.*;
import papafile.PapaTexture.ImmutableTextureSettings;

public class BatchConvert extends JDialog  {

	private static final long serialVersionUID = -6635278548154607017L;
	
	private PapaOptions papaOptions;
	
	private JPanel contentPane;
	private JButton convertButton, optionsButton, cancelButton, closeButton;
	
	private final int width = 460;
	private final int height = 530;
	
	private OptionsSection optionsSection;
	private ProgressSection progressSection;
	private LogSection logSection;
	private FileWorker currentTask;
	
	private boolean isWorking;
	
	
	public BatchConvert(JFrame owner, PapaOptions o) {
		super(owner);
		
		setIconImages(owner.getIconImages());
		
		papaOptions = o;
		addWindowListener(wl);
		setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		setModalityType(ModalityType.APPLICATION_MODAL);
		setResizable(false);
		setTitle("Convert Folder");
		
		setBounds(0, 0, width, height);
		
		contentPane = new JPanel();
		setContentPane(contentPane);
		SpringLayout layout = new SpringLayout();
		contentPane.setLayout(layout);
		
		optionsSection = new OptionsSection();
		layout.putConstraint(SpringLayout.NORTH, optionsSection, 10, SpringLayout.NORTH, contentPane);
		layout.putConstraint(SpringLayout.WEST, optionsSection, 10, SpringLayout.WEST, contentPane);
		layout.putConstraint(SpringLayout.SOUTH, optionsSection, 165, SpringLayout.NORTH, contentPane);
		layout.putConstraint(SpringLayout.EAST, optionsSection, -10, SpringLayout.EAST, contentPane);
		add(optionsSection);
		
		progressSection = new ProgressSection();
		layout.putConstraint(SpringLayout.NORTH, progressSection, 10, SpringLayout.SOUTH, optionsSection);
		layout.putConstraint(SpringLayout.WEST, progressSection, 10, SpringLayout.WEST, contentPane);
		layout.putConstraint(SpringLayout.SOUTH, progressSection, 90, SpringLayout.SOUTH, optionsSection);
		layout.putConstraint(SpringLayout.EAST, progressSection, -10, SpringLayout.EAST, contentPane);
		add(progressSection);
		
		logSection = new LogSection();
		layout.putConstraint(SpringLayout.NORTH, logSection, 10, SpringLayout.SOUTH, progressSection);
		layout.putConstraint(SpringLayout.WEST, logSection, 10, SpringLayout.WEST, contentPane);
		layout.putConstraint(SpringLayout.SOUTH, logSection, 200, SpringLayout.SOUTH, progressSection);
		layout.putConstraint(SpringLayout.EAST, logSection, -10, SpringLayout.EAST, contentPane);
		add(logSection);
		
		
		closeButton = new JButton("Close");
		closeButton.setMnemonic('c');
		closeButton.addActionListener((ActionEvent e) -> {
			exitOrCancel();
		});
		layout.putConstraint(SpringLayout.NORTH, closeButton, -30, SpringLayout.SOUTH, contentPane);
		layout.putConstraint(SpringLayout.WEST, closeButton, -85, SpringLayout.EAST, contentPane);
		layout.putConstraint(SpringLayout.SOUTH, closeButton, -5, SpringLayout.SOUTH, contentPane);
		layout.putConstraint(SpringLayout.EAST, closeButton, -5, SpringLayout.EAST, contentPane);
		add(closeButton);
		
		cancelButton = new JButton("Cancel");
		cancelButton.setMnemonic('a');
		cancelButton.addActionListener((ActionEvent e) -> {
			currentTask.cancel(true);
		});
		cancelButton.setEnabled(false);
		layout.putConstraint(SpringLayout.NORTH, cancelButton, 0, SpringLayout.NORTH, closeButton);
		layout.putConstraint(SpringLayout.WEST, cancelButton, -85, SpringLayout.WEST, closeButton);
		layout.putConstraint(SpringLayout.SOUTH, cancelButton, 0, SpringLayout.SOUTH, closeButton);
		layout.putConstraint(SpringLayout.EAST, cancelButton, -5, SpringLayout.WEST, closeButton);
		add(cancelButton);
		
		optionsButton = new JButton("Options");
		optionsButton.setMnemonic('o');
		optionsButton.addActionListener((ActionEvent e) -> {
			papaOptions.setActiveFile(null);
			papaOptions.updateLinkOptions(new PapaFile[0]);
			papaOptions.showAt(getX()+getWidth()/2, getY()+getHeight()/2);
		});
		layout.putConstraint(SpringLayout.NORTH, optionsButton, -30, SpringLayout.SOUTH, contentPane);
		layout.putConstraint(SpringLayout.WEST, optionsButton, 5, SpringLayout.WEST, contentPane);
		layout.putConstraint(SpringLayout.SOUTH, optionsButton, -5, SpringLayout.SOUTH, contentPane);
		layout.putConstraint(SpringLayout.EAST, optionsButton, 85, SpringLayout.WEST, contentPane);
		add(optionsButton);
		
		convertButton = new JButton("Convert");
		convertButton.setMnemonic('o');
		convertButton.addActionListener((ActionEvent e) -> {
			if(!optionsSection.validateSelection())
				return;
			progressSection.reset();
			currentTask = new FileWorker(new File(optionsSection.input.getText()), new File(optionsSection.output.getText()),
							optionsSection.toImage.isSelected() ? FileHandler.PAPA_INTERFACE : FileHandler.IMAGE_INTERFACE,
							optionsSection.toImage.isSelected() ? FileHandler.getImageFilters()[optionsSection.formats.getSelectedIndex() + 1] : FileHandler.getPapaFilter(),
							papaOptions.getCurrentSettings(), optionsSection.subdirectories.isSelected(), optionsSection.writeLinked.isSelected(),
							optionsSection.overwrite.isSelected(), optionsSection.ignoreHierarchy.isSelected());
			currentTask.execute();
		});
		layout.putConstraint(SpringLayout.NORTH, convertButton, -30, SpringLayout.SOUTH, contentPane);
		layout.putConstraint(SpringLayout.WEST, convertButton, 5, SpringLayout.EAST, optionsButton);
		layout.putConstraint(SpringLayout.SOUTH, convertButton, -5, SpringLayout.SOUTH, contentPane);
		layout.putConstraint(SpringLayout.EAST, convertButton, 85, SpringLayout.EAST, optionsButton);
		add(convertButton);
		
		optionsSection.validateSelection();
		
		this.getRootPane().setDefaultButton(closeButton);
	}
	
	private void setWorking(boolean working) {
		isWorking=working;
		cancelButton.setEnabled(working);
		convertButton.setEnabled(! working);
		optionsButton.setEnabled(! working);
	}
	
	public boolean isRecursive() {
		return optionsSection.subdirectories.isSelected();
	}
	
	public void setRecursive(boolean b) {
		optionsSection.subdirectories.setSelected(b);
	}
	
	public boolean isWritingLinkedFiles() {
		return optionsSection.writeLinked.isSelected();
	}
	
	public void setWriteLinkedFiles(boolean b) {
		optionsSection.writeLinked.setSelected(b);
	}
	
	public boolean isOverwrite() {
		return optionsSection.overwrite.isSelected();
	}
	
	public void setOverwrite(boolean b) {
		optionsSection.overwrite.setSelected(b);
	}
	
	public boolean isIgnoreHierarchy() {
		return optionsSection.ignoreHierarchy.isSelected();
	}
	
	public void setIgnoreHierarchy(boolean b) {
		optionsSection.ignoreHierarchy.setSelected(b);
	}
	
	public void showAt(int x, int y) {
		setBounds(x-width/2, y-height/2, width, height);
		setVisible(true);
		
	}
	
	private void exitOrCancel() {
		if(isWorking)
			currentTask.cancel(true);
		else
			dispose();
	}
	
	public class FileWorker extends SwingWorker<Void,PapaFile> {

		private File input, output;
		private String inputString, outputString;
		private ImmutableTextureSettings settings = null;
		private ImportInterface importInterface = null;
		private FileNameExtensionFilter fileExtensionFilter;
		private boolean papaInput, recursive, writeLinked, overwrite, ignoreHierarchy;
		private AtomicInteger processedFiles = new AtomicInteger();
		private float totalFiles;
		
		public FileWorker(File inputDirectory, File outputDirectory, ImportInterface importInterface, FileNameExtensionFilter fileExtensionFilter, ImmutableTextureSettings settings,
							boolean recursive, boolean writeLinked, boolean overwrite, boolean ignoreHierarchy) {
			this.input = inputDirectory;
			this.output = outputDirectory;
			this.importInterface = importInterface;
			try {
				inputString = input.getCanonicalPath();
				outputString = output.getCanonicalPath();
			} catch(Exception e) {
				throw new IllegalArgumentException(e);
			}
			papaInput = importInterface == FileHandler.PAPA_INTERFACE;
			this.settings=settings;
			this.fileExtensionFilter=fileExtensionFilter;
			this.recursive=recursive;
			this.writeLinked=writeLinked;
			this.overwrite=overwrite;
			this.ignoreHierarchy = ignoreHierarchy;
		}
		
		@Override
		protected Void doInBackground() throws Exception {
			SwingUtilities.invokeLater(()->setWorking(true));
			logSection.clear();
			logSection.log("Beginning conversion of "+input.getName());
			progressSection.setStatus("Scannning File System");
			ImportInfo info = new ImportInfo();
			info.setTextureSettings(settings);
			info.setActivityListener(new ActivityListener() {
				
				@Override
				public void onFoundAcceptableFile(File f, int currentTotal) {
					SwingUtilities.invokeLater(() -> {
						progressSection.setTotal(currentTotal);
						progressSection.setProcessed(0);
					});
				}
				@Override
				public void onGotTotalFiles(int totalFiles) {
					SwingUtilities.invokeLater(() -> {
						FileWorker.this.totalFiles = totalFiles;
						progressSection.setTotal(totalFiles);
						progressSection.setStatus(null);
					});
				}
				@Override
				public void onEndProcessFile(File f, String threadName, boolean success) {
					setProgress((int) ((float)processedFiles.getAndAdd(1) / totalFiles * 100));
				}
				@Override
				public void onRejectFile(File f, String reason) {
					rejectFile(f, reason);
				}
				@Override
				public void onAcceptFile(PapaFile p) {
					writeFileToDestination(p);
				}
			});
			info.setInternalMode(true);
			try {
				FileHandler.readFiles(input, importInterface, info, recursive, true);
				if(info.getNumAcceptedFiles() ==0 && info.getNumRejectedFiles()==0)
					logSection.logLater("Input contains no "+importInterface.getType() +" files");
			} catch (InterruptedException e) {
				cancelButton.setEnabled(false);
				progressSection.setStatus("Stopping");
				FileHandler.cancelActiveTask();
				progressSection.setStatus("Task Cancelled");
			}
			logSection.logLater("Finished conversion of "+input.getName());
			
			SwingUtilities.invokeLater(()->setWorking(false));
			return null;
		}
		
		@Override
		protected void done() {
			try {
				get();
			} catch(Exception e) {
				if(e.getClass()!=CancellationException.class)
					throw new RuntimeException(e);
			}
		}
		
		private void rejectFile(File f, String reason) {
			SwingUtilities.invokeLater(()-> {
				progressSection.reject();
				logSection.log("Error: "+f.getName()+", "+reason);
			});
		}
		
		private void acceptFile(File f) {
			SwingUtilities.invokeLater(()-> {
				progressSection.accept();
				logSection.log("Success: "+f.getName());
			});
		}
		
		private void writeFileToDestination(PapaFile p) {
			File f = p.getFile();
			File targetLocation = getFileRelative(inputString, outputString, f);
			boolean hasMadeDir = false;
			String rejectMessage ="";
			if(papaInput) {
				if(p.getNumTextures()==0) {
					rejectFile(f, "File contains no textures");
					return;
				}
				for(int i = 0;i<p.getNumTextures();i++) {
					PapaTexture tex = p.getTexture(0);
					if(tex.isLinked()) {
						if( ! writeLinked) {
							rejectMessage+="Ignoring linked texture "+tex.getName()+"; ";
							continue;
						}
						if( ! tex.linkValid()) {
							rejectMessage+="Linked texture "+tex.getName()+" not found; ";
							continue;
						}
						tex = tex.getLinkedTexture();
					}
					File targetLocationImage = new File(targetLocation.getParent()+File.separator 
							+replaceExtension(extractName(tex.getName()),fileExtensionFilter.getExtensions()[0]));
					if(targetLocationImage.exists() && ! overwrite) {
						rejectMessage+="File"+targetLocationImage+" already exists; ";
						continue;
					}
					
					if(!hasMadeDir) {
						if(!ignoreHierarchy && ! makeDirectory(targetLocation.getParentFile())) {
							rejectFile(f, "Could not create directory "+targetLocation.getParentFile());
							return;
						}
						hasMadeDir = true;
					}
					try {
						FileHandler.exportImage(tex, targetLocationImage);
					} catch (IOException e) {
						rejectMessage+=e.getMessage()+"; ";
					}
				}
			} else {
				if(!ignoreHierarchy && !makeDirectory(targetLocation.getParentFile())) {
					rejectFile(f, "Could not create directory "+targetLocation.getParentFile());
					return;
				}
				File targetLocationTexture = new File(replaceExtension(targetLocation.getAbsolutePath(),fileExtensionFilter.getExtensions()[0]));
				if(targetLocationTexture.exists() && ! overwrite) {
					rejectFile(f, "File"+targetLocationTexture+" already exists; ");
					return;
				}
				try {
					FileHandler.writeFile(p, targetLocationTexture);
				} catch (IOException e) {
					rejectFile(f, e.getMessage());
					return;
				}
			}
			if(rejectMessage.equals(""))
				acceptFile(f);
			else
				rejectFile(f, rejectMessage.substring(0,rejectMessage.length() - 2));
				
		}
		
		private String extractName(String in) {
			int loc = Math.max(in.lastIndexOf('\\'), in.lastIndexOf('/'));
			if(loc == -1)
				return in;
			return in.substring(loc + 1);
		}
		
		private String replaceExtension(String input, String newExtension) {
			int loc = input.lastIndexOf('.');
			if(loc == -1)
				return input+"."+newExtension;
			else
				return input.substring(0,loc)+"."+newExtension;
		}
		
		private File getFileRelative(String sourceDirectory, String targetDirectory, File input) {
			String inputString;
			try {
				inputString = input.getCanonicalPath();
			} catch (IOException e) {
				throw new IllegalArgumentException(e);
			}
			if(ignoreHierarchy)
				return new File(targetDirectory + inputString.substring(inputString.lastIndexOf(File.separatorChar)));
			return new File(targetDirectory + inputString.substring(sourceDirectory.length()));
		}
		
		private synchronized boolean makeDirectory(File f) {
			File target = f;
			target.mkdirs();
			return target.exists();
		}
	}
	
	private class OptionsSection extends JPanel {
		private static final long serialVersionUID = -2793881931131513062L;
		private JTextField input, output;
		private JRadioButton toPapa, toImage;
		private JComboBox<String> formats;
		private JCheckBox subdirectories, writeLinked, overwrite, ignoreHierarchy;
		private JButton browseInput, browseOutput;
		
		private final Border defaultTextBorder;
		
		public OptionsSection() {
			setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(192, 192, 192)),"Options"));
			
			SpringLayout layout = new SpringLayout();
			setLayout(layout);
			
			final int leftOffset = 65;
			
			input = new JTextField();
			layout.putConstraint(SpringLayout.NORTH, input, 5, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.WEST, input, leftOffset, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.SOUTH, input, 25, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.EAST, input, -45, SpringLayout.EAST, this);
			add(input);
			
			input.getDocument().addDocumentListener(new DocumentListener() {
				
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
					validateSelection();
				}
			});
			
			browseInput = new JButton("...");
			layout.putConstraint(SpringLayout.NORTH, browseInput, 0, SpringLayout.NORTH, input);
			layout.putConstraint(SpringLayout.WEST, browseInput, 10, SpringLayout.EAST, input);
			layout.putConstraint(SpringLayout.SOUTH, browseInput, 0, SpringLayout.SOUTH, input);
			layout.putConstraint(SpringLayout.EAST, browseInput, -10, SpringLayout.EAST, this);
			browseInput.addActionListener((ActionEvent e) -> {
				JFileChooser j = new JFileChooser();
			
				File f = new File(input.getText());
				if(f.exists())
					j.setCurrentDirectory(f);
				
				j.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				j.setDialogTitle("Select Input Directory");
				if(j.showOpenDialog(OptionsSection.this)==JFileChooser.APPROVE_OPTION) {
					try {
						input.setText(j.getSelectedFile().getCanonicalPath());
						validateSelection();
					} catch (IOException e1) {}
				}
			});
			add(browseInput);
			
			output = new JTextField();
			layout.putConstraint(SpringLayout.NORTH, output, 5, SpringLayout.SOUTH, input);
			layout.putConstraint(SpringLayout.WEST, output, leftOffset, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.SOUTH, output, 25, SpringLayout.SOUTH, input);
			layout.putConstraint(SpringLayout.EAST, output, -45, SpringLayout.EAST, this);
			
			output.getDocument().addDocumentListener(new DocumentListener() {
				
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
					validateSelection();
				}
			});
			add(output);
			
			browseOutput = new JButton("...");
			layout.putConstraint(SpringLayout.NORTH, browseOutput, 0, SpringLayout.NORTH, output);
			layout.putConstraint(SpringLayout.WEST, browseOutput, 10, SpringLayout.EAST, output);
			layout.putConstraint(SpringLayout.SOUTH, browseOutput, 0, SpringLayout.SOUTH, output);
			layout.putConstraint(SpringLayout.EAST, browseOutput, -10, SpringLayout.EAST, this);
			browseOutput.addActionListener((ActionEvent e) -> {
				JFileChooser j = new JFileChooser();
				
				File f = new File(output.getText());
				if(f.exists())
					j.setCurrentDirectory(f);
				
				j.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				j.setDialogTitle("Select Output Directory");
				if(j.showOpenDialog(OptionsSection.this)==JFileChooser.APPROVE_OPTION) {
					try {
						output.setText(j.getSelectedFile().getCanonicalPath());
						validateSelection();
					} catch (IOException e1) {}
				}
			});
			add(browseOutput);
			
			toPapa = new JRadioButton("To Papa");
			layout.putConstraint(SpringLayout.NORTH, toPapa, 5, SpringLayout.SOUTH, output);
			layout.putConstraint(SpringLayout.WEST, toPapa, leftOffset, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.SOUTH, toPapa, 25, SpringLayout.SOUTH, output);
			layout.putConstraint(SpringLayout.EAST, toPapa, 80, SpringLayout.WEST, output);
			add(toPapa);
			toPapa.setSelected(true);
			
			toImage = new JRadioButton("To Image");
			layout.putConstraint(SpringLayout.NORTH, toImage, 5, SpringLayout.SOUTH, output);
			layout.putConstraint(SpringLayout.WEST, toImage, 35, SpringLayout.EAST, toPapa);
			layout.putConstraint(SpringLayout.SOUTH, toImage, 25, SpringLayout.SOUTH, output);
			layout.putConstraint(SpringLayout.EAST, toImage, 115, SpringLayout.EAST, toPapa);
			toImage.addChangeListener((ChangeEvent c) -> formats.setEnabled(toImage.isSelected()));
			add(toImage);
			
			ButtonGroup bg = new ButtonGroup();
			bg.add(toPapa);
			bg.add(toImage);
			
			
			formats = new JComboBox<String>();
			for(String s : ImageIO.getWriterFileSuffixes())
				formats.addItem(s);
			layout.putConstraint(SpringLayout.NORTH, formats, 5, SpringLayout.SOUTH, output);
			layout.putConstraint(SpringLayout.WEST, formats, 25, SpringLayout.EAST, toImage);
			layout.putConstraint(SpringLayout.SOUTH, formats, 25, SpringLayout.SOUTH, output);
			layout.putConstraint(SpringLayout.EAST, formats, 0, SpringLayout.EAST, output);
			formats.setEnabled(false);
			add(formats);
			
			subdirectories = new JCheckBox("Include Subdirectories");
			layout.putConstraint(SpringLayout.NORTH, subdirectories, 5, SpringLayout.SOUTH, formats);
			layout.putConstraint(SpringLayout.WEST, subdirectories, leftOffset, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.SOUTH, subdirectories, 25, SpringLayout.SOUTH, formats);
			layout.putConstraint(SpringLayout.EAST, subdirectories, 50, SpringLayout.WEST, toImage);
			add(subdirectories);
			
			writeLinked = new JCheckBox("Include Linked Files");
			layout.putConstraint(SpringLayout.NORTH, writeLinked, 5, SpringLayout.SOUTH, formats);
			layout.putConstraint(SpringLayout.WEST, writeLinked, 0, SpringLayout.EAST, subdirectories);
			layout.putConstraint(SpringLayout.SOUTH, writeLinked, 25, SpringLayout.SOUTH, formats);
			layout.putConstraint(SpringLayout.EAST, writeLinked, 0, SpringLayout.EAST, output);
			add(writeLinked);
			
			overwrite = new JCheckBox("Overwrite Destination");
			layout.putConstraint(SpringLayout.NORTH, overwrite, 5, SpringLayout.SOUTH, subdirectories);
			layout.putConstraint(SpringLayout.WEST, overwrite, leftOffset, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.SOUTH, overwrite, 25, SpringLayout.SOUTH, subdirectories);
			layout.putConstraint(SpringLayout.EAST, overwrite, 50, SpringLayout.WEST, toImage);
			add(overwrite);
			
			ignoreHierarchy = new JCheckBox("Ignore Hierarchy");
			layout.putConstraint(SpringLayout.NORTH, ignoreHierarchy, 5, SpringLayout.SOUTH, subdirectories);
			layout.putConstraint(SpringLayout.WEST, ignoreHierarchy, 0, SpringLayout.EAST, subdirectories);
			layout.putConstraint(SpringLayout.SOUTH, ignoreHierarchy, 25, SpringLayout.SOUTH, subdirectories);
			layout.putConstraint(SpringLayout.EAST, ignoreHierarchy, 0, SpringLayout.EAST, output);
			add(ignoreHierarchy);
			
			JLabel labelInput = new JLabel("Input:");
			layout.putConstraint(SpringLayout.NORTH, labelInput, 3, SpringLayout.NORTH, input);
			layout.putConstraint(SpringLayout.WEST, labelInput, 20, SpringLayout.WEST, this);
			add(labelInput);
			
			
			JLabel labelOutput = new JLabel("Output:");
			layout.putConstraint(SpringLayout.NORTH, labelOutput, 3, SpringLayout.NORTH, output);
			layout.putConstraint(SpringLayout.WEST, labelOutput, 20, SpringLayout.WEST, this);
			add(labelOutput);
			
			JLabel labelMode = new JLabel("Mode:");
			layout.putConstraint(SpringLayout.NORTH, labelMode, 3, SpringLayout.NORTH, toPapa);
			layout.putConstraint(SpringLayout.WEST, labelMode, 20, SpringLayout.WEST, this);
			add(labelMode);
			
			initializeTransferHandler();
			
			defaultTextBorder = input.getBorder();
		}
		
		private void initializeTransferHandler() {
		    input.setTransferHandler(new FileTransferHandler(input));
		    output.setTransferHandler(new FileTransferHandler(output));
		}
		
		private boolean validateSelection() {
			if(isWorking)
				return false;
			File in = new File(input.getText());
			File out = new File(output.getText());
			
			boolean inpitValid = (in.exists() && in.isDirectory());
			boolean outputValid = (out.exists() && out.isDirectory());
			
			if(inpitValid)
				input.setBorder(defaultTextBorder);
			else
				input.setBorder(BorderFactory.createLineBorder(Color.red));
			
			if(outputValid)
				output.setBorder(defaultTextBorder);
			else
				output.setBorder(BorderFactory.createLineBorder(Color.red));
			boolean valid = inpitValid && outputValid;
			convertButton.setEnabled(valid);
			return valid;
		}
		
		private class FileTransferHandler extends TransferHandler {
			private static final long serialVersionUID = 7164387004757064014L;
			private JTextField text;
			public FileTransferHandler(JTextField t) {
				this.text = t;
			}
			
			public boolean canImport(TransferHandler.TransferSupport support) {
	            if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor) || ! support.isDrop()) {
	                return false;
	            }
	            boolean moveSupported = (COPY & support.getSourceDropActions()) == COPY;

	            if (!moveSupported)
	                return false;
	            support.setDropAction(TransferHandler.COPY);
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
					File f = l.get(0);
					if(f.isDirectory())
						text.setText(f.getCanonicalPath());
					else
						text.setText("Invalid Selection");
					validateSelection();
	            } catch (Exception e) {
	            	e.printStackTrace();
	            	return false;
	            }

	            return true;
	        }
	    }
	}
	private class ProgressSection extends JPanel {
		private static final long serialVersionUID = -2793881931131513062L;
		private JProgressBar progress;
		JLabel labelAccepted, labelRejected, labelProcessed;
		private int accepted, rejected, processed, total;
		
		public ProgressSection() {
			setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(192, 192, 192)),"Progress"));
			
			SpringLayout layout = new SpringLayout();
			setLayout(layout);
			
			progress = new JProgressBar();
			layout.putConstraint(SpringLayout.NORTH, progress, 5, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.WEST, progress, 10, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.SOUTH, progress, 25, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.EAST, progress, -10, SpringLayout.EAST, this);
			progress.setStringPainted(true);
			add(progress);
			
			labelAccepted = new JLabel("Accepted: 0");
			layout.putConstraint(SpringLayout.NORTH, labelAccepted, 5, SpringLayout.SOUTH, progress);
			layout.putConstraint(SpringLayout.WEST, labelAccepted, 0, SpringLayout.WEST, progress);
			layout.putConstraint(SpringLayout.SOUTH, labelAccepted, 20, SpringLayout.SOUTH, progress);
			layout.putConstraint(SpringLayout.EAST, labelAccepted, 95, SpringLayout.WEST, progress);
			add(labelAccepted);
			
			labelRejected = new JLabel("Rejected: 0");
			layout.putConstraint(SpringLayout.NORTH, labelRejected, 5, SpringLayout.SOUTH, progress);
			layout.putConstraint(SpringLayout.WEST, labelRejected, 24, SpringLayout.EAST, labelAccepted);
			layout.putConstraint(SpringLayout.SOUTH, labelRejected, 20, SpringLayout.SOUTH, progress);
			layout.putConstraint(SpringLayout.EAST, labelRejected, 120, SpringLayout.EAST, labelAccepted);
			add(labelRejected);
			
			labelProcessed = new JLabel("Processed:     0 of     0");
			layout.putConstraint(SpringLayout.NORTH, labelProcessed, 5, SpringLayout.SOUTH, progress);
			layout.putConstraint(SpringLayout.WEST, labelProcessed, 24, SpringLayout.EAST, labelRejected);
			layout.putConstraint(SpringLayout.SOUTH, labelProcessed, 20, SpringLayout.SOUTH, progress);
			layout.putConstraint(SpringLayout.EAST, labelProcessed, 0, SpringLayout.EAST, progress);
			add(labelProcessed);
		}
		
		public void setStatus(String status) {
			progress.setString(status);
		}
		
		public void reject() {
			processed++;
			rejected++;
			labelRejected.setText("Rejected: "+rejected);
			setProcessed(processed);
			progress.setValue((int) ((float)processed / (float)total * 100f));
		}
		
		public void accept() {
			processed++;
			accepted++;
			labelAccepted.setText("Accepted: "+accepted);
			setProcessed(processed);
			
			progress.setValue((int) ((float)processed / (float)total * 100f));
		}
		
		private void setProcessed(int numProcessed) {
			labelProcessed.setText("Processed: "+String.format("%1$5s", processed)+" of "+String.format("%1$5s", total));
		}
		
		public void setTotal(int total) {
			this.total = total;
		}
		
		public void reset() {
			accepted = rejected = total = processed = 0;
			labelAccepted.setText("Accepted: 0");
			labelRejected.setText("Rejected: 0");
			labelProcessed.setText("Processed:     0 of     0");
			progress.setValue(0);
		}
	}
	
	private class LogSection extends JPanel {
		private static final long serialVersionUID = -2793881931131513062L;
		private JTextArea logTextArea;
		private JScrollPane jsp;
		
		public LogSection() {
			setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(192, 192, 192)),"Log"));
			
			SpringLayout layout = new SpringLayout();
			setLayout(layout);
			
			logTextArea = new JTextArea();
			logTextArea.setEditable(false);
			
			DefaultCaret caret = (DefaultCaret)logTextArea.getCaret();
			caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
			
			jsp = new JScrollPane(logTextArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
			layout.putConstraint(SpringLayout.NORTH, jsp, 5, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.WEST, jsp, 10, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.SOUTH, jsp, -10, SpringLayout.SOUTH, this);
			layout.putConstraint(SpringLayout.EAST, jsp, -10, SpringLayout.EAST, this);
			add(jsp);
			
		}
		
		public void logLater(String message) {
			SwingUtilities.invokeLater(()->log(message));
		}

		public void clear() {
			logTextArea.setText("");
		}
		
		public void log(String message) {
			logTextArea.append(message+"\n");
		}
	}
	
	private WindowListener wl = new WindowListener() {
		
		@Override
		public void windowOpened(WindowEvent e) {}
		
		@Override
		public void windowIconified(WindowEvent e) {}
		
		@Override
		public void windowDeiconified(WindowEvent e) {}
		
		@Override
		public void windowDeactivated(WindowEvent e) {}
		
		@Override
		public void windowClosing(WindowEvent e) {
			exitOrCancel();
		}
		
		@Override
		public void windowClosed(WindowEvent e) {}
		
		@Override
		public void windowActivated(WindowEvent e) {
		}
	};
}
