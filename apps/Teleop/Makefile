# Wrapper Makefile to call "ant" in the correct way.

ROSJAVA_ROOT = $(shell rospack find rosjava)
LIBRARY = "../../library"

all: compile

$(ROSJAVA_ROOT)/java/dist/rosjava.jar:
	cd $(ROSJAVA_ROOT)/java && ant dist

clean:
	ant clean
	cd $(LIBRARY) && ant clean

toplevel:
	cd ../.. && ant

# Install the app on a connected android device.
install: clean $(ROSJAVA_ROOT)/java/dist/rosjava.jar toplevel
	ant -f build-wrapper.xml install

# Build an .apk file of the app.
compile: clean $(ROSJAVA_ROOT)/java/dist/rosjava.jar toplevel
	ant -f build-wrapper.xml compile
