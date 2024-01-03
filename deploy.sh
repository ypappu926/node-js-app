#!/bin/bash

echo "Starting to deploy docker image.."
DOCKER_IMAGE_URI = 917411321124.dkr.ecr.ap-south-1.amazonaws.com/node-app-repo:latest
docker pull $DOCKER_IMAGE_URI
docker ps -q --filter ancestor=$DOCKER_IMAGE_URI | xargs -r docker stop
docker run -d -p 5000:5000 $DOCKER_IMAGE_URI
