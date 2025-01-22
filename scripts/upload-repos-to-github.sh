#!/bin/bash

repo_dir=/home/pi/repos
counter=0

# iterate over every directory in the repo_dir
for repo in "$repo_dir"/*; do
  # get the team name from the directory name
  teamname=$(basename "$repo")
  echo "Pushing $teamname to GitHub"

  # change to the repository directory
  cd "$repo"

  # check if gh repo list kude-platform/submission-$teamname exists
  if gh repo view "kude-platform/submission-$teamname" &> /dev/null; then
    git remote
    git push upstream
    echo "Repo kude-platform/submission-$teamname already exists, skipping"
    continue
  fi

  # add the remote repository
  gh repo create "kude-platform/submission-$teamname" --private --source=. --remote=upstream

  git push upstream

  counter=$((counter+1))
  echo "Pushed $counter repos"
done