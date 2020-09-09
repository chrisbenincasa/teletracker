#!/usr/bin/env bash

set -x

DATE=$1
OUTDIR=$2
TYPE=${3:-movie}

echo "$TYPE"
echo "'$TYPE'"

if [ ! -d "$OUTDIR" ]; then mkdir "$OUTDIR"; fi

URIS=$(aws s3api list-objects-v2 --bucket teletracker-data-us-west-2 --prefix elasticsearch/items/"$DATE"/ | jq -r '.Contents[].Key')

read -r -d '' SCRIPT << EOM
select s."type", s.external_ids, s.id from s3object[*]._source s where s."type" = '$TYPE'
EOM

ITER=0
for uri in $URIS; do
  aws s3api select-object-content --bucket teletracker-data-us-west-2 --key "$uri" \
    --expression "$SCRIPT"  \
    --expression-type "SQL" \
    --input-serialization '{"JSON":{"Type":"Lines"}}' \
    --output-serialization '{"JSON":{}}' \
    "$OUTDIR/$TYPE-$ITER.json" >/dev/null
  ((ITER++))
done
