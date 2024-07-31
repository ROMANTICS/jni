include Makefile.common

RESOURCE_DIR = src/main/resources

.phony: all package native native-all deploy

all: jni-header

deploy:
	mvn package deploy -DperformRelease=true

DOCKER_RUN_OPTS=--rm
MVN:=mvn
CODESIGN:=docker run $(DOCKER_RUN_OPTS) -v $$PWD:/workdir gotson/rcodesign sign
SRC:=src/main/java
LIBRARY_OUT:=$(TARGET)/$(LIBRARY)-$(OS_NAME)-$(OS_ARCH)


CCFLAGS:= -I$(LIBRARY_OUT) -I$(LIBRARY_INCLUDE) $(CCFLAGS)

$(JAVA_HEADER_FILE):$(JAVA_FILE)

jni-header:$(JAVA_HEADER_FILE)
	$(info $(JAVA_HEADER_FILE))


test:
	mvn test

clean: clean-native clean-java clean-tests

$(LIBRARY_OBJ): ./native/obj/%.o : ./native/src/%.c $(JAVA_HEADER_FILE)
	$(CC) $(CCFLAGS)  -I $(LIBRARY_INCLUDE) -I $(TARGET)/headers -c -o $@ $<


$(LIBRARY_OUT)/$(LIBNAME):  $(LIBRARY_OBJ)  $(JAVA_HEADER_FILE)
	$(info lb:$(LIBRARY_OBJ))
	@mkdir -p $$PWD/$(@D)
	$(CC) $(CCFLAGS) -o $@  $(LIBRARY_OBJ) $(LINKFLAGS)


NATIVE_DIR=src/main/resources/org/romantics/jni/native/$(OS_NAME)/$(OS_ARCH)
NATIVE_TARGET_DIR:=$(TARGET)/classes/org/romantics/jni/native/$(OS_NAME)/$(OS_ARCH)
NATIVE_DLL:=$(NATIVE_DIR)/$(LIBNAME)

# For cross-compilation, install docker. See also https://github.com/dockcross/dockcross
native-all: native win32 win64 win-armv7 win-arm64 mac64-signed mac-arm64-signed linux32 linux64 freebsd32 freebsd64 freebsd-arm64 linux-arm linux-armv6 linux-armv7 linux-arm64 linux-android-arm linux-android-arm64 linux-android-x86 linux-android-x64 linux-ppc64 linux-musl32 linux-musl64 linux-musl-arm64

native: $(NATIVE_DLL)

$(NATIVE_DLL): $(LIBRARY_OUT)/$(LIBNAME)
	cp $$PWD/$< $$PWD/$@
	@mkdir -p $$PWD/$(NATIVE_TARGET_DIR)
	cp $$PWD/$< $$PWD/$(NATIVE_TARGET_DIR)/$(LIBNAME)

win32:  jni-header
	./docker/dockcross-windows-x86 -a $(DOCKER_RUN_OPTS) bash -c 'make clean-native native CROSS_PREFIX=i686-w64-mingw32.static- OS_NAME=Windows OS_ARCH=x86'

win64:  jni-header
	./docker/dockcross-windows-x64 -a $(DOCKER_RUN_OPTS) bash -c 'make clean-native native CROSS_PREFIX=x86_64-w64-mingw32.static- OS_NAME=Windows OS_ARCH=x86_64'

win-armv7:  jni-header
	./docker/dockcross-windows-armv7 -a $(DOCKER_RUN_OPTS) bash -c 'make clean-native native CROSS_PREFIX=armv7-w64-mingw32- OS_NAME=Windows OS_ARCH=armv7'

win-arm64:  jni-header
	./docker/dockcross-windows-arm64 -a $(DOCKER_RUN_OPTS) bash -c 'make clean-native native CROSS_PREFIX=aarch64-w64-mingw32- OS_NAME=Windows OS_ARCH=aarch64'

linux32:  jni-header
	docker run $(DOCKER_RUN_OPTS) -v $$PWD:/work xerial/centos5-linux-x86 bash -c 'make clean-native native OS_NAME=Linux OS_ARCH=x86'

linux64:  jni-header
	docker run $(DOCKER_RUN_OPTS) -v $$PWD:/work xerial/centos5-linux-x86_64 bash -c 'make clean-native native OS_NAME=Linux OS_ARCH=x86_64'

freebsd32:  jni-header
	docker run $(DOCKER_RUN_OPTS) -v $$PWD:/workdir empterdose/freebsd-cross-build:9.3 sh -c 'apk add bash; apk add openjdk8; apk add perl; make clean-native native OS_NAME=FreeBSD OS_ARCH=x86 CROSS_PREFIX=i386-freebsd9-'

freebsd64:  jni-header
	docker run $(DOCKER_RUN_OPTS) -v $$PWD:/workdir empterdose/freebsd-cross-build:9.3 sh -c 'apk add bash; apk add openjdk8; apk add perl; make clean-native native OS_NAME=FreeBSD OS_ARCH=x86_64 CROSS_PREFIX=x86_64-freebsd9-'

freebsd-arm64:  jni-header
	docker run $(DOCKER_RUN_OPTS) -v $$PWD:/workdir gotson/freebsd-cross-build:aarch64-11.4 sh -c 'make clean-native native OS_NAME=FreeBSD OS_ARCH=aarch64 CROSS_PREFIX=aarch64-unknown-freebsd11-'

linux-musl32:  jni-header
	docker run $(DOCKER_RUN_OPTS) -v $$PWD:/work gotson/alpine-linux-x86 bash -c 'make clean-native native OS_NAME=Linux-Musl OS_ARCH=x86'

linux-musl64:  jni-header
	docker run $(DOCKER_RUN_OPTS) -v $$PWD:/work xerial/alpine-linux-x86_64 bash -c 'make clean-native native OS_NAME=Linux-Musl OS_ARCH=x86_64'

linux-musl-arm64:  jni-header
	./docker/dockcross-musl-arm64 -a $(DOCKER_RUN_OPTS) bash -c 'make clean-native native CROSS_PREFIX=aarch64-linux-musl- OS_NAME=Linux-Musl OS_ARCH=aarch64'

linux-arm:  jni-header
	./docker/dockcross-armv5 -a $(DOCKER_RUN_OPTS) bash -c 'make clean-native native CROSS_PREFIX=armv5-unknown-linux-gnueabi- OS_NAME=Linux OS_ARCH=arm'

linux-armv6:  jni-header
	./docker/dockcross-armv6-lts -a $(DOCKER_RUN_OPTS) bash -c 'make clean-native native CROSS_PREFIX=armv6-unknown-linux-gnueabihf- OS_NAME=Linux OS_ARCH=armv6'

linux-armv7:  jni-header
	./docker/dockcross-armv7a-lts -a $(DOCKER_RUN_OPTS) bash -c 'make clean-native native CROSS_PREFIX=arm-cortexa8_neon-linux-gnueabihf- OS_NAME=Linux OS_ARCH=armv7'

linux-arm64:  jni-header
	./docker/dockcross-arm64-lts -a $(DOCKER_RUN_OPTS) bash -c 'make clean-native native CROSS_PREFIX=aarch64-unknown-linux-gnu- OS_NAME=Linux OS_ARCH=aarch64'

linux-android-arm:  jni-header
	./docker/dockcross-android-arm -a $(DOCKER_RUN_OPTS) bash -c 'make clean-native native CROSS_PREFIX=/usr/arm-linux-androideabi/bin/arm-linux-androideabi- OS_NAME=Linux-Android OS_ARCH=arm'

linux-android-arm64:  jni-header
	./docker/dockcross-android-arm64 -a $(DOCKER_RUN_OPTS) bash -c 'make clean-native native CROSS_PREFIX=/usr/aarch64-linux-android/bin/aarch64-linux-android- OS_NAME=Linux-Android OS_ARCH=aarch64'

linux-android-x86:  jni-header
	./docker/dockcross-android-x86 -a $(DOCKER_RUN_OPTS) bash -c 'make clean-native native CROSS_PREFIX=/usr/i686-linux-android/bin/i686-linux-android- OS_NAME=Linux-Android OS_ARCH=x86'

linux-android-x64:  jni-header
	./docker/dockcross-android-x86_64 -a $(DOCKER_RUN_OPTS) bash -c 'make clean-native native CROSS_PREFIX=/usr/x86_64-linux-android/bin/x86_64-linux-android- OS_NAME=Linux-Android OS_ARCH=x86_64'

linux-ppc64:  jni-header
	./docker/dockcross-ppc64 -a $(DOCKER_RUN_OPTS) bash -c 'make clean-native native CROSS_PREFIX=powerpc64le-unknown-linux-gnu- OS_NAME=Linux OS_ARCH=ppc64'

mac64:  jni-header
	docker run $(DOCKER_RUN_OPTS) -v $$PWD:/workdir -e CROSS_TRIPLE=x86_64-apple-darwin multiarch/crossbuild make clean-native native OS_NAME=Mac OS_ARCH=x86_64

mac-arm64:  jni-header
	docker run $(DOCKER_RUN_OPTS) -v $$PWD:/workdir -e CROSS_TRIPLE=aarch64-apple-darwin gotson/crossbuild make clean-native native OS_NAME=Mac OS_ARCH=aarch64 CROSS_PREFIX="/usr/osxcross/bin/aarch64-apple-darwin20.4-"

# deprecated
mac32:  jni-header
	docker run $(DOCKER_RUN_OPTS) -v $$PWD:/workdir -e CROSS_TRIPLE=i386-apple-darwin multiarch/crossbuild make clean-native native OS_NAME=Mac OS_ARCH=x86

sparcv9:
	$(MAKE) native OS_NAME=SunOS OS_ARCH=sparcv9

mac64-signed: mac64
	$(CODESIGN) src/main/resources/org/romantics/jni/native/Mac/x86_64/lib$(LIBRARY_NAME).dylib

mac-arm64-signed: mac-arm64
	$(CODESIGN) src/main/resources/org/romantics/jni/native/Mac/aarch64/lib$(LIBRARY_NAME).dylib

package: native-all
	rm -rf target/dependency-maven-plugin-markers
	$(MVN) package

clean-native:
	-rm -rf $$(PWD)/$(LIBRARY_OUT)

clean-java:
	-rm -rf $$(PWD)/$(TARGET)/*classes
	-rm -rf $$(PWD)/$(TARGET)/*headers

clean-tests:


docker-linux64:
	docker build -f docker/Dockerfile.linux_x86_64 -t xerial/centos5-linux-x86_64 .

docker-linux32:
	docker build -f docker/Dockerfile.linux_x86 -t xerial/centos5-linux-x86 .

docker-linux-musl32:
	docker build -f docker/Dockerfile.alpine-linux_x86 -t gotson/alpine-linux-x86 .

docker-linux-musl64:
	docker build -f docker/Dockerfile.alpine-linux_x86_64 -t xerial/alpine-linux-x86_64 .

