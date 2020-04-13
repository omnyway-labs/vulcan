#!/bin/bash -xe

export DEBIAN_FRONTEND=noninteractive

apt-get update
apt-get install -yy  --no-install-recommends curl gnupg procps git wget make git openssh-client netcat bzip2

wget https://github.com/aktau/github-release/releases/download/v0.7.2/linux-amd64-github-release.tar.bz2
tar jxvf linux-amd64-github-release.tar.bz2
mv bin/linux/amd64/github-release /usr/local/bin/github-release
chmod +x /usr/local/bin/github-release
rm -f linux-amd64-github-release.tar.bz2

CLOJURE_TOOLS_VERSION=1.9.0.348


curl -O https://download.clojure.org/install/linux-install-${CLOJURE_TOOLS_VERSION}.sh
chmod +x linux-install-${CLOJURE_TOOLS_VERSION}.sh
./linux-install-${CLOJURE_TOOLS_VERSION}.sh

mkdir -p ~/.clojure
mv /build/deps.edn ~/.clojure/deps.edn
mv /build/vulcan /usr/local/bin/vulcan
chmod +x /usr/local/bin/vulcan

vulcan

echo "deb http://apt.postgresql.org/pub/repos/apt/ stretch-pgdg main" | tee -a /etc/apt/sources.list.d/pg.list
wget https://www.postgresql.org/media/keys/ACCC4CF8.asc
apt-key add ACCC4CF8.asc
apt-get update
apt-get install -yy  --no-install-recommends --allow-unauthenticated postgresql-client-9.6 postgresql-common

apt-get install -yy jq
apt-get clean
apt-get autoremove
apt-get remove -yy gcc
rm -rf /build/.git
