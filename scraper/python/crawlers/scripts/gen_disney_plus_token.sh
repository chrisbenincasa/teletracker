#!/usr/bin/env bash

RESPONSE=$(curl -s 'https://global.edge.bamgrid.com/token' \
  -H 'User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:77.0) Gecko/20100101 Firefox/77.0' \
  -H 'Accept: application/json' \
  -H 'Accept-Language: en-US,en;q=0.5' --compressed \
  -H 'authorization: Bearer ZGlzbmV5JmJyb3dzZXImMS4wLjA.Cu56AgSfBTDag5NiRA81oLHkDZfu5L3CKadnefEAY84' \
  -H 'content-type: application/x-www-form-urlencoded' \
  -H 'DNT: 1' \
  -H 'Connection: keep-alive' \
  -H 'Referer: https://www.disneyplus.com/' \
  -H 'TE: Trailers' \
  --data-raw 'grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Atoken-exchange&latitude=0&longitude=0&platform=browser&subject_token=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJlYjM1NDVhZS1mYzVjLTQ2NmQtODZhMC00MDhhMTcyY2I4NTYiLCJhdWQiOiJ1cm46YmFtdGVjaDpzZXJ2aWNlOnRva2VuIiwibmJmIjoxNTkyMDU5MDEwLCJpc3MiOiJ1cm46YmFtdGVjaDpzZXJ2aWNlOmRldmljZSIsImV4cCI6MjQ1NjA1OTAxMCwiaWF0IjoxNTkyMDU5MDEwLCJqdGkiOiJmNmFkOTQ5Ni02YzRkLTQwOGItODU5My0zMTdhMzRmZWE2NGMifQ.MB7kqTec5DhFF2B4QdkU_EVaUwcROqiZvuMnIEHsGpEoKUaFWvL-sYZ6pc4g4FWxnwEaU-Orm4RKSUeZw-EVew&subject_token_type=urn%3Abamtech%3Aparams%3Aoauth%3Atoken-type%3Adevice')

echo "$RESPONSE" | jq -r '.access_token'