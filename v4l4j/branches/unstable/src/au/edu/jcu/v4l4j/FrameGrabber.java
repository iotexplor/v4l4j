
/*
* Copyright (C) 2007-2008 Gilles Gigan (gilles.gigan@gmail.com)
* eResearch Centre, James Cook University (eresearch.jcu.edu.au)
*
* This program was developed as part of the ARCHER project
* (Australian Research Enabling Environment) funded by a   
* Systemic Infrastructure Initiative (SII) grant and supported by the Australian
* Department of Innovation, Industry, Science and Research
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public  License as published by the
* Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
* or FITNESS FOR A PARTICULAR PURPOSE.  
* See the GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program.  If not, see <http://www.gnu.org/licenses/>.
*
*/


package au.edu.jcu.v4l4j;
/**
 * 
 * @author gilles 
 */

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import au.edu.jcu.v4l4j.exceptions.CaptureChannelException;
import au.edu.jcu.v4l4j.exceptions.ControlException;
import au.edu.jcu.v4l4j.exceptions.ImageDimensionsException;
import au.edu.jcu.v4l4j.exceptions.ImageFormatException;
import au.edu.jcu.v4l4j.exceptions.InitialistationException;
import au.edu.jcu.v4l4j.exceptions.StateException;
import au.edu.jcu.v4l4j.exceptions.V4L4JException;
import au.edu.jcu.v4l4j.exceptions.VideoStandardException;


/**
 * This class provides methods to :
 * <ul>
 * <li>Capture JPEG-encoded frames from a Video4Linux source and,</li>
 * <li>Control the video source.</li>
 * </ul>
 * Create an instance of it attached to a V4L device to grab JPEG-encoded frames from it. A typical use is as follows:
 * <ul>
 * <li>Create an instance of FrameGrabber: <code>FrameGrabber f = new FrameGrabber("/dev/video0", 320, 240, 0, 0, 80);</code></li>
 * <li>Initialise the framegrabber: <code>f.init();</code></li>
 * <li>Start the frame capture: <code>f.startCapture();</code></li>
 * <li><code>while (!stop) </code></li> 
 *  <ul><li>Retrieve a frame: <code>ByteBuffer b= f.getFrame();</code> (frame size is <code>b.limit()</code>)</li>
 *  <li>do something useful with b</li>
 *  </ul>
 * <li>Stop the capture: <code>f.stopCapture();</code></li>
 * <li>Free resources: <code>f.remove();</code></li>
 * </ul>
 * Once the frame grabber is intialised, the video source controls are made available (<code>f.getControls</code>) and can be changed at any time.
 * Once the frame grabber is <code>remove</code>d(), it can be re-initialised again (without the need to create a new instance). 
 * 
 * @author gilles
 *
 */
public class FrameGrabber {
	/**
	 * Video standard value for webcams
	 */
	public static int WEBCAM=0;
	/**
	 * Video standard value for PAL sources
	 */
	public static int PAL=1;
	/**
	 * Video standard value for SECAM sources
	 */
	public static int SECAM=2;
	/**
	 * Video standard value for NTSC sources
	 */
	public static int NTSC=3;
	/**
	 * Setting the capture width to this value will set the actual width to the
	 * maximum width supported by the hardware  
	 */
	public static int MAX_WIDTH = 0;
	/**
	 * Setting the capture height to this value will set the actual height to the
	 * maximum height supported by the hardware  
	 */
	public static int MAX_HEIGHT = 0;
	
	/**
	 * This value represents the maximum value of the JPEG quality setting
	 */
	public static int MAX_JPEG_QUALITY = 100;
	
	/**
	 * This value represents the minimum value of the JPEG quality setting
	 */
	public static int MIN_JPEG_QUALITY = 0;	
	
	private String dev;
	private int width;
	private int height;
	private int channel;
	private int quality;
	private int standard;
	private int nbV4LBuffers = 4;
	private ByteBuffer[] bufs;
	private State state;
	private Control[] ctrls;
	
	/*
	 * JNI returns a long (which is really a pointer) when a device is allocated for use
	 * This field is read-only (!!!) 
	 */
	private long object;
	
	static {
		try {
			System.loadLibrary("v4l4j");
		} catch (UnsatisfiedLinkError e) {
			System.err.println("Cant load v4l4j JNI library");
			throw e;
		}
	}
	
	private native long allocateObject() throws InitialistationException;
	private native ByteBuffer[] init_v4l(long o, String f, int w, int h, int ch, int std, int nbBuf, int q)
		throws V4L4JException;
	private native void start(long o) throws V4L4JException;
	private native void setQuality(long o, int i);
	private native int getBuffer(long o) throws V4L4JException;
	private native int getBufferLength(long o);
	private native void stop(long o);
	private native void delete(long o) throws V4L4JException;
	private native int getCtrlValue(long o, int i) throws ControlException;
	private native int setCtrlValue(long o, int i, int v) throws ControlException;
	private native long freeObject(long o);
	
	/**
	 * Construct a FrameGrabber object used to capture JPEG frames from a video source
	 * @param device the V4L device from which to capture
	 * @param w the requested frame width 
	 * @param h the requested frame height
	 * @param ch the channel
	 * @param std the video standard
	 * @param q the JPEG image quality (the higher, the better the quality)
	 * @throws V4L4JException if one of the JPEG quality value is incorrect or the device file is not a readable file
	 */
	public FrameGrabber(String device, int w, int h, int ch, int std, int q) throws V4L4JException {
		if(!(new File(device).canRead()))
			throw new V4L4JException("The device file is not readable");
		
		if(q<MIN_JPEG_QUALITY || q>MAX_JPEG_QUALITY)
			throw new V4L4JException("The JPEG quality must be 0<q<100");
		
		state= new State();
		
		dev = device;
		width = w;
		height = h;
		channel = ch;
		standard= std;
		quality = q;
	}
	
	/**
	 * Construct a FrameGrabber object used to capture JPEG frames from a video source. The capture
	 * will use the first channel(0) and the resolution will be set to the maximum supported by the hardware.
	 * Ths standard will be set to PAL. 
	 * @param device the V4L device from which to capture
	 * @param q the JPEG image quality (the higher, the better the quality)
	 * @throws V4L4JException if one of the JPEG quality value is incorrect or the device file is not a readable file
	 */
	public FrameGrabber(String device, int q) throws V4L4JException {
		this(device, MAX_WIDTH, MAX_HEIGHT, 0, WEBCAM, q);
	}
	
	/**
	 * Construct a FrameGrabber object used to capture JPEG frames from a video source. The
	 * resolution will be set to the maximum supported by the hardware. 
	 * @param device the V4L device from which to capture
	 * @param ch the channel
	 * @param std the video standard
	 * @param q the JPEG image quality (the higher, the better the quality)
	 * @throws V4L4JException if one of the JPEG quality value is incorrect or the device file is not a readable file
	 */
	public FrameGrabber(String device, int ch, int std, int q) throws V4L4JException {
		this(device, MAX_WIDTH, MAX_HEIGHT, ch, std, q);
	}
	
	
	/**
	 * Construct a FrameGrabber object used to capture JPEG frames from a webcam video source. The
	 * resolution will be set to that specified by parameters w and h, if supported. Otherwise,
	 * the closest one will be used.	 
	 * @param device the V4L device from which to capture
	 * @param w image width
	 * @param h image height
	 * @throws V4L4JException if the device file is not a readable file
	 */
	public FrameGrabber(String device, int w, int h) throws V4L4JException {
		this(device, w, h, 0, WEBCAM, 80);
	}

	
	/**
	 * Initialise the capture, and apply the capture parameters.
	 * V4L may either adjust the height and width parameters to the closest valid values
	 * or reject them altogether. If the values were adjusted, they can be retrieved 
	 * after calling init() using getWidth() and getHeight()
	 * @throws VideoStandardException if the chosen video standard is not supported
	 * @throws ImageFormatException if the selected video device uses an unsupported image format (let the author know, see README file)
	 * @throws CaptureChannelException if the given channel number value is not valid
	 * @throws ImageDimensionException if the given image dimensions are not supported
	 * @throws InitialistationException if the video device file cant be initialised 
	 * @throws StateException if the framegrabber is already initialised
	 * @throws V4L4JException if there is an error applying capture parameters
	 */
	public void init() throws V4L4JException{
		if(!state.init())
			throw new StateException("Invalid method call");
		
		object = allocateObject();	
		try {
			bufs = init_v4l(object, dev, width, height, channel, standard, nbV4LBuffers, quality);
		} catch (InitialistationException e) {
			freeObject(object);
			throw e;
		} catch (ImageDimensionsException e) {
			freeObject(object);
			throw e;
		} catch (CaptureChannelException e) {
			freeObject(object);
			throw e;
		} catch (ImageFormatException e) {
			freeObject(object);
			throw e;
		} catch (VideoStandardException e) {
			freeObject(object);
			throw e;
		}
		state.commit();
	}
	
	/**
	 * Start the capture. After this call, frames can be retrieved with getFrame()
	 * @throws V4L4JException if the capture cant be started
	 * @throws StateException if <code>init()</code> hasnt been called successfully before.
	 */
	public void startCapture() throws StateException, V4L4JException {
		if(!state.start())
			throw new StateException("Invalid method call");
		
		start(object);
		state.commit();
	}
	
	/**
	 * Retrieve one JPEG-encoded frame from the video source. The ByteBuffer <code>limit()</code> is
	 * set to the size of the JPEG encoded frame. The buffer's <code>position()</code> must be set back
	 * to 0 when finished. Note that the returned ByteBuffer is not backed by an array.
	 * @return a ByteBuffer containing the JPEG-encoded frame data
	 * @throws V4L4JException if there is an error capturing from the source
	 * @throws StateException if the object isnt successfully initialised and started
	 */
	public ByteBuffer getFrame() throws V4L4JException {
		//we need the synchronized statement to serialise calls to getBuffer
		//since libv4l is not reentrant
		synchronized(state) {
			if(!state.isStarted())
				throw new StateException("Invalid method call");
			ByteBuffer b = bufs[getBuffer(object)];
			b.limit(getBufferLength(object)).position(0);
			return b;
		}
	}
	
	/**
	 * Stop the capture.
	 * @throws StateException if the object isnt successfully initialised and started
	 */
	public void stopCapture() throws StateException {
		if(!state.stop())
			throw new StateException("Invalid method call");
		
		stop(object);
		state.commit();
	}
	
	/**
	 * Free resources used by the FrameCapture object.
	 * @throws V4L4JException if there is a problem freeing resources
	 * @throws StateException if the object isnt successfully initialised or the capture isnt stopped
	 */
	public void remove() throws V4L4JException {
		if(state.isStarted())
			stopCapture();
		
		if(!state.remove())
			throw new StateException("Invalid method call");
		
		delete(object);
		freeObject(object);
		state.commit();
	}
	
	/**
	 * Return the actual height of captured frames 
	 * @return the height
	 */
	public int getHeight(){
		return height;
	}
	
	/**
	 * Return the actual width of captured frames
	 * @return the width
	 */
	public int getWidth(){
		return width;
	}
	
	/**
	 * Return the current JPEG quality 
	 * @return the JPEG quality
	 */
	public int getJPGQuality(){
		return quality;
	}
	
	/**
	 * Set the desired JPEG quality
	 * @param q the quality (between 0 and 100 inclusive)
	 * @throws V4L4JException if the quality value is not valid
	 */
	public void setJPGQuality(int q) throws V4L4JException{
		if(q<MIN_JPEG_QUALITY || q>MAX_JPEG_QUALITY)
			throw new V4L4JException("The JPEG quality must be "+MIN_JPEG_QUALITY+"<q<"+MAX_JPEG_QUALITY);
		setQuality(object, q);
		quality = q;
	}
	
	/**
	 * Set the specified control to the specified value
	 * @param id the control index (in the array of controls as returned by getControls() )
	 * @param value the new value
	 * @throws StateException if the object is not initialised
	 * @throws ControlException if the value can not be retrieved
	 */
	void setControlValue(int id, int value) throws StateException, ControlException{
		if(!state.get())
			throw new StateException("Invalid method call");

		setCtrlValue(object, id, value);
		state.put();
	}

	/**
	 * Get the current value of the specified control
	 * @param id the control index (in the array of controls as returned by getControls() )
	 * @return the current value of a control
	 * @throws StateException if the object is not initialised
	 * @throws ControlException if the value can not be retrieved
	 */
	int getControlValue(int id) throws StateException, ControlException{
		if(!state.get())
			throw new StateException("Invalid method call");
		int ret = getCtrlValue(object, id);
		state.put();
		return ret;
	}
	
	/**
	 * Retrieve a list of available controls
	 * @return a list of available controls
	 */
	public List<Control> getControls() {
		return new Vector<Control>(Arrays.asList(ctrls));
	}
	
	private static class State {

		private int state;
		private int temp;
		private int users;
		
		private int UNINIT=0;
		private int INIT=1;
		private int STARTED=2;
		private int STOPPED=3;
		private int REMOVED=4;

		public State() {
			state = UNINIT;
			temp = UNINIT;
			users = 0;
		}
		
		public synchronized boolean init(){
			if(state==UNINIT || state==REMOVED && temp!=INIT) {
				temp=INIT;
				return true;
			}
			return false;
		}
		
		public synchronized boolean start(){
			if(state==INIT || state==STOPPED && temp!=STARTED) {
				temp=STARTED;
				return true;
			}
			return false;
		}
		
		/**
		 * Must be called with object lock held
		 * @return
		 */
		public boolean isStarted(){
			return state==STARTED && temp!=STOPPED;
		}

		public synchronized boolean get(){
			if(state==INIT || state==STARTED && temp!=STOPPED) {
				users++;
				return true;
			} else
				return false;
		}
		
		public synchronized boolean put(){
			if(state==INIT || state==STARTED) {
				if(--users==0  && temp!=STOPPED)
					notify();
				return true;
			} else
				return false;
		}
		
		
		public synchronized boolean stop(){
			if(state==STARTED && temp!=STOPPED) {
				temp=STOPPED;
				while(users!=0)
					try {
						wait();
					} catch (InterruptedException e) {
						System.err.println("Interrupted while waiting for v4l4j users to complete");
						e.printStackTrace();
					}
				return true;
			}
			return false;
		}
		
		public synchronized boolean remove(){
			if(state==INIT || state==STOPPED && temp!=REMOVED) {
				temp=REMOVED;
				return true;
			}
			return false;
		}
		
		public synchronized void commit(){
			state=temp;
		}
	}
}
