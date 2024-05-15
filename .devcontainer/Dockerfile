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
RUN apt-get -y install git help2man perl python3 make autoconf g++ flex bison ccache cmake neofetch verilator iverilog yosys openjdk-17-jdk gtkwave