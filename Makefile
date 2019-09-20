.PHONY: all
all: test

.PHONY: test
test:
	./gradlew test jacocoTestReport --no-daemon --stacktrace
