.PHONY: build-all test-all build-macos test-macos build-android test-android clean

build-all: build-macos build-android

test-all: test-macos test-android

build-macos:
	cd macos-app && swift build

test-macos:
	cd macos-app && swift test

build-android:
	cd android-app && ./gradlew assembleDebug

test-android:
	cd android-app && ./gradlew testDebugUnitTest

clean:
	cd macos-app && swift package clean
	cd android-app && ./gradlew clean
