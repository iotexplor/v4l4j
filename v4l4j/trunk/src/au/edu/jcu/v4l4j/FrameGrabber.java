
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
import java.io.IOException;
import java.nio.ByteBuffer;


/**
 * This class provides methods to :
 * <ul>
 * <li>Capture JPEG-encoded framesfrom a Video4Linux source and,</li>
 * <li>Control the video source.</li>
 * </ul>
 * Create an instance of it attached to a V4L device to grab JPEG-encoded frames from it. A typical use is as follows:
 * <ul>
 * <li>Create an instance of FrameGrabber: <code>FrameGrabber f = new FrameGrabber("/dev/video0", 320, 240, 0, 0, 80);</code></li>
 * <li>Initialise the framegrabber: <code>f.init();</code></li>
 * <li>Start the frame capture: <code>f.startCapture();</code></li>
 * <li><code>while (!stop) </code></li> 
 *  <ul><li>Retrieve a frame: <code>ByteBuffer b= f.getFrame();</code></li>
 *  <li>do something useful with b</li>
 *  <li>when done, set buffer position to 0: <code>b.position(0);</code></li>
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
	
	private String dev;
	private int width;
	private int height;
	private int channel;
	private int quality;
	private int standard;
	private int nbV4LBuffers = 4;
	private ByteBuffer[] bufs;
	private State state;
	private V4L2Control[] ctrls;
	
	/*
	 * JNI returns an long (which is really a pointer) when a device is allocated for use
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
	
	private native long allocateObject() throws V4L4JException;
	private native ByteBuffer[] init_v4l(long o, String f, int w, int h, int ch, int std, int nbBuf, int q);
	private native void start(long o) throws V4L4JException;
	private native void setQuality(long o, int i) throws V4L4JException;
	private native int getBuffer(long o) throws V4L4JException;
	private native int getBufferLength(long o) throws V4L4JException;
	private native void stop(long o) throws V4L4JException;
	private native void delete(long o) throws V4L4JException;
	private native int getCtrlValue(long o, int i) throws V4L4JException;
	private native int setCtrlValue(long o, int i, int v) throws V4L4JException;
	
	/**
	 * Construct a FrameGrabber object used to capture JPEG frames from a video source
	 * @param device the V4L device from which to capture
	 * @param w the requested frame width 
	 * @param h the requested frame height
	 * @param ch the channel
	 * @param std the video standard
	 * @param q the JPEG image quality (the higher, the better the quality)
	 * @throws V4L4JException if one of the parameters is incorrect
	 */
	public FrameGrabber(String device, int w, int h, int ch, int std, int q) throws V4L4JException {
		if(!(new File(device).canRead()))
			throw new V4L4JException("The device file is not readable");
		
		if(q<0 || q>100)
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
	 * will use the first channel(0) and set the resolution to the maximum supported by the hardware.
	 * Ths standard will be set to PAL. 
	 * @param device the V4L device from which to capture
	 * @param q the JPEG image quality (the higher, the better the quality)
	 * @throws V4L4JException if one of the parameters is incorrect
	 */
	public FrameGrabber(String device, int q) throws V4L4JException {
		this(device, MAX_WIDTH, MAX_HEIGHT, 0, WEBCAM, q);
	}
	
	/**
	 * Construct a FrameGrabber object used to capture JPEG frames from a video source. The capture
	 * will set the resolution to the maximum supported by the hardware. 
	 * @param device the V4L device from which to capture
	 * @param ch the channel
	 * @param std the video standard
	 * @param q the JPEG image quality (the higher, the better the quality)
	 * @throws V4L4JException if one of the parameters is incorrect
	 */
	public FrameGrabber(String device, int ch, int std, int q) throws V4L4JException {
		this(device, MAX_WIDTH, MAX_HEIGHT, ch, std, q);
	}
	
	/**
	 * Initialise the capture, and apply the capture parameters.
	 * V4L may either adjust the height and width parameters to the closest valid values
	 * or reject them altogether. If the values were adjusted, they can be retrieved 
	 * after calling init() using getWidth() and getHeight()
	 * @throws V4L4JException if one of the parameters is invalid
	 */
	public void init() throws V4L4JException {
		if(!state.init())
			throw new V4L4JException("Invalid method call");
		
		object = allocateObject();		
		bufs = init_v4l(object, dev, width, height, channel, standard, nbV4LBuffers, quality);
		state.commit();
	}
	
	/**
	 * Start the capture. After this call, frames can be retrieved with getFrame()
	 * @throws V4L4JException if one of the parameters is invalid
	 */
	public void startCapture() throws V4L4JException {
		if(!state.start())
			throw new V4L4JException("Invalid method call");
		
		start(object);
		state.commit();
	}
	
	/**
	 * Retrieve one JPEG-encoded frame from the video source. The ByteBuffer <code>limit()</code> is
	 * set to the size of the JPEG encoded frame. The buffer's <code>position()</code> must be set back
	 * to 0 when finished. Note that the returned ByteBuffer is not backed by an array.
	 * @return a ByteBuffer containing the JPEG-encoded frame data
	 * @throws V4L4JException if there is an error capturing from the source
	 */
	public ByteBuffer getFrame() throws V4L4JException {
		synchronized(state) {
			if(!state.isStarted())
				throw new V4L4JException("Invalid method call");
			ByteBuffer b = bufs[getBuffer(object)];
			b.limit(getBufferLength(object)).position(0);
			return b;
		}
	}
	
	/**
	 * Stop the capture.
	 * @throws V4L4JException if the method call is not valid (if the capture was never started for instance)
	 */
	public void stopCapture() throws V4L4JException {
		if(!state.stop())
			throw new V4L4JException("Invalid method call");
		
		stop(object);
		state.commit();
	}
	
	/**
	 * Free resources used by the FrameCapture object.
	 * @throws V4L4JException if there is a problem freeing resources
	 */
	public void remove() throws V4L4JException {
		if(state.isStarted())
			stopCapture();
		
		if(!state.remove())
			throw new V4L4JException("Invalid method call");
		
		delete(object);
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
		if(q<0 || q>100)
			throw new V4L4JException("The JPEG quality must be 0<q<100");
		setQuality(object, q);
		quality = q;
	}
	
	/**
	 * Set the specified control to the specified value
	 * @param id the control index (in the array of controls as returned by getControls() )
	 * @param value the new value
	 * @throws V4L4JException if something goes wrong
	 */
	void setControlValue(int id, int value) throws V4L4JException{
		//synchronized(state) {
			if(!state.isInit() && !state.isStarted())
				throw new V4L4JException("Invalid method call");
			
			setCtrlValue(object, id, value);
		//}
	}

	/**
	 * Get the current value of the specified control
	 * @param id the control index (in the array of controls as returned by getControls() )
	 * @return the current value of a control
	 * @throws V4L4JException if something goes wrong
	 */
	int getControlValue(int id) throws V4L4JException{
		//synchronized(state) {
			if(!state.isInit() && !state.isStarted())
				throw new V4L4JException("Invalid method call");
			
			return getCtrlValue(object, id);
		//}
	}
	
	/**
	 * Retrieve an array of available controls
	 * @return the array of available controls
	 */
	public V4L2Control[] getControls() {
		return ctrls;
	}
	
	private class State {

		private int state;
		private int temp;
		
		private int UNINIT=0;
		private int INIT=1;
		private int STARTED=2;
		private int STOPPED=3;
		private int REMOVED=4;

		public State() {
			state=UNINIT;
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
		
		public boolean isStarted(){
			return state==STARTED;
		}
		
		public boolean isInit(){
			return state==INIT || state==STOPPED;
		}
		
		public synchronized boolean stop(){
			if(state==STARTED && temp!=STOPPED) {
				temp=STOPPED;
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
	
	public static void main(String[] args) throws V4L4JException, IOException {
		V4L2Control[] ctrls;
		String dev;
		int w, h, std, channel, qty, captureLength = 5;
		//Check if we have the required args
		try {
			dev = args[0];
			w = Integer.parseInt(args[1]);
			h = Integer.parseInt(args[2]);
			std = Integer.parseInt(args[3]);
			channel = Integer.parseInt(args[4]);
			qty = Integer.parseInt(args[5]);
		} catch (Exception e){
			//otherwise put sensible values in
			dev = "/dev/video0";
			w = MAX_WIDTH;
			h = MAX_HEIGHT;
			std = 0;
			channel = 0;
			qty = 80;
		}
		

		long start=0, now=0;
		int n=0;
		ByteBuffer b;
		FrameGrabber f = null;

		System.out.println("This program will open "+dev+", list the available control, capture frames for "
					+ captureLength+ " seconds and print the FPS");
		System.out.println("Make sure the webcam is connected and press <Enter>, or Ctrl-C to abort now.");
		System.in.read();

		try {
			f= new FrameGrabber(dev, w, h, channel, std, qty);
		} catch (V4L4JException e) {
			e.printStackTrace();
			System.out.println("Failed to instanciate the FrameGrabber ("+dev+")");
			throw e;
		}

		try {
			f.init();
		} catch (V4L4JException e) {
			e.printStackTrace();
			System.out.println("Failed to initialise the device "+dev+"");
			throw e;
		}
		ctrls = f.getControls();
		System.out.println("Found "+ctrls.length+" controls");
		try {
			for (int i = 0; i < ctrls.length; i++)
				System.out.println("control "+i+" - name: "+ctrls[i].getName()+" - min: "+ctrls[i].getMin()+" - max: "+ctrls[i].getMax()+" - step: "+ctrls[i].getStep()+" - value: "+ctrls[i].getValue());
		} catch (V4L4JException e) {
			e.printStackTrace();
			System.out.println("Failed to list associated controls");
			throw e;
		}

		try {
			f.startCapture();
		} catch (V4L4JException e) {
			e.printStackTrace();
			System.out.println("Failed to start capture");
			throw e;
		}

		try {
			System.out.println("Starting test capture at "+f.getWidth()+"x"+f.getHeight()+" for "+captureLength+" seconds");
			now=start=System.currentTimeMillis();
			while(now<start+(captureLength*1000)){
				b = f.getFrame();
				//System.out.println("Frame size:"+b.limit());
				
				//Uncomment the following to dump the captured frame to a jpeg file
				//also import java.io.FileOutputStream 
				//new FileOutputStream("file"+n+".jpg").getChannel().write(b);
				n++;
				b.position(0);
				now=System.currentTimeMillis();
			}
		} catch (V4L4JException e) {
			e.printStackTrace();
			System.out.println("Failed to perform test capture");
			throw e;
		}

		System.out.println(" =====  TEST RESULTS  =====");
		System.out.println("\tFrames captured :"+n);
		System.out.println("\tFPS: "+((float) n/(now/1000-start/1000)));
		System.out.println(" =====  END  RESULTS  =====");
		try {
			f.stopCapture();
			f.remove();
		} catch (V4L4JException e) {
			e.printStackTrace();
			System.out.println("Failed to stop capture");
			throw e;
		}
	}
}

