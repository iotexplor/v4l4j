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

#ifndef H_COMMON__
#define H_COMMON__

#include <stdio.h>
#include <jpeglib.h>
#include <jni.h>

#include "libvideo.h"
#include "debug.h"

struct v4l4j_device;

struct jpeg_data {
	JSAMPROW *y,*cb,*cr;
	JSAMPARRAY data[3];
	struct jpeg_compress_struct *cinfo;
	struct jpeg_destination_mgr *destmgr;
	struct jpeg_error_mgr *jerr;
	int lines_written_per_loop;
	int jpeg_quality;			//the jpeg quality, set to -1 if disable
};

struct rgb_data {
	struct jpeg_decompress_struct *cinfo;
	struct jpeg_source_mgr *srcmgr;
	struct jpeg_error_mgr *jerr;
	int nb_pixel;
};

enum output_format {
	OUTPUT_RAW=0,
	OUTPUT_JPG,
	OUTPUT_RGB24,
	OUTPUT_BGR24,
	OUTPUT_YUV420,
	OUTPUT_YVU420
};


struct v4l4j_device {
	/**
	 * Method for v4l4j conversion
	 */
	size_t (*convert) (struct v4l4j_device *device, unsigned char *src, unsigned char *dst);
	unsigned char *conversion_buffer;
	/**
	 * Conversion buffer used when two conversions are required
	 */
	unsigned char *double_conversion_buffer;
	struct video_device *vdev;	//the libvideo struct
	union {
		struct jpeg_data *j;	//the converter's data
		struct rgb_data *r;
	};
	/**
	 * The output format (see enum above)
	 */
	enum output_format output_fmt;
	/**
	 * The size of the last captured frame by libvideo
	 */
	size_t capture_len;
	/**
	 * This flag is set by Java_au_edu_jcu_v4l4j_FrameGrabber_doInit, and says
	 * whether  v4l4j (1) or libvideo (0) does the output format conversion.
	 * 0 means no conversion needed at all.
	 * In practice, the only time that this will be set to 1 will be if the
	 * output format is JPEG, which is handled in jpeg.c
	 */
	bool need_conv;
};

#ifndef ARRAY_SIZE
#define ARRAY_SIZE(x) (((x)==NULL) ? 0 : (sizeof(x)/sizeof((x)[0])))
#endif

#define JPEG_CONVERTIBLE_FORMATS \
	{JPEG, MJPEG, YUV420, YUYV, YVYU, UYVY, RGB24, BGR24, RGB32, BGR32}




#define BYTEBUFER_CLASS			"java/nio/ByteBuffer"
#define V4L4J_PACKAGE			"au/edu/jcu/v4l4j/"
#define CONTROL_CLASS			V4L4J_PACKAGE "Control"
#define CONSTANTS_CLASS			V4L4J_PACKAGE "V4L4JConstants"
#define EXCEPTION_PACKAGE		V4L4J_PACKAGE "exceptions/"
#define GENERIC_EXCP			EXCEPTION_PACKAGE "V4L4JException"
#define INIT_EXCP				EXCEPTION_PACKAGE "InitialisationException"
#define DIM_EXCP				EXCEPTION_PACKAGE "ImageDimensionException"
#define CHANNEL_EXCP			EXCEPTION_PACKAGE "CaptureChannelException"
#define FORMAT_EXCP				EXCEPTION_PACKAGE "ImageFormatException"
#define STD_EXCP				EXCEPTION_PACKAGE "VideoStandardException"
#define CTRL_EXCP				EXCEPTION_PACKAGE "ControlException"
#define RELEASE_EXCP			EXCEPTION_PACKAGE "ReleaseException"
#define INVALID_VAL_EXCP		EXCEPTION_PACKAGE "InvalidValueException"
#define UNSUPPORTED_METH_EXCP	EXCEPTION_PACKAGE "UnsupportedMethod"
#define JNI_EXCP				EXCEPTION_PACKAGE "JNIException"
#define OVERFLOW_EXCP			EXCEPTION_PACKAGE "BufferOverflowException"
#define UNDERFLOW_EXCP			EXCEPTION_PACKAGE "BufferUnderflowException"
#define NULL_EXCP				"java/lang/NullPointerException"
#define ARG_EXCP				"java/lang/IllegalArgumentException"


/* Exception throwing helper */
#define EXCEPTION_MSG_LENGTH	100
#define THROW_EXCEPTION(e, c, format, ...)\
		do {\
			info("[V4L4J] " format "\n", ## __VA_ARGS__);\
			char msg[EXCEPTION_MSG_LENGTH+1];\
			jclass JV4L4JException = (*e)->FindClass(e, c);\
			int msglen = snprintf(msg, EXCEPTION_MSG_LENGTH, format, ## __VA_ARGS__);\
			if (JV4L4JException) {\
				jmethodID ctor;\
				if ((*e)->ExceptionCheck(e) && (ctor = (*e)->GetMethodID(e, JV4L4JException, "<init>", "(Ljava/lang/String;Ljava/lang/Throwable)V"))) {\
					jstring msgStr = (*e)->NewString(e, (const jchar*)msg, msglen > EXCEPTION_MSG_LENGTH ? EXCEPTION_MSG_LENGTH : msglen);\
					jthrowable exception = (*e)->NewObject(e, JV4L4JException, ctor, msgStr, (*e)->ExceptionOccurred(e));\
					(*e)->Throw(e, exception);\
				} else {\
					(*e)->ThrowNew(e, JV4L4JException, msg);\
				}\
			}\
		} while(0)

#define CLIP(x) (unsigned char) ((x) > 255) ? 255 : (((x) < 0) ? 0 : (x));

#endif /*H_COMMON_*/
