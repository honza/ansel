# docker build -t ansel .
# docker run -p 8000 ansel
from ubuntu:14.04
run apt-get update
run apt-get install -y openjdk-7-jre-headless wget
run wget http://honza.ca/ansel/releases/ansel-0.3.0-standalone.jar -O /opt/ansel.jar
expose 8000
workdir /opt
cmd ["java", "-Xmx1g", "-Djava.awt.headless=true", "-jar", "ansel.jar"]
