VERSION=1.0.15

default: versioncheck

clean:
	./gradlew clean

compile:
	./gradlew build -x test

jar:
	./gradlew shadowJar

tests:
	./gradlew check

refresh:
	./gradlew build --refresh-dependencies

build-docker: clean compile
	docker build -t pambrose/etcd-recipes-k8s-example:${VERSION} .

push-docker:
	docker push pambrose/etcd-recipes-k8s-example:${VERSION}

run-docker:
	docker run -p 8080:8080 pambrose/etcd-recipes-k8s-example:${VERSION}

versioncheck:
	./gradlew dependencyUpdates