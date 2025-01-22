#!/bin/bash

repo_dir=/home/pi/repos
counter=0
rm -f "$repo_dir"/repos-cloned.csv
touch "$repo_dir"/repos-cloned.csv
for repo in "$repo_dir"/*; do
  if [ ! -d "$repo" ]; then
    continue
  fi
  teamname=$(basename "$repo")
  cd "$repo"
  repoUrl=$(git remote get-url upstream)
  echo "$repoUrl;$teamname" >> "$repo_dir"/repos-cloned.csv
done