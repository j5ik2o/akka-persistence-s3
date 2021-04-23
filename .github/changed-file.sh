#!/bin/sh

set -e

TARGET_FILES=($1)
CHANGED_FILES=($2)

echo "TARGET_FILES=[${TARGET_FILES}]"
echo "CHANGED_FILES=[$CHANGED_FILES]"

EXIST="false"

for CHANGED_FILE in ${CHANGED_FILES[@]}; do
  echo "CHANGED_FILE=$CHANGED_FILE"
  for TARGET_FILE in ${TARGET_FILES[@]}; do
    echo "TARGET_FILE=$TARGET_FILE"
    if [[ ${CHANGED_FILE} = ${TARGET_FILE} ]]; then
      EXIST="true"
      break
    fi
  done
  if [[ "$EXIST" = "true" ]]; then
    break
  fi
done

echo $EXIST
