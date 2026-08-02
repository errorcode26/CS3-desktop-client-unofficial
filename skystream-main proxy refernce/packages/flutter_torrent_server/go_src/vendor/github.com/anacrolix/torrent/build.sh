#!/usr/local/bin/bash

PLATFORMS=(
#   'linux/arm64'
#   'linux/arm7'
#   'linux/amd64'
#   'linux/arm5'
#   'linux/386'
#   'windows/amd64'
#   'windows/386'
#   'darwin/amd64'
#   'darwin/arm64'
#   'freebsd/amd64'
#   'linux/mips'
#   'linux/mipsle'
#   'linux/mips64'
#   'linux/mips64le'
)

type setopt >/dev/null 2>&1

GOBIN="go"

$GOBIN version

LDFLAGS="'-s -w'"
FAILURES=""
ROOT=${PWD}
OUTPUT="${ROOT}/dist"

#### Build server
echo "Build server"
cd "${ROOT}" || exit 1
$GOBIN mod tidy
$GOBIN mod download

BUILD_FLAGS="-ldflags=${LDFLAGS}"

for PLATFORM in "${PLATFORMS[@]}"; do
  GOOS=${PLATFORM%/*}
  GOARCH=${PLATFORM#*/}
  BIN_FILENAME="${OUTPUT}-${GOOS}-${GOARCH}"
  if [[ "${GOOS}" == "windows" ]]; then BIN_FILENAME="${BIN_FILENAME}.exe"; fi
  CMD="GOOS=${GOOS} GOARCH=${GOARCH} ${GOBIN} build ${BUILD_FLAGS} -o ${BIN_FILENAME} ."
  echo "${CMD}"
  eval "$CMD"
done