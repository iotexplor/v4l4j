package au.edu.jcu.v4l4j.api.stream;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

public interface ContentStream extends AutoCloseable {
	
	Object getProperty(String property);
	Object setProperty(String property, Object value);
	
	ContentStream checkAvailableBytesToRead(int bytesRequested, Consumer<ContentStream> callback);
	ContentStream checkAvailableBytesToWrite(int bytesRequested, Consumer<ContentStream> callback);
	
	int getCurrentPosition();
	ContentStream setPosition(ContentStreamOrigin origin, int offset);
	
	ByteBuffer getReadBuffer(int size, boolean forbidCopy);
	void releaseReadBuffer(ByteBuffer buffer);
	InputStream asInputStream();
	
	ByteBuffer getWriteBuffer(int size, boolean forbidCopy);
	void releaseWriteBuffer(ByteBuffer buffer);
	OutputStream asOutputStream();
	
	void registerCallback(ContentStreamCallback callback);
	
	ContentStreamAccessType getAccess();
}
