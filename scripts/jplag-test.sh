#!/bin/bash

repo_dir=/home/pi/repos
counter=0
while IFS=";" read -r repo team
do
  teamname=$(echo $team | sed 's/;*$//')
  echo "Cloning $repo into $repo_dir/$teamname"
  if [ -d "$repo_dir"/"$teamname" ]; then
    echo "Repo $teamname already exists, skipping"
    continue
  fi
  repo_with_psw=$(echo $repo | sed "s/https:\/\//https:\/\/$GITHUB_USERNAME:$GITHUB_TOKEN@/")
  git clone "$repo_with_psw" "$repo_dir"/"$teamname"
  counter=$((counter+1))
  echo "Cloned $counter repos"
done < "$repo_dir"/repos.csv
