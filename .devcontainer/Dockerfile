FROM mcr.microsoft.com/vscode/devcontainers/base:ubuntu

ENV LANG C.UTF-8
ARG INSTALL_ZSH="true"

RUN apt-get update
#install sbt
RUN sudo apt-get install apt-transport-https curl gnupg -yqq
RUN echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
RUN echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | sudo tee /etc/apt/sources.list.d/sbt_old.list
RUN curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo -H gpg --no-default-keyring --keyring gnupg-ring:/etc/apt/trusted.gpg.d/scalasbt-release.gpg --import
RUN sudo chmod 644 /etc/apt/trusted.gpg.d/scalasbt-release.gpg
RUN sudo apt-get update && export DEBIAN_FRONTEND=noninteractive
RUN sudo apt-get install sbt
#Install everything else
RUN apt-get -y install git help2man perl python3 make autoconf g++ flex bison ccache cmake neofetch openjdk-17-jdk wget 

#gtkwave z3 verilator iverilog yosys from oss cad suite
RUN cd /opt && wget https://github.com/YosysHQ/oss-cad-suite-build/releases/download/2024-11-21/oss-cad-suite-linux-x64-20241121.tgz && tar -xf oss-cad-suite-linux-x64-20241121.tgz && export PATH="/opt/oss-cad-suite/bin:$PATH" && rm oss-cad-suite-linux-x64-20241121.tgz