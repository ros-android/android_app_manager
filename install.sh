#!/bin/bash
cd `rospack find app_chooser`
ant install
cd `rospack find pan_tilt`
ant install
cd `rospack find make_a_map`
ant install
cd `rospack find map_nav`
ant install
cd `rospack find android_teleop`
ant install
cd `rospack find android_pr2_props`
ant install
cd `rospack find voice_recognition`
ant install


