#!/bin/bash

repo_dir=/home/pi/repos
counter=0
while IFS=";" read -r repo team
do
  echo "Cloning $repo into $repo_dir/$team"
  repo_with_psw=$(echo $repo | sed "s/https:\/\//https:\/\/$GITHUB_USERNAME:$GITHUB_TOKEN@/")
  git clone "$repo_with_psw" "$repo_dir"/"$team"
  counter=$((counter+1))
  echo "Cloned $counter repos"
done < "$repo_dir"/repos.csv
