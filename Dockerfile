FROM openjdk:8-jre-alpine

# Define the user to use in this instance to prevent using root that even in a container, can be a security risk.
ENV APPLICATION_USER demo

# Then add the user, create the /app folder and give permissions to our user.
RUN adduser -D -g '' $APPLICATION_USER
RUN mkdir /app
RUN chown -R $APPLICATION_USER /app

# Mark this container to use the specified $APPLICATION_USER
USER $APPLICATION_USER

COPY ./build/libs/etcd-recipes-k8s-demo-1.0.9-all.jar /app/etcd-recipes-k8s-demo-all.jar
WORKDIR /app

EXPOSE 8080

CMD []

# Launch java to execute the jar with defaults intended for containers.
ENTRYPOINT ["java", "-server", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-XX:InitialRAMFraction=2", "-XX:MinRAMFraction=2", "-XX:MaxRAMFraction=2", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=100", "-XX:+UseStringDeduplication", "-jar", "/app/etcd-recipes-k8s-demo-all.jar"]
