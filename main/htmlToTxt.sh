#! /bin/bash
# replace local references with non-local ones \
# remove link number from USAGE \
# remove indent for <div> and <p> \
# remove title \
# remove SF.net and GPLv3 logos \
# remove SF.net and GPLv3 links from reference list \
lynx -width=80 -dump -dont_wrap_pre $1 \
| sed 's!USAGE: \[[0-9+]\]!USAGE: !' \
| sed -e 's!^ \{2,3\}\([^ ]\)!\1!' \
| awk '$1 !~ /\[[0-9]+\](SF\.net)|(GPLv3)/ { print }'

