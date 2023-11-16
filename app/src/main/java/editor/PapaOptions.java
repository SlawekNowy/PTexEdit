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
import java.awt.event.*;
import java.io.File;

import com.github.memo33.jsquish.Squish.CompressionMethod;

import papafile.PapaFile;
import papafile.PapaTexture.ImmutableTextureSettings;
import papafile.PapaTexture.TextureSettings;

import javax.swing.*;

public class PapaOptions extends JDialog  {

	private static final long serialVersionUID = -6635278548154607017L;
	private static final int MINIMUM_RESIZE_SIZE = 128;
	private static final int MAXIMUM_RESIZE_SIZE = 2048;
	private JPanel contentPane;
	private JButton okButton;
	private JCheckBox repromptCheckBox;
	private final int width = 555;
	private final int height = 320;
	private TextureSettings internalSettings;
	private ImmutableTextureSettings settings;
	private GeneralSection generalSection;
	private ResizeSection resizeSection;
	private MipmapSection mipmapSection;
	private LinkSection linkSection;
	private JLabel fileName;
	private boolean wasAccepted;
	private boolean ignoreReprompt;
	private boolean multiMode;
	
	
	public void setActiveFile(File f) {
		if(f==null) {
			fileName.setText("");
			return;
		}
		if(!f.exists())
			throw new IllegalArgumentException("Files "+f.getAbsolutePath()+" does not exist");
		boolean isDirectory = f.isDirectory();
		fileName.setText("Importing "+f.getAbsolutePath()+" ("+(isDirectory ? "directory" : "file") +")");
	}

	public void updateLinkOptions(PapaFile[] targetablePapaFiles) {
		linkSection.setTargetableFiles(targetablePapaFiles);
	}
	
	public void showAt(int x, int y) {
		validateSettings();
		wasAccepted = false;
		setBounds(x-width/2, y-height/2, width, height);
		setVisible(true);
		
	}

	public boolean getWasAccepted() {
		return wasAccepted;
	}
	
	public ImmutableTextureSettings getCurrentSettings() {
		return settings;
	}
	
	public void validateSettings() {
		generalSection.applySettings(internalSettings);
		resizeSection.applySettings(internalSettings);
		mipmapSection.applySettings(internalSettings);
		linkSection.applySettings(internalSettings);
		applySettings(internalSettings);
	}
	
	public void applySettingsDirect(TextureSettings settings) {
		this.internalSettings = settings;
		if(this.internalSettings==null)
			this.internalSettings = TextureSettings.defaultSettings();
		validateSettings();
	}
	
	public boolean ignoreReprompt() {
		boolean ret = ignoreReprompt;
		ignoreReprompt = false;
		return ret;
	}
	
	public void setMultiMode(boolean multiMode) {
		this.multiMode = multiMode;
	}
	
	private void applySettings(TextureSettings settings) {
		repromptCheckBox.setSelected(true);
		repromptCheckBox.setVisible(multiMode);
		ignoreReprompt = true;
	}
	
	private void commit() { // applies all of the current settings
		TextureSettings settings = new TextureSettings();
		generalSection.commit(settings);
		resizeSection.commit(settings);
		mipmapSection.commit(settings);
		linkSection.commit(settings);
		settings.setSRGBTexname(this.settings.srgbTexname);
		
		internalSettings = settings;
		this.settings = internalSettings.immutable();
	}
	
	public PapaOptions(JFrame owner, ImmutableTextureSettings currentValues) {
		super(owner);
		
		setIconImages(owner.getIconImages());
		
		ImmutableTextureSettings t = currentValues;
		if(t==null)
			t = TextureSettings.defaultSettings().immutable();
		internalSettings = new TextureSettings();
		internalSettings.setFormat(t.format);
		internalSettings.setCompressionMethod(t.method);
		internalSettings.setGenerateMipmaps(t.generateMipmaps);
		internalSettings.setMipmapResizeMethod(t.mipmapResizeMethod);
		internalSettings.setSRGB(t.SRGB);
		internalSettings.setResize(t.resize);
		internalSettings.setResizeMethod(t.resizeMethod);
		internalSettings.setResizeMode(t.resizeMode);
		internalSettings.setSRGBTexname(t.srgbTexname);
		settings = t;
		
		
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		setModalityType(ModalityType.APPLICATION_MODAL);
		setResizable(false);
		setTitle("Import Settings");
		
		setBounds(0, 0, width, height);
		
		addWindowListener(wl);
		
		contentPane = new JPanel();
		setContentPane(contentPane);
		SpringLayout layout = new SpringLayout();
		contentPane.setLayout(layout);
		
		fileName = new JLabel();
		layout.putConstraint(SpringLayout.NORTH, fileName, 5, SpringLayout.NORTH, contentPane);
		layout.putConstraint(SpringLayout.WEST, fileName, 5, SpringLayout.WEST, contentPane);
		layout.putConstraint(SpringLayout.SOUTH, fileName, 20, SpringLayout.NORTH, contentPane);
		layout.putConstraint(SpringLayout.EAST, fileName, -5, SpringLayout.EAST, contentPane);
		add(fileName);
		
		JPanel settingsPanel = new JPanel();
		settingsPanel.setBorder(BorderFactory.createLineBorder(new Color(192, 192, 192)));
		layout.putConstraint(SpringLayout.NORTH, settingsPanel, 25, SpringLayout.NORTH, contentPane);
		layout.putConstraint(SpringLayout.WEST, settingsPanel, 5, SpringLayout.WEST, contentPane);
		layout.putConstraint(SpringLayout.SOUTH, settingsPanel, -35, SpringLayout.SOUTH, contentPane);
		layout.putConstraint(SpringLayout.EAST, settingsPanel, -5, SpringLayout.EAST, contentPane);
		add(settingsPanel);
		
		SpringLayout innerLayout = new SpringLayout();
		settingsPanel.setLayout(innerLayout);
		
		generalSection = new GeneralSection(internalSettings);
		innerLayout.putConstraint(SpringLayout.NORTH, generalSection, 10, SpringLayout.NORTH, settingsPanel);
		innerLayout.putConstraint(SpringLayout.WEST, generalSection, 10, SpringLayout.WEST, settingsPanel);
		innerLayout.putConstraint(SpringLayout.SOUTH, generalSection, 90, SpringLayout.NORTH, settingsPanel);
		innerLayout.putConstraint(SpringLayout.EAST, generalSection, 260, SpringLayout.WEST, settingsPanel);
		settingsPanel.add(generalSection);
		
		resizeSection = new ResizeSection(internalSettings);
		innerLayout.putConstraint(SpringLayout.NORTH, resizeSection, 5, SpringLayout.SOUTH, generalSection);
		innerLayout.putConstraint(SpringLayout.WEST, resizeSection, 0, SpringLayout.WEST, generalSection);
		innerLayout.putConstraint(SpringLayout.SOUTH, resizeSection, 120, SpringLayout.SOUTH, generalSection);
		innerLayout.putConstraint(SpringLayout.EAST, resizeSection, 0, SpringLayout.EAST, generalSection);
		settingsPanel.add(resizeSection);
		
		mipmapSection = new MipmapSection(internalSettings);
		innerLayout.putConstraint(SpringLayout.NORTH, mipmapSection, 0, SpringLayout.NORTH, generalSection);
		innerLayout.putConstraint(SpringLayout.WEST, mipmapSection, 10, SpringLayout.EAST, generalSection);
		innerLayout.putConstraint(SpringLayout.SOUTH, mipmapSection, 0, SpringLayout.SOUTH, generalSection);
		innerLayout.putConstraint(SpringLayout.EAST, mipmapSection, 260, SpringLayout.EAST, generalSection);
		settingsPanel.add(mipmapSection);
		
		linkSection = new LinkSection(internalSettings);
		innerLayout.putConstraint(SpringLayout.NORTH, linkSection, 5, SpringLayout.SOUTH, generalSection);
		innerLayout.putConstraint(SpringLayout.WEST, linkSection, 10, SpringLayout.EAST, generalSection);
		innerLayout.putConstraint(SpringLayout.SOUTH, linkSection, 120, SpringLayout.SOUTH, generalSection);
		innerLayout.putConstraint(SpringLayout.EAST, linkSection, 260, SpringLayout.EAST, generalSection);
		settingsPanel.add(linkSection);
		
		JButton cancelButton = new JButton("Cancel");
		cancelButton.setMnemonic('c');
		cancelButton.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				dispose();
			}
			
		});
		
		layout.putConstraint(SpringLayout.NORTH, cancelButton, 5, SpringLayout.SOUTH, settingsPanel);
		layout.putConstraint(SpringLayout.WEST, cancelButton, -85, SpringLayout.EAST, contentPane);
		layout.putConstraint(SpringLayout.SOUTH, cancelButton, -5, SpringLayout.SOUTH, contentPane);
		layout.putConstraint(SpringLayout.EAST, cancelButton, -5, SpringLayout.EAST, contentPane);
		add(cancelButton);
		
		okButton = new JButton("OK");
		okButton.setMnemonic('o');
		okButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				commit();
				wasAccepted = true;
				dispose();
			}
			
		});
		
		layout.putConstraint(SpringLayout.NORTH, okButton, 5, SpringLayout.SOUTH, settingsPanel);
		layout.putConstraint(SpringLayout.WEST, okButton, -85, SpringLayout.WEST, cancelButton);
		layout.putConstraint(SpringLayout.SOUTH, okButton, -5, SpringLayout.SOUTH, contentPane);
		layout.putConstraint(SpringLayout.EAST, okButton, -5, SpringLayout.WEST, cancelButton);
		add(okButton);
		
		repromptCheckBox = new JCheckBox("Do not reprompt for this import");
		repromptCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ignoreReprompt = repromptCheckBox.isSelected();
			}
			
		});
		
		layout.putConstraint(SpringLayout.NORTH, repromptCheckBox, 5, SpringLayout.SOUTH, settingsPanel);
		layout.putConstraint(SpringLayout.WEST, repromptCheckBox, 0, SpringLayout.WEST, contentPane);
		layout.putConstraint(SpringLayout.SOUTH, repromptCheckBox, -5, SpringLayout.SOUTH, contentPane);
		layout.putConstraint(SpringLayout.EAST, repromptCheckBox, 250, SpringLayout.WEST, contentPane);
		add(repromptCheckBox);
		
		this.getRootPane().setDefaultButton(okButton);
		
		validateSettings();
	}
	
	private class GeneralSection extends JPanel {
		private static final long serialVersionUID = -2793881931131513062L;
		private JComboBox<String> formatSelector, dxtCompressionMode;
		private JLabel labelFormat, labelDXTMode;

		
		private int methodToIndex(CompressionMethod method) {
			if(method.equals(CompressionMethod.RANGE_FIT))
				return 0;
			if(method.equals(CompressionMethod.CLUSTER_FIT))
				return 1;
			return 1;
		}
		
		private CompressionMethod valueToMethod(String value) {
			if(value.equals("Quality"))
				return CompressionMethod.CLUSTER_FIT;
			if(value.equals("Fast"))
				return CompressionMethod.RANGE_FIT;
			return CompressionMethod.CLUSTER_FIT;
		}
		
		private boolean checkIsDXT() {
			String value = (String) formatSelector.getSelectedItem();
			return value.equals(TextureSettings.DXT1) || value.equals(TextureSettings.DXT5) || value.equals(TextureSettings.DXT_AUTO);
		}
		
		public GeneralSection(TextureSettings settings) {
			setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(192, 192, 192)),"General"));
			
			SpringLayout layout = new SpringLayout();
			setLayout(layout);
			
			final int leftOffset = 85;
			
			final String[] formats = new String[] {TextureSettings.DXT_AUTO, TextureSettings.DXT1,TextureSettings.DXT5,
					TextureSettings.R8G8B8A8,TextureSettings.R8G8B8X8,TextureSettings.B8G8R8A8,TextureSettings.R8}; // DXT3
			
			formatSelector = new JComboBox<String>();
			for(String s : formats)
				formatSelector.addItem(s);
			layout.putConstraint(SpringLayout.NORTH, formatSelector, 5, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.WEST, formatSelector, leftOffset, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.SOUTH, formatSelector, 25, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.EAST, formatSelector, -10, SpringLayout.EAST, this);
			add(formatSelector);
			
			formatSelector.addActionListener(new ActionListener() {
				
				@Override
				public void actionPerformed(ActionEvent e) {
					boolean enabled = checkIsDXT();
					dxtCompressionMode.setEnabled(enabled);
					labelDXTMode.setEnabled(enabled);
				}
			});
			
			dxtCompressionMode = new JComboBox<String>();
			dxtCompressionMode.addItem("Fast");
			dxtCompressionMode.addItem("Quality");
			layout.putConstraint(SpringLayout.NORTH, dxtCompressionMode, 5, SpringLayout.SOUTH, formatSelector);
			layout.putConstraint(SpringLayout.WEST, dxtCompressionMode, leftOffset, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.SOUTH, dxtCompressionMode, 25, SpringLayout.SOUTH, formatSelector);
			layout.putConstraint(SpringLayout.EAST, dxtCompressionMode, -10, SpringLayout.EAST, this);
			add(dxtCompressionMode);
			
			labelFormat = new JLabel("Format:");
			layout.putConstraint(SpringLayout.NORTH, labelFormat, 3, SpringLayout.NORTH, formatSelector);
			layout.putConstraint(SpringLayout.WEST, labelFormat, 20, SpringLayout.WEST, this);
			add(labelFormat);
			
			
			labelDXTMode = new JLabel("DXT Mode:");
			layout.putConstraint(SpringLayout.NORTH, labelDXTMode, 3, SpringLayout.NORTH, dxtCompressionMode);
			layout.putConstraint(SpringLayout.WEST, labelDXTMode, 20, SpringLayout.WEST, this);
			add(labelDXTMode);
			
		}
		
		private void applySettings(TextureSettings settings) {
			formatSelector.setSelectedItem(settings.getFormat());
			dxtCompressionMode.setSelectedIndex(methodToIndex(settings.getCompressionMethod()));
			dxtCompressionMode.setEnabled(checkIsDXT());
			labelDXTMode.setEnabled(checkIsDXT());
		}
		
		private void commit(TextureSettings settings) {
			settings.setFormat(formatSelector.getSelectedItem().toString());
			settings.setCompressionMethod(valueToMethod(dxtCompressionMode.getSelectedItem().toString()));
		}
	}
	
	private class ResizeSection extends JPanel {
		private static final long serialVersionUID = -2889774212551032335L;
		private JCheckBox doResize;
		private JComboBox<String> resizeMethod;
		private JComboBox<Object> resizeMode;
		private JLabel labelMethod, labelAlgorithm;
		
		public ResizeSection(TextureSettings settings) {
			setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(192, 192, 192)),"Resize"));
			
			SpringLayout layout = new SpringLayout();
			setLayout(layout);
			
			doResize = new JCheckBox("Resize");
			layout.putConstraint(SpringLayout.NORTH, doResize, 5, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.WEST, doResize, 15, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.SOUTH, doResize, 25, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.EAST, doResize, -10, SpringLayout.EAST, this);
			add(doResize);
			
			doResize.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					boolean enabled = doResize.isSelected();
					resizeMode.setEnabled(enabled);
					resizeMethod.setEnabled(enabled);
					labelAlgorithm.setEnabled(enabled);
					labelMethod.setEnabled(enabled);
				}
				
			});
			
			final int leftOffset = 85;
			
			
			resizeMode = new JComboBox<Object>();
			resizeMode.addItem("Nearest Power of 2");
			resizeMode.addItem("Up to Power of 2");
			resizeMode.addItem("Down to Power of 2");
			for(int i = PapaOptions.MAXIMUM_RESIZE_SIZE;i>=PapaOptions.MINIMUM_RESIZE_SIZE;i>>>=1) {
				resizeMode.addItem(new Integer(i));
			}
			layout.putConstraint(SpringLayout.NORTH, resizeMode, 5, SpringLayout.SOUTH, doResize);
			layout.putConstraint(SpringLayout.WEST, resizeMode, leftOffset, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.SOUTH, resizeMode, 25, SpringLayout.SOUTH, doResize);
			layout.putConstraint(SpringLayout.EAST, resizeMode, -10, SpringLayout.EAST, this);
			add(resizeMode);
			
			resizeMethod = new JComboBox<String>();
			resizeMethod.addItem("Nearest Neighbour");
			resizeMethod.addItem("Bicubic");
			resizeMethod.addItem("Bilinear");
			layout.putConstraint(SpringLayout.NORTH, resizeMethod, 5, SpringLayout.SOUTH, resizeMode);
			layout.putConstraint(SpringLayout.WEST, resizeMethod, leftOffset, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.SOUTH, resizeMethod, 25, SpringLayout.SOUTH, resizeMode);
			layout.putConstraint(SpringLayout.EAST, resizeMethod, -10, SpringLayout.EAST, this);
			add(resizeMethod);
			
			labelMethod = new JLabel("Value:");
			layout.putConstraint(SpringLayout.NORTH, labelMethod, 3, SpringLayout.NORTH, resizeMode);
			layout.putConstraint(SpringLayout.WEST, labelMethod, 20, SpringLayout.WEST, this);
			add(labelMethod);
			
			labelAlgorithm = new JLabel("Algorithm:");
			layout.putConstraint(SpringLayout.NORTH, labelAlgorithm, 3, SpringLayout.NORTH, resizeMethod);
			layout.putConstraint(SpringLayout.WEST, labelAlgorithm, 20, SpringLayout.WEST, this);
			add(labelAlgorithm);
			
		}
		
		private void applySettings(TextureSettings settings) {
			doResize.setSelected(settings.getResize());
			if(settings.getResizeMode()>=PapaOptions.MINIMUM_RESIZE_SIZE) {
				resizeMode.setSelectedItem((Integer)settings.getResizeMode());
			} else {
				resizeMode.setSelectedIndex(settings.getResizeMode());
			}
			resizeMode.setEnabled(settings.getResize());
			resizeMethod.setEnabled(settings.getResize());
			resizeMethod.setSelectedIndex(settings.getResizeMethod());
			labelAlgorithm.setEnabled(settings.getResize());
			labelMethod.setEnabled(settings.getResize());
		}
		
		private void commit(TextureSettings settings) {
			settings.setResize(doResize.isSelected());
			settings.setResizeMethod(resizeMethod.getSelectedIndex());
			if(resizeMode.getSelectedItem().getClass() == Integer.class) {
				settings.setResizeMode((Integer)resizeMode.getSelectedItem());
			} else {
				settings.setResizeMode(resizeMode.getSelectedIndex());
			}
			
		}
	}
	private class MipmapSection extends JPanel {
		private static final long serialVersionUID = -2889774212551032335L;
		private JCheckBox generateMipmaps;
		private JComboBox<String> mipmapScaleAlgorithm;
		private JLabel labelAlgorithm;
		
		public MipmapSection(TextureSettings settings) {
			setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(192, 192, 192)),"Mipmaps"));
			
			SpringLayout layout = new SpringLayout();
			setLayout(layout);
			
			generateMipmaps = new JCheckBox("Generate Mipmaps");
			layout.putConstraint(SpringLayout.NORTH, generateMipmaps, 5, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.WEST, generateMipmaps, 15, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.SOUTH, generateMipmaps, 25, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.EAST, generateMipmaps, -10, SpringLayout.EAST, this);
			add(generateMipmaps);
			
			generateMipmaps.addActionListener(new ActionListener() {
				
				@Override
				public void actionPerformed(ActionEvent e) {
					boolean enabled = generateMipmaps.isSelected();
					mipmapScaleAlgorithm.setEnabled(enabled);
					labelAlgorithm.setEnabled(enabled);
					
				}
			});
			
			final int leftOffset = 85;
			
			mipmapScaleAlgorithm = new JComboBox<String>();
			mipmapScaleAlgorithm.addItem("Nearest Neighbour");
			mipmapScaleAlgorithm.addItem("Bicubic");
			mipmapScaleAlgorithm.addItem("Bilinear");
			layout.putConstraint(SpringLayout.NORTH, mipmapScaleAlgorithm, 5, SpringLayout.SOUTH, generateMipmaps);
			layout.putConstraint(SpringLayout.WEST, mipmapScaleAlgorithm, leftOffset, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.SOUTH, mipmapScaleAlgorithm, 25, SpringLayout.SOUTH, generateMipmaps);
			layout.putConstraint(SpringLayout.EAST, mipmapScaleAlgorithm, -10, SpringLayout.EAST, this);
			add(mipmapScaleAlgorithm);
			
			labelAlgorithm = new JLabel("Algorithm:");
			layout.putConstraint(SpringLayout.NORTH, labelAlgorithm, 3, SpringLayout.NORTH, mipmapScaleAlgorithm);
			layout.putConstraint(SpringLayout.WEST, labelAlgorithm, 20, SpringLayout.WEST, this);
			add(labelAlgorithm);
			
			applySettings(settings);
		}
		
		private void applySettings(TextureSettings settings) {
			generateMipmaps.setSelected(settings.getGenerateMipmaps());
			mipmapScaleAlgorithm.setSelectedIndex(settings.getMipmapResizeMethod());
			mipmapScaleAlgorithm.setEnabled(settings.getGenerateMipmaps());
			labelAlgorithm.setEnabled(settings.getGenerateMipmaps());
		}
		
		private void commit(TextureSettings settings) {
			settings.setGenerateMipmaps(generateMipmaps.isSelected());
			settings.setMipmapResizeMethod(mipmapScaleAlgorithm.getSelectedIndex());
		}
	}
	
	private class LinkSection extends JPanel {
		private static final long serialVersionUID = -2889774212551032335L;
		private JCheckBox linkEnabled;
		private JComboBox<PapaFile> linkTargets;
		private JComboBox<String> linkMethods;
		private JLabel labelTarget, labelLinkTypes;
		
		public LinkSection(TextureSettings settings) {
			setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(192, 192, 192)),"Link"));
			
			SpringLayout layout = new SpringLayout();
			setLayout(layout);
			
			linkEnabled = new JCheckBox("Link Enabled");
			layout.putConstraint(SpringLayout.NORTH, linkEnabled, 5, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.WEST, linkEnabled, 15, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.SOUTH, linkEnabled, 25, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.EAST, linkEnabled, -10, SpringLayout.EAST, this);
			add(linkEnabled);
			
			linkEnabled.setEnabled(false);
			
			linkEnabled.addActionListener(new ActionListener() {
				
				@Override
				public void actionPerformed(ActionEvent e) {
					linkTargets.setEnabled(linkEnabled.isSelected());
					linkMethods.setEnabled(linkEnabled.isSelected());
					labelTarget.setEnabled(linkEnabled.isSelected());
					labelLinkTypes.setEnabled(linkEnabled.isSelected());
				}
			});
			
			final int leftOffset = 85;
			
			linkTargets = new JComboBox<PapaFile>();
			layout.putConstraint(SpringLayout.NORTH, linkTargets, 5, SpringLayout.SOUTH, linkEnabled);
			layout.putConstraint(SpringLayout.WEST, linkTargets, leftOffset, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.SOUTH, linkTargets, 25, SpringLayout.SOUTH, linkEnabled);
			layout.putConstraint(SpringLayout.EAST, linkTargets, -10, SpringLayout.EAST, this);
			add(linkTargets);
			
			labelTarget = new JLabel("Target:");
			layout.putConstraint(SpringLayout.NORTH, labelTarget, 3, SpringLayout.NORTH, linkTargets);
			layout.putConstraint(SpringLayout.WEST, labelTarget, 20, SpringLayout.WEST, this);
			add(labelTarget);
			
			linkMethods = new JComboBox<String>();
			linkMethods.addItem("Embed");
			linkMethods.addItem("Link");
			layout.putConstraint(SpringLayout.NORTH, linkMethods, 5, SpringLayout.SOUTH, linkTargets);
			layout.putConstraint(SpringLayout.WEST, linkMethods, leftOffset, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.SOUTH, linkMethods, 25, SpringLayout.SOUTH, linkTargets);
			layout.putConstraint(SpringLayout.EAST, linkMethods, -10, SpringLayout.EAST, this);
			add(linkMethods);
			
			labelLinkTypes = new JLabel("Link Type:");
			layout.putConstraint(SpringLayout.NORTH, labelLinkTypes, 3, SpringLayout.NORTH, linkMethods);
			layout.putConstraint(SpringLayout.WEST, labelLinkTypes, 20, SpringLayout.WEST, this);
			add(labelLinkTypes);
			
			applySettings(settings);
		}
		
		private void setTargetableFiles(PapaFile[] targets) {
			linkTargets.removeAllItems();
			linkEnabled.setEnabled(targets.length!=0);
			for(PapaFile p : targets)
				linkTargets.addItem(p);
		}
		
		private void applySettings(TextureSettings settings) {
			linkEnabled.setSelected(settings.getLinkEnabled() && linkTargets.getItemCount()!=0);
			linkEnabled.setEnabled(linkTargets.getItemCount()!=0);
			
			boolean enable = linkEnabled.isSelected();
			
			linkTargets.setSelectedItem(settings.getLinkTarget());
			linkTargets.setEnabled(enable);
			if(linkTargets.getSelectedIndex()==-1 && linkTargets.getItemCount()!=0)
				linkTargets.setSelectedIndex(0);
			
			labelTarget.setEnabled(enable);
			
			linkMethods.setSelectedIndex(settings.getLinkMethod());
			linkMethods.setEnabled(enable);
			
			labelLinkTypes.setEnabled(enable);
		}
		
		private void commit(TextureSettings settings) {
			settings.setLinkEnabled(linkEnabled.isSelected());
			settings.setLinkTarget((PapaFile)linkTargets.getSelectedItem());
			settings.setLinkMethod(linkMethods.getSelectedIndex());
			linkEnabled.setSelected(false); // spaghetti fix for auto convert. this section is basically unused anyway
		}
	}
	private WindowListener wl = new WindowListener() {
		
		@Override
		public void windowOpened(WindowEvent e) {
		}
		
		@Override
		public void windowIconified(WindowEvent e) {}
		
		@Override
		public void windowDeiconified(WindowEvent e) {}
		
		@Override
		public void windowDeactivated(WindowEvent e) {}
		
		@Override
		public void windowClosing(WindowEvent e) {}
		
		@Override
		public void windowClosed(WindowEvent e) {}
		
		@Override
		public void windowActivated(WindowEvent e) {
			okButton.requestFocusInWindow();
		}
	};
}
