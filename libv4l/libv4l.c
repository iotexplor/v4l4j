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
#include <fcntl.h>		//for open
#include <string.h>		//for strcpy
#include <sys/ioctl.h>	//for ioctl
#include <unistd.h>		//for write, close



#include "fps-param-probe.h"
#include "gspca-probe.h"
#include "libv4l.h"
#include "libv4l-err.h"
#include "log.h"
#include "pwc-probe.h"
#include "qc-probe.h"
#include "v4l1-input.h"
#include "v4l1-query.h"
#include "v4l2-input.h"
#include "v4l2-query.h"
#include "videodev_additions.h"


char *get_libv4l_version(char * c) {
	sprintf(c, "%d.%d-%d", VER_MAJ, VER_MIN, VER_REL);
	return c;
}

/*
 *
 * VIDEO DEVICE INTERFACE
 *
 */
struct video_device *open_device(char *file) {
	struct video_device *vdev;
	int fd = -1;
	char version[10];

	dprint(LIBV4L_LOG_SOURCE_V4L, LIBV4L_LOG_LEVEL_INFO, "Using libv4l version %s\n", get_libv4l_version(version));

	//open device
	dprint(LIBV4L_LOG_SOURCE_V4L, LIBV4L_LOG_LEVEL_DEBUG2, "V4L: Opening device file %s.\n", file);
	if ((strlen(file) == 0) || ((fd = open(file,O_RDWR )) < 0)) {
		info("V4L: unable to open device file %s. Check the name and permissions\n", file);
		fd = -1;
	}

	XMALLOC(vdev, struct video_device *, sizeof(struct video_device));

	//Check v4l version (V4L2 first)
	dprint(LIBV4L_LOG_SOURCE_V4L, LIBV4L_LOG_LEVEL_DEBUG2, "V4L: Checking V4L version on device %s\n", file);
	if(check_capture_capabilities_v4l2(fd, file)==0) {
		dprint(LIBV4L_LOG_SOURCE_V4L, LIBV4L_LOG_LEVEL_DEBUG2, "V4L: device %s is V4L2\n", file);
		vdev->v4l_version=V4L2_VERSION;
	} else if(check_capture_capabilities_v4l1(fd, file)==0){
		dprint(LIBV4L_LOG_SOURCE_V4L, LIBV4L_LOG_LEVEL_DEBUG2, "V4L: device %s is V4L1\n", file);
		vdev->v4l_version=V4L1_VERSION;
	} else {
		info("libv4l was unable to detect the version of V4L used by device %s\n", file);
		info("Please let the author know about this error.\n");
		info("See the ISSUES section in the libv4l README file.\n");

		close_device(vdev);
		return NULL;
	}

	vdev->fd = fd;
	strncpy(vdev->file, file, FILENAME_LENGTH -1);

	return vdev;
}

int close_device(struct video_device *vdev) {
	//Close device file
	dprint(LIBV4L_LOG_SOURCE_V4L, LIBV4L_LOG_LEVEL_DEBUG2, "V4L: closing device file %s.\n", vdev->file);

	//TODO: try and release info, capture and controls instead of failing
	//check that we have released the info, capture and controls stuff
	if(vdev->info) {
		dprint(LIBV4L_LOG_SOURCE_V4L, LIBV4L_LOG_LEVEL_ERR, "V4L: Cant close device file %s - device info data not released\n", vdev->file);
		return LIBV4L_ERR_INFO_IN_USE;
	}
	if(vdev->capture) {
		dprint(LIBV4L_LOG_SOURCE_V4L, LIBV4L_LOG_LEVEL_ERR, "V4L: Cant close device file %s - capture interface not released\n", vdev->file);
		return LIBV4L_ERR_CAPTURE_IN_USE;
	}
	if(vdev->controls) {
		dprint(LIBV4L_LOG_SOURCE_V4L, LIBV4L_LOG_LEVEL_ERR, "V4L: Cant close device file %s - control interface not released\n", vdev->file);
		return LIBV4L_ERR_CONTROL_IN_USE;
	}

	close(vdev->fd);
	XFREE(vdev);
	return 0;
}

/*
 *
 * CAPTURE INTERFACE
 *
 */

static void setup_capture_actions(struct video_device *vdev) {
	struct capture_device *c = vdev->capture;
	XMALLOC(c->actions, struct capture_actions *, sizeof(struct capture_actions) );
	if(vdev->v4l_version == V4L1_VERSION) {
		c->actions->set_cap_param = set_cap_param_v4l1;
		c->actions->init_capture = init_capture_v4l1;
		c->actions->start_capture = start_capture_v4l1;
		c->actions->dequeue_buffer = dequeue_buffer_v4l1;
		c->actions->enqueue_buffer = enqueue_buffer_v4l1;
		c->actions->stop_capture = stop_capture_v4l1;
		c->actions->free_capture = free_capture_v4l1;
		c->actions->list_cap = list_cap_v4l1;

	} else {
		c->actions->set_cap_param = set_cap_param_v4l2;
		c->actions->init_capture = init_capture_v4l2;
		c->actions->start_capture = start_capture_v4l2;
		c->actions->dequeue_buffer = dequeue_buffer_v4l2;
		c->actions->enqueue_buffer = enqueue_buffer_v4l2;
		c->actions->stop_capture = stop_capture_v4l2;
		c->actions->free_capture = free_capture_v4l2;
		c->actions->list_cap = list_cap_v4l2;
	}
}


//device file, width, height, channel, std, nb_buf
struct capture_device *init_capture_device(struct video_device *vdev, int w, int h, int ch, int s, int nb_buf){
	//create capture device
	dprint(LIBV4L_LOG_SOURCE_V4L, LIBV4L_LOG_LEVEL_DEBUG2, "V4L: Initialising capture interface\n");
	XMALLOC(vdev->capture, struct capture_device *,sizeof(struct capture_device));
	XMALLOC(vdev->capture->mmap, struct mmap *, sizeof(struct mmap));

	//fill in cdev struct
	vdev->capture->mmap->req_buffer_nr = nb_buf;
	vdev->capture->width = w;
	vdev->capture->height = h;
	vdev->capture->channel = ch;
	vdev->capture->std = s;

	setup_capture_actions(vdev);

	return vdev->capture;
}

//counterpart of init_capture_device, must be called it init_capture_device was successful
void free_capture_device(struct video_device *vdev){
	dprint(LIBV4L_LOG_SOURCE_V4L, LIBV4L_LOG_LEVEL_DEBUG2, "V4L: Freeing libv4l on device %s.\n", vdev->file);
	XFREE(vdev->capture->actions);
	XFREE(vdev->capture->mmap);
	XFREE(vdev->capture);
}

/*
 *
 * QUERY INTERFACE
 *
 */
struct device_info *get_device_info(struct video_device *vdev){
	dprint(LIBV4L_LOG_SOURCE_V4L, LIBV4L_LOG_LEVEL_DEBUG2, "V4L: Querying device %s.\n", vdev->file);

	XMALLOC(vdev->info, struct device_info *, sizeof(struct device_info));

	if(vdev->v4l_version == V4L2_VERSION) {
		//v4l2 device
		query_device_v4l2(vdev);
	} else if (vdev->v4l_version == V4L1_VERSION) {
		//v4l1 device
		query_device_v4l1(vdev);
	} else {
		info("libv4l was unable to detect the version of V4L used by device %s\n", vdev->file);
		info("Please let the author know about this error.\n");
		info("See the ISSUES section in the libv4l README file.\n");
		XFREE(vdev->info);
	}

	return vdev->info;
}

void release_device_info(struct video_device *vdev){
	if(vdev->v4l_version == V4L2_VERSION) {
		//v4l2 device
		free_video_device_v4l2(vdev);
	} else if (vdev->v4l_version == V4L1_VERSION) {
		//v4l1 device
		free_video_device_v4l1(vdev);
	} else {
		info("libv4l was unable to detect the version of V4L used by device %s\n", vdev->file);
		info("Please let the author know about this error.\n");
		info("See the ISSUES section in the libv4l README file.\n");
		return;
	}

	XFREE(vdev->info);
}


/*
 *
 * CONTROL INTERFACE
 *
 */
static struct v4l_driver_probe known_driver_probes[] = {
	{
		.probe 		= pwc_driver_probe,
		.list_ctrl 	= pwc_list_ctrl,
		.get_ctrl	= pwc_get_ctrl,
		.set_ctrl	= pwc_set_ctrl,
		.priv = NULL,
	},
	{
		.probe 		= gspca_driver_probe,
		.list_ctrl 	= gspca_list_ctrl,
		.get_ctrl	= gspca_get_ctrl,
		.set_ctrl	= gspca_set_ctrl,
		.priv = NULL,
	},
	{
		.probe 		= qc_driver_probe,
		.list_ctrl 	= qc_list_ctrl,
		.get_ctrl	= qc_get_ctrl,
		.set_ctrl	= qc_set_ctrl,
		.priv = NULL,
	},
	{
		.probe 		= fps_param_probe,
		.list_ctrl 	= fps_param_list_ctrl,
		.get_ctrl	= fps_param_get_ctrl,
		.set_ctrl	= fps_param_set_ctrl,
		.priv = NULL,
	},
};

#define PROBE_NB 4


static void add_node(driver_probe **list, struct v4l_driver_probe *probe) {
	driver_probe *t;
	if((t=*list)) {
		//create the subsequent nodes
		while(t->next) t = t->next;
		XMALLOC(t->next, driver_probe *, sizeof(driver_probe));
		t->next->probe = probe;
	} else {
		//create the first node
		XMALLOC((*list), driver_probe *, sizeof(driver_probe));
		(*list)->probe = probe;
	}
 }
static void empty_list(driver_probe *list){
	driver_probe *t;
 	while(list) {
		t = list->next;
		XFREE(list);
		list = t;
 	}
 }

// ****************************************
// Control methods
// ****************************************
struct control_list *get_control_list(struct video_device *vdev){
	struct v4l2_control ctrl;
	int probe_id = 0, count = 0, priv_ctrl_count = 0, nb=0;
	driver_probe *e = NULL;
	struct control_list *l;

	dprint(LIBV4L_LOG_SOURCE_CONTROL, LIBV4L_LOG_LEVEL_DEBUG, "CTRL: Listing controls\n");

	XMALLOC(vdev->controls, struct control_list *, sizeof(struct control_list));
	l = vdev->controls;

	CLEAR(ctrl);

	//dry run to see how many control we have
	if(vdev->v4l_version==V4L2_VERSION)
		count = count_v4l2_controls(vdev);
	else if(vdev->v4l_version==V4L1_VERSION)
		//4 basic controls in V4L1
		count = count_v4l1_controls(vdev);
	else {
		dprint(LIBV4L_LOG_SOURCE_CONTROL, LIBV4L_LOG_LEVEL_ERR, "CTRL: Weird V4L version (%d)...\n", vdev->v4l_version);
		l->count=0;
		return l;
	}


	/*
	 *  The following is an attempt to support driver private (custom) ioctls.
	 * First libv4l will probe and detect the underlying video driver. Then, it will create fake V4L controls for every private ioctls so
	 * that the application can call these private ioctls through normal V4L controls.In struct v4l2_query, libv4l will use the reserved[0]
	 * field  and set it to a special unused value V4L2_PRIV_IOCTL (currently in kernel 2.6.25, only values from 1 to 6 are used by v4l2).
	 * The following code attempts to probe the underlying driver (pwc, bttv, gspca, ...) and create fake v4l2_ctrl based on supported
	 * ioctl (static list which must be updated manually after inspecting the code for each driver => ugly but there is no other option until all
	 * drivers make their private ioctl available through a control (or control class like the camera control class added to 2.6.25))
	 */
	//go through all probes
	while ( probe_id<PROBE_NB ){
		if ( (nb = known_driver_probes[probe_id].probe(vdev, &known_driver_probes[probe_id].priv)) != -1) {
			//if the probe is successful, add the nb of private controls detected to the grand total
			priv_ctrl_count += nb;
			add_node(&l->probes,&known_driver_probes[probe_id]);
		}
		probe_id++;
	}


	count += priv_ctrl_count;

	l->count = count;
	if(count>0) {
		XMALLOC( l->ctrl , struct v4l2_queryctrl *, (l->count * sizeof(struct v4l2_queryctrl)) );

		dprint(LIBV4L_LOG_SOURCE_CONTROL, LIBV4L_LOG_LEVEL_DEBUG, "CTRL: listing controls (found %d)...\n", count);

		//fill in controls
		if(vdev->v4l_version==V4L2_VERSION)
			count = create_v4l2_controls(vdev, l);
		else if(vdev->v4l_version==V4L1_VERSION)
			count = create_v4l1_controls(vdev, l);

		dprint(LIBV4L_LOG_SOURCE_CONTROL, LIBV4L_LOG_LEVEL_DEBUG, "CTRL: listing private controls (found %d)...\n", priv_ctrl_count);
		//probe the driver for private ioctl and turn them into fake V4L2 controls
		//if(priv_ctrl_count>0)

		for(e = l->probes;e;e=e->next)
		 		e->probe->list_ctrl(vdev, &l->ctrl[count], e->probe->priv);
		dprint(LIBV4L_LOG_SOURCE_CONTROL, LIBV4L_LOG_LEVEL_DEBUG, "CTRL: done listing controls\n");

	} else {
		dprint(LIBV4L_LOG_SOURCE_CONTROL, LIBV4L_LOG_LEVEL_DEBUG, "CTRL: No controls found...\n");
	}

	return l;
}

int get_control_value(struct video_device *vdev, struct v4l2_queryctrl *ctrl, int *val){
	int ret = 0;
	dprint(LIBV4L_LOG_SOURCE_CONTROL, LIBV4L_LOG_LEVEL_DEBUG, "CTRL: getting value for control %s\n", ctrl->name);
	if(ctrl->reserved[0]==V4L2_PRIV_IOCTL){
		struct v4l_driver_probe *s = &known_driver_probes[ctrl->reserved[1]];
		ret = s->get_ctrl(vdev, ctrl, s->priv, val);
	} else {
		if(vdev->v4l_version==V4L2_VERSION)
			ret = get_control_value_v4l2(vdev, ctrl, val);
		else if(vdev->v4l_version==V4L1_VERSION)
			ret =  get_control_value_v4l1(vdev, ctrl, val);
		else {
			dprint(LIBV4L_LOG_SOURCE_CONTROL, LIBV4L_LOG_LEVEL_ERR, "CTRL: Weird V4L version (%d)...\n", vdev->v4l_version);
			ret =  LIBV4L_ERR_WRONG_VERSION;
		}
	}
	return ret;
}

int set_control_value(struct video_device *vdev, struct v4l2_queryctrl *ctrl, int i){
	int ret = 0;
	dprint(LIBV4L_LOG_SOURCE_CONTROL, LIBV4L_LOG_LEVEL_DEBUG, "CTRL: setting value (%d) for control %s\n",i, ctrl->name);
	if(i<ctrl->minimum || i > ctrl->maximum){
		dprint(LIBV4L_LOG_SOURCE_CONTROL, LIBV4L_LOG_LEVEL_ERR, "CTRL: control value out of range\n");
		return LIBV4L_ERR_OUT_OF_RANGE;
	}

	if(ctrl->reserved[0]==V4L2_PRIV_IOCTL){
		struct v4l_driver_probe *s = &known_driver_probes[ctrl->reserved[1]];
		ret = s->set_ctrl(vdev, ctrl, i,s->priv);
	} else {
		if(vdev->v4l_version==V4L2_VERSION)
			ret = set_control_value_v4l2(vdev, ctrl, i);
		else if(vdev->v4l_version==V4L1_VERSION)
			ret = set_control_value_v4l1(vdev, ctrl, i);
		else {
			dprint(LIBV4L_LOG_SOURCE_CONTROL, LIBV4L_LOG_LEVEL_ERR, "CTRL: Weird V4L version (%d)...\n", vdev->v4l_version);
			ret = LIBV4L_ERR_WRONG_VERSION;
		}

	}
	return ret;
}

void release_control_list(struct video_device *vdev){
	dprint(LIBV4L_LOG_SOURCE_CONTROL, LIBV4L_LOG_LEVEL_DEBUG, "CTRL: Freeing controls \n");
	driver_probe *e;
	if (vdev->controls->ctrl)
		XFREE(vdev->controls->ctrl);

	for(e = vdev->controls->probes; e; e = e->next)
		if (e->probe->priv)
			XFREE(e->probe->priv);

	empty_list(vdev->controls->probes);

	if (vdev->controls)
		XFREE(vdev->controls);
}
