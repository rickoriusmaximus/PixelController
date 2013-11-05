/**
 * Copyright (C) 2011-2013 Michael Vogt <michu@neophob.com>
 *
 * This file is part of PixelController.
 *
 * PixelController is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * PixelController is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PixelController.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.neophob.sematrix.generator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.neophob.sematrix.PixelControllerElement;
import com.neophob.sematrix.generator.ColorScroll.ScrollMode;
import com.neophob.sematrix.generator.Generator.GeneratorName;
import com.neophob.sematrix.glue.Collector;
import com.neophob.sematrix.glue.FileUtils;
import com.neophob.sematrix.glue.MatrixData;
import com.neophob.sematrix.glue.Visual;
import com.neophob.sematrix.input.ISound;
import com.neophob.sematrix.properties.ApplicationConfigurationHelper;
import com.neophob.sematrix.properties.ValidCommands;
import com.neophob.sematrix.resize.IResize;

/**
 * The Class PixelControllerGenerator.
 *
 * @author michu
 */
public class PixelControllerGenerator implements PixelControllerElement {

    /** The log. */
    private static final Logger LOG = Logger.getLogger(PixelControllerGenerator.class.getName());

    private static final String DEFAULT_BLINKENLIGHTS = "torus.bml";
    private static final String DEFAULT_IMAGE = "logo.gif";
    private static final String DEFAULT_TEXT = "PixelInvaders!";
    private static final String DEFAULT_TTF = "04B_03__.TTF";
    private static final String DEFAULT_TTF_SIZE = "82";

    /** The all generators. */
    private List<Generator> allGenerators;

    /** The blinkenlights. */
    private Blinkenlights blinkenlights;

    /** The image. */
    private Image image;

    /** The ColorScroller. */
    private ColorScroll colorScroll;

    private OscListener oscListener1;
    private OscListener oscListener2;
    
    /** The textwriter. */
    private Textwriter textwriter;
    
	private float brightness = 1.0f;	
    
    private ApplicationConfigurationHelper ph;
    
    private FileUtils fileUtils;

    private boolean isCaptureGeneratorActive = false;
    
    private MatrixData matrix;
    
    private int fps;
    
    private ISound sound;
    
    private IResize resize;
    
    /**
     * Instantiates a new pixel controller generator.
     */
    public PixelControllerGenerator(ApplicationConfigurationHelper ph, FileUtils fileUtils, MatrixData matrix, 
    		int fps, ISound sound, IResize resize) {
        this.ph = ph;
        this.fileUtils = fileUtils;
        this.matrix = matrix;
        this.fps = fps;
        this.sound = sound;
        this.resize = resize;
    }


    /**
     * initialize all generators.
     */
    public void initAll() {
    	LOG.log(Level.INFO, "Start init, data root: {0}", fileUtils.getRootDirectory());
    	
        allGenerators = new CopyOnWriteArrayList<Generator>();	

        String fileBlinken = ph.getProperty(Blinkenlights.INITIAL_FILENAME, DEFAULT_BLINKENLIGHTS);
        blinkenlights = new Blinkenlights(matrix, fileBlinken, fileUtils, resize);
        allGenerators.add(blinkenlights);
        
        String fileImageSimple = ph.getProperty(Image.INITIAL_IMAGE, DEFAULT_IMAGE);
        image = new Image(matrix, fileImageSimple, fileUtils, resize);
        allGenerators.add(image);
        		
        allGenerators.add(new Plasma2(matrix));        
        allGenerators.add(new PlasmaAdvanced(matrix));
        allGenerators.add(new Fire(matrix));
        allGenerators.add(new PassThruGen(matrix));
        allGenerators.add(new Metaballs(matrix));
        allGenerators.add(new PixelImage(matrix,sound, fps));
        
        textwriter = new Textwriter(matrix, 
                ph.getProperty(Textwriter.FONT_FILENAME, DEFAULT_TTF), 
                Integer.parseInt(ph.getProperty(Textwriter.FONT_SIZE, DEFAULT_TTF_SIZE)),
                ph.getProperty(Textwriter.INITIAL_TEXT, DEFAULT_TEXT),
                fileUtils
        );
        allGenerators.add(textwriter);

        allGenerators.add(new Cell(matrix));
        allGenerators.add(new FFTSpectrum(matrix, sound));
        allGenerators.add(new Geometrics(matrix, sound));                
        
        int screenCapureXSize = ph.parseScreenCaptureWindowSizeX();
        if (screenCapureXSize>0) {
        	allGenerators.add(
        			new ScreenCapture(matrix, ph.parseScreenCaptureOffset(), screenCapureXSize, ph.parseScreenCaptureWindowSizeY())
        	);
            isCaptureGeneratorActive = true;
        }
        colorScroll = new ColorScroll(matrix);
        allGenerators.add(colorScroll);
        
        allGenerators.add(new ColorFade(matrix));
        
        this.oscListener1 = new OscListener(matrix, GeneratorName.OSC_GEN1);
        this.oscListener2 = new OscListener(matrix, GeneratorName.OSC_GEN2);
        allGenerators.add(oscListener1);
        allGenerators.add(oscListener2);
        
        allGenerators.add(new VisualZero(matrix));
        
    	LOG.log(Level.INFO, "Init finished");
    }

    /* (non-Javadoc)
     * @see com.neophob.sematrix.glue.PixelControllerElement#getCurrentState()
     */
    public List<String> getCurrentState() {
        List<String> ret = new ArrayList<String>();

        ret.add(ValidCommands.BLINKEN+" "+blinkenlights.getFilename());
        ret.add(ValidCommands.IMAGE+" "+image.getFilename());
        ret.add(ValidCommands.TEXTWR+" "+textwriter.getText());
        ret.add(ValidCommands.TEXTWR_OPTION+" "+textwriter.getTextscroller());
        ret.add(ValidCommands.COLOR_SCROLL_OPT+" "+colorScroll.getScrollMode().getMode());
        int brightnessInt = (int)(this.brightness*100f);
        ret.add(ValidCommands.CHANGE_BRIGHTNESS+" "+brightnessInt);
        
        return ret;
    }

    /* (non-Javadoc)
     * @see com.neophob.sematrix.glue.PixelControllerElement#update()
     */
    @Override
    public void update() {        
        //get a set with active visuals
        List<Visual> allVisuals = Collector.getInstance().getAllVisuals();
        Set<Integer> activeVisuals = new HashSet<Integer>();        
        for (Visual v: allVisuals) {
            activeVisuals.add(v.getGenerator1Idx());
            activeVisuals.add(v.getGenerator2Idx());
        }
        
        //update only selected generators
        for (Generator m: allGenerators) {
            if (activeVisuals.contains(m.getId())) {
                m.update();
                m.setActive(true);
            } else {
            	m.setActive(false);
            }
        }
    }




    /*
     * GENERATOR ======================================================
     */

    /**
     * Gets the generator.
     *
     * @param name the name
     * @return the generator
     */
    public Generator getGenerator(GeneratorName name) {
        for (Generator gen: allGenerators) {
            if (gen.getId() == name.getId()) {
                return gen;
            }
        }
        
        LOG.log(Level.WARNING, "Invalid Generator name selected: {0}", name);
        return null;
    }

    /**
     * Gets the all generators.
     *
     * @return the all generators
     */
    public List<Generator> getAllGenerators() {
        return allGenerators;
    }

    /**
     * Gets the generator.
     * 
     * return null if index is out of scope
     *
     * @param index the index
     * @return the generator
     */
    public Generator getGenerator(int index) {
        for (Generator gen: allGenerators) {
            if (gen.getId() == index) {
                return gen;
            }
        }

        LOG.log(Level.WARNING, "Invalid Generator index selected: {0}", index);
        return null;
    }

    /**
     * Gets the size.
     *
     * @return the size
     */
    public int getSize() {
        return allGenerators.size();
    }


    /**
     * Gets the file blinken.
     *
     * @return the file blinken
     */
    public String getFileBlinken() {
        return blinkenlights.getFilename();
    }

    /**
     * 
     * @return
     */
    public ScrollMode getScrollMode() {
    	return colorScroll.getScrollMode();
    }
    
    /**
     * Sets the file blinken.
     *
     * @param fileBlinken the new file blinken
     */
    public void setFileBlinken(String fileBlinken) {
        blinkenlights.loadFile(fileBlinken);
    }

    /**
     * Gets the file image simple.
     *
     * @return the file image simple
     */
    public String getFileImageSimple() {
        return image.getFilename();
    }

    /**
     * Sets the file image simple.
     *
     * @param fileImageSimple the new file image simple
     */
    public void setFileImageSimple(String fileImageSimple) {
        image.loadFile(fileImageSimple);

    }	

    /**
     * Sets the color scroll direction.
     *
     * @param colorScrollDir the newcolor scroll direction
     */
    public void setColorScrollingDirection(int colorScrollDir) {
    	colorScroll.setScrollMode(colorScrollDir);
    }
    
    /**
     * Gets the text.
     *
     * @return the text
     */
    public String getText() {
        return textwriter.getText();
    }

    /**
     * Sets the text.
     *
     * @param text the new text
     */
    public void setText(String text) {
        if (text==null || text.isEmpty()) {
            text=" ";
        }
        textwriter.createTextImage(text);
    }

    /**
     * 
     * @param scollerNr
     */
    public void setTextOption(int scollerNr) {
    	textwriter.setTextscroller(scollerNr);
    }
    
    public int getTextOption() {
    	return textwriter.getTextscroller();
    }

    
	/**
	 * @return the brightness
	 */
	public float getBrightness() {
		return brightness;
	}

	/**
	 * @param brightness the brightness to set
	 */
	public void setBrightness(float brightness) {
		if (brightness<0f || brightness>1.0f) {
			LOG.log(Level.WARNING, "Invalid brightness value: {0}", brightness);
			return;
		}
		this.brightness = brightness;
	}

	/**
	 * 
	 * @return
	 */
	public OscListener getOscListener1() {
		return oscListener1;
	}

	/**
	 * 
	 * @return
	 */
	public OscListener getOscListener2() {
		return oscListener2;
	}


    /**
     * @return the isCaptureGeneratorActive
     */
    public boolean isCaptureGeneratorActive() {
        return isCaptureGeneratorActive;
    }

	
}